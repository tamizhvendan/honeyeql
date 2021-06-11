# Debugging & Troubleshooting

## Viewing entities & attributes of a DB schema

The `db-adapter` returned from `honeyeql.db/initialize` is a Record that has a key `:heql-meta-data` with the value contains all the metadata queried and computed by HoneyEQL. 

This map has two keys `:attributes` and `:entities` pointing to their respective metadata.

You can make use of [Portal](https://github.com/djblue/portal) or [Reveal](https://vlaaad.github.io/reveal/) to visualize this map. 

Here is a sample Code Snippet for how to do it

```clojure
(comment
  (def pg-adapter (heql-db/initialize {:dbtype   "postgres"
                                       :dbname   "graphqlize"
                                       :user     "postgres"
                                       :password "postgres"}))

  ; portal
  (require '[portal.api :as p])
  (p/open)
  (add-tap #'p/submit)
  (tap> pg-adapter)
  (p/close)

  ; reveal

```


## Viewing Query Execution Steps

HoneyEQL makes extensive use of [tap>](https://clojuredocs.org/clojure.core/tap%3E) function to log all the intermediate steps of query executions. 

When we execute an HoneyEQL query like this, 

```clojure
(heql/query-single pg-adapter {[:actor/actor-id 148]
                                [:actor/first-name
                                {:actor/films [[:count :film/film-id]]}]})
```
the following values are getting "tapped"

```clojure
; :sql - Generated SQL 
; :hsql - Generated Honey SQL 
; :eql-ast - AST of EQL 
```

You can make use of [Portal](https://github.com/djblue/portal) or [Reveal](https://vlaaad.github.io/reveal/) as mentioned earlier to view this taps