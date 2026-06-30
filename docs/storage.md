# Storage

Vev has completed the first durable SQLite proof phase. Durable storage is not
production-complete, but the basic semantic loop is now real and measured:
open, write, close, reopen, query, transaction metadata, host connection
handles, and Datalevin-style write-bench scaffolding all exist. The active
storage phase is now the shared immutable/chunked index architecture described
in `docs/storage-architecture.md`.

The storage layer must preserve the existing semantic model:

- DB values are immutable snapshots.
- Transactions append facts and retractions with stable tx ids.
- Query, pull, entity, and index semantics are owned by the engine, not by SQL.
- Host APIs should keep using opaque connection and DB snapshot handles.

## Current Slice

The first implemented storage slice was snapshot-file persistence:

- `save-db-snapshot-text`
- `load-db-snapshot-text`
- `open-conn-snapshot-text`
- `persist-conn-snapshot-text`

These functions write and read the existing EDN-ish serializable datom snapshot.
This is deliberately a scaffold, not the final SQLite backend. Its job is to
make the durable open/write/close/reopen/query loop real while the storage
boundary is still small.

The SQLite-backed slice persists datoms as rows:

- `save-db-sqlite`
- `load-db-sqlite`
- `open-conn-sqlite`
- `persist-conn-sqlite`
- `open-sqlite-conn`
- `transact-sqlite-tx-data`
- `transact-sqlite-text`

It creates Vev metadata, transaction, datom, and forward-compatible index
root/chunk tables, writes one row per datom through a SQLite transaction,
reopens from disk, rebuilds the in-memory indexes from those rows, and then
runs normal Vev queries. The older snapshot table remains as a compatibility
fallback for databases created by the first SQLite snapshot slice. Vev now
writes stable `vev_datoms.log_index` values and bounded logical-index chunks
after successful SQLite transactions and explicit SQLite persists: small
indexes use a single payload chunk, larger indexes use bounded leaf chunks plus
a parent root chunk, and a root row records the visible chunk root for each
logical index at the committed basis tx. Reopen
now loads the latest root metadata before datom rows, rebuilds from datom rows
as the compatibility path, and validates persisted index entries back through
root/chunk edges against those rebuilt indexes. Vev can now also load bounded
persisted index-entry pages by offset and limit, reading only the leaf chunks
covering that page. A read-only SQLite index cursor wraps that page loader with
cached-page `count`/`at` access over persisted logical index entries. The
runtime `DB-Index-View` abstraction can now wrap either resident arrays or a
SQLite cursor, and tests exercise view `count`/`at`/bound helpers over a
persisted cursor. SQLite can also fetch an individual datom by durable log
index, which is needed for resolving persisted index entries without full
datom-log materialization. `SQLite-Index-Snapshot` now packages the four
persisted index cursors and datom lookup behind one path/open handle, and
`SQLite-DB-Snapshot` adds basis tx plus datom count metadata, so Vev can open
storage root/cursor state without `load-db-sqlite`. It can also read a single
entity by binary-searching persisted EAVT and resolving only that entity's
datoms, plus attribute and entity+attribute reads by binary-searching persisted
AEVT/EAVT and resolving matching durable log indexes. The DB snapshot keeps a
prepared datom-by-log-index statement open for repeated materialization.
Live SQLite connections now have internal text and prepared query wrappers
(`q-result-sqlite-conn-text` and `q-result-sqlite-conn-prepared`) that open a
short-lived `SQLite-DB-Snapshot`, query through `DB-Read-Source`, close the
storage snapshot, and return an owned `Query-Result`. Normal public
`connect`/`db` access through those cursors is the next storage step. There is
also an internal source-only SQLite connection opener that skips
`load-db-sqlite` and keeps only the live SQLite handle plus a small resident
shell for cleanup/error reporting. That source-only handle can query through
the persisted snapshot wrappers and rejects transactions explicitly.
The storage-neutral aliases (`open-store-read-only`, `q-result-store-text`, and
`q-result-store-prepared`) now expose this mode inside Vev without making
query-facing code mention SQLite. `store-read-only?` and
`store-resident-db-available?` make the current compatibility boundary explicit:
read-only stores can query persisted index chunks, but they do not contain a
resident rebuilt `DB`.
`Store-DB` is the internal storage-neutral immutable DB snapshot handle for
this path. It currently wraps a retained `SQLite-DB-Snapshot`, exposes the
same `DB-Read-Source` boundary as resident DBs, and can run text/prepared
queries without rebuilding resident arrays. It also has a shared-snapshot
variant for the shared in-memory publish path. The C ABI `vev_db_t` wrapper has
started moving to this shape internally, but public C/JVM verification is still
blocked by the raw Odin transaction-listener callback compile issue in
`src/vev_abi/vev_abi.kvist`.

