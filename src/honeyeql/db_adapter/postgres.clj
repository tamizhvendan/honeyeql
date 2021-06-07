(ns ^:no-doc honeyeql.db-adapter.postgres
  (:require [honeyeql.meta-data :as heql-md]
            [honeyeql.core :as heql]
            [honeysql.format :as fmt]
            [honeysql.core :as hsql]
            [honeyeql.db-adapter.core :as db]
            [next.jdbc.sql :as jdbc]
            [clojure.string :as string])
  (:import [java.time LocalDateTime]
           [java.time.temporal ChronoField]
           [java.time.format DateTimeFormatter DateTimeFormatterBuilder]))

(defmethod ^{:private true} fmt/format-clause :pg-left-join-lateral [[_ join-groups] _]
  (string/join
   " "
   (map (fn [[join-group alias]]
          (str "LEFT JOIN LATERAL " (fmt/to-sql join-group) " AS \"" (name alias) "\" ON TRUE"))
        join-groups)))

(defmethod ^{:private true} fmt/format-clause :pg-coalesce-array [[_ x] _]
  (str " COALESCE (" (fmt/to-sql x) ",'[]')"))

(fmt/register-clause! :pg-left-join-lateral 135)

(defmethod heql-md/get-db-config "PostgreSQL" [_]
  {:schema             {:default "public"
                        :ignore  #{"information_schema" "pg_catalog" "pg_toast"}}
   :foreign-key-suffix "_id"})

; Ref: https://www.postgresql.org/docs/current/datatype.html
(defn- pg-type->col-type [type-name]
  (case type-name
    ("bigint" "int8" "bigserial" "serial8") :attr.type/long
    ("bit" "bit varying")  :attr.type/string
    ("boolean" "bool")     :attr.type/boolean
    ("character" "char" "character varying" "varchar" "citext" "bpchar" "text" "money") :attr.type/string
    ("real" "float4" "float8") :attr.type/float
    "double precision" :attr.type/double
    ("numeric" "decimal") :attr.type/decimal
    "inet" :attr.type/ip-address
    ("integer" "int" "int2" "int4") :attr.type/integer
    "interval" :attr.type/time-span
    ("json" "jsonb") :attr.type/json
    ("macaddr" "macaddr8") :attr.type/string
    ("smallint" "smallserial" "serial" "serial2" "serial4") :attr.type/integer
    ; https://jdbc.postgresql.org/documentation/head/8-date-time.html
    "date" :attr.type/date
    ("time" "time without time zone") :attr.type/time
    ("timetz" "time with time zone") :attr.type/time-with-time-zone
    ("timestamp" "timestamp without time zone") :attr.type/date-time
    ("timestamptz" "timestamp with time zone") :attr.type/date-time-with-time-zone
    "uuid" :attr.type/uuid
    "xml" :attr.type/xml
    :attr.type/unknown))

(defmethod heql-md/derive-attr-type "PostgreSQL" [_ column-meta-data]
  (pg-type->col-type (:type_name column-meta-data)))

(defn- entities-meta-data [db-spec jdbc-meta-data]
  (->> (into-array String ["TABLE" "VIEW" "MATERIALIZED VIEW"])
       (.getTables jdbc-meta-data nil "%" nil)
       (heql-md/datafied-result-set db-spec)
       vec))

(defn- attributes-meta-data [db-spec jdbc-meta-data]
  (->> (.getColumns jdbc-meta-data nil "%" "%" nil)
       (heql-md/datafied-result-set db-spec)
       vec))

(defn- primary-keys-meta-data [db-spec jdbc-meta-data]
  (->> (.getPrimaryKeys jdbc-meta-data nil "" nil)
       (heql-md/datafied-result-set db-spec)
       vec))

(defn- foreign-keys-meta-data [db-spec jdbc-meta-data]
  (->> (.getImportedKeys jdbc-meta-data nil "" nil)
       (heql-md/datafied-result-set db-spec)
       vec))

(defmethod heql-md/get-db-meta-data "PostgreSQL" [_ db-spec db-conn]
  (let [jdbc-meta-data  (.getMetaData db-conn)]
    {:entities     (entities-meta-data db-spec jdbc-meta-data)
     :attributes   (attributes-meta-data db-spec jdbc-meta-data)
     :primary-keys (primary-keys-meta-data db-spec jdbc-meta-data)
     :foreign-keys (foreign-keys-meta-data db-spec jdbc-meta-data)}))

(defn- result-set-hql [hql]
  {:with   [[:rs hql]]
   :from   [[{:select [:*]
              :from   [:rs]} :rs]]
   :select [(hsql/raw "coalesce (json_agg(\"rs\"), '[]')::character varying as result")]})

(defn- hsql-column-name [{:keys [alias function-attribute-ident key]} attr-md]
  (let [{:keys [parent]} alias
        c (->> (heql-md/attr-column-name attr-md)
             (str parent ".")
             keyword)]
    (if function-attribute-ident
      (keyword (str "%" (name (first key)) "." (name c)))
      c)))

(defn- eql-node->select-expr [db-adapter heql-meta-data {:keys [attr-ident alias]
                                                         :as   eql-node}]
  (let [{:keys [parent self]} alias
        attr-md               (heql-md/attr-meta-data heql-meta-data attr-ident)
        select-attr-expr      (case (:attr.column.ref/type attr-md)
                                :attr.column.ref.type/one-to-one (keyword (str parent "__" self))
                                (:attr.column.ref.type/one-to-many :attr.column.ref.type/many-to-many) (heql/eql->hsql db-adapter heql-meta-data eql-node)
                                (hsql-column-name eql-node attr-md))]
    [select-attr-expr (heql/select-clause-alias eql-node)]))

(defn- assoc-one-to-one-hsql-queries [db-adapter heql-meta-data hsql eql-nodes]
  (if-let [one-to-one-join-children
           (seq (filter #(= :one-to-one-join (heql/find-join-type heql-meta-data %)) eql-nodes))]
    (assoc hsql :pg-left-join-lateral (map #(heql/eql->hsql db-adapter heql-meta-data %) one-to-one-join-children))
    hsql))

(defn- json-agg [select-alias]
  (keyword (str "%json_agg." (name select-alias) ".*")))

(def ^:private date-time-formatter
  (-> (DateTimeFormatterBuilder.)
      (.append DateTimeFormatter/ISO_LOCAL_DATE_TIME)
      (.appendFraction ChronoField/MICRO_OF_SECOND 0 6 true)
      .toFormatter))

(defrecord PostgresAdapter [db-spec heql-config heql-meta-data]
  db/DbAdapter
  (to-sql [pg-adapter hsql]
    (hsql/format (result-set-hql hsql) :quoting :ansi))
  (query [pg-adapter sql]
         (-> (jdbc/query (:db-spec pg-adapter) sql)
             first
             :result))
  (coerce [_ value target-type]
    (case target-type
     :attr.type/date-time (LocalDateTime/parse value date-time-formatter)
     :attr.type/boolean value))
  (resolve-one-to-one-relationship-alias [db-adapter {:keys [parent self]}]
                                         (keyword (format "%s__%s" parent self)))
  (select-clause [db-adapter heql-meta-data eql-nodes]
    (vec (map #(eql-node->select-expr db-adapter heql-meta-data %) eql-nodes)))
  (resolve-children-one-to-one-relationships [db-adapter heql-meta-data hsql eql-nodes]
    (assoc-one-to-one-hsql-queries db-adapter heql-meta-data hsql eql-nodes))
  (resolve-one-to-one-relationship [db-adapter heql-meta-data hsql {:keys [alias children]}]
    [(assoc-one-to-one-hsql-queries db-adapter heql-meta-data hsql children)
     (keyword (str (:parent alias) "__" (:self alias)))])
  (resolve-one-to-many-relationship [db-adapter heql-meta-data hsql {:keys [children]}]
    (let [projection-alias (gensym)]
      {:pg-coalesce-array {:select [(json-agg projection-alias)]
                           :from   [[(assoc-one-to-one-hsql-queries db-adapter heql-meta-data hsql children)
                                     projection-alias]]}}))
  (resolve-many-to-many-relationship [db-adapter heql-meta-data hsql {:keys [children]}]
    (let [projection-alias (gensym)]
      {:pg-coalesce-array {:select [(json-agg projection-alias)]
                           :from   [[(assoc-one-to-one-hsql-queries db-adapter heql-meta-data hsql children)
                                     projection-alias]]}})))