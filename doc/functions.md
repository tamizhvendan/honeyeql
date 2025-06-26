# Functions

Function calls (and expressions with operators) can be specified as vectors where the first element is the keyword representing the function name.

```clojure
; :eql.mode/lenient syntax
(heql/query-single pg-adapter [{[:employee/id 1]
                                  [[[:upper :employee/first-name] :as :employee/first-name]]}])
; :eql.mode/strict syntax
(heql/query-single pg-adapter `[{([:employee/id 1])
                                   [[[:upper :employee/first-name] :as :employee/first-name]]}])
```

It returns

```clojure
#:employee{:first-name "ANDREW"}
```

## Multi Arity Function

> NOTE: Supported only for Postgres


```clojure
; :eql.mode/lenient syntax
(heql/query-single pg-adapter
                     {[:employee/id 1]
                      [[[:concat :employee/first-name " " :employee/last-name] :as :employee/full-name]]})
; :eql.mode/strict syntax
(heql/query-single pg-adapter
                     `[{([:employee/id 1])
                        [[[:concat :employee/first-name " " :employee/last-name] :as :employee/full-name]]}])
```

It returns

```clojure
#:employee{:full-name "Andrew Adams"}
```

```clojure
; :eql.mode/lenient syntax
(heql/query-single pg-adapter
                    {[:employee/id 7]
                    [[[:pgp_sym_decrypt [:cast :employee/ssn :bytea] "encryption-key"] :as :employee/ssn]]})

; :eql.mode/strict syntax
(heql/query-single pg-adapter
                    `[{([:employee/id 7])
                      [[[:pgp_sym_decrypt [:cast :employee/ssn :bytea] "encryption-key"] :as :employee/ssn]]}])
```

It returns

```clojure
#:employee{:ssn "xxxxxxxxxxxxx"}
```