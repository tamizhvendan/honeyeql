(ns ^:no-doc honeyeql.dsl
  (:require [honeyeql.meta-data :as heql-md]
            [inflections.core :as inf]))

(defn alias-attribute-ident? [x]
  (and (vector? x)
       (= (count x) 3)
       (= :as (second x))))

(defn find-join-type [heql-meta-data eql-node]
  (let [{node-type :type
         node-key  :key} eql-node]
    (cond
      (= :root node-type) :root
      (and (= :join node-type) (vector? node-key) (empty? node-key)) :non-ident-join
      (and (= :join node-type) (keyword? node-key)) (-> (heql-md/attr-column-ref-type heql-meta-data node-key)
                                                        name
                                                        (str "-join")
                                                        keyword)
      (and (= :join node-type) (alias-attribute-ident? node-key)) (-> (heql-md/attr-column-ref-type heql-meta-data (first node-key))
                                                                      name
                                                                      (str "-join")
                                                                      keyword)
      (and (= :join node-type) (seq node-key) (even? (count node-key))) :ident-join)))

(defn column-alias [attr-naming-convention attr-ident]
  (case attr-naming-convention
    :naming-convention/qualified-kebab-case (str (namespace attr-ident) "/" (name attr-ident))
    :naming-convention/unqualified-kebab-case (name attr-ident)
    :naming-convention/unqualified-camel-case (inf/camel-case (name attr-ident) :lower)))

(defn select-clause-alias [{:keys [attr-ident key function-attribute-ident]}]
  (let [attr-ident (cond
                     function-attribute-ident (if (alias-attribute-ident? key)
                                                (nth key 2)
                                                (if (vector? attr-ident)
                                                  (keyword (namespace (second attr-ident)) (str (name (first attr-ident)) "-" (name (first key)) "-of-" (name (second attr-ident))))
                                                  (keyword (namespace attr-ident) (str (name (first key)) "-of-" (name attr-ident)))))
                     (alias-attribute-ident? key) (nth key 2)
                     :else attr-ident)]
    (column-alias :naming-convention/qualified-kebab-case attr-ident)))

(defmulti eql->hsql (fn [db-adapter heql-meta-data eql-node]
                      (find-join-type heql-meta-data eql-node)))