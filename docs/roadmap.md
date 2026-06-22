# Roadmap

## Phase 0: Spec

Current phase.

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

## Phase 2: Pull and entity reads

Goal:

- basic pull
- entity-style access if it still fits the design cleanly

Preferred boundary:

- Datomic/DataScript-compatible pull syntax first
- typed internal pull representation second

## Phase 3: DataScript parity

Goal:

- continue porting the DataScript test suite namespace by namespace
- close in-memory query, transaction, pull, schema, and index gaps
- keep Kvist literal APIs and internal typed forms aligned

Non-goal:

- durable storage
- SQLite integration
- server/transactor packaging

## Phase 4: Portable query frontend

Goal:

- parse EDN text for queries, pull patterns, and transaction data
- expose parse-and-run helpers for non-Kvist callers
- expose prepared query handles for repeated execution
- lower parsed EDN into the same internal structures as Kvist literals

This phase is required for broad C/Odin/host-language consumption. It should
not create a second query engine.

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

Do not start by solving:

- every backend
- every query feature
- every host language
- every deployment story

Get the in-memory semantic core right first.
