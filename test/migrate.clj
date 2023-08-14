(ns migrate
  (:require [clojure.edn :as edn]
            [clojure.set :as set]
            [clojure.pprint :as pp]
            suite-reader
            ))

(defn pretty-spit
  [file-name collection]
  (spit (java.io.File. file-name)
        (with-out-str (pp/write collection :dispatch pp/code-dispatch))))

(as-> (slurp "/Users/tamizhvendans/graphqlize/graphqlize-qa/test/suite.edn") $
  (edn/read-string suite-reader/read-string-opts $)
  (update-in $ [:sakila :assertions] (fn [assertions] (mapv #(dissoc % :gql) assertions)))
  (update-in $ [:graphqlize :assertions] (fn [assertions] (mapv #(dissoc % :gql) assertions)))
  (set/rename-keys $ {:graphqlize :honeyeql})
  (spit "test/suite.edn" $))








