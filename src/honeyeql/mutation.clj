(ns honeyeql.mutation
  (:require [next.jdbc.result-set :as rs]
            [next.jdbc.sql :as sql]
            [clojure.string :as str]
            [honeyeql.db-adapter.core :as db]
            [honeyeql.meta-data :as heql-md]))

(defn- entity-name [entity]
  (-> entity keys first namespace))

(defn- table-name [entity]
  (-> (entity-name entity) (str/replace #"-" "_") keyword))

(defn- sqlize-entity [db-adapter entity]
  (into {}
        (map (fn [[k v]]
               [(-> (name k) (str/replace #"-" "_") keyword)
                (heql-md/coerce-attr-value db-adapter :to-db k v)])
             entity)))

(defn- namespacify-attributes [entity-name entity]
  (update-keys entity #(keyword entity-name (name %))))

(defn insert! [db-adapter entity]
  (namespacify-attributes
   (entity-name entity)
   (sql/insert! (:db-spec db-adapter)
                (table-name entity)
                (sqlize-entity db-adapter entity)
                {:column-fn (db/table-fn db-adapter)
                 :table-fn (db/table-fn db-adapter)
                 :builder-fn rs/as-kebab-maps})))

(defn insert-multi! [db-adapter entities]
  (when (seq entities)
    (let [entities (map #(into (sorted-map) %) entities)
          first-entity (first entities)
          first-entity-name (entity-name first-entity)
          sqlized-entities (map (partial sqlize-entity db-adapter) entities)]
      (map
       #(namespacify-attributes first-entity-name %)
       (sql/insert-multi! (:db-spec db-adapter)
                          (table-name first-entity)
                          (keys (first sqlized-entities))
                          (map vals sqlized-entities)
                          {:column-fn (db/table-fn db-adapter)
                           :table-fn (db/table-fn db-adapter)
                           :builder-fn rs/as-kebab-maps})))))

(defn update! [db-adapter update-params where-params]
  (sql/update! (:db-spec db-adapter) (table-name update-params)
               (sqlize-entity db-adapter update-params) (sqlize-entity db-adapter where-params)
               {:column-fn (db/table-fn db-adapter)
                :table-fn (db/table-fn db-adapter)}))

(defn delete! [db-adapter where-params]
  (sql/delete! (:db-spec db-adapter) (table-name where-params)
               (sqlize-entity db-adapter where-params)
               {:column-fn (db/table-fn db-adapter)
                :table-fn (db/table-fn db-adapter)}))