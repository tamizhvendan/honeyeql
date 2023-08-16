(ns playground
  (:require [honeyeql.core :as heql]
            [honeyeql.mutation :as hm]
            [honeyeql.db :as heql-db]
            [portal.api :as p]
            [edn-query-language.core :as eql]
            [clojure.spec.alpha :as s]
            [next.jdbc :as jdbc]))

(def logs (atom {}))

(defn- logger [x]
  (swap! logs merge x))

(defn- reset-logger []
  (reset! logs {})
  (remove-tap logger)
  (add-tap logger))

(defn- setup-portal [x]
  (add-tap #'p/submit)
  (tap> x)
  (p/open {:launcher :vs-code}))

(comment
  (reset-logger)

  (def pg-adapter (heql-db/initialize {:dbtype   "postgres"
                                       :dbname   "honeyeql"
                                       :user     "postgres"
                                       :password "postgres"}))

  (def mysql-adapter (heql-db/initialize {:dbtype   "mysql"
                                          :dbname   "honeyeql"
                                          :user     "root"
                                          :password "mysql123"}))
  
  )