# Filtering

HoneyEQL supports filtering using the `:where` parameter. This parameter takes the value similar to HoneySQL's a `where` clause expect that instead of column name, we'll be using the attribute ident.

```clojure
; :eql.mode/lenient syntax
(heql/query
  db-adapter
  {[[] 
    ; HoneySQL: {:where [:= city_id 3]}
    {:where [:= :city/city-id 3]}]
    [:city/city]})

; :eql.mode/strict syntax
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

; :eql.mode/lenient syntax
{[[] {:where [:<> :language/name "English"]}]
 [:language/name]}

; :eql.mode/strict syntax
[{([] {:where [:<> :language/name "English"]}) 
  [:language/name]}]
```

```clojure
; Greater than

; :eql.mode/lenient syntax
{[[] {:where [:> :payment/amount 11.99M]}]
 [:payment/rental-id]}

; :eql.mode/strict syntax
[{([] {:where [:> :payment/amount 11.99M]}) 
  [:payment/rental-id]}]
```

```clojure
; Greater than and equal to

; :eql.mode/lenient syntax
{[[] {:where [:>= :payment/amount 11.99M]}]
 [:payment/rental-id]}

; :eql.mode/strict syntax
[{([] {:where [:>= :payment/amount 11.99M]}) 
  [:payment/rental-id]}]
```

```clojure
; Less than

; :eql.mode/lenient syntax
{[[] {:where [:< :payment/amount 11.99M]}]
 [:payment/rental-id]}

; :eql.mode/strict syntax
[{([] {:where [:< :payment/amount 11.99M]}) 
  [:payment/rental-id]}]
```

```clojure
; Less than and equal to

; :eql.mode/lenient syntax
{[[] {:where [:<= :payment/amount 11.99M]}]
 [:payment/rental-id]}

; :eql.mode/strict syntax
[{([] {:where [:<= :payment/amount 11.99M]}) 
  [:payment/rental-id]}]
```

Date, Time & TimeStamp values can be used either as string or the using their corresponding type defined [in this mapping](#type-mappings).

```clojure
; Between two timestamps as strings

; :eql.mode/lenient syntax
{[[] {:where [:between :payment/payment-date "2005-08-23T21:00:00" "2005-08-23T21:03:00"]}] 
 [:payment/rental-id]}

; :eql.mode/strict syntax
[{([] {:where [:between :payment/payment-date "2005-08-23T21:00:00" "2005-08-23T21:03:00"]}) 
  [:payment/rental-id]}]
```
```clojure
; Between two timestamps as LocalDateTime

; :eql.mode/lenient syntax
(let [from (LocalDateTime/parse "2005-08-23T21:00:00")
      to   (LocalDateTime/parse "2005-08-23T21:03:00")]
  (heql/query db-adapter
              {[[] {:where [:between :payment/payment-date from to]}] 
                [:payment/rental-id]}))

; :eql.mode/strict syntax
(let [from (LocalDateTime/parse "2005-08-23T21:00:00")
      to (LocalDateTime/parse "2005-08-23T21:03:00")]
  (heql/query db-adapter
              `[{([] {:where [:between :payment/payment-date ~from ~to]}) 
                [:payment/rental-id]}]))
```

The same logic applies for UUIDs as well

```clojure
; in filter with implicit type coercion

; :eql.mode/lenient syntax
{[[] {:where [:in :customer/id ["847f09a7-39d1-4021-b43d-18ceb7ada8f6"  
                                "e5156dce-58ff-44f5-8533-932a7250bd29"]]}]
 [:customer/first-name]}

; :eql.mode/strict syntax
[{([] {:where [:in :customer/id ["847f09a7-39d1-4021-b43d-18ceb7ada8f6"  
                                "e5156dce-58ff-44f5-8533-932a7250bd29"]]}) 
 [:customer/first-name]}]
```

```clojure
; not-in filter with explicit type

