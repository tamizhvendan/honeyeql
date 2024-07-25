# Cast Operation

HoneyEQL supports type cast operation using the `:cast` parameter. This parameter takes the value similar to HoneySQL's a `cast` parameter except that instead of column name, we'll be using the attribute ident.

```clojure
; :eql.mode/lenient syntax
(heql/query-single pg-adapter [{[:actor/actor-id 1]
                                [[:cast :actor/actor-id :text]]}])
; :eql.mode/strict syntax
(heql/query-single pg-adapter `[{([:actor/actor-id 1])
                                  [[:cast :actor/actor-id :text]]}])
```

It returns

```clojure
{:actor/cast-of-actor-id "1"}
```

The `cast` operation often used along with an alias, which we would results a better output.

```clojure
; :eql.mode/lenient syntax
(heql/query-single pg-adapter [{[:actor/actor-id 1]
                                [[[:cast :actor/actor-id :text] :as :actor/id-as-string]]}])
; :eql.mode/strict syntax
(heql/query-single pg-adapter `[{([:actor/actor-id 1])
                                  [[[:cast :actor/actor-id :text] :as :actor/id-as-string]]}])
```

It now returns

```clojure
{:actor/id-as-string "1"}
```
