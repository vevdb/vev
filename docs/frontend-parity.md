# Frontend Parity

Vev has one engine and multiple frontends:

- Kvist literal macros for Kvist applications
- EDN text for the C ABI, host wrappers, CLI, and stored/prepared queries
- host data adapters, such as the Clojure package, that serialize ordinary host
  values to EDN text

The frontends must converge into the same internal `Query`, transaction data,
and pull-pattern representations. A feature should not exist only in one
frontend unless it is explicitly host syntax sugar.

## Current Parity Rule

When adding or changing syntax, update both paths:

- Kvist literal macro parser in `src/vev/macros.kvist`
- EDN text parser in `src/vev/edn_text.kvist`

Then add or extend a parity test in `src/vev_tests/vev_test.kvist`.

The current smoke parity test is
`frontend-parity-kvist-literals-edn-text-and-prepared`. It covers:

- transaction data through Kvist literals and EDN text
- query literals and EDN text queries
- prepared EDN text queries
- collection `:in` inputs
- pull patterns through Kvist literals and EDN text

## Checklist

Keep these feature groups aligned across frontends:

- data patterns, constants, lookup refs, and ignored `_`
- `:find`, scalar/collection/tuple/relation find specs, and return maps
- `:in` scalar, collection, tuple, relation, named DB, relation source, and
  rules inputs
- predicates, functions, `ground`, `get-else`, `get-some`, and `missing?`
- `not`, `not-join`, `or`, `or-join`, `and`, and source-qualified groups
- rules, required rule vars, source-qualified rule calls, and recursive rules
- aggregates and custom/native aggregate names
- pull expressions, pull pattern inputs, recursion, defaults, aliases, limits,
  reverse attrs, and xforms
- tx vector forms, tx maps, tempids, lookup refs, CAS, retracts, tx functions,
  and tx metadata

## Clojure Layer

The Clojure package is a host adapter, not a separate parser. Its public API is
intended to feel close to Datomic/DataScript:

```clojure
(with-open [conn (vev/create-conn "build/lib/libvev.dylib")]
  (vev/transact! conn [{:db/id 1 :user/name "Ada"}])
  (let [db (vev/db conn)]
    (vev/q '[:find ?name :where [?e :user/name ?name]] db)
    (vev/pull db [:user/name] 1)))
```

The adapter should keep serializing Clojure data to the EDN text frontend unless
a real use case proves that a separate host AST builder is needed.
