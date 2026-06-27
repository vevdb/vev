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

Initial local discovery did not find a checked-in full mbrainz/MusicBrainz dump
under `/Users/andreas/Projects` or the nearby home-directory project tree, so
the first harness starts with a deterministic mbrainz-shaped mini fixture.

The 1968-1973 sample backup is now locally restorable with
`scripts/musicbrainz_sample.sh`. It downloads and extracts the backup under the
ignored `build/musicbrainz` directory, starts the local Datomic Pro install at
`/Users/andreas/datomic/datomic-pro-1.0.7277`, and restores to:

```text
datomic:dev://localhost:4334/mbrainz-1968-1973
```

Local restore/smoke status:

- backup source: `https://s3.amazonaws.com/mbrainz/datomic-mbrainz-1968-1973-backup-2017-07-20.tar`
- local backup URI: `file://$repo/build/musicbrainz/mbrainz-1968-1973`
- restored Datomic basis t: `148253`
- `scripts/musicbrainz_sample.sh smoke-datomic` successfully reads datoms and
  artist names from the restored database

Primary upstream references:

- Datomic blog announcement:
  `https://blog.datomic.com/2013/07/datomic-musicbrainz-sample-database.html`
- sample project:
  `https://github.com/Datomic/mbrainz-sample`
- sample schema:
  `https://github.com/Datomic/mbrainz-sample/blob/master/schema.edn`
- sample query wiki:
  `https://github.com/Datomic/mbrainz-sample/wiki/Queries`
- current Amazing Day of Datomic workshop:
  `https://github.com/Datomic/day-of-datomic-conj`
- original Day of Datomic samples:
  `https://github.com/Datomic/day-of-datomic`

The 2013 blog references the original full backup
`http://s3.amazonaws.com/mbrainz/datomic-mbrainz-backup-20130611.tar`, about
2.8 GB, with a restore target like `datomic:free://localhost:4334/mbrainz`.
The current sample repo README points at the smaller 1968-1973 subset backup:
`https://s3.amazonaws.com/mbrainz/datomic-mbrainz-1968-1973-backup-2017-07-20.tar`.
That subset should be the first real Datomic comparison target because it is
large enough to be meaningful but much easier to restore and iterate on.

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

Status: first mini-fixture harness exists in
`src/vev_tests/musicbrainz_test.kvist`. It imports schema and seed data via EDN
text, runs tutorial-shaped queries against an in-memory DB, and repeats the
same assertions after SQLite-backed commit, close, reopen, and query. The mini
fixture now uses real mbrainz-shaped attrs such as `:release/media`,
`:medium/tracks`, `:track/artists`, and `:artist/startYear`, plus a small
subset of the upstream `rules.edn` shape:

- `track-release`
- `track-info`
- `short-track`

Current covered query shapes include direct joins, reverse refs, predicates,
aggregates, prepared EDN text input, collection binding, tuple binding,
relation binding, scalar/tuple/collection find specs, rule-backed queries,
duration function expressions, return maps, enum refs through `:db/ident`,
`get-else`, statistics aggregates, and nested pull through
release/media/tracks. It also covers wildcard pull, map-form EDN queries,
split/composed rules, `not`/`not-join`, `or`/`or-join`, `get-some`, dynamic
pull pattern inputs, `missing?`, lookup-ref inputs, dynamic attr inputs, top-n
aggregates, and the Day-of-Datomic query-stats final John Lennon pre-1970
tracks example. Restored `get-some` rows now match Datomic's attr-entity
semantics by projecting the attr through `:db/ident`. The detailed coverage ledger is
`docs/musicbrainz-query-matrix.md`.

The restored Datomic comparison matrix now also covers release date
projection, `get-else`, restored `get-some`, `:keys` return-map rows, dynamic
pull pattern input, `missing?`, dynamic attr input, query-stats tutorial
traversal, and top-n aggregate rows against the real 1968-1973 sample.

The restored sample forced one real Vev data-model addition: UUID values.
MusicBrainz GID attrs such as `:artist/gid` and `:release/gid` use
`:db.type/uuid`, so Vev now parses EDN UUID literals as a distinct primitive
value kind instead of stringifying them during import.

The restored sample also forced a real import-shape decision: Datomic entity
ids are larger than Vev's current explicit entity-id range. The current
MusicBrainz exporter remaps Datomic eids into a compact Vev id space while
preserving all ref values through the same remap table. This is appropriate for
sample/database import tooling; Vev-native databases still allocate their own
ids.

`scripts/export_mbrainz_subset.clj` and `scripts/musicbrainz_sample.sh` now
export Vev-compatible EDN transaction files from the restored Datomic sample.
The exporter can write either one transaction file or staged files:

