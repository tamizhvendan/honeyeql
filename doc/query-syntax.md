### one-to-one relationship

![](https://www.graphqlize.org/img/address_city_country_er_diagram.png)

```clojure
(heql/query-single
  db-adapter
  {[:city/city-id 3] [:city/city
                      {:city/country [:country/country]}]})
```

### one-to-many relationship

![](https://www.graphqlize.org/img/address_city_country_er_diagram.png)

```clojure
(heql/query-single
  db-adapter
  {[:country/country-id 2] [:country/country
                            {:country/cities [:city/city]}]})
```

### many-to-many relationship

![](https://www.graphqlize.org/img/film_actor_er_diagram.png)

```clojure
(heql/query-single
  db-adapter
  {[:actor/actor-id 148] [:actor/first-name
                          {:actor/films [:film/title]}]})
```
