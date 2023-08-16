(ns honeyeql.core
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [edn-query-language.core :as eql]
            [next.jdbc.result-set :as rs]
            [next.jdbc.sql :as sql]
            [honey.sql.helpers :as hsql-helpers]
            [honeyeql.db-adapter.core :as db]
            [honeyeql.debug :refer [trace>>]]
            [honeyeql.dsl :as dsl]
            [honeyeql.meta-data :as heql-md]
            [inflections.core :as inf]))

(def ^:no-doc default-heql-config {:attr/return-as :naming-convention/qualified-kebab-case
                                   :eql/mode       :eql.mode/lenient})

(defn- eql-ident? [x]
  (and (vector? x) ((comp not map? second) x)))

(defn- transform-honeyeql-query [eql-query]
  (cond
    (keyword? eql-query) eql-query
    (vector? eql-query) eql-query
    (map? eql-query) (let [first-key (ffirst eql-query)
                           props     (->> (eql-query first-key)
                                          (map transform-honeyeql-query)
                                          vec)]
                       (cond
                         (keyword? first-key) {first-key props}
                         (list? first-key) {first-key props}
                         (eql-ident? first-key) {first-key props}
                         :else {(apply list first-key) props}))))

(defn- transform-honeyeql-queries [eql-queries]
  (let [eql-queries (if (vector? eql-queries) eql-queries (vector eql-queries))]
    (vec (map transform-honeyeql-query eql-queries))))

(defn- function-attribute-ident? [x]
  (and (vector? x)
       (keyword? (first x))
       (not (qualified-keyword? (first x)))))

(defn- eql-node->attr-ident [{:keys [key type dispatch-key]}]
  (cond
    (and (= :prop type) (keyword? key)) key
    (and (= :prop type) (function-attribute-ident? key)) (second key)
    (and (= :prop type) (dsl/alias-attribute-ident? key)) (if (function-attribute-ident? (first key))
                                                            (second (first key))
                                                            (first key))
    (and (= :join type) dispatch-key) key))

(defn- one-to-one-join-predicate [heql-meta-data {:attr.column.ref/keys [left right]} alias]
  [:=
   (keyword (str (:parent alias) "." (heql-md/attr-column-name heql-meta-data left)))
   (keyword (str (:self alias) "." (heql-md/attr-column-name heql-meta-data right)))])

(defn- one-to-many-join-predicate [heql-meta-data {:attr.column.ref/keys [left right]} alias]
  [:=
   (keyword (str (:parent alias) "." (heql-md/attr-column-name heql-meta-data left)))
   (keyword (str (:self alias) "." (heql-md/attr-column-name heql-meta-data right)))])

(defn- many-to-many-join-predicate [heql-meta-data {:attr.column.ref/keys [left right]
                                                    :as                   join-attr-md} alias assoc-table-alias]
  (let [{:attr.column.ref.associative/keys [left-ident right-ident]} join-attr-md]
    [:and
     [:=
      (keyword (str (:parent alias) "." (heql-md/attr-column-name heql-meta-data left)))
      (keyword (str assoc-table-alias "." (heql-md/attr-column-name heql-meta-data left-ident)))]
     [:=
      (keyword (str assoc-table-alias "." (heql-md/attr-column-name heql-meta-data right-ident)))
      (keyword (str (:self alias) "." (heql-md/attr-column-name heql-meta-data right)))]]))

(defmethod ^{:private true} dsl/eql->hsql :root [db-adapter heql-meta-data eql-node]
  (dsl/eql->hsql db-adapter heql-meta-data (first (:children eql-node))))

(defn- eql-ident->hsql-predicate [db-adapter [attr-ident value] alias]
  (let [heql-meta-data (:heql-meta-data db-adapter)
        attr-col-name  (heql-md/attr-column-name heql-meta-data attr-ident)
        attr-value     (heql-md/coerce-attr-value db-adapter :from-db attr-ident value)]
    [:= (keyword (str (:self alias) "." attr-col-name)) attr-value]))

