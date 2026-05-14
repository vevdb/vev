# Architecture

## Core principle

The semantic engine should own the database model.
Storage and foreign-language integration should sit at the edges.

That means the architecture should separate:

1. semantic core
2. query frontend
3. storage adapter
4. host/interop layer

## Proposed layers

### 1. Semantic core

This layer owns:

- datoms
- schema
- immutable DB snapshots
- transactions
- indexes
- query planning/execution
- pull/entity behavior

This is the most important layer to stabilize.

### 2. Query frontend

This layer owns:

- EDN-like reader for query data
- Datalog parser
- query AST
- validation and normalized query forms

The canonical external query syntax should stay close to Datomic/DataScript.
Inside the engine, queries should be compiled to typed Odin data structures.

### 3. Storage adapter

This layer owns:

- opening and closing durable stores
- physical index persistence
- transaction durability
- crash recovery semantics
- compaction/migration concerns

The semantic core should not become SQLite-shaped.

### 4. Interop layer

This layer owns:

- native public API
- optional C ABI
- later JVM/Clojure wrapper

The C ABI should be a packaging boundary, not the internal architecture.

## In-memory model

The in-memory engine should start with Datomic/DataScript-style index families:

- EAVT
- AEVT
- AVET
- optional VAET when refs/backrefs require it

The initial rule should be:

- optimize for clarity first
- use sorted indexes
- preserve immutable snapshot semantics

## Connection model

The public model should distinguish:

- connection: mutable handle used for writes
- DB snapshot: immutable value used for reads

That is one of the most valuable pieces of the DataScript/Datomic mental model
and should remain central.

## Backend strategy

### Phase 1

- in-memory only

### Phase 2

- SQLite-backed persistence

Recommended initial posture:

- store engine-owned index pages or normalized records through a narrow storage API
- do not leak SQL row concepts into the semantic layer

### Later

- optional LMDB backend

LMDB may fit the index model better, but should not complicate the first
durable proof.
