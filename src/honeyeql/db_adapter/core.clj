(ns honeyeql.db-adapter.core)

(defprotocol DbAdapter
  (to-sql [db-adapter hsql])
  (select-clause [db-adapter heql-meta-data eql-nodes])
  (coerce-date-time [db-adapter value])
  (resolve-one-to-one-relationship [db-adapter heql-meta-data hsql eql-node])
  (resolve-children-one-to-one-relationships [db-adapter heql-meta-data hsql eql-nodes])
  (resolve-one-to-many-relationship [db-adapter heql-meta-data hsql eql-node])
  (resolve-many-to-many-relationship [db-adapter heql-meta-data hsql eql-node]))