There are now two write modes:

- explicit full persist: `persist-conn-sqlite` replaces the durable datom rows
  with the connection's current full datom log
- SQLite-backed connection wrapper: `transact-sqlite-*` runs the normal Vev
  transaction engine and appends the successful report tx-data plus tx metadata
  rows to SQLite before returning

If the SQLite append fails after the in-memory transaction succeeds, the
wrapper restores the previous DB snapshot and reports a failed transaction.
That keeps the wrapper-level connection consistent with the durable store.
The wrapper now owns a live SQLite handle for its lifetime, so repeated
transactions do not reopen the file or rerun schema setup on every commit.

The public host API now exposes this durable connection shape through
storage-neutral connection handles:

- C ABI: `vev_connect`, `vev_connection_*`
- Python: `vev.connect(...)`
- Java: `vev.connect(...)`
- Clojure: `vev/connect`
- Rust example: `DurableConn::open`

The current durable backend is SQLite. A plain filesystem path and
`sqlite://...` URI both select the SQLite backend. The older
`vev_sqlite_conn_*`, `open-sqlite`, and `openSqlite` names remain as
backend-specific compatibility/debug entry points, but application examples
should use the neutral `connect` shape.

Durable connection metadata is available through the same neutral boundary:
`vev_connection_backend` reports `"sqlite"` today, `vev_connection_path`
reports the concrete storage path, and `vev_connection_basis_t` reports the
last committed transaction visible to the connection. `vev_connection_tx_count`
reports the number of persisted transaction-log rows, and
`vev_connection_tx_ids` returns the persisted transaction ids in order. Basis,
transaction count, and transaction ids are recomputed when opening a durable
store, so close/reopen preserves both rows and observable transaction-log
metadata. Higher-level wrappers expose the same diagnostic shape as
`backend`/`path`/`basis_t`/`tx_count`/`tx_ids` methods or Clojure
`connection-info`. `vev_connection_info_edn` is the C-friendly
convenience form for logging or simple tooling that wants the same metadata as
one EDN map string.

Live connection transactions return reports whose `db-after` is the
connection's current DB value. Cleanup code for those reports should use
`delete-live-tx-report-shallow`, which deletes the report-owned `db-before`
and report collections without freeing the live connection DB. The generic
`delete-tx-report-shallow` remains appropriate for immutable `with-*` reports
and failed reports that do not alias a live connection.

Explicit full DB persist cannot reconstruct report-only tx metadata from a
bare DB value; metadata rows are written by the SQLite-backed transaction
wrapper when it has the successful transaction report in hand.

`bench/sqlite_storage.kvist` now measures the first durable storage baseline:

- `single-append`: one SQLite-backed transaction per entity
- `batch-append`: one SQLite-backed transaction for a configurable entity batch
- `batch-transact-memory`: the in-memory transaction part of the same batch
- `batch-append-sqlite`: the SQLite append part of the same batch
- `batch-before-snapshot`: reportable DB-before snapshot creation
- `batch-resolve-tx`: tx-data resolution into concrete tx ops
- `batch-apply-resolved`: applying already-resolved ops to the connection
- `append-log-copy`: direct copy/append cost for the current datom log
- `append-index-build`: direct incremental index build cost for the copied log
- `persisted-index-load`: load persisted logical indexes through SQLite
  root/chunk rows without building a full `DB`
- `persisted-index-page-load`: walk persisted logical indexes through bounded
  SQLite root/chunk pages without building a full `DB`
- `persisted-index-cursor-scan`: scan persisted logical indexes through the
  cached SQLite index cursor abstraction
- `persisted-index-snapshot-open`: open all persisted index cursors as a
  `SQLite-Index-Snapshot` and resolve a representative datom by durable log
  index without rebuilding a full resident `DB`
- `persisted-db-snapshot-open`: open basis/datom-count metadata plus all
  persisted index cursors as a `SQLite-DB-Snapshot`, then resolve a
  representative datom by durable log index
- `persisted-db-snapshot-entity-read`: keep a `SQLite-DB-Snapshot` open,
  binary-search persisted EAVT for one entity, and materialize only that
  entity's datoms by durable log index
