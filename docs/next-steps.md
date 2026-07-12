# Next Steps

This is the current Vev execution plan. Keep it short and current; do not use
this file as a changelog.

## Active Gate

Make Vev good enough to follow the MusicBrainz Datomic workshop in both
Clojure and Kvist against a persistent local Vev store.

Work only from upstream sources checked out under ignored `build/upstream`.
Do not invent tutorial examples.

Current upstream target:

- file:
  `build/upstream/mbrainz-sample/examples/clj/datomic/samples/mbrainz.clj`
- section: `data queries`
- status: all executable forms in this file are now represented in the Vev
  Clojure and Kvist workshop paths
- current task: productize and improve the covered persistent comparison path,
  not port more invented examples

Other upstream coverage already in this gate:

- `build/upstream/day-of-datomic-conj/src/music_brainz.clj`
- `build/upstream/day-of-datomic/tutorial/query.clj`
- `build/upstream/day-of-datomic/tutorial/pull.clj`
- `build/upstream/day-of-datomic/tutorial/aggregates.clj`
- `build/upstream/day-of-datomic/tutorial/decomposing_a_query.clj`
- `build/upstream/mbrainz-sample/schema.edn`
- `build/upstream/mbrainz-sample/resources/rules.edn`

## Hard Constraints

- In-memory mode must stay first-class and must not require SQLite.
- Durable mode may require system SQLite, but SQLite details should only appear
  at database creation/opening and operational setup boundaries.
- Public APIs should feel Datomic/DataScript-like unless Vev has a clear reason
  to differ. Clojure and Kvist query examples should use query-first order:
  `(d/q query db & inputs)` and equivalent Kvist forms.
- Clojure parity is not enough. The same MusicBrainz tutorial path must work
  cleanly from Kvist using Vev's native package and literal/query conveniences.
- Non-Kvist consumers are primary. EDN text APIs, native ABI handles, and host
  wrappers are product surfaces.
- Performance work must be generic Datalog/storage engine work, not hard-coded
  benchmark or MusicBrainz query handling.

## Current Status

Vev can run the current MusicBrainz workshop slice from both Clojure and Kvist
against the same persistent local store.

Covered behavior:

- MusicBrainz query, pull, aggregate, rules, relation input, negation,
  disjunction, function expression, fulltext, missing?, transaction-log,
  dynamic attribute, and decomposing-query tutorial slices.
- Clojure `vev.core` uses a Datomic-like API shape for the covered tutorial.
- Kvist uses native Vev package calls and has query-first literal Datalog forms
  for many covered sections. Some remaining Kvist examples intentionally still
  use EDN strings where they exercise the interop/parser surface or where a
  clean native pull-pattern API is still pending.
- Persistent store close/reopen/query works for the covered tutorial path.
- `scripts/compare_musicbrainz_workshop.sh` compares Clojure Vev, Kvist Vev,
  and local Datomic for the covered `mbrainz-sample` rows by row count and
  portable fingerprint.

Latest important correctness status:

- Clojure Vev and Kvist Vev pass the covered workshop validation.
- Local Datomic comparison passes row counts and portable fingerprints for the
  covered `mbrainz-sample` data-query workloads.

Latest important performance status:

- In-memory/native Vev performance remains strong. The active gap is
  persistent MusicBrainz query throughput through the Clojure/ABI path.
- The comparison harness supports focused `--workload`, `--query-stats`,
  `--warmup-runs`, and `--measure-runs`; use it for every performance claim.
- `mbrainz-title-album-year-by-artist` is correct in Clojure, Kvist, and
  Datomic comparison: 93 rows, fingerprint `d2548ca97497433f`. Current Vev
  stats: 7 source operators, 325 source-index scans, 449 clause candidates,
  no binding materialization. Warmed Vev Clojure is about 31-33ms versus local
  Datomic about 3.8-4.7ms.
- `mbrainz-pre-1970-title-album-year` is correct in Clojure, Kvist, and
  Datomic comparison: 18 rows, fingerprint `4598839c2af58631`. Current Vev
  stats: 7 source operators, 304 source-index scans, 428 clause candidates,
  no binding materialization. Warmed Vev Clojure is about 27-28ms versus local
  Datomic about 3.4-5.6ms.
- The remaining bottleneck for these rows is not join cardinality; it is the
  cost of many persisted SQLite EAVT/VAET point streams over sparse sorted
  frontiers. The next storage primitive must batch exact `[entity attr]`
  frontier reads without scanning a whole min/max span, collecting all datoms
  before yielding, or multiplying `prefix-count * run-count` binary searches.
