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
- source-backed data clauses now build a `Clause-Index-Scan` over
  `DB-Read-Source` ranges instead of using a separate ad hoc candidate picker,
  including entity/int comparison at source boundaries, omitted tx/op terms, and
  reverse attribute data clauses such as `[301 :item/_parent ?child]`. EDN
  lookup-ref entity terms such as `[[:user/email "ada@example.com"] :user/name
  ?name]` now resolve against the same source before binding.
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
  function outputs over owned source bindings. `ground` clauses such as
  `[(ground 301) ?e]` now bind values before later source-backed data clauses,
  source-backed reverse data clauses can scan reverse refs through `vaet`,
  source-backed lookup-ref entity clauses resolve through source indexes, and
  source-backed `get-else`/`get-some` clauses can read current attr values
  directly from the persisted source. Source-backed `missing?` works through the
  same not-group path used for ordinary negated data clauses. Source-backed
  `or` and `or-join` clauses now execute branch groups over the same persisted
  snapshot source and merge owned branch bindings back into the incoming row.
  Flat literal pull finds such as `(pull ?e [:db/id :item/name])`, wildcard
  pulls such as `(pull ?e [*])`, flat reverse-ref pulls such as
  `(pull ?e [:item/_parent])`, nested forward-ref pulls such as
  `(pull ?e [{:item/parent [:item/name]}])`, nested reverse-ref pulls such as
  `(pull ?e [{:item/_parent [:item/child-name]}])`, pull pattern inputs such as
  `(pull ?e ?pattern)`, and missing-attr defaults such as
  `[:item/missing :default "fallback"]` now render from the same source-backed
  snapshot without rebuilding a resident DB. Pull attr limits such as
  `[:item/_parent :limit 1]` are enforced during source-backed scans. Built-in
  pull xforms `:xform vector` and `:xform name` are also supported for
  source-backed pull finds, and bounded pull recursion now walks the same
  persisted source with cycle guards. Source-backed query APIs also have
  `with-fns` variants so native pull xform callbacks can run against persisted
  snapshots. Named `$source` qualifiers can now resolve to distinct
  `DB-Read-Source` values for source-backed data clauses, pull finds, and
  source-taking function forms such as `get-else`/`get-some`, with the original
  single-source functions preserved as wrappers. Both bounded recursion and
  unbounded `...` recursion have source-backed persisted snapshot coverage.
  Source-backed text queries also accept scalar `:in` values through direct
  `Query-Input` and EDN input text.
- `storage_architecture_test` now covers these paths against a
  `SQLite-DB-Snapshot`, including parsed query text, a multi-clause join, and a
  retraction case. It also checks that primary `$`, same-source named aliases,
  and a distinct named `DB-Read-Source` work for source-backed data clauses,
  pull finds, `get-else`, and `get-some`, plus predicate filtering with both
  matching and empty results, scalar and destructuring function output,
  `ground`, `get-else`, `get-some`, reverse data clauses, lookup-ref entity
  clauses,
  `missing?`/not-group, `or`, and `or-join` clauses, flat literal pull finds,
  wildcard pull finds, flat reverse-ref pull finds, nested forward-ref and
  nested reverse-ref pull finds, pull defaults and limits, scalar inputs, and
  built-in and callback pull xforms, bounded and unbounded pull recursion,
  scalar inputs, and pull pattern inputs through both direct `Query-Input` and
  EDN input text.
- `bench/sqlite_storage.kvist` now reports
  `persisted-db-snapshot-source-query` separately from raw entity/attr helpers
  and from `reopen-rebuild`.
- `bench/sqlite_storage.kvist` now accepts `--workload <name>`, plus the
  groups `append`, `durable`, and `source`, so durable source reads can be
  measured without running the full write/reopen suite. The source connection
  benchmark labels now use storage-neutral names:
  `store-conn-source-prepared-query`, `store-read-only-open`, and
  `store-read-only-prepared-query`, plus `store-db-prepared-query` for the
  retained immutable snapshot shape. The `source` group has been verified to
  run cleanly against the storage-neutral wrappers and retained `Store-DB`.
- source-backed query result rows use owned value copies, and callers now have
  `delete-result-set-owned-values` for the matching cleanup shape.
- SQLite-backed live connections can now run text and prepared queries through
  a short-lived `SQLite-DB-Snapshot` plus `DB-Read-Source`, returning the
  ownership-tagged `Query-Result` shape after closing the storage snapshot.
  This starts the normal durable read path without forcing callers through
  `load-db-sqlite`.
- SQLite also has an internal source-only connection opener that skips
  `load-db-sqlite` entirely, keeps only the live SQLite handle plus an empty
  resident connection shell for cleanup/error reporting, and rejects
  transactions explicitly. Source-only connections can run the same
  persisted-snapshot text/prepared query wrappers.
- storage-neutral read-only store wrappers now sit on top of that mode:
  `open-store-read-only`, `q-result-store-text`, and
  `q-result-store-prepared`. Tests use these names so the next host-facing API
  work does not have to expose SQLite-specific query calls.
- `store-read-only?` and `store-resident-db-available?` make the current
  boundary explicit: source-only durable stores can query persisted chunks but
  cannot provide a resident rebuilt `DB` value.
- `Store-DB` is now an internal storage-neutral immutable DB snapshot handle.
  It can wrap a retained `SQLite-DB-Snapshot`, expose a `DB-Read-Source`, run
  text/prepared queries, and close the retained snapshot explicitly. Retaining
  a SQLite-backed `Store-DB` now reopens an independent read snapshot for the
  same durable path at the original basis tx, so the retained handle remains
  queryable after the original read-only store and original snapshot have been
  closed. SQLite index cursors are basis-pinned too: an old durable `Store-DB`
  and a retained copy keep reading the original index root even after the
  writer advances and creates later roots. This is the internal shape the C
  ABI/JVM/Clojure durable `d/db` handle maps to.
- source-backed `Store-DB` direct pulls now render owned nested ref maps through
  the same `DB-Read-Source` boundary as source-backed pull finds. The shared
  storage-neutral API test covers retaining a `Store-DB`, querying it, and
  pulling `[:item/name {:item/friend [:item/name]}]` without using a resident
  DB clone. The storage architecture test also covers the retained SQLite
  read-only `Store-DB` variant with `[:user/name {:user/friend [:user/name]}]`.
- source-backed function clauses copy produced values into owned result
  bindings and then shallow-clean temporary function result containers, avoiding
  leaked vector/map wrappers without deleting scalar values that may be borrowed
  from existing bindings.
