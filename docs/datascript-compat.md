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
| `query` | partial | DataScript join subset ported; inputs, collection/tuple/relation bindings partly covered |
| `query_aggregates` | partial | Relation-input subset ported; exact edge behavior and advanced aggregates incomplete |
| `query_find_specs` | partial | Subset ported; scalar, collection, tuple, aggregate find specs, and multiple-result cuts covered |
| `query_fns` | partial | DataScript subset ported for predicates, `ground`, `get-else`, `get-some`, and `missing?`; arbitrary host/function clauses missing |
| `query_not` | partial | Single-source DataScript subset ported; nested `not`, default-source forms, and insufficient-binding errors incomplete |
| `query_or` | partial | Single-source DataScript subset ported, including scalar-input `or`; default-source forms and relation-source `or-join` incomplete |
| `query_pull` | partial | Basic literal-pattern DataScript subset ported; var patterns, multi-source pull, and exact tuple return shape incomplete |
| `query_return_map` | partial | `:keys`, `:strs`, `:syms` covered; tuple return-map subset ported |
| `query_rules` | missing | Biggest remaining query feature |
| `parser_*` | missing | Vev currently uses Kvist query literals, not an EDN/text parser |
| `pull_api` | partial | Attrs, wildcard, reverse refs, nesting, limits covered; recursion/components missing |
| `pull_parser` | partial | Kvist pull literals cover a subset; full attr-expr parser missing |
| `transact` | partial | Add/retract/map forms and explicit cardinality-one replacement covered; tx fns/CAS/call and unschematized default cardinality-one incomplete |
| `upsert` | partial | Unique lookup exists; full DataScript upsert semantics missing |
| `lookup_refs` | partial | Lookup refs covered in key paths |
| `ident` | partial | Idents covered in key paths |
| `components` | missing | Component recursion/retract behavior missing |
| `entity` | missing | Entity view API missing |
| `conn`, `listen`, `filter`, `serialize`, `storage`, `datafy` | later | API/package features after semantic core |
| `db`, `index`, `tuples`, `validation`, `issues` | partial | Need namespace-by-namespace porting |

## Next Porting Order

1. Port the small examples from `query.cljc`, `query_find_specs.cljc`, and
   `query_aggregates.cljc` that match Vev's current literal API.
2. Finish cheap transaction syntax aliases from `transact.cljc`.
3. Decide the minimum viable rules implementation before porting
   `query_rules.cljc`.
