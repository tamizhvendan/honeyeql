(ns ^:no-doc honeyeql.meta-data
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [inflections.core :as inf]
            [clojure.set :as set]
            [clojure.string :as string]
            [honeyeql.db-adapter.core :as db]
            [honeyeql.debug :refer [trace>>]])
  (:import [java.time OffsetDateTime LocalDateTime LocalDate LocalTime OffsetTime]))

(defn datafied-result-set [db-spec result-set]
  (rs/datafiable-result-set result-set db-spec {:builder-fn rs/as-unqualified-lower-maps}))

(defn- coerce-boolean [bool-str]
  (case bool-str
    "YES" true
    "NO" false))

(defn- default-schema? [db-config table-schema]
  (or (nil? table-schema) (= (get-in db-config [:schema :default]) table-schema)))

(defn- entity-ident
  ([db-config {:keys [table_schem table_name]}]
   (entity-ident db-config table_schem table_name))
  ([db-config table_schem table_name]
   (if (default-schema? db-config table_schem)
     (keyword (inf/singular (inf/hyphenate table_name)))
     (keyword (inf/hyphenate table_schem) (inf/singular (inf/hyphenate table_name))))))

(defn- entity-ident-in-pascal-case [entity-ident]
  (if-let [schema (namespace entity-ident)]
    (keyword (inf/camel-case (str schema "-" (name entity-ident))))
    (keyword (inf/camel-case (name entity-ident)))))

(defn- entity-ident-in-camel-case [entity-ident]
  (if-let [schema (namespace entity-ident)]
    (keyword (inf/camel-case (str schema "-" (name entity-ident)) :lower))
    (keyword (inf/camel-case (name entity-ident) :lower))))

(defn- pluralize-entity-ident [entity-ident]
  (if-let [schema (namespace entity-ident)]
    (keyword (inf/camel-case (str schema "-" (inf/plural (name entity-ident))) :lower))
    (keyword (inf/camel-case (inf/plural (name entity-ident)) :lower))))

(defn- relation-ident
  ([db-config {:keys [table_schem table_name]}]
   (if (default-schema? db-config table_schem)
     (keyword table_name)
     (keyword (str (inf/hyphenate table_schem) "." (inf/hyphenate table_name))))))

(defn- attribute-ident
  ([db-config {:keys [table_schem table_name column_name]}]
   (attribute-ident db-config table_schem table_name column_name))
  ([db-config table_schem table_name column_name]
   (if (default-schema? db-config table_schem)
     (keyword (inf/singular (inf/hyphenate table_name)) (inf/hyphenate column_name))
     (keyword (str (inf/hyphenate table_schem) "." (inf/singular (inf/hyphenate table_name)))
              (inf/hyphenate column_name)))))

(defn- attribute-ident-in-camel-case [attr-ident]
  (keyword (inf/camel-case (name attr-ident) :lower)))

(defn- column-ident [db-config {:keys [table_schem table_name column_name relationship-column]}]
  (let [col-name-separator (if relationship-column "_" ".")]
    (if (default-schema? db-config table_schem)
      (keyword (str table_name col-name-separator column_name))
      (keyword (str table_schem "." table_name col-name-separator column_name)))))

(defn- to-entity-meta-data [db-config {:keys [remarks table_type table_schem table_name]
                                       :as   table-meta-data}]
  (let [ident (entity-ident db-config table-meta-data)]
    [ident
     {:entity/doc                   remarks
      :entity/ident                 ident
      :entity.ident/pascal-case     (entity-ident-in-pascal-case ident)
      :entity.ident/camel-case      (entity-ident-in-camel-case ident)
      :entity.ident/plural          (pluralize-entity-ident ident)
      :entity.relation/type         (case table_type
                                      "TABLE" :table
                                      "VIEW" :view
                                      "MATERIALIZED VIEW" :materialized-view)
      :entity.relation/schema       table_schem
      :entity.relation/name         table_name
      :entity/opt-attrs             #{}
      :entity/req-attrs             #{}
      :entity.relation/foreign-keys #{}
      :entity/attrs                 #{}
      :entity/rel-attrs             #{}
      :entity.relation/ident        (relation-ident db-config table-meta-data)}]))

