# DataScript Test Port Ledger

This tracks direct ports from `../datascript/test/datascript/test/*.cljc`.

`subset` means Vev has local tests for the main behavior, but not every
DataScript assertion or exact Clojure API shape.

| Namespace | Upstream deftests | Vev status | Next gap |
| --- | ---: | --- | --- |
| `query_find_specs.cljc` | 1 | subset | exact result container shape |
| `query_aggregates.cljc` | 1 | subset | `median`, `variance`, `stddev`, custom aggregates |
| `query_not.cljc` | 5 | subset | error behavior and default-source forms |
| `query_or.cljc` | 5 | subset | relation-source `or-join` |
| `query_pull.cljc` | 7 | subset | returning pattern values, multi-source pull |
| `query_rules.cljc` | 3 | subset | rule sources, validation, unbounded fixpoint |
| `query_fns.cljc` | 6 | subset | arbitrary host functions |
| `query_return_map.cljc` | 1 | subset | exact map/symbol return shape |
| `lookup_refs.cljc` | 5 | subset | collection and keyword-valued input refs |
| `ident.cljc` | 4 | subset | exact edge errors |
| `entity.cljc` | 6 | subset | equality/hash/print/cache semantics |
| `pull_api.cljc` | 17 | subset | recursion and exact collection/scalar render shape |
| `components.cljc` | 2 | subset | exact entity/touch reverse shapes |
| `transact.cljc` | 19 | subset | tx functions and exact errors |
| `upsert.cljc` | 6 | subset | full conflict matrix and messages |
| `db.cljc` | 4 | partial | datom/index API compatibility |
| `index.cljc` | 5 | partial | API-level datoms/index/rseek coverage |
| `tuples.cljc` | 11 | missing | tuple attrs |
| `validation.cljc` | 2 | partial | full schema validation behavior |
| `parser*.cljc` | 19 | missing | EDN/text parser |
| `conn.cljc`, `listen.cljc`, `filter.cljc`, `serialize.cljc`, `datafy.cljc` | 15 | later | app/runtime APIs after core parity |

Near-term rule: port one namespace at a time, and only mark `covered` when the
remaining differences are intentional non-Clojure API shape differences.
