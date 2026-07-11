# Next Steps

This is the current Vev execution plan. Keep it short and current; do not use
this file as a changelog.

## Active Gate

Make Vev good enough to follow the MusicBrainz Datomic workshop in Clojure and
Kvist against a persistent local Vev store.

The next phase is concrete and user-facing: take the Day of Datomic /
MusicBrainz tutorial style, run it through Vev from Clojure and Kvist, persist
the data, reopen it, and compare correctness and performance against local
Datomic. Work that does not move that scenario forward should wait.

Do not invent tutorial coverage from memory. Port from the upstream source
files that are checked out locally under ignored `build/upstream`:

- `build/upstream/day-of-datomic-conj/src/music_brainz.clj`
  at `cf1e260cff0aa582fe2ae17bb1fcfaeebb139f80`
- `build/upstream/day-of-datomic/tutorial/query.clj`
  at `daa457f766e16f55243a95513e759573b8827329`
- `build/upstream/day-of-datomic/tutorial/pull.clj`
  at `daa457f766e16f55243a95513e759573b8827329`
- `build/upstream/day-of-datomic/tutorial/aggregates.clj` and
  `build/upstream/day-of-datomic/resources/day-of-datomic/bigger-than-pluto.edn`
  at `daa457f766e16f55243a95513e759573b8827329`
- `build/upstream/mbrainz-sample/schema.edn`,
  `build/upstream/mbrainz-sample/resources/rules.edn`, and
  `build/upstream/mbrainz-sample/examples/clj/datomic/samples/mbrainz.clj`
  at `a7c0aab6828cfa09d5ff3c6075579673377b4a43`

Hard constraints:

- in-memory mode must stay first-class and must not require SQLite
- durable mode may require system SQLite, but SQLite details should only appear
  at database creation/opening and operational setup boundaries
- public APIs should feel Datomic/DataScript-like unless Vev has a clear reason
  to differ; Clojure query examples should use the familiar
  `(d/q query db & inputs)` order, and Kvist query examples should use the
  same query-first order
- the Clojure API being close to Datomic is not enough by itself: the same
  Day-of-Datomic/MusicBrainz tutorial path must also work cleanly from Kvist
  using Vev's native package and literal/query conveniences
- non-Kvist consumers are primary: EDN text APIs, native ABI handles, and host
  wrappers must be treated as product surfaces
- docs should explain how to use Vev today, not narrate historical
  implementation work
- performance work should be driven by the MusicBrainz tutorial/workshop
  workload and benchmark gates, not cursor micro-optimizations in isolation

## Current Status

Vev is usable enough to run the current MusicBrainz workshop slice from both
Clojure and Kvist against the same persistent local store.

Covered in both `examples/clojure/musicbrainz_workshop.clj` and
`examples/kvist/musicbrainz_workshop.kvist`:

- opening pull query from the query-stats prompt
- tuple, collection, and relation input/find examples, including
  vector-of-tuples relation binding and Datomic-like errors when explicit
  `:in` omits the default `$` data source while the query reads from it
- collection, tuple, scalar, and return-map result shapes, including Clojure
  return-map rows that support both key lookup and positional destructuring
- negation and disjunction count examples from `tutorial/query.clj`: `not`,
  `not-join`, `or`, and `or-join`
- predicate and function-expression examples from `tutorial/query.clj` that use
  the MusicBrainz DB: the built-in `<` start-year predicate and `quot`
  track-duration expression
- `get-else` and `get-some` examples from `tutorial/query.clj`, including
  collection input to `get-else` and scalar keyword input to `get-some`
- `fulltext` and `missing?` examples from `tutorial/query.clj`, including
  durable source-backed fulltext rows shaped as `[entity text tx score]`
- transaction-log examples from `tutorial/query.clj`: Datomic-shaped
  Clojure `(d/log conn)` queries for `tx-ids` and `tx-data`, plus Kvist
  validation over the same durable transaction log data