- source-backed data-clause matching now treats scanned datom values as
  borrowed candidates and copies only when a value is stored in an output
  binding. This fixes cleanup for larger source-backed scans and matches the
  ownership model of persisted index cursors.
- prepared query objects can now execute directly against `DB-Read-Source`
  through `q-prepared-db-read-source...` wrappers. This keeps the durable
  snapshot path aligned with the prepared-query API that host bindings will use,
  instead of making persisted snapshots depend on text parsing at call time.
- an ownership-tagged `Query-Result` wrapper now exists for source-backed
  prepared and text queries. The new wrappers return `Query-Result` and clean up
  with `delete-query-result`, which is the direction host handles should follow
  so callers do not have to know whether row values are shallow or owned copies.
  Resident text and prepared query wrappers now use the same `Query-Result`
  shape, tagged as shallow cleanup.
- source-backed lookup-ref resolution now consults current persisted schema
  datoms and requires the lookup attr to have `:db/unique` set to either
  `:db.unique/identity` or `:db.unique/value`, matching the resident resolver's
  basic guard. The storage architecture test covers both a successful unique
  lookup ref and a rejected non-unique lookup ref.
- source-backed lookup refs now also resolve tuple lookup-ref values before the
  AVET lookup, including ref-component resolution through entity ids, ints,
  idents, and nested lookup-ref vector values. The persisted snapshot test
  covers unique tuple attr lookup refs with both scalar components and a
  ref-typed component resolved through a nested lookup ref.
- same-transaction tuple maintenance now removes stale pending derived tuple
  adds for the same entity and tuple attr even when the pending ops have an
  empty map group. The persisted source test keeps schema, entity data, tuple
  components, and lookup-ref coverage in one transaction.
- `DB-Read-Source` now exposes source-neutral `eavt` entity ranges,
  entity+attr positions, and cardinality-one value reads. These helpers use the
  same source index boundary for resident and persisted SQLite snapshot sources,
  so query-facing code no longer needs to reach for resident `eavt` side tables
  just to answer entity-local reads. Source-level typed integer reads and
  equality checks now sit on the same boundary. Typed entity/int and
  entity/string/int projection fallbacks now use those helpers instead of
  resident entity-position side tables, with owned string copies tracked in the
  typed string/int column container when needed.
- The legacy resident `eavt-entity-range` helper now uses `DB-Index-View`
  binary search over `eavt` instead of reading `eavt-entities` /
  `eavt-entity-starts` directly. Transaction append-eligibility checks still get
  the same return shape, but the side table is no longer required for ordinary
  entity range existence checks.
- Dead resident-only position helpers that exposed `eavt-entity-starts`
  positions into query-facing code have been removed:
  `eavt-entity-position-at-or-after`, `eavt-entity-range-at-position`,
  `eavt-entity-attr-position-at-entity-position`, and the cardinality-one
  `...at-entity-position...` wrappers. The remaining cardinality-one fast
  helpers start from entity/attr terms and use the `DB-Index-View` range path.
- Source-qualified text queries now have profiled wrappers
  (`q-text-profiled-with-sources...`), so named-source query plans can be
  measured directly instead of requiring prepared-query plumbing in tests.

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

1. Thread `DB-Read-Source` into ordinary resident query execution beyond the
   source-backed runner. The low-level source-backed clause scan now uses the
   same `Clause-Index-Scan` shape and source-backed prepared queries are
   available, but the full resident query engine still has `DB`/`DB-Source`
   entry points and shallow binding/result ownership.
2. Carry the ownership-tagged `Query-Result` shape into C ABI/JVM result
   handles. Internal resident and source-backed wrappers now use this shape. The
   ABI wrapper has been moved in that direction: `vev_db_t` storage now wraps a
   `Store-DB`, result handles track whether row values are owned or shallow, and
   the main prepared-query DB-handle path can route through
   `q-result-store-db-prepared-with-input-text`. The old ABI compile blocker is
   gone: the failure was malformed ABI `case` defaults, not the raw listener
   callback helper. `scripts/build_c_abi.sh` now builds `libvev` and passes the
   C, Python, Rust, Go, Node, Java, and Clojure smoke coverage, including
   direct nested pull through `vev_pull_edn`, storage-neutral DB-snapshot
   prepared query rendering, and host-wrapper nested pull traversal.
3. Continue moving host-visible DB-value transaction operations off resident
   guards. Query and pull DB-value paths are storage-neutral now. Immutable
   `with`/`db-with` over host DB handles now accepts retained `Store-DB`
   snapshots by materializing the source snapshot into a resident compatibility
   DB at the transaction boundary, applying the existing transaction engine,
   and returning a resident derived DB/report. That makes durable/shared host
   DB values usable today without mutating the source snapshot. The remaining
   architecture work is replacing this materializing bridge with a source-native
   write-state overlay.

Acceptance:

- resident in-memory tests still pass
- `storage_architecture_test` still passes
- a read-only persisted-snapshot query test proves at least one real parsed
  query executes without rebuilding a full resident `DB`
- benchmark output separates lazy snapshot query time from `reopen-rebuild`

## Batch 2: Entity Position Side Tables

Status: started.

Goal:

- remove the remaining `eavt` resident side-table dependency from query-facing
  reads.

Work:

1. Decide the representation for entity ranges over chunked `eavt`:
   - derive ranges by binary search over persisted `eavt` cursor first. This is
     now implemented at the `DB-Read-Source` helper boundary.
   - add persisted entity range side tables only if benchmarks require it
2. Replace direct `eavt-entities` / `eavt-entity-starts` reads in query-facing
   code with source methods. The source methods exist, source-level integer and
   equality helpers are covered for resident and persisted snapshots, and the
   entity/int plus string/int projection fallbacks now use them. Runtime
   string/int typed columns can now mark owned string copies, and the ABI wrapper
   reads that ownership bit for cleanup. The legacy resident entity-range helper
   now also uses the same `DB-Index-View` binary-search shape instead of the
   side table, and the dead position-indexed helpers have been removed.
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
   - normal read mode should construct a `SQLite-DB-Snapshot` from latest roots.
     The first internal text/prepared query wrappers now do this and return
     owned `Query-Result` values.
   - an internal source-only SQLite connection opener now skips resident rebuild
     on open and rejects writes explicitly
   - storage-neutral read-only store wrappers now expose that path internally
     without requiring callers to mention SQLite-specific query functions
   - `Store-DB` now retains a chunk-backed read snapshot and can be passed to
     query code as an immutable DB value inside Vev
2. Keep datom-log replay available for recovery, validation, migration, and
   fallback.
