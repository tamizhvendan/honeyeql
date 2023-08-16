(ns sample
  (:require [honeyeql.core :as heql]
            [honeyeql.mutation :as hm]
            [honeyeql.db :as heql-db]))


(comment

  (def pg-adapter (heql-db/initialize {:dbtype   "postgres"
                                       :dbname   "honeyeql"
                                       :host "localhost"
                                       :port 5432
                                       :user     "postgres"
                                       :password "postgres"}))

  (def mysql-adapter (heql-db/initialize {:dbtype   "mysql"
                                          :dbname   "honeyeql"
                                          :port 3306
                                          :user     "root"
                                          :password "mysql123"}))
  
  
  (heql/query pg-adapter {[:country/id 1] [:country/name]})
  (hm/insert! pg-adapter #:country{:id 3 :name "Sri Lanka" :continent_identifier 1}))
