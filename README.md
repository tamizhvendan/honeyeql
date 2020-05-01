## HoneyEQL

HoneyEQL is a Clojure library enables you to query database using the [EDN Query Language](https://edn-query-language.org)(EQL). HoneyEQL transforms the EQL into single efficient SQL and query the database using [next.jdbc](https://github.com/seancorfield/next-jdbc).

HoneyEQL powers [GraphQLize](https://www.graphqlize.org).

[![Clojars Project](https://img.shields.io/clojars/v/org.graphqlize/honeyeql.svg)](https://clojars.org/org.graphqlize/honeyeql) <a href="https://discord.gg/akkdPqf"><img src="https://img.shields.io/badge/chat-discord-brightgreen.svg?logo=discord&style=flat"></a>

> CAUTION: HoneyEQL is at its early stages now. **It is not production-ready yet!**. It currently supports Postgres (9.4 & above) and MySQL (8.0 & above) only.

## Table of contents

- [Getting Started](#getting-started)
  - Queries
    - [one-to-one relationship](#one-to-one-relationship)
    - [one-to-many relationship](#one-to-many-relationship)
    - [many-to-many relationship](#many-to-many-relationship)
    - Pagination
      - [limit and offset](#limit-and-offset)
    - [Sorting](#sorting)
    - [Filtering](#filtering)
      - [Filter Based On Relationship Attributes](#filter-based-on-relationship-attributes)
      - [Filtering Relationships](#filtering-relationships)
  - Coercion
    - [Type Mappings](#type-mappings)
- [Metadata](#metadata)

## Getting Started

Let's get started by adding HoneyEQL to your project.

[![Clojars Project](https://clojars.org/org.graphqlize/honeyeql/latest-version.svg)](https://clojars.org/org.graphqlize/honeyeql)

In addition, you will need to add dependencies for the JDBC drivers you wish to use for whatever databases you are using and preferably a connection pooling library like [HikariCP](https://github.com/tomekw/hikari-cp) or [c3p0](https://github.com/bostonaholic/clojure.jdbc-c3p0).

This documentation uses [deps](https://clojure.org/guides/deps_and_cli) and assumes you are connecting to the the sakila database created from [this JOOQ's example repository](https://github.com/jOOQ/jOOQ/tree/master/jOOQ-examples/Sakila).

```clojure
;; deps.edn
{:paths ["src"]
 :deps  {org.graphqlize/honeyeql     {:mvn/version "0.1.0-alpha16"}
         hikari-cp                   {:mvn/version "2.10.0"}
         org.postgresql/postgresql   {:mvn/version "42.2.8"}
         mysql/mysql-connector-java  {:mvn/version "8.0.19"}}}
```

The next step is initializing the `db-adapter` using either a [db-spec-map](https://cljdoc.org/d/seancorfield/next.jdbc/1.0.409/doc/getting-started#the-db-spec-hash-map) or a [DataSource](https://docs.oracle.com/javase/7/docs/api/javax/sql/DataSource.html).

### Postgres with db-spec map

```clojure
(ns core
  (:require [honeyeql.db :as heql-db]))

(def db-adapter (heql-db/initialize {:dbtype   "postgres"
                                     :dbname   "sakila"
                                     :user     "postgres"
                                     :password "postgres"}))
```

### MySQL with a data source (via connection pool)

```clojure
(ns core
  (:require [honeyeql.db :as heql-db]
            [hikari-cp.core :as hikari]))

(def db-adapter
  (heql-db/initialize
    (hikari/make-datasource
      {:server-name       "localhost"
       :maximum-pool-size 1
       :jdbc-url          "jdbc:mysql://localhost:3306/sakila"
       :driver-class-name "com.mysql.cj.jdbc.MysqlDataSource"
       :username          "root"
       :password          "mysql123"})))
```

Then we query the database using either `query-single` to retrieve a single item or `query` to retrieve multiple items.

```clojure
(ns core
  (:require ; ...
            [honeyeql.core :as heql]))

; ...
(heql/query-single
  db-adapter
  [{[:actor/actor-id 1] [:actor/first-name
                         :actor/last-name]}])
; returns
{:actor/first-name "PENELOPE"
 :actor/last-name  "GUINESS"}

(heql/query
  db-adapter
  [{[] [:language/name]}])
; returns
({:language/name "English"} {:language/name "Italian"}
 {:language/name "Japanese"} {:language/name "Mandarin"}
 {:language/name "French"} {:language/name "German"})
```

Supports all kind of relationships as well

### one-to-one relationship

![](https://www.graphqlize.org/img/address_city_country_er_diagram.png)

```clojure
(heql/query-single
  db-adapter
  [{[:city/city-id 3] [:city/city
                       {:city/country [:country/country]}]}])
```

### one-to-many relationship

![](https://www.graphqlize.org/img/address_city_country_er_diagram.png)

```clojure
(heql/query-single
  db-adapter
  [{[:country/country-id 2] [:country/country
                             {:country/cities [:city/city]}]}])
```

### many-to-many relationship

![](https://www.graphqlize.org/img/film_actor_er_diagram.png)

```clojure
(heql/query-single
  db-adapter
  [{[:actor/actor-id 148] [:actor/first-name
                           {:actor/films [:film/title]}]}])
```

### Pagination

#### Limit and Offset

```clojure
(heql/query
  db-adapter
  '[{([] {:limit 2 :offset 2})
     [:actor/actor-id :actor/first-name]}])
; returns
({:actor/actor-id 3, :actor/first-name "ED"}
 {:actor/actor-id 4, :actor/first-name "JENNIFER"})
```

Both `limit` and `offset` can be applied on `one-to-many` and `many-to-many` relationships as well.

```clojure
(heql/query-single
  db-adapter
  '[{[:country/country-id 2]
     [:country/country
      ; one-to-many relationship
      {(:country/cities {:limit 2 :offset 2})
       [:city/city]}]}])
```

```clojure
(heql/query
  db-adapter
  '[{[:actor/actor-id 148]
     [:actor/first-name
     ; many-to-many relationship
     {(:actor/films {:limit 1 :offset 2})
       [:film/title]}]}])
```

### Sorting

HoneyEQL supports sorting using the `:order-by` parameter. It takes a vector similar to HoneySQL and transform that to a corresponding `ORDER BY` SQL clause to sort the return value.

```clojure
; sorting by :language/name
(heql/query
  db-adapter
  '[{([] {:order-by [:language/name]}) 
     [:language/name]}])
; returns
({:language/name "English"} {:language/name "French"} {:language/name "German"}
 {:language/name "Italian"} {:language/name "Japanese"}  {:language/name "Mandarin"})
```

```clojure
; sorting by :language/name in descending order
(heql/query
  db-adapter
  '[{([] {:order-by [[:language/name :desc]]}) ; vector of vector!
     [:language/name]}])
; returns
({:language/name "Mandarin"} {:language/name "Japanese"} {:language/name "Italian"}
 {:language/name "German"} {:language/name "French"}  {:language/name "English"})
```

```clojure
; sorting by multiple attributes
; :actor/first-name is ascending order and then :actor/last-name in descending order
(heql/query
  db-adapter
  '[{([] {:order-by [:actor/first-name [:actor/last-name :desc]]
          :limit    2}) 
     [:actor/first-name :actor/last-name]}])
; returns
({:actor/first-name "ADAM" :actor/last-name  "HOPPER"} 
 {:actor/first-name "ADAM" :actor/last-name  "GRANT"})
```

We can sort the relationship query results as well.

```clojure
; sorting one-to-many relationship query results
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
(heql/query
  db-adapter
  '[{[:actor/actor-id 148] 
     [:actor/first-name
      ; sorting `:actor/films` by `:film/title` in descending order   
      {(:actor/films {:order-by [[:film/title :desc]]}) 
       [:film/title]}]}])
```

> **NOTE:** Currently, soring the relationship query results is not supported in MySQL


### Filtering

HoneyEQL supports filtering using the `:where` parameter. This parameter takes the value similar to HoneySQL's a `where` clause expect that instead of column name, we'll be using the attribute ident.

```clojure
(heql/query
  db-adapter
  `[{([] 
      ; HoneySQL: {:where [:= city_id 3]}
      {:where [:= :city/city-id 3]}) 
     [:city/city]}])
```

Some sample queries

```clojure
; Not Equal To
[{([] {:where [:<> :language/name "English"]}) 
  [:language/name]}]
```

```clojure
; Greater than
[{([] {:where [:> :payment/amount 11.99M]}) 
  [:payment/rental-id]}]
```

```clojure
; Greater than and equal to
[{([] {:where [:>= :payment/amount 11.99M]}) 
  [:payment/rental-id]}]
```

```clojure
; Less than
[{([] {:where [:< :payment/amount 11.99M]}) 
  [:payment/rental-id]}]
```

```clojure
; Less than and equal to
[{([] {:where [:<= :payment/amount 11.99M]}) 
  [:payment/rental-id]}]
```

Date, Time & TimeStamp values can be used either as string or the using their corresponding type defined [in this mapping](#type-mappings).

```clojure
; Between two timestamps as strings
[{([] {:where [:between :payment/payment-date "2005-08-23T21:00:00" "2005-08-23T21:03:00"]}) 
  [:payment/rental-id]}]
```
```clojure
; Between two timestamps as LocalDateTime
(let [from (LocalDateTime/parse "2005-08-23T21:00:00")
      to (LocalDateTime/parse "2005-08-23T21:03:00")]
  (heql/query db-adapter
              `[{([] {:where [:between :payment/payment-date ~from ~to]}) 
                [:payment/rental-id]}]))
```

The same logic applies for UUIDs as well

```clojure
; in filter with implicit type coercion
[{([] {:where [:in :customer/id ["847f09a7-39d1-4021-b43d-18ceb7ada8f6" "e5156dce-58ff-44f5-8533-932a7250bd29"]]}) 
  [:customer/first-name]}]
```

```clojure
; not-in filter with explicit type
(let [customer-ids [#uuid "847f09a7-39d1-4021-b43d-18ceb7ada8f6"
                    #uuid "e5156dce-58ff-44f5-8533-932a7250bd29"]]
  (db/query 
   db-adapter 
   `[{([] {:where [:not-in :customer/id ~customer-ids]}) [:customer/first-name]}]))
```

We can also filter the results using logical operators `and`, `or` & `not`.

```clojure
[{([] {:where [:and 
                [:= :payment/customer-id 1] 
                [:> :payment/amount 5.99M]]}) 
  [:payment/payment-id :payment/amount]}]  
```

```clojure
[{([] {:where [:or 
                [:= :language/name "English"] 
                [:= :language/name "French"]]}) 
  [:language/id :language/name]}]  
```

```clojure
[{([] {:where [:not 
                [:or 
                  [:= :language/name "English"] 
                  [:= :language/name "French"]]]}) 
  [:language/language-id :language/name]}]
```

#### Filter Based On Relationship Attributes

With HoneyEQL, we can filter the results based on the attributes of a relationship. The only difference in the syntax is, in the place of the attribute ident, we will be using a vector of two attribute idents. The first ident is the relationship attribute and then second one is the attribute of the related entity. 

For example, to get all the cities of a county using the country' name,

![](https://www.graphqlize.org/img/address_city_country_er_diagram.png)

we can use the following query.

```clojure
; filtering by one-to-one relationship attribute
[{([] {:where [:= [:city/country :country/country] "Algeria"]}) 
  [:city/city-id :city/city]}]
```

If the relationship attribute is refers a one-to-many or many-to-many relationship, the filter condition yield the results if **any** of the related entities satisfy the condition.

For the above schema, we can get a list of countries which has at-least one city that starts with `Ab`.

```clojure
; filtering by one-to-many relationship attribute
[{([] {:where [:like [:country/cities :city/city] "Ab%"]}) 
 [:country/country-id :country/country]}]
```

For many-to-many relationships also, the query looks similar.

![](https://www.graphqlize.org/img/film_actor_er_diagram.png)


For the above schema, to get the actors who are part of at-lease one film which has the word `LIFE` in its title.

```clojure
; filtering by many-to-many relationship attribute
[{([] {:where [:like [:actor/films :film/title] "%LIFE%"] }) 
 [:actor/first-name :actor/last-name]}]
```

If we want to retrieve only certain entities only if **all** of its related entities satisfy the condition, then we need to used the `:not` and the reverse the filter condition together.

Let's assume that we have schema like below 

![](https://www.graphqlize.org/img/author_course_er_diagram.png)

To filter authors who has **at-least** one course with the rating `5`, we can achieve it using the following query.

```clojure
[{([] {:where [:= [:author/courses :course/rating] 5]}) 
  [:author/first-name :author/last-name]}]
```

If we want to filter only the authors who has got the rating `5` in all their courses, we can achieve it by

```clojure
[{([] {:where [:not [:<> [:author/courses :course/rating] 5]]}) 
  [:author/first-name :author/last-name]}]
```


#### Filtering Relationships

We can filter the relationships as well!

```clojure
[{[:country/country-id 2] 
  [:country/country
   ; filtering one-to-many relationship
   {(:country/cities {:where [:= :city/city "Batna"]}) 
    [:city/city-id :city/city]}]}]
; returns
{:country/country "Algeria"
 :cities [{:city/city-id 59 :city/city "Batna"}]}
```

```clojure
[{[:actor/actor-id 148] 
  [:actor/first-name
   {(:actor/films {:where [:= :film/title "SEA VIRGIN"]}) 
    [:film/title]}]}]
; returns
{:actor/first-name "EMILY"
 :actor/films [{:film/title "SEA VIRGIN"}]})
```


### Type Mappings

While retrieving the data from the database, HoneyEQL coerce the return value to the corresponding JVM type as mentioned in the below table.

| Type             | Postgres                                                                                   | MySQL                                                                                                 |
| ---------------- | ------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------- |
| java.lang.Long   | `integer`, `int`, `int2` `int4`, `smallint`, `smallserial`, `serial`, `serial2`, `serial4`, `bigint`,`int8`,`bigserial`,`serial8` | `SMALLINT`, `MEDIUMINT`, `INT`, `TINYINT UNSIGNED`, `SMALLINT UNSIGNED`, `MEDIUMINT UNSIGNED`, `YEAR`, `INT UNSIGNED`, `BIGINT` |
| java.math.BigDecimal |  `real`, `float4`, `float8`, `double precision`,`numeric`,`decimal`                                                                                        |           `REAL`, `FLOAT`, `DOUBLE`, `DECIMAL`, `NUMERIC`                                                                                            |
|   java.lang.String               |     `bit`, `bit varying`, `char`, `character varying`, `varchar`, `citext`, `bpchar`, `macaddr8`, `text`, `money`                                                                                       |       `CHAR`, `VARCHAR`, `TINYTEXT`, `TEXT`, `MEDIUMTEXT`, `LONGTEXT`, `ENUM`, `SET`, `BINARY`, `VARBINARY`, `TINYBLOB,` `BLOB`, `LONGBLOB`, `BIT`                                                                                                |
| java.lang.Boolean | `boolean` | `TINYINT(1)`, `BIT(1)`|
|          java.util.UUID        |          `uuid`                                                                                  |                                                 --                                                      |
| java.time.LocalDate | `date`| `DATE`|
| java.time.LocalTime | `time`, `time without time zone`| `TIME`|
| java.time.OffsetTime | `timetz`, `time with time zone` | --|
| java.time.LocalDateTime | `timestamp`, `timestamp without time zone` | `DATETIME`, `TIMESTAMP` |
| java.time.OffsetDateTime| `timestamptz`, `timestamp with time zone` | -- | 

## Metadata

In addition to querying, HoneyEQL supports quering the metadata of the database also.

```clojure
(heql/meta-data db-adapter)
; returns
{:entities ...
 :attributes ...
 :namespaces ...}
```

The visual representation of the above data for the Postgres Sakila database is available in the below links

- [Entities](https://www.graphqlize.org/html/entities.html)
- [Attributes](https://www.graphqlize.org/html/attributes.html)
