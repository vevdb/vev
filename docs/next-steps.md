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

- In-memory/native Vev performance remains strong.
- Persistent Vev is still slower than local Datomic on the current
  MusicBrainz data-query rows.
- The worst previous source-operator explosion has been reduced.
- The no-overlay direct result path and query-stats/relation path now share a
  generic batched multi-hop ref-chain operator for the supported forward-chain
  shape. It scans each relevant attr stream once and propagates values through
  maps instead of opening nested point streams for each intermediate entity.
- Source rule calls now recognize a generic reverse-derived two-hop shape,
  such as `[?middle :x ?left] [?right :y ?middle]`, and execute it as a typed
  source relation instead of falling back to generic clause-by-clause rule
  expansion.
- Source fulltext function clauses now extend typed relations directly for
  upstream shapes such as `[(fulltext $ :track/name ?q) [[?track ?name]]]`,
  preserving the same row-pattern semantics as the existing binding path while
  avoiding an explicit materialize-to-bindings round trip.
- Seeded non-recursive rule calls can now project rule outputs while carrying
  the seed/outer relation columns forward, avoiding a separate project-then-join
  step when the rule body relation already contains those columns.
- SQLite snapshot entity/attr point lookups now compare the indexed attr column
  directly while probing an entity range, instead of deserializing a full datom
  just to decide whether the attr matches. This is generic storage lookup work,
  not a query-specific optimization.
- Current no-Datomic comparison run has the direct Clojure MusicBrainz rows at
  roughly: title-by-artist 176 ms, title/album/year-by-artist 471 ms,
  pre-1970 title/album/year 171 ms, track-release rule 70 ms,
  track-search-info 898 ms, collaboration 88 ms, collaboration-net-2
  80 ms, nested collaboration 78 ms, and Bill Withers collaborations
  90 ms.
- Current local Datomic comparison matches row counts/fingerprints and reports
  Vev slower on the covered persistent rows: roughly 3.3x slower for
  title-by-artist, 11.1x for title/album/year, 21.0x for pre-1970
  title/album/year, 3.0x for track-release rule, 3.5x for track-search-info,
  7.3x/5.7x/11.2x for the collaboration rows, and 2.2x for Bill Withers
  collaborations.
- Focused query-stats checks for the current target rows are correct:
  title/album/year-by-artist 93 rows, pre-1970 title/album/year 18 rows,
  track-search-info 92 rows. The `track-search-info` path now uses 6 source
  operators, 7 source-index scans, 2 data clauses, and no binding
  materialization in the focused stats run.
- The remaining slow rows are now mostly about broader star/value joins,
  persisted page reuse, rule composition around fulltext-produced seed
  relations, and host/result materialization.

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

   Done:

   - add a generic batched/fused source operator for the no-overlay direct
     multi-hop ref/value-chain result path
   - route query-stats/request-map relation execution for the same supported
     forward-chain shape through that batched source representation
   - add a generic persisted-source operator for reverse-derived two-hop rules,
     covering rule shapes like MusicBrainz `track-release` without keying on
     rule or attribute names
   - keep fulltext rule function clauses in typed relation form for upstream
     source rules like `track-search`
   - carry seed/outer columns through bounded non-recursive rule projections
     instead of always projecting rule outputs and joining them back
   - avoid full datom deserialization in SQLite snapshot entity/attr attr-name
     probes

   Next useful engine work:

   - improve remaining rule composition after fulltext-produced seed
     relations, especially the `track-info` expansion after `track-search`,
     so downstream data clauses feed more batched operators and do less
     row-by-row persisted lookup work
   - add a sound generic operator for broader reverse component/ref chains and
     star-shaped joins without changing join cardinality
   - reuse loaded persisted pages across neighboring point/range operators
   - reduce host/result materialization cost for typed source-column results
   - keep all fixes keyed by query shape and indexes, not by attribute names or
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