- dynamic attribute specs and equivalent country identity inputs from
  `tutorial/query.clj`: attribute/value lookup with a numeric value,
  collection input over attribute properties, and the three equivalent country
  inputs by lookup ref, ident keyword, and entity id
- opening aggregate examples from `tutorial/query.clj`: source-less relation
  input over the monster-heads data, demonstrating the incorrect aggregate
  without `:with` and the corrected aggregate with `:with`
- built-in function expressions and aggregate examples used by this slice:
  min duration, count/count-distinct artist names, sum medium track count,
  source-less distinct collection aggregate, top-n min/max track durations,
  `rand`/`sample` artist-name aggregate selections, and custom aggregate
  callback registration for `(user/mode ?track-count)`. In the Clojure API,
  aggregate tuple finds such as `[:find [(min ?x) (max ?x)]]` use the
  Datomic tuple result shape rather than a one-row relation shape. The
  Clojure request-map `:timeout` example is also covered as host API behavior.
- request-map query-stats examples around the pre-1970 John Lennon queries
- Datomic-style `%` rules input, first rules exercise, and composed
  `track-info` rules
- Pattern inputs: pull expressions in `:find`, dynamic pull patterns, legal
  multiple-pull forms, Datomic-like duplicate-pull rejection for the same
  query var, and nested pull ref navigation
- direct pull tutorial examples from `tutorial/pull.clj` setup through map
  specifications: attribute-name pulls, lookup refs including ident keywords,
  reverse noncomponent lookup, component defaults, reverse component lookup,
  map specifications, nested map specifications, and wildcard plus map
  specifications, default option, and default option with a different value
  type, absent attributes omitted from results, explicit limit, limit with
  subspec, limit with subspec plus `:as`, no limit, and empty results including
  empty nested results in a collection, pull expressions in queries, and dynamic
  pull-pattern query inputs
- opening aggregate tutorial examples from `tutorial/aggregates.clj` over
  `bigger-than-pluto.edn`: count objects, largest radius, smallest radius,
  average radius, median radius, standard deviation of radius, and random
  object name selection, the `min 3` and `max 3` radius top-n aggregates, and
  `rand 5`/`sample 5` object-name selections, plus the schema-name
  average-length query, schema-name modes custom aggregate query, and final
  schema attribute/value-type count query. Vev loads the upstream schema
  bootstrap transaction and object transaction into a persistent store for both
  Clojure and Kvist, while stripping only Datomic's
  `:db.install/_attribute` marker. Statistical aggregates handle float inputs
  in the relation, typed, and persisted source-backed aggregate paths, scalar
  `(rand ?x)` works from both EDN text and Kvist literal queries, scalar top-n
  aggregates return the Datomic vector shape, and Clojure limited `rand`
  aggregate values expose Datomic's sequence shape while `sample` exposes the
  unique vector shape; Kvist keeps the native vector value shape for both.
  Custom aggregate results can preserve EDN set values through native Vev, the
  C ABI, Java, and Clojure. `scripts/compare_aggregates_tutorial.sh` compares
  this upstream aggregate section against local Datomic: portable object
  aggregate rows match, while schema-introspection rows intentionally differ
  because local Datomic includes internal system schema facts and Vev evaluates
  the upstream fixture schema facts.
- opening `decomposing_a_query.clj` examples over the in-memory `kvs`
  relation source: original slow query count, dropped-clause count,
  cross-product count, single-clause count, reordered count, and optimized
  `:in` query result. The Clojure path uses Datomic-like request-map
  `d/query`; the Kvist path uses native query-first literal Datalog through
  `vev.q-relation-db`.
- opening direct data queries from
  `mbrainz-sample/examples/clj/datomic/samples/mbrainz.clj`: John Lennon
  track titles by artist name, title/album/year by artist name, and the
  pre-1970 filtered variant, plus the rules-backed
  `(track-release ?t ?r)` variant using the upstream
  `mbrainz-sample/resources/rules.edn` rules file as `%`. Both Clojure and
  Kvist use the upstream query shapes against the persistent MusicBrainz
  store. The `track-search` plus `track-info`, `collab`, `collab-net-2`, and
  nested recursive `d/q` relation-input `collab` examples, plus the final
  Bill Withers collaboration self-join query from the same upstream file, are
  also covered in both tutorial paths.