3. Update C ABI/JVM/Clojure connection DB snapshots so the public API can pass
   the durable snapshot as an immutable DB value.
4. Make errors explicit when a query path still requires resident arrays. The
   first explicit guard is `store-resident-db-available?`; host-facing snapshot
   handles still need to turn this into a public error/alternate source-backed
   DB handle.

Acceptance:

- reopening a durable DB no longer scales with total datom count for the common
  read path
- `vev/connect`, `d/db`, and C ABI DB snapshot handles preserve the same API
  shape
- MusicBrainz subset reopen/query benchmark includes both:
  - compatibility resident rebuild
  - chunk-backed normal read path

## Batch 4: Shared Immutable Index Storage For Commits

Status: started.

Goal:

- stop creating a full copied datom/index array set for every committed DB
  snapshot.

Implemented so far:

- `bench/write_bench.kvist` now has a `snapshot-heavy` workload. It performs
  normal SQLite-backed transactions, calls the ordinary `db` snapshot API after
  each commit, and retains every old DB value until the end of the run. This
  gives Batch 4 a concrete acceptance harness for Datomic-style "DB as value"
  usage with many old snapshots alive.
- A local batch-1 baseline with 500 retained DB snapshots shows commit latency
  growing from about 0.33 ms near 100 writes to about 2.07 ms near 500 writes,
  while the explicit post-commit `db` clone is still only about 0.003-0.008 ms
  per retained snapshot at that scale. The immediate problem is therefore the
  commit/publish path copying and rebuilding resident arrays, not just host DB
  handle retention.
- `Shared-Int-Index` is the first in-memory representation building block for
  shared immutable indexes. It stores int index entries in explicitly retained
  chunks, supports old snapshot retention plus append-by-new-chunks, and has
  tests proving older and newer handles can be released independently while
  surviving snapshots still read correctly. It is not wired into `DB` yet.
- `Shared-DB-Int-Indexes` now groups the resident int index arrays, and
  `DB-Index-View` can read from shared chunked indexes in addition to resident
  slices and SQLite cursors. Tests compare shared-backed `eavt`, `aevt`, `avet`,
  and `vaet` views against the current resident arrays and verify retained
  grouped index handles survive after the original handle is released.
- `DB-Read-Source` now has a shared-resident-index variant. It scans shared
  chunked index views while still materializing datoms/currentness from the
  resident datom log. A source-backed EDN query test proves the existing
  persisted/source query runner can execute over this representation.
- Shared-resident source currentness now reads the shared chunked `current`
  index with binary search instead of using the resident `DB.current` array.
  The query test includes an add/retract pair and verifies the retracted fact
  is filtered through the shared current index path.
- `Shared-Datom-Log` now stores datoms in retained immutable chunks, and
  `DB-Read-Source` has a shared-datoms-plus-shared-indexes variant. A
  source-backed query test now runs without a resident `DB` pointer for datom
  materialization, while still filtering retractions through the shared
  `current` index.
- `Shared-DB-Snapshot` now packages the shared datom log and shared indexes as
  one retained immutable DB-value handle. The query test retains a snapshot,
  releases the original, and queries through the retained handle, matching the
  lifetime shape needed for old DB values in reports and host handles.
- `shared-db-snapshot-with-tx-report` can now build a new shared snapshot from
  an old snapshot plus a transaction report. It shares the old datom-log chunks
  and appends only the report tx-data chunks. Shared int indexes retain old
  chunks for known append/prefix cases, with fallback rebuild for
  merged/reordered indexes. Tests verify the base datom, current, and EAVT
  chunks are retained and the appended snapshot remains queryable.
- `Shared-Conn` is the first connection-side publish wrapper for this
  representation. It still delegates transaction application to the existing
  resident `Conn`, but successful commits publish a `Shared-DB-Snapshot` from
  the old snapshot plus the post-transaction DB. Tests verify the published
  source is queryable and retains old datom/current/EAVT chunks across a simple
  append commit.
- `bench/write_bench.kvist` now has a `shared-snapshot-heavy` workload for
  the new publish path. A local batch-1, 100-write sample with chunk size 64
  reported about 0.049-0.145 ms shared commit latency versus about
  0.206-0.481 ms for the SQLite-backed resident `snapshot-heavy` path. This is
  a useful improvement, but the upward slope remains because `Shared-Conn`
  still adapts from resident post-commit arrays instead of building shared
  chunks directly at commit time.
- Shared publish now carries append-only/new-entity facts from the transaction
  result into snapshot publication. Append-only commits retain the `current`
  index directly, and ordered new-entity commits also retain `eavt` directly
  without scanning old prefixes. `eavt-entity-starts` is now a resident DB
  build/index maintenance side table, not part of shared immutable snapshots. A local
  batch-1, 500-write `shared-snapshot-heavy` sample with chunk size 64 ended at
  about 0.323 ms commit latency, versus the earlier resident snapshot-heavy
  baseline growing past 2 ms near 500 writes. The remaining slope is mainly the
  indexes that still need merge/range chunk sharing (`aevt`, `avet`, `vaet` and
  non-new-entity `eavt` cases).
- `Shared-Int-Index` now has an explicit page-sharing constructor that can
  retain unchanged same-offset chunks while rebuilding changed pages. Tests
  cover old handle release plus retained unchanged pages. This is deliberately
  not wired into every publish fallback yet: a blind "compare every old page"
  experiment made the append-heavy benchmark slower than exact-prefix-or-rebuild.
  The next production version needs merge-aware sharing or a cost model that can
  predict when page comparison will actually save enough copying.
- Append-only shared publication now has a merge-aware index builder for
  `eavt`, `aevt`, `avet`, and `vaet`. It merges sorted new datom indexes with
  old shared chunks and retains any old chunk that the merge copies
  contiguously. Tests cover an interleaved `aevt` merge retaining old chunks.
  This reduces retained-snapshot copying pressure, but it is not yet a latency
  win in the small append-heavy sample: local `shared-snapshot-heavy` at batch 1,
  500 writes, chunk size 1024 ended around 0.446 ms commit latency after adding
  a batched added-tail append, and 1000 writes ended around 0.804 ms before that
  tail batching. The remaining work is to avoid still building resident indexes
  first and to make the merge builder emit chunk-sized runs with less per-value
  overhead.
- The shared merge builder now emits full chunk-sized appended slices directly
  when the pending buffer is empty, instead of pushing those values one at a
  time through the pending buffer. This does not remove the resident-index-first
  adaptation yet, but it reduces one source of per-value overhead in the
  append-only shared index path.
