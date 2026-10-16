# Coercion

HoneyEQL converts values as they cross the boundary between the application and the database.

There are two coercion directions:

```text
application
    ↓
  :to-db
    ↓
database

database
    ↓
 :from-db
    ↓
application
```

HoneyEQL provides default coercion based on the database column type.

While retrieving data from the database, HoneyEQL coerces returned values to the corresponding JVM types described below.

[keep the existing type table here]

## Custom Attribute Coercion

An application may override the default coercion for a specific HoneyEQL attribute.

Configure attribute coercions when initializing the database adapter:

```clojure
(def db-adapter
  (heql-db/initialize
   db-spec
   {:attr/coercions
    {:certificate/item-type
     {:to-db name
      :from-db keyword}}}))
```

In this example, the application represents `:certificate/item-type` as a keyword while the database stores it as text.

Writing:

```clojure
(mutation/insert!
 db-adapter
 #:certificate{:item-type :ssl})
```

passes the value through:

```text
:ssl
  ↓ :to-db
"ssl"
  ↓
database
```

Reading:

```clojure
(heql/query-single
 db-adapter
 {[:certificate/id 1]
  [:certificate/item-type]})
```

passes the database value through:

```text
"ssl"
  ↓ :from-db
:ssl
```

and returns:

```clojure
#:certificate{:item-type :ssl}
```

The same `:to-db` coercion is also used when an attribute value participates in a query condition.

For example:

```clojure
(heql/query
 db-adapter
 {[:certificate/item-type :ssl]
  [:certificate/id]})
```

coerces `:ssl` using the configured `:to-db` function before producing the SQL parameter.

This keeps the attribute's application representation consistent across both mutations and queries.

## Coercion Precedence

For each attribute and direction:

```text
custom attribute coercion configured?
        │
        ├── yes → use custom coercion
        │
        └── no  → use HoneyEQL default coercion
```

Custom coercion is attribute-specific.

Configuring coercion for:

```clojure
:certificate/item-type
```

does not change the behavior of other text columns.

The `:to-db` and `:from-db` functions are independent. An application may override only one direction when that is sufficient.