The Clojure path uses the Datomic-like `vev.core` API. The Kvist path uses the
Vev Kvist package directly against the same persistent store. The Kvist query
surface now has query-first macros, including `vev.q` for in-memory DB values
and `vev.q-result-store-db` for persistent store DB snapshots. The workshop
Kvist file is being migrated to native literal Datalog forms where the data DSL
already supports the upstream syntax; EDN strings remain for interop/parser
coverage and any tutorial form that still needs explicit migration. Both paths
are validated by smoke commands listed below.

Important performance signal:

- in-memory/native Vev performance is strong and often well ahead of
  DataScript in the current query/rule benchmarks
- MusicBrainz persistent-store correctness is in good shape, but the current
  `mbrainz-sample` data-query comparison still shows persistent Vev slower
  than local Datomic on the covered rows. Treat that as the active engine work:
  use the existing comparison/profiling workflow to find generic planner,
  source-index, and persisted-row fixes rather than adding benchmark-specific
  query handlers.
- the upstream MusicBrainz statistics query now matches Datomic exactly and is
  faster than local Datomic in the comparison harness
- the generic source physical planner prefers clauses connected to already
  available variables before disconnected broad scans, which keeps the
  opening MusicBrainz direct data queries on a viable plan without hard-coding
  those query texts
- source-backed seeded rule bodies now use the same bind-join operators as
  top-level planned queries. Small bound value/ref joins use exact AVET ranges
  while broad inputs keep the merge-scan path, which makes the upstream
  `track-search` plus `track-info` MusicBrainz query viable against the
  persistent store.
- source-backed bound entity/cardinality-one joins now prefer the existing
  sorted merge operator for larger typed relations before falling back to
  per-row point lookups. This reduces repeated persisted source operators in
  the current MusicBrainz data-query rows: the remaining album/year rows drop
  from roughly `527` source index scans to `155`, `track-search-info` drops
  from roughly `570` to `192`, and the Bill Withers self-join drops from
  roughly `220` to `33`.
- source-backed value-bound joins choose between AVET merge scans and exact
  per-value AVET ranges using a simple generic persisted-cost model: broad
  merge scans are tried first only when the persisted attribute range is small
  enough relative to the typed input relation. This keeps the efficient
  merge-path for broad joins while avoiding full-attribute scans for selective
  MusicBrainz chains. Correctness still matches local Datomic, and the focused
  query-stats rows now show low source-operator/candidate counts, for example
  `pre-1970-track-titles` at `6` source operators and `82` candidates. Elapsed
  time is still slower than local Datomic, so the remaining bottleneck is now
  persisted point/range read cost and row/result materialization behind those
  operators. Selective exact AVET value joins now run through the shared
  `Source-Index-Datom-Stream` path instead of open-coded per-entry index view
  loads, so future persisted page/log-row optimizations can improve both exact
  and merge source operators through one stream abstraction. SQLite snapshot
  streams also skip per-entry current/retraction checks when the snapshot
  metadata says there are no retractions, matching the existing non-run stream
  fast path. Retained-page run-range streams now borrow the current datom from
  the loaded SQLite index page instead of cloning/deleting an owned datom per
  stream step, and ordinary no-retraction SQLite cursor streams use the same
  borrowed-current shape from their loaded cursor page. The generic
  source-clause stream wrapper now also borrows current datoms from the
  underlying source/index stream where possible, and ordinary no-retraction
  SQLite clause scans borrow from the loaded cursor page instead of cloning a
  datom per candidate. Source-column result renderers now move owned strings
  from typed column buffers into result rows instead of cloning them during
  result materialization. The next remaining cost is persisted row decode/page
  reuse around point/range reads; the current comparison gate still matches
  Datomic rows/fingerprints but Vev remains slower on the persisted
  MusicBrainz data-query rows.
