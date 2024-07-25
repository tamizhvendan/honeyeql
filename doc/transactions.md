# Transactions

If the database supports transactions, start a transaction with next.jdbc and use it with `use-tx`.

For example:

```clojure
(require '[next.jdbc :as jdbc])
(require '[honeyeql.core :as heql])
;; get existing db-spec to restore at end of transaction
(let [db-spec (heql/db-spec db-adapter)]
  ;; start transaction
  (jdbc/with-transaction [tx db-spec]
    (try
      ;; use transaction with honeyeql
      (heql/use-tx db-adapter tx)
      ;; perform database actions
      ,,,
      (finally
        ;; restore db-spec after transaction
        (heql/use-tx db-adapter db-spec)))))
```
