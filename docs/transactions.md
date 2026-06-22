# Transactions

## Goal

Define the first transaction model for Vev without overcommitting to a
separate event-sourcing subsystem too early.

The exact syntax compatibility target is summarized in
[docs/datomic-syntax.md](datomic-syntax.md).

The intended direction is Datomic-like:

- datoms remain the core state model
- transactions are reified
- transactions can carry metadata
- callers receive a transaction report
- transaction syntax should stay close to Datomic/DataScript transaction data

## Base transaction shape

The first transaction input should stay close to Datomic/DataScript:

```text
[:db/add e a v]
[:db/retract e a v]
[:db/retract e a]
[:db/retractEntity e]
[:db.fn/retractAttribute e a]
[:db.fn/retractEntity e]
```

This keeps the semantic core direct and easy to inspect.

It also preserves the value of existing Datomic/DataScript tutorials and tx-data
examples. Divergence here should be treated as costly and only justified by
real embedding constraints.

`[:db/retractEntity e]` and `[:db.fn/retractEntity e]` expand to retract
operations for the entity's current facts in the DB snapshot before the
transaction.

`[:db/retract e a]` and `[:db.fn/retractAttribute e a]` expand to retract
operations for current values of that entity+attribute in the DB snapshot before
the transaction.

## Reified transactions

Every successful transaction should have a transaction identity.

Conceptually, each datom is associated with:

- entity `e`
- attribute `a`
- value `v`
- transaction `tx`
- added flag `added`

That transaction identity is useful for:

- auditing
- debugging
- tracing state changes
- attaching transaction-level context

## Transaction metadata

Vev should support optional metadata on the transaction as a whole.

This is the preferred first mechanism for carrying domain context such as:

- actor id
- request id
- command source
- import source
- reason/cause
- correlation ids

Examples of the kind of meaning this can capture:

- "user 7 changed this"
- "this came from the nightly CRM import"
- "this transaction belongs to request req-123"
- "this was triggered by profile-edit UI flow"

The preferred direction is to mirror Datomic's transaction-context model as
closely as practical rather than inventing a new transaction-metadata syntax.

## Facts vs transaction metadata

These two things solve different problems.

Facts answer:

- what is true now?

Transaction metadata answers:

- under what context did this transaction happen?

Example:

```text
[:db/add 42 :user/name "Anna"]
```

This says the current name is `Anna`.

Transaction metadata can add:

- actor `7`
- reason `:profile-edit`
- request id `req-123`

That is usually enough for:

- provenance
- audit context
- operational tracing

without introducing a separate event stream yet.

## Why not a first-class event layer yet

A separate event model is only justified if the application truly needs to
distinguish:

- resulting state
- from domain happenings as first-class records

For many applications, transaction metadata plus tx reports is enough.

That is the default stance Vev should take first:

- prefer tx metadata
- let application code react to transaction results directly
- delay event-stream design until a real gap appears

## Transaction report

The transaction result should expose a shape close to:

- `ok`
- `error`
- `db-before`
- `db-after`
- `tx-data`
- `tempids`
- `tx-meta`

This gives callers:

- commit success/failure without exceptions
- immutable before/after snapshots
- the exact fact delta
- tempid resolution
- transaction context in one place
- the boundary where embedded applications react after commit

String tempids in list-form tx data are resolved during transact and returned
through `tempids`.

Lookup refs in list-form entity positions resolve through `:db/unique` attrs.
Missing lookup refs fail the report with `ok=false`.

The reserved tempids `:db/current-tx`, `"datomic.tx"`, and `"datascript.tx"`
name the current transaction entity. Facts targeting that entity are written
as datoms and are also mirrored into `tx-meta` for convenient transaction
report inspection.

In the embedded single-process case, this is usually enough:

1. transact
2. receive `Tx_Report`
3. inspect `tx-data` and `tx-meta`
4. perform application work such as SSE push, cache updates, or projection refresh

That keeps reaction logic in application code rather than introducing a
subscription mechanism inside the database core.

## Later extension point

If Vev later needs stronger same-transaction behavior, the next step should
be deterministic transaction-time derivation configured as part of the
connection or engine, not arbitrary user callbacks passed into `transact`.

That is a later design step, not a phase-1 requirement.