- the upstream `collab` query over a collection input and `%` rules now works
  from both Clojure and Kvist against the persistent MusicBrainz store. The
  Clojure request-map timeout validation uses a tighter timeout than the
  upstream prose because Vev often completes the original `100` ms example
  before it can demonstrate timeout behavior.
- the upstream `collab`, `collab-net-2`, and nested recursive `d/q` `collab`
  queries now work from both Clojure and Kvist without broad same-entity
  relation materialization. Generic bounded source-backed rule operators use
  incoming relation bindings for attr-parameter same-entity pair rules and
  derived two-hop self-join rules. Current profiling shows the broad
  collaboration candidates reduced from roughly `101k` to under `1k` for the
  active upstream rows. The EDN/query input parser also accepts relation row
  collections represented as sets as well as vectors, so a Clojure `d/q`
  relation result can feed directly into the next `d/q` relation input.
- the upstream Bill Withers collaboration self-join query now works from both
  Clojure and Kvist against the persistent MusicBrainz store. It exercises
  inequality predicates over literals and vars, repeated track-name joins, and
  duplicate artist exclusion.

## Exit Criteria

This gate is done when the MusicBrainz workshop path works without reading Vev
internals:

- a user can load or generate the MusicBrainz sample/subset into persistent Vev
- a user can follow representative Day of Datomic / MusicBrainz tutorial
  queries from Clojure using `vev.core` with Datomic-like API shape
- a user can follow the same tutorial path from Kvist using Vev's native package
  and literal/query conveniences
- the persistent store can be closed, reopened, queried, and pulled from both
  Clojure and Kvist without SQLite details leaking into normal app code
- row counts and portable result fingerprints match local Datomic for the
  covered tutorial/workshop queries
- performance is as good as or better than local Datomic for the covered local
  persistent workload, or any slower row has a documented concrete bottleneck
  and next fix
- setup docs clearly state native library and system SQLite requirements

## Remaining Work