- Append-only shared publication now builds the `current` index tail directly
  from the committed datom range instead of slicing or prefix-checking the
  resident post-commit `current` array. This is a small direct-publication step:
  the shared path still delegates transaction application to the resident
  engine, but one more shared index no longer depends on resident index
  materialization.
- Ordered new-entity shared publication now also builds the appended `eavt`
  tail from the new datoms sorted in EAVT order, rather than slicing the
  resident post-commit `eavt` array. General append-only interleaved `eavt`
  still uses the merge-aware shared builder.
- Append-only `Shared-Conn` publication now appends the shared datom log from
  `report.tx-data` instead of slicing the resident post-commit datom array.
  The shared index merge builders still use the resident post-commit datom log
  as their O(1) comparison source until shared datom logs have a better random
  access shape; a fully direct old-shared-vs-new merge was correct but slower
  with the current chunk representation.
- `Shared-Datom-Log` now stores per-chunk start offsets, so datom lookup can
  find the containing chunk with binary search instead of scanning chunk by
  chunk. This is the structural prerequisite for making direct shared-index
  merges compare old shared datoms with new transaction datoms without
  regressing publish latency.
- The direct old-shared-vs-new merge was retried on top of chunk-start lookup,
  including a fast estimated-chunk path. It remained slower on the current
  `shared-snapshot-heavy` sample, so the hot publication path deliberately
  stays on resident post-commit datom comparisons for now. The retained
  primitive is indexed shared datom-log lookup; the next direct-publication
  step should avoid building resident indexes first, rather than adding another
  indirect comparison layer to the current adapter.
- `Shared-Tx-Report` now gives the shared connection path retained
  `db-before` and `db-after` shared snapshots plus shallow report metadata. A
  test transacts through `Shared-Conn`, moves the connection forward with a
  second transaction, and verifies the first report's shared `db-before` and
  `db-after` remain independently queryable. This is the host-facing DB-value
  lifetime shape needed before the C/JVM handles can stop depending on resident
  DB clones.
- Transaction reports now carry the transaction engine's datom append start
  index plus append-only and ordered-new-entity publication facts. `Shared-Conn`
  consumes those fields directly instead of recomputing append position and
  eligibility from the resident DB and `tx-data` after the resident transaction
  has already been applied. This is a small but important step toward making
  the transaction engine emit a publish plan that shared storage can consume
  directly.
- Ordered new-entity shared publication now builds the appended EAVT tail from
  the transaction's appended datom slice plus the report start index, not from
  the resident post-commit datom log. General interleaved append-only index
  merges still compare through the resident post-commit datom log.
- `Shared-Conn` now publishes through a `shared-db-snapshot-with-tx-report`
  boundary. That helper still receives the resident post-commit `DB` for the
  remaining adapter paths, but it centralizes the transaction-report-to-shared
  snapshot conversion that future direct shared publication should replace
  internally.
- `Shared-Tx-Report` now preserves the same append start and append-mode
  metadata as ordinary `Tx-Report`, so retained shared reports do not lose the
  publish facts that produced their `db-after` snapshot.
- Shared index publication now has a tested direct merge primitive that compares
  old datoms from `Shared-Datom-Log` with new datoms from the transaction slice,
  without using the resident post-commit datom array as the comparison source.
  This is intentionally not the default `Shared-Conn` path yet: a local
  `shared-snapshot-heavy` batch-1 sample regressed to about 0.31 ms commit
  latency at 200 writes when routed through direct shared-log comparison,
  compared with the faster resident-comparison adapter. The useful next step is
  not another lookup tweak; it is for the transaction engine to emit ordered
  per-index publish tails or merge inputs directly, so shared publication can
  avoid both resident index materialization and repeated shared-log lookups.
- The direct shared-log publication path is now exposed as an explicit
  benchmark-only connection entry point and `shared-snapshot-heavy-direct`
  workload. A local batch-1, 300-write comparison with chunk size 1024 ended
  around 0.248 ms commit latency for the default shared adapter, 0.458 ms for
  direct shared-log comparison, and 0.246 ms for storage-neutral `Store-DB`.
  This keeps the direct path measurable while confirming it should not become
  the hot path until the transaction engine emits better per-index publish
  inputs.
- Append-only transaction reports now carry a `Tx-Publish-Plan` with the
  ordered appended EAVT/AEVT/AVET/VAET tails consumed by shared index
  publication. Shared storage no longer derives that plan after the fact from
  the report; it consumes the transaction-owned publish metadata directly. A
  local batch-1, 300-write sample ended near 0.268 ms commit latency for the
  default shared path and 0.267 ms for storage-neutral `Store-DB`. That is a
  small cost versus the previous storage-derived sidecar, but it moves the
  architecture in the right direction: the next optimization is to avoid
  duplicate sorting/materialization while building resident indexes and the
  publish plan.
- Resident append-only DB construction now consumes the same `Tx-Publish-Plan`
  ordered tails used by shared publication. This removes the duplicate
  per-index appended-tail sorting that appeared when the publish plan moved
  onto transaction reports. A local batch-1, 300-write sample improved to about
  0.155 ms commit latency for the default shared path and 0.154 ms for
  storage-neutral `Store-DB`; direct shared-log comparison remains slower at
  about 0.259 ms.
- Append-only shared index merges now compare old index entries through
  `db-before.datoms` plus new entries through `report.tx-data`, rather than
  comparing through the resident post-commit `after.datoms` array. The merge is
  still the same generic chunk-retaining shared-index algorithm, but it no
  longer depends on the post-commit resident datom log as its comparison
  source. A local batch-1, 300-write sample ended near 0.151 ms commit latency
  for the default shared path and 0.145 ms for storage-neutral `Store-DB`;
  direct shared-log comparison remains slower at about 0.261 ms.
- Append-only shared snapshot publication now has a report-only helper:
  `shared-db-snapshot-with-append-tx-report`. The outer compatibility helper
  still accepts a post-commit resident `DB` for non-append fallback publishing,
  but successful append-only commits no longer pass that `DB` into the shared
  publication helper. `Shared-Conn` now branches at the call site too, so
  append-only reports go directly to the report-only helper and do not pass the
  post-commit resident `DB` into shared publication at all. A local batch-1,
  300-write sample stayed in the same range: about 0.146 ms commit latency for
  the default shared path and 0.145 ms for storage-neutral `Store-DB`; direct
  shared-log comparison remains slower at about 0.267 ms.
