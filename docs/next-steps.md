# Next Steps

This is the current concrete execution plan for Vev. It is intentionally
implementation-oriented: each batch should leave the system more production-like
without changing the public Datomic/DataScript-shaped API.

## Current Gate

Vev has enough in-memory DataScript/Datomic-shaped semantics, host interop, and
MusicBrainz coverage to make durable storage architecture the active work.
SQLite open/write/close/reopen/query works, but normal durable reopen still
rebuilds resident in-memory indexes from datom rows.

The current gate is:

- normal durable reads should use Vev-owned persisted index chunks instead of a
  full datom-log replay on open
- immutable DB snapshots should share index storage instead of copying whole
  datom/index arrays on every commit
- query, pull, entity, rules, and host APIs must keep seeing ordinary immutable
  DB values
- SQLite remains the durable log/page store, not the Datalog executor

## Batch 1: Query-Facing Durable Snapshot

Status: in progress.

Goal:

- make the existing `SQLite-DB-Snapshot` usable as a read-only query source
  through the same logical index boundary as resident `DB` values.

Implemented so far:

- `DB-Read-Source` can represent either a resident `DB` or a
  `SQLite-DB-Snapshot`.
- source-backed index scans can ask for `eavt`, `aevt`, `avet`, and `vaet`
  views without owning resident arrays.
- source-backed attr and entity+attr datom reads work over persisted SQLite
  index chunks.
- source-backed reads check currentness by resolving the matching
  entity/attr/value history, so retracted facts are filtered out instead of
  treating every persisted index entry as current.
- a simple source-backed pull-value helper can render `:db/id` plus forward
  attrs from a persisted snapshot.
- `q-text-db-read-source` parses EDN query text and executes the first
  source-backed query shapes: plain data clauses with normal `:find` variables
  over entity, attr, and value positions, including multi-clause joins and
  primary `$` source-qualified clauses. Ordered predicate filters over already
  materialized bindings and scalar function clauses such as
  `[(count ?name) ?len]` are also supported, including tuple/destructuring
  function outputs over owned source bindings. Flat literal pull finds such as
  `(pull ?e [:db/id :item/name])`, wildcard pulls such as `(pull ?e [*])`, flat
  reverse-ref pulls such as `(pull ?e [:item/_parent])`, nested forward-ref
  pulls such as `(pull ?e [{:item/parent [:item/name]}])`, and pull pattern
  inputs such as `(pull ?e ?pattern)` now render from the same source-backed
  snapshot without rebuilding a resident DB. Source-backed text queries also
  accept scalar `:in` values through direct `Query-Input` and EDN input text.
- `storage_architecture_test` now covers these paths against a
  `SQLite-DB-Snapshot`, including parsed query text, a multi-clause join, and a
  retraction case. It also checks that primary `$` source-qualified clauses work
  and named source-qualified clauses fail explicitly until multi-source durable
  querying is implemented, plus predicate filtering with both matching and empty
  results, scalar and destructuring function output, flat literal pull finds,
  wildcard pull finds, flat reverse-ref pull finds, nested forward-ref pull
  finds, scalar inputs, and pull pattern inputs through both direct `Query-Input`
  and EDN input text.
- `bench/sqlite_storage.kvist` now reports
  `persisted-db-snapshot-source-query` separately from raw entity/attr helpers
  and from `reopen-rebuild`.
- source-backed query result rows use owned value copies, and callers now have
  `delete-result-set-owned-values` for the matching cleanup shape.

Work:

1. Introduce or extend a query-facing DB source abstraction so clause scans can
   ask for `eavt`, `aevt`, `avet`, and `vaet` views without assuming resident
   arrays. This is started with `DB-Read-Source`.
2. Move ordinary data-clause scan paths from "resident DB plus
   `DB-Index-View`" to "DB source plus `DB-Index-View`".
3. Route entity and pull reads that already work over lazy
   `SQLite-DB-Snapshot` helpers through the same source boundary. Basic
   attr/entity+attr reads and simple pull-value rendering are now source-backed.
4. Add a small durable query test that opens a persisted snapshot without
   `load-db-sqlite` and answers at least:
   - entity lookup by id
   - one bound-attribute query
   - one bound entity+attribute query
   - one simple pull

Remaining in this batch:

1. Thread `DB-Read-Source` into ordinary data-clause execution, not only the
   new source-backed plain-clause query runner.
2. Broaden `q-text-db-read-source` beyond plain data clauses:
   - named or multiple source-qualified clauses
   - richer function-output ownership cleanup for temporary strings/containers
     produced by shared function evaluators
3. Extend source-backed pull beyond simple forward scalar/many attrs or
   explicitly route full pull through the same source boundary. Flat literal
   forward, wildcard, flat reverse-ref, nested forward-ref, and pattern-variable
   pull finds are now covered; remaining pull work is nested reverse attrs, pull
   options/defaults/xforms, and source-qualified named pull sources.
