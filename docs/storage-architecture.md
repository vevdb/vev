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
rather than a SQL query table. Reopen now reads the latest root metadata before
the datom rows and validates persisted chunk indexes against the rebuilt
in-memory indexes. Vev can follow the latest root, load leaf chunks in edge
order, parse the persisted index-entry vector, and compare it to the rebuilt
`DB`. It can also load a bounded entry page from the persisted chunk tree by
offset and limit, reading only the leaf chunks that cover the requested window.
On top of that page loader, Vev now has a read-only SQLite index cursor that
keeps one cached page and serves `count`/`at` access over a persisted logical
index. The cursor is not wired into normal `DB` query execution yet; it is the
first concrete storage object that can become a chunk-backed index view. The
public datom index APIs plus transaction, schema, lookup-ref, uniqueness, and
current-value helper paths now go through a resident `DB-Index-View` boundary
instead of directly owning the slice logic at each call site; this is still
array-backed, but it gives query-facing and write-facing code a place to accept
chunk-backed index views later.
This is still a guarded compatibility path: Vev materializes datom rows and
rebuilds indexes before validation. Wiring query/reopen to use chunk-backed DB
snapshots and paged index cursors is the next implementation step.

## Implementation Milestones

1. Schema, metadata, and bounded root writer.
   Add root/chunk tables, architecture marker, inspection functions, and
   bounded logical-index chunk/root publication. This is implemented; no query
   or reopen behavior changes yet.

2. Add read-only chunk cursors.
   Teach Vev index accessors to read ranges from persisted chunks with an
   in-memory cache. Whole-index loading and bounded page loading now exist and
   are tested against rebuilt indexes. A first read-only SQLite index cursor
   exists with cached-page `count`/`at` access. Public datom index APIs and
   key transaction/schema/validation helper paths now use a resident index-view
   boundary. The remaining work is to make deeper query/index accessors consume
   that same boundary and then add a persisted cursor-backed implementation.

3. Extend chunk-backed cursors to `aevt`, `avet`, and `vaet`.
   Query planning should choose the same Vev logical indexes whether they are
   fully resident arrays, chunk-backed cursors, or a snapshot delta overlay.

4. Replace whole-array DB snapshot ownership.
   Move `DB` internals toward shared immutable index storage plus small per-tx
   deltas. A new DB value should share unchanged chunks with older snapshots and
   own only new/replaced chunks and delta data.

5. Replace reopen rebuild behavior.
   Reopen now loads metadata and latest root pointers first, then validates the
   chunks after datom-log rebuild. The next step is to construct `DB` index
   views from chunk roots directly, with datom-log replay reserved for recovery
   and migration rather than normal large-database startup.

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
