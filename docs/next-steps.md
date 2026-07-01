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
- source-resolvable `with` / `db-with` should publish immutable overlay DB
  values for shared and SQLite snapshots, not materialize resident clones
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
- Continue moving relation-source inputs from generic binding execution toward
  typed/source physical operators. `$rows` / collection / map-relation source
  inputs are now represented as relation-value `DB-Read-Source` entries.
  Straight-line source relation clauses, predicates, scalar functions, and
  `ground` now execute through typed relation operators for source-backed
  `DB-Read-Source` queries. Source-backed `not` / `not-join` now use the same
  typed relation-value operators as anti-joins, and source-backed `or` /
  `or-join` union typed branch outputs over relation-value sources. Aggregate
  queries over relation-value sources now aggregate directly from typed
  relation columns for built-in aggregates and named custom/native aggregate
  functions.
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
  relation operators where it matters. The common source relation input path is
  now typed for straight-line clauses, source `not` / `not-join` anti-joins,
  source `or` / `or-join` branches, and aggregate results over typed
  relation-value source results; the remaining source-input fallback is broader
  multi-source shapes and more reusable operator composition.
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

- Continue replacing `with-store-db-*` / `db-with-store-db-*` fallback paths
  that still call `store-db-materialize-db` for unsupported transaction shapes.
  Source-backed `with` reports now fail explicitly instead of silently
  rebuilding a resident DB when the source resolver cannot publish an overlay;
  remaining work is to either add source support for those rejected shapes or
  keep the public error if the shape is intentionally unsupported. The older
  resident-shaped `with-store-db-text` / `with-store-db-tx-data` helpers now
  reject source-backed `Store-DB` values directly and point callers at the
  storage-neutral report helpers instead of materializing a resident `Tx-Report`.
- Keep current shared and SQLite `db-with-store-db-*` handle behavior: source
  DB values should publish another source/overlay `Store-DB`, not a resident
  clone. C ABI `vev_db_with_edn` and typed builder `vev_tx_db_with` now derive
  the returned DB handle from the storage-neutral `with` report path and return
  null on failed or unsupported source-backed reports instead of using the
  legacy no-error materializing helper. The legacy no-error `db-with-store-db-*`
  helper now also routes source-backed snapshots through the checked source
  report path after the direct append/overlay fast paths; unsupported
  source-backed shapes retain the original DB value instead of silently
  materializing a resident clone.
- Keep direct source-native `db-with` fast paths limited to shapes whose
  current-index effects are represented in the source overlay.
- Keep source transaction-function `Store-DB` report variants on the
  source-overlay path, with callbacks reading transaction-local `DB-Read-Source`
  state. Move older resident `DB` callback transaction-function variants off the
  resident path only where an equivalent source callback shape exists.
- Broaden source-only `db-with` resolution for remaining unsupported
  transaction shapes. Existing-schema nested map and cardinality-many map
  collection forms stay source-native, as do same-transaction ref schema plus
  nested map values and same-transaction cardinality-many schema plus map
  collection values. Same-transaction lookup refs and identity tempid upserts
  stay source-native for shared and SQLite Store-DB overlays. Same-transaction
  ref schema plus reverse ref add/retract forms stay source-native for shared
  and SQLite Store-DB overlays. Existing-schema reverse refs with lookup-ref
  and vector values, plus reverse refs with nested map values, stay
  source-native for shared and SQLite Store-DB overlays. `db-with` over shared
  and SQLite Store-DB snapshots stays source-native for `:db.fn/retractAttribute`,
  `:db/retractEntity`, and `:db/cas`. Ref-valued string tempids and negative
  int tempids stay source-native for shared and SQLite Store-DB overlays,
  including tempid ref values through ref attrs declared in the same
  transaction. `:db/current-tx` in value and entity positions stays
  source-native for shared and SQLite Store-DB overlays. Same-transaction
  `:db/ident` declarations can be referenced as entity ids and ref values by
  later tx forms without leaving the source-native path. Unique-identity tempid
  upserts where the unique value is a ref tempid that resolves through another
  identity upsert also stay source-native for shared and SQLite Store-DB
  overlays. CAS expected lookup refs now resolve through earlier
  same-transaction source-overlay writes, same-transaction ref schema, and
  source-resolvable lookup refs even when the lookup attr is also written
  elsewhere in the transaction. CAS validation reads prior resolved overlay ops
  before falling back to the durable source, matching resident transaction
  ordering more closely. Overlay publication also preserves cardinality-one
  replacement semantics inside the overlay while respecting cardinality-many
  schema declared earlier in the same transaction.
  Source transaction functions work for `with` reports and `db-with` on
  shared/SQLite Store-DB snapshots and overlay DB values. Chained overlays
  preserve per-transaction tx identity by publishing multi-transaction datom
  overlays when a `with` is applied to an existing overlay DB value.
