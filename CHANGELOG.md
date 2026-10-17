* 2.0.7 - 2026-10-17
  - Normalize JDBC-native `TIMESTAMPTZ` mutation results to `OffsetDateTime` and improve mutation coercion error diagnostics.
* 2.0.6 - 2026-10-17
  - Normalize JDBC-native temporal values returned by mutations so `date`, `time`, and `timestamp` attributes are converted to the same `java.time` representations used by query results.
* 2.0.5 - 2026-10-17
  - Apply `:from-db` attribute coercion to entities returned by `insert!` and `insert-multi!`, so mutation results use the same application-level representation as query results.
* 2.0.4 - 2026-10-16
  - Added per-attribute `:to-db` and `:from-db` coercion through `:attr/coercions`.
  - Query ident and predicate values now consistently use `:to-db` coercion.
* 2.0.3 - 2026-10-05
  - Preserve the underlying attribute type when returning aliased scalar attributes.
  - Aliases now affect only the returned key; result coercion continues to use the source attribute metadata.
  - Attribute-path projections also preserve the terminal attribute's type when aliased.
* 2.0.1 - 2026-10-03
  - Reuse shared relationship-path prefixes when projecting multiple attributes through the same one-to-one relationships.
  - Avoid generating duplicate joins for sibling attribute-path expressions that traverse the same relationships.
* 2.0.0 - 2026-10-02
  - **Breaking**: Foreign keys backed by UNIQUE constraints are now correctly inferred as one-to-one relationships.
* 1.0.8 - 2026-10-02
  - Added support for projecting attributes through one-to-one relationship paths
* 1.0.7 - 2026-09-18
  - Upgrade dependencies to their latest versions 
* 1.0.6 - 2025-06-26
  - Added support for multi-arity function in postgres
* 1.0.5 - 2025-06-17
  - Fixes #22
  - Upgrade dependencies to their latest versions  
* 1.0.4 - 2024-10-31
  - Fixes #15
* 1.0.3 - 2024-07-26
  - Updated docs
* 1.0.2 - 2024-01-02
  - Added support for composite keys #8
  - Removed `opt-attrs` from metadata
  - Removed `rel-attrs` from `req-attrs`
  - Fix to populate `rel-attrs` correctly
  - Removed unused functions
  - Bumped dependencies to their latest versions 
* 1.0.1 -- 2023-11-21
  - Adding type hints for metadata generation. Credits [Dekel Pilli](https://github.com/tamizhvendan/honeyeql/pull/10)