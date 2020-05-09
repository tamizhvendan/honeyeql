## Syntax Improvement

```clojure
; earlier
[{[:author/id 1] 
 [:author/first-name]}]

; new
{[:author/id 1] 
 [:author/first-name]}
```

## have

```clojure
[{([] {:where [:not [:have :author/courses]]}) 
  [:author/first-name
   :author/last-name]}]
```


## Wild card select 

```clojure
[{([] {:where [:have :author/courses]}) 
  [:author/* 
   {:author/courses [:course/*]}]}]
```

## Bug Fix

Null Check

```clojure

```