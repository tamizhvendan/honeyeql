# Aggregate Queries

HoneyEQL supports the standard aggregate functions `count`, `max`, `min`, `sum` & `avg`. HoneyEQL models these functions as two elements vector where the first element representing the function and the second element represents the attribute.

```clojure
; SELECT count(id) FROM course;
[:count :course/id] 
; SELECT avg(rating) FROM course;
[:avg :course/rating] 
```

> Support for `count(*)` will be added in a later release. 

We'll use this sample database schema to walk us through the syntax
![](https://www.graphqlize.org/img/author_course_er_diagram.png)

```clojure
; :eql.mode/lenient syntax
(heql/query-single
  db-adapter
  {[] [[:count :course/rating]
       [:avg :course/rating]
       [:max :course/rating]
       [:min :course/rating]
       [:sum :course/rating]]})
; :eql.mode/strict syntax
(heql/query-single
  db-adapter
  `[{([] [[:count :course/rating]
          [:avg :course/rating]
          [:max :course/rating]
          [:min :course/rating]
          [:sum :course/rating]])}])
```

The output of the queries would look like below for the respective `:attr/return-as` configuration 

```clojure
; :naming-convention/qualified-kebab-case
{:course/count-of-rating 4
 :course/avg-of-rating   4.75M
 :course/max-of-rating   5
 :course/min-of-rating   4
 :course/sum-of-rating   19}

; :naming-convention/unqualified-kebab-case
{:count-of-rating 4
 :avg-of-rating   4.75M
 :max-of-rating   5
 :min-of-rating   4
 :sum-of-rating   19}

; :naming-convention/unqualified-camel-case
{:countOfRating 4
 :avgOfRating   4.75M
 :maxOfRating   5
 :minOfRating   4
 :sumOfRating   19}
```

If you want to retain the query shape, returning the keys as vector instead of `function-of-column` format, you can make use of the `:attr/aggregate-attr-convention` configuration attribute by setting it to `:aggregate-attr-naming-convention/vector`. With this configuration, the result would look like

```clojure
; :naming-convention/qualified-kebab-case
{[:count :course/rating] 4
 [:avg :course/rating]   4.75M
 [:max :course/rating]   5
 [:min :course/rating]   4
 [:sum :course/rating]   19}
```
> Other naming conventions are not supported currently.

## Aggregates Over Relationships

We can use the aggregate functions over the `one-to-many` and `many-to-many` relationship fields as well.

```clojure
; :eql.mode/lenient syntax
[{[:author/id 1] [:author/id
                  :author/first-name
                  {:author/courses [[:count :course/rating]
                                    [:avg :course/rating]
                                    [:max :course/rating]
                                    [:min :course/rating]
                                    [:sum :course/rating]]}]
; returns
#:author{:id         1
         :first-name "John"
         :courses    [#:course{:count-of-rating 2
                               :avg-of-rating   4.5000000000000000M
                               :max-of-rating   5
                               :min-of-rating   4
                               :sum-of-rating   9}]
```

## Group By Queries

The aggregate functions often paired along with the GROUP BY operation and HoneySQL supports it as well!
For the above schema, we can group the courses by their rating and get their count using the following queries

```clojure
; group all courses ratings

; :eql.mode/lenient syntax
{[[] {:group-by [:course/rating]}] [:course/rating
                                    [:count :course/rating]]}
; :eql.mode/strict syntax
`[{([] {:group-by [:course/rating]}) [:course/rating
                                      [:count :course/rating]]}]


; returns - default convention
(#:course{:rating 5 :count-of-rating 3} 
 #:course{:rating 4 :count-of-rating 1})
; returns (for :aggregate-attr-naming-convention/vector)
({:course/rating 5 [:count :course/rating] 3} 
 {:course/rating 4 [:count :course/rating] 1})


; group all courses ratings of a given author `1`

; :eql.mode/lenient syntax
{[:author/id 1] [:author/first-name
                 :author/last-name
                 {[:author/courses {:group-by [:course/rating]}]
                   [:course/rating 
                    [:count :course/rating]]}]}

; :eql.mode/strict syntax
`[{[:author/id 1] [:author/first-name
                   :author/last-name
                   {(:author/courses {:group-by [:course/rating]})
                     [:course/rating 
                      [:count :course/rating]]}]}]

; returns
#:author{:first-name "John",
         :last-name "Doe",
         :courses [#:course{:rating 4 :count-of-rating 1} 
                   #:course{:rating 5 :count-of-rating 1}]}
; returns (for :aggregate-attr-naming-convention/vector)
(#:author{:first-name "John",
          :last-name "Doe",
          :courses [{:course/rating 4 [:count :course/rating] 1} 
                    {:course/rating 5 [:count :course/rating] 1}]})
```