# Query Model

## Main choice

The canonical query syntax should be Datomic/DataScript-style Datalog data.

The exact syntax compatibility target is summarized in
[docs/datomic-syntax.md](datomic-syntax.md).

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
engine use direct typed data structures internally.

## Why not only strings?

Because the engine benefits from:

- parse once, run many times
- earlier validation
- better diagnostics
- easier planning/caching
- clearer separation between parsing and execution

## Proposed native API shape

At the native API level:

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

## Current Kvist proof

The current in-memory implementation does not parse query text yet. It has a
Kvist query literal macro that lowers Datomic-shaped data to the typed `Query`
representation and evaluates that directly.

Supported now:

- `:find` with one or more variables
- `:with`
- scalar, collection, and tuple find syntax: `:find ?x .`, `:find [?x ...]`, `:find [[?x ?y]]`
- standalone and grouped `count`, `min`, `max`, `sum`, and `avg` aggregates
- datom clauses shaped like `[e a v]`
- source-var datom clauses shaped like `[$ e a v]` with single-source semantics
- variables in entity, attribute, and value positions
- wildcard `_` pattern terms
- literal entity, keyword, string, int, and bool values
- ident keywords in entity position
- entity refs as values
- joins through repeated variables
- positional `:in` variables
- `$` in `:in` as the current DB source
- collection `:in` variables shaped like `[?x ...]`
- tuple `:in` variables shaped like `[?a ?b]`
- relation `:in` variables shaped like `[[?a ?b]]`
- pull expressions in `:find`
- simple predicate clauses: `=`, `!=`, `<`, `<=`, `>`, `>=`
- `missing?`
- `not` groups
- `not-join`
- simple `or` groups with data-clause or `(and ...)` data-clause branches
- `or-join`
- top-level `and` groups over data clauses
- append-only transaction history with retractions hidden from current reads

Example:

```clojure
(v.q db
  [:find ?e ?name
   :where
   [?e :user/email "ada@example.com"]
   [?e :user/active _]
   [?e :user/name ?name]])
```

```clojure
(v.q db
  [:find ?name
   :where
   [:user/ada :user/name ?name]])
```

Vev accepts DataScript scalar and collection find syntax, currently lowered to
the same row-oriented `Result-Set` representation:

```clojure
(v.q db
  [:find ?name .
   :where
   [?e :user/email "ada@example.com"]
   [?e :user/name ?name]])
```

```clojure
(v.q db
  [:find [?name ...]
   :where
   [?e :user/name ?name]])
```

```clojure
(v.q db
  [:find [[?name ?age]]
   :where
   [?e :user/name ?name]
   [?e :user/age ?age]])
```

```clojure
(v.q db
  [:find (count ?e)
   :where
   [?e :user/name ?name]])
```

```clojure
(v.q db
  [:find ?age (count ?e)
   :where
   [?e :user/age ?age]])
```

```clojure
(v.q db
  [:find (min ?age) (max ?age)
   :where
   [?e :user/age ?age]])
```

```clojure
(v.q db
  [:find (sum ?age) (avg ?age)
   :where
   [?e :user/age ?age]])
```

`$` source-var clauses parse the same way against the current DB:

```clojure
(v.q db
  [:find ?name
   :where
   [$ ?e :user/email "ada@example.com"]
   [$ ?e :user/name ?name]])
```

```clojure
(v.q db
  [:find ?e ?name
   :in $ ?email
   :where
   [?e :user/email ?email]
   [?e :user/name ?name]
   [?e :user/age ?age]
   [(> ?age 30)]]
  "ada@example.com")
```

```clojure
(v.q db
  [:find ?name
   :in [?email ...]
   :where
   [?e :user/email ?email]
   [?e :user/name ?name]]
  ["ada@example.com" "grace@example.com"])
```

```clojure
(v.q db
  [:find ?e
   :in [?name ?age]
   :where
   [?e :user/name ?name]
   [?e :user/age ?age]]
  ["Ada" 37])
```

```clojure
(v.q db
  [:find ?e
   :in [[?name ?age]]
   :where
   [?e :user/name ?name]
   [?e :user/age ?age]]
  [["Ada" 37] ["Grace" 41]])
```

```clojure
(v.q db
  [:find (pull ?e [:db/id :user/name])
   :where
   [?e :user/email "ada@example.com"]])
```

```clojure
(v.q db
  [:find ?name
   :where
   [?e :user/name ?name]
   (not [?e :user/active true])])
```

```clojure
(v.q db
  [:find ?name
   :where
   [?e :user/name ?name]
   [(missing? $ ?e :user/email)]])
```

```clojure
(v.q db
  [:find ?name
   :where
   [?e :user/name ?name]
   (not [?e :user/friend ?friend]
        [?friend :user/active false])])
```

```clojure
(v.q db
  [:find ?name
   :where
   [?e :user/name ?name]
   (not-join [?e]
     [?e :user/friend ?friend]
     [?friend :user/name ?name])])
```

```clojure
(v.q db
  [:find ?name
   :where
   (or [?e :user/email "ada@example.com"]
       [?e :user/email "grace@example.com"])
   [?e :user/name ?name]])
```

```clojure
(v.q db
  [:find ?name
   :where
   (or (and [?e :user/active true]
            [?e :user/age 37])
       [?e :user/email "grace@example.com"])
   [?e :user/name ?name]])
```

```clojure
(v.q db
  [:find ?name
   :where
   (and [?e :user/active true]
        [?e :user/age 37])
   [?e :user/name ?name]])
```

```clojure
(v.q db
  [:find ?label
   :where
   [?e :user/name ?name]
   (or-join [?e ?label]
     (and [?e :user/active true]
          [?e :user/name ?label])
     (and [?e :user/active false]
          [?e :user/email ?label]))])
```

Basic clauses now use in-memory indexes. Text parsing, rules, and advanced
predicates remain later work. Results are deduped by returned values, with
`:with` vars included in the dedupe key but not returned.
Aggregates currently support `count`, `min`, `max`, `sum`, and `avg`.
Numeric aggregates are currently integer-only; `avg` uses integer division.

## Pull model

Pull should be supported, but query and transact come first.
The sequence should be:

1. transact
2. query
3. pull
4. rules/advanced features

The pull syntax itself should stay close to Datomic/DataScript pull syntax.
The preferred project stance is:

- pull patterns at the boundary should look like familiar Datomic/DataScript patterns
- internal representation can be a typed pull AST
- any unsupported pull feature should be documented as "not implemented yet",
  not replaced with a different surface syntax

## Literal Syntax Role

The Kvist literal macro is useful for native Kvist callers and for exercising
Kvist's macro system, but it should remain syntax sugar over the same typed
query representation that text/EDN parsing will eventually produce.
