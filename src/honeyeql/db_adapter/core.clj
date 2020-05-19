(ns ^:no-doc honeyeql.db-adapter.core)

(defprotocol DbAdapter
  (to-sql [db-adapter hsql])
  (query [db-adapter sql])
  (select-clause [db-adapter heql-meta-data eql-nodes])
  (coerce [db-adapter value target-type])
  (resolve-one-to-one-relationship [db-adapter heql-meta-data hsql eql-node])
  (resolve-one-to-one-relationship-alias [db-adapter alias])
  (resolve-children-one-to-one-relationships [db-adapter heql-meta-data hsql eql-nodes])
  (resolve-one-to-many-relationship [db-adapter heql-meta-data hsql eql-node])
  (resolve-many-to-many-relationship [db-adapter heql-meta-data hsql eql-node]))