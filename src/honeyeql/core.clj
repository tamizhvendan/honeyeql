(ns honeyeql.core
  (:require [edn-query-language.core :as eql]
            [honeyeql.meta-data :as heql-md]
            [clojure.data.json :as json]
            [inflections.core :as inf]))

(defn- find-join-type [eql-node]
  (let [{node-type :type
         node-key  :key} eql-node]
    (cond
      (= :root node-type) :root
      (and (= :join node-type) (seq node-key) (even? (count node-key))) :ident-join)))

(defn- eql-nodes->attr-idents [eql-nodes]
  (map (fn [{:keys [key type]}]
         (cond
           (and (= :prop type) (keyword? key)) key)) eql-nodes))

(defn- column-alias [attr-return-as attr-ident]
  (case attr-return-as
    :qualified-kebab-case (str (namespace attr-ident) "/" (name attr-ident))
    :unqualified-camel-case (inf/camel-case (name attr-ident) :lower)))

(defn- select-clause [heql-meta-data eql-nodes]
  (let [attr-idents              (eql-nodes->attr-idents eql-nodes)
        attr-column-idents       (map (fn [attr-ident]
                                        [(heql-md/attr-column-ident heql-meta-data attr-ident)
                                         (column-alias :qualified-kebab-case attr-ident)]) attr-idents)]
    (vec attr-column-idents)))

(defmulti eql->hsql (fn [heql-meta-data eql-node] (find-join-type eql-node)))

(defmethod eql->hsql :root [heql-meta-data eql-node]
  (eql->hsql heql-meta-data (first (:children eql-node))))

(defn- eql-ident-key->hsql-predicate [heql-meta-data [attr-ident value]]
  (let [attr-col-ident           (heql-md/attr-column-ident heql-meta-data attr-ident)
        attr-value               (heql-md/coarce-attr-value heql-meta-data attr-ident value)]
    [:= attr-col-ident attr-value]))

(defmethod eql->hsql :ident-join [heql-meta-data eql-node]
  (let [{:keys [key children]}   eql-node]
    {:from   [(heql-md/entity-relation-ident heql-meta-data (first key))]
     :where  (eql-ident-key->hsql-predicate heql-meta-data key)
     :select (select-clause heql-meta-data children)}))

(defmulti execute-query (fn [db-spec heql-meta-data hsql]
                          (get-in heql-meta-data [:db-config :db-product-name])))

(def ^:private default-heql-config {:attribute {:return-as :qualified-kebab-case}})
(defonce ^:private global-db-spec (atom nil))
(defonce ^:private global-heql-meta-data (atom nil))
(defonce ^:private global-heql-config (atom nil))

(defn initialize!
  ([db-spec]
   (initialize! db-spec default-heql-config))
  ([db-spec heql-config]
   (reset! global-db-spec db-spec)
   (reset! global-heql-meta-data (heql-md/fetch db-spec))
   (swap! global-heql-config merge heql-config)))

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

(defn query
  ([eql-query]
   (query @global-db-spec @global-heql-config @global-heql-meta-data eql-query))
  ([db-spec heql-config heql-meta-data eql-query]
   (let [hsql           (eql->hsql heql-meta-data (eql/query->ast eql-query))
         attr-return-as (get-in heql-config [:attribute :return-as])]
     (tap> {:hsql hsql})
     (map  #(transform-keys attr-return-as %)
           (json/read-str (execute-query db-spec heql-meta-data hsql)
                          :bigdec true
                          :key-fn #(json-key-fn attr-return-as %)
                          :value-fn #(json-value-fn heql-meta-data attr-return-as %1 %2))))))

(defn query-single
  ([eql-query]
   (first (query eql-query)))
  ([db-spec heql-config heql-meta-data eql-query]
   (first (query db-spec heql-config heql-meta-data eql-query))))