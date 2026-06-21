# DataScript Test Port Ledger

This tracks direct ports from `../datascript/test/datascript/test/*.cljc`.
See [datascript-test-matrix.md](datascript-test-matrix.md) for the broader
namespace classification and batch order.

`subset` means Vev has local tests for the main behavior, but not every
DataScript assertion or exact Clojure API shape.

| Namespace | Upstream deftests | Vev status | Next gap |
| --- | ---: | --- | --- |
| `query_find_specs.cljc` | 1 | covered | Vev result helpers cover collection, tuple, scalar, cut, and aggregate find specs |
| `query_aggregates.cljc` | 1 | subset | custom aggregates |
| `query_not.cljc` | 5 | subset | source-prefixed forms plus nested inherited/override source covered; error behavior remains |
| `query_or.cljc` | 5 | subset | source-prefixed forms plus nested inherited/override source covered; exact validation errors remain |
| `query_pull.cljc` | 7 | subset | multi-source pull |
| `query_rules.cljc` | 4 | subset | source-qualified relation rule calls and fixpoint recursion covered; host predicate inputs, validation, and semi-naive performance remain |
| `query_fns.cljc` | 6 | subset | more built-in predicates covered; arbitrary host functions |
| `query_return_map.cljc` | 1 | covered | Vev uses keyed rows with keyword/string/symbol key kinds instead of Clojure maps |
| `lookup_refs.cljc` | 5 | subset | exact invalid lookup-ref error messages |
| `ident.cljc` | 4 | covered | Vev returns ref values as `Value.Entity`; behavior is otherwise covered |
| `entity.cljc` | 6 | subset | Clojure equality/hash/print/cache protocol semantics |
| `pull_api.cljc` | 17 | subset | xform/visitor options and exact collection/scalar render shape |
| `components.cljc` | 2 | subset | schema validation errors and exact entity/touch render shapes |
| `transact.cljc` | 22 | subset | current tx tempids, forward/cyclic tempid ref values, map-valued datom compare/lookup, unused value-tempid rejection, and CAS tempid rejection covered; tx functions and exact errors remain |
| `upsert.cljc` | 8 | subset | unique cardinality-many, unique-value no-upsert, redefining tempids, forward string tempid refs, and non-upsert of new ref tempids covered; remaining conflict matrix and messages |
| `db.cljc` | 4 | partial | datom/index API compatibility |
| `index.cljc` | 5 | partial | main order, checked indexed-attribute errors, and sequence compare covered; finish exact public index surface |
| `tuples.cljc` | 14 | partial | tuple value/component upsert, tuple lookup-ref queries, multi-component unique updates, direct tuple attr add/retract validation, and invalid tuple schema shapes covered; remaining tuple conflict matrix and exact errors |
| `validation.cljc` | 2 | partial | bad transaction forms and exact validation errors |
| `parser*.cljc` | 19 | missing | EDN/text parser |
| `conn.cljc`, `listen.cljc`, `filter.cljc`, `serialize.cljc`, `datafy.cljc` | 15 | later | app/runtime APIs after core parity |

Near-term rule: port one namespace at a time, and only mark `covered` when the
remaining differences are intentional non-Clojure API shape differences.
