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

- durable storage
- SQLite integration
- server/transactor packaging

Status: current compatibility gate. The broad in-memory surface is present:
query, pull, tx-data, schema, lookup refs, tuples, indexes, parser text paths,
prepared APIs, and host-facing EDN/C ABI query paths. The local compatibility
suite currently passes 354 tests. Remaining work is concentrated in exact
parser diagnostics/object rendering, query/rule planner maturity,
MusicBrainz/Datomic workload coverage, and higher-level host wrapper
ergonomics.

Current batch order:

1. Query planner/operator layer: replace local heuristic planning and isolated
   fast paths with reusable physical operators for indexed scan, bind join,
   hash/semi join, anti join, rule calls, projection, and aggregation.
2. Generic recursive rules: build component/SCC-local semi-naive evaluation,
   reusable/materialized rule relations where appropriate, and bound-recursive
   query support that can later grow toward magic-set-style rewriting.
3. DataScript/Datalevin read benchmark integration: add Vev to the Datalevin
   `benchmarks/datascript-bench` shape through the Clojure API, starting with
   q1/q2/q2-switch/q3/q4/qpred1/qpred2. Use this to compare Vev against
   Datomic, DataScript, and Datalevin on the same common read workloads.
4. Parser/API exactness: make malformed EDN query, rule, pull, return-map, and
   tx-data shapes fail predictably through the portable text/prepared APIs.
5. Host wrapper ergonomics: keep C as the stable raw ABI, but make Clojure and
   Java feel close to Datomic/DataScript for common tutorials, including
   listener/report callbacks where useful.
6. MusicBrainz/Datomic comparison: import a Day of Datomic / mbrainz-shaped
   dataset, run equivalent Datomic workshop queries against Vev and Datomic,
   compare result sets first and performance second.

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

Why before durability:

- MusicBrainz is a real Datomic-shaped workload, not a synthetic microbench
- it tests whether Vev can follow existing Datomic teaching material
- it gives a shared correctness/performance target before SQLite storage
- it exercises large in-memory indexes, immutable DB values, EDN text APIs, and
  host wrappers under realistic pressure

Initial scope:

- start with the Day of Datomic dataset or a deterministic MusicBrainz slice
- keep the import path simple and repeatable
- store expected query results in Vev tests or benchmark fixtures
- report Datomic vs Vev timings as comparative ratios, not raw claims

Status: not started. This should happen after the next parser/callback cleanup
batch, after the DataScript/Datalevin read benchmark is running, and before
durable SQLite work.

## Phase 5b: External Optimizer Benchmarks

Goal:

- use established external benchmark shapes to check that Vev is moving toward
  a real embedded Datomic-like database, not just passing local microbenchmarks
- compare against Datomic, DataScript, Datalevin, PostgreSQL, and SQLite where
  the benchmark already does that
- use benchmark failures to drive general planner/operator work

Order:

1. DataScript bench: immediate. Add Vev to the Datalevin
   `benchmarks/datascript-bench` shape. This is small, in-memory, and directly
   exercises common Datomic/DataScript query forms through the Clojure API.
2. JOB bench: after the query planner/operator layer is underway. This is a
   large join-order benchmark and should validate costed planning, join
   ordering, predicates, ranges, aggregates, and large import behavior.
3. Write bench: after durable storage exists. This should measure transaction
   throughput, commit latency, batching, sync/WAL behavior, and mixed
   read/write behavior against SQLite and Datalevin.

Current stance:

- DataScript bench is a near-term development benchmark.
- JOB bench is the planner milestone.
- Write bench is the durability milestone.
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

Status: not started. Keep this postponed until parser/API exactness,
MusicBrainz/Datomic workload coverage, and rule/query performance are stable
enough that the storage layer can preserve semantics instead of reshaping them.

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
smokes exercise the native library, and the ABI-vs-native benchmark covers
small lookups, DB snapshots, transaction reports, many-row results, direct row
visitors, nested pull-many values, and host-provided transaction function
callbacks. Further interop work should be driven by specific adapter needs,
especially higher-level host wrappers over the raw C ABI.

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

Do not start durable storage by solving:

- every backend
- every host language
- every deployment story

Get the in-memory semantic core, EDN/C ABI surface, and performance baseline
right first. The next durable-storage gate is not "all possible DataScript host
details"; it is:

- portable parser and tx-data APIs reject bad input predictably
- recursive rules and large relation queries have measured, acceptable behavior
- MusicBrainz/Datomic workshop queries have correctness coverage and comparison
  benchmarks
- Clojure/Java examples can follow common Datomic/DataScript tutorial shapes

SQLite durability comes after those gates, so storage preserves established
semantics instead of reshaping them.
