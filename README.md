## HoneyEQL

HoneyEQL is a Clojure library enables you to query database using the [EDN Query Language](https://edn-query-language.org)(EQL). HoneyEQL transforms the EQL into single efficient SQL and query the database using [next.jdbc](https://github.com/seancorfield/next-jdbc).

HoneyEQL powers [GraphQLize](https://www.graphqlize.org).

[![Clojars Project](https://img.shields.io/clojars/v/org.graphqlize/honeyeql.svg)](https://clojars.org/org.graphqlize/honeyeql) <a href="https://discord.gg/akkdPqf"><img src="https://img.shields.io/badge/chat-discord-brightgreen.svg?logo=discord&style=flat"></a>

> CAUTION: HoneyEQL is at its early stages now. **It is not production-ready yet!**. It currently supports Postgres (9.4 & above) and MySQL (8.0 & above) only.

## Supported Features

- Query by Primary Key(s)
- Query entire table
- Query 1-1, 1-n and m-n relationships

## Upcoming Features

- Filters
- Sorting
- Aggregate Queries
- DML Queries

## Table of contents

- [Getting Started](#getting-started)
  - Queries
    - [one-to-one relationship](#one-to-one-relationship)
    - [one-to-many relationship](#one-to-many-relationship)
    - [many-to-many relationship](#many-to-many-relationship)
    - Pagination
      - [limit and offset](#limit-and-offset)
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
 :deps  {org.graphqlize/honeyeql     {:mvn/version "0.1.0-alpha7"}
         hikari-cp                   {:mvn/version "2.10.0"}
         org.postgresql/postgresql   {:mvn/version "42.2.8"}
         mysql/mysql-connector-java  {:mvn/version "8.0.19"}}}
```

The next step is initializing the `db-adapter` using either [db-spec-map](https://cljdoc.org/d/seancorfield/next.jdbc/1.0.409/doc/getting-started#the-db-spec-hash-map) or

### Postgres with db-spec map

```clojure
(ns core
  (:require [honeyeql.db :as heql-db]))

(def db-adapter (heql-db/initialize {:dbtype   "postgres"
                                     :dbname   "sakila"
                                     :user     "postgres"
                                     :password "postgres"}))
```

### MySQL with db connection pool

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
  [{'([] {:limit 2 :offset 2})
   [:actor/actor-id :actor/first-name]}])
; returns
({:actor/actor-id 3, :actor/first-name "ED"}
 {:actor/actor-id 4, :actor/first-name "JENNIFER"})
```

Both `limit` and `offset` can be applied on `one-to-many` and `many-to-many` relationships as well.

```clojure
(heql/query-single
  db-adapter
  [{[:country/country-id 2]
    [:country/country
     ; one-to-many relationship
     {'(:country/cities {:limit 2 :offset 2})
       [:city/city]}]}])
```

```clojure
(heql/query
  db-adapter
  [{[:actor/actor-id 148]
    [:actor/first-name
    ; many-to-many relationship
    {'(:actor/films {:limit 1 :offset 2})
      [:film/title]}]}])
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
