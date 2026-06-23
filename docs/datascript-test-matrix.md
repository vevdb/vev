# DataScript Test Matrix

This is the working matrix for reaching DataScript compatibility without
turning every Clojure runtime detail into an engine blocker.

Vev's primary long-term consumers are non-Kvist native callers. A feature is
therefore not considered compatibility-complete until it is available through
the EDN/text or prepared-query/transaction surface. Kvist literal macros are
valuable convenience coverage and macro-system exercise, but they are secondary
frontends over the same typed query/tx/pull representations.

Status:

- `passing`: comparable Vev tests exist and pass
- `partial`: a useful subset is ported, more semantic cases remain
- `planned`: not ported yet, should become Vev tests
- `interop`: requires EDN/text APIs, parser frontend, or C ABI-facing shape
- `host`: Clojure/JVM/runtime behavior, not core engine semantics

## Semantic Core

These are the main in-memory parity target before durable storage.

| Namespace | Upstream tests | Status | Next batch |
| --- | ---: | --- | --- |
| `query.cljc` | 11 | partial | DB-backed tx/op data pattern terms, primary/named collection datom sources, map-form text queries, direct map-as-relation inputs, nested `:in` binding patterns, nested map values, DataScript namespaced reverse attrs, checked scalar/collection/tuple/relation input shape errors, and issue-425 symbol constants covered; host functions and exact input error wording remain |
| `query_find_specs.cljc` | 1 | passing | keep covered |
| `query_fns.cljc` | 7 | partial | broader native built-ins including `keyword`, `str`, lookup-ref `get-else`/`get-some` entity inputs in literal and text APIs, regex predicate surface, regex match-return clauses, nested result binding including EDN text vector/map literal function args and text/prepared tuple/relation/collection output destructuring, named predicate/function operator inputs, and registered native-op aliases for EDN/prepared text queries including rule bodies covered; checked query errors now cover unknown native predicate/function ops and insufficient function/predicate bindings; arbitrary host callback function surface remains |
| `query_not.cljc` | 5 | partial | source semantics plus checked unbound-group errors covered; query text and Kvist literal macros have ordered top-level `not` binding validation; supported rule bodies execute in source order |
| `query_or.cljc` | 5 | partial | source semantics, plain-`or` branch var matching, nested `or-join` binding forms, and `or-join` projection validation covered; exact diagnostics remain |
| `query_pull.cljc` | 8 | partial | multi-source pull, text/Kvist `:in` pattern variables, plus scalar, collection, and tuple pull find-spec shapes covered; exact Clojure return rendering remains |
| `query_return_map.cljc` | 1 | passing | keep covered with Vev keyed-row shape |
| `query_rules.cljc` | 3 | partial | source args, recursion, repeated calls, ordered body execution for literal/text rules, inline map-query `:rules`, and unknown/arity/param validation covered; host predicate inputs and semi-naive performance remain |
| `query_aggregates.cljc` | 1 | partial | built-in aggregates including numeric sum/avg, top-n, median, variance, stddev, and DataScript-shaped named custom aggregates covered; arbitrary host callback aggregates later |
| `transact.cljc` | 19 | partial | DataScript-style report `:db/current-tx` tempids, EDN text/prepared negative numeric tempids and single operation vectors, schema-aware map vector values, empty cardinality-many tempid validation, native tx functions, registry-backed ident tx functions including EDN text/prepared `:db.fn/call` and shorthand ident calls, tempids-outside-add validation, and large entity-id rejection covered; exact Clojure function-value storage and exact errors remain |
| `upsert.cljc` | 6 | partial | vector tx tempid ordering, DataScript retry-order tempid merging, unique-value no-upsert, current-tx conflict, explicit-id identity conflicts, main conflict matrix, and EDN text/prepared transaction coverage for ordinary identity upsert, lookup-ref upsert, retry convergence, forward ref tempids, and rollback on conflict covered; exact messages remain |
| `validation.cljc` | 2 | partial | runtime bad tx-data validation including `:db.type/symbol` plus `transact-text` bad-shape rollback covered; reader/macro bad forms and exact errors remain |
| `index.cljc` | 5 | partial | main index surface and indexed-attribute errors covered; exact lazy sequence/API rendering details remain |
| `tuples.cljc` | 11 | partial | direct/component tuple upsert, redundant direct tuple-value ignore cases, lookup-ref tuple values, and EDN text/prepared tuple upsert plus lookup-ref transaction coverage covered; remaining exact errors and edge conflict matrix |
| `lookup_refs.cljc` | 5 | partial | mixed entity-id inputs covered; exact invalid lookup-ref behavior remains |
| `ident.cljc` | 4 | passing | keep covered |
| `explode.cljc` | 4 | partial | cardinality-many tx-map collection expansion, ref collections, reverse ref tx-map values, nested maps, multi-valued nested maps including EDN sets, reverse nested maps, and circular/nested ref shapes covered through EDN/text and Kvist tx-data; host list/array inputs and exact reverse-attr schema errors remain |
| `components.cljc` | 2 | partial | reverse component pull id/pattern/recursion behavior covered; exact schema validation and render/touch shapes remain |
| `pull_api.cljc` | 17 | partial | repeated attr-spec normalization, DataScript namespaced reverse attrs, reverse component pull id/pattern/recursion shapes, and ABI-friendly named `:xform` transforms for `vector`/`name` covered; arbitrary host xform functions, visitor options, and exact collection/scalar shapes remain |
| `entity.cljc` | 6 | partial | engine-relevant entity reads are covered; Clojure protocol behavior is host |
| `filter.cljc` | 1 | partial | materialized filtered DBs cover query/entity/index-visible semantics; exact hash/equality/runtime wrapper behavior remains |

