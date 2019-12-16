(ns honeyeql.core
  (:require [edn-query-language.core :as eql]
            [honeyeql.meta-data :as heql-md]
            [cheshire.core :as json]
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

(defn- select-clause [heql-context eql-nodes]
  (let [{:keys [heql-meta-data heql-config]} heql-context
        {:keys [attribute]}                  heql-config
        attr-idents                          (eql-nodes->attr-idents eql-nodes)
        attr-column-idents                   (map (fn [attr-ident]
                                                    [(heql-md/attr-column-ident heql-meta-data attr-ident)
                                                     (column-alias (attribute :return-as) attr-ident)]) attr-idents)]
    (vec attr-column-idents)))

(defmulti eql->hsql (fn [heql-context eql-node] (find-join-type eql-node)))

(defmethod eql->hsql :root [heql-context eql-node]
  (eql->hsql heql-context (first (:children eql-node))))

(defn- eql-ident-key->hsql-predicate [heql-context [attr-ident value]]
  (let [{:keys [heql-meta-data]} heql-context
        attr-col-ident           (heql-md/attr-column-ident heql-meta-data attr-ident)
        attr-value               (heql-md/coarce-attr-value heql-meta-data attr-ident value)]
    [:= attr-col-ident attr-value]))

(defmethod eql->hsql :ident-join [heql-context eql-node]
  (let [{:keys [heql-meta-data]} heql-context
        {:keys [key children]}   eql-node]
    {:from   [(heql-md/entity-relation-ident heql-meta-data (first key))]
     :where  (eql-ident-key->hsql-predicate heql-context key)
     :select (select-clause heql-context children)}))

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

(defn query
  ([eql-query]
   (query @global-db-spec @global-heql-config @global-heql-meta-data eql-query))
  ([db-spec heql-config heql-meta-data eql-query]
   (let [heql-context {:heql-config    (merge default-heql-config heql-config)
                       :heql-meta-data heql-meta-data}
         hsql         (eql->hsql heql-context (eql/query->ast eql-query))]
     (tap> {:hsql hsql})
     (json/parse-string (execute-query db-spec heql-meta-data hsql) true))))

(defn query-single
  ([eql-query]
   (first (query eql-query)))
  ([db-spec heql-config heql-meta-data eql-query]
   (first (query db-spec heql-meta-data heql-config eql-query))))