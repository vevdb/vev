# Roadmap

## Phase 0: Spec

Done.

Outcomes:

- scope pinned down
- architecture pinned down
- query model pinned down
- interop direction pinned down
- semantic style pinned down
- embedded-first/server-possible stance pinned down
- Kvist-first implementation stance pinned down
- native library plus CLI packaging direction pinned down

## Phase 1: In-memory proof

Goal:

- create connection
- transact small dataset
- run simple query
- return immutable DB snapshots for reads
- keep generated Odin readable

Success shape:

- datoms exist
- core indexes exist
- one or two query forms work
- transaction metadata exists in tx reports

Status: done. Vev has a Kvist implementation, immutable DB values, sorted
indexes, transaction reports, and a DataScript-shaped query surface.

## Phase 2: Pull and entity reads

Goal:

- basic pull
- entity-style access if it still fits the design cleanly

Preferred boundary:

- Datomic/DataScript-compatible pull syntax first
- typed internal pull representation second

Status: mostly done for the in-memory engine. Remaining work is host/API shape
and callback/streaming polish, not the basic pull model.

## Phase 3: DataScript parity

Goal:

- continue porting the DataScript test suite namespace by namespace
- close in-memory query, transaction, pull, schema, and index gaps
- keep Kvist literal APIs and internal typed forms aligned

Non-goal:

- server/transactor packaging

Status: mostly satisfied as the current compatibility gate. The broad in-memory surface is present:
query, pull, tx-data, schema, lookup refs, tuples, indexes, parser text paths,
prepared APIs, and host-facing EDN/C ABI query paths. The local compatibility
suite currently passes 364 tests. Remaining work is concentrated in exact
parser diagnostics/object rendering, query/rule planner maturity,
MusicBrainz/Datomic workload coverage, higher-level host wrapper ergonomics,
and durable storage integration.

Deferred engine batch order:

1. Typed/columnar relation storage: replace hot `Binding` tuple intermediates
   with struct-of-arrays style relation columns while preserving the current
   logical relation API. Migrate one operator family end-to-end first, then
   fold q1/q2/q3/q4-style entity/projection paths into the same relation result
   path instead of keeping them as separate recognizers.
2. Query planner/operator layer: replace local heuristic planning and isolated
   fast paths with reusable physical operators for indexed scan, bind join,
   hash/semi join, anti join, rule calls, projection, and aggregation.
3. Generic recursive rules: build component/SCC-local semi-naive evaluation,
   reusable/materialized rule relations where appropriate, and bound-recursive
   query support that can later grow toward magic-set-style rewriting. This
   should build on the typed relation/operator layer rather than on the current
   generic binding representation.
4. Datalevin benchmark integration: start with `datascript-bench` through the
   Clojure API, then move to `math-bench` and `openrulebench` for rule-engine
   validation. Use these to compare Vev against Datomic, DataScript, and
   Datalevin on shared workloads before moving to larger planner benchmarks.
5. Parser/API exactness: make malformed EDN query, rule, pull, return-map, and
   tx-data shapes fail predictably through the portable text/prepared APIs.
6. Host wrapper ergonomics: keep C as the stable raw ABI, expose durable
   storage through storage-neutral `connect`/connection handles, and make
   Clojure and Java feel close to Datomic/DataScript for common tutorials,
   including listener/report callbacks where useful.
MusicBrainz/Datomic comparison is no longer part of this deferred list; it is
the next active phase.

## Current Query Engine State

Vev has enough query machinery to pass most DataScript-level semantic tests:
sorted datom indexes, binary seek/range access, candidate selection per data
clause, relation-engine execution for supported query shapes, prepared queries,
text/EDN lowering, and profiling counters for candidates, intermediate binding
sizes, rule calls, and rule iterations.

The planner is still primitive. It mostly chooses the next clause from current
bindings, with additional specialized paths for common indexed projections and
relation-engine-supported queries. It does not yet have a full cost model,
histograms, global join ordering across all operator kinds, or a reusable
physical operator layer. This is the main query-engine gap before large
real-world Datomic-shaped workloads.