## Parser And Portable API

These are the primary compatibility gate for broad consumption. The first query
and transaction text APIs now exist, and query text inputs are consumed in
DataScript `:in` source order, but full parser parity still trails some native
Kvist literal conveniences. New DataScript compatibility work should prefer
EDN/text coverage first and add Kvist macro coverage as a convenience layer.

| Namespace | Upstream tests | Status | Notes |
| --- | ---: | --- | --- |
| `parser.cljc` | 3 | partial | flat EDN node reader supports nested vectors/lists/maps/sets and now feeds query/pull/tx text subsets; full validation remains |
| `parser_find.cljc` | 4 | partial | text query parser covers scalar, collection, tuple, pull, aggregate, and top-n aggregate find specs; exact validation remains |
| `parser_query.cljc` | 1 | partial | EDN-reader-backed text query subset covers vector and map query forms, map-form inline `:rules`, `:find`, `:with`, `:in`, named DB sources, optional ordered `:where`, direct map-as-relation inputs, float constants, nested input/result destructuring, execution through normal query inputs/sources, reusable prepared query values, and checked parse/input/source errors; parser-style validation now covers unknown `:find`/`:with` vars, `:find`/`:with` overlap, duplicate variable inputs, duplicate `:with` vars, unknown source vars, and missing `%` for rule calls; exact diagnostics remain |
| `parser_return_map.cljc` | 1 | partial | text query parser accepts `:keys`/`:strs`/`:syms` and exposes keyed text helpers; exact validation remains |
| `parser_rules.cljc` | 3 | partial | ordinary and source-qualified rule calls, rule definitions, inline map-query `:rules`, ordered text rule bodies, rule-body `get-else`/`get-some`, and empty/duplicate/arity rule validation covered; remaining exact parser shapes remain |
| `parser_where.cljc` | 6 | partial | data pattern including lookup-ref entity terms, named DB source patterns, relation-source rows, predicate, built-in function, missing?, not/not-join, or/or-join, `and` branches, ordinary rule clauses, nested text `not`, and ordered top-level execution/validation covered; parser validation rejects empty rule calls, empty `not`/`or`, empty `not-join`/`or-join` bodies, and empty join-var sets with DataScript-shaped errors; exact diagnostics remain |
| `pull_parser.cljc` | 1 | partial | query text pull finds, pull pattern variables, direct pull text APIs, and reusable prepared pull pattern values cover attrs, wildcard, DataScript namespaced reverse attrs, nested maps with vector or list pattern values, recursive map values, flat/nested `:default`/`:as`/`:limit`/`:xform` option forms including string/vector option heads, repeated attr specs, option-wrapped map keys, checked rejection of malformed option/map specs, and schema-aware validation for reverse refs, nested refs/recursion, and explicit limits through query pull literals and pattern inputs; exact diagnostics remain |
| transaction EDN text | n/a | partial | `transact-text` and reusable prepared tx-data cover `:db/add`, `:db/retract`, `:db/retractEntity`, `:db.fn/retractAttribute`, `:db.fn/cas`, single operation vectors, map tx-data, set-valued collections, lookup refs, idents, float values, string/current-tx/negative numeric tempids, generated map ids, nested maps, reverse ref tx-map attrs including nested maps, ordinary and tuple upsert/lookup-ref transactions, and bad-shape/no-mutation validation through the normal transaction engine |

## Host Or Later Runtime APIs

These are useful, but not the next engine-parity gate.

| Namespace | Upstream tests | Status | Notes |
| --- | ---: | --- | --- |
| `conn.cljc` | 2 | partial | conn-from-db/from-datoms and reset reports covered |
| `listen.cljc` | 1 | partial | named report-sink listeners covered; arbitrary callback/closure API remains |
| `serialize.cljc` | 5 | partial | `init-db` from datoms and typed EDN-ish datom snapshot text roundtrip covered, including schema, retractions, refs, symbol/map/vector values, and next-tx recovery; JSON/transit-style formats later |
| `storage.clj` | 5 | planned | durable/storage work later |
| `datafy.cljc` | 1 | host | Clojure-specific shape |
| `db.cljc` | 4 | partial | semantic DB diff covered; hash/cache/uuid/record behavior is host |
| `issues.cljc` | 5 | partial | engine-relevant regressions for vector result isolation, mixed-type DB diff, and schema inspection covered; metadata/pprint cases are Clojure host behavior |
| `query_v3.cljc` | 2 | partial | input arity validation and non-collection source validation covered; remaining query-v3 engine surface later |
| `lru.cljc` | 2 | host | DataScript implementation detail |
| `core.cljc` | 1 | host | test harness/runtime behavior |
| `cljs.cljc` | 0 | host | ClojureScript environment behavior |

## Batch Order

1. Broaden parser/EDN APIs around pull options, return-map markers,
   source-qualified rules, transactions, and exact validation. This is the
   primary compatibility route because non-Kvist consumers depend on it.
2. Port remaining `transact.cljc`, `upsert.cljc`, and `validation.cljc`
   semantics that are not host-runtime-specific, with text/prepared API tests
   where a portable representation exists.
3. Finish tuple/index public API behavior as one schema/index batch.
4. Keep Kvist macros aligned with the typed AST as ergonomic sugar, not as the
   definition of compatibility.
