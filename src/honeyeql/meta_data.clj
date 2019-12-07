(ns honeyeql.meta-data
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [inflections.core :as inf]))

(defn- meta-data-result [db-spec result-set]
  (rs/datafiable-result-set result-set db-spec {:builder-fn rs/as-unqualified-lower-maps}))

(defn- coarce-boolean [bool-str]
  (case bool-str
    "YES" true
    "NO" false))

(defn- entity-ident [db-config {:keys [table_schem table_name]}]
  (if (= (get-in db-config [:schema :default]) table_schem)
    (keyword (inf/singular (inf/hyphenate table_name)))
    (keyword (inf/hyphenate table_schem) (inf/hyphenate table_name))))

(defn- attribute-ident 
  ([db-config {:keys [table_schem table_name column_name]}]
   (attribute-ident db-config table_schem table_name column_name))
  ([db-config table_schem table_name column_name]
   (if (= (get-in db-config [:schema :default]) table_schem)
     (keyword (inf/singular (inf/hyphenate table_name)) (inf/hyphenate column_name))
     (keyword (str (inf/hyphenate table_schem) "." (inf/singular (inf/hyphenate table_name)))
              (inf/hyphenate column_name)))))

(defn- column-ident [db-config {:keys [table_schem table_name column_name]}]
  (if (= (get-in db-config [:schema :default]) table_schem)
    (keyword (str table_name "." column_name))
    (keyword (str table_schem "." table_name "." column_name))))


(defn- to-entity-meta-data [db-config {:keys [remarks table_type table_schem table_name]
                                       :as   table-meta-data}]
  (let [ident (entity-ident db-config table-meta-data)]
    [ident
     {:entity/doc             remarks
      :entity/ident           ident
      :entity.relation/type   (case table_type
                                "TABLE" :table
                                "VIEW" :view)
      :entity.relation/schema table_schem
      :entity.relation/name   table_name
      :entity.relation/ident  (keyword (str table_schem "." table_name))}]))

(defn- entities-meta-data [db-spec jdbc-meta-data db-config]
  (->> (into-array String ["TABLE" "VIEW"])
       (.getTables jdbc-meta-data nil "%" nil)
       (meta-data-result db-spec)
       (map #(to-entity-meta-data db-config %))
       (into {})))

(defn- filter-columns [db-config columns]
  (remove #(contains? (get-in db-config [:schema :ignore]) (:table_schem %)) columns))

(defmulti derive-attr-type
  (fn [db-config _]
    (:db-product-name db-config)))

(defn- add-attribute-meta-data [db-config heql-meta-data
                                {:keys [table_schem table_name column_name data_type column_size
                                        remarks is_nullable is_autoincrement type_name ordinal_position]
                                 :as   column-meta-data}]
  (let [attr-ident            (attribute-ident db-config column-meta-data)
        entity-ident          (entity-ident db-config column-meta-data)
        is-nullable           (coarce-boolean is_nullable)
        entity-attr-qualifier (if is-nullable
                                :entity/opt-attrs
                                :entity/req-attrs)]
    (update-in
     (assoc-in heql-meta-data [:attributes attr-ident]
               {:attr/ident                     attr-ident
                :attr/doc                       remarks
                :attr/type                      (derive-attr-type db-config column-meta-data)
                :attr/nullable                  is-nullable
                :attr.column/name               column_name
                :attr.column/schema             table_schem
                :attr.column/relation           table_name
                :attr.column/size               column_size
                :attr.column/auto-incrementable (coarce-boolean is_autoincrement)
                :attr.column/jdbc-type          data_type
                :attr.column/db-type            type_name
                :attr.column/ident              (column-ident db-config column-meta-data)
                :attr.column/ordinal-position   ordinal_position})
     [:entities entity-ident entity-attr-qualifier]
     conj attr-ident)))

(defn- add-attributes-meta-data [db-spec jdbc-meta-data db-config heql-meta-data]
  (->> (.getColumns jdbc-meta-data nil "%" "%" nil)
       (meta-data-result db-spec)
       (filter-columns db-config)
       (reduce (partial add-attribute-meta-data db-config) heql-meta-data)))

(defn- add-primary-keys-meta-data [db-spec jdbc-meta-data db-config heql-meta-data]
  (let [schemas-to-ignore (get-in db-config [:schema :ignore])]
    (->> (.getPrimaryKeys jdbc-meta-data nil "" nil)
         (meta-data-result db-spec)
         (remove #(contains? schemas-to-ignore (:table_schem %)))
         (group-by (fn [{:keys [table_schem table_name]}]
                     [table_schem table_name]))
         (reduce (fn [pks [[table_schem table_name] v]]
                   (assoc pks
                          (entity-ident db-config {:table_schem table_schem
                                                   :table_name  table_name})
                          {:entity.relation/primary-key {:primary-key/name  (:pk_name (first v))
                                                         :primary-key/attrs (set (map #(attribute-ident db-config %) v))}})) {})
         (merge-with merge (:entities heql-meta-data))
         (assoc heql-meta-data :entities))))

(defmulti get-db-config identity)

(defn- foreign-key-column-to-attr-name [{:keys [foreign-key-suffix]} fkcolumn_name]
  (if foreign-key-suffix
    (clojure.string/replace fkcolumn_name (re-pattern (str foreign-key-suffix "$")) "")
    fkcolumn_name))

(defn- add-relationships-meta-data [db-spec jdbc-meta-data db-config hql-meta-data]
  (->> (.getImportedKeys jdbc-meta-data nil "" nil)
       (meta-data-result db-spec)
       (reduce (fn [refs {:keys [fktable_schem fktable_name fkcolumn_name
                                 pktable_schem pktable_name pkcolumn_name]}]
                 (assoc refs
                        (attribute-ident db-config fktable_schem fktable_name (foreign-key-column-to-attr-name db-config fkcolumn_name))
                        {:attr/type :attr.type/ref
                         :attr.ref/cardinality :attr.cardinality/one
                         :attr.ref/left  (attribute-ident db-config fktable_schem fktable_name fkcolumn_name)
                         :attr.ref/right (attribute-ident db-config pktable_schem pktable_name pkcolumn_name)})) {})
       (merge-with merge (:attributes hql-meta-data))
       (assoc hql-meta-data :attributes)))

(defn fetch [db-spec]
  (with-open [conn (jdbc/get-connection db-spec)]
    (let [jdbc-meta-data  (.getMetaData conn)
          db-product-name (.getDatabaseProductName jdbc-meta-data)
          db-config       (assoc (get-db-config db-product-name) :db-product-name db-product-name)]
      (->> (entities-meta-data db-spec jdbc-meta-data db-config)
           (hash-map :entities)
           (add-attributes-meta-data db-spec jdbc-meta-data db-config)
           (add-primary-keys-meta-data db-spec jdbc-meta-data db-config)
           (add-relationships-meta-data db-spec jdbc-meta-data db-config)))))