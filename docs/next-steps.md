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
- built-in function expressions and basic aggregates used by this slice
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
- MusicBrainz vs Datomic is broadly healthy; most representative rows are
  faster in Vev, with a small number of known slower rows
- the upstream MusicBrainz statistics query now matches Datomic exactly and is
  faster than local Datomic in the comparison harness

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
   `day-of-datomic-conj/src/music_brainz.clj` through Pattern inputs, then
   all of `day-of-datomic/tutorial/pull.clj`.

   Kvist tutorial API status:

   - simple query, tuple input, collection input, relation input, find-shape,
     return-map, function-expression, and aggregate examples now use
     `vev.q-result-store-db` with literal Datalog forms and query-first order
   - the remaining EDN strings in `examples/kvist/musicbrainz_workshop.kvist`
     should be migrated to literal forms when touching those sections, except
     for cases deliberately exercising the EDN text parser or non-Kvist
     interop surface
   - direct pull helpers still use text pull patterns today; migrate them only
     when there is a clean native pull-pattern store API, not by inventing a
     parallel tutorial shape

   Next upstream section:

   - `build/upstream/day-of-datomic/tutorial/query.clj`
   - section: Predicate Expressions / Statistics / Custom Aggregates

   Keep both tutorial paths moving together. Every new Clojure tutorial slice
   should have a matching Kvist slice unless the missing piece is explicitly
   documented as a Kvist language/runtime blocker.

   Current blockers exposed by the upstream port:

   - arbitrary host Clojure predicate calls such as `(user/teste ?year)` are
     parsed but not executed by the native query engine
   - custom Clojure aggregate functions such as `(user/mode ?track-count)` are
     not executed by the native query engine

   Current measured durable-store status for this section:

   - `musicbrainz-real-track-name-statistics`, from the upstream Statistics
     section, matches local Datomic by row count and portable fingerprint
   - latest local run: Vev median about 326ms, Datomic median about 426ms
   - Datalog `(count ?string)` now follows Datomic/Clojure Java string length
     semantics by counting UTF-16 code units from Vev's UTF-8 strings

   Engine status:

   - persisted scalar/function aggregate execution now has a generic typed
     source operator for fixed-attribute ref chains plus string length
   - the operator builds typed relation columns from sequential source index
     streams and lets the normal typed aggregate reducer handle `:with`,
     grouping, `avg`, `median`, and `stddev`
   - this is generic ref-chain engine work keyed by query shape and variables,
     not by exact MusicBrainz query text
   - aggregate distinctness in the direct source path includes grouping vars,
     `:with` vars, and aggregate argument values, matching Datomic behavior

   Next work for this upstream section:

   - execute arbitrary host Clojure predicates through the native function
     registry without breaking pure native/Kvist execution
   - execute custom Clojure aggregate functions such as
     `(user/mode ?track-count)` through the native function registry
   - decide which host-callback cases belong in the smoke path and which should
     be documented as slower host interop checks

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

3. **Datomic comparison harness**

   Required:

   - run the same query definitions against local Datomic and Vev
   - report row count, stable fingerprint, Vev time, Datomic time, and ratio
   - keep timing methodology simple and visible enough to trust
   - separate correctness failures from performance regressions

4. **Only fix engine/storage issues exposed by this path**

   Do not return to broad optimization work yet. Fix issues only when they
   block or materially slow the MusicBrainz persistent tutorial scenario:

   - missing query/pull/rule syntax used by the tutorial
   - wrapper API friction that makes tutorial code unlike Datomic/DataScript
   - persistent store reopen/query behavior that makes the workflow clumsy
   - specific MusicBrainz query rows slower than local Datomic

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
