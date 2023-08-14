(ns core-test
  (:require [clojure.test :refer [deftest testing is run-tests]]
            [honeyeql.core :as heql]
            [honeyeql.db :as heql-db]
            [clojure.edn :as edn]
            [suite-reader :refer [read-string-opts]]
            [hikari-cp.core :as hikari]))

(deftest honeyeql-core-test-suite
  (doseq [[_ {:keys [ds-opts assertions]}]
          (edn/read-string read-string-opts (slurp "./test/suite.edn"))]
    (doseq [{:keys [database-name adapter jdbc-url db-product-name]
             :as   ds-opt} ds-opts]
      (with-open [db-spec (hikari/make-datasource (dissoc ds-opt :db-product-name))]
        (let [db-adapter (heql-db/initialize db-spec)]
          (doseq [{:keys [name eql]} assertions]
            (when eql
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
                      (is (= actual-expected (heql/query db-adapter query))))))))))))))

(comment
  (run-tests))