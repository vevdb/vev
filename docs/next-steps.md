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
  `(d/q query db & inputs)` order
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
- tuple, collection, and relation input/find examples
- collection, tuple, scalar, and return-map result shapes where currently
  supported
- built-in function expressions and basic aggregates used by this slice
- request-map query-stats examples around the pre-1970 John Lennon queries
- Datomic-style `%` rules input, first rules exercise, and composed
  `track-info` rules
- Pattern inputs: pull expressions in `:find`, dynamic pull patterns, legal
  multiple-pull forms, and nested pull ref navigation
- direct pull tutorial examples from `tutorial/pull.clj` setup through map
  specifications: attribute-name pulls, lookup refs, component defaults,
  reverse component lookup, map specifications, nested map specifications, and
  wildcard plus map specifications, default option, and default option with a
  different value type, absent attributes omitted from results, explicit
  limit, limit with subspec, limit with subspec plus `:as`, no limit, and
  empty results including empty nested results in a collection, pull
  expressions in queries, and dynamic pull-pattern query inputs

The Clojure path uses the Datomic-like `vev.core` API. The Kvist path uses the
Vev Kvist package directly against the same persistent store. Both paths are
validated by smoke commands listed below.

Important performance signal:

- in-memory/native Vev performance is strong and often well ahead of
  DataScript in the current query/rule benchmarks
- MusicBrainz vs Datomic is broadly healthy; most representative rows are
  faster in Vev, with a small number of known slower rows
- durable/source-backed storage has remaining internal bottlenecks, especially
  persisted cursor scans and result/page materialization, but this should now be
  addressed only when the MusicBrainz tutorial path exposes it

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

   Next upstream section:

   - close the remaining documented blockers exposed by the already-ported
     upstream tutorial/workshop files before inventing new examples

   Keep both tutorial paths moving together. Every new Clojure tutorial slice
   should have a matching Kvist slice unless the missing piece is explicitly
   documented as a Kvist language/runtime blocker.

   Current blockers exposed by the upstream port:

   - relation binding with vector-of-tuples is ported but pending for the
     durable Clojure wrapper; the native/Python layers have typed relation
     helpers, but Java/Clojure do not yet expose the needed relation binding
     path for this source-backed query shape
   - the upstream missing-`$` teaching query returns no rows in Vev instead of
     Datomic's helpful "missing data source" error
   - return-map rows support key destructuring but not Datomic's positional
     destructuring, because Vev currently returns ordinary Clojure maps rather
     than indexed map rows
   - arbitrary host Clojure predicate calls such as `(user/teste ?year)` are
     parsed but not executed by the native query engine
   - duplicate pull expressions on the same query var are accepted by Vev but
     should be rejected like Datomic
   - direct reverse pull `[:artist/_country] :country/GB` currently returns an
     empty collection in Vev even though the equivalent forward query over
     `:artist/country :country/GB` has rows
   - the upstream track-name statistics query using `median`, `avg`, `stddev`,
     and string length currently runs too slowly against the persistent
     tutorial store for the smoke path
   - custom Clojure aggregate functions such as `(user/mode ?track-count)` are
     not executed by the native query engine

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
