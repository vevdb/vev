# Scope

## First identity

Odinlog should start as an embedded Datalog database for native applications,
not as a general-purpose database server.

The first useful end state is:

- in-process
- one local database file
- immutable DB snapshots for reads
- transaction API for writes
- Datomic-flavored Datalog queries
- pull/entity-style reads

## What this project is

- a native engine
- a local-first database
- a graph/query-oriented alternative to writing ad hoc SQL by hand
- something that may later be consumed from Odin, Clojure, and other hosts

## What this project is not, at first

- a port of DataScript implementation details
- a network database
- a PostgreSQL replacement
- a distributed system
- a search engine
- a custom storage engine project for its own sake

## Why DataScript still matters

DataScript is the semantic reference for:

- transaction shape
- DB value model
- Datalog query model
- pull/entity behavior
- index expectations

But Odinlog should not assume:

- persistent Clojure collections
- Clojure protocol/type machinery
- Clojure laziness
- exact internal code structure

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
