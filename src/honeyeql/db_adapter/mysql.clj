(ns ^:no-doc honeyeql.db-adapter.mysql
  (:require [honeyeql.meta-data :as heql-md]
            [honeyeql.core :as heql]
            [honeysql.core :as hsql]
            [honeyeql.db-adapter.core :as db]
            [clojure.string :as string]
            [next.jdbc.sql :as jdbc])
  (:import [java.time LocalDateTime]
           [java.time.temporal ChronoField]
           [java.time.format DateTimeFormatterBuilder DateTimeParseException]))

(defmethod heql-md/get-db-config "MySQL" [_]
  {:schema             {:default "xyz"
                        :ignore  #{}}
   :foreign-key-suffix "_id"})

(#{"BIT" "TINYINT"} "BIT")

;; https://dev.mysql.com/doc/refman/8.0/en/data-types.html
;; https://dev.mysql.com/doc/connector-j/8.0/en/connector-j-reference-type-conversions.html
(defn- mysql-type->col-type [{:keys [type_name column_size]}]
  (if (and (#{"BIT" "TINYINT"} type_name) (= 1 column_size))
    :attr.type/boolean
    (case type_name
      ("CHAR" "VARCHAR" "TINYTEXT" "TEXT" "MEDIUMTEXT" "LONGTEXT" "ENUM" "SET" "BINARY" "VARBINARY" "TINYBLOB" "BLOB" "LONGBLOB") :attr.type/string
      ("TINYINT" "SMALLINT" "MEDIUMINT" "INT" "TINYINT UNSIGNED" "SMALLINT UNSIGNED" "MEDIUMINT UNSIGNED") :attr.type/integer
      ("INT UNSIGNED" "BIGINT") :attr.type/long
      "BIT" :attr.type/string
      "BIGINT UNSIGNED" :attr.type/big-integer
      ("DECIMAL", "NUMERIC") :attr.type/decimal
      ("REAL" "FLOAT") :attr.type/float
      "DOUBLE" :attr.type/double
      "JSON" :attr.type/json
      "DATE" :attr.type/date
      "DATETIME" :attr.type/date-time
      "TIMESTAMP" :attr.type/date-time
      "TIME" :attr.type/time
      "YEAR" :attr.type/integer
      :attr.type/unknown)))

(defmethod heql-md/derive-attr-type "MySQL" [_ column-meta-data]
  (mysql-type->col-type column-meta-data))

(defn- entities-meta-data [db-spec jdbc-meta-data catalog]
  (->> (into-array String ["TABLE" "VIEW"])
       (.getTables jdbc-meta-data catalog "%" nil)
       (heql-md/datafied-result-set db-spec)
       vec))

(defn- attributes-meta-data [db-spec jdbc-meta-data catalog]
  (->> (.getColumns jdbc-meta-data catalog "%" "%" nil)
       (heql-md/datafied-result-set db-spec)
       vec))

(defn- primary-keys-meta-data [db-spec jdbc-meta-data catalog table-name]
  (->> (.getPrimaryKeys jdbc-meta-data catalog "" table-name)
       (heql-md/datafied-result-set db-spec)
       vec))

(defn- foreign-keys-meta-data [db-spec jdbc-meta-data catalog table-name]
  (->> (.getImportedKeys jdbc-meta-data catalog "" table-name)
       (heql-md/datafied-result-set db-spec)
       vec))

(defn- get-pks-and-fks [db-spec jdbc-meta-data catalog table-names]
  (reduce (fn [s table-name]
            (update (update s
                            :primary-keys (comp vec concat) (primary-keys-meta-data db-spec jdbc-meta-data catalog table-name))
                    :foreign-keys (comp vec concat) (foreign-keys-meta-data db-spec jdbc-meta-data catalog table-name)))
          {:primary-keys []
           :foreign-keys []} table-names))

(defmethod heql-md/get-db-meta-data "MySQL" [_ db-spec db-conn]
  (let [jdbc-meta-data                      (.getMetaData db-conn)
        catalog                             (.getCatalog db-conn)
        entities-meta-data                  (entities-meta-data db-spec jdbc-meta-data catalog)
        table-names                         (map :table_name entities-meta-data)
        {:keys [primary-keys foreign-keys]} (get-pks-and-fks db-spec jdbc-meta-data catalog table-names)]
    {:entities     entities-meta-data
     :attributes   (attributes-meta-data db-spec jdbc-meta-data catalog)
     :primary-keys primary-keys
     :foreign-keys foreign-keys}))

(defn- result-set-hql
  ([hsql]
   (result-set-hql hsql :rs))
  ([hsql alias]
   (let [select-clause (str "COALESCE(JSON_ARRAYAGG(`" (name alias)  "`.`result`), JSON_ARRAY())")]
     {:with   [[alias hsql]]
      :select [[(hsql/raw (if (= :rs alias)
                            "`rs`.`result`"
                            select-clause)) :result]]
      :from   [alias]})))

(defn- function-attribute-json-v [k [op v]]
  (list (format "'%s', %s(`%s`.`%s`)" k (name op) (namespace v) (name v))))

(defn- json-kv [[k v]]
  (cond
    (keyword? v) (list (format "'%s', `%s`.`%s`" k (namespace v) (name v)))
    (vector? v) (function-attribute-json-v k v)
    :else (let [[sql & args] (hsql/format v :quoting :mysql)]
            (cons (str "'" k "'" ", (" sql ")") args))))

#_(json-kv ["course/count-of-title" [:count :G__249077/title]])

; HoneySQL raw doesn't treat String as parameter
; (hsql/format (hsql/raw ["JOBJ(?, ?)" "foo" "bar"])) 
; returns ["JOBJ(?, ?)foobar"] instead of ["JOBJ(?, ?)" "foo" "bar"]
; this function is an workardound to convert String to StringBuilder 
; which results in ["JOBJ(?, ?)??" #object[StringBuilder "foo"] #object[StringBuilder "bar"]]
(defn- convert-string-args [arg]
  (if (string? arg)
    (StringBuilder. arg)
    arg))

(defn- json-object [obj]
  (let [json-kvs     (map json-kv obj)
        json-obj-str (format "JSON_OBJECT(%s)" (string/join ", " (map first json-kvs)))
        sql-args     (mapcat rest json-kvs)]
    (if (seq sql-args)
      (->> (map convert-string-args sql-args)
           (cons json-obj-str)
           vec
           hsql/raw)
      (hsql/raw json-obj-str))))

(defn- select-clause-column [{:keys [function-attribute-ident alias key]} attr-md]
  (let [column-name           (heql-md/attr-column-name attr-md)
        {:keys [_ parent]} alias
        c (keyword (name parent) (name column-name))]
    (if function-attribute-ident
      [(first key) c]
      c)))

(defn- mysql-select-clause [db-adapter heql-meta-data eql-nodes]
  (json-object
   (reduce (fn [obj {:keys [attr-ident alias]
                     :as   eql-node}]
             (let [{:keys [self parent]} alias
                   attr-md               (heql-md/attr-meta-data heql-meta-data attr-ident)
                   attr-column-ref-type  (heql-md/attr-column-ref-type attr-md)]
               (assoc
                obj
                (heql/select-clause-alias eql-node)
                (case attr-column-ref-type
                  :attr.column.ref.type/one-to-one (keyword (str parent "__" self) "result")
                  (:attr.column.ref.type/one-to-many
                   :attr.column.ref.type/many-to-many) (result-set-hql
                                                        (heql/eql->hsql db-adapter heql-meta-data eql-node)
                                                        (keyword (str parent "__" self)))
                  (select-clause-column eql-node attr-md))))) {} eql-nodes)))

(defn- assoc-one-to-one-hsql-queries [db-adapter heql-meta-data hsql eql-nodes]
  (->> (filter #(= :one-to-one-join (heql/find-join-type heql-meta-data %)) eql-nodes)
       (map (fn [{:keys [alias]
                  :as   eql-node}]
              [(hsql/raw "LATERAL")
               [(heql/eql->hsql db-adapter heql-meta-data eql-node)
                (keyword (str (:parent alias) "__" (:self alias)))]]))
       (update hsql :from #(apply concat %1 %2))))

(defn- fix-lateral [[x & xs]]
  (conj xs (string/replace x "LATERAL," "LATERAL")))

(defn- fix-params [[x & xs]]
  (conj xs (string/replace x #"\?+ AS " " AS ")))

(defn- fix-string-builder-args [[x & xs]]
  (conj (map (fn [arg]
               (if (= StringBuilder (type arg))
                 (str arg)
                 arg)) xs) x))

(def ^:private date-time-formatter
  (-> (DateTimeFormatterBuilder.)
      (.appendPattern "yyyy-MM-dd HH:mm:ss")
      (.appendFraction ChronoField/MICRO_OF_SECOND 0 6 true)
      .toFormatter))

(defn- coerce-datetime [value]
  (try
    (LocalDateTime/parse value date-time-formatter)
    (catch DateTimeParseException _
      (LocalDateTime/parse value))))

(defn- coerce-boolean [value]
  (if (integer? value)
    (not= 0 value)
    (= "base64:type16:AQ==" value)))

(defrecord MySqlAdapter [db-spec heql-config heql-meta-data]
  db/DbAdapter
  (to-sql [mysql-adapter hsql]
    (-> (hsql/format (result-set-hql hsql) :quoting :mysql)
        fix-lateral
        fix-params
        fix-string-builder-args))
  (query [mysql-adapter sql]
    (let [result (->> (jdbc/query (:db-spec mysql-adapter) sql)
                      (map :result)
                      (string/join ","))]
      (str "[" result "]")))
  (coerce [_ value target-type]
    (case target-type
      :attr.type/date-time (coerce-datetime value)
      :attr.type/boolean (coerce-boolean value)))
  (select-clause [db-adapter heql-meta-data eql-nodes]
    [[(mysql-select-clause db-adapter heql-meta-data eql-nodes) :result]])
  (resolve-one-to-one-relationship-alias [db-adapter {:keys [parent self]}]
                                         (keyword (format "%s__%s" parent self) "result"))
  (resolve-children-one-to-one-relationships [db-adapter heql-meta-data hsql eql-nodes]
    (assoc-one-to-one-hsql-queries db-adapter heql-meta-data hsql eql-nodes))
  (resolve-one-to-one-relationship [db-adapter heql-meta-data hsql {:keys [children]}]
    (assoc-one-to-one-hsql-queries db-adapter heql-meta-data hsql children))
  (resolve-one-to-many-relationship [db-adapter heql-meta-data hsql {:keys [children]}]
    (assoc-one-to-one-hsql-queries db-adapter heql-meta-data hsql children))
  (resolve-many-to-many-relationship [db-adapter heql-meta-data hsql {:keys [children]}]
    (assoc-one-to-one-hsql-queries db-adapter heql-meta-data hsql children)))