- `persisted-db-snapshot-entity-attr-read`: read one entity+attribute from a
  persisted DB snapshot without reopening resident arrays
- `persisted-db-snapshot-attr-read`: scan one attribute through persisted AEVT
  and materialize matching datoms; sampled lightly because it intentionally
  materializes many rows through the snapshot's prepared log-index reader
- `persisted-db-snapshot-source-query`: parse and execute a one-clause EDN
  query against `SQLite-DB-Snapshot` through the new source-backed query path,
  without reopening resident arrays
- `persisted-db-snapshot-source-join-query`: parse and execute a multi-clause
  EDN join query against `SQLite-DB-Snapshot` through the source-backed query
  path, without reopening resident arrays
- live store source queries: internal text/prepared wrappers query the latest
  persisted snapshot through source-backed reads, then close the snapshot before
  returning owned result rows
- read-only store open/query: skips resident datom-log replay and uses the live
  handle only for short-lived persisted snapshot reads
- `store-conn-source-prepared-query`: open a normal live durable store and
  measure the prepared-query wrapper that opens/closes a short-lived persisted
  snapshot for each read
- `store-read-only-open`: open a read-only durable store without resident
  datom-log replay
- `store-read-only-prepared-query`: query through a read-only durable store,
  opening short-lived persisted snapshots for each read
- `store-db-prepared-query`: retain a chunk-backed immutable `Store-DB`
  snapshot once and run prepared queries against that DB value
- `reopen-rebuild`: reopen SQLite datom rows and rebuild in-memory indexes
- `reopened-query`: run a prepared query against the reopened DB snapshot

`bench/sqlite_storage.kvist` accepts `--workload <name>` to run one workload.
The group names `append`, `durable`, and `source` run related subsets; `all`
remains the default.

The current `source` subset exercises the persisted source query path, the
store-level prepared query wrapper, read-only store open/query, and retained
`Store-DB` DB-value queries without resident datom-log replay. While adding
this selector, the source-backed clause matcher was tightened to treat scanned
datom values as borrowed and copy only when a value is stored in an output
binding. That keeps source snapshot query cleanup valid for larger scans.

The first benchmark pass found and fixed a serializer ownership bug: storage
callers delete `value-serializable-text` results, so that function must return
heap-owned text for literals, formatted scalars, keywords, symbols, vectors,
and maps.

`bench/write_bench.kvist` now starts mapping Datalevin `write-bench` concepts
onto Vev's durable API:

- pure SQLite-backed writes at batch sizes 1, 10, 100, and 1000
- mixed read/write behavior using prepared EDN queries against the live DB
  snapshot
- end-to-end call latency and commit-path latency reporting

This harness is intentionally smaller than the final external benchmark. It is
for regular development runs and accepts `--total`, `--report-every`,
`--mixed-operations`, `--batch`, and `--seed-batch` so larger runs can be
launched without source edits. It also has
`--workload pure|mixed|snapshot-heavy|shared-snapshot-heavy|shared-store-db-heavy|both`
and `--path`, which allows the Datalevin-style sequence of writing a durable
store first and then running mixed read/write against the same store. It uses a
plain long `:item/key`, matching Datalevin's write-bench schema.
The `snapshot-heavy` workload keeps the same durable write path but calls the
ordinary DB snapshot API after each commit and retains every old DB value until
the end of the run. That workload is the current Batch 4 acceptance harness for
Datomic-style code that passes DB snapshots around while a connection continues
to transact.
The `shared-store-db-heavy` workload measures the storage-neutral host-facing
shape over the shared in-memory publish path. It commits typed tx-data through
`Store-Conn`, retains every `Store-DB`, and closes those retained handles at the
end. A local batch-1, 200-write run on June 30, 2026 matched raw
`shared-snapshot-heavy` almost exactly: both ended near 0.172 ms commit latency
and 0.012 ms snapshot-retain latency at 200 writes. That means the
storage-neutral `Store-DB` wrapper is not adding visible cost at this scale;
the remaining work is still the shared publication adapter and resident-index
boundary.

A 10k-row local run on June 26, 2026 shows the current durable shape clearly:

- batch-100 pure write: about 13k writes/second by 10k rows
- batch-1 pure write: about 544 writes/second by 10k rows
- mixed read/write over a 10k-row store: about 184 writes/second

An initial `snapshot-heavy --total 500 --batch 1` local run on June 30, 2026
kept 500 old DB snapshots alive. Commit latency grew from about 0.33 ms near
100 writes to about 2.07 ms near 500 writes. The explicit post-commit `db`
clone was still only about 0.003-0.008 ms per snapshot at that scale, so the
visible problem is the commit/publish path copying and rebuilding resident
arrays, not the additional retained handle container by itself.