(defn- entities-meta-data [db-config db-meta-data]
  (->> (:entities db-meta-data)
       (map #(to-entity-meta-data db-config %))
       (into {})))

(defn- schemas-to-ignore [db-config]
  (get-in db-config [:schema :ignore]))

(defn- filter-columns [db-config columns]
  (remove #(contains? (schemas-to-ignore db-config) (:table_schem %)) columns))

(defmulti derive-attr-type
  (fn [db-config column-meta-data]
    (:db-product-name db-config)))

(defmulti get-db-meta-data
  (fn [db-config db-spec db-conn]
    (:db-product-name db-config)))

(defn- add-attribute-meta-data [db-config heql-meta-data
                                {:keys [table_schem table_name column_name data_type column_size
                                        remarks is_nullable is_autoincrement type_name ordinal_position]
                                 :as   column-meta-data}]
  (let [attr-ident                (attribute-ident db-config column-meta-data)
        entity-ident              (entity-ident db-config column-meta-data)
        is-nullable               (coerce-boolean is_nullable)
        entity-req-attr-qualifier (if is-nullable :entity/opt-attrs :entity/req-attrs)
        attr-type                 (derive-attr-type db-config column-meta-data)
        entity-attr-qualifier     (if-not (= :attr.type/ref attr-type) :entity/attrs :entity/rel-attrs)]
    (-> (assoc-in heql-meta-data [:attributes attr-ident]
                  {:attr/ident                     attr-ident
                   :attr.ident/camel-case          (attribute-ident-in-camel-case attr-ident)
                   :attr/doc                       remarks
                   :attr/type                      attr-type
                   :attr/nullable                  is-nullable
                   :attr.column/name               column_name
                   :attr.column/schema             table_schem
                   :attr.column/relation           table_name
                   :attr.column/size               column_size
                   :attr.column/auto-incrementable (coerce-boolean is_autoincrement)
                   :attr.column/jdbc-type          data_type
                   :attr.column/db-type            type_name
                   :attr.column/ident              (column-ident db-config column-meta-data)
                   :attr.column/ordinal-position   ordinal_position
                   :attr.entity/ident              entity-ident})
        (update-in [:entities entity-ident entity-req-attr-qualifier] conj attr-ident)
        (update-in [:entities entity-ident entity-attr-qualifier] conj attr-ident))))

(defn- add-attributes-meta-data [db-meta-data {:keys [db-config]
                                               :as   heql-meta-data}]
  (->> (:attributes db-meta-data)
       (filter-columns db-config)
       (reduce (partial add-attribute-meta-data db-config) heql-meta-data)))

(defn- add-primary-keys-meta-data [db-meta-data {:keys [db-config]
                                                 :as   heql-meta-data}]
  (->> (:primary-keys db-meta-data)
       (remove #(contains? (schemas-to-ignore db-config) (:table_schem %)))
       (group-by (fn [{:keys [table_schem table_name]}]
                   [table_schem table_name]))
       (reduce (fn [pks [[table_schem table_name] v]]
                 (assoc pks
                        (entity-ident db-config table_schem table_name)
                        {:entity.relation/primary-key {:entity.relation.primary-key/name  (:pk_name (first v))
                                                       :entity.relation.primary-key/attrs (set (map #(attribute-ident db-config %) v))}})) {})
       (update heql-meta-data :entities (partial merge-with merge))))

(defmulti get-db-config identity)

(defn- foreign-key-column->attr-name [{:keys [foreign-key-suffix]} fkcolumn_name]
  (string/replace fkcolumn_name (re-pattern (str foreign-key-suffix "$")) ""))

(defn- one-to-many-attr-ident
  ([left-entity-ident right-entity-ident]
   (one-to-many-attr-ident left-entity-ident right-entity-ident nil))
  ([left-entity-ident right-entity-ident one-to-one-attr-ident]
   (let [attr-name (cond
                     (nil? one-to-one-attr-ident) (inf/plural (name left-entity-ident))
                     (= one-to-one-attr-ident (keyword (name left-entity-ident) (name right-entity-ident))) (inf/plural (name left-entity-ident))
                     :else (str (name one-to-one-attr-ident) "-" (inf/plural (name left-entity-ident))))]
     (if-let [e-ns (namespace right-entity-ident)]
       (keyword (str e-ns "." (name right-entity-ident)) attr-name)
       (keyword (name right-entity-ident) attr-name)))))

#_(one-to-many-attr-ident :film :language :film/language)
#_(one-to-many-attr-ident :film :language :film/original-language)
#_(one-to-many-attr-ident :a/film :a/language :aFilm/language)
#_(one-to-many-attr-ident :a/film :a/language :aFilm/original-language)

(defn- add-one-to-many-metadata [heql-meta-data
                                 {:keys [left-entity-ident left-attr-ident
                                         right-entity-ident right-attr-ident
                                         one-to-one-attr-ident]}]
  (let [one-to-many-attr-ident (one-to-many-attr-ident left-entity-ident right-entity-ident one-to-one-attr-ident)]
    (update-in (assoc-in heql-meta-data
                         [:attributes one-to-many-attr-ident]
                         {:attr/ident            one-to-many-attr-ident
                          :attr.ident/camel-case (attribute-ident-in-camel-case one-to-many-attr-ident)
                          :attr/type             :attr.type/ref
                          :attr/nullable         false
                          :attr.ref/cardinality  :attr.ref.cardinality/many
                          :attr.ref/type         left-entity-ident
                          :attr.entity/ident     right-entity-ident
                          :attr.column.ref/type  :attr.column.ref.type/one-to-many
                          :attr.column.ref/left  right-attr-ident
                          :attr.column.ref/right left-attr-ident})
               [:entities right-entity-ident :entity/req-attrs]
               conj one-to-many-attr-ident)))

(defn- add-one-to-one-metadata [heql-meta-data
                                {:keys [left-entity-ident left-attr-ident
                                        right-entity-ident right-attr-ident
                                        one-to-one-attr-ident]}]
  (let [one-to-many-attr-ident (one-to-many-attr-ident left-entity-ident right-entity-ident one-to-one-attr-ident)]
    (update-in (assoc-in heql-meta-data
                         [:attributes one-to-many-attr-ident]
                         {:attr/ident            one-to-many-attr-ident
                          :attr.ident/camel-case (attribute-ident-in-camel-case one-to-many-attr-ident)
                          :attr/type             :attr.type/ref
                          :attr/nullable         false
                          :attr.ref/cardinality  :attr.ref.cardinality/many
                          :attr.ref/type         left-entity-ident
                          :attr.entity/ident     right-entity-ident
                          :attr.column.ref/type  :attr.column.ref.type/one-to-many
                          :attr.column.ref/left  right-attr-ident
                          :attr.column.ref/right left-attr-ident})
               [:entities right-entity-ident :entity/req-attrs]
               conj one-to-many-attr-ident)))

(defn- one-to-one-attr-name-result [db-config {:keys [pktable_schem pktable_name fkcolumn_name]}]
  (let [n (foreign-key-column->attr-name db-config fkcolumn_name)]
    (if (= fkcolumn_name n)
      [n (-> (entity-ident db-config pktable_schem pktable_name)
             name
             (str "_by_" fkcolumn_name))]
      [n])))

(defn- one-to-one-relationship? [db-config heql-meta-data {:keys [fktable_schem fktable_name fkcolumn_name]}]
  (let [attr-ident (attribute-ident db-config fktable_schem fktable_name fkcolumn_name)
        e-ident (entity-ident db-config fktable_schem fktable_name)
        e-primary-keys (get-in heql-meta-data [:entities e-ident :entity.relation/primary-key :entity.relation.primary-key/attrs])]
    (= e-primary-keys #{attr-ident})))

(defn- add-fk-one-to-many-rel-metadata [db-config heql-meta-data
                                        {:keys [fktable_schem fktable_name fkcolumn_name fk_name
                                                pktable_schem pktable_name pkcolumn_name]
                                         :as   fk-md}]
  (let [[one-to-one-attr-name modified-one-to-one-attr-name]  (one-to-one-attr-name-result db-config fk-md)
        one-to-one-attr-ident (attribute-ident db-config fktable_schem fktable_name (if modified-one-to-one-attr-name
                                                                                      modified-one-to-one-attr-name
                                                                                      one-to-one-attr-name))
        ident-for-one-to-many-ident (attribute-ident db-config fktable_schem fktable_name one-to-one-attr-name)
        left-attr-ident       (attribute-ident db-config fktable_schem fktable_name fkcolumn_name)
        left-entity-ident     (entity-ident db-config fktable_schem fktable_name)
        right-attr-ident      (attribute-ident db-config pktable_schem pktable_name pkcolumn_name)
        right-entity-ident    (entity-ident db-config pktable_schem pktable_name)
        is-nullable           (get-in heql-meta-data [:attributes left-attr-ident :attr/nullable])]
    (-> (assoc-in heql-meta-data [:attributes one-to-one-attr-ident]
                  {:attr/ident            one-to-one-attr-ident
                   :attr.ident/camel-case (attribute-ident-in-camel-case one-to-one-attr-ident)
                   :attr/type             :attr.type/ref
                   :attr/nullable         is-nullable
                   :attr.ref/cardinality  :attr.ref.cardinality/one
                   :attr.ref/type         right-entity-ident
                   :attr.entity/ident     left-entity-ident
                   :attr.column.ref/type  :attr.column.ref.type/one-to-one
                   :attr.column.ref/left  left-attr-ident
                   :attr.column.ref/right right-attr-ident
                   :attr.column/ident     (column-ident db-config {:table_schem         fktable_schem
                                                                   :table_name          fktable_name
                                                                   :column_name         one-to-one-attr-name
                                                                   :relationship-column true})})
        (update-in [:entities left-entity-ident (if is-nullable :entity/opt-attrs :entity/req-attrs)]
                   conj one-to-one-attr-ident)
        (update-in [:entities left-entity-ident :entity.relation/foreign-keys]
                   conj {:entity.relation.foreign-key/name      fk_name
                         :entity.relation.foreign-key/self-attr left-attr-ident
                         :entity.relation.foreign-key/ref-attr  right-attr-ident})
        (add-one-to-many-metadata {:left-entity-ident     left-entity-ident
                                   :left-attr-ident       left-attr-ident
                                   :right-entity-ident    right-entity-ident
                                   :right-attr-ident      right-attr-ident
                                   :one-to-one-attr-ident ident-for-one-to-many-ident}))))

(defn- add-fk-one-to-one-rel-metadata [db-config heql-meta-data {:keys [fktable_schem fktable_name fkcolumn_name fk_name
                                                                        pktable_schem pktable_name pkcolumn_name]}]
  (let [child-attr-ident (attribute-ident db-config fktable_schem fktable_name pktable_name)
        parent-attr-ident (attribute-ident db-config pktable_schem pktable_name fktable_name)
        left-attr-ident       (attribute-ident db-config fktable_schem fktable_name fkcolumn_name)
        left-entity-ident     (entity-ident db-config fktable_schem fktable_name)
        right-attr-ident      (attribute-ident db-config pktable_schem pktable_name pkcolumn_name)
        right-entity-ident    (entity-ident db-config pktable_schem pktable_name)]
    (-> (assoc-in heql-meta-data [:attributes child-attr-ident]
                  {:attr/ident            child-attr-ident
                   :attr.ident/camel-case (attribute-ident-in-camel-case child-attr-ident)
                   :attr/type             :attr.type/ref
                   :attr/nullable         false
                   :attr.ref/cardinality  :attr.ref.cardinality/one
                   :attr.ref/type         right-entity-ident
                   :attr.entity/ident     left-entity-ident
                   :attr.column.ref/type  :attr.column.ref.type/one-to-one
                   :attr.column.ref/left  left-attr-ident
                   :attr.column.ref/right right-attr-ident
                   :attr.column/ident     (column-ident db-config {:table_schem         fktable_schem
                                                                   :table_name          fktable_name
                                                                   :column_name         pktable_name
                                                                   :relationship-column true})})
        (update-in [:entities left-entity-ident :entity/req-attrs]
                   conj child-attr-ident)
        (update-in [:entities left-entity-ident :entity.relation/foreign-keys]
                   conj {:entity.relation.foreign-key/name      fk_name
                         :entity.relation.foreign-key/self-attr left-attr-ident
                         :entity.relation.foreign-key/ref-attr  right-attr-ident})
        (assoc-in [:attributes parent-attr-ident]
                  {:attr/ident            parent-attr-ident
                   :attr.ident/camel-case (attribute-ident-in-camel-case parent-attr-ident)
                   :attr/type             :attr.type/ref
                   :attr/nullable         true
                   :attr.ref/cardinality  :attr.ref.cardinality/one
                   :attr.ref/type         left-entity-ident
                   :attr.entity/ident     right-entity-ident
                   :attr.column.ref/type  :attr.column.ref.type/one-to-one
                   :attr.column.ref/left  right-attr-ident
                   :attr.column.ref/right left-attr-ident
                   :attr.column/ident     (column-ident db-config {:table_schem         pktable_schem
                                                                   :table_name          pktable_name
                                                                   :column_name         fktable_name
                                                                   :relationship-column true})})
        (update-in [:entities right-entity-ident :entity/opt-attrs]
                   conj parent-attr-ident)
        (update-in [:entities right-entity-ident :entity.relation/foreign-keys]
                   conj {:entity.relation.foreign-key/name      fk_name
                         :entity.relation.foreign-key/self-attr right-attr-ident
                         :entity.relation.foreign-key/ref-attr  left-attr-ident}))))

(defn- add-fk-rel-meta-data [db-config heql-meta-data fk-md]
  (if (one-to-one-relationship? db-config heql-meta-data fk-md)
    (add-fk-one-to-one-rel-metadata db-config heql-meta-data fk-md)
    (add-fk-one-to-many-rel-metadata db-config heql-meta-data fk-md)))

(defn- add-relationships-meta-data [db-meta-data {:keys [db-config]
                                                  :as   heql-meta-data}]
  (reduce
   #(merge-with merge %1 (add-fk-rel-meta-data db-config %1 %2))
   heql-meta-data
   (:foreign-keys db-meta-data)))

(defn- associative-entity? [{:entity.relation/keys [primary-key foreign-keys]}]
  (let [pk-attrs (:entity.relation.primary-key/attrs primary-key)
        fk-attrs (set (map :entity.relation.foreign-key/self-attr foreign-keys))]
    (and (= 2 (count pk-attrs))
         (set/subset? pk-attrs fk-attrs))))

(defn- associative-foreign-keys [{:entity.relation/keys [primary-key foreign-keys]}]
  (filter #(contains? (:entity.relation.primary-key/attrs primary-key)
                      (:entity.relation.foreign-key/self-attr %))
          foreign-keys))

(defn- add-many-to-many-relation-data [h-md entity-meta-data]
  (let [[l-fk-md r-fk-md]                                        (associative-foreign-keys entity-meta-data)
        {:entity.relation.foreign-key/keys [ref-attr self-attr]} l-fk-md
        entity-ident                                             (:entity/ident entity-meta-data)
        r-ref-attr                                               (:entity.relation.foreign-key/ref-attr r-fk-md)
        r-self-attr                                              (:entity.relation.foreign-key/self-attr r-fk-md)
        left-entity-ident                                        (get-in h-md [:attributes ref-attr :attr.entity/ident])
        right-entity-ident                                       (get-in h-md [:attributes r-ref-attr :attr.entity/ident])
        many-to-many-attr-ident                                  (one-to-many-attr-ident left-entity-ident right-entity-ident)
        many-to-many-rev-attr-ident                              (one-to-many-attr-ident right-entity-ident left-entity-ident)]
    (-> (update-in h-md [:entities entity-ident] assoc :entity/is-associative true)
        (assoc-in [:attributes many-to-many-attr-ident]
                  {:attr/ident                              many-to-many-attr-ident
                   :attr.ident/camel-case                   (attribute-ident-in-camel-case many-to-many-attr-ident)
                   :attr/type                               :attr.type/ref
                   :attr/nullable                           false
                   :attr.ref/cardinality                    :attr.ref.cardinality/many
                   :attr.ref/type                           left-entity-ident
                   :attr.entity/ident                       right-entity-ident
                   :attr.column.ref/type                    :attr.column.ref.type/many-to-many
                   :attr.column.ref/left                    r-ref-attr
                   :attr.column.ref.associative/ident       entity-ident
                   :attr.column.ref.associative/left-ident  r-self-attr
                   :attr.column.ref.associative/right-ident self-attr
                   :attr.column.ref/right                   ref-attr})
        (assoc-in [:attributes many-to-many-rev-attr-ident]
                  {:attr/ident                              many-to-many-rev-attr-ident
                   :attr.ident/camel-case                   (attribute-ident-in-camel-case many-to-many-rev-attr-ident)
                   :attr/type                               :attr.type/ref
                   :attr/nullable                           false
                   :attr.ref/cardinality                    :attr.ref.cardinality/many
                   :attr.ref/type                           right-entity-ident
                   :attr.entity/ident                       left-entity-ident
                   :attr.column.ref/type                    :attr.column.ref.type/many-to-many
                   :attr.column.ref/left                    ref-attr
                   :attr.column.ref.associative/ident       entity-ident
                   :attr.column.ref.associative/left-ident  self-attr
                   :attr.column.ref.associative/right-ident r-self-attr
                   :attr.column.ref/right                   r-ref-attr})
        (update-in [:entities left-entity-ident :entity/req-attrs]
                   conj many-to-many-rev-attr-ident)
        (update-in [:entities right-entity-ident :entity/req-attrs]
                   conj many-to-many-attr-ident))))

(defn- add-many-to-many-rels-meta-data [heql-meta-data]
  (reduce (fn [h-md [entity-ident entity-meta-data]]
            (if (associative-entity? entity-meta-data)
              (add-many-to-many-relation-data
               (update-in h-md [:entities entity-ident] assoc :entity/is-associative true)
               entity-meta-data)
              h-md))
          heql-meta-data
          (:entities heql-meta-data)))

(defn- namespace-ident [schema]
  (keyword (inf/hyphenate schema)))

(defn- add-namespaces [{:keys [db-config]
                        :as   heql-meta-data}]
  (let [default-schema (get-in db-config [:schema :default])]
    (->> (:entities heql-meta-data)
         (map (fn [[_ v]]
                (:entity.relation/schema v)))
         distinct
         (remove #(= default-schema %))
         (reduce (fn [sm s]
                   (if s
                     (assoc sm (namespace-ident s)
                            {:namespace/ident       (namespace-ident s)
                             :namespace.schema/name s})
                     sm)) {})
         (assoc heql-meta-data :namespaces))))

(defn fetch [db-spec]
  (with-open [db-conn (jdbc/get-connection db-spec)]
    (let [jdbc-meta-data  (.getMetaData db-conn)
          db-product-name (.getDatabaseProductName jdbc-meta-data)
          db-config       (assoc (get-db-config db-product-name) :db-product-name db-product-name)
          db-meta-data    (get-db-meta-data db-config db-spec db-conn)]
      (->> {:db-config db-config
            :entities  (entities-meta-data db-config db-meta-data)}
           (add-attributes-meta-data db-meta-data)
           (add-primary-keys-meta-data db-meta-data)
           (add-relationships-meta-data db-meta-data)
           add-many-to-many-rels-meta-data
           add-namespaces
           (trace>> :heql-meta-data)))))

;; Query Functions

(defn namespace-idents [heql-meta-data]
  (map key (:namespaces heql-meta-data)))

(defn entities [heql-meta-data]
  (vals (:entities heql-meta-data)))

(defn entity-meta-data [heql-meta-data entity-ident]
  (if-let [entity-meta-data (get-in heql-meta-data [:entities entity-ident])]
    entity-meta-data
    (throw (Exception. (str "entity " entity-ident " not found")))))

(defn attr-meta-data [heql-meta-data attr-ident]
  (if-let [attr-meta-data (get-in heql-meta-data [:attributes attr-ident])]
    attr-meta-data
    (throw (ex-info (str "attribute " attr-ident " not found") {:type :heql.exception/attr-not-found}))))

(defn entity-relation-ident [heql-meta-data attr-ident]
  (let [attr-md (attr-meta-data heql-meta-data attr-ident)]
    (:entity.relation/ident (entity-meta-data heql-meta-data (:attr.entity/ident attr-md)))))

(defn ref-entity-relation-ident [heql-meta-data attr-ident]
  (let [attr-md (attr-meta-data heql-meta-data attr-ident)]
    (when (= :attr.type/ref (:attr/type attr-md))
      (:entity.relation/ident (entity-meta-data heql-meta-data (:attr.ref/type attr-md))))))

(defn attr-column-ident [heql-meta-data attr-ident]
  (let [attr-md (attr-meta-data heql-meta-data attr-ident)]
    (get-in heql-meta-data [:attributes (:attr/ident attr-md) :attr.column/ident])))

(defn attr-column-name
  ([attr-meta-data]
   (:attr.column/name attr-meta-data))
  ([heql-meta-data attr-ident]
   (let [attr-md (attr-meta-data heql-meta-data attr-ident)]
     (attr-column-name attr-md))))

(defn attr-column-ref-type
  ([heql-meta-data attr-ident]
   (attr-column-ref-type (attr-meta-data heql-meta-data attr-ident)))
  ([attr-meta-data]
   (:attr.column.ref/type attr-meta-data)))

(defn- same-type? [expected-type x]
  (= expected-type (type x)))

(defn- local-date? [x]
  (same-type? LocalDate x))

(defn- local-date-time? [x]
  (same-type? LocalDateTime x))

(defn- local-time? [x]
  (same-type? LocalTime x))

(defn- offset-date-time? [x]
  (same-type? OffsetDateTime x))

(defn- coerce [type-check-fn parse-fn x]
  (if (type-check-fn x)
    x
    (when x
      (parse-fn x))))

(defn coerce-attr-value [db-adapter attr-ident value]
  (let [heql-meta-data (:heql-meta-data db-adapter)
        attr-md        (try
                         (attr-meta-data heql-meta-data attr-ident)
                         (catch Throwable ex
                           (if (and (= :heql.exception/attr-not-found (:type (ex-data ex))))
                             {:attr/type :attr.type/unknown}
                             (throw ex))))]
    (case (:attr/type attr-md)
      :attr.type/uuid (coerce uuid? #(java.util.UUID/fromString %) value)
      :attr.type/date (coerce local-date? #(LocalDate/parse %) value)
      :attr.type/time (coerce local-time? #(LocalTime/parse %) value)
      :attr.type/time-with-time-zone (coerce local-time? #(OffsetTime/parse %) value)
      :attr.type/date-time (coerce local-date-time? #(db/coerce db-adapter % :attr.type/date-time) value)
      :attr.type/boolean (coerce boolean? #(db/coerce db-adapter % :attr.type/boolean) value)
      :attr.type/date-time-with-time-zone (coerce offset-date-time? #(OffsetDateTime/parse %) value)
      :attr.type/ref (if (and (= :attr.ref.cardinality/many (:attr.ref/cardinality attr-md)) (nil? value))
                       []
                       value)
      value)))

(defn db-product-name [heql-meta-data]
  (get-in heql-meta-data [:db-config :db-product-name]))

(defn attr-idents [entity-meta-data]
  (let [{:entity/keys [req-attrs opt-attrs]} entity-meta-data]
    (concat req-attrs opt-attrs)))