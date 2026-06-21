# DataScript Compatibility

Vev has not ported the DataScript test suite yet. This file tracks the local
`../datascript/test/datascript/test/*.cljc` namespaces as the compatibility
checklist.

See `docs/datascript-test-port-ledger.md` for namespace-by-namespace port
status.

Status key:

- `covered`: Vev has comparable tests and behavior
- `partial`: Vev supports a useful subset
- `missing`: not implemented yet
- `later`: likely outside the near-term in-memory compatibility target

## Current Map

| DataScript area | Status | Notes |
| --- | --- | --- |
| `query` | partial | DataScript join subset ported; inputs, no-where input-only queries, empty collection/relation inputs, plain int entity inputs, collection/tuple/relation bindings, cardinality-many joins, named relation source joins, `_` input placeholders, and constant-substitution subset covered |
| `query_aggregates` | partial | Relation-input subset ported; grouped aggregates, `:with` duplicate preservation, keyword min/max comparator subset, literal top-n min/max, and parameterized top-n min/max covered; custom aggregates and exact edge behavior incomplete |
| `query_find_specs` | partial | Subset ported; scalar, collection, tuple, aggregate find specs, and multiple-result cuts covered |
| `query_fns` | partial | DataScript subset ported for predicates including `even?`, mixed-type predicate comparison, scalar/collection/tuple `ground`, `get-else`, `get-some`, lookup-ref inputs to query functions, `missing?`, literal vector/function args, tuple construction/destructuring, function-result unification with vector `first`/`second`, and built-in result clauses for `identity`, `inc`, `vector`, `tuple`, `untuple`, `hash-map`, `get`, string `count`, and integer arithmetic; arbitrary host/function clauses missing |
| `query_not` | partial | Single-source DataScript subset ported, including nested `not`, explicit named relation sources inside `not`, direct source-prefixed `($src not ...)`, explicit source override inside source-prefixed `not`, and nested default-source inheritance for `not`; nested source override and insufficient-binding errors incomplete |
| `query_or` | partial | Single-source DataScript subset ported, including scalar-input `or`, direct source-prefixed `($src or ...)`, explicit source override inside source-prefixed `or`, nested default-source inheritance for `or`, and relation-source `or-join`; nested source override and exact validation errors incomplete |
| `query_pull` | partial | Basic literal-pattern and `:in` variable-pattern DataScript subsets ported; multi-source pull, returning pattern values, and exact tuple return shape incomplete |
| `query_return_map` | partial | DataScript subset ported for all-row `:keys`/`:strs`/`:syms` and tuple return maps |
| `query_rules` | partial | Literal rule input subset started: rule branches plus bounded recursive/mutual rules with data clauses, predicates including `even?`, `ground`, built-in function clauses, false arguments, and source-qualified relation rule calls; host predicate inputs and validation incomplete |
| `parser_*` | missing | Required for EDN string APIs and broad interop; not required for the in-memory semantic engine because Kvist literals lower to the same internal representation |
| `pull_api` | partial | Attrs, wildcard, reverse refs, nesting, nested ref-map filtering, pull-many, string/false/ref defaults, reverse-ref defaults, default/numeric/nil limits, bounded recursion, capped `...` recursion, component expansion, and component map override examples covered; exact rendered collection/scalar shapes missing |
| `pull_parser` | partial | Kvist pull literals cover a subset; full attr-expr parser missing |
| `transact` | partial | Add/retract/map forms, nil item skipping, value-specific retract no-ops, incoming-ref cleanup on retractEntity, ref-typed numeric value resolution, current tx tempids, unused value-tempid rejection, intermediate-DB lookup/CAS resolution subset, cardinality-one/unique/default replacement, and CAS one/many/nil/lookup-ref value subset covered; tx fn call and exact errors incomplete |
| `upsert` | partial | Unique-identity map/list tempid upsert, unique-value no-upsert enforcement, intermediate-db retry subset, string tempid refs, unique-ref numeric/lookup-ref upsert, and conflicting unique-field subsets covered; exact conflict messages incomplete |
| `lookup_refs` | partial | Lookup refs covered in query entity/value positions, scalar and collection query inputs, pull, tx entity/value/map-value/CAS entity/expected/value positions, nested tuple values, missing-ref retract no-ops, and `datoms`/`seek-datoms`/`index-range` index access; exact Clojure invalid lookup-ref errors not modeled |
| `ident` | partial | DataScript query/transact/pull subset, entity ident lookup, and missing-ident retract no-ops covered |
| `components` | partial | Component schema requires ref attrs, component `retractEntity`, `retractAttribute`, incoming-ref cleanup, forward pull expansion, explicit reverse pull subsets, and entity `touch` covered; exact reverse scalar shape missing |
| `entity` | partial | Basic entity view API covered: id/db access, lookup-ref and ident construction, scalar/many attr reads, forward and reverse ref navigation, contains, missing entities, and touch; Clojure map protocol/cache/print/equality semantics do not apply directly in Kvist |
| `index` | partial | `datoms`, `find-datom` including empty DB, `seek-datoms`, `rseek-datoms`, upstream `index-range` bounds/order examples, and public AVET filtering covered; exact indexed-attribute error behavior incomplete |
| `validation` | partial | Nil value rejection plus value type, uniqueness, component schema, ident values, schema boolean attrs, schema keyword enum attrs, and cardinality subsets covered; bad transaction forms and exact validation errors incomplete |
| `conn`, `listen`, `filter`, `serialize`, `storage`, `datafy` | later | API/package features after semantic core |
| `db`, `issues` | partial | Need namespace-by-namespace porting |
| `tuples` | partial | Tuple attr schema, derived multi-tuple transaction maintenance, direct tuple attr rejection, unique tuple lookup refs including ref-component nested lookup refs, component-based tuple upsert, tuple unique conflict update shapes, public AVET/index-range without explicit `:db/index`, tuple query functions, tuple type/attrs validation, nested tuple dependency rejection, and cardinality-many tuple/component rejection subsets started; remaining upsert conflict matrix missing |

## Next Porting Order

1. Close high-surface query gaps: variable pull patterns, more rule source
   forms, and broader host function/predicate clauses.
2. Port the remaining DataScript transaction cases that do not require a text
   parser or Clojure runtime behavior.
3. Add a DataScript test-port ledger so each upstream namespace has pass/fail
   counts instead of a prose-only status.