The batch-1 and mixed curves point at the same bottleneck: each successful
connection transaction produces a new immutable `DB` value by copying the datom
log plus `current`, `eavt`, `aevt`, `avet`, and `vaet` index arrays. That is
semantically correct and keeps DB values immutable, but it is not the final
durable write representation. The next storage implementation work should move
`DB` internals toward shared immutable index storage or chunked index pages so a
new DB value can share old index pages and only add/replace the affected tail
or page set. Special reportless fast paths are not the desired fix; transaction
reports, listeners, and `db` snapshots still need correct immutable values.

## Active Storage Architecture Work

The current SQLite path is correct, but the known follow-up is architectural
rather than a small local optimization:

1. Replace whole-array DB/index ownership copies with shared immutable DB/index
   storage or chunked index pages.
2. Preserve ordinary immutable DB snapshot semantics: reports, listeners, host
   handles, and `db` values must still see stable values after later writes.
3. Keep SQLite as the durable log, metadata, and page/chunk store.
4. Add chunk-backed read cursors over the persisted page loader for `eavt`,
   then extend to `aevt`, `avet`, and `vaet`. The first cursor exists for all
   four persisted indexes, and the index-view boundary can wrap those cursors.
   The next step is to make reopened DB snapshots choose those cursor-backed
   views instead of rebuilding resident arrays for normal access.
5. Replace normal reopen with metadata/root loading plus lazy or bounded chunk
   loading. Datom-log replay should become recovery/migration behavior, not the
   large-database startup path.
6. Scale `bench/write_bench.kvist`, MusicBrainz reopen/query, and
   snapshot-heavy loops against this representation.

Special reportless fast paths are not the desired fix. They would make the
benchmark look better while dodging the core requirement that Vev DB values are
immutable values applications can pass around.

The first resumed storage-copy cleanup removed an unnecessary full `DB` clone
from registered/native transaction-function paths. Those paths now follow the
ordinary live transaction ownership shape: the pre-transaction DB becomes the
successful report's `db-before`, and the connection moves to the newly
published `db-after`. This is not the shared-index representation yet, but it
keeps all transaction entrypoints on the same publish boundary before the DB
layout changes.

