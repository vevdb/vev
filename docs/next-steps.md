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
- current task: improve the covered persistent comparison path for the
  rule-backed data-query rows, not port more invented examples

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
  Prepared Clojure queries now retain their original query form, so
  Datomic-style `%` rule inputs work for prepared `d/q`, `rows`, and `scalar`
  calls instead of only for one-shot query forms.
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
  `--prepared-vev`, `--warmup-runs`, and `--measure-runs`; use it for every
  performance claim. Run focused SQLite-backed comparisons sequentially, not in
  parallel, because concurrent scripts can contend for the same local store.
- `mbrainz-title-album-year-by-artist` is correct in Clojure, Kvist, and
  Datomic comparison: 93 rows, fingerprint `d2548ca97497433f`. Current Vev
  stats after deeper ref-chain frontier activation: 7 source operators, 7
  source-index scans, 449 clause candidates, no binding materialization. Warmed
  Vev Clojure is about 7.9ms on a focused Datomic-inclusive run; local Datomic
  was about 4.8ms on the same run.
- `mbrainz-pre-1970-title-album-year` is correct in Clojure, Kvist, and
  Datomic comparison: 18 rows, fingerprint `4598839c2af58631`. Current Vev
  stats after exact-prefix frontier activation: 7 source operators, 7
  source-index scans, 428 clause candidates, no binding materialization. Warmed
  Vev Clojure is about 6.7ms on a focused skip-Datomic run; local Datomic was
  previously about 3.4-5.6ms.
- `mbrainz-track-release-rule` is correct in Clojure, Kvist, and Datomic
  comparison: 93 rows, fingerprint `d2548ca97497433f`. Current Vev stats:
  7 source-index scans, 6 source operators, 501 clause candidates, 1 rule call,
  1 rule iteration, no binding materialization. Current focused comparison
  after typed VAET direct-frontier payloads:
  warmed Vev Clojure median about 10.4ms, local Datomic median about 6.0ms,
  ratio about 1.7x. Prepared Vev timing is effectively the same as one-shot
  `d/q`, so query parsing/preparation is not the main remaining cost. The
  ratio moves with local Datomic variance, but the Vev absolute time is still
  roughly 10ms.
- `mbrainz-track-search-info` is correct in Clojure, Kvist, and Datomic
  comparison:
  92 rows, fingerprint `1d14f508a95941fd`. Current Vev stats: 7 source-index
  scans, 6 source operators, 478 clause candidates, 2 rule calls, 2 rule
  iterations, no binding materialization. Current focused comparison after
  removing the storage text round-trip for persistent fulltext candidates,
  tightening seeded typed rule projection, and reading VAET direct-frontier
  entity values from typed `value_entity` storage:
  warmed Vev Clojure is roughly 35-38ms in focused runs, local Datomic is
  roughly 8-10ms, ratio about 4x. Prepared Vev timing is effectively the same
  as one-shot `d/q`, so query parsing/preparation is not the main remaining
  cost. The scalar-input `track-search` rule can inline before planning; the
  remaining time is in the bound `track-info` / `track-release` rule path, not
  in query parsing, value parsing, or ordinary host result delivery.
- Rule-backed persistent queries can now use the native column-batch path
  through ABI/Java/Clojure when the native batch builder can represent the
  result shape. The active three-column `mbrainz-track-release-rule` row
  verifies this path directly: `d/columns` returns 93 rows with
  `[:string :string :int]`. The four-column `mbrainz-track-search-info` row
  also verifies this path directly: `d/columns` returns 92 rows with
  `[:string :string :string :int]`. Clojure can now render generic flat column
  batches into ordinary rows/sets instead of rerunning optimized shapes through
  the generic result handle. This closes part of the host API/result-
  materialization gap,
  but it does not materially improve the current rule-backed MusicBrainz
  timings. The direct ABI column-batch path is now useful product/API work, but
  it is not the main remaining bottleneck for these rows; that is still
  physical rule/source planning and indexed rule-body execution.
