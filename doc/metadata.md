# Metadata

In addition to querying, HoneyEQL supports querying the metadata of the database also.

```clojure
(:heql-meta-data db-adapter)

; returns
{:entities ...
 :attributes ...
 :namespaces ...}
```

The visual representation of the above data for the Postgres Sakila database is available in the below links

- [Entities](https://www.graphqlize.org/html/entities.html)
- [Attributes](https://www.graphqlize.org/html/attributes.html)