(defn- eql-ident-key->hsql-predicate [db-adapter eql-ident-key alias]
  (let [predicates (map #(eql-ident->hsql-predicate db-adapter % alias) (partition 2 eql-ident-key))]
    (if (< 1 (count predicates))
      (conj predicates :and)
      (first predicates))))

(defn- resolve-group-by-column [db-adapter eql-node attr-ident]
  (->> (:children eql-node)
       (filter #(= attr-ident (:key %)))
       first
       :alias
       (db/resolve-one-to-one-relationship-alias db-adapter)))

(defn- hsql-column
  ([db-adapter attr-ident-or-rel-attr-ident eql-node]
   (hsql-column db-adapter attr-ident-or-rel-attr-ident eql-node false))
  ([db-adapter attr-ident-or-rel-attr-ident eql-node group-by-column]
   (let [heql-meta-data    (:heql-meta-data db-adapter)
         attr-ident        (if (keyword? attr-ident-or-rel-attr-ident) attr-ident-or-rel-attr-ident (last attr-ident-or-rel-attr-ident))
         attr-md           (heql-md/attr-meta-data heql-meta-data attr-ident)
         attr-col-name     (:attr.column/name attr-md)
         attr-col-ref-type (:attr.column.ref/type attr-md)
         eql-node          (if (keyword? attr-ident-or-rel-attr-ident)
                             eql-node
                             (first (filter #(= (:key %) (first attr-ident-or-rel-attr-ident)) (:children eql-node))))
         {:keys [self parent]} (:alias eql-node)]
     (if (and group-by-column (= :attr.column.ref.type/one-to-one attr-col-ref-type))
       (resolve-group-by-column db-adapter eql-node attr-ident)
       (if (keyword? attr-ident-or-rel-attr-ident)
         (keyword (str self "." attr-col-name))
         (keyword (str parent "__" self)
                  (str (namespace (:attr/ident attr-md)) "/" (name (:attr/ident attr-md)))))))))

(defn- order-by-clause [db-adapter eql-node clause]
  (if (keyword? clause)
    (hsql-column db-adapter clause eql-node)
    (let [[c t]         clause]
      (if (#{:asc :desc} t)
        [(hsql-column db-adapter c eql-node) t]
        (hsql-column db-adapter clause eql-node)))))

(defn- apply-order-by [hsql heql-meta-data clause eql-node]
  (assoc hsql :order-by (map #(order-by-clause heql-meta-data eql-node %) clause)))

(defn- coerce-value [db-adapter eql-node col value]
  (if (coll? value)
    (map #(coerce-value db-adapter eql-node col %) value)
    (if (and (keyword? value) (some? (namespace value)) (heql-md/attribute? (:heql-meta-data db-adapter) value))
      (hsql-column db-adapter value eql-node)
      (heql-md/coerce-attr-value db-adapter :from-db col value))))

(defn- hsql-predicate [db-adapter eql-node clause]
  (let [[op col v1 v2] clause
        hsql-col       (hsql-column db-adapter col eql-node)]
    (if v2
      [op hsql-col (coerce-value db-adapter eql-node col v1) (coerce-value db-adapter eql-node col v2)]
      [op hsql-col (coerce-value db-adapter eql-node col v1)])))

(defn- hsql-join-predicate [db-adapter eql-node join-attr-md self-alias]
  (let [heql-meta-data      (:heql-meta-data db-adapter)
        ref-type            (:attr.column.ref/type join-attr-md)
        alias               {:self   self-alias
                             :parent (get-in eql-node [:alias :self])}
        from-relation-ident (heql-md/entity-relation-ident heql-meta-data (:attr.column.ref/right join-attr-md))
        from-clause         [from-relation-ident (keyword self-alias)]]
    (case ref-type
      :attr.column.ref.type/one-to-one [[from-clause]
                                        (one-to-one-join-predicate heql-meta-data join-attr-md alias)]
      :attr.column.ref.type/one-to-many [[from-clause]
                                         (one-to-many-join-predicate heql-meta-data join-attr-md alias)]
      :attr.column.ref.type/many-to-many (let [assoc-table-alias       (str (gensym))
                                               assoc-table-from-clause [(->> (:attr.column.ref.associative/ident join-attr-md)
                                                                             (heql-md/entity-meta-data heql-meta-data)
                                                                             :entity.relation/ident)
                                                                        (keyword assoc-table-alias)]]
                                           [[from-clause assoc-table-from-clause]
                                            (many-to-many-join-predicate heql-meta-data join-attr-md alias assoc-table-alias)]))))

(defn- nested-entity-attr-predicate [db-adapter eql-node clause hsql join-attr-md attr-ident]
  (let [[op _ v1 v2] clause
        attr-ident   (if (qualified-keyword? attr-ident)
                       attr-ident
                       (keyword (name (:attr.ref/type join-attr-md)) (name attr-ident)))]
    (hsql-helpers/where
     hsql
     (hsql-predicate db-adapter eql-node [op attr-ident v1 v2]))))

(defn- nested-entity-predicate [db-adapter eql-node clause]
  (let [[op col]         clause
        join-attr-ident  (if (= :exists op) col (first col))
        attr-ident       (when-not (= :exists op) (second col))
        heql-meta-data   (:heql-meta-data db-adapter)
        self-alias       (str (gensym))
        self-eql-node    {:alias {:self self-alias}}
        join-attr-md     (heql-md/attr-meta-data heql-meta-data join-attr-ident)
        [from join-pred] (hsql-join-predicate db-adapter eql-node join-attr-md self-alias)
        hsql             {:select [1]
                          :from   from
                          :where  join-pred}]
    [:exists (if attr-ident
               (nested-entity-attr-predicate db-adapter self-eql-node clause hsql join-attr-md attr-ident)
               hsql)]))

(defn- where-predicate [db-adapter clause eql-node]
  (let [[op col] clause]
    (case op
      :and (concat [:and] (map #(where-predicate db-adapter % eql-node) (rest clause)))
      :or (concat [:or] (map #(where-predicate db-adapter % eql-node) (rest clause)))
      :not (conj [:not] (where-predicate db-adapter (second clause) eql-node))
      :exists (nested-entity-predicate db-adapter eql-node clause)
      (if (keyword? col)
        (hsql-predicate db-adapter eql-node clause)
        (nested-entity-predicate db-adapter eql-node clause)))))

(defn- apply-where [hsql db-adapter clause eql-node]
  (hsql-helpers/where hsql (where-predicate db-adapter clause eql-node)))

(defn- apply-group-by [hsql db-adapter clause eql-node]
  (apply hsql-helpers/group-by hsql (map #(hsql-column db-adapter % eql-node true) clause)))

(defn- apply-params [db-adapter hsql eql-node]
  (let [{:keys [limit offset order-by where group-by]} (:params eql-node)]
    (cond-> hsql
      limit  (assoc :limit limit)
      offset (assoc :offset offset)
      order-by (apply-order-by db-adapter order-by eql-node)
      where (apply-where db-adapter where eql-node)
      group-by (apply-group-by db-adapter group-by eql-node)
      :else identity)))

(defmethod ^{:private true} dsl/eql->hsql :ident-join [db-adapter heql-meta-data eql-node]
  (let [{:keys [key children alias]} eql-node
        hsql                         {:from   [[(heql-md/entity-relation-ident heql-meta-data (first key))
                                                (keyword (:self alias))]]
                                      :where  (eql-ident-key->hsql-predicate db-adapter key alias)
                                      :select (db/select-clause db-adapter heql-meta-data children)}
        hsql                         (apply-params db-adapter hsql eql-node)]
    (db/resolve-children-one-to-one-relationships db-adapter heql-meta-data hsql children)))

(defmethod ^{:private true} dsl/eql->hsql :non-ident-join [db-adapter heql-meta-data eql-node]
  (let [{:keys [children alias]} eql-node
        first-child-ident        (eql-node->attr-ident (first children))
        hsql                     {:from   [[(heql-md/entity-relation-ident heql-meta-data first-child-ident)
                                            (keyword (:self alias))]]
                                  :select (db/select-clause db-adapter heql-meta-data children)}
        hsql                     (apply-params db-adapter hsql eql-node)]
    (db/resolve-children-one-to-one-relationships db-adapter heql-meta-data hsql children)))

(defmethod ^{:private true} dsl/eql->hsql :one-to-one-join [db-adapter heql-meta-data eql-node]
  (let [{:keys [key children alias]} eql-node
        key                          (if (dsl/alias-attribute-ident? key)
                                       (first key)
                                       key)
        join-attr-md                 (heql-md/attr-meta-data heql-meta-data key)
        hsql                         {:from   [[(heql-md/ref-entity-relation-ident heql-meta-data key)
                                                (keyword (:self alias))]]
                                      :where  (one-to-one-join-predicate heql-meta-data join-attr-md alias)
                                      :select (db/select-clause db-adapter heql-meta-data children)}
        hsql                         (apply-params db-adapter hsql eql-node)]
    (db/resolve-one-to-one-relationship db-adapter heql-meta-data hsql eql-node)))

(defmethod ^{:private true} dsl/eql->hsql :one-to-many-join [db-adapter heql-meta-data eql-node]
  (let [{:keys [key children alias]} eql-node
        key                          (if (dsl/alias-attribute-ident? key)
                                       (first key)
                                       key)
        join-attr-md                 (heql-md/attr-meta-data heql-meta-data key)
        hsql                         {:from   [[(heql-md/ref-entity-relation-ident heql-meta-data key)
                                                (keyword (:self alias))]]
                                      :where  (one-to-many-join-predicate heql-meta-data join-attr-md alias)
                                      :select (db/select-clause db-adapter heql-meta-data children)}
        hsql                         (apply-params db-adapter hsql eql-node)]
    (db/resolve-one-to-many-relationship db-adapter heql-meta-data hsql eql-node)))

(defmethod ^{:private true} dsl/eql->hsql :many-to-many-join [db-adapter heql-meta-data eql-node]
  (let [{:keys [key children alias]} eql-node
        key                          (if (dsl/alias-attribute-ident? key)
                                       (first key)
                                       key)
        join-attr-md                 (heql-md/attr-meta-data heql-meta-data key)
        assoc-table-alias            (gensym)
        hsql                         {:from   [[(heql-md/ref-entity-relation-ident heql-meta-data key)
                                                (keyword (:self alias))]
                                               [(->> (:attr.column.ref.associative/ident join-attr-md)
                                                     (heql-md/entity-meta-data heql-meta-data)
                                                     :entity.relation/ident)
                                                (keyword assoc-table-alias)]]
                                      :where  (many-to-many-join-predicate heql-meta-data join-attr-md alias assoc-table-alias)
                                      :select (db/select-clause db-adapter heql-meta-data children)}
        hsql                         (apply-params db-adapter hsql eql-node)]
    (db/resolve-many-to-many-relationship db-adapter heql-meta-data hsql eql-node)))

(defn- json-key-fn [attribute-return-as aggregate-attr-convention key]
  (let [default-key (keyword key)]

    (if (= :naming-convention/qualified-kebab-case attribute-return-as)
      (if (= :aggregate-attr-naming-convention/vector aggregate-attr-convention)
        (if-let [[_ aggr-fun attr-name] (first (re-seq #"(.*)-of-(.*)" (name default-key)))]
          [(keyword aggr-fun) (keyword (namespace default-key) attr-name)]
          default-key)
        default-key)
      [default-key (dsl/column-alias attribute-return-as default-key)])))

(defn- json-value-fn [db-adapter attribute-return-as json-key json-value]
  (if (= :naming-convention/qualified-kebab-case attribute-return-as)
    (heql-md/coerce-attr-value db-adapter :from-db json-key json-value)
    (heql-md/coerce-attr-value db-adapter :from-db (first json-key) json-value)))

(defn- transform-keys [attribute-return-as return-value]
  (if (= :naming-convention/qualified-kebab-case attribute-return-as)
    return-value
    (inf/transform-keys return-value (comp keyword second))))

(defn- handle-non-default-schema [entity-name]
  (if (str/includes? (name entity-name) ".")
    (let [[schema-name table-name] (str/split (name entity-name) #"\.")]
      (keyword schema-name table-name))
    entity-name))

(defn- resolve-eql-nodes [{:keys [entities]} wild-card-select-node]
  (->> (:key wild-card-select-node)
       namespace
       keyword
       handle-non-default-schema
       entities
       :entity/attrs
       (map #(merge wild-card-select-node
                    {:key          %
                     :dispatch-key %
                     :attr-ident   %}))))


(declare ^:private enrich-eql-node)

(defn- resolve-wid-card-attributes [{:keys [heql-config heql-meta-data]
                                     :as   db-adapter} self-alias eql-nodes]
  (let [eql-nodes             (map #(enrich-eql-node db-adapter % self-alias) eql-nodes)]
    (if (= :eql.mode/lenient (:eql/mode heql-config))
      (let [[props joins]         ((juxt filter remove) #(= :prop (:type %)) eql-nodes)
            wild-card-select-node (some #(when (and (keyword? (:key %))
                                                    (= "*" (name (:key %)))) %) props)]
        (if wild-card-select-node
          (concat (resolve-eql-nodes heql-meta-data wild-card-select-node) joins)
          eql-nodes))
      eql-nodes)))

(defn-
  enrich-eql-node
  "Adds ident & alias to the eql node and also resolve wild-card-select props"
  ([db-adapter eql-node]
   (enrich-eql-node db-adapter eql-node nil))
  ([db-adapter eql-node parent-alias]
   (let [attr-ident                           (eql-node->attr-ident eql-node)]
     (case (:type eql-node)
       :root (update (assoc eql-node :attr-ident attr-ident) :children
                     (fn [eql-nodes]
                       (vec (map #(enrich-eql-node db-adapter %) eql-nodes))))
       :join (let [self-alias (gensym)]
               (update (assoc eql-node
                              :alias {:self   self-alias
                                      :parent parent-alias}
                              :attr-ident attr-ident)
                       :children
                       (partial resolve-wid-card-attributes db-adapter self-alias)))
       :prop (-> (assoc eql-node :attr-ident attr-ident)
                 (assoc-in [:alias :parent] parent-alias)
                 (assoc :function-attribute-ident (if (dsl/alias-attribute-ident? (:key eql-node))
                                                    (function-attribute-ident? (first (:key eql-node)))
                                                    (function-attribute-ident? (:key eql-node)))))))))

(defn query [db-adapter eql-query]
  (let [{:keys [heql-meta-data heql-config]} db-adapter
        {:attr/keys [return-as aggregate-attr-convention]} heql-config
        eql-query                            (case (:eql/mode heql-config)
                                               :eql.mode/lenient (trace>> :transformed-eql (transform-honeyeql-queries eql-query))
                                               :eql.mode/strict  eql-query)]
    (map  #(transform-keys return-as %)
          (json/read-str (->> (eql/query->ast eql-query)
                              (trace>> :raw-eql-ast)
                              (enrich-eql-node db-adapter)
                              (trace>> :eql-ast)
                              (dsl/eql->hsql db-adapter heql-meta-data)
                              (trace>> :hsql)
                              (db/to-sql db-adapter)
                              (trace>> :sql)
                              (db/query db-adapter))
                         :bigdec true
                         :key-fn #(json-key-fn return-as aggregate-attr-convention %)
                         :value-fn #(json-value-fn db-adapter return-as %1 %2)))))

(defn query-single [db-adapter eql-query]
  (first (query db-adapter eql-query)))

(defn- entity-name [entity]
  (-> entity keys first namespace))

(defn- table-name [entity]
  (-> (entity-name entity) (str/replace #"-" "_") keyword))

(defn- sqlize-entity [db-adapter entity]
  (into {}
        (map (fn [[k v]]
               [(-> (name k) (str/replace #"-" "_") keyword)
                (heql-md/coerce-attr-value db-adapter :to-db k v)])
             entity)))

(defn- namespacify-attributes [entity-name entity]
  (update-keys entity #(keyword entity-name (name %))))

(defn insert! [db-adapter entity]
  (namespacify-attributes
   (entity-name entity)
   (sql/insert! (:db-spec db-adapter)
                (table-name entity)
                (sqlize-entity db-adapter entity)
                {:column-fn (db/table-fn db-adapter)
                 :table-fn (db/table-fn db-adapter)
                 :builder-fn rs/as-kebab-maps})))

(defn insert-multi! [db-adapter entities]
  (when (seq entities)
    (let [entities (map #(into (sorted-map) %) entities)
          first-entity (first entities)
          first-entity-name (entity-name first-entity)
          sqlized-entities (map (partial sqlize-entity db-adapter) entities)]
      (map
       #(namespacify-attributes first-entity-name %)
       (sql/insert-multi! (:db-spec db-adapter)
                          (table-name first-entity)
                          (keys (first sqlized-entities))
                          (map vals sqlized-entities)
                          {:column-fn (db/table-fn db-adapter)
                           :table-fn (db/table-fn db-adapter)
                           :builder-fn rs/as-kebab-maps})))))

(defn update! [db-adapter update-params where-params]
  (sql/update! (:db-spec db-adapter) (table-name update-params)
               (sqlize-entity db-adapter update-params) (sqlize-entity db-adapter where-params)
               {:column-fn (db/table-fn db-adapter)
                :table-fn (db/table-fn db-adapter)}))

(defn delete! [db-adapter where-params]
  (sql/delete! (:db-spec db-adapter) (table-name where-params)
               (sqlize-entity db-adapter where-params)
               {:column-fn (db/table-fn db-adapter)
                :table-fn (db/table-fn db-adapter)}))

(defn db-spec [db-adapter]
  (:db-spec db-adapter))

(defn use-tx [db-adapter tx]
  (assoc db-adapter :db-spec tx))