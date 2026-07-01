# Next Steps

This is the current todo list for Vev. Keep it focused on remaining work only;
do not use this file as a historical changelog.

## Current Gate

Durable storage architecture is the active gate.

Vev already has in-memory DataScript/Datomic-shaped semantics, host interop,
MusicBrainz coverage, source-backed durable reads, storage-neutral `Store-DB`
handles, and a growing physical query operator layer over `DB-Read-Source`.

The remaining goal is to make durable DB values production-shaped:

- normal durable reads should use Vev-owned persisted/chunked indexes, not a
  full datom-log replay on open
- immutable DB snapshots should share index storage instead of copying whole
  datom/index arrays on every commit
- query, pull, entity, rules, `with`, `db-with`, and host APIs must continue to
  expose ordinary immutable Datomic-like DB values
- SQLite remains the durable log/page store, not the Datalog executor

## 1. Unify Query Execution Around `DB-Read-Source`

Status: in progress.

Todo:

- Thread `DB-Read-Source` into the ordinary resident query engine, not only the
  source-backed runner.
- Collapse the split between older `DB` / `DB-Source` entry points and the
  newer source-backed query path.
- Move resident query result ownership toward the same `Query-Result` shape used
  by source-backed and host-facing paths.
- Keep resident-array mode available as an implementation strategy for tiny DBs,
  tests, and debugging, but stop making it a query-engine assumption.

Acceptance:

- resident and source-backed query entry points share the same logical operator
  boundary
- result cleanup semantics are explicit and host-safe
- DataScript compatibility tests remain green

## 2. Finish Source-Native Physical Query Operators

Status: in progress.

Todo:

- Continue replacing generic `Binding` row materialization with typed/source
  relation operators where it matters.
- Convert the current set of source recognizers into reusable indexed scan,
  bind-join, merge/star, projection, and aggregate operator pieces.
- Add source-aware operators for remaining common graph traversal and broader
  multi-clause shapes exposed by MusicBrainz and Datalevin-style benchmarks.
- Continue predicate pushdown for source-aware predicate combinations beyond a
  single AVET range driver with scalar same-entity projections.
- Keep benchmark improvements tied to general operators and common query shapes,
  not query-text-specific shortcuts.
- Keep `Query-Stats` useful enough to show when a query crossed from physical
  source/typed operators into generic binding materialization.

Acceptance:

- important source-backed query shapes report zero binding materialization
- MusicBrainz query matrix stays green
- Datalevin-style read benchmark regressions are explainable by operator gaps,
  not hidden fallback paths

## 3. Replace Host `with` / `db-with` Materialization Bridge

Status: in progress.

Todo:

- Finish replacing `with-store-db-*` / `db-with-store-db-*` in
  `src/vev/storage.kvist`, which still call `store-db-materialize-db` for
  transaction shapes outside the current source-native append subset.
- Keep the current shared `db-with-store-db-*` handle behavior: shared DB
  values should publish another shared `Store-DB`, not a resident clone.
- Keep the direct source-native shared `db-with` fast path limited to true
  append-only changes until current-index invalidation is represented in shared
  storage.
- Migrate remaining language wrappers to expose C ABI transaction report
  `db-before` / `db-after` handles ergonomically. Python, Rust, JVM/Clojure,
  Go, and Node now expose them; Odin still needs wrapper-level methods.
- Move transaction-function `with` variants off the resident callback path where
  possible, while preserving callback access to transaction-local DB state.
- Broaden source-only shared `db-with` resolution for any remaining unsupported
  transaction shapes, especially complex same-transaction lookup/upsert cases
  and schema-changing transactions that still need fallback validation.
- Reuse the existing source-aware transaction resolver from shared storage:
  `shared-write-state-resolve-tx-data`, `resolve-shared-source-tx-segment!`,
  and `tx-ops-overlay-db-read-source`.
- Apply transactions against a source-native write-state overlay, then publish a
  `Store-DB` value backed by shared/chunked state instead of a resident clone.
- Preserve exact Datomic-like immutable DB semantics for `db-before`,
  `db-after`, transaction reports, tempids, listeners, and retained old DB
  handles.
- Keep transaction functions able to read earlier same-transaction writes through
  a transaction-local `DB-Read-Source` overlay.

Acceptance:

- host-visible `with` / `db-with` works for durable/shared DB values without
  rebuilding a full resident DB
- old DB handles remain valid after later commits
- transaction report semantics remain unchanged

## 4. Reduce Commit-Time Copying

Status: in progress.

Todo:

- Continue moving shared publication away from full resident `db-after`
  construction.
- Make shared immutable index chunks the normal commit publication path for
  append-heavy workloads.
- Keep the transaction engine producing the metadata storage needs instead of
  making storage adapters recompute append-only/new-entity decisions.
- Decide which shared chunk/page reuse primitives are broadly useful and which
  should remain benchmark-only experiments.
- Preserve resident-array publication as a compatibility/small-DB strategy.

Acceptance:

- `snapshot-heavy` and retained-DB-handle workloads are not dominated by
  whole-array copies
- batch-1 and mixed write benchmarks improve without reportless shortcuts
- old DB handles remain valid and cheap to retain

## 5. Durable Reopen Without Rebuild

Status: partially working for read-only/source-backed paths.

Todo:

- Make storage-neutral durable `connect` / `db` expose the chunk-backed
  `Store-DB` read path as the normal public path.
- Keep datom-log replay available for recovery, validation, migration, and
  fallback.
- Ensure C ABI, JVM, Clojure, and other host DB snapshot handles use the
  storage-neutral durable DB value path by default.
- Turn any remaining resident-required path into either a source-backed
  implementation or a clear public error.

Acceptance:

- reopening a durable DB for ordinary reads no longer scales with total datom
  count
- `vev/connect`, `d/db`, and C ABI DB handles preserve Datomic-like API shape
- MusicBrainz reopen/query benchmarks include both compatibility resident
  rebuild and chunk-backed normal read paths

## 6. Benchmark And Regression Matrix

Status: ongoing.

Todo:

- Maintain benchmark output as ratios against relevant baselines, not only raw
  timings.
- Run MusicBrainz open/query/reopen after storage changes.
- Run write-bench variants after shared index/write-state changes:
  `snapshot-heavy`, `shared-snapshot-heavy`, `shared-snapshot-heavy-direct`,
  `shared-store-db-heavy`, `pure --batch 1`, and `mixed`.
- Run DataScript/Datalevin read benchmarks periodically as regression checks,
  not as the active storage gate.
- Track regressions against the architecture batch that caused them.

Acceptance:

- benchmark docs show current ratios and known bottlenecks
- no benchmark requires a public API shape different from the Datomic-like API
- performance work remains attached to correctness and architecture goals

## Later Work

Important, but not the current gate:

- exact parser diagnostic/object parity
- generic SCC-local semi-naive recursive rule fallback
- full Maven/Clojars/PyPI/npm/crates.io publication polish
- optional server/transactor packaging mode
- replication/sync primitives
