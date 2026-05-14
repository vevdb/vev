# Design Principles

## Goal

Capture the small set of project-level engineering decisions that should remain
stable as Spor grows.

## Functional semantics, imperative implementation

Spor should think functionally about semantics and allow local mutation in
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

## What this does not mean

This is not a requirement for purely functional implementation style.

Spor is being built in Odin, and the implementation should freely use local
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
- Odin is good at direct, explicit, data-oriented implementation
- forcing purely functional implementation style would likely make the code
  harder to read and reason about in this language

## Rule of thumb

When choosing a design:

- keep the semantic contract as value-oriented and explicit as possible
- keep mutation local to implementation details
- avoid making internal mutation patterns leak into the public model

## Relationship to Datomic/DataScript compatibility

This principle works well with the Datomic/DataScript direction:

- boundary syntax stays familiar
- boundary semantics stay value-oriented
- internal implementation remains Odin-native

That is the intended balance for Spor.
