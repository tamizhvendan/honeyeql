# Sorting

HoneyEQL supports sorting using the `:order-by` parameter. It takes a vector similar to HoneySQL and transform that to a corresponding `ORDER BY` SQL clause to sort the return value.

```clojure
; sorting by :language/name

; :eql.mode/lenient syntax
(heql/query
  db-adapter
  {[[] {:order-by [:language/name]}]
   [:language/name]})

; :eql.mode/strict syntax
(heql/query
  db-adapter
  '[{([] {:order-by [:language/name]}) 
     [:language/name]}])
```

```clojure
; sorting by :language/name in descending order

; :eql.mode/lenient syntax
(heql/query
  db-adapter
  {[[] {:order-by [[:language/name :desc]]}]
   [:language/name]})

; :eql.mode/strict syntax
(heql/query
  db-adapter
  '[{([] {:order-by [[:language/name :desc]]})
     [:language/name]}])
```

```clojure
; sorting by multiple attributes
; :actor/first-name is ascending order and then :actor/last-name in descending order

; :eql.mode/lenient syntax
(heql/query
  db-adapter
  {[[] {:order-by [:actor/first-name [:actor/last-name :desc]]
        :limit    2}]
   [:actor/first-name :actor/last-name]})

; :eql.mode/strict syntax
(heql/query
  db-adapter
  '[{([] {:order-by [:actor/first-name [:actor/last-name :desc]]
          :limit    2}) 
     [:actor/first-name :actor/last-name]}])

```

We can sort the relationship query results as well.

```clojure
; sorting one-to-many relationship query results

; :eql.mode/lenient syntax
(heql/query
  db-adapter
  {[:country/country-id 2] 
   [:country/country
    ; sorting `:country/cities` by `:city/city` in descending order  
    {[:country/cities {:order-by [[:city/city :desc]]}] 
     [:city/city]}]})

; :eql.mode/strict syntax
(heql/query
  db-adapter
  '[{[:country/country-id 2] 
     [:country/country
      ; sorting `:country/cities` by `:city/city` in descending order  
      {(:country/cities {:order-by [[:city/city :desc]]}) 
       [:city/city]}]}])
```

```clojure
; sorting many-to-many relationship query results

; :eql.mode/lenient syntax
(heql/query
  db-adapter
  {[:actor/actor-id 148] 
   [:actor/first-name
    ; sorting `:actor/films` by `:film/title` in descending order   
    {[:actor/films {:order-by [[:film/title :desc]]}] 
     [:film/title]}]})

; :eql.mode/strict syntax
(heql/query
  db-adapter
  '[{[:actor/actor-id 148] 
     [:actor/first-name
      ; sorting `:actor/films` by `:film/title` in descending order   
      {(:actor/films {:order-by [[:film/title :desc]]}) 
       [:film/title]}]}])
```

> **NOTE:** Currently, sorting the relationship query results is not supported in MySQL