- Prepared Clojure rule queries are now supported and covered by the Clojure
  smoke test. The comparison harness can now time prepared Vev queries directly
  with `--prepared-vev`, which confirmed that preparation is not the active
  bottleneck for the rule-backed MusicBrainz rows.
- Simple non-recursive rule calls can be inlined before physical source
  planning when they are uncorrelated with variables already bound at that
  point in the query. Correlated rule calls stay on the rule engine because
  blind inlining turns reusable rule evaluation into repeated per-row source
  scans. Scalar `:in` variables are not treated as already-bound correlation
  variables for this guard, so top-level rules like `(track-search ?search
  ?track)` can inline while truly correlated calls still use the rule engine.
  This is a generic planning guard, not a MusicBrainz special case.
- Bound rule seed construction now stays in typed relation/column form instead
  of converting the seed row through named `Binding` maps and back into typed
  columns. The common alias-only case clones/reuses typed columns directly; the
  fallback still handles value args, equality checks, wildcards, and rejected
  lookup-ref args. This is a generic rule engine improvement, not a
  MusicBrainz-specific branch. The passthrough branch now returns that already
  unique typed relation directly instead of running a second full-row dedupe.
- Left-bound two-hop reverse-derived rule calls can now use batched VAET
  frontier scans over the full bound entity list before falling back to the
  row-by-row stream path. This follows the Datalevin reverse-ref join
  principle and is generic by rule shape, but it did not materially improve the
  current `track-release` / `track-info` timings by itself.
- The direct SQLite exact-prefix lookup primitive is now active in eligible
  persistent source frontier operators:
  - source must be a current SQLite snapshot
  - there must be no retractions in the snapshot
  - order must be EAVT `(entity, attr)` or VAET `(target-entity, attr)`
  - otherwise the engine falls back to the older source stream path
- The active direct frontier operator now asks SQLite for the datom payloads
  directly instead of reading matching log indexes and then doing a second
  batch lookup by log index. Direct frontier prefixes are normalized to a
  unique sorted list before SQL execution, matching the fallback stream's set
  semantics and avoiding duplicate prefix work when the bound input repeats.
  Single-prefix direct frontier scans use fixed indexed SQLite statements
  instead of constructing a dynamic `WITH wanted` query. Owned-datom streams
  also borrow the current datom from their owned array instead of cloning each
  current datom. This is generic source/stream work and is covered by focused
  storage and source tests. Prefix normalization and fixed single-prefix SQL
  are useful storage cleanups but did not materially change the active
  MusicBrainz candidate counts, so they are not the main remaining performance
  fix.
- Persistent datoms now maintain a typed `value_entity` lane for entity-ref
  values, with a migration/backfill for older stores. VAET direct frontiers use
  `(value_entity, attr)` instead of formatting entity refs into `value_text`.
  This follows the Datalevin typed-index principle and is generic durable
  storage work, not MusicBrainz query-name handling. The schema bootstrap
  deliberately creates the `value_entity` index only after the migration has
  ensured the column, so older stores can open and upgrade in place.
- Persistent fulltext candidates now come back from SQLite as typed datom parts
  instead of a serialized DB-text payload that Vev reparses. This follows the
  same storage-parts pattern as direct frontiers and removes a real generic
  round-trip, but focused measurement shows it is only a small win for
  `mbrainz-track-search-info`. The remaining fix is still a better rule/source
  physical plan, not more host result polishing.
- Persistent VAET direct-frontier candidates now come back with typed
  `value_entity` payloads instead of reparsing `[:vev/entity ...]` text into
  entity values. This removes another generic storage parse from reverse-ref
  rule walks and is covered by the direct-frontier storage test, but focused
  measurement shows it is not the active MusicBrainz bottleneck.
- This is generic source-operator work, not query-name or attribute-name
  special casing. Focused storage and source tests cover the primitive and the
  value/ref/star source-backed relation path; the MusicBrainz rows above verify
  the same path against real persistent data.
