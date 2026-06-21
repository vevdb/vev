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
| `query.cljc` | 11 | partial | port remaining basic query cases as one batch |
| `query_find_specs.cljc` | 1 | passing | keep covered |
| `query_fns.cljc` | 7 | partial | decide host function surface; port all built-in function cases |
| `query_not.cljc` | 5 | partial | insufficient-binding/error cases and default-source variants |
| `query_or.cljc` | 5 | partial | validation/error cases and default-source variants |
| `query_pull.cljc` | 8 | partial | multi-source pull after query source semantics are settled |
| `query_return_map.cljc` | 1 | passing | keep covered with Vev keyed-row shape |
| `query_rules.cljc` | 3 | partial | rule source args, validation, and broader recursion/fixpoint behavior |
| `query_aggregates.cljc` | 1 | partial | remaining built-ins and exact edge cases; custom aggregates later |
| `transact.cljc` | 19 | partial | current tx tempids covered; tx functions, bad forms, exact no-op/retract behavior remain |
| `upsert.cljc` | 6 | partial | full conflict matrix |
| `validation.cljc` | 2 | partial | bad transaction forms and schema validation errors |
| `index.cljc` | 5 | partial | checked indexed-attribute errors now started; finish exact index surface |
| `tuples.cljc` | 11 | partial | remaining schema validation and tuple upsert/conflict matrix |
| `lookup_refs.cljc` | 5 | partial | exact invalid lookup-ref behavior |
| `ident.cljc` | 4 | passing | keep covered |
| `components.cljc` | 2 | partial | exact schema validation and render/touch shapes |
| `pull_api.cljc` | 17 | partial | xform/visitor options and exact collection/scalar shapes |
| `entity.cljc` | 6 | partial | engine-relevant entity reads are covered; Clojure protocol behavior is host |

## Parser And Interop

These matter for broad consumption, but they should not block the in-memory
semantic engine. They become active once EDN string APIs and prepared queries
exist.

| Namespace | Upstream tests | Status | Notes |
| --- | ---: | --- | --- |
| `parser.cljc` | 3 | interop | EDN reader/parser frontend |
| `parser_find.cljc` | 4 | interop | query parser |
| `parser_query.cljc` | 1 | interop | query parser |
| `parser_return_map.cljc` | 1 | interop | query parser |
| `parser_rules.cljc` | 3 | interop | query parser plus rules |
| `parser_where.cljc` | 6 | interop | query parser |
| `pull_parser.cljc` | 1 | interop | pull parser |

## Host Or Later Runtime APIs

These are useful, but not the next engine-parity gate.

| Namespace | Upstream tests | Status | Notes |
| --- | ---: | --- | --- |
| `conn.cljc` | 2 | planned | connection API after semantic core |
| `listen.cljc` | 1 | planned | listener API after connection surface |
| `filter.cljc` | 1 | planned | filtered DB API later |
| `serialize.cljc` | 5 | planned | serialization format decision later |
| `storage.clj` | 5 | planned | durable/storage work later |
| `datafy.cljc` | 1 | host | Clojure-specific shape |
| `db.cljc` | 4 | partial | diff/index pieces are semantic; hash/cache record behavior is host |
| `issues.cljc` | 5 | planned | triage individually; some may already be covered elsewhere |
| `query_v3.cljc` | 2 | planned | triage after query parity pass |
| `explode.cljc` | 4 | host | likely implementation/debug tooling specific |
| `lru.cljc` | 2 | host | DataScript implementation detail |
| `core.cljc` | 1 | host | test harness/runtime behavior |
| `cljs.cljc` | 0 | host | ClojureScript environment behavior |

## Batch Order

1. Finish `index.cljc` and `tuples.cljc` semantic cases.
2. Port `transact.cljc`, `upsert.cljc`, and `validation.cljc` as one tx/schema
   batch.
3. Port remaining query namespaces by feature cluster, not one assertion at a
   time.
4. Only then start parser/EDN API work for interop.
