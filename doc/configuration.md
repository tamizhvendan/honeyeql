# Configuration

During the initialization of the database adapter, HoneyEQL enables you to configure and override certain default behaviors of it. 

```clojure
(ns ..
  (:require [honeyeql.db :as db]))

(def db-spec ...)

; using default behaviors
(def db-adapter (db/initialize db-spec))

; overriding default behaviors
(def heql-config {...})
(def db-adapter (db/initialize db-spec heql-config))
```

The `heql-config` is a map with the following keys.

## EQL Mode

`:eql/mode` - configures the EQL syntax

### Possible Values

* `:eql.mode/lenient` - Use EQL Lenient Syntax (default)
* `:eql.mode/strict` - Use EQL Standard Syntax

## Attribute Return As

`:attr/return-as` - configures the naming convention of the attributes in the return values.

### Possible Values

* `:naming-convention/qualified-kebab-case` (default)
```clojure
; sample return value
{:actor/first-name "PENELOPE"
 :actor/last-name  "GUINESS"}
```

* `:naming-convention/unqualified-kebab-case`
```clojure
; sample return value
{first-name "PENELOPE"
 last-name  "GUINESS"}
```

* `:naming-convention/unqualified-camel-case`
```clojure
; sample return value
{firstName "PENELOPE"
 lastName  "GUINESS"}
```