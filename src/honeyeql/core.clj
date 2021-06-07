(ns honeyeql.core
  (:require [edn-query-language.core :as eql]
            [honeyeql.meta-data :as heql-md]
            [clojure.data.json :as json]
            [inflections.core :as inf]
            [honeysql.helpers :as hsql-helpers]
            [honeyeql.db-adapter.core :as db]
            [clojure.string :as str]
            [honeyeql.debug :refer [trace>>]]))

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

(defn ^:no-doc find-join-type [heql-meta-data eql-node]
  (let [{node-type :type
         node-key  :key} eql-node]
    (cond
      (= :root node-type) :root
      (and (= :join node-type) (vector? node-key) (empty? node-key)) :non-ident-join
      (and (= :join node-type) (keyword? node-key)) (-> (heql-md/attr-column-ref-type heql-meta-data node-key)
                                                        name
                                                        (str "-join")
                                                        keyword)
      (and (= :join node-type) (seq node-key) (even? (count node-key))) :ident-join)))

(defn- function-attribute-ident? [x]
  (vector? x))

(defn- eql-node->attr-ident [{:keys [key type dispatch-key]}]
  (cond
    (and (= :prop type) (or (keyword? key) (function-attribute-ident? key))) (if (function-attribute-ident? key)
                                                                               (second key)
                                                                               key)
    (and (= :join type) dispatch-key) key))

(defn ^:no-doc column-alias [attr-naming-convention attr-ident]
  (case attr-naming-convention
    :naming-convention/qualified-kebab-case (str (namespace attr-ident) "/" (name attr-ident))
    :naming-convention/unqualified-kebab-case (name attr-ident)
    :naming-convention/unqualified-camel-case (inf/camel-case (name attr-ident) :lower)))

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

(defmulti ^:no-doc eql->hsql (fn [db-adapter heql-meta-data eql-node] (find-join-type heql-meta-data eql-node)))

(defmethod ^{:private true} eql->hsql :root [db-adapter heql-meta-data eql-node]
  (eql->hsql db-adapter heql-meta-data (first (:children eql-node))))

