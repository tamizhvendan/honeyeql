# Pagination

As we typically do in SQL, in HoneyEQL we can leverage `limit` and `offset` parameters to perform the pagination. 

## Limit and Offset

```clojure
; :eql.mode/lenient syntax
(heql/query
  db-adapter
  {[[] {:limit 2 :offset 2}]
   [:actor/actor-id :actor/first-name]})

; :eql.mode/strict syntax
(heql/query
  db-adapter
  '[{([] {:limit 2 :offset 2})
     [:actor/actor-id :actor/first-name]}])
```

Both `limit` and `offset` can be applied on `one-to-many` and `many-to-many` relationships as well.

```clojure
; :eql.mode/lenient syntax
(heql/query-single
  db-adapter
  {[:country/country-id 2]
   [:country/country
    ; one-to-many relationship
    {[:country/cities {:limit 2 :offset 2}]
     [:city/city]}]})

; :eql.mode/strict syntax
(heql/query-single
  db-adapter
  '[{[:country/country-id 2]
     [:country/country
      ; one-to-many relationship
      {(:country/cities {:limit 2 :offset 2})
       [:city/city]}]}])
```

```clojure
; :eql.mode/lenient syntax
(heql/query
  db-adapter
  {[:actor/actor-id 148]
   [:actor/first-name
    ; many-to-many relationship
    {[:actor/films {:limit 1 :offset 2}]
     [:film/title]}]})

; :eql.mode/strict syntax
(heql/query
  db-adapter
  '[{[:actor/actor-id 148]
     [:actor/first-name
     ; many-to-many relationship
     {(:actor/films {:limit 1 :offset 2})
      [:film/title]}]}])
```

