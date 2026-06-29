# Storage Architecture

Vev uses SQLite as the durable backing store, but SQLite is not the Datalog
executor. Query, pull, entity, rules, and index semantics belong to Vev's own
engine. SQLite stores durable facts, transaction metadata, and eventually
engine-owned immutable index chunks.

## Current State

The current SQLite path is correct but primitive:

- successful transactions append datom rows and tx metadata rows
- reopen reads durable datom rows
- Vev rebuilds all in-memory indexes from those rows
- immutable DB snapshots own dynamic arrays for the datom log and indexes

This proves open/write/close/reopen/query semantics, but it does not scale like
a real embedded Datomic-like database. Large reopen currently means "read all
datoms and rebuild all indexes", and frequent writes still copy too much DB and
index storage per committed snapshot.

## Target Shape

The durable store should become:

- SQLite transaction log plus metadata as the source of durable truth
- persisted immutable index chunks for Vev's logical indexes
- root rows that identify the index chunk roots visible at a basis tx
- DB snapshots that reference immutable index chunks plus small deltas
- bounded/lazy chunk loading on reopen instead of full datom-log replay

The query engine should continue to run over Vev indexes and physical query
operators. We should not compile Datalog to SQL except for internal diagnostic
or migration tooling.

## SQLite Schema Direction

The SQLite schema now includes forward-compatible tables for the next storage
generation:

- `vev_index_chunks`: immutable index pages/segments for `eavt`, `aevt`,
  `avet`, and `vaet`
- `vev_index_chunk_edges`: parent/child links for multi-level chunk trees
- `vev_index_roots`: basis tx to root chunk pointers
- `vev_meta.storage-architecture = vev-sqlite-chunked-index-v0`

The new tables are no longer only schema scaffolding. On successful SQLite
transactions and explicit SQLite persists, Vev writes persisted logical index
artifacts for `eavt`, `aevt`, `avet`, and `vaet`. Small indexes use a single
payload chunk. Larger indexes are split into bounded leaf chunks with a parent
root chunk linked through `vev_index_chunk_edges`. Root rows point at the
visible chunk root for each index at the committed basis tx. The payload stores
current in-memory datom-index order, so it is a persisted Vev index artifact
rather than a SQL query table. Existing reopen behavior still uses datom-row
replay and in-memory index rebuild. Vev can already follow the latest root,
load leaf chunks in edge order, parse the persisted index-entry vector, and
validate it against the rebuilt in-memory indexes. Wiring query/reopen to use
that loader is the next implementation step.

## Implementation Milestones

1. Schema, metadata, and bounded root writer.
   Add root/chunk tables, architecture marker, inspection functions, and
   bounded logical-index chunk/root publication. This is implemented; no query
   or reopen behavior changes yet.

2. Add read-only chunk cursors.
   Teach Vev index accessors to read ranges from persisted chunks with an
   in-memory cache. The first persisted-entry loader exists and is tested
   against rebuilt indexes; the remaining work is range-oriented cursors rather
   than whole-index materialization.

3. Extend chunk-backed cursors to `aevt`, `avet`, and `vaet`.
   Query planning should choose the same Vev logical indexes whether they are
   fully resident arrays, chunk-backed cursors, or a snapshot delta overlay.

4. Replace whole-array DB snapshot ownership.
   Move `DB` internals toward shared immutable index storage plus small per-tx
   deltas. A new DB value should share unchanged chunks with older snapshots and
   own only new/replaced chunks and delta data.

5. Replace reopen rebuild behavior.
   Reopen should load metadata and latest root pointers first, then load chunks
   lazily or in bounded batches. Datom-log replay remains available for recovery
   and migration, not as the normal large-database startup path.

6. Benchmark real workloads.
   Measure MusicBrainz open/query/reopen, Datalevin-style write-bench append
   workloads, and snapshot-heavy read/write loops. Compare against Datomic,
   DataScript, and Datalevin where applicable.

## Non-Goals

- exposing SQLite tables as the public Vev data model
- making application callers configure SQLite schemas directly
- special reportless transaction fast paths that violate immutable DB snapshot
  semantics
- distributed Datomic client/transactor semantics before embedded durable
  storage is solid
