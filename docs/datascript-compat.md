# DataScript Compatibility

Vev has not ported the DataScript test suite yet. This file tracks the local
`../datascript/test/datascript/test/*.cljc` namespaces as the compatibility
checklist.

Status key:

- `covered`: Vev has comparable tests and behavior
- `partial`: Vev supports a useful subset
- `missing`: not implemented yet
- `later`: likely outside the near-term in-memory compatibility target

## Current Map

| DataScript area | Status | Notes |
| --- | --- | --- |
| `query` | partial | DataScript join subset ported; inputs, plain int entity inputs, collection/tuple/relation bindings, `_` input placeholders, and constant-substitution subset covered |
| `query_aggregates` | partial | Relation-input subset ported; grouped aggregates, `:with` duplicate preservation, and keyword min/max comparator subset covered; exact edge behavior and advanced aggregates incomplete |
| `query_find_specs` | partial | Subset ported; scalar, collection, tuple, aggregate find specs, and multiple-result cuts covered |
| `query_fns` | partial | DataScript subset ported for predicates including `even?`, mixed-type predicate comparison, scalar/collection/tuple `ground`, `get-else`, `get-some`, `missing?`, and built-in result clauses for `identity`, string `count`, and integer arithmetic; arbitrary host/function clauses missing |
| `query_not` | partial | Single-source DataScript subset ported, including nested `not`; default-source forms and insufficient-binding errors incomplete |
| `query_or` | partial | Single-source DataScript subset ported, including scalar-input `or`; default-source forms and relation-source `or-join` incomplete |
| `query_pull` | partial | Basic literal-pattern and `:in` variable-pattern DataScript subsets ported; multi-source pull, returning pattern values, and exact tuple return shape incomplete |
| `query_return_map` | partial | DataScript subset ported for all-row `:keys`/`:strs`/`:syms` and tuple return maps |
| `query_rules` | partial | Literal rule input subset started: rule branches plus bounded recursive/mutual rules with data clauses, predicates including `even?`, `ground`, and built-in function clauses; rule sources and validation incomplete |
| `parser_*` | missing | Vev currently uses Kvist query literals, not an EDN/text parser |
| `pull_api` | partial | Attrs, wildcard, reverse refs, nesting, pull-many, string/false/ref defaults, reverse-ref defaults, explicit/default limits, and component expansion examples covered; recursion and exact rendered collection/scalar shapes missing |
| `pull_parser` | partial | Kvist pull literals cover a subset; full attr-expr parser missing |
| `transact` | partial | Add/retract/map forms, nil item skipping, value-specific retract no-ops, incoming-ref cleanup on retractEntity, ref-typed numeric value resolution, intermediate-DB lookup/CAS resolution subset, cardinality-one/unique replacement, and CAS one/many/nil/lookup-ref value subset covered; tx fn call and unschematized default cardinality-one incomplete |
| `upsert` | partial | Unique-identity map/list tempid upsert, intermediate-db retry subset, string tempid refs, unique-ref numeric/lookup-ref upsert, and conflicting unique-field subsets covered; exact conflict messages incomplete |
| `lookup_refs` | partial | Lookup refs covered in query entity/value positions, scalar non-keyword query inputs, pull, tx entity/value/map-value/CAS entity/expected/value positions, and missing-ref retract no-ops for entity and value refs; collection/keyword-valued input lookup refs incomplete |
| `ident` | partial | DataScript query/transact/pull subset, entity ident lookup, and missing-ident retract no-ops covered |
| `components` | partial | Component `retractEntity`, `retractAttribute`, incoming-ref cleanup, forward pull expansion, explicit reverse pull subsets, and entity `touch` covered; exact reverse scalar shape missing |
| `entity` | partial | Basic entity view API covered: id/db access, lookup-ref and ident construction, scalar/many attr reads, forward and reverse ref navigation, contains, missing entities, and touch; Clojure map protocol/cache/print/equality semantics do not apply directly in Kvist |
| `conn`, `listen`, `filter`, `serialize`, `storage`, `datafy` | later | API/package features after semantic core |
| `db`, `index`, `tuples`, `validation`, `issues` | partial | Need namespace-by-namespace porting |

## Next Porting Order

1. Close high-surface query gaps: variable pull patterns, more rule source
   forms, and broader host function/predicate clauses.
2. Port the remaining DataScript transaction cases that do not require a text
   parser or Clojure runtime behavior.
3. Add a DataScript test-port ledger so each upstream namespace has pass/fail
   counts instead of a prose-only status.
