## Syntax Improvement

```clojure
; earlier
[{[:author/id 1] 
 [:author/first-name]}]

; new
{[:author/id 1] 
 [:author/first-name]}
```

```clojure
; earlier
{([] {:where [:= [:author/courses :course/rating] 5]})
 [:author/first-name
  {(:author/courses {:where [:= :course/rating 5]})
   [:course/title :course/rating]}]}

; new
{[[] {:where [:= [:author/courses :course/rating] 5]}]
 [:author/first-name
  {[:author/courses {:where [:= :course/rating 5]}]
   [:course/title :course/rating]}]}
```

## Wild card select 

```clojure
[{([] {:where [:have :author/courses]}) 
  [:author/* 
   {:author/courses [:course/*]}]}]
```

## have

```clojure
[{([] {:where [:not [:have :author/courses]]}) 
  [:author/first-name
   :author/last-name]}]
```


## 1-to-1 relationship without the id suffix

```clojure
; TODO
```

## Bug Fix

Null Check during coerce

```clojure

```