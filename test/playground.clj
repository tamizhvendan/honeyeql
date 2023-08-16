(ns playground
  (:require [honeyeql.core :as heql]
            [honeyeql.db :as heql-db]
            [next.jdbc :as jdbc]
            [honey.sql :as sql]))

(def logs (atom {}))

(defn- logger [x]
  (swap! logs merge x))

(defn- reset-logger []
  (reset! logs {})
  (remove-tap logger)
  (add-tap logger))

(comment
  (reset-logger)
  (def pg-adapter (heql-db/initialize {:dbtype   "postgres"
                                       :dbname   "honeyeql"
                                       :user     "postgres"
                                       :password "postgres"}
                                      {:attr/aggregate-attr-convention
                                       :aggregate-attr-naming-convention/vector}))
  
    (def mysql-adapter (heql-db/initialize {:dbtype   "mysql"
                                          :dbname   "honeyeql"
                                          :user     "root"
                                          :password "mysql123"}))
  
  (sql/format {:select [[[:count [:distinct :G__14491.rating]] "course/count-of-rating"]]
               :from [[:course :G__14491]]})

  (:sql @logs)

  (:hsql @logs)

  (heql/query-single
   mysql-adapter
   {[] [[[:count :course/rating] :as :course/total-rating]
        [[:count [:distinct :course/rating]] :as :course/distinct-ratings]
        [[:avg :course/rating] :as :course/average-rating]
        [[:max :course/rating] :as :course/max-rating]
        [[:min :course/rating] :as :course/min-rating]]})
    
    (heql/query-single
     pg-adapter
     {[] [[:count [:distinct :course/rating]]]})

  (heql/query-single mysql-adapter {[] [[:count [:distinct :course/rating]]]})
  )