4. Decide the public API shape for source-backed result ownership before this
   becomes host-facing. Internally the cleanup path is explicit now, but the C
   ABI/JVM wrappers should not expose an easy-to-misuse ownership split.

Acceptance:

- resident in-memory tests still pass
- `storage_architecture_test` still passes
- a read-only persisted-snapshot query test proves at least one real parsed
  query executes without rebuilding a full resident `DB`
- benchmark output separates lazy snapshot query time from `reopen-rebuild`

## Batch 2: Entity Position Side Tables

Goal:

- remove the remaining `eavt` resident side-table dependency from query-facing
  reads.

Work:

1. Decide the representation for entity ranges over chunked `eavt`:
   - derive ranges by binary search over persisted `eavt` cursor first
   - add persisted entity range side tables only if benchmarks require it
2. Replace direct `eavt-entities` / `eavt-entity-starts` reads in query-facing
   code with source methods.
3. Keep the resident side table as an implementation detail for resident DBs,
   not as a query-engine assumption.

Acceptance:

- all entity-star/entity-attribute fast paths can run against a persisted
  snapshot source
- entity range reads do not force full index materialization
- broad query behavior is unchanged for resident DBs

## Batch 3: Normal Durable Reopen Without Rebuild

Goal:

- make storage-neutral durable `connect`/`db` return or expose a chunk-backed
  DB snapshot for reads instead of always rebuilding resident arrays.

Work:

1. Add an explicit durable read mode for SQLite-backed connections:
   - compatibility resident mode can remain for debugging/recovery
   - normal read mode should construct a `SQLite-DB-Snapshot` from latest roots
2. Keep datom-log replay available for recovery, validation, migration, and
   fallback.
3. Update C ABI/JVM/Clojure connection DB snapshots so the public API can pass
   the durable snapshot as an immutable DB value.
4. Make errors explicit when a query path still requires resident arrays.

Acceptance:

- reopening a durable DB no longer scales with total datom count for the common
  read path
- `vev/connect`, `d/db`, and C ABI DB snapshot handles preserve the same API
  shape
- MusicBrainz subset reopen/query benchmark includes both:
  - compatibility resident rebuild
  - chunk-backed normal read path

## Batch 4: Shared Immutable Index Storage For Commits

Goal:

- stop creating a full copied datom/index array set for every committed DB
  snapshot.

Work:

1. Introduce a DB index storage layer with immutable base chunks plus a small
   transaction delta.
2. Make a new DB snapshot share unchanged chunks with older snapshots.
3. Keep transaction reports, listeners, retained host DB handles, and `db-before`
   / `db-after` semantics exact.
4. Fold the existing append-only incremental path into this representation
   instead of maintaining it as a separate optimization.
5. Preserve resident-array mode as a useful small/in-memory implementation
   strategy if it remains simpler for tests and tiny databases.

Acceptance:

- snapshot-heavy write benchmark shows per-commit cost is no longer dominated
  by whole-array copy
- batch-1 and mixed read/write durable write-bench improve without reportless
  shortcuts
- old DB handles remain valid after many later commits

## Batch 5: Physical Query Operators Over Sources

Goal:

- make the query engine naturally consume resident, chunk-backed, and
  delta-overlay sources through reusable physical operators.

Work:

1. Continue replacing binding-row materialization with typed relation/operator
   paths where it matters.
2. Add source-aware operators for:
   - indexed scan
   - bind join
   - merge/star scan over entity attributes
   - anti/missing scans
   - projection and aggregate materialization
3. Keep benchmark wins tied to general operators, not special recognizers for
   named benchmark queries.

Acceptance:

- DataScript compatibility tests remain green
- MusicBrainz matrix remains green
- Datalevin-style read benchmark comparisons are periodically re-run
- query profiling can show which physical operators ran

## Batch 6: Benchmarks And Production Readiness Checks

Goal:

- prove that the new storage architecture improves real workloads and does not
  merely move costs around.

Workloads:

1. MusicBrainz open/query/reopen:
   - resident rebuild path
   - chunk-backed snapshot path
   - Datomic comparison where practical
2. Datalevin-style write-bench:
   - batch append
   - batch-1 append
   - mixed read/write
   - snapshot-heavy retained DB handles
3. DataScript/Datalevin read benchmarks:
   - run periodically as regression checks, not as the active storage gate

Acceptance:

- docs show current ratios, not just raw numbers
- regressions are tracked against the relevant architecture batch
- no benchmark requires a public API shape different from the Datomic-like API

## Later Work

These are important, but not the current gate:

- exact parser diagnostic/object parity
- generic SCC-local semi-naive recursive rule fallback
- full Maven/Clojars/PyPI/npm/crates.io publication polish
- optional server/transactor packaging mode
- replication/sync primitives