- Transaction application now has an explicit `Tx-Apply-Result` boundary.
  `apply-resolved-ops-to-datoms` packages the emitted post-transaction datom
  array, `tx-data`, tx id, append start index, append-only facts, and
  `Tx-Publish-Plan` before the resident `db-after` indexes are built.
  Append-only `Shared-Conn` transactions now resolve and apply tx data
  directly, publish the next shared snapshot from `Tx-Apply-Result`, and only
  then build the resident `db-after` needed for report/listener compatibility.
  Non-append shared transactions still use the resident fallback snapshot
  adapter. A fresh local batch-1, 300-write sample ended near 0.144 ms commit
  latency for the default shared path, 0.140 ms for storage-neutral `Store-DB`,
  and 0.261 ms for direct shared-log comparison.
- `shared-conn-transact-report`, the retained shared report path used by
  host-facing DB-value APIs, now follows the same direct apply-result publish
  path instead of wrapping `shared-conn-transact` and then converting its
  resident `Tx-Report` into a `Shared-Tx-Report`. It still builds the resident
  connection DB after publishing because the current transaction resolver needs
  a resident current DB for later tempid, unique, schema, lookup-ref, and
  transaction-function semantics. The remaining architecture work is therefore
  a source-backed write resolver/current-state layer, not just another report
  wrapper. A local rerun put both `shared-snapshot-heavy` and
  `shared-store-db-heavy` near 0.146 ms commit latency, with the benchmark-only
  direct shared-log path around 0.268 ms.
- `Shared-Conn` now names that remaining dependency explicitly as
  `Shared-Write-State`. Today it wraps the resident `Conn`, and shared writes
  advance it through `shared-write-state-*` helpers instead of reaching through
  a generic `.conn.db` field. This is intentionally a boundary refactor rather
  than a performance shortcut: the next implementation can replace
  `Shared-Write-State` internals with source-backed current/schema/unique
  state while leaving shared snapshot publication and host DB handles alone. A
  local batch-1, 300-write sample stayed in the same range: about 0.150 ms
  commit latency for both `shared-snapshot-heavy` and `shared-store-db-heavy`,
  with the benchmark-only direct shared-log path around 0.274 ms.
- `Shared-Write-State` now tracks `source-resolves` and
  `resident-fallbacks`, making the remaining resolver boundary observable in
  tests and future benchmarks. A focused test proves ordinary source-backed
  tx-data increments the source counter and that explicit tuple-schema writes
  no longer record the resident fallback path. This is measurement
  infrastructure for the remaining Batch 4 work, not a semantic shortcut.
- The first write resolver read has moved behind that boundary. Simple direct
  shared tx-data now checks direct eligibility using cached `Shared-Write-State`
  schema facts (`has-tuple-schema` and ref attrs), and only consults
  `DB-Read-Source` when a ref value actually needs source-backed resolution.
  Complex tx shapes still fall back to the existing resident resolver. This is
  deliberately not a full source-backed transaction resolver yet, but it puts
  the hot direct add/retract path on the new write-state API without forcing
  per-commit source schema scans. A local rerun ended near 0.151 ms commit
  latency for `shared-snapshot-heavy`, 0.146 ms for `shared-store-db-heavy`,
  and 0.259 ms for the benchmark-only direct shared-log path.
- The shared write resolver now also handles source-stable lookup-ref writes
  without entering the resident transaction resolver. This covers simple
  add/retract tx-data where the entity position is a unique lookup ref, or the
  value position is a ref-valued lookup ref, as long as the transaction does
  not require later target facts or tempid/upsert resolution from the same
  intermediate tx DB. Order-safe lookup attrs introduced earlier in the same tx
  are now covered by the write-state path too. The focused shared connection
  tests verify that retained source-backed snapshots can resolve lookup-ref
  entity and lookup-ref value writes through `DB-Read-Source` and through
  already-emitted tx-local ops.
- Source-stable ident entity writes now use the same shared write-state/source
  boundary. Simple add/retract tx-data with `:db/ident` entity terms can
  resolve the entity through source-backed schema reads, while idents created
  in the same transaction still fall back to the resident intermediate-tx-db
  resolver. The focused shared connection tests cover append-only ident writes
  and missing-ident retract no-ops.
- Simple `:db/current-tx` value writes now resolve from `Shared-Write-State`
  too. The shared path can record current transaction refs without entering the
  resident resolver, and report rendering still preserves the
  `:db/current-tx` tempid mapping.
- Ordinary non-upsert tempid entity writes now resolve from
  `Shared-Write-State` as well. The shared resolver caches the next entity id,
  assigns repeated tempid names consistently within the transaction, accounts
  for explicit entity ids in the same tx, and deliberately falls back to the
  resident resolver when a tempid add touches a unique attr so Datomic-style
  upsert semantics stay exact.
- Simple value-tempid ref writes now share that same assignment state. The
  shared resolver accepts value-tempids only for ref attrs, requires the value
  tempid to also appear as an entity tempid in the same transaction unless it
  is `:db/current-tx`, and resolves current-tx value-tempids through the same
  report tempid mapping as ordinary current-tx values. This covers the common
  `["a" :friend "b"]` plus facts-about-`"b"` shape without weakening exact
  edge-case behavior.
- Tempid upserts for `:db.unique/identity` attrs now resolve through
  `Shared-Write-State` as well. The shared resolver caches identity attrs
  separately from generic unique attrs, preassigns tempids for the whole simple
  transaction before emitting ops, and uses source-backed lookup-ref reads to
  choose an existing entity when one exists. Generic `:db.unique/value` attrs
  are treated as normal non-upsert tempid assignments and are left to normal
  validation, including conflict rejection, without entering the resident
  resolver.
- `:db/retractAttr` and `:db/retractEntity` now expand through
  `Shared-Write-State` using `DB-Read-Source` current-state scans. The shared
  path reads current entity facts from EAVT, recursively retracts component
  children, removes incoming VAET refs for retracted entities, and keeps the
  generated owned retract op values/attrs scoped to the resolved transaction.
- Literal ref tempids in source-backed shared transactions now follow the same
  conventions as the resident resolver. A ref-valued attribute can point at a
  same-transaction tempid using a string value such as `"b"` or a negative int
  such as `-1`, and the shared resolver maps that value through the transaction
  tempid table without rebuilding a resident intermediate DB.
- Explicit-ID schema-only transactions now resolve through the same
  source-backed shared write-state path. This covers common schema installation
  commits for `:db/ident`, `:db/valueType`, `:db/cardinality`, `:db/unique`,
  `:db/index`, and `:db/isComponent`.
- Explicit tuple-schema installation now also resolves through
  `Shared-Write-State`, including the same final tuple-schema validation as the
  resident path.
