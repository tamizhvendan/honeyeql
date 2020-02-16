(ns honeyeql.core
  (:require [edn-query-language.core :as eql]
            [honeyeql.meta-data :as heql-md]
            [clojure.data.json :as json]
            [inflections.core :as inf]
            [next.jdbc.sql :as jdbc-sql]
            [honeyeql.debug :refer [trace>>]]))

(def default-heql-config {:attribute {:return-as :qualified-kebab-case}})

(defprotocol DbAdapter
  (db-spec [db-adapter])
  (meta-data [db-adapter])
  (config [db-adapter])
  (merge-config [db-adapter config-to-override])
  (to-sql [db-adapter hsql])
  (select-clause [db-adapter heql-meta-data eql-nodes])
  (resolve-one-to-one-relationship [db-adapter heql-meta-data hsql eql-node])
  (resolve-children-one-to-one-relationships [db-adapter heql-meta-data hsql eql-nodes]))

(defn- find-join-type [heql-meta-data eql-node]
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

(defn- eql-node->attr-ident [{:keys [key type dispatch-key]}]
  (cond
    (and (= :prop type) (keyword? key)) key
    (and (= :join type) dispatch-key) key))

(defn column-alias [attr-return-as attr-ident]
  (case attr-return-as
    :qualified-kebab-case (str (namespace attr-ident) "/" (name attr-ident))
    :unqualified-camel-case (inf/camel-case (name attr-ident) :lower)))

(defmulti eql->hsql (fn [db-adapter heql-meta-data eql-node] (find-join-type heql-meta-data eql-node)))

(defmethod ^{:private true} eql->hsql :root [db-adapter heql-meta-data eql-node]
  (eql->hsql db-adapter heql-meta-data (first (:children eql-node))))

(defn- eql-ident->hsql-predicate [heql-meta-data [attr-ident value] alias]
  (let [attr-col-name (heql-md/attr-column-name heql-meta-data attr-ident)
        attr-value    (heql-md/coarce-attr-value heql-meta-data attr-ident value)]
    [:= (keyword (str (:self alias) "." attr-col-name)) attr-value]))

(defn- eql-ident-key->hsql-predicate [heql-meta-data eql-ident-key alias]
  (let [predicates (map #(eql-ident->hsql-predicate heql-meta-data % alias) (partition 2 eql-ident-key))]
    (if (< 1 (count predicates))
      (conj predicates :and)
      (first predicates))))

(defmethod ^{:private true} eql->hsql :ident-join [db-adapter heql-meta-data eql-node]
  (let [{:keys [key children alias]} eql-node
        hsql                         {:from   [[(heql-md/entity-relation-ident heql-meta-data (first key))
                                                (keyword (:self alias))]]
                                      :where  (eql-ident-key->hsql-predicate heql-meta-data key alias)
                                      :select (select-clause db-adapter heql-meta-data children)}]
    (resolve-children-one-to-one-relationships db-adapter heql-meta-data hsql children)))

(defmethod ^{:private true} eql->hsql :non-ident-join [db-adapter heql-meta-data eql-node]
  (let [{:keys [children alias]} eql-node
        first-child-ident        (eql-node->attr-ident (first children))
        hsql                     {:from   [[(heql-md/entity-relation-ident heql-meta-data first-child-ident)
                                            (keyword (:self alias))]]
                                  :select (select-clause db-adapter heql-meta-data children)}]
    (resolve-children-one-to-one-relationships db-adapter heql-meta-data hsql children)))

(defn- one-to-one-join-predicate [heql-meta-data {:attr.column.ref/keys [left right]} alias]
  [:=
   (keyword (str (:parent alias) "." (heql-md/attr-column-name heql-meta-data left)))
   (keyword (str (:self alias) "." (heql-md/attr-column-name heql-meta-data right)))])

(defmethod ^{:private true} eql->hsql :one-to-one-join [db-adapter heql-meta-data eql-node]
  (let [{:keys [key children alias]} eql-node
        join-attr-md                 (heql-md/attr-meta-data heql-meta-data key)
        hsql                         {:from   [[(heql-md/ref-entity-relation-ident heql-meta-data key)
                                                (keyword (:self alias))]]
                                      :where  (one-to-one-join-predicate heql-meta-data join-attr-md alias)
                                      :select (select-clause db-adapter heql-meta-data children)}]
    (resolve-one-to-one-relationship db-adapter heql-meta-data hsql eql-node)))

(defn- one-to-many-join-predicate [heql-meta-data {:attr.column.ref/keys [left right]} alias]
  [:=
   (keyword (str (:parent alias) "." (heql-md/attr-column-name heql-meta-data left)))
   (keyword (str (:self alias) "." (heql-md/attr-column-name heql-meta-data right)))])

(defn- json-agg [select-alias]
  (keyword (str "%json_agg." (name select-alias) ".*")))

(defmethod ^{:private true} eql->hsql :one-to-many-join [db-adapter heql-meta-data eql-node]
  (let [{:keys [key children alias]} eql-node
        join-attr-md                 (heql-md/attr-meta-data heql-meta-data key)
        hsql                         {:from   [[(heql-md/ref-entity-relation-ident heql-meta-data key)
                                                (keyword (:self alias))]]
                                      :where  (one-to-many-join-predicate heql-meta-data join-attr-md alias)
                                      :select (select-clause db-adapter heql-meta-data children)}
        projection-alias             (keyword (gensym))]
    {:coalesce-array {:select [(json-agg projection-alias)]
                      :from   [[(resolve-children-one-to-one-relationships db-adapter heql-meta-data hsql children) projection-alias]]}}))

(defn- many-to-many-join-predicate [heql-meta-data {:attr.column.ref/keys [left right]
                                                    :as                   join-attr-md} alias assoc-table-alias]
  (let [{:attr.column.ref.associative/keys [left-ident right-ident]} join-attr-md]
    [:and
     [:=
      (keyword (str (:self alias) "." (heql-md/attr-column-name heql-meta-data left)))
      (keyword (str assoc-table-alias "." (heql-md/attr-column-name heql-meta-data left-ident)))]
     [:=
      (keyword (str assoc-table-alias "." (heql-md/attr-column-name heql-meta-data right-ident)))
      (keyword (str (:parent alias) "." (heql-md/attr-column-name heql-meta-data right)))]]))

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
                                      :select (select-clause db-adapter heql-meta-data children)}
        projection-alias             (keyword (gensym))]
    {:coalesce-array {:select [(json-agg projection-alias)]
                      :from   [[(resolve-children-one-to-one-relationships db-adapter heql-meta-data hsql children) projection-alias]]}}))