- Datalevin's useful implementation hint is its nested/indexed list shape:
  query execution can ask storage for cheap prefix ranges and counts, then
  merge-scan star-like attributes. Vev should copy that principle, adapted to
  SQLite-backed immutable index chunks, rather than keep layering more point
  lookups over the current manifest.
- Datalevin's useful rule-engine hint is to use bound propagation and indexed
  rule-body walks rather than materializing broad rule relations or invoking a
  rule once per input row. For Vev, that means improving physical rule planning
  over persistent sources, not adding query-name branches.
- A Datalevin-style broad non-recursive rule expansion was tested and rejected
  for the current Vev persistent planner shape: it kept results correct but
  expanded `mbrainz-track-release-rule` to about 155 source operators/scans and
  roughly 88ms. Keep the correlated-call guard until Vev has SIP/prefix
  metadata or an equivalent indexed rule-body walk that can push bound values
  into storage without repeating source scans.
- The existing Vev run manifest has run bounds and per-run attr ranges. That is
  enough for broad attr scans, but not for sparse exact prefixes like
  EAVT `[artist :artist/name]` or VAET `[release :medium/release]`. The next
  manifest level needs persisted exact-prefix metadata for EAVT/VAET entity-ref
  prefixes, or an equivalent page-local directory, so source operators can jump
  directly to matching pages/runs.
- A sparse-prefix stream that schedules prefixes by loading every child run's
  first/last datoms is the wrong physical shape for the current MusicBrainz
  store. It proves the rows can be reduced to 18-93 real candidates, but it
  spends too much time in pre-yield storage work. Do not revive that route
  unless it is backed by persisted prefix/range metadata or a bounded page-local
  jump primitive.
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
- `scripts/compare_musicbrainz_workshop.sh --workload <name-or-suffix> --prepared-vev`
- `scripts/compare_musicbrainz_workshop.sh` when local Datomic is available
- `scripts/compare_musicbrainz_target_rows.sh` for the current focused
  persistent-performance rows against Vev and local Datomic
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

   - run `scripts/compare_musicbrainz_target_rows.sh` after each
     storage/operator change; it reports row count, fingerprint, Vev time,
     Datomic time, ratio, and Vev stats for the target rows
   - continue improving bound/correlated rule-call execution for persistent
     sources without losing the compact source stats above; the seed path is
     typed now, source frontier payload reads avoid the extra log-index round
     trip, VAET frontier entity payloads avoid text reparsing, and prepared
     Clojure rules are supported, so the remaining likely directions are
     lower-overhead native rule invocation, rule-result reuse within a query,
     and physical rule/operator planning that preserves indexed source scans
     without expanding into per-row scans
   - continue direct typed relation output from physical rule plans, but do not
     treat host column batching as the main remaining blocker for the active
     rows; rule-backed prepared column batches now work and the focused
     timings still point to rule/source planning
   - add a persisted exact-prefix directory for durable EAVT/VAET manifest
     runs, or an equivalent page-local directory:
     - EAVT key: `(entity, attr) -> run/root, low, high, count`
     - VAET key for entity refs: `(target-entity, attr) -> run/root, low, high,
       count`
   - build that directory when manifest runs are created or maintained; old
     stores may fall back to the current path until refreshed
   - use the directory from source frontier operators before falling back to
     dense span scans or ordinary per-prefix bounds
   - the read cursor must accept sorted prefixes, keep per-run/page state,
     advance prefix and page incrementally, and emit matching datoms lazily
   - the cursor must not scan the whole min/max entity span, collect all
     frontier datoms up front, do repeated `prefix-count * run-count` binary
     searches, or load every child run's first/last datoms before yielding
   - after persisted exact-prefix frontiers work, extend the same physical shape toward
     Datalevin-style star/merge scans so one entity frontier can retrieve
     several attributes from the same EAVT pages without row/binding
     materialization
   - success means covered MusicBrainz rows still match Datomic row counts and
     fingerprints, while warmed Vev Clojure stays close to local Datomic on the
     same machine
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
