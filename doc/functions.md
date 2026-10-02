# Functions

Database functions are one type of HoneyEQL expression.

A function expression is specified as a vector where the first element is the keyword representing the function name.

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

Like other HoneyEQL expressions, a function expression can be projected into an attribute using `:as`.

See [Query Syntax](./query-syntax.md#expressions) for other expression types, including attribute paths through one-to-one relationships.

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