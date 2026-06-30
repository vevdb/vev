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
- `vev_datoms.log_index`: stable datom-log position used by persisted index
  entries
- `vev_meta.storage-architecture = vev-sqlite-chunked-index-v0`

The new tables are no longer only schema scaffolding. On successful SQLite
transactions and explicit SQLite persists, Vev writes persisted logical index
artifacts for `eavt`, `aevt`, `avet`, and `vaet`. Small indexes use a single
payload chunk. Larger indexes are split into bounded leaf chunks with a parent
root chunk linked through `vev_index_chunk_edges`. Root rows point at the
visible chunk root for each index at the committed basis tx. The payload stores
current in-memory datom-index order, so it is a persisted Vev index artifact
rather than a SQL query table. Those datom positions are now durable too:
SQLite writes `vev_datoms.log_index`, preserves log-index order on load, and
can fetch a single serialized datom by log index. Reopen now reads the latest
root metadata before the datom rows and validates persisted chunk indexes
against the rebuilt in-memory indexes. Vev can follow the latest root, load
leaf chunks in edge order, parse the persisted index-entry vector, and compare
it to the rebuilt `DB`. It can also load a bounded entry page from the
persisted chunk tree by offset and limit, reading only the leaf chunks that
cover the requested window.
On top of that page loader, Vev now has a read-only SQLite index cursor that
keeps one cached page and serves `count`/`at` access over a persisted logical
index. `DB-Index-View` now has both resident-array and SQLite-cursor modes, and
the storage tests exercise the same view `count`/`at`/bound helpers over a
persisted cursor. `SQLite-Index-Snapshot` now opens all four persisted logical
index cursors directly from a SQLite path or live handle, without calling
`load-db-sqlite`, and can resolve individual datoms by durable log index.
`SQLite-DB-Snapshot` wraps that index snapshot with basis tx and datom-count
metadata, giving normal reopen/query code a durable snapshot shape to target. It
can also binary-search persisted EAVT for an entity and materialize only that
entity's datoms from durable log indexes. The same snapshot can binary-search
persisted AEVT for an attribute and materialize entity+attribute reads without
resident datom/index arrays. The snapshot owns a reusable prepared SQLite
statement for datom-by-log-index resolution so broad materialization does not
prepare one SQL statement per datom. Normal reopened `DB` values still use
resident arrays today, but the query-facing boundary can now represent a
chunk-backed source. `DB-Read-Source` can now wrap either a resident `DB` or a
`SQLite-DB-Snapshot`, expose logical index views, filter retracted facts through
source-backed currentness checks, run attr/entity+attr datom reads, render a
simple pull-value map, and execute the first parsed EDN query shape over a
persisted source: plain data clauses with `:find` variables, including
multi-clause joins, primary `$` source-qualified clauses, predicate filters, and
scalar plus destructuring function clauses over already materialized values. It
can also render flat literal pull finds over forward attrs, wildcard pulls, flat
reverse-ref pulls, nested forward-ref pulls, scalar inputs, and pull pattern
inputs plus pull defaults from the persisted snapshot. The
public datom index APIs plus transaction, schema, lookup-ref, uniqueness,
current-value, pull, and entity helper paths now go through a resident
`DB-Index-View` boundary instead of directly owning the slice logic at each
call site; this is still array-backed, but it gives query-facing and
write-facing code a place to accept chunk-backed index views later. The general
`Clause-Index-Scan` query operator now also carries a `DB-Index-View` instead
of a raw index slice, so ordinary clause planning/matching is starting to use
the same storage-facing abstraction. The optimized entity-star, threshold,
self-join, two-attribute, entity-attribute, entity-int, entity string/int,
top-N aggregate, and missing-attribute projection operators use the same
boundary for their `avet`/`aevt`/`eavt` scans. The low-level latest-attribute
and cardinality-one fast entity helpers also read datom indexes through that
boundary now. Schema validation, transaction cardinality retractions, recursive
rule adjacency builders, and the remaining optimized typed/projection scans no
longer read datom index slices directly. The remaining resident read coupling
is concentrated in `db-index-slice` itself and the `eavt` entity-position side
tables.
This is still a guarded compatibility path: Vev materializes datom rows and
rebuilds indexes before validation for normal `DB` values. Wiring query/reopen
to use `SQLite-Index-Snapshot`/chunk-backed DB snapshots instead of resident
arrays is the next implementation step.

The June 30, 2026 local SQLite storage benchmark now includes
`persisted-db-snapshot-source-query` and
`persisted-db-snapshot-source-join-query`, which parse and execute simple
queries against a persisted `SQLite-DB-Snapshot` source. In that run the
one-clause source query median was about 1.1ms, the three-clause joined source
query median was about 3.4ms for the 2,000-entity fixture, and the compatibility
`reopen-rebuild` path was about 61ms. This is not the final query path, but it
proves parsed query execution can begin from persisted Vev index chunks without
first rebuilding a resident `DB`.

## Implementation Milestones

The concrete batch plan for the active storage phase is maintained in
`docs/next-steps.md`. This section records the architectural milestones and
current status.

1. Schema, metadata, and bounded root writer.
   Add root/chunk tables, architecture marker, inspection functions, and
   bounded logical-index chunk/root publication. This is implemented; no query
   or reopen behavior changes yet.

2. Add read-only chunk cursors.
   Teach Vev index accessors to read ranges from persisted chunks with an
   in-memory cache. Whole-index loading and bounded page loading now exist and
   are tested against rebuilt indexes. A read-only SQLite index cursor exists
   with cached-page `count`/`at` access, and `DB-Index-View` can now wrap that
   cursor as well as resident arrays. Public datom index APIs and
   key transaction/schema/validation, pull, entity helper, general
   `Clause-Index-Scan`, entity-star, threshold, self-join, two-attribute,
   entity-attribute, entity-int, entity string/int, top-N aggregate, and
   missing-attribute projection paths plus low-level latest-attribute and
   cardinality-one fast entity helpers now use a resident index-view boundary.
   Schema validation, transaction cardinality retractions, recursive rule
   adjacency builders, and the remaining optimized typed/projection scans now
   use that boundary too. Datom rows now carry durable `log_index` values and
   SQLite can fetch an individual datom by log index, which is the next required
   primitive for resolving persisted index entries without rebuilding the whole
   DB. `SQLite-Index-Snapshot` packages the four persisted cursors plus datom
   lookup, and `SQLite-DB-Snapshot` adds basis/datom-count metadata as the first
   lazy-reopen DB snapshot object. It can now use persisted EAVT plus datom
   lookup to read one entity, and persisted AEVT plus datom lookup to read an
   attribute or entity+attribute, without resident datom/index arrays. Datom
   lookup is backed by a reusable prepared statement on the snapshot. The
   remaining work is to make normal reopened DB values construct persisted
   cursor-backed views where appropriate and decide how entity-position side
   tables are represented in shared/chunked snapshots.

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
