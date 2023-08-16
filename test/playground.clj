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
                                       :dbname   "honeyeql"
                                       :user     "postgres"
                                       :password "postgres"}))
  
  (heql/query pg-adapter {[] [[[:trim :customer/first-name] :as :customer/first-name]]})
  
  (heql/query-single pg-adapter {[] [[[:count :course/rating] :as :course/total-ratings]]})
  )