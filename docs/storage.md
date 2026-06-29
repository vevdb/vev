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
writes bounded logical-index chunks after successful SQLite transactions and
explicit SQLite persists: small indexes use a single payload chunk, larger
indexes use bounded leaf chunks plus a parent root chunk, and a root row records
the visible chunk root for each logical index at the committed basis tx. Reopen
now loads the latest root metadata before datom rows, rebuilds from datom rows
as the compatibility path, and validates persisted index entries back through
root/chunk edges against those rebuilt indexes. Vev can now also load bounded
persisted index-entry pages by offset and limit, reading only the leaf chunks
covering that page. Chunk-backed DB snapshots and query cursors are the next
storage step.

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
- persisted index page loading is now available as a storage primitive for the
  next chunk-backed cursor work, though the benchmark still primarily reports
  whole-index loading
- `reopen-rebuild`: reopen SQLite datom rows and rebuild in-memory indexes
- `reopened-query`: run a prepared query against the reopened DB snapshot

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
launched without source edits. It also has `--workload pure|mixed|both` and
`--path`, which allows the Datalevin-style sequence of writing a durable store
first and then running mixed read/write against the same store. It uses a plain
long `:item/key`, matching Datalevin's write-bench schema.

A 10k-row local run on June 26, 2026 shows the current durable shape clearly:

- batch-100 pure write: about 13k writes/second by 10k rows
- batch-1 pure write: about 544 writes/second by 10k rows
- mixed read/write over a 10k-row store: about 184 writes/second

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
   then extend to `aevt`, `avet`, and `vaet`.
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

The direct datom append paths now also share the transaction engine's guarded
append-only index builder when the appended datoms are simple additions that do
not touch validation schema and do not repeat current or in-batch facts.
Retractions, schema datoms, repeated facts, and unkeyable values still fall back
to full DB/index rebuilds. This keeps `db-with-datoms`, `with-datoms`, and
`transact-datoms` on the same incremental-index path used by ordinary
append-only transactions without changing their correctness model.

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
   incremental index build, and SQLite append cost. The next write-performance
   milestone, when we return to storage, is replacing whole-array DB/index
   ownership copies with a shared immutable DB/index representation.
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
