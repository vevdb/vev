# Query Model

Vev's query surface targets Datomic/DataScript-style Datalog data. EDN text and
prepared query handles are the canonical portable API because Vev is meant to be
used from Kvist, C, Clojure, Java, Python, Rust, Go, Odin, and other native
hosts.

Kvist literal macros are a convenience frontend. They should lower to the same
typed query representation as the EDN parser, not become a separate semantic
implementation.

## Public Shape

The intended API split is:

- parse EDN text into a typed `Query`
- run prepared queries against immutable DB values
- provide parse-and-run helpers for convenience
- expose result handles and typed/column APIs for host languages

Typical shapes:

```text
q(db, "[:find ?name :where [?e :user/name ?name]]")
prepare_query("[:find ?e :in $ ?email :where [?e :user/email ?email]]")
q_prepared(db, prepared, inputs)
```

This is analogous to SQL strings plus prepared statements, but the language is
Datomic/DataScript Datalog data and execution stays inside Vev.

## Supported Query Semantics

The current implementation supports the core Datomic/DataScript tutorial and
DataScript-compatibility surface:

- `:find`, `:with`, `:in`, and `:where`
- scalar, tuple, collection, relation, and return-map find specs
- aggregate and grouped aggregate find expressions
- pull find expressions and runtime pull patterns through `:in`
- data clauses, attr-existence clauses, wildcards, lookup refs, reverse attrs,
  source-qualified clauses, and named DB sources
- scalar, collection, tuple, relation, relation-source, and named-source inputs
- predicate and function clauses over supported built-ins and registered native
  functions
- `ground`, `get-else`, and `get-some`
- `not`, `not-join`, `or`, and `or-join`
- rules, including recursive positive rules

Unsupported or deliberately guarded areas should fail explicitly rather than
silently taking a resident-only or query-specific shortcut.

## Internal Model

The execution model is a typed relation engine:

- `Query-Relation` is the logical relation representation.
- Typed columns are the preferred physical representation.
- Compatibility `Binding` rows remain available through an audited
  materialization boundary.
- `DB-Read-Source` abstracts over resident DBs, durable SQLite snapshots, and
  named sources.
- `DB-Index-View` abstracts over resident index arrays and persisted/chunked
  index cursors.
- Physical operators consume and produce `Query-Relation` values instead of
  bypassing the engine with query-specific result APIs.

The important design rule is that performance work should broaden these generic
operators. A fast path is acceptable when it is a reusable physical operator or
a common indexed shape, not when it only recognizes one benchmark query string.

## Source-Backed Execution

Source-backed durable reads should use the same logical query model as
in-memory reads. The source-backed path currently covers:

- ordinary indexed data-clause scans
- bound joins, entity/value/ref projections, and same-entity star scans
- relation-source inputs and named relation sources
- source-qualified clauses inside top-level queries, `not`, `or`, and rule
  bodies
- predicates, functions, `ground`, `get-else`, and `get-some` over typed rows
- pull and aggregate result rendering from typed rows
- many rule-call and recursive-rule paths through memo/delta relations

Profiled source-backed tests assert zero binding materialization for the common
query shapes that have been moved onto typed operators.

## Remaining Query Work

1. Keep replacing row/binding fallbacks with typed operators.
   The fallback boundary is explicit now; remaining uses should either become a
   reusable typed operator or stay documented as compatibility behavior.

2. Continue rule-engine columnar work.
   Recursive memo/delta tables still keep row tables as the semantic source of
   truth. The next substantial rule step is typed-first memo storage and broader
   semi-naive behavior for recursive components.

3. Broaden named/source input planning.
   Relation-source inputs are typed for common cases, including bound primitive
   keyed joins. More named-source combinations should move to reusable source
   operators when tests or profiles expose fallback.

4. Keep host result APIs aligned.
   Internal typed/column execution should feed C ABI, Java, Clojure, Python,
   Rust, Go, and future Odin clients without forcing unnecessary row
   materialization.

5. Preserve parser/API compatibility.
   EDN text, prepared query handles, and Kvist literal macros must continue to
   support the same syntax surface.