1. **Continue the upstream MusicBrainz tutorial port**

   Status: active. `examples/clojure/musicbrainz_workshop.clj` and
   `examples/kvist/musicbrainz_workshop.kvist` cover
   `day-of-datomic-conj/src/music_brainz.clj` through Pattern inputs,
   `day-of-datomic/tutorial/query.clj` through the final aggregate/request-map
   examples, then all of `day-of-datomic/tutorial/pull.clj`.
   `examples/clojure/aggregates_tutorial.clj` and
   `examples/kvist/aggregates_tutorial.kvist` cover
   all current executable forms in `day-of-datomic/tutorial/aggregates.clj`.
   `examples/clojure/decomposing_query.clj` and
   `examples/kvist/decomposing_query.kvist` cover the opening executable
   relation-source forms in `day-of-datomic/tutorial/decomposing_a_query.clj`.
   `examples/clojure/musicbrainz_workshop.clj` and
   `examples/kvist/musicbrainz_workshop.kvist` also cover the opening direct
   data queries, the rules-backed `(track-release ?t ?r)` query, and the
   upstream `track-search` plus `track-info` query, plus the upstream
   `collab`, `collab-net-2`, and nested recursive `d/q` `collab` relation
   input queries, plus the final Bill Withers collaboration self-join query in
   `mbrainz-sample/examples/clj/datomic/samples/mbrainz.clj`.

   Next exact upstream section: no executable forms remain in
   `build/upstream/mbrainz-sample/examples/clj/datomic/samples/mbrainz.clj`.
   The current task is productizing comparison for this now-covered upstream
   data-query section. `scripts/compare_musicbrainz_workshop.sh` runs the
   matching Clojure Vev query set, the Kvist workshop validation, and, when
   requested, the matching local Datomic query set.

   Kvist tutorial API status:

   - simple query, tuple input, collection input, relation input, find-shape,
     return-map, function-expression, dynamic attribute-position queries, and
     aggregate examples now use `vev.q-result-store-db` with literal Datalog
     forms and query-first order
   - the remaining EDN strings in `examples/kvist/musicbrainz_workshop.kvist`
     should be migrated to literal forms when touching those sections, except
     for cases deliberately exercising the EDN text parser or non-Kvist
     interop surface
   - direct pull helpers still use text pull patterns today; migrate them only
     when there is a clean native pull-pattern store API, not by inventing a
     parallel tutorial shape

   Keep both tutorial paths moving together. Every new Clojure tutorial slice
   should have a matching Kvist slice unless the missing piece is explicitly
   documented as a Kvist language/runtime blocker.

   Current host-function status:

   - the JVM-only `System/getProperties` / `.endsWith` examples and the
     collection-only `subs` example are host/query-language examples, not
     MusicBrainz DB-backed tutorial gates. They should live in a later general
     host interop/query-language slice unless they become necessary for
     MusicBrainz workshop parity.
   - Vev covers the DB-backed dynamic attribute examples that follow those
     host forms, including Datomic's warning case and the upstream
     `datomic.api/entid` repair query. Dynamic attribute clauses no longer
     implicitly resolve keyword idents in value position; the upstream form
     marked "this will not work as desired" returns no Belgian artist rows,
     while the repaired `entid` form returns the expected rows.
   - query-time `datomic.api/entid` / `entid` is supported for the default
     source token and is validated by the exact upstream dynamic-attribute
     repair query in both Clojure and Kvist.
   - the Kvist literal query DSL covers the upstream numeric attribute-position
     form `[?attr 42 _]`, matching the EDN text API for this section.
   - Clojure `d/log` supports the upstream `tx-ids` and `tx-data` query shapes
     against durable Vev connections; local Vev stores use compact tx ids
     rather than Datomic sample tx ids, so examples preserve the upstream query
     shape while selecting Vev-local ranges/ids
   - native ABI/Java expose durable transaction-log tx data as EDN text for
     host wrappers; Kvist currently validates tx ids and tx data through the
     native store API rather than a first-class `vev.log` query source
   - general Java/Clojure row result conversion preserves Vev keyword and
     symbol value kinds, so Clojure tutorial queries return real Clojure
     keywords/symbols rather than stringified EDN tokens
   - EDN parsed query inputs own their string-like values, so scalar
     keyword/string/symbol inputs remain valid after parsing and can safely be
     passed through host APIs
   - Clojure `d/q` now supports source-less queries whose `:in` does not name
     `$`, using a temporary empty in-memory Vev DB only as the native query
     engine source. This covers the exact upstream monster-heads relation
     aggregate call shape. Kvist validates the same query/input data through
     an empty in-memory Vev DB because the query has no DB clauses.
   - `(distinct ?v)` aggregate parsing and execution is supported through the
     Kvist literal macro, EDN text parser, generic aggregate reducer, typed
     aggregate reducer, and source aggregate reducer. Native Vev represents
     the aggregate value as a vector of unique values; the Clojure `d/q` wrapper
     exposes it as a Clojure set to match the DataScript/Datomic aggregate
     shape. The exact upstream source-less collection-input form now passes in
     both `examples/clojure/musicbrainz_workshop.clj` and
     `examples/kvist/musicbrainz_workshop.kvist`.

   - arbitrary host Clojure predicate calls such as `(user/teste ?year)` are
     resolved by `vev.core`, registered with the native query function
     registry, and executed by the native query engine
   - custom Clojure aggregate functions such as `(user/mode ?track-count)` are
     resolved by `vev.core`, registered with the native query function
     registry, and executed by the native query engine. Kvist can now call the
     same native query-function registry against persistent store snapshots
     through `vev.q-result-store-db-with-fns`; the workshop validates
     `(user/mode ?track-count)` with a native Kvist callback and literal
     query form.
   - built-in aggregate tuple finds in the Clojure API now match Datomic's
     tuple shape for the covered `min`/`max` and `rand`/`sample` examples
   - the Clojure workshop validation now asserts the upstream custom aggregate
     result: `(user/mode ?track-count)` returns `2` on the local MusicBrainz
     tutorial store
   - the Clojure workshop validation now asserts the upstream request-map
     timeout form from `tutorial/query.clj`; `:timeout 100` throws
     `query timed out` on the local persistent MusicBrainz store
   - the Clojure wrapper now supports the `decomposing_a_query.clj` style
     in-memory vector source: request-map `:args` may start with a vector of
     `[e a v]` triples, which is queried as a relation DB source
   - `examples/clojure/decomposing_query.clj` and
     `examples/kvist/decomposing_query.kvist` port the opening `kvs` relation
     and validate the fast decomposition queries: original slow query count
     `1`, dropped-clause count `2000`, cross-product count `4000000`,
     single-clause count `2000`, reordered count `1`, and optimized `:in`
     query result `#{[10 10]}` / `[10 10]`
   - large relation-source query results can use a delayed counted relation:
     `(count (d/query ...))` validates dangerous cross-products without
     materializing millions of host rows, while ordinary small results still
     return concrete sets
   - the matching Kvist story for tutorial-local custom aggregate functions is
     covered for the upstream `user/mode` aggregate. A Kvist custom predicate
     example should only be added if a concrete upstream tutorial form requires
     it.

   Current measured durable-store status for this section:

   - `musicbrainz-real-track-name-statistics`, from the upstream Statistics
     section, matches local Datomic by row count and portable fingerprint
   - latest local run: Vev median about 326ms, Datomic median about 426ms
   - Datalog `(count ?string)` now follows Datomic/Clojure Java string length
     semantics by counting UTF-16 code units from Vev's UTF-8 strings

   Engine status:

   - source-backed `fulltext` scans string values in the requested attribute,
     applies token-boundary matching, and returns Datomic-shaped
     `[entity text tx score]` rows; current scoring is sample-compatible
     (`1.0`, `0.625`, `0.5`), while Lucene-style analyzer/scoring parity is a
     later explicit feature if needed
   - persisted scalar/function aggregate execution now has a generic typed
     source operator for fixed-attribute ref chains plus string length
   - the operator builds typed relation columns from sequential source index
     streams and lets the normal typed aggregate reducer handle `:with`,
     grouping, `avg`, `median`, and `stddev`
   - this is generic ref-chain engine work keyed by query shape and variables,
     not by exact MusicBrainz query text
   - aggregate distinctness in the direct source path includes grouping vars,
     `:with` vars, and aggregate argument values, matching Datomic behavior
   - source-backed `not`/`not-join` now use a planned set anti-join when the
     branch is eligible for typed relation execution, avoiding row-by-row
     negative branch evaluation on the MusicBrainz tutorial queries
   - source-backed `or`/`or-join` now use a planned branch-union path when the
     branches are eligible for typed relation execution; this keeps the OR
     examples on the generic relation engine, though broad branch scans remain
     a legitimate later performance target

   Current comparison workflow:

   - `scripts/compare_musicbrainz_workshop.sh --skip-datomic` validates the
     covered upstream data-query section against Clojure Vev and Kvist Vev
   - `scripts/compare_musicbrainz_workshop.sh` additionally runs the same
     named query workloads against local Datomic and compares row count plus a
     stable portable fingerprint
   - the wrapper starts and stops the local Datomic sample transactor for
     Datomic comparisons; the sample DB still must have been prepared at least
     once with `scripts/musicbrainz_sample.sh prepare`
   - Datomic comparison output includes Vev time, Datomic time, and a
     machine-readable `ratio` for each workload
   - current result: full Clojure/Kvist/Datomic workflow passes correctness
     for all nine ported `mbrainz-sample` data-query workloads by row count
     and portable fingerprint
   - Clojure request-map `:query-stats` now handles Datomic-style `%` rules
     input for persistent DB snapshots. Rule-backed MusicBrainz rows now report
     rule calls/iterations, source index scans, source operators, clause
     candidates, and output rows instead of `unsupported`.
   - current performance signal: persistent Vev is still slower than local
     Datomic on these rows. A recent comparison run showed the slowest rows in
     the rule/recursive and broad-filter cases, while all rows still matched by
     count/fingerprint. This is the next real bottleneck; do not mark the gate
     complete until the representative rows are at least close to Datomic or
     each remaining slow row has a specific engine/storage explanation and
     planned fix.

