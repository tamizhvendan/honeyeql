(ns playground
  (:require [honeyeql.core :as heql]
            [honeyeql.db :as heql-db]
            [next.jdbc :as jdbc]))

(def logs (atom {}))

(defn- logger [x]
  (swap! logs merge x))

(defn- reset-logger []
  (reset! logs {})
  (remove-tap logger)
  (add-tap logger))

(comment 
  (def pg-adapter (heql-db/initialize {:dbtype   "postgres"
                                       :dbname   "temp"
                                       :user     "postgres"
                                       :password "postgres"}))
  )