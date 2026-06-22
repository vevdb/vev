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
| `query` | partial | DataScript join subset ported including existence and multi-hop joins; inputs, no-where input-only queries, empty collection/relation inputs, plain int entity inputs, collection/tuple/relation bindings, cardinality-many joins, named relation source joins, primary/named collection datom sources including long `[e a v tx op]` rows, multi-DB source joins, `_` input placeholders, and constant-substitution subset covered |
| `query_aggregates` | partial | Relation-input subset ported; grouped aggregates, `:with` duplicate preservation, keyword min/max comparator subset, literal top-n min/max, and parameterized top-n min/max covered; custom aggregates and exact edge behavior incomplete |
| `query_find_specs` | partial | Subset ported; scalar, collection, tuple, aggregate find specs, and multiple-result cuts covered |
| `query_fns` | partial | DataScript subset ported for predicates including `even?`, `odd?`, `zero?`, `pos?`, `neg?`, `true?`, `false?`, `nil?`, `some?`, `not`, `empty?`, string contains/prefix/suffix predicates, mixed-type predicate comparison, scalar/collection/tuple `ground`, `get-else`, `get-some`, lookup-ref inputs to query functions, `missing?`, literal vector/function args, tuple construction/destructuring, function-result unification with vector `first`/`second`, rule/function binding interaction, regex predicates and match-return clauses via `re-pattern`/`re-find`, and built-in result clauses for `identity`, `inc`, `dec`, `name`, `str`, `keyword`, `subs`, `vector`, `tuple`, `untuple`, `hash-map`, `get`, string `count`, integer arithmetic, `quot`, `rem`, `mod`, `min`, `max`, and `compare`; arbitrary host/function clauses and exact error behavior remain |
| `query_not` | partial | DataScript subset ported, including nested `not`, explicit relation/DB sources inside `not`, direct source-prefixed `($src not ...)`, explicit source override inside source-prefixed `not`, nested inherited/override source for `not`, and checked errors for groups with no positive binding; precise source-order insufficient-binding errors need an ordered query representation |
| `query_or` | partial | DataScript subset ported, including scalar-input `or`, direct source-prefixed `($src or ...)`, explicit source override inside source-prefixed `or`, nested default-source inheritance and nested source override for `or`, relation-source `or-join`, checked plain-`or` branch var matching, and `or-join` projection validation; exact Clojure diagnostics and nested binding-form validation remain |
| `query_pull` | partial | Basic literal-pattern, `:in` variable-pattern, lookup-ref input, multi-source pull, and pull-with-aggregates DataScript subsets ported; returning pattern values and exact tuple return shape incomplete |
| `query_return_map` | partial | DataScript subset ported for all-row `:keys`/`:strs`/`:syms` and tuple return maps |
| `query_rules` | partial | Literal rule input subset started: rule branches plus fixpoint recursive/mutual/symmetric rules with data clauses, repeated rule calls, predicates including `even?`, `ground`, built-in function clauses, false arguments, and source-qualified relation rule calls; host predicate inputs, validation, and semi-naive performance work incomplete |
| `parser_*` | partial | Text APIs are backed by the flat EDN reader. Query text parses `[:find ... :with ... :in ... :where ...]` with scalar, collection, tuple, pull, aggregate, and top-n aggregate find specs, keyed return-map markers, scalar/collection/tuple/relation/relation-source/named-DB-source inputs, data clauses including 2/3/4/5-wide relation-source rows, predicate clauses, built-in function clauses, `missing?`, `not`/`not-join`, `or`/`or-join`, ordinary/source-qualified rule calls and definitions, and no-`:where` input-only queries into the same `Query` representation. Pull text APIs parse EDN pull vectors directly, including named `:xform` options. Transaction text parses common tx-data vectors and maps into normal `Tx-Data`; exact validation remains |
| `pull_api` | partial | Attrs, wildcard, reverse refs, nesting, nested ref-map filtering, pull-many, string/false/ref defaults, reverse-ref defaults, default/numeric/nil limits, bounded recursion, capped `...` recursion, component expansion, component map override examples, and ABI-friendly named `:xform` transforms for `vector`/`name` are covered; arbitrary host xform functions, visitors, and exact rendered collection/scalar shapes remain |
| `pull_parser` | partial | Kvist pull literals, query text pull finds, and direct `pull-text` APIs cover attrs, wildcard, nested map patterns, recursive map values, flat/nested `:default`/`:as`/`:limit`/`:xform` option vectors, and option-wrapped map keys; validation remains |
| `transact` | partial | Add/retract/map forms, nil item skipping, value-specific retract no-ops including int-vs-float exactness, retract not-found/idempotency, mixed-type unique lookup/index compare, incoming-ref cleanup and adjacent tx-data reporting on retractEntity, ref-typed numeric value resolution, current tx tempids, forward/cyclic tempid ref values including generated-id map parents, map-valued datom compare/lookup subset, unused value-tempid rejection including empty cardinality-many map definitions, intermediate-DB lookup/CAS resolution subset, cardinality-one/unique/default replacement, CAS one/many/nil/lookup-ref value subset, tempids-outside-add rejection, native transaction functions returning tx-data, registry-backed ident transaction functions via DataScript-shaped tx-data vectors, and `transact-text` EDN string interop for common vector/map tx-data covered; arbitrary Clojure function values and exact errors incomplete |
| `upsert` | partial | Unique-identity map/list tempid upsert, multi-identity convergence, vector tx tempid ordering, current-tx conflict, unique-value no-upsert enforcement, intermediate-db retry subset, string tempid refs including forward refs, non-upsert of newly allocated ref tempids, unique-ref numeric/lookup-ref upsert, and conflicting unique-field subsets covered; exact conflict messages incomplete |
| `lookup_refs` | partial | Lookup refs covered in query entity/value positions, scalar and collection query inputs including mixed entity-id collections and multi-source joins, pull, tx entity/value/map-value/CAS entity/expected/value positions, nested tuple values, missing-ref retract no-ops, and `datoms`/`seek-datoms`/`index-range` index access; exact Clojure invalid lookup-ref errors not modeled |
| `ident` | partial | DataScript query/transact/pull subset, entity ident lookup, and missing-ident retract no-ops covered |
| `components` | partial | Component schema requires ref attrs, component `retractEntity`, `retractAttribute`, incoming-ref cleanup, forward pull expansion, explicit reverse pull subsets, and entity `touch` covered; exact reverse scalar shape missing |
| `entity` | partial | Basic entity view API covered: id/db access, lookup-ref and ident construction, scalar/many attr reads, forward and reverse ref navigation, contains, missing entities, and touch; Clojure map protocol/cache/print/equality semantics do not apply directly in Kvist |
| `index` | partial | `datoms`, `find-datom` including empty DB and prefix lookups, `seek-datoms`, `rseek-datoms`, upstream `index-range` bounds/order examples, and public AVET filtering covered; exact indexed-attribute error behavior incomplete |
| `validation` | partial | Nil value rejection plus value type, uniqueness, component schema, ident values, schema boolean attrs, schema keyword enum attrs, cardinality, unknown tx op, bad attrs, bad lookup attrs, and tempids-outside-add subsets covered; remaining reader/macro bad forms and exact validation errors incomplete |
| `db` | partial | Immutable DB values, current datom indexes, public datom access, and semantic DB diff covered; Clojure hash/cache/record/uuid helpers are host/runtime-specific |
| `conn` | partial | `conn-from-db`, `conn-from-datoms`, reset reports, and named report-sink listeners covered; arbitrary callback/closure API remains |
| `filter` | partial | Materialized `filter-db` covers DataScript-style predicate filtering, chained filters, filtered entity reads, and index-backed queries; exact hash/equality/runtime wrapper behavior remains |
| `serialize` | partial | `init-db` from datoms covered; text/EDN/JSON serialization format later |
| `issues` | partial | Need namespace-by-namespace triage |
| `listen`, `storage`, `datafy` | later | API/package features after semantic core |
| `tuples` | partial | Tuple attr schema, derived multi-tuple transaction maintenance, direct tuple attr rejection, unique tuple lookup refs including ref-component nested lookup refs, query lookup refs over tuple attrs, component-based tuple upsert, direct tuple-value tempid upsert, tuple unique conflict and multi-component update shapes, public AVET/index-range without explicit `:db/index`, tuple query functions, tuple type/attrs validation including invalid `:db/tupleAttrs` shapes, nested tuple dependency rejection, and cardinality-many tuple/component rejection subsets started; remaining exact errors and edge conflict matrix missing |

## Next Porting Order

1. Close high-surface query gaps: variable pull patterns, more rule source
   forms, and broader host function/predicate clauses.
2. Broaden EDN string interop for pull options, return maps, source-qualified rules, and exact
   parser validation.
3. Add a DataScript test-port ledger so each upstream namespace has pass/fail
   counts instead of a prose-only status.
