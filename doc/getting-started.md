# Getting Started

Let's get started by adding HoneyEQL to your project.

[![Clojars Project](https://clojars.org/org.graphqlize/honeyeql/latest-version.svg)](https://clojars.org/org.graphqlize/honeyeql)

In addition, you will need to add dependencies for the JDBC drivers you wish to use for whatever databases you are using and preferably a connection pooling library like [HikariCP](https://github.com/tomekw/hikari-cp) or [c3p0](https://github.com/bostonaholic/clojure.jdbc-c3p0).

This documentation uses [deps](https://clojure.org/guides/deps_and_cli) and assumes you are connecting to the the sakila database created from [this JOOQ's example repository](https://github.com/jOOQ/jOOQ/tree/master/jOOQ-examples/Sakila).

```clojure
;; deps.edn
{:paths ["src"]
 :deps  {org.graphqlize/honeyeql     {:mvn/version "0.1.0-alpha31"}
         hikari-cp                   {:mvn/version "2.10.0"}
         org.postgresql/postgresql   {:mvn/version "42.2.8"}
         mysql/mysql-connector-java  {:mvn/version "8.0.19"}}}
```

The next step is initializing the `db-adapter` using either a [db-spec-map](https://cljdoc.org/d/seancorfield/next.jdbc/1.0.409/doc/getting-started#the-db-spec-hash-map) or a [DataSource](https://docs.oracle.com/javase/7/docs/api/javax/sql/DataSource.html).

## Postgres with db-spec map

```clojure
(ns core
  (:require [honeyeql.db :as heql-db]))

(def db-adapter (heql-db/initialize {:dbtype   "postgres"
                                     :dbname   "sakila"
                                     :user     "postgres"
                                     :password "postgres"}))
```

## MySQL with a data source (via connection pool)

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
```

```clojure
; ...
; select first_name, last_name 
; from actor 
; where actor_id = 1
(heql/query-single
  db-adapter
  {[:actor/actor-id 1] [:actor/first-name
                        :actor/last-name]})
; returns
{:actor/first-name "PENELOPE"
 :actor/last-name  "GUINESS"}
```

```clojure
; select name
; from language
(heql/query
  db-adapter
  {[] [:language/name]})

; returns
({:language/name "English"} {:language/name "Italian"}
 {:language/name "Japanese"} {:language/name "Mandarin"}
 {:language/name "French"} {:language/name "German"})
```