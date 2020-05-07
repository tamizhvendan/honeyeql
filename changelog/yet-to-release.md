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