(defn- json-key-fn [attribute-return-as key]
  (if (= :qualified-kebab-case attribute-return-as)
    (keyword key)
    [(keyword key) (column-alias attribute-return-as (keyword key))]))

(defn- json-value-fn [heql-meta-data attribute-return-as json-key json-value]
  (if (= :qualified-kebab-case attribute-return-as)
    (heql-md/coarce-attr-value heql-meta-data json-key json-value)
    (heql-md/coarce-attr-value heql-meta-data (first json-key) json-value)))

(defn- transform-keys [attribute-return-as return-value]
  (if (= :qualified-kebab-case attribute-return-as)
    return-value
    (inf/transform-keys return-value (comp keyword second))))

(defn- add-alias-and-ident
  ([eql-node]
   (add-alias-and-ident eql-node nil))
  ([eql-node parent-alias]
   (let [attr-ident (eql-node->attr-ident eql-node)]
     (case (:type eql-node)
       :root (update (assoc eql-node :attr-ident attr-ident) :children
                     (fn [eql-nodes]
                       (vec (map #(add-alias-and-ident %) eql-nodes))))
       :join (let [self-alias (gensym)]
               (update (assoc eql-node
                              :alias {:self   self-alias
                                      :parent parent-alias}
                              :attr-ident attr-ident)
                       :children
                       (fn [eql-nodes]
                         (vec (map #(add-alias-and-ident % self-alias) eql-nodes)))))
       :prop (assoc-in (assoc eql-node :attr-ident attr-ident) [:alias :parent] parent-alias)))))

(defn query [db-adapter eql-query]
  (let [heql-meta-data (meta-data db-adapter)
        attr-return-as (get-in (config db-adapter) [:attribute :return-as])]
    (map  #(transform-keys attr-return-as %)
          (json/read-str (->> (eql/query->ast eql-query)
                              add-alias-and-ident
                              (trace>> :eql-ast)
                              (eql->hsql db-adapter heql-meta-data)
                              (trace>> :hsql)
                              (to-sql db-adapter)
                              (trace>> :sql)
                              (jdbc-sql/query (db-spec db-adapter))
                              first
                              :result)
                         :bigdec true
                         :key-fn #(json-key-fn attr-return-as %)
                         :value-fn #(json-value-fn heql-meta-data attr-return-as %1 %2)))))

(defn query-single [db-adapter eql-query]
  (first (query db-adapter eql-query)))