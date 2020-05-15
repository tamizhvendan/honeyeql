(ns ^:no-doc honeyeql.debug)

(defn trace>> [key value]
  (tap> {key value})
  value)

(defn trace> [value key]
  (tap> {key value})
  value)