The desired direction is not to add benchmark-specific recognizers. The next
planner work should introduce reusable operators and make benchmark wins fall
out of better generic planning: indexed scans, bind joins, hash/semi joins,
anti joins, rule operators, projection, aggregate, and pull integration.

The immediate implementation batch is:

1. Define a typed relation storage direction: keep logical relation attrs, but
   store hot rows as typed struct-of-arrays columns and keep the existing
   `Binding` API as a compatibility veneer during migration.
2. Port one operator family end-to-end, starting with primitive
   entity/int/string columns and the existing compound primitive hash join.
3. Benchmark after each migration with `datascript-bench` q1-q5/qpred plus
   focused relation-source compound join tests.
4. Then build the generic semi-naive rules engine on top of the improved
   relation/operator representation.

## Current Rules Engine State

Rules are semantically useful now. Vev supports DataScript-style rule branches,
required rule vars, source-qualified rule calls, recursive and mutual recursive
rules, rule bodies with data clauses/predicates/functions/ground/not/or, text
and prepared rule APIs, and rule profiling counters.

Recent work added the foundation for serious rule planning:

- dependency graph analysis
- dependency-closure filtering per called rule
- acyclic-vs-recursive classification
- reusable `Rule-Call-Plan` values
- cached recognition of linear transitive rule operators
- cached recognition of alternating mutual-recursive transitive operators
- bound-source transitive result fast path

The remaining rules milestone is a generic semi-naive engine. Specialized
transitive operators should remain as fast physical operators, but the fallback
recursive path should become component/SCC-local, delta-driven, and predictable
for large recursive workloads. Datalevin is the main implementation reference
for this direction; DataScript remains the semantic compatibility floor.

## Phase 4: Portable query frontend

Goal:

- parse EDN text for queries, pull patterns, and transaction data
- expose parse-and-run helpers for non-Kvist callers
- expose prepared query handles for repeated execution
- lower parsed EDN into the same internal structures as Kvist literals

This phase is required for broad C/Odin/host-language consumption. It should
not create a second query engine.

Status: substantially done and now part of the compatibility gate. EDN text and
prepared query/tx/pull paths lower into the same typed structures as Kvist
literals. C, Python, Rust, Java, and Clojure smokes exercise the EDN path
through the native library. The remaining work is exact malformed-input
behavior, parser value rendering where host APIs expose it, and wrapper
ergonomics demanded by real host usage.

## Phase 5: MusicBrainz/Datomic Workload

Goal:

- load a Datomic MusicBrainz / Day of Datomic-shaped dataset into Vev
- port workshop queries as a correctness suite
- run the same queries against Datomic locally
- compare result sets before comparing performance
- use the workload to expose planner, rule, pull, aggregate, and API gaps

Why it matters:

- MusicBrainz is a real Datomic-shaped workload, not a synthetic microbench
- it tests whether Vev can follow existing Datomic teaching material
- it gives a shared correctness/performance target for SQLite storage
- it exercises large in-memory indexes, immutable DB values, EDN text APIs, and
  host wrappers under realistic pressure

Initial scope:

- start with the Day of Datomic dataset or a deterministic MusicBrainz slice
- keep the import path simple and repeatable
- store expected query results in Vev tests or benchmark fixtures
- report Datomic vs Vev timings as comparative ratios, not raw claims

Status: active next phase. The basic SQLite reopen/query loop exists, host
connection wrappers can reach it, and the remaining storage architecture work
is known. The current mini MusicBrainz fixture covers tutorial-shaped EDN
queries, pulls, rules, SQLite reopen, and query profiling. The 1968-1973 sample
backup is locally restorable and smoke-tested against Datomic. The next larger
step is exporting/importing that restored sample into Vev and comparing the
same query matrix against local Datomic. See `docs/musicbrainz.md` and
`docs/musicbrainz-query-matrix.md` for the active work plan.

