(ns honeyeql.core
  (:require [edn-query-language.core :as eql]
            [honeyeql.meta-data :as heql-md]
            [clojure.data.json :as json]
            [inflections.core :as inf]
            [honeyeql.debug :refer [trace>>]]))

(defn- find-join-type [heql-meta-data eql-node]
  (let [{node-type :type
         node-key  :key} eql-node]
    (cond
      (= :root node-type) :root
      (and (= :join node-type) (keyword? node-key)) (-> (heql-md/attr-column-ref-type heql-meta-data node-key)
                                                        name
                                                        (str "-join")
                                                        keyword)
      (and (= :join node-type) (seq node-key) (even? (count node-key))) :ident-join)))

(defn- eql-node->attr-ident-with-alias [{:keys [key type dispatch-key alias]}]
  (cond
    (and (= :prop type) (keyword? key)) [key alias]
    (and (= :join type) dispatch-key) [key alias]))

(defn- eql-nodes->attr-idents-with-alias [eql-nodes]
  (map eql-node->attr-ident-with-alias eql-nodes))

(defn- column-alias [attr-return-as attr-ident]
  (case attr-return-as
    :qualified-kebab-case (str (namespace attr-ident) "/" (name attr-ident))
    :unqualified-camel-case (inf/camel-case (name attr-ident) :lower)))

(defn- select-clause [heql-meta-data eql-nodes]
  (let [attr-idents-with-alias (eql-nodes->attr-idents-with-alias eql-nodes)
        attr-column-idents     (map (fn [[attr-ident {:keys [parent self]}]]
                                      [(if (and self parent) 
                                         (keyword (str parent "__" self))
                                         (->> (heql-md/attr-column-name heql-meta-data attr-ident)
                                                                  (str parent ".")
                                                                  keyword))
                                       (column-alias :qualified-kebab-case attr-ident)]) attr-idents-with-alias)]
    (vec attr-column-idents)))

(defmulti ^{:private true} eql->hsql (fn [heql-meta-data eql-node] (find-join-type heql-meta-data eql-node)))

(defmethod ^{:private true} eql->hsql :root [heql-meta-data eql-node]
  (eql->hsql heql-meta-data (first (:children eql-node))))

;; Ident Join

(defn- eql-ident->hsql-predicate [heql-meta-data [attr-ident value] alias]
  (let [attr-col-name (heql-md/attr-column-name heql-meta-data attr-ident)
        attr-value    (heql-md/coarce-attr-value heql-meta-data attr-ident value)]
    [:= (keyword (str (:self alias) "." attr-col-name)) attr-value]))

(defn- eql-ident-key->hsql-predicate [heql-meta-data eql-ident-key alias]
  (let [predicates (map #(eql-ident->hsql-predicate heql-meta-data % alias) (partition 2 eql-ident-key))]
    (if (< 1 (count predicates))
      (conj predicates :and)
      (first predicates))))

(defn- assoc-one-to-one-hsql-queries [heql-meta-data hsql eql-nodes]
  (if-let [one-to-one-join-children
           (seq (filter #(= :one-to-one-join (find-join-type heql-meta-data %)) eql-nodes))]
    (assoc hsql :left-join-lateral (map #(eql->hsql heql-meta-data %) one-to-one-join-children))
    hsql))

(defmethod ^{:private true} eql->hsql :ident-join [heql-meta-data eql-node]
  (let [{:keys [key children alias]} eql-node
        hsql                         {:from   [[(heql-md/entity-relation-ident heql-meta-data (first key))
                                               (keyword (:self alias))]]
                                      :where  (eql-ident-key->hsql-predicate heql-meta-data key alias)
                                      :select (select-clause heql-meta-data children)}]
    (assoc-one-to-one-hsql-queries heql-meta-data hsql children)))

;; One to One Join

(defn- one-to-one-join-predicate [heql-meta-data {:attr.column.ref/keys [left right]} alias]
  [:= 
   (keyword (str (:parent alias) "." (heql-md/attr-column-name heql-meta-data left))) 
   (keyword (str (:self alias) "." (heql-md/attr-column-name heql-meta-data right)))])

(defn join-predicate [heql-meta-data join-attr-ident alias]
  (let [join-attr-md (heql-md/attr-meta-data heql-meta-data join-attr-ident)]
    (when (= :attr.type/ref (:attr/type join-attr-md))
      (case (:attr.column.ref/type join-attr-md)
        :attr.column.ref.type/one-to-one (one-to-one-join-predicate heql-meta-data join-attr-md alias)))))

(defmethod eql->hsql :one-to-one-join [heql-meta-data eql-node]
  (let [{:keys [key children alias]} eql-node
        hsql                   {:from   [[(heql-md/ref-entity-relation-ident heql-meta-data key)
                                          (keyword (:self alias))]]
                                :where  (join-predicate heql-meta-data key alias)
                                :select (select-clause heql-meta-data children)}]
    [(assoc-one-to-one-hsql-queries heql-meta-data hsql children)
     (keyword (str (:parent alias) "__" (:self alias)))]))

(def default-heql-config {:attribute {:return-as :qualified-kebab-case}})

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

(defprotocol DbAdapter
  (db-spec [db-adapter])
  (meta-data [db-adapter])
  (config [db-adapter])
  (merge-config [db-adapter config-to-override])
  (execute [db-adapter hsql]))

(defn- add-alias
  ([eql-node]
   (add-alias eql-node nil))
  ([eql-node parent-alias]
   (case (:type eql-node)
     :root (update eql-node :children
                   (fn [eql-nodes]
                     (vec (map #(add-alias %) eql-nodes))))
     :join (let [self-alias (gensym)]
             (update (assoc eql-node
                            :alias {:self   self-alias
                                    :parent parent-alias})
                     :children
                     (fn [eql-nodes]
                       (vec (map #(add-alias % self-alias) eql-nodes)))))
     :prop (assoc-in eql-node [:alias :parent] parent-alias))))

(defn query [db-apapter eql-query]
  (let [heql-meta-data (meta-data db-apapter)
        attr-return-as (get-in (config db-apapter) [:attribute :return-as])]
    (map  #(transform-keys attr-return-as %)
          (json/read-str (->> (eql/query->ast eql-query)
                              add-alias
                              (trace>> :eql-ast)
                              (eql->hsql heql-meta-data)
                              (trace>> :hsql)
                              (execute db-apapter))
                         :bigdec true
                         :key-fn #(json-key-fn attr-return-as %)
                         :value-fn #(json-value-fn heql-meta-data attr-return-as %1 %2)))))

(defn query-single [db-apapter eql-query]
  (first (query db-apapter eql-query)))