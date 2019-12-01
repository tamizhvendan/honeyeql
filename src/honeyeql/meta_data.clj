(ns honeyeql.meta-data
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [inflections.core :as inf]))

(defn- meta-data-result [db-spec result-set]
  (rs/datafiable-result-set result-set db-spec {:builder-fn rs/as-unqualified-lower-maps}))

(defn- entity-ident [db-config {:keys [table_schem table_name]}]
  (if (= (get-in db-config [:schema :default]) table_schem)
    (keyword (inf/hyphenate table_name))
    (keyword (inf/hyphenate table_schem) (inf/hyphenate table_name))))

(defn- attribute-ident [db-config {:keys [table_schem table_name column_name]}]
  (if (= (get-in db-config [:schema :default]) table_schem)
    (keyword (inf/hyphenate table_name) (inf/hyphenate column_name))
    (keyword (str (inf/hyphenate table_schem) "." (inf/hyphenate table_name)) (inf/hyphenate column_name))))

(defn- column-ident [db-config {:keys [table_schem table_name column_name]}]
  (if (= (get-in db-config [:schema :default]) table_schem)
    (keyword (str table_name "." column_name))
    (keyword (str table_schem "." table_name "." column_name))))

(defn- to-entity-meta-data [db-config {:keys [remarks table_type table_schem table_name]
                                       :as   table-meta-data}]
  (let [ident (entity-ident db-config table-meta-data)]
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

#_(filter-columns {:schema {:ignore #{"information_schema"}}}
                  [{:table_schem "information_schema"}
                   {:table_schem "public"}])

(defn- to-attribute-meta-data [db-config column-meta-data]
  (let [{:keys [table_schem table_name column_name]} column-meta-data
        ident                                        (attribute-ident db-config column-meta-data)]
    [ident {:db/ident           ident
            :db.column/name     column_name
            :db.column/schema   table_schem
            :db.column/relation table_name
            :db.column/ident    (column-ident db-config column-meta-data)}]))

(defn- add-attributes-meta-data [db-spec jdbc-meta-data db-config heql-meta-data]
  (->> (.getColumns jdbc-meta-data nil "%" "%" nil)
       (meta-data-result db-spec)
       (filter-columns db-config)
       (map #(to-attribute-meta-data db-config %))
       (into {})
       (assoc heql-meta-data :attributes)))

(defn fetch [db-spec db-config]
  (with-open [conn (jdbc/get-connection db-spec)]
    (let [jdbc-meta-data     (.getMetaData conn)
          entities-meta-data (entities-meta-data db-spec jdbc-meta-data db-config)
          heql-meta-data     {:entities entities-meta-data}]
      #_(spit "./dev/attributes.edn"
              (:attributes (add-attributes-meta-data db-spec jdbc-meta-data db-config heql-meta-data)))
      (add-attributes-meta-data db-spec jdbc-meta-data db-config heql-meta-data))))

#_(def db-spec {:dbtype   "postgresql"
                :dbname   "invoice-app"
                :user     "postgres"
                :password "postgres"})

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