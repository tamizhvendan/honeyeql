(ns honeyeql.meta-data
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [inflections.core :as inf]))

#_(def db-spec {:dbtype   "postgresql"
                :dbname   "invoice-app"
                :user     "postgres"
                :password "postgres"})

(defn- meta-data-result [db-spec result-set]
  (rs/datafiable-result-set result-set db-spec {:builder-fn rs/as-unqualified-lower-maps}))

(defn- db-ident
  ([schema relation]
    (keyword (inf/hyphenate schema) (inf/hyphenate relation))))

#_ (db-ident "public" "user")
#_ (db-ident "public" "invoice_tax")
#_ (db-ident "meta_data" "invoice_tax")

(defn- to-entity-meta-data [{:keys [remarks table_type table_schem table_name]}]
  [(db-ident table_schem table_name)
   {:db/doc         remarks
    :db/ident       (db-ident table_schem table_name)
    :db.entity/type (case table_type
                      "TABLE" :table
                      "VIEW" :view)
    :db/schema      table_schem
    :db/relation    table_name}])

#_ (inf/hyphenate "meta_data")

(defn- entities-meta-data [db-spec jdbc-meta-data]
  (->> (into-array String ["TABLE" "VIEW"])
       (.getTables jdbc-meta-data nil "%" nil)
       (meta-data-result db-spec)
       (map to-entity-meta-data)
       (into {})))

(defn fetch [db-spec]
  (with-open [conn (jdbc/get-connection db-spec)]
    (let [jdbc-meta-data (.getMetaData conn)]
      (entities-meta-data db-spec jdbc-meta-data))))

#_(fetch db-spec)

#_{:public/user         {:db/doc         nil
                         :db/ident       :public/user
                         :db.entity/type :table
                         :db/schema      "public"
                         :db/relation    "user"}
   :public/invoice-item {:db/doc         nil
                         :db/ident       :public/invoice-item
                         :db.entity/type :table
                         :db/schema      "public"
                         :db/relation    "invoice_item"}}