(ns honeyeql.db-adapter.mysql
  (:require [honeyeql.meta-data :as heql-md]
            [honeyeql.core :as heql]
            [honeysql.core :as hsql]
            [clojure.string :as string]))

(defmethod heql-md/get-db-config "MySQL" [_]
  {:schema             {:default "xyz"
                        :ignore  #{}}
   :foreign-key-suffix "_id"})

;; https://dev.mysql.com/doc/refman/8.0/en/data-types.html
(defn- mysql-type->col-type [{:keys [type_name]}]
  (case type_name
    ("CHAR" "VARCHAR" "TINYTEXT" "TEXT" "MEDIUMTEXT" "LONGTEXT" "ENUM" "SET" "BINARY" "VARBINARY" "TINYBLOB" "BLOB" "LONGBLOB") :attr.type/string
    ("TINYINT" "SMALLINT" "MEDIUMINT" "INT" "TINYINT UNSIGNED" "SMALLINT UNSIGNED" "MEDIUMINT UNSIGNED" "INT UNSIGNED") :attr.type/integer
    "BIT" :attr.type/string
    "BIGINT" :attr.type/big-integer
    ("DECIMAL", "NUMERIC") :attr.type/decimal
    ("REAL" "FLOAT") :attr.type/float
    "DOUBLE" :attr.type/double
    "JSON" :attr.type/json
    "DATE" :attr.type/date
    "DATETIME" :attr.type/data-time
    "TIMESTAMP" :attr.type/offset-date-time
    "TIME" :attr.type/time
    "YEAR" :attr.type/integer
    :attr.type/unknown))

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
                            (str "CAST(" select-clause " AS CHAR)")
                            select-clause)) :result]]
      :from   [alias]})))

(defn- json-kv [[k v]]
  (if (keyword? v)
    (list (str "'" k "'" ", "  "`" (namespace v) "`.`" (name v) "`"))
    (let [[sql & args] (hsql/format v :quoting :mysql)]
      (cons (str "'" k "'" ", (" sql ")") args))))

(defn- json-object [obj]
  (let [json-kvs     (map json-kv obj)
        json-obj-str (format "JSON_OBJECT(%s)" (string/join ", " (map first json-kvs)))
        sql-args     (mapcat rest json-kvs)]
    (if (seq sql-args)
      (-> (cons json-obj-str sql-args)
          vec
          hsql/raw)
      (hsql/raw json-obj-str))))

(defn- mysql-select-clause [db-adapter heql-meta-data eql-nodes]
  (json-object
   (reduce (fn [obj {:keys [attr-ident alias]
                     :as   eql-node}]
             (let [{:keys [self parent]} alias
                   attr-md               (heql-md/attr-meta-data heql-meta-data attr-ident)
                   column-name           (heql-md/attr-column-name attr-md)
                   attr-column-ref-type  (heql-md/attr-column-ref-type attr-md)]
               (assoc
                obj
                (heql/column-alias :qualified-kebab-case attr-ident)
                (case attr-column-ref-type
                  :attr.column.ref.type/one-to-one (keyword (str parent "__" self) "result")
                  (:attr.column.ref.type/one-to-many
                   :attr.column.ref.type/many-to-many) (result-set-hql
                                                        (heql/eql->hsql db-adapter heql-meta-data eql-node)
                                                        (keyword (str parent "__" self)))
                  (keyword (name parent) (name column-name)))))) {} eql-nodes)))

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

(defrecord MySqlAdapter [db-spec heql-config heql-meta-data]
  heql/DbAdapter
  (db-spec [mysql-adapter]
    (:db-spec mysql-adapter))
  (meta-data [mysql-adapter]
    (:heql-meta-data mysql-adapter))
  (config [mysql-adapter]
    (:heql-config mysql-adapter))
  (to-sql [mysql-adapter hsql]
    (-> (hsql/format (result-set-hql hsql) :quoting :mysql)
        fix-lateral
        fix-params))
  (select-clause [db-adapter heql-meta-data eql-nodes]
    [[(mysql-select-clause db-adapter heql-meta-data eql-nodes) :result]])
  (resolve-children-one-to-one-relationships [db-adapter heql-meta-data hsql eql-nodes]
    (assoc-one-to-one-hsql-queries db-adapter heql-meta-data hsql eql-nodes))
  (resolve-one-to-one-relationship [db-adapter heql-meta-data hsql {:keys [children]}]
    (assoc-one-to-one-hsql-queries db-adapter heql-meta-data hsql children))
  (resolve-one-to-many-relationship [db-adapter heql-meta-data hsql {:keys [children]}]
    (assoc-one-to-one-hsql-queries db-adapter heql-meta-data hsql children))
  (resolve-many-to-many-relationship [db-adapter heql-meta-data hsql {:keys [children]}]
    (assoc-one-to-one-hsql-queries db-adapter heql-meta-data hsql children)))