```bash
scripts/musicbrainz_sample.sh export-subset build/musicbrainz/vev-mbrainz-subset.edn
scripts/musicbrainz_sample.sh export-subset-split build/musicbrainz/vev-mbrainz-subset-5000 5000
```

The full current subset export writes 763,274 transaction items, about 41 MB,
from restored Datomic basis t `148253`. The staged export writes schema/ident
facts separately from value facts so Vev can transact schema first and bulk
values second.

`bench/musicbrainz_import_subset.kvist` is the current Vev import smoke. It can
load either a single EDN tx file or staged schema/value files:

```bash
cd /Users/andreas/Projects/kvist
./kvist build /Users/andreas/Projects/vev/bench/musicbrainz_import_subset.kvist \
  --out /Users/andreas/Projects/vev/build/bench/musicbrainz_import_subset

/Users/andreas/Projects/vev/build/bench/musicbrainz_import_subset \
  --schema /Users/andreas/Projects/vev/build/musicbrainz/vev-mbrainz-subset-5000-schema.edn \
  --values /Users/andreas/Projects/vev/build/musicbrainz/vev-mbrainz-subset-5000-values.edn
```

Current status:

- 100-item compact-id single-file import passes.
- 500-value staged import passes.
- 5,000-value staged import passes and queries successfully.
- 50k, 100k, 200k, 400k, and the current full chunked staged import run locally
  and preserve the expected tutorial query results for the sampled slice.
- EDN parse time is already small for these slices; transaction/index
  publication dominates.

The important finding is functional rather than cosmetic: MusicBrainz import is
now correct for a real restored Datomic-derived slice. Full chunked import uses
separate schema/value files and releases each parsed value chunk after applying
it. A parsed-string lifetime bug was fixed by making DB log datoms own their
attribute/value payloads; chunked imports no longer depend on input file buffers
remaining alive. The remaining import work is reducing whole-array DB/index
publication costs as database values grow.

The mini fixture also exercises Vev query profiling for MusicBrainz-shaped
joins. `src/vev_tests/musicbrainz_test.kvist` asserts that profiled EDN and
prepared EDN queries return the expected rows and non-empty planner/profile
statistics. `bench/musicbrainz_query_profile.kvist` is the small standalone
runner for clause-order timings and profile counters:

```bash
cd /Users/andreas/Projects/kvist
./kvist run /Users/andreas/Projects/vev/bench/musicbrainz_query_profile.kvist
```

The same runner now also accepts `--dataset real` plus staged schema/value
paths. It imports the restored exported subset, runs the same clause-order,
rule, aggregate, not/or, relation-input, pull, nested pull, and direct
lookup-ref pull shapes, and prints row counts, portable result fingerprints,
timing samples, step count, clause count, candidate count, maximum intermediate
bindings, and output rows. `--print-rows true` prints sorted projected row keys
for direct `diff` against Datomic.

Local Datomic comparison is available through:

```bash
scripts/musicbrainz_sample.sh query-matrix-datomic --samples 2 --warmups 1
```

The first real comparison rows are now equal against Datomic:

- `musicbrainz-real-release-first`: 96 rows,
  fingerprint `0ea8943f9ef3eb03`
- `musicbrainz-real-track-first`: 89 rows,
  fingerprint `9902d35f51335e40`

Current timings show Vev is now fast on these ordinary multi-hop
clause/predicate joins after dependency-aware clause planning. The imported Vev
subset returns the same projected rows as Datomic for these tutorial shapes.
Pure rule-expanded joins made from data clauses and rule calls now use a
dependency-aware rule-body planner. Bounded `or`/`or-join` and
`not`/`not-join` now reuse the planned group-clause path, so the current
real-data matrix no longer exposes a clear slow query-planner outlier.

## Work Items

1. Build a Datomic-to-Vev export/import path for the restored 1968-1973 sample.
   Status: first exporter/import smoke exists. Next work is making staged
   5k/50k/full imports fast enough to use routinely.
2. Port the `day-of-datomic-conj/src/music_brainz.clj` query set into a Vev
   fixture file, marking each form as passing, Vev-difference, or pending.
   Track this in `docs/musicbrainz-query-matrix.md`.
3. Expand the mini fixture only when it exposes a missing semantic shape; the
   restored sample is now the main correctness/performance target.
4. Add an importer that converts the Datomic dataset into Vev EDN transaction
   text or prepared `Tx-Data` values.
5. Add a small query fixture file containing Datomic tutorial queries, expected
   result normalization rules, and notes for any deliberate Vev differences.
6. Add a Vev harness that can run:
   - in-memory import and query
   - SQLite import, close/reopen, and query
   - optional Datomic comparison when the local Datomic process/database is
     available
   Status: in-memory real import/query and Datomic comparison exist for the
   first two clause-order joins.
7. Record comparisons as result equality plus relative timing ratios. Avoid
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