- Reuse the existing source-aware transaction resolver from shared storage:
  `shared-write-state-resolve-tx-data`, `resolve-shared-source-tx-segment!`,
  and `tx-ops-overlay-db-read-source`.
- Apply all supported transaction shapes against a source-native write-state
  overlay, then publish a `Store-DB` value backed by shared/chunked state
  instead of a resident clone.
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
- Keep whole-DB datom copying out of transaction paths that have not yet
  validated. Transaction application now delays cloning the resident datom log
  until after operation validation succeeds; failed writes should not pay the
  publication copy cost.
- Keep the live shared write-state metadata incremental. Ordinary non-schema
  commits advance cached tx/entity counters without cloning/scanning schema
  metadata from the resident `db-after`. Same-transaction schema declaration
  additions and additions to schema entities that already have `:db/ident` in
  the source now update cached ref, unique, unique-identity, tuple-schema, tx,
  and entity metadata incrementally. Schema retractions for identifiable schema
  attrs now sync only the affected cached ref/unique/unique-identity/tuple flags
  from the post-transaction DB. Schema facts whose attr identity cannot be
  resolved from the tx/source still fall back to refreshing from the resident
  `db-after` until the write-state owns enough metadata to update those cases
  without a full scan.
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
  storage-neutral durable DB value path by default. Prepared statement DB
  sources now retain `Store-DB` handles and execute through `DB-Read-Source`,
  including collection, tuple, relation, and map relation `:in` inputs for
  source-backed snapshots. Prepared DB column batches also execute through
  `Store-DB` / `DB-Read-Source` for durable snapshots. Prepared direct pull,
  lookup-ref pull, pull-many, and UUID lookup-ref pull-many now use storage
  helpers over `Store-DB` / `DB-Read-Source` instead of resident DB pointers.
  Prepared query profile EDN also executes over `Store-DB` / `DB-Read-Source`.
  Typed entity, string, entity/int-pair, and entity/string/int-triple prepared
  C ABI convenience paths now execute over `DB-Read-Source` and convert the
  result shape at the ABI boundary. Prepared query-with-rules DB snapshot
  paths now execute over `Store-DB` / `DB-Read-Source`; source-backed rule
  evaluation currently covers non-recursive rule bodies made from source
  clauses, predicates, scalar functions, ground, get-else, get-some,
  not/missing?, and or/or-join.
- Source-backed DB snapshot `with` reports with C ABI transaction functions now
  use source-backed `Store-DB` transaction-function resolution, including
  transaction-local overlay reads and owned callback tx-data returned from the
  ABI. Source-backed callbacks now expose a retained/cloned `Store-DB` handle
  for retainable shared, SQLite, and overlay read sources, so host callbacks can
  query the temporary callback DB through the normal storage-neutral
  `DB-Read-Source` path instead of forcing resident materialization. Resident
  in-memory DB snapshots keep the existing resident callback path. Non-retainable
  callback sources now fail explicitly instead of materializing a resident DB
  behind the ABI.
- Turn any remaining resident-required path into either a source-backed
  implementation or a clear public error. The known intentional resident path
  is `vev_conn_from_db`, which can only create a live mutable in-memory
  connection from resident DB values.

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
