(ns playground
  (:require [honeyeql.core :as heql]
            [honeyeql.db :as heql-db]))

(def logs (atom {}))

(defn- logger [x]
  (swap! logs merge x))

(defn- reset-logger []
  (reset! logs {})
  (remove-tap logger)
  (add-tap logger))

(comment 
  (def pg-adapter (heql-db/initialize {:dbtype   "postgres"
                                       :dbname   "sakila"
                                       :user     "postgres"
                                       :password "postgres"}))
  (heql/query-single pg-adapter
                     {[:language/language-id 2]
                      [:language/name
                       {:language/films [:film/title]}]})
  )