The first shared-index building block is now present as `Shared-Int-Index`.
It stores index entries in retained immutable chunks, can append new chunks
while sharing old chunks, and has tests for releasing old and new handles
independently. It is not yet wired into `DB.eavt`, `DB.aevt`, `DB.avet`, or
`DB.vaet`; the next storage step is to add a DB-index wrapper around this
chunked representation and move resident index publication to that boundary.
That wrapper now exists as `Shared-DB-Int-Indexes`, and the existing
`DB-Index-View` abstraction can read from it. The remaining step is changing
transaction publication to store or retain these shared indexes as part of DB
snapshots, rather than only materializing owned resident arrays.
`DB-Read-Source` can also wrap a resident datom log with shared chunked index
views, and a source-backed query test now runs EDN text over that source. This
is the first executable query path over the new in-memory shared-index
representation. Currentness for that path now also reads the shared chunked
`current` index, so add/retract history is filtered without reaching back to
the resident `DB.current` side table.
Datoms can now also be stored in `Shared-Datom-Log` retained chunks. The
source-backed query path has a variant that reads both datoms and indexes from
shared chunks, so simple data-clause queries can execute without a resident
`DB` pointer for materialization. `Shared-DB-Snapshot` packages those shared
datom and index chunks into one retained immutable DB-value handle; tests retain
one snapshot, release the original handle, and continue querying through the
retained value.
Appended shared snapshots can now share the old datom-log chunks and append
only new datom chunks. Shared int indexes also retain old chunks when the
post-transaction index keeps the old index as an exact prefix; merged or
reordered indexes still rebuild for correctness. Replacing that fallback with
range/page sharing is the next Batch 4 implementation step.
`Shared-Conn` now provides the first connection-shaped publish path over those
shared snapshots. It still applies transactions through the existing resident
connection, then publishes a new retained `Shared-DB-Snapshot` from the previous
snapshot and the post-transaction DB. That is an intermediate architecture
step, not the final storage model: the next version should build shared chunks
directly at the commit boundary instead of adapting from resident arrays after
the fact. `bench/write_bench.kvist --workload shared-snapshot-heavy` measures
that path separately from SQLite-backed `snapshot-heavy`; a local batch-1,
100-write sample showed the shared path faster. Shared publication now also
uses append-only/new-entity facts from the transaction report to retain
`current` and `eavt` directly when those indexes are known prefixes.
`eavt-entity-starts` is a resident DB build/index maintenance side table, not
part of shared immutable snapshots. A local batch-1, 500-write sample with chunk
size 64 ended at
about 0.323 ms commit latency. The remaining increase comes from indexes that
still rebuild or scan merged/reordered resident arrays.
`Shared-Int-Index` also has a tested same-offset page-sharing constructor for
retaining unchanged chunks while replacing changed pages. It is currently a
building block, not the default publish fallback: comparing every old page in
the append-heavy workload cost more than exact-prefix-or-rebuild. The durable
storage direction is therefore merge-aware page publication, where the merge
knows which pages are unchanged instead of rediscovering it by scanning.
Append-only shared publication now has that first merge-aware path for the
logical int indexes. It streams sorted new datom indexes together with old
shared chunks and retains any old chunk copied contiguously by the merge. This
is a memory/copy architecture step, not a finished latency win: the current path
still builds resident indexes first, and the shared merge builder has per-value
overhead that should be reduced when publication moves fully to shared chunks.
The first overhead reduction batches the tail of added indexes instead of
pushing every remaining added value individually.
The merge builder also emits full chunk-sized appended slices directly when the
pending buffer is empty, avoiding per-value pending-buffer pushes for those
runs.
`Shared-Tx-Report` now preserves transaction report DB values as retained shared
snapshots. That lets a report's `db-before` and `db-after` be queried after the
connection has advanced, without cloning resident DB arrays for those report
values.
`Store-DB` can now also wrap a retained shared in-memory snapshot, not only a
SQLite snapshot. That keeps the host-facing immutable DB handle direction
storage-neutral: the same `q-result-store-db-*` wrappers can query persisted
snapshots and shared in-memory snapshots through `DB-Read-Source`.
`q-result-store-db-prepared-with-input-text` is the prepared-query-with-inputs
variant used by the ABI direction, so durable host DB handles can execute
ordinary prepared queries without resident DB rebuild once the ABI wrapper
build is unblocked.
`Store-Conn` is also now storage-neutral at the connection boundary. The
default `open-store` functions still create SQLite-backed stores, while
`create-shared-store` creates the in-memory shared-index publish path. Both use
the same `store-db`, `q-result-store-*`, `transact-store-text`, and
`close-store` entry points, so host bindings can move toward one DB-handle shape
instead of separate resident, SQLite, and shared APIs.
`Store-DB` can also render query and pull values through `DB-Read-Source`.
This lets storage-neutral callers, including the CLI, print parsed query results
from retained SQLite/shared snapshots without reopening or reaching through a
resident `DB` field just for value rendering.
The write benchmark now has a `shared-store-db-heavy` workload over this same
storage-neutral connection/snapshot API. It provides the current host-handle
acceptance check for the shared publish path, and early numbers match the raw
shared snapshot workload closely, so the next optimization should target direct
shared chunk publication rather than another host wrapper bypass.
Transaction reports now include the transaction engine's datom append start
index plus append-only and ordered-new-entity publication facts. The shared
connection publish path uses those fields directly, instead of recomputing
append position and eligibility from the resident DB and emitted `tx-data`
after the resident transaction completes. This keeps the storage adapter
aligned with the transaction engine and is the first small boundary change
toward a real shared publish plan.
That path now goes through `shared-db-snapshot-with-tx-report`, which is the
explicit report-to-shared-snapshot boundary future direct shared publication
should replace internally.
Retained `Shared-Tx-Report` values preserve that append start and append-mode
metadata too, alongside their shared `db-before`/`db-after` snapshots.
The resident entity-range helper now uses the same `DB-Index-View` binary-search
shape as the source boundary instead of reading the `eavt-entities` /
`eavt-entity-starts` side table directly. The side table still exists as a
resident DB build/index maintenance detail, but ordinary entity range/existence
checks no longer require it.
The unused position-indexed resident helpers have also been removed, so
query-facing code no longer exposes `eavt-entity-starts` positions as an API
shape.

