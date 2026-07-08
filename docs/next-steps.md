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
- non-Kvist consumers are primary: EDN text APIs, native ABI handles, and host
  wrappers must be treated as product surfaces
- docs should explain how to use Vev today, not narrate historical
  implementation work
- performance work should be driven by the MusicBrainz tutorial/workshop
  workload and benchmark gates, not cursor micro-optimizations in isolation

## Current Status

Vev has enough foundation to attempt the MusicBrainz workshop path:

- in-memory connections support schema/data transactions, Datalog queries, pull,
  rules, aggregates, inputs, lookup refs, `with`, and DB values
- durable SQLite-backed stores support normal `connect/open -> transact -> db ->
  q/pull/entity -> close/reopen -> q/pull/entity` workflows
- retained `Store-DB` snapshots are immutable values and can be passed around
- C, Java, Clojure, Python, and Rust clients/smokes exist over the native ABI
- Clojure has a Datomic-like API shape and the full MusicBrainz wrapper matrix
  verifies the non-Kvist EDN path
- the real MusicBrainz query matrix matches Datomic by row count and portable
  fingerprint across the current listed workloads
- the README and getting-started docs now lead with the actual Vev workflow
  instead of smoke-script setup
- Clojure examples are in `(comment ...)` tutorial style and use
  Datomic/DataScript query-first `q`
- Java, Python, Rust, Go, Node, and Odin docs now show the same
  DB-value-centered transaction, query, and pull workflow where their wrappers
  support it
- Python, Go, and Node now have one-shot query helpers over the existing
  prepared-query paths; Node also has explicit close methods for connection,
  DB, and prepared-query handles
- Rust now has the same app-facing `q` convenience for connections and DB
  values, plus durable `transact` over the report path
- Rust now has a real `lib.rs` crate surface plus a smoke binary, and the
  focused package smoke validates a temporary external Cargo project with a
  path dependency on `clients/rust`
- Python now has a small contact-book example that runs the same workflow in
  in-memory and durable modes
- the next important user-facing gap is a Clojure and Kvist MusicBrainz
  tutorial path, not more generic wrapper polish
- the first Clojure tutorial slice now ports the opening/query-stats prompt and
  binding examples from `day-of-datomic-conj/src/music_brainz.clj`
- the Clojure tutorial path now ports from the file top through the pre-1970
  release clause-order query-stats examples in the same upstream file

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

1. **Clojure MusicBrainz tutorial path**

   Status: active. `examples/clojure/musicbrainz_workshop.clj` starts from the
   upstream `day-of-datomic-conj/src/music_brainz.clj` top section and now
   covers through the pre-1970 release clause-order query-stats examples. The
   exact upstream "Statistics" and custom aggregate forms are present in the
   tutorial file but remain pending. `scripts/musicbrainz_workshop_clojure.sh`
   validates the covered slices against a persistent Vev MusicBrainz store.

   Covered:

   - use the durable Vev store path, not an in-memory-only demo
   - keep examples in `(comment ...)` form for editor-driven evaluation
   - opening John Lennon pull query from the query-stats prompt
   - tuple binding for `["John Lennon" "Mind Games"]`
   - request-map `d/query` shape for Elis Regina release pulls
   - collection binding for Paul McCartney / George Harrison
   - relation find spec with explicit `$`
   - collection, tuple, and scalar find specs with Datomic-like Clojure result
     shapes
   - return maps with `:keys`, including key destructuring
   - built-in function expression `(quot ?millis 60000) ?minutes`
   - built-in expression join for Janis Joplin artist type/gender
   - basic aggregations: `min`, scalar `sum`, and `count` /
     `count-distinct`
   - first deeper `d/query` request-map form with `:io-context`, returning
     John Lennon track titles
   - `d/query` request-map forms with `:query-stats true` for the two
     pre-1970 John Lennon release-name clause-order examples

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
   - the upstream track-name statistics query using `median`, `avg`, `stddev`,
     and string length currently runs too slowly against the persistent
     tutorial store for the smoke path
   - custom Clojure aggregate functions such as `(user/mode ?track-count)` are
     not executed by the native query engine

2. **Kvist MusicBrainz tutorial path**

   Build the same tutorial/workshop path from Kvist.

   Required:

   - use the Vev Kvist package directly
   - use Kvist-native query/tx/pull literals where they improve the experience
   - use the same persistent MusicBrainz Vev store as the Clojure path where
     practical
   - cover the same query set as the Clojure tutorial path
   - produce comparable timing/fingerprint output

3. **MusicBrainz import and fixture setup**

   Current MusicBrainz tests exist, but the tutorial path needs a clear fixture
   story.

   Required:

   - document which MusicBrainz dataset/subset is used
   - provide one command to create or refresh the persistent Vev store
   - provide one command to run Clojure tutorial validation
   - provide one command to run Kvist tutorial validation
   - keep the generated store out of git

4. **Datomic comparison harness**

   Required:

   - run the same query definitions against local Datomic and Vev
   - report row count, stable fingerprint, Vev time, Datomic time, and ratio
   - keep timing methodology simple and visible enough to trust
   - separate correctness failures from performance regressions

5. **Only fix engine/storage issues exposed by this path**

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