- Simple writes against existing tuple schemas now also stay on the
  source-backed shared write-state path. The resolver discovers tuple schemas
  through `DB-Read-Source`, computes tuple values from the source plus
  already-resolved tx ops, emits derived tuple maintenance ops, and handles
  component replacement without recording a resident fallback. Same-transaction
  tuple schema define-and-use is also source-backed for explicit tuple schema
  facts plus ordinary component writes.
- Mixed schema/data transactions now have a safe source-backed subset:
  explicit schema facts and ordinary data facts can share a tx when the data
  facts do not depend on attrs/idents introduced by that same transaction.
  Schema-dependent mixed transactions still fall back to the resident resolver,
  preserving intermediate-tx-db semantics until `Shared-Write-State` has a real
  schema overlay.
- Same-transaction scalar schema define-and-use now has a source-backed subset:
  a tx can install a simple non-ref, non-tuple, non-unique attr and write data
  for that attr in the same transaction. Same-transaction unique schema
  define-and-use is source-backed for explicit entity writes when the unique
  schema fact appears before the data facts, preserving the existing
  order-sensitive validation semantics. Other cases where resolution depends on
  transaction-local schema overlay state still use the resident fallback path.
- Same-transaction ref schema define-and-use now also has a source-backed
  subset when the data side uses already-resolved entity values or
  same-transaction literal/value tempids, plus value lookup refs whose lookup
  attr is source-stable or order-safe lookup attrs introduced earlier in the
  same transaction. Unique attrs introduced in the same transaction still need
  a fuller transaction-local schema overlay for tempid/upsert-heavy shapes.
- Same-transaction tuple schema define-and-use now has a source-backed subset
  for explicit tuple schema facts and ordinary component facts in the same tx.
  The resolver scans tuple schemas declared by the tx while maintaining derived
  tuple values, so the common "install tuple attr and immediately write its
  components" shape no longer falls back to resident resolution.
- Built-in compare-and-swap transaction data (`:db.fn/cas` / `:db/cas`) now
  resolves through `Shared-Write-State` for source-stable entity/value shapes,
  including lookup-ref expected values. Failed CAS reports the same
  DataScript-style current-value error string without entering the resident
  resolver. Registered transaction functions now have two shared-connection
  APIs: the existing DB-callback shape publishes through the shared snapshot
  path, and the new source-callback shape passes `DB-Read-Source` to the
  callback so it can read facts written earlier in the same transaction without
  receiving a resident `DB`. The source-callback API is available for typed
  tx-data plus EDN text and prepared tx-data. The source-callback
  implementation now uses a transaction-local `DB-Read-Source` overlay at each
  callback boundary, so callbacks can read earlier resolved ops without
  building a temporary shared snapshot. The overlay currently covers the
  current-fact reads needed by source callback resolution; the remaining
  storage work is extending this model to the broader host-visible immutable
  `with`/`db-with` bridge.
- `Store-DB`, the storage-neutral immutable DB handle, now has a
  `Shared-Snapshot` variant in addition to the existing `SQLite-Snapshot`
  variant. `shared-store-db` retains a `Shared-Conn` snapshot, and the existing
  `q-result-store-db-*` query wrappers run against it through `DB-Read-Source`.
  A test verifies the retained store DB snapshot remains queryable after the
  shared connection advances. The SQLite variant now has the same close-original
  regression shape for durable read-only stores: retain the `Store-DB`, close
  the original snapshot/store, and query the retained handle.
- `Store-Conn` is now a real tagged storage-neutral connection wrapper instead
  of an alias for `SQLite-Conn`. `open-store` and `open-store-read-only` still
  produce SQLite-backed stores, while `create-shared-store` produces the
  shared in-memory publish path. The same `store-db`,
  `q-result-store-prepared`, `q-result-store-text`, `transact-store-text`, and
  `close-store` functions now work over both variants. A focused test verifies
  that a retained shared `Store-DB` remains immutable after the store advances.
- `Store-DB` can now render source-backed query results to serializable
  `Value` maps without a resident `DB`, including pull result rendering through
  `DB-Read-Source` schema reads. The CLI `query` and `pull` commands now open a
  read-only store, retain a `Store-DB`, and render through that storage-neutral
  snapshot instead of reaching through `store.sqlite.conn.db`.
- `bench/write_bench.kvist` now has a `shared-store-db-heavy` workload. It uses
  the storage-neutral `Store-Conn`/`Store-DB` APIs over the shared publish path,
  commits typed tx-data without EDN text roundtrips, retains every `Store-DB`
  snapshot until the end of the run, and closes those retained handles
  explicitly. A local batch-1, 200-write sample with chunk size 1024 matched
  raw `shared-snapshot-heavy` almost exactly: both ended near 0.172 ms commit
  latency and 0.012 ms snapshot-retain latency at 200 writes. The host-facing
  `Store-DB` wrapper is therefore not the current bottleneck; the remaining
  slope is still the shared publication adapter/resident-index boundary.

Work:

