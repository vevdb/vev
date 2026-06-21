# Datomic Syntax Compatibility

## Goal

Pin down the Datomic/DataScript syntax that Vev should preserve at the
boundary so that existing tutorials, examples, and habits transfer with minimal
friction.

This document is about boundary syntax and user-visible semantics.
It does not require Vev to copy Datomic's internal implementation.

## Compatibility rule

Vev should preserve Datomic/DataScript syntax wherever practical for:

- transaction data
- transaction metadata
- query data
- pull patterns

Divergence should only happen when native embedding constraints or a simpler,
clearer engine boundary make it necessary.

## Transaction data

The official Datomic transaction reference describes transaction data as data,
not strings, with three main transaction forms:

- list forms
- map forms
- transaction functions

The first-phase Vev direction should be:

- support Datomic list forms first
- support Datomic map forms next
- defer transaction functions until the core transaction model is stable

### Transaction list forms

These are the core transaction forms Vev should match first:

```clojure
[:db/add e a v]
[:db/retract e a v]
[:db/retract e a]
[:db/retractEntity e]
```

Important compatibility notes from Datomic:

- `:db/retract` may omit the value
- `:db/retractEntity` retracts the entity's current facts
- transaction data is authored as an ordered collection for convenience
- semantically it is still one atomic information set

### Entity identifiers in tx data

Datomic allows several ways to identify entities in transaction data. Vev
should aim to preserve this model:

- entity ids
- tempids
- lookup refs
- idents

Examples:

```clojure
[:db/add 42 :user/name "Anna"]
[:db/add "u1" :user/name "Anna"]
[:db/add [:user/email "anna@example.com"] :user/name "Anna"]
[:db/add :some/ident :flag/enabled true]
```

Current Vev supports numeric entity ids, string tempids, lookup refs, and
idents in list-form entity positions. Repeated use of the same tempid in one
transaction resolves to the same entity and the transaction report exposes the
resolved `tempids` mapping. Lookup refs require a current `:db/unique` schema
attr. Idents resolve through current `:db/ident` facts.

### Map forms

Map forms are part of the Datomic transaction surface and should be supported
once the first list-form proof is stable.

Examples:

```clojure
{:db/id 42
 :user/name "Anna"
 :user/email "anna@example.com"}
```

```clojure
{:db/id "u1"
 :user/name "Anna"}
```

Current Vev supports map forms in tx data when `:db/id` is first, with one to
three attrs after it, and the common two-attr shape with `:db/id` last. Nested
component maps and arbitrary key order remain later work.

### Transaction metadata

Vev should follow Datomic's reified transaction model as closely as
practical.

The key Datomic convention is the reserved tempid:

```clojure
"datomic.tx"
```

This names the current transaction entity.

Example:

```clojure
[{:db/id "datomic.tx"
  :request/id "req-123"
  :tx/reason :profile-edit}
 [:db/add 42 :user/name "Anna"]]
```

This should be the default transaction-context model for Vev instead of
inventing a different metadata syntax.

Current Vev supports this shape: facts targeting `"datomic.tx"` are returned
as `tx_meta` entries instead of ordinary datoms.

## Query data

The official Datomic query reference defines queries as Datalog data.
Vev should preserve that boundary shape.

The core query shape is:

```clojure
[:find ...
 :with ...
 :in ...
 :where ...]
```

Phase 1 should focus on the most tutorial-heavy subset first.

### Phase 1 query subset

The first supported subset should be:

- `:find`
- `:in`
- `:where`
- data patterns
- simple predicate expressions only if clearly needed
- simple `or` clauses
- `and` branches inside `or`

### Query data patterns

Datomic data patterns have the general shape:

```clojure
[src-var? (variable | constant | '_')+]
```

The most important practical pattern for Vev phase 1 is:

```clojure
[?e :attr ?v]
```

Phase 1 should also be prepared for these common variations:

```clojure
[?e :user/email "anna@example.com"]
[?e :user/email _]
```

### Deferred query features

These are part of Datomic's query language, but Vev should defer them until
the core path is stable:

- `:with`
- rules
- `or-join`
- top-level `and`
- function expressions
- aggregates beyond a very small starter subset
- multiple result-shape conveniences beyond what is needed first

The important rule is:

- unsupported Datomic syntax should be documented as unsupported
- it should not be replaced with a Vev-specific query surface

## Pull patterns

The official Datomic pull reference defines pull patterns as EDN data.
Vev should preserve that surface.

### Pull pattern core grammar

The key Datomic pull shape is:

```clojure
[attr-spec+]
```

Where `attr-spec` can be:

- attribute name
- wildcard
- map spec
- attribute expression

### Phase 1/2 pull subset

Start with the smallest useful Datomic-shaped subset:

- plain attribute names
- `:db/id`
- nested map specs for refs
- one entity at a time

Examples:

```clojure
[:user/name :user/email]
```

```clojure
[:db/id :user/name]
```

```clojure
[:user/name {:user/friends [:user/name]}]
```

### Deferred pull features

These are part of Datomic pull and should remain syntax goals, but can be
deferred:

- recursion limits such as `{:person/friends 6}` or `{:person/friends ...}`
- richer attr options such as `:xform`

Again, the rule is to defer support, not invent different syntax.

## Internal representation

The compatibility rule applies at the boundary.
Inside Vev, parsed forms should become typed data structures:

- tx data -> typed transaction input
- query data -> typed query AST
- pull data -> typed pull AST

This is the intended split:

- Datomic-compatible syntax outside
- native typed structures inside

## First implementation priority

The implementation order should remain:

1. list-form tx data
2. tx metadata via `"datomic.tx"`
3. small query subset
4. small pull subset
5. map-form tx data
6. broader Datomic syntax surface

That keeps the first engine small while still locking in the right syntax
direction.

## Official references

This compatibility direction is based on the official Datomic references:

- Transaction data: https://docs.datomic.com/transactions/transaction-data-reference.html
- Transaction model: https://docs.datomic.com/transactions/model.html
- Query data reference: https://docs.datomic.com/query/query-data-reference.html
- Pull reference: https://docs.datomic.com/query/query-pull.html
