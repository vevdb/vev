# Storage

Vev's durable storage phase is now active. The storage layer must preserve the
existing semantic model:

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

The SQLite-backed slice now persists datoms as rows:

- `save-db-sqlite`
- `load-db-sqlite`
- `open-conn-sqlite`
- `persist-conn-sqlite`
- `open-sqlite-conn`
- `transact-sqlite-tx-data`
- `transact-sqlite-text`

It creates Vev metadata, transaction, and datom tables, writes one row per
datom through a SQLite transaction, reopens from disk, rebuilds the in-memory
indexes from those rows, and then runs normal Vev queries. The older snapshot
table remains as a compatibility fallback for databases created by the first
SQLite snapshot slice.

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
reports the number of persisted transaction-log rows. Basis and transaction
count are recomputed when opening a durable store, so close/reopen preserves
both rows and observable transaction-log metadata. Higher-level wrappers expose
the same diagnostic shape as `backend`/`path`/`basis_t`/`tx_count` methods or
Clojure `connection-info`. `vev_connection_info_edn` is the C-friendly
convenience form for logging or simple tooling that wants the same metadata as
one EDN map string.

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
- `reopen-rebuild`: reopen SQLite datom rows and rebuild in-memory indexes
- `reopened-query`: run a prepared query against the reopened DB snapshot

The first benchmark pass found and fixed a serializer ownership bug: storage
callers delete `value-serializable-text` results, so that function must return
heap-owned text for literals, formatted scalars, keywords, symbols, vectors,
and maps.

## SQLite Backend Plan

SQLite is the first production durable backend.

Initial schema direction:

- database metadata table with format version and current basis tx
- append-only transaction table
- append-only datom table storing `e`, `a`, typed value payload, `tx`, and
  `added`
- optional materialized current-datom table once commit/read costs need it
- optional persisted logical index tables/pages once full-load rebuild is too
  slow

Implementation order:

1. Add storage-level metadata inspection/replay APIs only where concrete tools
   need them.
2. Keep rebuilding in-memory indexes from the datom tables on open until reopen
   cost measurements require persisted logical indexes.
3. Continue the append-only transaction path. The current implementation avoids
   full index rebuilds for conservative direct add-only transactions, skips
   full-schema validation for ordinary non-schema transactions, and clones
   reportable DB snapshots from existing indexes instead of rebuilding them.
   Append-only eligibility skips current-DB fact/entity-attr checks when all
   ops target entities above the current max entity id, which is the common
   bulk-import shape.
   The benchmark now separates snapshot, resolution, apply, log copy,
   incremental index build, and SQLite append cost. The next write-performance
   milestone is reducing the remaining append application/report/index
   maintenance overhead before introducing a more complex shared DB/index
   representation.
4. Move selected logical indexes to persisted structures only after benchmarks
   show full rebuild is the bottleneck.
5. Once the local harness is stable, map Datalevin `write-bench` concepts onto
   Vev's API and compare commit/reopen behavior against existing systems.

Non-goals for the first SQLite backend:

- making SQL rows the public data model
- query execution by translating Vev Datalog to SQL
- multi-backend abstraction before SQLite works well
- distributed client/transactor semantics

## Validation Workloads

MusicBrainz/Datomic workshop data should validate the durable backend once
basic SQLite reopen/query works. Datalevin `write-bench` becomes relevant after
SQLite commit semantics and batch append behavior are both measured.
