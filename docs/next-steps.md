# Next Steps

This is the current todo list for Vev. Keep it focused on remaining work only;
do not use this file as a historical changelog.

## Current Gate

Durable storage architecture is the active gate.

Vev has in-memory DataScript/Datomic-shaped semantics, host interop,
MusicBrainz coverage, source-backed durable reads, storage-neutral `Store-DB`
handles, and a physical query layer over `DB-Read-Source`.

The remaining goal is to make durable DB values production-shaped:

- opening/reopening a durable DB for reads must not rebuild the full resident DB
- immutable DB values must share persisted/chunked index storage across commits
- durable transaction reports must publish `db-before` / `db-after` handles
  without resident materialization for normal transaction shapes
- query, pull, entity, rules, `with`, `db-with`, and host APIs must expose
  ordinary immutable Datomic-like DB values
- SQLite remains the durable log/page store, not the Datalog executor

## 1. Finish Durable Write Publication Without Resident DBs

Status: active.

Storage-neutral durable transaction reports now cover the normal SQLite
source-direct write path, shared-store source-function reports, and SQLite
parse/read-only/direct-error report failures. Supported writes resolve tx-data
against `DB-Read-Source`, append durable add/retract datoms, publish new index
roots, and return retained `Store-DB` handles for `db-before` and `db-after`
without resident materialization. Source-backed report failures also return
storage-neutral DB handles instead of falling back through resident clones.

Implemented:

- storage-neutral transaction-report APIs return empty `Store-DB` handles for
  unopened-store failures instead of manufacturing empty resident DBs
- SQLite `Store-Tx-Report` failures before the first durable index root now
  also return empty storage handles instead of wrapping an empty resident DB
- the CLI transaction command now uses `transact-store-report-text` and
  `Store-Tx-Report` output instead of the older resident `Tx-Report`
  compatibility path

Remaining work:

- move older public `Tx-Report` compatibility APIs toward the
  storage-neutral `Store-Tx-Report` shape where host-facing code needs
  immutable DB values; normal SQLite `Store-Tx-Report` writes and source
  transaction-function writes are already storage-neutral unless the connection
  has explicitly entered resident compatibility mode
- reduce remaining resident fallback shapes to explicit unsupported-shape errors
  or small-DB/debug compatibility, never a hidden path for normal durable writes
- keep live connection tx metadata, basis, tempids, listeners, and retained old
  DB handles semantically identical to the in-memory engine

Acceptance:

- normal durable writes do not require `store-resident-db-available?`
- retained `db-before` and `db-after` remain queryable after later commits
- DataScript transaction compatibility tests remain green over durable handles

## 2. Reduce Commit-Time Copying

Status: active.

Implemented:

- direct SQLite append writes for new entities can publish EAVT as a recursive
  parent root that shares the previous EAVT root and only adds new leaf chunks
- SQLite source-function commits use the same EAVT append-root publication when
  the function-expanded transaction data only appends new entities
- source-backed transaction reports now carry append-publication metadata
  through to SQLite commit, so source-function commits do not reread write-state
  metadata just to rediscover append-root eligibility
- source-backed transaction reports now carry per-index append-mode proofs for
  EAVT, AEVT, AVET, and VAET, and SQLite commit uses those proofs directly
  instead of rebuilding index eligibility decisions
- SQLite publication now chooses append-root sharing independently for EAVT,
  AEVT, AVET, and VAET when the appended segment is proven to be after the
  previous root in that index's own key order
- recursive persisted roots are readable through both full debug reads and
  page-bounded lazy index reads, so retained DB handles do not need to flatten
  the whole root for normal paged source scans
- empty/no-op index chunks have stable metadata bounds, so canceling
  add/retract transactions keep the storage schema valid
- source-derived shared write-state metadata no longer allocates an empty
  resident DB just to carry tx/listener metadata; live shared connections still
  explicitly own their resident DB, while durable/source write helpers stay
  metadata-only

Remaining work:

- keep transaction validation and write-state metadata incremental beyond the
  current source-derived metadata path, so failed writes and non-schema writes
  do not clone full datom/index arrays
- preserve resident-array publication only as a small-DB/debug compatibility
  strategy

Acceptance:

- snapshot-heavy and retained-DB-handle benchmarks are not dominated by
  whole-array copies
- batch-1 and mixed write benchmarks improve without reportless shortcuts
- old DB handles remain valid and cheap to retain

## 3. Finish Query Execution Around `DB-Read-Source`

Status: active.

Implemented:

- live SQLite text, text-with-input, and prepared query wrappers now acquire a
  `Store-DB` snapshot and use the same `q-result-store-db-*` boundary as
  retained immutable DB handles, instead of each wrapper manually opening a
  SQLite snapshot and calling source functions directly
- reopened read-only durable stores are covered for prepared and text-with-input
  source-backed queries while remaining non-resident
- resident `q-result-text*` and `q-result-prepared*` wrappers now enter through
  `resident-db-read-source` and return owned `Query-Result` values, matching
  source-backed cleanup semantics while leaving lower-level resident
  `Result-Set` APIs intact
- `Query-Stats` now exposes explicit `source-operators` and
  `generic-materialization-steps` counters, so source-backed physical operator
  use is visible directly instead of inferred from scan/materialization counts
- no-input resident `q-text-profiled*` and `q-prepared-profiled*` wrappers now
  enter through `resident-db-read-source`, so ordinary text/prepared profiling
  uses the same source/operator boundary as durable `Store-DB` reads
- source-backed text queries with EDN input strings now compute profiling stats
  through the input-aware read-source stats helper, so host-facing input-text
  APIs report input bindings/source operators instead of no-input heuristics

Remaining work:

- collapse the split between older resident `DB` entry points and the
  source-backed query path
- keep resident-array mode as an implementation strategy, not a query-engine
  assumption
- keep moving lower-level resident query execution plus input/function/rule/
  named-source profiling toward the same source boundary where compatibility
  and operator coverage permit it

Acceptance:

- resident and source-backed query entry points share the same logical operator
  boundary
- result cleanup semantics are explicit and host-safe
- DataScript compatibility tests remain green

## 4. Grow Physical Query Operators

Status: active.

Remaining work:

- turn current source recognizers into reusable indexed scan, bind-join,
  merge/star, projection, and aggregate operators
- add source-aware operators for remaining common graph traversal and
  multi-clause shapes exposed by MusicBrainz and Datalevin-style benchmarks
- continue predicate/range pushdown beyond single AVET range drivers
- keep improvements attached to general operators and common query shapes, not
  query-text-specific shortcuts

Acceptance:

- important source-backed query shapes report zero binding materialization
- MusicBrainz query matrix stays green
- Datalevin-style read benchmark regressions are explainable by operator gaps

## 5. Benchmark And Regression Matrix

Status: ongoing.

Remaining work:

- keep benchmark output as ratios against relevant baselines
- run MusicBrainz open/query/reopen after storage changes
- run write-bench variants after shared index/write-state changes:
  `snapshot-heavy`, `shared-snapshot-heavy`, `shared-snapshot-heavy-direct`,
  `shared-store-db-heavy`, `pure --batch 1`, and `mixed`
- run DataScript/Datalevin read benchmarks periodically as regression checks

Acceptance:

- benchmark docs show current ratios and known bottlenecks
- no benchmark requires a public API shape different from the Datomic-like API
- performance work remains attached to correctness and architecture goals

## Later

Important, but not the current gate:

- exact parser diagnostic/object parity
- generic SCC-local semi-naive recursive rule fallback
- full Maven/Clojars/PyPI/npm/crates.io publication polish
- optional server/transactor packaging mode
- replication/sync primitives
