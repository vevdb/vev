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

Storage-neutral durable transaction reports now have a SQLite source-direct
path for source-resolved writes. The direct path resolves tx-data against
`DB-Read-Source`, appends durable add/retract datoms, writes new persisted index
roots, and returns SQLite-backed `Store-DB` handles for `db-before` and
`db-after` without loading the resident DB. Covered shapes include add,
cardinality-one replacement, explicit retract, retract-attribute,
retract-entity, lookup refs, nested maps through ref attrs, tempids in ref
values, unique-identity upserts, tuple-identity upserts with tempid/lookup
components, transaction metadata, current-tx aliases, and CAS.

Remaining work:

- broaden the direct path to all ordinary DataScript/Datomic transaction shapes
  already supported by the source overlay resolver, including remaining
  multi-step tempid/upsert combinations and source transaction functions
- make unsupported source-backed durable transaction reports fail with clear
  public errors instead of falling back through resident materialization
- move the older resident-shaped `Tx-Report` public compatibility path toward
  the storage-neutral `Store-Tx-Report` shape
- keep live connection tx metadata, basis, tempids, listeners, and retained old
  DB handles semantically identical to the in-memory engine

Acceptance:

- normal durable writes do not require `store-resident-db-available?`
- retained `db-before` and `db-after` remain queryable after later commits
- DataScript transaction compatibility tests remain green over durable handles

## 2. Reduce Commit-Time Copying

Status: active.

Remaining work:

- make shared immutable index chunks the normal commit publication path for
  append-heavy workloads
- keep transaction validation and write-state metadata incremental, so failed
  writes and non-schema writes do not clone full datom/index arrays
- make the transaction engine return enough publication metadata that storage
  adapters do not recompute append/new-entity decisions independently
- preserve resident-array publication only as a small-DB/debug compatibility
  strategy

Acceptance:

- snapshot-heavy and retained-DB-handle benchmarks are not dominated by
  whole-array copies
- batch-1 and mixed write benchmarks improve without reportless shortcuts
- old DB handles remain valid and cheap to retain

## 3. Finish Query Execution Around `DB-Read-Source`

Status: active.

Remaining work:

- collapse the split between older resident `DB` entry points and the
  source-backed query path
- keep resident-array mode as an implementation strategy, not a query-engine
  assumption
- move resident result ownership toward the same `Query-Result` shape used by
  source-backed and host-facing paths
- keep `Query-Stats` explicit about when a query uses physical source operators
  versus generic binding materialization

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
