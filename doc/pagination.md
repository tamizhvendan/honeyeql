## Pagination

#### Limit and Offset

```clojure
(heql/query
  db-adapter
  '{([] {:limit 2 :offset 2})
    [:actor/actor-id :actor/first-name]})
; returns
({:actor/actor-id 3, :actor/first-name "ED"}
 {:actor/actor-id 4, :actor/first-name "JENNIFER"})
```

Both `limit` and `offset` can be applied on `one-to-many` and `many-to-many` relationships as well.

```clojure
(heql/query-single
  db-adapter
  '{[:country/country-id 2]
    [:country/country
    ; one-to-many relationship
    {(:country/cities {:limit 2 :offset 2})
      [:city/city]}]})
```

```clojure
(heql/query
  db-adapter
  '{[:actor/actor-id 148]
    [:actor/first-name
    ; many-to-many relationship
    {(:actor/films {:limit 1 :offset 2})
      [:film/title]}]})
```

