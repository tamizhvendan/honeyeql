# Query Syntax

HoneyEQL uses [EDN Query Language](https://edn-query-language.org)(EQL) to query the database declaratively. This query language is inspired from [GraphQL](https://graphql.org/) and the Datomic's [Pull API](https://docs.datomic.com/on-prem/pull.html).

HoneyEQL supports two modes of using EQL.

1. `:eql.mode/strict` - The query should adhere to the specifications of EQL. 
2. `:eql.mode/lenient` - It supports both EQL specifications and HoneyEQL overrides of EQL specifications for ease of use.

Based on your requirements, you can choose between either of these during the initialization of HoneyEQL. By default HoneyEQL supports `:eql.mode/lenient`.

This section documents the usage of EDN Query Language and its overrides in the context of using it with HoneyEQL. 

> This section assumes that you are familiar with HoneyEQL's [attributes](./attributes.md)

## Selecting Attributes

To select the list of attributes that we want to see in the output, we'll be using a vector of attributes.

```clojure
[:customer/customer-id
 :customer/first-name 
 :customer/last-name]
```

### HoneyEQL Override

The EQL specification is agonistic of the data storage and hence it doesn't provide anything specific to database querying. As per the EQL standard, if we want to select (query) all the columns of a table, we need to specify it explicitly.

```clojure
[:actor/actor-id
 :actor/first-name
 :actor/last-name
 :actor/last-update]
```
If a table has a lot of columns, specifying all of them in the select vector may be hard. Hence HoneyEQL provides a override to select all the columns of a table using the special attribute `*`.  

```clojure
[:actor/*]
```

During query resolution, HoneyEQL replaces this attribute with all the attributes of the corresponding table. 

### Selecting Join Attributes

If the attribute that we want to select is a join (relationship) attribute, then we will be using a Clojure map with a single key-value pair. The key will be the join attribute and the value will be the vector of attributes that we want to select from the related entity. 

```clojure
[:customer/customer-id
 :customer/first-name 
 :customer/last-name
 ; one to many 
 {:customer/rentals
  [:rental/rental-id
   :rental/rental-date]}]
```

We can have more than one join attributes as well.

```clojure
[:customer/customer-id
 :customer/first-name 
 :customer/last-name
 ; one to one
 {:customer/address
  [:address/postal-code
   :address/phone]}
 ; one to many 
 {:customer/rentals
  [:rental/rental-id
   :rental/rental-date]}
 ; many to many 
 {:customer/payments
  [:payment/payment-id
   :payment/payment-date
   :payment/amount]}]
```

## Lookup using Idents

The next step after defining the vector of attributes that want to query is to specify the lookup using [Idents](https://edn-query-language.org/eql/1.0.0/specification.html#_idents).

An ident is a vector with an **even number** of elements. The elements at the even positions are attributes and the odd positions contain the value using which we are going to lookup for.

```clojure
; select attributes of a customer with the id `148`
[:customer/customer-id 148]
```

```clojure
; select attributes of a film-actor 
; with the film-id `1` and the actor-id `2`
[:film-actor/film-id 1 :film-actor/actor-id 2]
```

If we don't want to narrow down by any attributes, then ident will be an empty vector

```clojure
; select attributes without any specific filter
[]
```

This idents and the attributes selection vector together forms a query in EQL. The query is a vector with only one map as element. The map inside the vector contains has a single key-value pair. The key represent the ident and the value contains the vector of attributes that we want to select.

```clojure
; select customer_id, first_name, last_name
; from customer
; where customer_id = 148

[{[:customer/customer-id 148] ; ident as key
  [:customer/customer-id  ; vector of attributes as value
   :customer/first-name 
   :customer/last-name]}]
```

```clojure
; select customer_id, first_name, last_name
; from customer

[{[] ; ident as key
  [:customer/customer-id  ; vector of attributes as value
   :customer/first-name 
   :customer/last-name]}]
```

### HoneyEQL Override

In the above example, the outside vector will always be a vector with only one map when using it with HoneyEQL. Even if it contains any other elements, only the first item will be taken into consideration for querying the database. 

The actual EQL spec is meant for specifying multiple queries and hence a vector made sense. However with respect to HoneyEQL, if want to run only one query, this vector is redundant. Hence in the `:eql.mode/lenient` mode, the wrapping vector is optional. 

The above queries can also be written as 

```clojure
{[:customer/customer-id 148]
 [:customer/customer-id
  :customer/first-name 
  :customer/last-name]}
```

```clojure
{[]
 [:customer/customer-id
  :customer/first-name 
  :customer/last-name]}
```

## Customizing Queries Via Parameters

More often, we need to customize the query to include sorting, pagination and filtering. EQL provides parameters to enable this.

A parameter is a Clojure `list` with the two elements. 

1. The first element is either an `ident` or a `join attribute`. 
2. The second element is a map.

Here are some examples of using parameter with an ident.

```clojure
{([:customer/customer-id 148] {:order-by [:customer/first-name]})
 [:customer/customer-id
  :customer/first-name 
  :customer/last-name]}

{([] {:order-by [:customer/first-name]})
 [:customer/customer-id
  :customer/first-name 
  :customer/last-name]}
```

and the following examples uses parameters on the join attributes.

```clojure
{[:actor/actor-id 148] 
 [:actor/first-name
  {(:actor/films {:order-by [[:film/title :desc]]}) 
   [:film/title]}]}
```

### HoneyEQL Override

The EQL specification uses Clojure list for defining parameters. Because of this while using we need to use [Quote](https://clojure.org/guides/weird_characters#_quote) to prevent it from being treated as function.

```clojure
(heql/query
  db-adapter
  '{[:actor/actor-id 148] ; ignoring the quote here will return an error
    [:actor/first-name
     {(:actor/films {:order-by [[:film/title :desc]]}) 
      [:film/title]}]})
```

If the query involves any dynamic parameter, then we need to use [Syntax Quote](https://clojure.org/guides/weird_characters#syntax_quote) along with [Unquote](https://clojure.org/guides/weird_characters#unquote).

```clojure
(let [actor-id 148]
  (heql/query
    db-adapter
    `{[:actor/actor-id ~actor-id] ; syntax quote + unquote
      [:actor/first-name
      {(:actor/films {:order-by [[:film/title :desc]]}) 
        [:film/title]}]}))
```

In a real-world application, these scenarios are more prevalent and hence HoneyEQL overrides this parameter specification by using a **vector instead of a list**.

The above query using a vector in the `:eql.mode/lenient` mode would look like

```clojure
(let [actor-id 148]
  (heql/query
    db-adapter
    {[:actor/actor-id actor-id]
      [:actor/first-name
      {[:actor/films {:order-by [[:film/title :desc]]}]
        [:film/title]}]}))
```

It works on parameters on the idents as well.

```clojure
{[[:customer/customer-id 148] {:order-by [:customer/first-name]}]
 [:customer/customer-id
  :customer/first-name 
  :customer/last-name]}
```