The direct datom append paths now also share the transaction engine's guarded
append-only index builder when the appended datoms are simple additions that do
not touch validation schema and do not repeat current or in-batch facts.
Retractions, schema datoms, repeated facts, and unkeyable values still fall back
to full DB/index rebuilds. This keeps `db-with-datoms`, `with-datoms`, and
`transact-datoms` on the same incremental-index path used by ordinary
append-only transactions without changing their correctness model.
In the shared publication path, append-only `current` indexes are now extended
directly from the committed datom range instead of copied from the resident
post-commit `current` array. This is still an intermediate architecture because
the transaction engine builds a resident DB first, but it removes one index from
the resident-index adaptation step.
Ordered new-entity `eavt` publication follows the same direction: it builds the
appended tail by sorting only the new datom indexes in EAVT order and appending
that tail to the retained shared base, instead of slicing the resident
post-commit `eavt` array.
For general append-only shared publication, the shared datom log is now extended
from `report.tx-data` instead of slicing the resident post-commit datom array.
The ordered new-entity `eavt` tail now also uses the appended `report.tx-data`
slice plus the report start index as its datom source, so that path no longer
needs the resident post-commit datom log for EAVT publication.
The shared `eavt`, `aevt`, `avet`, and `vaet` merge builders still compare
through the resident post-commit datom log for O(1) datom access. A direct
old-shared-vs-new merge was correct but slower with the current chunked datom
log, so the next structural step is efficient shared datom random access before
removing that remaining resident comparison source.
`Shared-Datom-Log` now carries per-chunk start offsets and uses binary search
to find the chunk for a datom position. This keeps retained/appended partial
chunks addressable without scanning through all previous chunks and gives the
next direct shared-index merge attempt the right lookup primitive.
That direct merge was retried after adding chunk starts and an estimated-chunk
fast path. It was still slower than the current resident-comparison merge in
the small retained-snapshot benchmark, so it is not the default hot path. The
useful retained result is indexed shared datom lookup; the next production
storage step is to publish shared datom/index chunks directly from the
transaction application path, instead of adapting from a fully built resident
`DB` and trying to make that adapter ever more clever.

SQLite rollback cleanup now also follows the live-report ownership rule. When
an in-memory transaction succeeds but SQLite append fails, the wrapper restores
the previous DB and cleans the successful report as a live connection report so
`db-after` is not freed twice.

## SQLite Backend Shape

SQLite is the first production durable backend.

Initial schema direction:

- database metadata table with format version and current basis tx
- append-only transaction table
- append-only datom table storing `e`, `a`, typed value payload, `tx`, and
  `added`
- persisted logical index root/chunk tables for Vev-owned index segments
- optional materialized current-datom table once commit/read costs need it

Current implementation status and later order:

1. Add storage-level metadata inspection/replay APIs only where concrete tools
   need them.
2. Keep rebuilding in-memory indexes from the datom tables on open until reopen
   cost measurements require persisted logical indexes.
3. Continue the append-only transaction path when storage work resumes. The current implementation avoids
   full index rebuilds for conservative direct add-only transactions, skips
   full-schema validation for ordinary non-schema transactions, and clones
   reportable DB snapshots from existing indexes instead of rebuilding them.
   Append-only eligibility skips current-DB fact/entity-attr checks when all
   ops target genuinely absent entities. Ordered absent-entity imports enforce
   cardinality-one and cardinality-many duplicate rules locally, and
   append-only index maintenance extends the `eavt` entity table when new entity
   ids sort after existing ids. Parsed EDN transaction values are cloned into
   DB log datoms, so chunked imports are independent from per-file input buffer
   lifetimes.
   The benchmark now separates snapshot, resolution, apply, log copy,
   incremental index build, SQLite append cost, and a shared snapshot-heavy
   publish path. The next write-performance milestone is replacing the
   remaining resident-array adaptation in `Shared-Conn` with direct shared
   chunk publication.
4. Move selected logical indexes to persisted structures, starting with a single
   read-only chunk writer/cursor and then expanding to the full index set.
5. Keep extending the new `bench/write_bench.kvist` harness until it can run at
   Datalevin `write-bench` scale, then compare commit/reopen behavior against
   existing systems.

Non-goals for the first SQLite backend:

- making SQL rows the public data model
- query execution by translating Vev Datalog to SQL
- multi-backend abstraction before SQLite works well
- distributed client/transactor semantics

## Validation Workloads

MusicBrainz/Datomic workshop data is now the next validation workload. See
`docs/musicbrainz.md` for the active plan. It should validate that the current
in-memory and SQLite-backed paths preserve the same Datomic-shaped semantics
under realistic query, pull, schema, ident, and import pressure. Datalevin
`write-bench` remains the later durable-write comparison once the shared
immutable index-storage work resumes.