- A sparse-prefix stream that schedules prefixes by loading every child run's
  first/last datoms is also the wrong physical shape for the current
  MusicBrainz store. It proves the rows can be reduced to 18-93 real
  candidates, but it spends too much time in pre-yield storage work. The next
  attempt needs a persisted prefix/run directory or a true page-local cursor
  that can find relevant runs without walking all run bounds first.
- Do not route these rows through row-by-row cardinality-one point lookups,
  broad attr scans, or the inactive sparse-prefix scaffold unless focused
  measurement proves the generic path improves.

## Exit Criteria

This gate is done when:

- a user can create or refresh the persistent MusicBrainz Vev store with one
  documented command
- a user can run the Clojure MusicBrainz tutorial validation with one
  documented command
- a user can run the Kvist MusicBrainz tutorial validation with one documented
  command
- Clojure and Kvist tutorial examples are close enough to Datomic tutorial style
  that Vev internals are not needed to understand them
- Vev and local Datomic match row counts and portable fingerprints for the
  covered tutorial/workshop queries
- persistent Vev is at least close to local Datomic on the covered local
  workload, or each slower row has a concrete engine/storage explanation and
  planned fix
- setup docs clearly state native library and system SQLite requirements

## Current Verification Commands

- `scripts/musicbrainz_workshop_clojure.sh`
- `scripts/musicbrainz_workshop_kvist.sh`
- `scripts/compare_musicbrainz_workshop.sh --skip-datomic`
- `scripts/compare_musicbrainz_workshop.sh --workload <name-or-suffix>`
- `scripts/compare_musicbrainz_workshop.sh --workload <name-or-suffix> --query-stats`
- `scripts/compare_musicbrainz_workshop.sh` when local Datomic is available
- `scripts/aggregates_tutorial_clojure.sh`
- `scripts/aggregates_tutorial_kvist.sh`
- `scripts/compare_aggregates_tutorial.sh`
- `scripts/decomposing_query_clojure.sh`
- `scripts/decomposing_query_kvist.sh`

## Remaining Work

1. **Close the persistent source-operator performance gap**

   Current target rows:

   - `mbrainz-title-album-year-by-artist`
   - `mbrainz-pre-1970-title-album-year`
   - `mbrainz-track-release-rule`
   - `mbrainz-track-search-info`
   - recursive collaboration rows if they regress relative to Datomic

   Next useful engine work:

   - implement a merge/manifest-aware cursor for exact EAVT `[entity attr]`
     and VAET `[entity attr]` frontier reads
   - the cursor must accept sorted prefixes, skip impossible runs by stored
     bounds when available, keep per-run/page state, advance prefix and page
     incrementally, and emit matching datoms lazily
   - the cursor must not scan the whole min/max entity span, collect all
     frontier datoms up front, or do repeated `prefix-count * run-count` binary
     searches
   - the cursor must not load every child run's first/last datoms before
     yielding; it needs stored prefix/range metadata or another bounded way to
     jump to candidate runs
   - success means both `mbrainz-title-album-year-by-artist` and
     `mbrainz-pre-1970-title-album-year` still match Datomic row counts and
     fingerprints, while warmed Vev Clojure moves materially below the current
     27-33ms range
   - after that, recheck `mbrainz-track-release-rule` and
     `mbrainz-track-search-info`; only then decide whether broader star/value
     joins or host/result materialization are the next blocker
   - keep all fixes keyed by query shape and indexes, never attribute names or
     exact query strings

2. **Keep Clojure and Kvist workshop paths aligned**

   Required:

   - when a covered Clojure tutorial section has a Kvist counterpart, keep both
     current
   - migrate remaining Kvist EDN-string examples to literal Datalog only when
     the native DSL supports the exact upstream shape cleanly
   - do not invent Kvist-only examples to paper over missing Datomic-style
     behavior

3. **Productize setup docs**

   Required:

   - document the MusicBrainz dataset/subset used by the local persistent store
   - document system SQLite and native library requirements
   - document one command to create/refresh the store
   - document one command each for Clojure validation, Kvist validation, and
     Datomic comparison
   - keep generated stores and build artifacts out of git

4. **Keep comparison harness normal and repeatable**

   Required:

   - keep `scripts/compare_musicbrainz_workshop.sh` as the normal regression
     command for this upstream section
   - when a mismatch appears, fix engine/API behavior rather than changing
     upstream query shapes
   - report correctness separately from performance ratios

## Later

Important, but not the active gate:

- broader host wrapper polish outside Clojure/Kvist
- package publication polish: Maven/Clojars/PyPI/crates/packages
- durable storage architecture beyond what blocks the MusicBrainz persistent
  tutorial gate
