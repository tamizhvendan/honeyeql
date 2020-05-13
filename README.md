## HoneyEQL

HoneyEQL is a Clojure library enables you to query the database declaratively using the [EDN Query Language](https://edn-query-language.org)(EQL). It aims to simplify the effort required to work with the relational databases in Clojure.

HoneyEQL powers [GraphQLize](https://www.graphqlize.org). 

[![Clojars Project](https://img.shields.io/clojars/v/org.graphqlize/honeyeql.svg)](https://clojars.org/org.graphqlize/honeyeql) <a href="https://discord.gg/akkdPqf"><img src="https://img.shields.io/badge/chat-discord-brightgreen.svg?logo=discord&style=flat"></a>

> CAUTION: HoneyEQL is at its early stages now. **It is not production-ready yet!**. It currently supports Postgres (9.4 & above) and MySQL (8.0 & above) only.

## Rationale

When a query involves more than one table, the declarative nature of SQL depreciates. Depends on the type of relationship, we have to put appropriate join conditions. 

Let's assume that we have the following schema.

![](https://www.graphqlize.org/img/film_actor_er_diagram.png)

To get all the films of an actor with the id `148` along with his/her first name & last name, we have to query it as 

```clojure
(jdbc/execute! ds ["SELECT actor.first_name, actor.last_name, film.title
                    FROM actor
                    LEFT OUTER JOIN film_actor ON film_actor.actor_id = actor.actor_id
                    LEFT OUTER JOIN film ON film_actor.film_id = film.film_id
                    WHERE actor.actor_id = ?" 148])
```

The query result would look like 

```clojure
[{:actor/first_name "EMILY", :actor/last_name "DEE", :film/title "ANONYMOUS HUMAN"}
 {:actor/first_name "EMILY", :actor/last_name "DEE", :film/title "BASIC EASY"}
 {:actor/first_name "EMILY", :actor/last_name "DEE", :film/title "CHAMBER ITALIAN"}
 ...]
```

Then we need to group by `first_name` & `last_name` to get the exact result result that we want!

How about making these steps truly declarative? 

With HoneyEQL, we can do it as

```clojure
(heql/query-single 
  db-adapter  
  {[:actor/actor-id 148] 
   [:actor/first-name 
    :actor/last-name 
    {:actor/films 
     [:film/title]}]})
```
The above query **yields the results in the exact-shape that we wanted** and **without any** explicit data transformations.

```clojure
{:actor/first-name "EMILY"
 :actor/last-name "DEE"
 :actor/films [{:film/title "ANONYMOUS HUMAN"}
               {:film/title "BASIC EASY"}
               {:film/title "CHAMBER ITALIAN"}
               ...]}
```

As the query syntax is made up of Clojure's data structures, we can construct it **dynamically** at runtime. 

HoneyEQL transforms the EQL into single efficient SQL and query the database using [next.jdbc](https://github.com/seancorfield/next-jdbc).

## Documentation

[![cljdoc badge](https://cljdoc.org/badge/org.graphqlize/honeyeql)](https://cljdoc.org/d/org.graphqlize/honeyeql/CURRENT)

## Acknowledgements

[Walkable](https://walkable.gitlab.io/) is the inspiration behind HoneyEQL.

HoneyEQL is not possible without the following excellent Clojure libraries.

- [HoneySQL](https://github.com/jkk/honeysql)
- [next-jdbc](https://github.com/seancorfield/next-jdbc)
- [inflections](https://github.com/r0man/inflections-clj)
- [data-json](https://github.com/clojure/data.json)

The samples in the documentation of HoneyEQL uses the [Sakila](https://www.jooq.org/sakila) database from [JOOQ](https://www.jooq.org) extensively.

## License

The use and distribution terms for this software are covered by the [Eclipse Public License - v 2.0](https://www.eclipse.org/legal/epl-2.0). By using this software in any fashion, you are agreeing to be bound by the terms of this license.