(defn- eql-ident->hsql-predicate [db-adapter [attr-ident value] alias]
  (let [heql-meta-data (:heql-meta-data db-adapter)
        attr-col-name  (heql-md/attr-column-name heql-meta-data attr-ident)
        attr-value     (heql-md/coerce-attr-value db-adapter attr-ident value)]
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
  ([db-adapter attr-ident eql-node]
   (hsql-column db-adapter attr-ident eql-node false))
  ([db-adapter attr-ident eql-node group-by-column]
   (let [heql-meta-data (:heql-meta-data db-adapter)
         attr-md        (heql-md/attr-meta-data heql-meta-data attr-ident)
         attr-col-name  (:attr.column/name attr-md)
         attr-type      (:attr.column.ref/type attr-md)
         {:keys [self]} (:alias eql-node)]
     (if (and group-by-column (= :attr.column.ref.type/one-to-one attr-type))
       (resolve-group-by-column db-adapter eql-node attr-ident)
       (keyword (str self "." attr-col-name))))))

(defn- order-by-clause [db-adapter eql-node clause]
  (if (keyword? clause)
    (hsql-column db-adapter clause eql-node)
    (let [[c t]         clause]
      [(hsql-column db-adapter c eql-node) t])))

(defn- apply-order-by [hsql heql-meta-data clause eql-node]
  (assoc hsql :order-by (map #(order-by-clause heql-meta-data eql-node %) clause)))

(defn- hsql-predicate [db-adapter eql-node clause]
  (let [[op col v1 v2] clause
        hsql-col       (hsql-column db-adapter col eql-node)]
    (case op
      (:in :not-in) [op hsql-col (map #(heql-md/coerce-attr-value db-adapter col %) v1)]
      (if v2
        [op hsql-col (heql-md/coerce-attr-value db-adapter col v1) (heql-md/coerce-attr-value db-adapter col v2)]
        [op hsql-col (heql-md/coerce-attr-value db-adapter col v1)]))))

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
    (hsql-helpers/merge-where
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

#_(nested-entity-predicate db n c)

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
  (hsql-helpers/merge-where hsql (where-predicate db-adapter clause eql-node)))

(defn- apply-group-by [hsql db-adapter clause eql-node]
  (apply hsql-helpers/group hsql (map #(hsql-column db-adapter % eql-node true) clause)))

(defn- apply-params [db-adapter hsql eql-node]
  (let [{:keys [limit offset order-by where group-by]} (:params eql-node)]
    (cond-> hsql
      limit  (assoc :limit limit)
      offset (assoc :offset offset)
      order-by (apply-order-by db-adapter order-by eql-node)
      where (apply-where db-adapter where eql-node)
      group-by (apply-group-by db-adapter group-by eql-node)
      :else identity)))

(defmethod ^{:private true} eql->hsql :ident-join [db-adapter heql-meta-data eql-node]
  (let [{:keys [key children alias]} eql-node
        hsql                         {:from   [[(heql-md/entity-relation-ident heql-meta-data (first key))
                                                (keyword (:self alias))]]
                                      :where  (eql-ident-key->hsql-predicate db-adapter key alias)
                                      :select (db/select-clause db-adapter heql-meta-data children)}
        hsql                         (apply-params db-adapter hsql eql-node)]
    (db/resolve-children-one-to-one-relationships db-adapter heql-meta-data hsql children)))

(defmethod ^{:private true} eql->hsql :non-ident-join [db-adapter heql-meta-data eql-node]
  (let [{:keys [children alias]} eql-node
        first-child-ident        (eql-node->attr-ident (first children))
        hsql                     {:from   [[(heql-md/entity-relation-ident heql-meta-data first-child-ident)
                                            (keyword (:self alias))]]
                                  :select (db/select-clause db-adapter heql-meta-data children)}
        hsql                     (apply-params db-adapter hsql eql-node)]
    (db/resolve-children-one-to-one-relationships db-adapter heql-meta-data hsql children)))

(defmethod ^{:private true} eql->hsql :one-to-one-join [db-adapter heql-meta-data eql-node]
  (let [{:keys [key children alias]} eql-node
        join-attr-md                 (heql-md/attr-meta-data heql-meta-data key)
        hsql                         {:from   [[(heql-md/ref-entity-relation-ident heql-meta-data key)
                                                (keyword (:self alias))]]
                                      :where  (one-to-one-join-predicate heql-meta-data join-attr-md alias)
                                      :select (db/select-clause db-adapter heql-meta-data children)}
        hsql                         (apply-params db-adapter hsql eql-node)]
    (db/resolve-one-to-one-relationship db-adapter heql-meta-data hsql eql-node)))

(defmethod ^{:private true} eql->hsql :one-to-many-join [db-adapter heql-meta-data eql-node]
  (let [{:keys [key children alias]} eql-node
        join-attr-md                 (heql-md/attr-meta-data heql-meta-data key)
        hsql                         {:from   [[(heql-md/ref-entity-relation-ident heql-meta-data key)
                                                (keyword (:self alias))]]
                                      :where  (one-to-many-join-predicate heql-meta-data join-attr-md alias)
                                      :select (db/select-clause db-adapter heql-meta-data children)}
        hsql                         (apply-params db-adapter hsql eql-node)]
    (db/resolve-one-to-many-relationship db-adapter heql-meta-data hsql eql-node)))

(defmethod ^{:private true} eql->hsql :many-to-many-join [db-adapter heql-meta-data eql-node]
  (let [{:keys [key children alias]} eql-node
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
      [default-key (column-alias attribute-return-as default-key)])))

(comment
  (json-key-fn a c k)
  (first nil))



(defn- json-value-fn [db-adapter attribute-return-as json-key json-value]
  (if (= :naming-convention/qualified-kebab-case attribute-return-as)
    (heql-md/coerce-attr-value db-adapter json-key json-value)
    (heql-md/coerce-attr-value db-adapter (first json-key) json-value)))

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


(defn select-clause-alias [{:keys [attr-ident key function-attribute-ident]}]
  (let [attr-ident (if function-attribute-ident
                     (keyword (namespace attr-ident) (str (name (first key)) "-of-" (name attr-ident)))
                     attr-ident)]
    (column-alias :naming-convention/qualified-kebab-case attr-ident)))

(declare enrich-eql-node)

(defn resolve-wid-card-attributes [{:keys [heql-config heql-meta-data]
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
                 (assoc :function-attribute-ident (function-attribute-ident? (:key eql-node))))))))

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
                              (eql->hsql db-adapter heql-meta-data)
                              (trace>> :hsql)
                              (db/to-sql db-adapter)
                              (trace>> :sql)
                              (db/query db-adapter))
                         :bigdec true
                         :key-fn #(json-key-fn return-as aggregate-attr-convention %)
                         :value-fn #(json-value-fn db-adapter return-as %1 %2)))))

(defn query-single [db-adapter eql-query]
  (first (query db-adapter eql-query)))