2. **MusicBrainz import and fixture setup**

   Status: active enough for local work, but still needs product-quality docs.

   Required:

   - document which MusicBrainz dataset/subset is used
   - provide one command to create or refresh the persistent Vev store
   - provide one command to run Clojure tutorial validation
   - provide one command to run Kvist tutorial validation
   - keep the generated store out of git

   Current smoke commands:

   - `scripts/musicbrainz_workshop_clojure.sh`
   - `scripts/musicbrainz_workshop_kvist.sh`
   - `scripts/compare_musicbrainz_workshop.sh --skip-datomic`
   - `scripts/compare_musicbrainz_workshop.sh` when local Datomic is available
   - `scripts/aggregates_tutorial_clojure.sh`
   - `scripts/aggregates_tutorial_kvist.sh`
   - `scripts/compare_aggregates_tutorial.sh`
   - `scripts/decomposing_query_clojure.sh`
   - `scripts/decomposing_query_kvist.sh`

3. **Datomic comparison harness**

   Status: usable for the covered upstream `mbrainz-sample` data-query
   section. It runs Datomic first, then Vev, normalizes Clojure/Datomic Java
   collection result shapes, and compares row count plus stable portable
   fingerprint.

   Remaining:

   - keep `scripts/compare_musicbrainz_workshop.sh` as the normal regression
     command for this upstream section
   - if a future mismatch appears, fix it as an engine/API issue, not by
     changing the upstream query shapes
   - improve persistent query performance using these measured rows as the
     guide; the comparison harness now clearly separates correctness matches
     from slower Vev ratios