## Phase 5b: External Optimizer Benchmarks

Goal:

- use established external benchmark shapes to check that Vev is moving toward
  a real embedded Datomic-like database, not just passing local microbenchmarks
- compare against Datomic, DataScript, Datalevin, PostgreSQL, and SQLite where
  the benchmark already does that
- use benchmark failures to drive general planner/operator work

Datalevin benchmark ladder:

1. `datascript-bench`: immediate. Add Vev beside Datomic, DataScript, and
   Datalevin for the inherited DataScript read/write/rule workload. This is
   small, in-memory, and directly exercises common Datomic/DataScript query
   forms through the Clojure API.
2. `math-bench`: near-term rules benchmark. This uses a realistic Math
   Genealogy dataset and compares Datalog rule processing across DataScript,
   Datomic, and Datalevin. It is the best next external workload after
   `datascript-bench` for validating recursive rules beyond synthetic
   reachability.
3. `openrulebench`: rule-engine milestone. Use after the generic recursive
   rule engine is underway. It should validate component/SCC-local semi-naive
   behavior and expose rule workloads that are not just graph reachability.
4. `JOB-bench`: query planner milestone. This benchmark stresses join ordering,
   predicates, ranges, aggregates, and large import behavior over an
   IMDB-shaped dataset.
5. `LDBC-SNB-bench`: graph/read workload milestone. Use after the planner and
   larger import path can handle realistic graph-shaped data. It should
   validate interactive short reads and more complex graph queries.
6. `idoc-bench`: document/nested-value milestone. Relevant if Vev leans into
   document-ish nested values, arrays, ranges, wildcards, and YCSB-style query
   mixes.
7. `write-bench`: durability milestone. Started as a small Vev-native
   `bench/write_bench.kvist` harness now that durable SQLite-backed storage
   exists. It measures transaction throughput, commit latency, batching, and
   mixed read/write behavior. Scale it up and wire it to Datalevin's upstream
   shape once the local durable path is less dominated by report/index
   ownership-copy overhead.
8. `search-bench`: optional/full-text milestone. Relevant only if Vev owns a
   full-text search story instead of delegating to SQLite FTS or external
   indexes.

Current stance:

- `datascript-bench`, `math-bench`, and `openrulebench` are the near-term
  semantic/rule-engine benchmark path.
- `JOB-bench` and `LDBC-SNB-bench` are planner and large-read milestones.
- `write-bench` is now active as a small local durable harness; `idoc-bench`
  and `search-bench` remain phase-specific later benchmarks.
- None of these should be gamed with one-off recognizers; benchmark wins should
  come from reusable physical operators and better planning.

## Phase 6: Durable proof

Goal:

- open local DB
- transact facts
- close process
- reopen DB
- query facts successfully

Backend:

- SQLite first

Packaging:

- embedded native library path remains primary
- CLI binary exercises the same engine path

Status: first proof complete; deeper architecture deferred. Vev now has snapshot-file persistence, SQLite-backed datom row
persistence, explicit SQLite tx metadata rows, a native SQLite-backed
connection wrapper, and raw C ABI durable connection handles. The SQLite slice
creates metadata, transaction, tx metadata, and datom tables, writes one row
per datom through a SQLite transaction, reopens from disk, rebuilds in-memory
indexes, and then queries normally. The explicit persist API full-replaces
durable datom rows from the connection's current datom log; the SQLite
connection wrapper appends each successful transaction's report tx-data plus tx
metadata rows as it commits and rolls the in-memory connection back if the
durable append fails. A first SQLite storage benchmark now measures
single-transaction append latency, multi-entity append batches, full
reopen/index-rebuild cost, and query latency after reopen. The SQLite-backed
connection keeps a live SQLite handle open across transactions. Split batch
measurements show SQLite append is currently small compared with in-memory
transaction/index maintenance. Vev has a conservative append-only incremental
DB path for direct add-only transactions. A deeper split showed that ordinary
non-schema transactions were paying unnecessary full-schema validation cost;
that pass is now skipped when the transaction cannot alter schema validity.
Reportable DB snapshots now clone existing indexes/schema caches instead of
rebuilding every index from datoms. Append-only eligibility also has a
new-entity bulk-import shortcut. Ordered new-entity imports avoid formatted
entity/attr eligibility keys and extend the `eavt` entity table instead of
rebuilding it. A first Datalevin-style local write harness now measures pure
write throughput across batch sizes and mixed read/write behavior through the
SQLite-backed connection. A 10k-row durable run shows batch-100 writes are
acceptable for this phase, while batch-1 and mixed read/write are dominated by
per-commit immutable DB/index copying. The next durable milestone, when storage
work resumes, is replacing whole-array DB/index ownership copies with shared
immutable/chunked DB index storage, then scaling the write harness to direct
Datalevin `write-bench` comparisons.

