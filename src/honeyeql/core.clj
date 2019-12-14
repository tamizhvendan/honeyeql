(ns honeyeql.core
  (:require [edn-query-language.core :as eql]
            [honeyeql.meta-data :as heql-md]))

(defn- find-join-type [eql-node]
  (let [{node-type :type
         node-key  :key} eql-node]
    (cond
      (= :root node-type) :root
      (and (= :join node-type) (seq node-key) (even? (count node-key))) :ident-join)))

(defmulti eql->hsql (fn [heql-meta-data eql-node] (find-join-type eql-node)))

(defmethod eql->hsql :root [heql-meta-data eql-node]
  (eql->hsql heql-meta-data (first (:children eql-node))))

(defn- eql-ident-key->hsql-predicate [heql-meta-data [attr-ident value]]
  (let [attr-col-ident (heql-md/attr-column-ident heql-meta-data attr-ident)
        attr-value (heql-md/coarce-attr-value heql-meta-data attr-ident value)]
    [:= attr-col-ident attr-value]))

(defmethod eql->hsql :ident-join [heql-meta-data eql-node]
  (let [{:keys [key children]} eql-node]
    {:from (heql-md/entity-relation-ident heql-meta-data (first key))
     :where (eql-ident-key->hsql-predicate heql-meta-data key)}))

(defn query [db-spec heql-meta-data eql-query]
  (let [heql (eql->hsql heql-meta-data (eql/query->ast eql-query))]
    heql))