4. **Only fix engine/storage issues exposed by this path**

   Do not return to broad optimization work yet. Fix issues only when they
   block or materially slow the MusicBrainz persistent tutorial scenario:

   - missing query/pull/rule syntax used by the tutorial
   - wrapper API friction that makes tutorial code unlike Datomic/DataScript
   - persistent store reopen/query behavior that makes the workflow clumsy
   - specific MusicBrainz query rows slower than local Datomic

   Current priority:

   - use request-map `:query-stats true` and the Datomic comparison harness on
     the exact ported `mbrainz-sample` rows. The most important current targets
     remain `mbrainz-title-album-year-by-artist`,
     `mbrainz-pre-1970-title-album-year`, `mbrainz-track-release-rule`, and
     `mbrainz-track-search-info`.
   - the next concrete bottleneck is persisted point/range read cost and
     row/result materialization inside the remaining small number of source
     operators. The active rows now avoid the worst source-operator explosion
     and broad AVET scans; they are still slower because each generic operator
     does too much persisted cursor/page/log-row work for the amount of output
     produced. Exact AVET joins use the shared persisted stream path, and that
     stream now borrows datoms from retained SQLite pages instead of cloning an
     owned datom for every run-range row; ordinary no-retraction SQLite cursor
     streams also borrow from their loaded cursor page. The next fix should
     keep lowering stream/page and result materialization cost rather than
     adding another query-specific operator variant. Remaining cost is
     primarily loading/decoding persisted datom rows, reusing loaded pages
     across neighboring operators, and materializing host/query result values.
   - look for reusable typed/source operator paths that can batch adjacent
     exact ranges, reuse loaded persisted pages across neighboring point/range
     reads, or fuse adjacent ref/value joins without hard-coding MusicBrainz
     attribute names.
   - keep fixes generic to Datalog rule/join/index execution, not keyed to
     exact MusicBrainz query strings

## Later

Important, but not the active gate:

- broader host wrapper polish outside Clojure/Kvist
- full package publication polish: Maven/Clojars/PyPI/crates/packages
- persisted cursor/page traversal optimization
- shared/chunked durable index storage beyond the current architecture
- lower-allocation result/page materialization
- exact parser diagnostic/object parity
- generic SCC-local semi-naive recursive rule fallback
- optional server/transactor packaging mode
