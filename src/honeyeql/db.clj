(ns honeyeql.db
  (:require [honeyeql.core :as heql]
            [honeyeql.meta-data :as heql-md]
            [honeyeql.db-adapter.postgres :as heql-pg]
            [honeyeql.db-adapter.mysql :as heql-mysql]))

(defn initialize
  ([db-spec]
   (initialize db-spec heql/default-heql-config))
  ([db-spec heql-config]
   (let [heql-cfg        (merge heql/default-heql-config heql-config)
         heql-meta-data  (heql-md/fetch db-spec)
         db-product-name (get-in heql-meta-data [:db-config :db-product-name])
         db-init-params  {:db-spec        db-spec
                          :heql-config    heql-cfg
                          :heql-meta-data heql-meta-data}]
     (case db-product-name
       "PostgreSQL" (heql-pg/map->PostgresAdapter db-init-params)
       "MySQL" (heql-mysql/map->MySqlAdapter db-init-params)))))