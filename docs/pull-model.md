# Pull Model

## Goal

Define how Vev should support Datomic/DataScript-style pull without
inventing a new surface syntax.

The exact syntax compatibility target is summarized in
[docs/datomic-syntax.md](datomic-syntax.md).

The main rule is:

- pull syntax at the boundary should stay as close as practical to
  Datomic/DataScript pull patterns
- internal representation can be plain typed data structures

## Why pull matters

Pull is one of the most useful parts of the Datomic/DataScript user model.

It gives callers:

- attribute-oriented entity reads
- nested traversal for refs
- a stable, tutorial-friendly way to ask for shaped data

For Vev, the biggest value is not novelty. It is compatibility:

- existing examples should transfer
- application code should not need a custom Vev-only pull dialect
- future JVM/Clojure interop becomes easier

## Canonical boundary syntax

The canonical external pull syntax should be Datomic/DataScript-style pull
data.

Examples:

```clojure
[:user/name :user/email]
```

```clojure
[:user/name {:user/friends [:user/name]}]
```

```clojure
[:db/id :user/name]
```

These should be understood as boundary syntax goals even if phase 1/2 supports
only a subset at first.

## Native API stance

As with queries, plain text is acceptable at the boundary, but it should not
be the engine's only internal representation.

Recommended split:

- external pull syntax: text
- parsed pull pattern: typed structure
- execution API: parsed pull pattern
- convenience API: parse-and-run helper

Conceptually:

```text
parse_pull(text) -> Pull_Pattern
pull(db, pattern, eid) -> Value
pull_text(db, text, eid) -> Value
```

`pull_text` is convenience.
`pull` with a parsed pattern should be the core path.

## Phase 1/2 supported subset

Start with the smallest useful subset that matches common tutorials:

- plain attribute names
- `:db/id`
- one entity id at a time

Current Kvist proof:

```clojure
(v.pull db [:db/id :user/name :user/email] 1)
```

It also supports reverse ref attrs in the Datomic style:

```clojure
(v.pull db [:_user/friend] 2)
```

Delay these until later unless they become immediately necessary:

- nested map form for refs
- wildcard `*`
- recursion limits
- attribute options and aliases
- multi-entity pull-many helpers

This keeps the early implementation small while preserving syntax direction.

## Pull semantics

Pull should:

- operate on immutable DB snapshots
- read by entity id
- respect schema/cardinality rules
- return nested data shaped by the pattern

The engine does not need to clone every DataScript implementation trick.
It does need to preserve the user-facing meaning.

## Entity wrappers

Entity-style wrappers are optional and secondary.

Priority order should be:

1. direct transact semantics
2. direct query semantics
3. direct pull semantics
4. only then, optional entity-style convenience wrappers

That keeps Vev centered on explicit data access instead of lazy wrapper
magic.

## Compatibility rule

Any pull divergence should be treated as costly.

Preferred approach:

- keep the Datomic/DataScript syntax
- document unsupported forms as unsupported
- only introduce syntax differences when there is a concrete embedding or
  implementation reason

The project should not create a Vev-specific pull language just because Kvist
makes a different surface easy to invent.
