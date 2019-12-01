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
  ([relation]
   (keyword (inf/hyphenate relation)))
  ([schema relation]
   (keyword (inf/hyphenate schema) (inf/hyphenate relation))))

#_(db-ident "public" "user")
#_(db-ident "public" "invoice_tax")
#_(db-ident "meta_data" "invoice_tax")

(defn- to-entity-meta-data [{:keys [schema]}
                            {:keys [remarks table_type table_schem table_name]}]
  (let [ident (if (= (:default schema) table_schem)
                (db-ident table_name)
                (db-ident table_schem table_name))]
    [ident
     {:db/doc         remarks
      :db/ident       ident
      :db.entity/type (case table_type
                        "TABLE" :table
                        "VIEW" :view)
      :db/schema      table_schem
      :db/relation    table_name}]))

#_(inf/hyphenate "meta_data")

(defn- entities-meta-data [db-spec jdbc-meta-data db-config]
  (->> (into-array String ["TABLE" "VIEW"])
       (.getTables jdbc-meta-data nil "%" nil)
       (meta-data-result db-spec)
       (map #(to-entity-meta-data db-config %))
       (into {})))


(defn- filter-columns [db-config columns]
  (remove #(contains? (get-in db-config [:schema :ignore]) (:table_schem %)) columns))

#_ (filter-columns {:schema {:ignore #{"information_schema"}}} 
                   [{:table_schem "information_schema"}
                    {:table_schem "public"}]) 

(defn- add-attributes-meta-data [db-spec jdbc-meta-data db-config heql-meta-data]
  (->> (.getColumns jdbc-meta-data nil "%" "%" nil)
       (meta-data-result db-spec)
       (filter-columns db-config)
       (assoc heql-meta-data :attributes)))

(defn fetch [db-spec db-config]
  (with-open [conn (jdbc/get-connection db-spec)]
    (let [jdbc-meta-data     (.getMetaData conn)
          entities-meta-data (entities-meta-data db-spec jdbc-meta-data db-config)
          heql-meta-data     {:entities entities-meta-data}]
      (spit "./dev/columns.edn" (vec (:attributes (add-attributes-meta-data db-spec jdbc-meta-data heql-meta-data db-config)))))))

#_(fetch db-spec {:schema {:default "public"
                           :ignore  #{"information_schema" "pg_catalog"}}})

#_{:user         {:db/doc         nil
                  :db/ident       :public/user
                  :db.entity/type :table
                  :db/schema      "public"
                  :db/relation    "user"}
   :invoice-item {:db/doc         nil
                  :db/ident       :public/invoice-item
                  :db.entity/type :table
                  :db/schema      "public"
                  :db/relation    "invoice_item"}}

(comment
  (def columns (read-string (slurp "./dev/columns.edn")))
  (distinct (keys (group-by :table_schem columns))))