(ns honeyeql.db-adapter.postgres
  (:require [honeyeql.meta-data :as heql-md]
            [honeyeql.core :as heql]
            [honeysql.format :as fmt]
            [honeysql.core :as hsql]
            [clojure.string :as string]))

(defmethod ^{:private true} fmt/format-clause :left-join-lateral [[_ join-groups] _]
  (string/join
   " "
   (map (fn [[join-group alias]]
          (str "LEFT JOIN LATERAL " (fmt/to-sql join-group) " AS \"" (name alias) "\" ON TRUE"))
        join-groups)))

(defmethod ^{:private true} fmt/format-clause :coalesce-array [[_ x] _]
  (str " COALESCE (" (fmt/to-sql x) ",'[]')"))

(fmt/register-clause! :left-join-lateral 135)

(defmethod heql-md/get-db-config "PostgreSQL" [_]
  {:schema             {:default "public"
                        :ignore  #{"information_schema" "pg_catalog" "pg_toast"}}
   :foreign-key-suffix "_id"})

; Ref: https://www.postgresql.org/docs/current/datatype.html
(defn- pg-type->col-type [type-name]
  (case type-name
    ("bigint" "int8" "bigserial" "serial8") :attr.type/big-integer
    ("bit" "bit varying")  :attr.type/string
    ("boolean" "bool")     :attr.type/boolean
    ("character" "char" "character varying" "varchar" "citext" "bpchar") :attr.type/string
    "date" :attr.type/date
    "decimal" :attr.type/decimal
    ("real" "float4" "float8") :attr.type/float
    "double precision" :attr.type/double
    "inet" :attr.type/ip-address
    ("integer" "int" "int2" "int4") :attr.type/integer
    "interval" :attr.type/time-span
    ("json" "jsonb") :attr.type/json
    ("macaddr" "macaddr8") :attr.type/string
    ("money" "numeric") :attr.type/decimal
    ("smallint" "smallserial" "serial" "serial2" "serial4") :attr.type/integer
    "text" :attr.type/string
    ; https://jdbc.postgresql.org/documentation/head/8-date-time.html
    ("time" "time without time zone") :attr.type/time
    ("timetz" "time with time zone") :attr.type/offset-time
    ("timestamp" "timestamp without time zone") :attr.type/data-time
    ("timestamptz" "timestamp with time zone") :attr.type/offset-date-time
    "uuid" :attr.type/uuid
    "xml" :attr.type/xml
    :attr.type/unknown))

(defmethod heql-md/derive-attr-type "PostgreSQL" [_ column-meta-data]
  (pg-type->col-type (:type_name column-meta-data)))

(defn- entities-meta-data [db-spec jdbc-meta-data]
  (->> (into-array String ["TABLE" "VIEW"])
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

(defn- eql-node->select-expr [db-adapter heql-meta-data {:keys [attr-ident alias]
                                                         :as   eql-node}]
  (let [{:keys [parent self]} alias
        attr-md               (heql-md/attr-meta-data heql-meta-data attr-ident)
        select-attr-expr      (case (:attr.column.ref/type attr-md)
                                :attr.column.ref.type/one-to-one (keyword (str parent "__" self))
                                (:attr.column.ref.type/one-to-many :attr.column.ref.type/many-to-many) (heql/eql->hsql db-adapter heql-meta-data eql-node)
                                (->> (heql-md/attr-column-name attr-md)
                                     (str parent ".")
                                     keyword))]
    [select-attr-expr (heql/column-alias :qualified-kebab-case attr-ident)]))

(defn- assoc-one-to-one-hsql-queries [db-adapter heql-meta-data hsql eql-nodes]
  (if-let [one-to-one-join-children
           (seq (filter #(= :one-to-one-join (heql/find-join-type heql-meta-data %)) eql-nodes))]
    (assoc hsql :left-join-lateral (map #(heql/eql->hsql db-adapter heql-meta-data %) one-to-one-join-children))
    hsql))

(defn- json-agg [select-alias]
  (keyword (str "%json_agg." (name select-alias) ".*")))

(defrecord PostgresAdapter [db-spec heql-config heql-meta-data]
  heql/DbAdapter
  (db-spec [pg-adapter]
    (:db-spec pg-adapter))
  (meta-data [pg-adapter]
    (:heql-meta-data pg-adapter))
  (config [pg-adapter]
    (:heql-config pg-adapter))
  (merge-config [pg-adapter config-to-override]
    (update pg-adapter :heql-config merge config-to-override))
  (to-sql [pg-adapter hsql]
    (hsql/format (result-set-hql hsql) :quoting :ansi))
  (select-clause [db-adapter heql-meta-data eql-nodes]
    (vec (map #(eql-node->select-expr db-adapter heql-meta-data %) eql-nodes)))
  (resolve-children-one-to-one-relationships [db-adapter heql-meta-data hsql eql-nodes]
    (assoc-one-to-one-hsql-queries db-adapter heql-meta-data hsql eql-nodes))
  (resolve-one-to-one-relationship [db-adapter heql-meta-data hsql {:keys [alias children]}]
    [(assoc-one-to-one-hsql-queries db-adapter heql-meta-data hsql children)
     (keyword (str (:parent alias) "__" (:self alias)))])
  (resolve-one-to-many-relationship [db-adapter heql-meta-data hsql {:keys [children]}]
    (let [projection-alias (gensym)]
      {:coalesce-array {:select [(json-agg projection-alias)]
                        :from   [[(assoc-one-to-one-hsql-queries db-adapter heql-meta-data hsql children)
                                  projection-alias]]}}))
  (resolve-many-to-many-relationship [db-adapter heql-meta-data hsql {:keys [children]}]
    (let [projection-alias (gensym)]
      {:coalesce-array {:select [(json-agg projection-alias)]
                        :from   [[(assoc-one-to-one-hsql-queries db-adapter heql-meta-data hsql children)
                                  projection-alias]]}})))