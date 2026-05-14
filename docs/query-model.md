# Query Model

## Main choice

The canonical query syntax should be Datomic/DataScript-style Datalog data.

That means queries are represented externally as EDN-like values such as:

```clojure
[:find ?name
 :in $ ?email
 :where
 [?e :user/email ?email]
 [?e :user/name ?name]]
```

The project goal here is compatibility first:

- existing Datomic/DataScript tutorials should transfer directly where practical
- example queries should usually be copyable with minimal or no change
- any syntax divergence should be documented as a deliberate constraint-driven choice

## Native API stance

Plain strings are acceptable at the boundary, but they should not be the only
native shape.

Recommended split:

- external query syntax: text
- parsed query representation: typed AST
- execution API: parsed query object
- convenience API: parse-and-run helper

This split preserves Datomic syntax at the boundary while still letting the
engine use direct Odin data structures internally.

## Why not only strings?

Because the engine benefits from:

- parse once, run many times
- earlier validation
- better diagnostics
- easier planning/caching
- clearer separation between parsing and execution

## Proposed native API shape

At the Odin level:

```text
parse_query(text) -> Query
q(db, query, inputs...) -> Result_Set
q_text(db, text, inputs...) -> Result_Set
```

`q_text` can be convenience.
`q` with a parsed query should be the core path.

## Query scope for phase 1

Start with a tight slice:

- `:find`
- `:in`
- `:where`
- simple clauses `[?e :attr ?v]`
- scalar equality/predicate clauses as needed
- rules only after base query flow works
- pull after basic query/transact is stable

Phase 1 should bias toward the subset most commonly shown in Datomic/DataScript
examples so learning material transfers early.

## Pull model

Pull should be supported, but query and transact come first.
The sequence should be:

1. transact
2. query
3. pull
4. rules/advanced features

## OdinL role

OdinL may later provide a nicer surface for query literals, but that should be
treated as optional syntax sugar over the same parser and AST, not as the
engine's primary representation.