## Phase 7: Dogfood

Goal:

- use Vev in one or two real local tools
- use Vev as a serious Kvist workload
- verify whether tx metadata plus `Tx_Report` is enough for app-level reactions

Questions:

- where is this better than SQLite directly?
- where is it worse?
- what debugging/inspection tools are immediately missing?
- is a separate event layer still necessary once tx metadata is in use?

## Phase 8: Interop boundary

Goal:

- package the engine as a native library
- define and expose a narrow stable C ABI
- expose EDN text and prepared query entrypoints
- build a small wrapper for JVM/Clojure use if still justified

Non-goal:

- making the CLI binary the only application integration path

Status: pulled forward and baseline complete. Vev now has a C ABI with
connection handles, immutable DB snapshot handles, EDN transaction/query/pull
entrypoints, prepared queries, typed statement bindings, named DB source
bindings, typed result access, direct result-row visitors, status/error
accessors, and DB-value retain/release. C, Python, Rust, Java, and Clojure
smokes exercise the native library, including durable SQLite
open/transact/close/reopen/query through the raw C ABI and the Python, Rust,
Java, and Clojure example wrappers. The ABI-vs-native benchmark covers small
lookups, DB snapshots, transaction reports, many-row results, direct row
visitors, nested pull-many values, and host-provided transaction function
callbacks. Further interop work should be driven by specific adapter needs,
especially packaging and richer host-specific APIs over the stable raw C
surface.

## Phase 9: Optional packaging expansion

Goal:

- evaluate whether Vev should also run behind an out-of-process packaging mode

Constraint:

- this should be a packaging/deployment mode built on the same semantic core
- it should not require redesigning transaction/query/pull semantics
- it should only happen if real usage justifies it

Possible shapes:

- simple server/daemon wrapper
- later, if justified, transactor/peer-style split

## Later: Replication and sync primitives

Immutable transactions and stable snapshots may make replication and
local-first sync natural later extensions.

Possible goal:

- export/import transaction logs
- identify database snapshots by stable basis/version
- support backup and copy workflows
- explore local-first sync without committing to distributed query execution

Non-goal:

- designing Vev around sync from phase 1
- CRDT-first semantics
- distributed Datalog query execution
- hosted sync service

## Current rule

Do not continue durable storage by solving:

- every backend
- every host language
- every deployment story

The in-memory semantic core, EDN/C ABI surface, performance baseline, and first
SQLite durable loop are now strong enough to start MusicBrainz/Day-of-Datomic
validation. The next durable-storage gate is no longer proving that SQLite can
open/write/close/reopen/query; that exists. The next durable-storage gate, when
we return to storage, is:

- shared immutable/chunked DB index storage avoids per-commit whole-array
  copies while preserving immutable snapshot semantics
- transaction boundaries and SQLite-backed report metadata rows remain durable
- immutable DB snapshot semantics remain visible through the native ABI
- MusicBrainz/Datomic workshop queries continue to validate correctness and
  performance on both in-memory and durable paths

Storage must preserve established semantics instead of reshaping them.