1. Introduce a DB index storage layer with immutable base chunks plus a small
   transaction delta. The first retained chunk primitive, grouped DB int index
   wrapper, retained DB snapshot, and connection-side shared publish wrapper
   exist. Transaction reports now carry an explicit append publish plan with
   ordered per-index tails, resident append-only DB construction consumes the
   same tails, shared publication compares merge candidates through `db-before`
   plus `tx-data` instead of post-commit resident datoms, and append-only shared
   snapshot publication no longer receives the full post-commit resident `DB`.
   The transaction engine now emits a `Tx-Apply-Result` before resident
   `db-after` construction, and append-only shared connection publication
   consumes that apply result directly instead of going through resident `Conn`
   transaction application first. The retained shared report path does the same.
   The remaining resident `db-after` build is now isolated behind
   `Shared-Write-State`: it is a write-state dependency for future transaction
   resolution, not primarily a host report dependency. Simple direct tx-data now
   resolves through this write-state boundary with source-backed ref value
   resolution where needed, and source-stable lookup-ref writes now resolve
   through the same source boundary. Source-stable ident entity writes are also
   on that boundary now, as are simple `:db/current-tx` value writes,
   ordinary non-upsert tempid entity writes, simple value-tempid ref writes,
   current-tx value-tempids, literal string/negative-int ref tempids, identity
   tempid upserts, unique/value tempid validation, source-backed
   `retractAttr`/`retractEntity` expansion, and
   explicit-ID schema-only transactions, including tuple-schema installation,
   plus simple tuple maintenance over already-installed tuple schemas and
   schema-independent mixed schema/data transactions. Same-transaction scalar
   schema define-and-use is source-backed for simple scalar attrs and for ref
   attrs when values are already entity ids or same-transaction literal/value
   tempids, plus source-stable and order-safe same-transaction value lookup
   refs. Same-transaction lookup attrs can also resolve entity lookup refs when
   the unique schema fact is already emitted earlier in the transaction and the
   target fact is an explicit entity add, tempid entity add, or source-backed
   identity tempid upsert anywhere in the same tx. This covers both lookup-ref
   entity positions and ref-valued lookup-ref positions without building a
   resident intermediate DB.
   Same-transaction tuple schema define-and-use is
   source-backed for explicit tuple schema facts plus ordinary component
   writes. Same-transaction unique schema define-and-use is source-backed for
   order-safe explicit entity writes. Built-in
   compare-and-swap transaction data is also source-backed for source-stable
   entity/value shapes. Tempid upserts through already-installed unique tuple
   attrs now resolve through the same source-backed write-state path for simple
   component facts, source-backed lookup-ref component facts, and value-tempid
   component facts whose target tempids resolve through source-backed identity
   upserts.
   Same-transaction tuple lookup-ref targets are also source-backed, including
   tuple component facts written through value
   lookup-refs and tuple lookup-ref values that contain nested lookup-ref
   components, so ref-valued lookup refs can point at a tuple identity created
   by the same transaction without a resident intermediate DB. Scalar
   identity-upsert conflict detection and conflicting unique tuple tempid
   upsert detection also run through the write-state path for source-backed
   existing entities.
   `Shared-Write-State` now counts source-backed resolver use versus resident
   fallback use, so this remaining work can be tracked directly. Registered
   source transaction-function callback reads now use a transaction-local
   `DB-Read-Source` overlay instead of temporary source materialization. The
   next step is to move the remaining intermediate-tx-db resolver state behind
   the same boundary: same-tx unique tempid/upsert shapes beyond source-backed
   identity target resolution and host-visible immutable `with`/`db-with`
   should use a write-state/source overlay instead of a full resident DB rebuild
   on every append-only commit.
2. Make a new DB snapshot share unchanged chunks with older snapshots.
   Datom-log sharing works for appended snapshots. Index chunk sharing now
   works for exact-prefix append cases, and append-only/new-entity publication
   can skip prefix proof for the known-prefix indexes. Same-offset page sharing
   exists as a tested primitive, but is not yet used blindly on the hot publish
   fallback because it regressed append-heavy commits. Merge-aware append-only
   index publication now retains full old chunks copied contiguously by the
   merge. Append-only shared publication now consumes `report.tx-data` directly
   for the shared datom log and uses `report.db-before.datoms` plus
   `report.tx-data` for merged-index comparisons. Shared datom logs now keep
   chunk-start offsets for indexed access, and the direct shared-vs-new merge
   retry proved that lookup primitive is not enough by itself. Append-only
   shared publication is now independent of the post-commit resident `DB`
   parameter, leaving resident-array mode as a compatibility and small-database
   strategy rather than a shared publication requirement.
3. Keep transaction reports, listeners, retained host DB handles, and `db-before`
   / `db-after` semantics exact. Shared transaction reports now exist for the
   prototype shared connection path, and `Store-DB` can now wrap shared
   snapshots. `Store-Conn` now has SQLite and shared variants behind the same
   storage-neutral API, and source-backed result rendering lets CLI reads avoid
   resident DB rendering. The ABI `vev_db_t` wrapper has been changed to store a
   `Store-DB` internally. The ABI build is unblocked and the full host smoke
   script is green, including nested direct pull through DB handles. Shared and
   durable SQLite `Store-DB` nested-pull regressions now cover source-backed DB
   values; durable retained DB handles can also survive closing their original
   read-only store when the reopened basis matches, and old durable DB handles
   stay pinned to their original SQLite index root after a writer advances.
   Immutable host `with` and `db-with` now route through the storage-neutral
   `Store-DB` handle too, using a source-materializing compatibility bridge so
   non-resident DB values are no longer rejected. The next step is to replace
   that bridge with source-native write-state application.
4. Fold the existing append-only incremental path into this representation
   instead of maintaining it as a separate optimization. The first fold is in
   place: datom append position plus append-only/new-entity decisions are now
   report metadata owned by the transaction engine, not storage-adapter
   recomputation. `Shared-Conn` now consumes that metadata through a single
   report-shaped shared snapshot publish helper.
5. Preserve resident-array mode as a useful small/in-memory implementation
   strategy if it remains simpler for tests and tiny databases.
6. Re-run `snapshot-heavy`, `shared-snapshot-heavy`,
   `shared-snapshot-heavy-direct`, `shared-store-db-heavy`, `pure --batch 1`,
   and `mixed` write-bench after each representation step so the architecture
   work is measured against the actual immutable DB-value workload, the direct
   shared-log experiment, and the storage-neutral host handle boundary.

Acceptance:

- snapshot-heavy write benchmark shows per-commit cost is no longer dominated
  by whole-array copy
- batch-1 and mixed read/write durable write-bench improve without reportless
  shortcuts
- old DB handles remain valid after many later commits

## Batch 5: Physical Query Operators Over Sources

Status: started.

Goal:

- make the query engine naturally consume resident, chunk-backed, and
  delta-overlay sources through reusable physical operators.

Implemented so far:

- The typed bound entity+attribute operator now has a source-aware wrapper.
  Named `$source` clauses such as `[$left ?e :name ?name]` can stay on the
  same specialized EAVT entity+attr operator after an earlier clause has bound
  `?e`, instead of being rejected by the operator solely because the clause was
  source-qualified. A focused source test covers this through the new profiled
  text-with-sources API.
- `Query-Stats` now exposes the first physical operator counters:
  `:typed-index-scans`, `:source-index-scans`, and
  `:binding-materializations`. This makes the physical operator migration
  visible in ordinary profiled query output, so tests and benchmarks can tell
  whether a query stayed on typed/source index paths or fell back to binding
  materialization.
- Top-level `$rows`/relation-source input clauses now avoid becoming ordinary
  input bindings in the relation engine. Source inputs remain available to the
  clause operator, so a query like `[$rows ?e :id 2] [$rows ?e :name ?name]`
  can start from a typed relation-source scan rather than first materializing
  `$rows` as a binding value.
- The typed anti/missing existence operator now resolves named DB sources
  before checking attr existence. Source-qualified `not`/`missing?` shapes can
  stay on the typed anti scan while using the correct source DB, instead of
  accidentally checking the primary DB.
