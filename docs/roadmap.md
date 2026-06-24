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
prepared APIs, and host-facing EDN/C ABI query paths. Remaining work is
concentrated in exact parser diagnostics/object rendering, C ABI callback
registration shapes for host-provided transaction/listener behavior, and
measured recursive-rule/large-query optimization.

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
literals. C, Python, Rust, Java, and Clojure smokes now exercise the EDN path
through the native library. The remaining work is exact parser API parity and
any wrapper ergonomics demanded by real host usage.

## Phase 5: Durable proof

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

Status: not started. Keep this postponed until the in-memory compatibility,
API, and performance baseline are stable.

## Phase 6: Dogfood

Goal:

- use Vev in one or two real local tools
- use Vev as a serious Kvist workload
- verify whether tx metadata plus `Tx_Report` is enough for app-level reactions

Questions:

- where is this better than SQLite directly?
- where is it worse?
- what debugging/inspection tools are immediately missing?
- is a separate event layer still necessary once tx metadata is in use?

## Phase 7: Interop boundary

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
especially listener/report callback registration and higher-level host wrappers.

## Phase 8: Optional packaging expansion

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
right first. SQLite durability comes after DataScript-level behavior is stable
enough that the storage layer can preserve semantics instead of reshaping them.
