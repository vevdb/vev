# Indexes

## Goal

Define the first index set and what each index is responsible for.

The purpose is not to prematurely optimize everything.
The purpose is to avoid building a semantic core with vague storage access
patterns.

## First index family

The initial in-memory model should be Datomic/DataScript-like:

- EAVT
- AEVT
- AVET
- optional VAET

## Why these indexes

### EAVT

Primary uses:

- entity-centric reads
- pull
- walking all attrs for one entity
- transaction-time entity inspection

### AEVT

Primary uses:

- attribute-centric scans
- clauses where attribute is known first
- efficient query joins on attribute

### AVET

Primary uses:

- exact lookup by attribute + value
- unique attribute resolution
- lookup refs
- many query clauses where value-bound searches matter

### VAET

Primary uses:

- reverse reference traversal
- backrefs
- component-like graph walking

This can be deferred if ref-heavy features are deferred, but the architecture
should leave room for it.

## Ordering

Each index should be ordered lexicographically by its component order.

Examples:

- EAVT: `(e, a, v, tx)`
- AEVT: `(a, e, v, tx)`
- AVET: `(a, v, e, tx)`
- VAET: `(v, a, e, tx)` for ref values

The exact internal encoding can differ, but the access semantics should match
these orderings.

## Snapshot model

Indexes must support immutable DB snapshots.

That implies:

- reads operate against stable index views
- writes produce new index state
- old index state remains readable

Possible implementation strategies later:

- structural sharing over sorted trees
- append-and-rebuild chunks
- copy-on-write pages
- backend-managed persistence snapshots

Phase 1 does not need the final strategy, but it must preserve immutable read
semantics.

## In-memory phase

The in-memory proof should choose the simplest structure that preserves:

- sorted iteration
- exact lookup
- range/prefix scans
- immutable snapshot reasoning

The likely question is not "what is most clever?" but:

"What is the simplest structure that lets query evaluation be honest?"

## Persistence phase

The durable backend should not define the semantic index model.

Instead:

- the semantic layer owns the meaning of EAVT/AEVT/AVET/VAET
- the storage adapter chooses how those are persisted

For SQLite, this likely means:

- engine-owned logical indexes
- adapter-owned physical tables/pages/records

For LMDB later, this likely means:

- one ordered key/value space per logical index, or an equivalent partitioning

## Query implications

The query planner should be built with these assumptions in mind:

- clauses with bound entity like EAVT
- clauses with bound attribute/value like AVET
- broad attribute scans like AEVT
- reverse ref work may want VAET

This is one reason the index model should be specified before the planner is
implemented.

## First implementation rule

Phase 1 should probably start with:

- EAVT
- AEVT
- AVET

and only add VAET when:

- refs are solid
- pull/backref needs are clearer
- planner needs justify it
