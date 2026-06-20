# Scope

## First identity

Vev should start as an embedded-first Datalog database for native
applications, not as a general-purpose database server.

The first useful end state is:

- in-process
- one local database file
- Kvist-first engine source with readable Odin output
- Odin/Kvist source-package use where appropriate
- native library artifact for other host languages
- CLI binary for tooling and inspection
- immutable DB snapshots for reads
- transaction API for writes
- Datomic-flavored Datalog queries
- Datomic/DataScript-shaped transaction and pull syntax
- pull/entity-style reads

This does not mean embedded-only forever.
Server mode should remain a plausible later packaging/deployment option, but it
should not shape phase-1 priorities.

Possible later out-of-process shapes include:

- a simple daemon/API wrapper around the same engine
- a stronger transactor/peer-style split

Neither should be treated as the current commitment.

## What this project is

- a native engine
- a serious Kvist workload for building a real data-intensive application
- a local-first database
- a graph/query-oriented alternative to writing ad hoc SQL by hand
- something that should be consumable from Kvist, Odin, Clojure, and other hosts
- a native library first, with a thin CLI binary for operations and inspection
- a system that should preserve functional-style semantics at its boundaries

## What this project is not, at first

- a port of DataScript implementation details
- a network database
- a PostgreSQL replacement
- a distributed system
- a search engine
- a custom storage engine project for its own sake
- a CLI-only database product

Not being a network database at first does not mean the core should become so
process-specific that a server wrapper later becomes awkward or unnatural.

## Why DataScript still matters

DataScript is the semantic reference for:

- transaction shape
- DB value model
- Datalog query model
- pull/entity behavior
- index expectations

Datomic/DataScript should also be the primary syntax reference at the boundary.
Vev should prefer tutorial and example compatibility over inventing a new
native-facing surface unless a divergence is clearly justified.

But Vev should not assume:

- persistent Clojure collections
- Clojure protocol/type machinery
- Clojure laziness
- exact internal code structure

## Semantic style

Vev should prefer functional semantics at the public boundary:

- database snapshots are immutable read values
- transactions conceptually take data and return data
- queries conceptually take a DB value and return results
- pull conceptually takes a DB value, pattern, and entity id and returns data

This does not require purely functional implementation style.
Inside the engine, local mutation is acceptable and expected where it improves:

- clarity
- memory behavior
- construction of intermediate results
- parser and index-building code

The important distinction is:

- semantic model should be functional in character
- implementation strategy may be imperative internally

## Transportable boundaries

To keep future server mode viable, Vev should prefer semantic boundaries that
can be represented as plain data:

- transaction input
- transaction report
- query input
- query result
- pull input
- pull result
- transaction metadata

This does not mean designing RPC or wire protocols now.
It means avoiding phase-1 decisions that depend on:

- arbitrary in-process callbacks as part of transaction semantics
- opaque host-language object identity in public APIs
- result shapes that are difficult to serialize cleanly

## First use cases

- local CLI/TUI applications
- tooling metadata stores
- note/graph/personal information apps
- small single-node web apps later

## First backend choice

SQLite should be the first durable backend.

Reasons:

- simple deployment
- good local durability story
- easy inspection and backup
- well-understood failure modes

LMDB is a plausible later backend if the engine benefits from a more direct
ordered key/value storage model.
