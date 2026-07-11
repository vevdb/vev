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
- The comparison harness now supports `--workload <name-or-suffix>` so a single
  upstream-derived MusicBrainz row can be validated through Kvist Vev and
  compared through Clojure Vev and Datomic without running the full matrix.
- The comparison harness now also supports `--query-stats`, which reruns the
  selected Clojure Vev workload through `vev/query` with query stats enabled
  and prints the same row count/fingerprint plus engine counters.
- The comparison harness now supports `--warmup-runs N` and
  `--measure-runs N`, reporting median measured time plus best/worst when more
  than one run is measured. Use this for performance comparisons; single cold
  first-use timings are still useful for startup work, but they should not be
  treated as steady query throughput.
- Current focused Datomic comparison for
  `mbrainz-title-album-year-by-artist` matches row count and fingerprint
  (93 rows, `d2548ca97497433f`). Kvist Vev validates the same selected
  section.
- The focused stats run for `mbrainz-title-album-year-by-artist` now uses a
  generic no-filter reverse-chain source operator for this upstream data query
  shape: 7 source operators, 325 bounded source-index streams, and 449 clause
  candidates for 93 output rows, with no binding materialization. This replaces
  the previous fallback path with 155 source operators and the first broad-scan
  operator version with 223026 clause candidates.
- The warmed steady comparison for `mbrainz-title-album-year-by-artist` is
  correct but still too slow: with one warmup and five measured runs, local
  Datomic measured about 4.3ms median and Vev Clojure about 31.3ms median.
  The remaining bottleneck is persisted point-stream/page overhead, not join
  cardinality.
- Replacing the bounded value phases of this row with schema-cardinality-one
  per-entity point lookups is the wrong physical direction for the persistent
  store today: it keeps correctness but is much slower than the source-stream
  path. Keep this row on source streams until persisted page reuse/batching is
  improved.
- Current focused Datomic comparison for
  `mbrainz-pre-1970-title-album-year` matches row count and fingerprint
  (18 rows, `4598839c2af58631`). Kvist Vev validates the same selected
  section.
- The focused stats run for `mbrainz-pre-1970-title-album-year` now uses the
  generic ordered anchored reverse-chain source operator for this upstream data
  query shape: 7 source operators/scans and 215078 clause candidates for 18
  output rows, with no binding materialization. This replaces the previous 379
  source operators/scans on the same row.
- The first plain Clojure `d/q` call on
  `mbrainz-pre-1970-title-album-year` is a cold first-use cost, not steady
  query throughput: repeated measurement shows the first Vev call around
  1.23s, then repeated plain `d/q` calls around 55-59ms in the same process.
- The warmed steady comparison for `mbrainz-pre-1970-title-album-year` is
  improved but still not good enough: with one warmup and five measured runs,
  local Datomic measured about 3.6ms median, Vev Clojure about 26.4ms median,
  and Vev stats/profiled about 57.7ms. Correctness matches, but this row still
  needs generic source/storage throughput work.
- Focused query-stats checks for the current target rows are correct:
  title/album/year-by-artist 93 rows, pre-1970 title/album/year 18 rows,
  track-search-info 92 rows. The `track-search-info` path now uses 6 source
  operators, 7 source-index scans, 2 data clauses, and no binding
  materialization in the focused stats run.
- The remaining slow rows are now mostly about broader star/value joins,
  persisted page reuse, durable value/materialization reads, and host/result
  materialization.

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

   Done:

   - add focused `--workload` filtering to the MusicBrainz workshop comparison
     harness so slow upstream rows can be checked directly against Kvist Vev,
     Clojure Vev, and Datomic
   - add focused `--query-stats` reporting to the same comparison harness so
     correctness, Datomic ratio, and Vev engine counters are available from one
     selected upstream row
   - add focused warmup/repeated measurement options to the same comparison
     harness so cold first-use cost is separated from steady query throughput
   - add a generic batched/fused source operator for the no-overlay direct
     multi-hop ref/value-chain result path
   - route query-stats/request-map relation execution for the same supported
     forward-chain shape through that batched source representation
   - add a generic persisted-source operator for reverse-derived two-hop rules,
     covering rule shapes like MusicBrainz `track-release` without keying on
     rule or attribute names
   - keep fulltext rule function clauses in typed relation form for upstream
     source rules like `track-search`
   - prefilter durable SQLite snapshot fulltext candidates with a generic
     attr/string-contains storage query, then apply Vev's normal fulltext
     matching/scoring
   - carry seed/outer columns through bounded non-recursive rule projections
     instead of always projecting rule outputs and joining them back
   - avoid full datom deserialization in SQLite snapshot entity/attr attr-name
     probes
   - add a generic ordered anchored reverse-chain source operator for upstream
     shapes like `mbrainz-pre-1970-title-album-year`, where a bound anchor
     reaches a first entity through a reverse ref, carries a value on that
     first entity, follows reverse ref hops to a final entity, applies a range
     predicate on the final entity, and projects another final-entity value
   - add a generic Clojure typed-column result conversion path for
     `string,string,int` triples and route non-prepared relation `q` calls
     through set-shaped typed-column output instead of row materialization plus
     post-conversion
   - batch the final projected value read inside the ordered anchored
     reverse-chain source operator by carrying filtered final-entity links and
     scanning the final-value attribute once, rather than performing a
     persisted entity/attr lookup for each filtered link
   - add a generic no-filter two-final-value reverse-chain source operator for
     shapes like `mbrainz-title-album-year-by-artist`
   - make that operator drive reverse refs and final value lookups from the
     bounded reachable frontier, reducing the focused row from 223026 broad
     scan candidates to 449 real candidates
   - skip persisted current-check work inside cardinality-one value lookup for
     SQLite snapshots with no retractions; this is a generic lookup cleanup,
     but it is not enough to make per-entity point lookups the right plan for
     the current MusicBrainz row

   Next useful engine work:

   - reduce persisted point-stream overhead for bounded reverse-chain
     operators; `mbrainz-title-album-year-by-artist` now has the right
     cardinality but still performs 325 small SQLite-backed index streams
   - add reusable persisted page/range batching for the bounded stream path;
     do not replace these source streams with row-by-row cardinality-one point
     lookups unless measurement shows it wins for persistent stores
   - reduce warmed steady runtime for `mbrainz-pre-1970-title-album-year`;
     after the typed-column host materialization fix and final-value batching,
     this is now about 26.4ms
     median for Vev Clojure versus about 3.6ms median for local Datomic with
     the same warmup/repeat settings
   - improve the generic relation planner for broader reverse component/ref
     chains and star-shaped joins without changing join cardinality
   - batch durable value-column reads for broad value/filter joins such as the
     pre-1970 rows, instead of repeated row-by-row persisted lookups
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
