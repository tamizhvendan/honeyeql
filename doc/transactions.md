# Transactions

If the database supports transactions, start a transaction with next.jdbc and use it with `use-tx`.

For example:

```clojure
(require '[next.jdbc :as jdbc])
(require '[honeyeql.core :as heql])
;; start transaction
(jdbc/with-transaction [tx db-spec]
  ;; use transaction with honeyeql
  (let [tx-aware-db-adapter (heql/use-tx db-adapter tx)]
    (honeyeql.mutation/insert! tx-aware-db-adapter {...})
    (honeyeql.mutation/update! tx-aware-db-adapter {...})))
```
