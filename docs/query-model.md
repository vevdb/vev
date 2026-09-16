# Queries and pull

## Datalog

Queries are data and take explicit inputs:

```clojure
(d/q '[:find ?name
       :in $ ?active
       :where
       [?e :person/active ?active]
       [?e :person/name ?name]]
     db
     true)
```

Supported query features include:

- relation, collection, tuple, and scalar `:find`
- database and value inputs
- data, predicate, and function clauses
- `not`, `not-join`, `or`, and `or-join`
- aggregates
- rules, including recursive rules
- pull expressions in `:find`
- `:with` and return maps

The EDN frontend accepts vector and map query forms. Native APIs also provide
prepared queries and typed result access.

## Pull

Pull returns selected attributes for one or more entities:

```clojure
(d/pull db
  [:person/name
   {:person/friends [:person/name]}]
  person-id)
```

Supported patterns include:

- attribute keywords
- wildcard `*`
- nested joins
- reverse attributes
- recursion limits
- `:as`, `:default`, `:limit`, and `:xform`
- lookup refs

## Entity reads

Entity views provide lazy attribute lookup against one immutable database
value. Reference attributes can be followed without reading from the live
connection.

Use pull when the result shape is known. Use entity views for incremental
navigation.

## Index reads

Use `datoms`, `seek-datoms`, `rseek-datoms`, or `index-range` when index order
is part of the operation. See [Data model](data-model.md#indexes).

Exact function names and result types vary by package. See the package
repository for the host language.

## Bounded composite-key pages

`q-page` is a deliberately narrow keyset primitive for a query whose only
clause projects `[entity, unique-composite-tuple]`. It requires a non-empty
proper tuple prefix, uses a strict full-tuple `after` continuation, and returns
the tuple beside every candidate. The executor reads at most one page plus
lookahead from the current ordered index and, when present, a hard-bounded
SQLite or retained immutable shared overlay. An overlay above the implementation
bound is rejected before it is scanned; unsupported query programs and source
shapes fail instead of falling back to a generic Datalog materialization.

Every returned page includes a stable `:error-code` keyword independently of
its human-readable `:error` text. Success uses `:none`. Failures use one of
`:stale-basis`, `:invalid-request`, `:unsupported`, `:storage-error`, or
`:internal-error`. Callers should refresh a pinned continuation only for
`:stale-basis`; they must not infer error semantics from diagnostic text.
A retained legacy basis which exists but has no `:current-avet` page is
`:unsupported`, while SQLite read or corruption failures are `:storage-error`.

An immutable resident or shared snapshot may be filtered with `as-of` at its
own exact effective basis; that filter denotes the same snapshot and remains
pageable. An older filtered resident basis is still rejected explicitly because
it requires a versioned retained root. The executor never substitutes current
resident state for historical state.

The version-3 C ABI exports this primitive, and the in-tree Odin and Kvist
packages provide typed/data facades used by native applications. Java,
Clojure, Python, Rust, Go, Node.js, and the CLI do not yet expose a `q-page`
wrapper. Their ordinary query APIs remain unchanged; ABI-version agreement does
not imply identical high-level package surfaces.
