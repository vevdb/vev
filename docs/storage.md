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

The first SQLite-backed slice now exists too:

- `save-db-sqlite`
- `load-db-sqlite`
- `open-conn-sqlite`
- `persist-conn-sqlite`

This currently stores the same serializable datom snapshot inside SQLite. It
creates Vev metadata and snapshot tables, writes through SQLite transactions,
reopens from disk, rebuilds the in-memory indexes, and then runs normal Vev
queries. This proves the SQLite link/schema/write/reopen path without yet
committing the final durable datom layout.

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

1. Replace snapshot-in-SQLite persistence with append-only SQLite datom and
   transaction tables.
2. Store transaction boundaries and tx metadata explicitly.
3. Rebuild in-memory indexes from the append-only tables on open.
4. Add write-bench style measurements for commit latency, batch throughput, and
   reopen cost.
5. Move selected logical indexes to persisted structures only after benchmarks
   show full rebuild is the bottleneck.

Non-goals for the first SQLite backend:

- making SQL rows the public data model
- query execution by translating Vev Datalog to SQL
- multi-backend abstraction before SQLite works well
- distributed client/transactor semantics

## Validation Workloads

MusicBrainz/Datomic workshop data should validate the durable backend once
basic SQLite reopen/query works. Datalevin `write-bench` becomes relevant after
SQLite commit semantics exist.
