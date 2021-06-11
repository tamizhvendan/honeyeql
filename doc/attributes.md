# Attributes

Attributes are the building blocks of the HoneyEQL queries. During initialization, HoneyEQL reads the database metadata and creates attributes (generation phase) of each columns and relationships in the database. Then while querying, it uses these attributes to create the appropriate SQL query (resolution phase).

This section documents the convention behind the naming of attributes in the generation phase.

## What is an attribute?

HoneyEQL uses the terminology `entity` to represent a `table` & `attribute` to represent either a `column` or a `relationship`.

The attributes that are referring a column are called as **column attributes** and ones that represent a relationship are called as **join attributes**.

An attribute is represented using Clojure's namespaced keyword.

```clojure
:{table-name-in-kebab-case}/{column-or-relationship-name-in-kebab-case}
```

An attributes namespace namespace is the singularized, kebab-case version of the corresponding table or view name.

| Table/View Name | Attribute Namespace |
| --------------- | ------------------- |
| actor           | actor               |
| film_actor      | film-actor          |
| comments        | comment             |

HoneyEQL supports Postgres [schema](https://www.postgresql.org/docs/current/ddl-schemas.html). If the schema in question is not a default schema (`public`), then it will be used as a prefix in the Attribute namespace.

| Schema Name     | Table/View Name | Attribute namespace      |
| --------------- | --------------- | ------------------------ |
| person          | state_province  | person.state-province    |
| human_resources | employee        | human-resources.employee |

> **NOTE**: The table name is alone singularized and not the schema name.

The actual keyword part of an attribute refers to the column name in kebab-case.

| Schema Name     | Table/View Name | Column Name | Attribute                    |
| --------------- | --------------- | ----------- | ---------------------------- |
| public          | actor           | first_name  | :actor/first-name            |
| public          | film_actor      | actor_id    | :film-actor/actor-id         |
| public          | comments        | authorId    | :comment/author-id           |
| person          | state_province  | name        | :person.state-province/name  |
| human_resources | employee        | id          | :human-resources.employee/id |

> **NOTE**: MySQL doesn't support schema.

## Join (Relationship) Attributes

HoneyEQL identifies the relationships between database tables using their foreign keys, and generate appropriate join (relationship) attributes.

### One to Many

![](https://www.graphqlize.org/img/address_city_country_er_diagram.png)

For the database table relationships like above, HoneyEQL infers two `one-to-many` relationships.

- A `city` has many `address` (addresses) via `city_id` column in the `address` table.

- A `country` has many `city` (cities) via `country_id` column in the `city` table.

HoneyEQL then generate two join attributes.

```clojure
:city/addresses
:country/cities
```

The generated field name is the *kebab-case* version of the corresponding target table's pluralized name.

If a table has multiple foreign keys to the same table as below,

```sql
CREATE TABLE language (
  language_id serial PRIMARY KEY,
  name character(20) NOT NULL
);

CREATE TABLE film (
  film_id serial PRIMARY KEY,
  title character varying(255) NOT NULL,

  language_id smallint NOT NULL REFERENCES language(language_id),
  original_language_id smallint REFERENCES language(language_id)
);
```

HoneyEQL creates two `one-to-many` join attributes to represent these relationship between `language` and `films`. 

* The `language` has many `film` (films) via `language_id` column in the `film` table. 
* The `language` has many `film` (films) via `original_language_id` column in the `film` table.

```clojure
:language/films
:language/original-language-films
```

The nomenclature used here to generate the field name follows the below logic.

If the column name (`language_id`) after the removal of the foreign key suffix (`language`) matches the source table name, then the resulting field name is the *kebab-case* version of the pluralized form of the target table (`films`).

If the column name (`original_language_id`) after the removal of the foreign key suffix (`original_language`) did not match the source table name, then HoneyEQL removes the foreign key suffix and concatenate with the pluralized form of the target table and then convert it to its *kebab-case* version (`original-language-films`).

### One to One (Reverse side of One to Many)

![](https://www.graphqlize.org/img/address_city_country_er_diagram.png)

For the database table relationships like above, HoneyEQL infers two `one-to-one` relationships.

- An `address` is associated with a `city` via `city_id` column in the `address` table.

- A `city` is associated with a `country` via `country_id` column in the `city` table.

In this scenario, HoneyEQL generates two attributes representing these two relationships.

```clojure
:address/city
:city/country
```

The generated attribute keyword is the kebab-case version of the corresponding column name with the id suffix (\_id) removed.

Here are some of the examples.

| Table/View Name | Column Name            | Attribute                   |
| --------------- | ---------------------- | --------------------------- |
| film_actor      | actor_id               | :film-actor/actor           |
| film            | original_language_id   | :film/originalLanguage      |
| employee        | reports_to_employee_id | :employee/reportsToEmployee |

By default, HoneyEQL assumes `_id` as the suffix for foreign keys in both Postgres and MySQL.

> In future, HoneyEQL will provide a configuration to override this default behavior.

### One to One

HoneyEQL infers one to one relationship if the primary key and a foreign key of a table are same. 

![](https://www.graphqlize.org/img/one-to-one-relationship.png)

For the above DB schema, HoneyEQL infers two `one-to-one` relationships.

- An `site` is associated with a `site-meta-dataum` via `site_id` column in the `site-meta-dataum` table.
- An `site-meta-dataum` is associated with a `site` via `site_id` column in the `site-meta-dataum` table.

For this example, HoneyEQL generates two attributes

```clojure
:site/site-meta-datum
:site-meta-datum/site
```

### Many to Many

The `many-to-many` relationship is a bit tricky to figure out. 

HoneyEQL traverses each table's metadata to figure out whether it is an [associative table](https://en.wikipedia.org/wiki/Associative_entity) or not. 

A table is considered as an associative table if it satisfies the following two criteria

1. It should have a primary key made of two columns.
2. These primary key columns should be a subset of the foreign key columns present in that table. 

For the database table relationships like below,

![](https://www.graphqlize.org/img/film_actor_er_diagram.png)

The `film_actor` table is an associative table as

1. It has `actor_id` and `film_id` as the primary keys.
2. The primary keys `{actor_id, film_id}` is a subset of foreign keys `{actor_id, film_id}` of the `film_actor` table. 

In this scenario, HoneyEQL creates two join attributes for these two `many-to-many` relationships.  

```clojure
:film/actors
:actor/films
```

For the relationships between `film` & `film_actor` and `film_actor` & `actor` tables, HoneyEQL generates the following attributes.

```clojure
; one to many
:film/film-actors

; one to one
:film-actor/film
:film-actor/actor

; one to many
:actor/film-actors
```

### Foreign Key Without the Id Suffix

If the foreign key in question doesn't have the id suffix `_id`, then the HoneyEQL follows a slightly different approach to name the attributes.

Say, we have a below schema

```sql
CREATE TABLE continent (
  id SERIAL PRIMARY KEY,
  name TEXT NOT NULL
);
CREATE TABLE country (
  id SERIAL PRIMARY KEY,
  name TEXT NOT NULL,
  continent_identifier int REFERENCES continent(id)
);
```

The `continent_identifier` column doesn't have the foreign key suffix (`_id`). 

For the `one-to-one` relationship, HoneyEQL creates a field with the name `continentByContinentIdentifer`. 

The convention is `{targetTableNameInKebabCase}-by-{FKeyColumnNameInKebabCase}`.

```clojure
:country/continent-by-continent-identifier
```

On the `one-to-many` side, the attribute is the concatenation of the *kebab-case* version of the foreign key column name (`continent-identifer`) with the pluralized form of the target table (`countries`).

```clojure
:continent/continent-identifer-countries
```
