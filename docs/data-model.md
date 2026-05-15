# Data Model

## Goal

Define the semantic model Vev wants to preserve regardless of storage
backend or host language wrapper.

This is the layer that should feel recognizably Datomic/DataScript-like.

## Core concepts

### Datom

A datom is the atomic fact unit:

- entity id `e`
- attribute id or keyword `a`
- value `v`
- transaction id `tx`
- added flag `added`

Conceptually:

```text
(e, a, v, tx, added)
```

For phase 1, the engine may not need to expose every field at the public API
boundary, but this should remain the internal semantic shape.

### Entity id

Entities should use stable numeric ids internally.

Recommended direction:

- internal entity ids are 64-bit integers
- user-facing lookup refs and idents can resolve to entity ids
- tempids are only transaction-time placeholders

### Attribute

Attributes should be schema-backed identifiers with metadata such as:

- value type
- cardinality
- uniqueness
- index hints
- ref/non-ref

The public syntax can stay keyword-shaped, but the engine should normalize
attributes to compact internal ids where practical.

### Value

The value domain should support the minimum needed for Datomic/DataScript-like
semantics:

- integers
- floats
- booleans
- strings
- keywords/idents
- refs
- maybe instants later
- maybe UUIDs later
- tuples later if justified

The exact runtime representation can evolve, but value equality and ordering
rules must be pinned down early because indexes and queries depend on them.

## Database value

A DB value is an immutable snapshot used for reads.

It should contain enough to support:

- query
- pull
- entity lookup
- direct index iteration later

The important rule is:

- writes create a new DB value
- old DB values remain valid

Whether the implementation uses structural sharing, copy-on-write pages, or
another internal technique is secondary to preserving this semantic model.

## Connection

A connection is the mutable owner of the current DB state.

Expected responsibilities:

- hold the current committed DB snapshot
- serialize writes
- expose the current snapshot for readers
- coordinate persistence if durability is enabled

The connection is not the semantic database value.
The connection is the handle through which new database values are produced.

## Schema

Phase 1 should keep schema simple but explicit.

The engine should support:

- empty schema
- schema passed at DB creation/open time
- schema attributes that affect indexing and transaction behavior

First schema features worth supporting:

- `cardinality/one`
- `cardinality/many`
- `value_type/ref`
- uniqueness
- maybe component refs later

## Tempids

Transactions should support temporary ids.

The semantic rule should match mainstream Datomic/DataScript expectations:

- tempids are local to one transaction
- tempids resolve to stable entity ids during transact
- transaction results report the resolution map

This matters because it keeps transaction authoring pleasant even when internal
entity ids are engine-assigned.

## Transaction shape

The first transaction syntax should stay close to Datomic/DataScript:

```text
[:db/add e a v]
[:db/retract e a v]
```

Map/entity shorthand can be later if it helps ergonomics, but should not block
the first proof.

## Transaction metadata

Transactions should also support optional transaction-level metadata.

This is the first place Vev should capture domain context such as:

- actor/user id
- request id
- command source
- import batch id
- reason/cause
- correlation ids for application workflows

This should be modeled after Datomic's reified transaction entity rather than
as a separate event system in phase 1.

Important distinction:

- datoms describe state assertions or retractions
- transaction metadata describes the context of the transaction as a whole

That makes transaction metadata the preferred first mechanism for:

- provenance
- auditing
- request tracing
- "why did this transaction happen?"

In the embedded use case, the intended application flow is:

1. transact
2. receive `Tx_Report`
3. inspect `tx_data` and `tx_meta`
4. do follow-up work in application code

That means Vev does not need a built-in listener mechanism in the first
phase to support post-commit reactions.

It is not intended to model multiple distinct domain events inside one
transaction. If Vev eventually needs first-class event streams, that
should be a later layer built on top of the transaction model rather than
phase-1 core semantics.

## Transaction report

The transaction result should expose a DataScript-like report shape:

- db-before
- db-after
- tx-data
- tempids
- tx-meta

That shape is valuable because it:

- fits immutable snapshot semantics
- makes tooling/debugging easier
- matches existing user expectations from the Datomic family

## Pull and entity behavior

Pull should operate on DB snapshots and entity ids.

The boundary syntax should stay close to Datomic/DataScript pull syntax so that
existing examples and tutorials transfer directly where practical.

Entity-style lazy wrappers are optional.
The key requirement is semantic clarity, not cloning DataScript's exact
implementation style.

Reasonable phase order:

1. query by datoms
2. pull
3. entity wrappers if still justified

## Open design questions

These should be answered before implementation gets too wide:

- exact value ordering rules across mixed types
- exact keyword/ident representation
- whether transaction ids are purely internal or externally visible
- how much schema is mandatory vs inferred