; :eql.mode/lenient syntax
(let [customer-ids [#uuid "847f09a7-39d1-4021-b43d-18ceb7ada8f6"
                    #uuid "e5156dce-58ff-44f5-8533-932a7250bd29"]]
  (db/query 
   db-adapter 
   {[[] {:where [:not-in :customer/id customer-ids]}] 
    [:customer/first-name]}))

; :eql.mode/strict syntax
(let [customer-ids [#uuid "847f09a7-39d1-4021-b43d-18ceb7ada8f6"
                    #uuid "e5156dce-58ff-44f5-8533-932a7250bd29"]]
  (db/query 
   db-adapter 
   `[{([] {:where [:not-in :customer/id ~customer-ids]}) 
      [:customer/first-name]}]))
```

We can also filter the results using logical operators `and`, `or` & `not`.

```clojure
; :eql.mode/lenient syntax
{[[] {:where [:and 
                [:= :payment/customer-id 1] 
                [:> :payment/amount 5.99M]]}]
 [:payment/payment-id :payment/amount]}  

; :eql.mode/strict syntax
[{([] {:where [:and 
                [:= :payment/customer-id 1] 
                [:> :payment/amount 5.99M]]}) 
  [:payment/payment-id :payment/amount]}]  
```

```clojure
; :eql.mode/lenient syntax
{[[] {:where [:or 
                [:= :language/name "English"] 
                [:= :language/name "French"]]}]
 [:language/id :language/name]} 

; :eql.mode/strict syntax
[{([] {:where [:or 
                [:= :language/name "English"] 
                [:= :language/name "French"]]}) 
  [:language/id :language/name]}] 
```

```clojure
; :eql.mode/strict syntax
{[[] {:where [:not 
                [:or 
                  [:= :language/name "English"] 
                  [:= :language/name "French"]]]}] 
 [:language/language-id :language/name]}

; :eql.mode/strict syntax
[{([] {:where [:not 
                [:or 
                  [:= :language/name "English"] 
                  [:= :language/name "French"]]]}) 
  [:language/language-id :language/name]}]
```

## Filter Based On Relationship Attributes

With HoneyEQL, we can filter the results based on the attributes of a relationship. The only difference in the syntax is, in the place of the attribute ident, we will be using a vector of two attribute idents. The first ident is the relationship attribute and then second one is the attribute of the related entity. 

For example, to get all the cities of a county using the country' name,

![](https://www.graphqlize.org/img/address_city_country_er_diagram.png)

we can use the following query.

```clojure
; filtering by one-to-one relationship attribute

; :eql.mode/lenient syntax
{[[] {:where [:= [:city/country :country/country] "Algeria"]}]
 [:city/city-id :city/city]}

; :eql.mode/strict syntax
[{([] {:where [:= [:city/country :country/country] "Algeria"]}) 
  [:city/city-id :city/city]}]
```

If the relationship attribute is refers a one-to-many or many-to-many relationship, the filter condition yield the results if **any** of the related entities satisfy the condition.

For the above schema, we can get a list of countries which has at-least one city that starts with `Ab`.

```clojure
; filtering by one-to-many relationship attribute

; :eql.mode/lenient syntax
{[[] {:where [:like [:country/cities :city/city] "Ab%"]}]
 [:country/country-id :country/country]}

; :eql.mode/strict syntax
[{([] {:where [:like [:country/cities :city/city] "Ab%"]}) 
  [:country/country-id :country/country]}]
```

For many-to-many relationships also, the query looks similar.

![](https://www.graphqlize.org/img/film_actor_er_diagram.png)


For the above schema, to get the actors who are part of at-lease one film which has the word `LIFE` in its title.

```clojure
; filtering by many-to-many relationship attribute

; :eql.mode/lenient syntax
{[[] {:where [:like [:actor/films :film/title] "%LIFE%"] }]
 [:actor/first-name :actor/last-name]}

; :eql.mode/strict syntax
[{([] {:where [:like [:actor/films :film/title] "%LIFE%"] }) 
  [:actor/first-name :actor/last-name]}]
```

If we want to retrieve only certain entities only if **all** of its related entities satisfy the condition, then we need to used the `:not` and the reverse of the filter condition together.

Let's assume that we have a schema like below 

![](https://www.graphqlize.org/img/author_course_er_diagram.png)

To filter authors who has **at-least** one course with the rating `5`, we can achieve it using the following query.

```clojure
; :eql.mode/lenient syntax
{[[] {:where [:= [:author/courses :course/rating] 5]}] 
  [:author/first-name :author/last-name]}

; :eql.mode/strict syntax
[{([] {:where [:= [:author/courses :course/rating] 5]}) 
  [:author/first-name :author/last-name]}]
```

If we want to filter only the authors who has got the rating `5` in **all** their courses, we can achieve it by

```clojure
; :eql.mode/lenient syntax
{[[] {:where [:not [:<> [:author/courses :course/rating] 5]]}]
  [:author/first-name :author/last-name]}

; :eql.mode/strict syntax
[{([] {:where [:not [:<> [:author/courses :course/rating] 5]]}) 
  [:author/first-name :author/last-name]}]
```

## Filter Based On Relationship Attributes Existence

Using HoneyEQL, we can also filter by the existence of relationship attributes. 

For the above `author-course` schema, if we want to filter the authors who have at-least one course, we can query it as

```clojure
; :eql.mode/lenient syntax
{[[] {:where [:exists :author/courses]}]
 [:author/first-name :author/last-name]}

; :eql.mode/strict syntax
[{([] {:where [:exists :author/courses]}) 
 [:author/first-name :author/last-name]}]
```

The reverse is also possible by using it in conjunction with `:not`. i.e filtering authors who don't have any courses

```clojure
; :eql.mode/lenient syntax
{[[] {:where [:not [:exists :author/courses]]}]
 [:author/first-name :author/last-name]}

; :eql.mode/strict syntax
[{([] {:where [:not [:exists :author/courses]]}) 
 [:author/first-name :author/last-name]}]
```

## Filtering Relationships

We can filter the relationships as well!

```clojure
; :eql.mode/lenient syntax
{[:country/country-id 2] 
 [:country/country
  ; filtering one-to-many relationship
  {[:country/cities {:where [:= :city/city "Batna"]}] 
   [:city/city-id :city/city]}]}

; :eql.mode/strict syntax
[{[:country/country-id 2] 
  [:country/country
   ; filtering one-to-many relationship
   {(:country/cities {:where [:= :city/city "Batna"]}) 
    [:city/city-id :city/city]}]}]
```

```clojure
; :eql.mode/lenient syntax
{[:actor/actor-id 148] 
 [:actor/first-name
  ; filtering many-to-many relationship
  {[:actor/films {:where [:= :film/title "SEA VIRGIN"]}] 
   [:film/title]}]}

; :eql.mode/strict syntax
[{[:actor/actor-id 148] 
  [:actor/first-name
   ; filtering many-to-many relationship
   {(:actor/films {:where [:= :film/title "SEA VIRGIN"]}) 
    [:film/title]}]}]
```