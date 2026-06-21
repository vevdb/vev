# DataScript Test Port Ledger

This tracks direct ports from `../datascript/test/datascript/test/*.cljc`.

`subset` means Vev has local tests for the main behavior, but not every
DataScript assertion or exact Clojure API shape.

| Namespace | Upstream deftests | Vev status | Next gap |
| --- | ---: | --- | --- |
| `query_find_specs.cljc` | 1 | covered | Vev result helpers cover collection, tuple, scalar, cut, and aggregate find specs |
| `query_aggregates.cljc` | 1 | subset | custom aggregates |
| `query_not.cljc` | 5 | subset | error behavior and default-source forms |
| `query_or.cljc` | 5 | subset | 2-column source-backed relation clauses inside `or-join` |
| `query_pull.cljc` | 7 | subset | returning pattern values, multi-source pull |
| `query_rules.cljc` | 3 | subset | rule sources, validation, unbounded fixpoint |
| `query_fns.cljc` | 6 | subset | arbitrary host functions |
| `query_return_map.cljc` | 1 | covered | Vev uses keyed rows with keyword/string/symbol key kinds instead of Clojure maps |
| `lookup_refs.cljc` | 5 | subset | exact invalid lookup-ref error messages |
| `ident.cljc` | 4 | covered | Vev returns ref values as `Value.Entity`; behavior is otherwise covered |
| `entity.cljc` | 6 | subset | Clojure equality/hash/print/cache protocol semantics |
| `pull_api.cljc` | 17 | subset | xform/visitor options and exact collection/scalar render shape |
| `components.cljc` | 2 | subset | schema validation errors and exact entity/touch render shapes |
| `transact.cljc` | 19 | subset | tx functions and exact errors |
| `upsert.cljc` | 6 | subset | full conflict matrix and messages |
| `db.cljc` | 4 | partial | datom/index API compatibility |
| `index.cljc` | 5 | partial | exact indexed-attribute error behavior |
| `tuples.cljc` | 11 | partial | remaining tuple conflict matrix and schema validation |
| `validation.cljc` | 2 | partial | bad transaction forms and exact validation errors |
| `parser*.cljc` | 19 | missing | EDN/text parser |
| `conn.cljc`, `listen.cljc`, `filter.cljc`, `serialize.cljc`, `datafy.cljc` | 15 | later | app/runtime APIs after core parity |

Near-term rule: port one namespace at a time, and only mark `covered` when the
remaining differences are intentional non-Clojure API shape differences.