- Aggregate/projection fallback rendering now records binding materialization
  through the same `Query-Stats` helper as clause fallback paths. This does not
  remove materialization yet, but it makes the remaining typed-to-binding
  boundary visible in profiles instead of hiding it behind an otherwise typed
  operator plan.
- Simple scalar and collection `:in` inputs can now seed the physical relation
  engine for clause/predicate queries instead of forcing the older binding
  path. This keeps qpred-style shapes such as
  `[:find ?e ?s :in $ ?min :where [?e :salary ?s] [(> ?s ?min)]]` on typed
  input columns, typed index scans, and typed predicate filtering with zero
  binding materialization. Unbound typed clause producers are now counted in
  `:typed-index-scans` / `:source-index-scans` as physical index operators, so
  profiles no longer under-report work done before a bind join.
- Simple tuple and relation `:in` inputs now seed the same typed initial
  relation instead of falling back through `Binding` rows. Tuple inputs such as
  `:in $ [?target ?min]` and relation inputs such as
  `:in $ [[?target ?min]]` can feed typed clause scans and predicates directly,
  including Cartesian products with earlier scalar/collection inputs. Focused
  tests cover both direct initial-relation construction and profiled query
  execution with zero binding materialization.
- Relation `:in` inputs backed by scalar map values now use the same typed
  path for simple width-2 relation bindings. This keeps host/EDN map inputs for
  `:in [[?k ?v]]` on typed columns instead of routing through the generic
  binding expander.
- Persisted/source-backed `DB-Read-Source` queries now have a source-native
  one-clause entity/value projection for simple indexed scans such as
  `[?e :user/name ?name]`, plus a two-attribute projection for simple entity
  joins such as `[?e :user/name ?name] [?e :user/age ?age]`. Durable
  `Store-DB` queries can answer these shapes by scanning source indexes and
  entity-local values directly instead of first building generic source binding
  rows.
- The same source projection layer now covers a simple filtered bind-join:
  `[?e :user/email "ada@example.com"] [?e :user/name ?name]` scans AVET by the
  bound attr/value pair and then reads the projected entity attr through
  `DB-Read-Source`. This keeps the common lookup-then-project shape on source
  indexes and current-fact checks instead of generic binding rows.
- Fixed-entity two-attribute projections such as
  `[1 :user/name ?name] [1 :user/age ?age]` now use an entity-local
  source-star path over `DB-Read-Source` instead of generic binding rows. This
  is the first concrete source-native merge/star operator for entity attribute
  reads.
- Fixed-entity single-attribute projections such as
  `[1 :user/name ?name]` now use a direct source entity+attr projection. The
  operator stays on `DB-Read-Source`, skips reverse attributes so existing
  reverse lookup semantics still use the generic reverse-aware path, and
  returns no rows for retracted entity attrs.
- Fixed-entity star projections such as `[1 ?a ?v]` now use a direct
  entity-local source projection. The operator scans current source datoms for
  the entity, returns attr/value columns in `:find` order, and avoids generic
  source bindings for simple entity inspection queries.
- Fixed attr/value entity lookups such as
  `[?e :user/email "ada@example.com"]` now use a direct source AVET projection
  and return entity rows without creating generic source bindings.
- One-column attr scans such as `[?e :user/name ?name]` with `:find ?name`
  now use a direct source attr/value projection, deduping values and avoiding
  generic source bindings.
- Entity-only attr existence scans such as `[?e :user/name ?name]` with
  `:find ?e` now use a direct source attr/entity projection. The operator
  dedupes entity rows and filters retracted facts through `DB-Read-Source`.
- Omitted-value attr existence scans such as `[?e :user/name]` now use the
  same source attr/entity projection instead of materializing generic source
  binding rows.
- Simple source-backed pull queries such as
  `[:find ?e (pull ?e [:db/id :user/name]) :where [?e :user/name]]` and
  pull-only variants now scan source indexes and materialize pull rows
  directly, avoiding the source `Binding` bridge for the common
  entity-attr-existence pull shape. The same operator now handles pull
  patterns supplied through `:in` and a simple entity equality predicate such
  as `[(= ?e 302)]`, so host-supplied pull patterns and focused pull reads can
  stay source-native too.
- Single source-backed data clauses feeding `count`, `count-distinct`, and
  bounded top-n `min`/`max` aggregates now run as a direct source aggregate
  operator. The operator scans current source candidates, applies the same
  `:with`/aggregate-argument dedupe semantics as the generic aggregate path,
  and returns the aggregate row without first materializing source `Binding`
  rows.
- Single source-backed all-var clauses such as `[?e ?a ?v]` now have a
  source-native projection operator. It scans current source candidates,
  returns entity, attr keyword, and value columns in `:find` order, and keeps
  retracted datoms filtered without first creating generic source bindings.
- `DB-Read-Source` and `Store-DB` query wrappers now have profiled text and
  prepared-query variants. Source-backed profiles report coarse
  `:source-index-scans`, `:binding-materializations`, and `:output-rows`, so
  durable DB-value queries can show whether they stayed on direct source
  operators or crossed into generic binding materialization.
- Fixed-target reverse-ref clauses such as `[301 :item/_parent ?child]` now
  have a source-native VAET projection operator. This keeps common reverse
  navigation over durable DB values on `DB-Read-Source` indexes instead of
  materializing generic source bindings.
- One-clause reverse-ref pair projections such as
  `[?parent :item/_parent ?child]` now use the same source-native projection
  layer. The operator scans the forward attr and projects reverse logical
  entity/value columns directly, instead of accidentally treating the reverse
  keyword as a stored forward attr.
- Child-to-parent reverse lookups such as `[?parent :item/_parent 302]` now
  stay on the source projection path too, scanning the child entity's forward
  ref attr and projecting the stored parent entity without generic bindings.
- One-column reverse-ref projections such as
  `[?parent :item/_parent ?child]` with `:find ?child` or `:find ?parent` now
  also stay on source-native projection operators, deduping logical children
  or parents directly from the forward attr scan.

Work:

1. Continue replacing binding-row materialization with typed relation/operator
   paths where it matters.
2. Add source-aware operators for:
   - indexed scan
   - bind join
   - merge/star scan over entity attributes
   - projection and aggregate materialization
3. Keep benchmark wins tied to general operators, not special recognizers for
   named benchmark queries.

Acceptance:

- DataScript compatibility tests remain green
- MusicBrainz matrix remains green
- Datalevin-style read benchmark comparisons are periodically re-run
- query profiling can show which physical operators ran. The first typed/source
  index counters are in place; later operators should add similarly coarse
  counters when they become important enough to distinguish in profiles.

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
