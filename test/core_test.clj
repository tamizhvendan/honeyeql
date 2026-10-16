(ns core-test
  (:require [clojure.test :refer [deftest testing is run-tests]]
            [honeyeql.core :as heql]
            [honeyeql.db :as heql-db]
            [clojure.edn :as edn]
            [suite-reader :refer [read-string-opts]]
            [hikari-cp.core :as hikari]))

(defn- resolve-fn [value]
  (if (symbol? value)
    (-> value
        requiring-resolve
        deref)
    value))

(defn- resolve-attr-coercions [heql-config]
  (update
   heql-config
   :attr/coercions
   (fn [coercions]
     (update-vals
      coercions
      (fn [coercion]
        (cond-> coercion
          (:to-db coercion)
          (update :to-db resolve-fn)

          (:from-db coercion)
          (update :from-db resolve-fn)))))))

(deftest honeyeql-query-test-suite
  (doseq [[_ {:keys [ds-opts assertions heql-config]}]
          (edn/read-string read-string-opts (slurp "./test/suite.edn"))]
    (doseq [{:keys [database-name adapter jdbc-url db-product-name]
             :as   ds-opt} ds-opts]
      (with-open [db-spec (hikari/make-datasource (dissoc ds-opt :db-product-name))]
        (let [db-adapter (heql-db/initialize db-spec (or (resolve-attr-coercions heql-config) heql/default-heql-config))]
          (doseq [{:keys [name eql]} assertions]
            (testing (if (and database-name adapter)
                       (str "Testing " name " on " database-name " in " adapter)
                       (str "Testing " name " on " jdbc-url))
              (let [{:keys [query-single query expected ignore]} eql
                    actual-expected                              (if (vector? expected)
                                                                   (get (apply hash-map expected) db-product-name)
                                                                   expected)]
                (when-not (contains? ignore db-product-name)
                  (if query-single
                    (is (= actual-expected (heql/query-single db-adapter query-single)))
                    (is (= actual-expected (heql/query db-adapter query)))))))))))))

(comment
  (run-tests))