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
  text/prepared queries, and close the retained snapshot explicitly. This is
  the internal shape the C ABI/JVM/Clojure durable `d/db` handle should map to
  once the ABI callback compile blocker is cleared.
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
   handles. Internal resident and source-backed wrappers now use this shape, but
   the attempted C ABI ownership-tag patch exposed an ABI compile issue around
   the raw Odin wrapper block. Rechecking `scripts/build_c_abi.sh` still fails
   during `kvist compile` at the raw Odin transaction-listener callback call
   (`abi_tx_listener_notify`), before generated Odin is written. Host handles
   remain pending rather than partially merged. Raw `Result-Set` cleanup is
   still available internally, but host-facing code should get a single result
   handle/free operation.
3. Decide whether Batch 1 should keep pushing host-facing source-backed query
   handles now, or pause that until the raw-Odin ABI compile issue is fixed in
   Kvist/the ABI layer. The source-backed engine path is now ahead of the C ABI
   exposure path.

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
   reads that ownership bit for cleanup.
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
- `shared-db-snapshot-with-appended-db` can now build a new shared snapshot
  from an old snapshot plus a post-transaction resident DB. It shares the old
  datom-log chunks and appends only new datom chunks. Shared int indexes now
  also retain old chunks when the post-transaction index preserves the old index
  as an exact prefix, with fallback rebuild for merged/reordered indexes. Tests
  verify the base datom, current, and EAVT chunks are retained and the appended
  snapshot remains queryable.
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
  index directly, and ordered new-entity commits also retain `eavt` and
  `eavt-entity-starts` directly without scanning old prefixes. A local
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
  500 writes, chunk size 1024 ended around 0.475 ms commit latency, and 1000
  writes ended around 0.804 ms. The remaining work is to avoid still building
  resident indexes first and to make the merge builder emit chunk-sized runs
  with less per-value overhead.

Work:

1. Introduce a DB index storage layer with immutable base chunks plus a small
   transaction delta. The first retained chunk primitive, grouped DB int index
   wrapper, retained DB snapshot, and connection-side shared publish wrapper
   exist. The next step is to make transaction publication build shared chunks
   directly instead of publishing through a resident `DB` and then adapting it
   into a shared snapshot.
2. Make a new DB snapshot share unchanged chunks with older snapshots.
   Datom-log sharing works for appended snapshots. Index chunk sharing now
   works for exact-prefix append cases, and append-only/new-entity publication
   can skip prefix proof for the known-prefix indexes. Same-offset page sharing
   exists as a tested primitive, but is not yet used blindly on the hot publish
   fallback because it regressed append-heavy commits. Merge-aware append-only
   index publication now retains full old chunks copied contiguously by the
   merge. The next step is removing the resident-index-first adaptation and
   reducing per-value overhead inside the shared merge builder.
3. Keep transaction reports, listeners, retained host DB handles, and `db-before`
   / `db-after` semantics exact.
4. Fold the existing append-only incremental path into this representation
   instead of maintaining it as a separate optimization.
5. Preserve resident-array mode as a useful small/in-memory implementation
   strategy if it remains simpler for tests and tiny databases.
6. Re-run `snapshot-heavy`, `shared-snapshot-heavy`, `pure --batch 1`, and
   `mixed` write-bench after each representation step so the architecture work
   is measured against the actual immutable DB-value workload.

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
