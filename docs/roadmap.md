# Roadmap

## Phase 0: Spec

Current phase.

Outcomes:

- scope pinned down
- architecture pinned down
- query model pinned down
- interop direction pinned down

## Phase 1: In-memory proof

Goal:

- create connection
- transact small dataset
- run simple query
- return immutable DB snapshots for reads

Success shape:

- datoms exist
- core indexes exist
- one or two query forms work

## Phase 2: Pull and entity reads

Goal:

- basic pull
- entity-style access if it still fits the design cleanly

## Phase 3: Durable proof

Goal:

- open local DB
- transact facts
- close process
- reopen DB
- query facts successfully

Backend:

- SQLite first

## Phase 4: Dogfood

Goal:

- use Odinlog in one or two real local tools

Questions:

- where is this better than SQLite directly?
- where is it worse?
- what debugging/inspection tools are immediately missing?

## Phase 5: Interop boundary

Goal:

- define and expose a narrow stable C ABI
- build a small wrapper for JVM/Clojure use if still justified

## Current rule

Do not start by solving:

- every backend
- every query feature
- every host language
- every deployment story

Get the in-memory semantic core right first.
