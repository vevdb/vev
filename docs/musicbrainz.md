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
under `/Users/andreas/Projects` or the nearby home-directory project tree. The
current harness therefore starts with a deterministic mbrainz-shaped mini
fixture and leaves the full dataset path as the next discovery/import step.

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
release/media/tracks. The detailed coverage ledger is
`docs/musicbrainz-query-matrix.md`.

## Work Items

1. Download or locate the 1968-1973 mbrainz backup from the sample repo README,
   restore it into local Datomic, and document the exact local path/URI.
2. Port the `day-of-datomic-conj/src/music_brainz.clj` query set into a Vev
   fixture file, marking each form as passing, Vev-difference, or pending.
   Track this in `docs/musicbrainz-query-matrix.md`.
3. Expand the mini fixture toward that query set while the full dataset path is
   being located.
4. Add an importer that converts the dataset into Vev EDN transaction text or
   prepared `Tx-Data` values.
5. Add a small query fixture file containing Datomic tutorial queries, expected
   result normalization rules, and notes for any deliberate Vev differences.
6. Add a Vev harness that can run:
   - in-memory import and query
   - SQLite import, close/reopen, and query
   - optional Datomic comparison when the local Datomic process/database is
     available
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
