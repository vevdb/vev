# MusicBrainz / Day Of Datomic Workload

MusicBrainz/Day-of-Datomic is the next active Vev validation phase.

The goal is not to invent a new benchmark. The goal is to make existing
Datomic tutorial material work against Vev with minimal translation, then use
the same workload to compare correctness and performance against local Datomic.

## Current Starting Point

Vev is ready to start this phase because:

- the DataScript-shaped in-memory semantic surface is broad
- EDN text query, pull, rules, and transaction APIs exist for non-Kvist callers
- host wrappers can call through the C ABI
- SQLite-backed open/write/close/reopen/query works
- durable transaction metadata is persisted and inspectable
- the known durable write bottleneck is architectural and documented

The project should not block MusicBrainz on the shared-index storage rewrite.
The workload should instead tell us where that rewrite matters in practice.

## First Target

Start with a deterministic Day-of-Datomic / mbrainz-shaped dataset slice.

The first useful milestone is:

1. Import schema and seed data into an in-memory Vev connection.
2. Run a small set of tutorial queries against Vev.
3. Run the same queries against local Datomic.
4. Compare result sets before timing anything.
5. Repeat the same import/query path through the SQLite-backed Vev connection.

Correctness comes first. Performance comparisons become meaningful only after
the query and result shapes match.

## Work Items

1. Locate the local Day-of-Datomic or mbrainz dataset and document the exact
   source path, schema format, and data format used by the harness.
2. Add an importer that converts the dataset into Vev EDN transaction text or
   prepared `Tx-Data` values.
3. Add a small query fixture file containing Datomic tutorial queries, expected
   result normalization rules, and notes for any deliberate Vev differences.
4. Add a Vev harness that can run:
   - in-memory import and query
   - SQLite import, close/reopen, and query
   - optional Datomic comparison when the local Datomic process/database is
     available
5. Record comparisons as result equality plus relative timing ratios. Avoid
   unsupported raw timing claims until the harness has stable warmup and repeat
   behavior.

## Expected Pressure Points

This workload should exercise:

- schema attrs and idents
- refs and reverse refs
- lookup refs
- pull patterns
- aggregates
- multi-clause joins
- rules if present in the tutorial material
- host-facing EDN string APIs
- large in-memory index construction
- durable import and reopen behavior

If MusicBrainz exposes single-row durable write cost as the limiting factor,
resume the shared immutable/chunked DB index storage work from `docs/storage.md`.
If query time dominates instead, resume the physical operator/planner work from
`docs/roadmap.md` and `docs/benchmarks.md`.

## Non-Goals For This Phase

- full MusicBrainz production-scale ingestion before the small slice works
- query translation to SQL
- Datomic peer/client compatibility beyond tutorial-shaped API ergonomics
- solving the entire durable storage architecture before measuring this
  workload
