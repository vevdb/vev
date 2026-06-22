# Design Principles

## Goal

Capture the small set of project-level engineering decisions that should remain
stable as Vev grows.

## Functional semantics, imperative implementation

Vev should think functionally about semantics and allow local mutation in
implementation.

That means the public-facing model should look like:

- data in
- data out
- immutable read values
- explicit write results
- no hidden ambient effects in the semantic core

Examples:

- `db_before + tx_input -> tx_report`
- `db + query -> results`
- `db + pull_pattern + eid -> pulled_data`

## Database values are the product

Vev's primary bet is not local-first sync or graph queries. The core programming
model is that a database snapshot is an immutable value.

Applications should be able to:

- obtain a DB snapshot
- pass it through pure domain logic
- produce transaction data
- commit at the edge

This keeps Vev useful anywhere facts, relationships, history, and application
logic benefit from value semantics.

## What this does not mean

This is not a requirement for purely functional implementation style.

Vev is being built in Kvist, and the implementation should freely use local
mutation where it improves:

- clarity
- directness
- memory locality
- temporary result construction
- parser and index code

Examples of acceptable internal mutation:

- appending to dynamic arrays while building results
- mutating temporary maps/tables for tempid resolution
- building indexes with local buffers
- reusing scratch memory during query execution

## Why this is the right fit

This style matches both the project domain and the language:

- database semantics benefit from immutable snapshots and explicit transitions
- Kvist should be tested as a direct, data-oriented systems language here
- forcing purely functional implementation style would likely make the code
  harder to read and reason about

## Rule of thumb

When choosing a design:

- keep the semantic contract as value-oriented and explicit as possible
- keep mutation local to implementation details
- avoid making internal mutation patterns leak into the public model

## Relationship to Datomic/DataScript compatibility

This principle works well with the Datomic/DataScript direction:

- boundary syntax stays familiar
- boundary semantics stay value-oriented
- internal implementation remains native and typed

That is the intended balance for Vev.
