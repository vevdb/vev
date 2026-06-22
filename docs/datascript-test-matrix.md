# DataScript Test Matrix

This is the working matrix for reaching DataScript compatibility without
turning every Clojure runtime detail into an engine blocker.

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
| `query.cljc` | 11 | partial | primary/named collection datom sources, map relation inputs, nested `:in` binding patterns, and nested map values covered; host functions and exact input errors remain |
| `query_find_specs.cljc` | 1 | passing | keep covered |
| `query_fns.cljc` | 7 | partial | broader native built-ins including `keyword`, `str`, regex predicate surface, regex match-return clauses, and nested result binding covered; decide host function surface and exact error behavior |
| `query_not.cljc` | 5 | partial | source semantics covered; insufficient-binding/error cases remain |
| `query_or.cljc` | 5 | partial | source semantics covered; validation/error cases remain |
| `query_pull.cljc` | 8 | partial | multi-source pull plus scalar, collection, and tuple pull find-spec shapes covered; exact Clojure return rendering remains |
| `query_return_map.cljc` | 1 | passing | keep covered with Vev keyed-row shape |
| `query_rules.cljc` | 3 | partial | source args, recursion, and repeated calls covered; validation and semi-naive performance remain |
| `query_aggregates.cljc` | 1 | partial | built-in aggregates including top-n, median, variance, and stddev covered; custom aggregates later |
| `transact.cljc` | 19 | partial | native tx functions, registry-backed ident tx functions, tempids-outside-add validation, and large entity-id rejection covered; exact Clojure function-value storage and exact errors remain |
| `upsert.cljc` | 6 | partial | vector tx tempid ordering, unique-value no-upsert, current-tx conflict, explicit-id identity conflicts, and main conflict matrix covered; exact messages remain |
| `validation.cljc` | 2 | partial | runtime bad tx-data validation covered; reader/macro bad forms and exact errors remain |
| `index.cljc` | 5 | partial | main index surface covered; exact indexed-attribute errors remain |
| `tuples.cljc` | 11 | partial | direct/component tuple upsert and lookup-ref tuple values covered; remaining exact errors and edge conflict matrix |
| `lookup_refs.cljc` | 5 | partial | mixed entity-id inputs covered; exact invalid lookup-ref behavior remains |
| `ident.cljc` | 4 | passing | keep covered |
| `components.cljc` | 2 | partial | exact schema validation and render/touch shapes |
| `pull_api.cljc` | 17 | partial | ABI-friendly named `:xform` transforms for `vector`/`name` covered; arbitrary host xform functions, visitor options, and exact collection/scalar shapes remain |
| `entity.cljc` | 6 | partial | engine-relevant entity reads are covered; Clojure protocol behavior is host |
| `filter.cljc` | 1 | partial | materialized filtered DBs cover query/entity/index-visible semantics; exact hash/equality/runtime wrapper behavior remains |

## Parser And Interop

These matter for broad consumption. The first query and transaction text APIs
now exist, and query text inputs are consumed in DataScript `:in` source order,
but full parser parity still trails the native Kvist literal surface.

| Namespace | Upstream tests | Status | Notes |
| --- | ---: | --- | --- |
| `parser.cljc` | 3 | partial | flat EDN node reader supports nested vectors/lists/maps and now feeds query/pull/tx text subsets; full validation remains |
| `parser_find.cljc` | 4 | partial | text query parser covers scalar, collection, tuple, pull, aggregate, and top-n aggregate find specs; exact validation remains |
| `parser_query.cljc` | 1 | partial | EDN-reader-backed text query subset covers `:find`, `:with`, `:in`, named DB sources, optional `:where`, map relation inputs via helper, nested input/result destructuring, and execution through normal query inputs/sources; validation remains |
| `parser_return_map.cljc` | 1 | partial | text query parser accepts `:keys`/`:strs`/`:syms` and exposes keyed text helpers; exact validation remains |
| `parser_rules.cljc` | 3 | partial | ordinary and source-qualified rule calls and rule definitions covered; validation remains |
| `parser_where.cljc` | 6 | partial | data pattern, named DB source patterns, relation-source rows, predicate, built-in function, missing?, not/not-join, or/or-join, `and` branches, and ordinary rule clauses covered; exact validation remains |
| `pull_parser.cljc` | 1 | partial | query text pull finds and direct pull text APIs cover attrs, wildcard, nested maps, recursive map values, flat/nested `:default`/`:as`/`:limit`/`:xform` option forms, and option-wrapped map keys; exact validation remains |
| transaction EDN text | n/a | partial | `transact-text` covers `:db/add`, `:db/retract`, `:db/retractEntity`, `:db.fn/retractAttribute`, `:db.fn/cas`, map tx-data, lookup refs, idents, tempids, generated map ids, and nested maps through the normal transaction engine |

## Host Or Later Runtime APIs

These are useful, but not the next engine-parity gate.

| Namespace | Upstream tests | Status | Notes |
| --- | ---: | --- | --- |
| `conn.cljc` | 2 | partial | conn-from-db/from-datoms and reset reports covered |
| `listen.cljc` | 1 | partial | named report-sink listeners covered; arbitrary callback/closure API remains |
| `serialize.cljc` | 5 | partial | `init-db` from datoms covered; text/EDN/JSON serialization format later |
| `storage.clj` | 5 | planned | durable/storage work later |
| `datafy.cljc` | 1 | host | Clojure-specific shape |
| `db.cljc` | 4 | partial | semantic DB diff covered; hash/cache/uuid/record behavior is host |
| `issues.cljc` | 5 | planned | triage individually; some may already be covered elsewhere |
| `query_v3.cljc` | 2 | planned | triage after query parity pass |
| `explode.cljc` | 4 | host | likely implementation/debug tooling specific |
| `lru.cljc` | 2 | host | DataScript implementation detail |
| `core.cljc` | 1 | host | test harness/runtime behavior |
| `cljs.cljc` | 0 | host | ClojureScript environment behavior |

## Batch Order

1. Broaden parser/EDN APIs around pull options, return-map markers, source-qualified rules, and exact
   validation now that the first query/tx text paths are active.
2. Port remaining `transact.cljc`, `upsert.cljc`, and `validation.cljc`
   semantics that are not host-runtime-specific.
3. Finish tuple/index public API behavior as one schema/index batch.
4. Continue EDN/interoperability API coverage; primary collection DB rows now
   have a text API path, while Kvist native code can also use named sources.
