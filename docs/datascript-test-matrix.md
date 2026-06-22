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
| `query.cljc` | 11 | partial | named collection datom sources covered; primary collection DB syntax, host functions, and exact input errors remain |
| `query_find_specs.cljc` | 1 | passing | keep covered |
| `query_fns.cljc` | 7 | partial | built-ins covered; decide host function surface and exact error behavior |
| `query_not.cljc` | 5 | partial | source semantics covered; insufficient-binding/error cases remain |
| `query_or.cljc` | 5 | partial | source semantics covered; validation/error cases remain |
| `query_pull.cljc` | 8 | partial | multi-source pull covered; finish exact find-spec return shapes |
| `query_return_map.cljc` | 1 | passing | keep covered with Vev keyed-row shape |
| `query_rules.cljc` | 3 | partial | source args, recursion, and repeated calls covered; validation and semi-naive performance remain |
| `query_aggregates.cljc` | 1 | partial | remaining built-ins and exact edge cases; custom aggregates later |
| `transact.cljc` | 19 | partial | native tx functions and tempids-outside-add validation covered; ident-stored tx functions and exact errors remain |
| `upsert.cljc` | 6 | partial | vector tx tempid ordering, unique-value no-upsert, current-tx conflict, and main conflict matrix covered; exact messages remain |
| `validation.cljc` | 2 | partial | runtime bad tx-data validation covered; reader/macro bad forms and exact errors remain |
| `index.cljc` | 5 | partial | main index surface covered; exact indexed-attribute errors remain |
| `tuples.cljc` | 11 | partial | remaining schema validation and tuple upsert/conflict matrix |
| `lookup_refs.cljc` | 5 | partial | mixed entity-id inputs covered; exact invalid lookup-ref behavior remains |
| `ident.cljc` | 4 | passing | keep covered |
| `components.cljc` | 2 | partial | exact schema validation and render/touch shapes |
| `pull_api.cljc` | 17 | partial | xform/visitor options and exact collection/scalar shapes |
| `entity.cljc` | 6 | partial | engine-relevant entity reads are covered; Clojure protocol behavior is host |
| `filter.cljc` | 1 | partial | materialized filtered DBs cover query/entity/index-visible semantics; exact hash/equality/runtime wrapper behavior remains |

## Parser And Interop

These matter for broad consumption. The first query and transaction text APIs
now exist, but full parser parity still trails the native Kvist literal surface.

| Namespace | Upstream tests | Status | Notes |
| --- | ---: | --- | --- |
| `parser.cljc` | 3 | partial | flat EDN node reader supports nested vectors/lists/maps and now feeds query/tx text subsets; full EDN lowering remains |
| `parser_find.cljc` | 4 | interop | query parser |
| `parser_query.cljc` | 1 | partial | EDN-reader-backed text query subset covers `:find`, `:in`, optional `:where`, and execution through normal query inputs; validation remains |
| `parser_return_map.cljc` | 1 | interop | query parser |
| `parser_rules.cljc` | 3 | interop | query parser plus rules |
| `parser_where.cljc` | 6 | partial | data pattern, predicate, built-in function, missing?, not, and or clauses covered; rules and source inputs remain |
| `pull_parser.cljc` | 1 | partial | query text pull finds cover attrs, wildcard, and nested maps; options remain |
| transaction EDN text | n/a | partial | `transact-text` covers `:db/add`, `:db/retract`, `:db/retractEntity`, `:db.fn/retractAttribute`, `:db.fn/cas`, map tx-data, lookup refs, idents, tempids, generated map ids, and nested maps through the normal transaction engine |

## Host Or Later Runtime APIs

These are useful, but not the next engine-parity gate.

| Namespace | Upstream tests | Status | Notes |
| --- | ---: | --- | --- |
| `conn.cljc` | 2 | partial | conn-from-db/from-datoms and reset reports covered; listeners remain |
| `listen.cljc` | 1 | planned | listener API after connection surface |
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

1. Broaden parser/EDN APIs around query rules, pull options, source inputs, and exact
   validation now that the first query/tx text paths are active.
2. Port remaining `transact.cljc`, `upsert.cljc`, and `validation.cljc`
   semantics that are not host-runtime-specific.
3. Finish tuple/index public API behavior as one schema/index batch.
4. Keep primary collection DB syntax for EDN/interoperability APIs; Kvist native
   code uses named collection sources for raw datom rows.
