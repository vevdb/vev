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

Plain EDN strings and prepared parsed handles are the primary portable surface.
That is the main API compatibility target because Vev is intended to be used
from C, Odin, Kvist, and other native environments. Kvist literal syntax should
remain a convenience frontend, not the canonical compatibility path.

Recommended split:

- external query syntax: EDN text
- parsed query representation: typed AST
- execution API: parsed query object
- convenience API: parse-and-run helper
- Kvist convenience API: literal macro lowering to the same typed AST

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

The portable C/Odin-facing API should use the same split:

```text
vev_query_text(db, "[:find ?e :where [?e :name \"Ivan\"]]", inputs) -> result
vev_prepare_query(conn, "[:find ?e :where [?e :name ?name]]") -> query_handle
vev_query_prepared(db, query_handle, inputs) -> result
```

This is analogous to SQL strings plus prepared statements, but the parsed value
is Datomic/DataScript Datalog data instead of SQL text.

## Query scope for phase 1

The current in-memory query surface supports the Datomic/DataScript-shaped
forms that appear in common tutorials and most upstream semantic tests:

- `:find`
- scalar, tuple, collection, aggregate, pull, and return-map find specs
- `:with`
- `:in`
- `:where`
- queries with no `:where`, for input-only find/aggregate cases
- simple clauses `[?e :attr ?v]`
- attr-existence shorthand clauses `[?e :attr]`
- predicate and function clauses over the supported built-ins
- scalar, collection, tuple, relation, relation-source, and named DB inputs
- pull find expressions
- `not` / `not-join`
- `or` / `or-join`
- rules, including recursive rules

The remaining query work is parser/API exactness and measured performance for
recursive rules, large relation sources, aggregates, and index-backed planning.

## Current implementation

The current in-memory implementation has two query frontends:

- a text API, `q-text` / `q-text-with-inputs` / `q-text-with-sources` /
  `parse-query-text!`, that parses the portable EDN query surface
- prepared text query values, `prepare-query-text` /
  `prepare-query-text-with-sources`, plus `q-prepared` /
  `q-prepared-with-inputs` / `q-prepared-with-sources`, for callers that want
  to parse once and run repeatedly
- prepared pull pattern values, `prepare-pull-pattern-text`, plus
  `pull-prepared`, `pull-prepared-lookup-ref`, `pull-many-prepared`, and
  `pull-many-prepared-lookup-refs`
- a Kvist query literal macro that lowers Datomic-shaped data to the same typed
  `Query` representation

## Query Engine Strategy

Vev should follow DataScript's query architecture as the semantic baseline:

- clauses produce relations
- the query context is a set of relations, sources, and rules
- repeated variables are resolved by relation joins
- input bindings, DB datom patterns, predicates, functions, `or`, `not`, and
  rules should all lower to relation operations
- physical optimizations should live under that relation layer

The first relation-engine path is now implemented for data-clause, predicate,
and function-clause queries, including ordinary scalar, collection, tuple,
relation `:in` inputs, and relation-source clauses over `:in` sources such as
`$rows`. It builds one `Query-Relation` per input binding and datom/source
pattern, joins those relations with generic relation product/join operations,
applies predicates as relation filters, applies function clauses as relation
extensions, and then uses the existing result renderer. Joins use DB-aware
entity equality so entity ids, ints, and lookup refs compare the same way the
older evaluator does. This is intentionally conservative: named DB sources,
`not`, `or`, aggregates, rules, and synthetic primary collection DB
predicate/function queries still use the older binding-expansion evaluator
until their DataScript-style relation handlers are ported.

The older query-shape recognizers are not the long-term query strategy. They
are useful prototypes for physical operators that should be folded under the
relation engine:

- primitive entity/int/string result columns become typed relation/result
  storage
- same-entity star query paths become a generic star/merge-scan operator
- threshold and predicate paths become generic predicate/filter operators
- recursive rule fast paths become planned recursive/semi-naive relation
  operators

Near-term query work should expand the relation engine in this order:

1. Named DB sources: source-specific data patterns that produce relations from
   the chosen DB source.
2. `not`/`or`: relation subtraction and union with DataScript-compatible free
   variable checks.
3. Rules: relation-oriented rule calls, then measured recursive/semi-naive
   behavior.
4. Physical storage: replace generic `Binding` tuples with compact typed
   relation columns while keeping the same logical relation API.

The transaction side has the same split:

- a text API, `transact-text` / `parse-tx-text!`, that parses common
  DataScript-shaped EDN tx-data strings into normal `Tx-Data` before execution
- immutable DB-value text APIs, `with-text` and `db-with-text`, for
  DataScript-style `d/with` behavior without mutating a connection
- prepared tx-data values, `prepare-tx-text` plus `transact-prepared`,
  `with-prepared`, and `db-with-prepared`, for parsing EDN tx-data once and
  applying it through the same transaction engine
- registered transaction-function variants exist for both connection and
  immutable DB-value APIs, including `transact-text-with-string-fns`,
  `with-text-with-string-fns`, `db-with-text-with-string-fns`,
  `transact-prepared-with-string-fns`, `with-prepared-with-string-fns`, and
  `db-with-prepared-with-string-fns`
- a Kvist tx-data literal macro used by `transact`

Supported now:

- `:find` with one or more variables
- `:with`
- scalar, collection, and tuple find syntax: `:find ?x .`, `:find [?x ...]`, `:find [?x ?y]`
- return-map find markers: `:keys`, `:strs`, and `:syms`
- standalone and grouped `count`, `count-distinct`, `min`, `max`, `sum`, `avg`,
  `median`, `variance`, `stddev`, and named custom aggregates through
  `(aggregate ?f ?x)`
- text queries with simple data clauses, relation find, collection find,
  strings, booleans, ints, keywords, symbols, wildcards, and source vars
- text query inputs for scalar vars, collections, tuples, relations, and
  no-`:where` input-only queries
- text relation-source inputs such as `:in $rows` with 2/3/4/5-wide row
  clauses like `[$rows ?e ?a ?v ?tx ?op]`
- text query named DB sources for multi-source joins and source-specific pull
- text query rules through `q-text-with-rules`, covering ordinary rule calls,
  recursion, predicates, and function clauses
- text transactions with `:db/add`, `:db/retract`, `:db/retractEntity`,
  `:db.fn/retractAttribute`, `:db.fn/cas`, map tx-data, lookup refs, tempids,
  idents, generated map ids, nested maps through ref attributes, and
  schema-aware map vector values for cardinality-many and tuple attrs
- datom clauses shaped like `[e a]` or `[e a v]`
- lookup refs in text datom entity positions, such as `[[:name "Petr"] :age ?age]`
- source-var datom clauses shaped like `[$ e a]` or `[$ e a v]` with single-source semantics
- reverse attrs in datom clauses, such as `:_user/friend`
- variables in entity, attribute, and value positions
- wildcard `_` pattern terms
- literal entity, keyword, string, int, and bool values
- int values reused in entity position resolve as entity ids
- ident keywords in entity position
- lookup refs in entity position, such as `[:user/email "ada@example.com"]`
- entity refs as values
- numeric value literals on attrs declared `:db.type/ref` resolve as entity refs
- joins through repeated variables
- positional `:in` variables
- `$` in `:in` as the current DB source
- collection `:in` variables shaped like `[?x ...]`
- tuple `:in` variables shaped like `[?a ?b]`
- relation `:in` variables shaped like `[[?a ?b]]`
- `_` holes in tuple and relation inputs
- pull expressions in `:find`, with optional `$` source var
- predicate clauses: `=`, `!=`, `not=`, `<`, `<=`, `>`, `>=`, including chained comparisons
- `missing?`
- `get-else`
- `get-some`
- `ground` scalar and collection bindings
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
   [[:user/email "ada@example.com"] :user/name ?name]])
```

```clojure
(v.q db
  [:find ?source
   :where
   [2 :_user/friend ?source]])
```

```clojure
(v.q db
  [:find ?name
   :where
   [:user/ada :user/name ?name]])
```

Vev accepts DataScript scalar, collection, and tuple find syntax. `q` still
returns the row-oriented `Result-Set`; use `q-scalar`, `q-collection`, or
`q-tuple` when a shaped result is more convenient:

```clojure
(v.q-scalar db
  [:find ?name .
   :where
   [?e :user/email "ada@example.com"]
   [?e :user/name ?name]])
```

```clojure
(v.q-collection db
  [:find [?name ...]
   :where
   [?e :user/name ?name]])
```

```clojure
(v.q-tuple db
  [:find [?name ?age]
   :where
   [?e :user/name ?name]
   [?e :user/age ?age]])
```

Return-map markers are accepted. `q-keys`, `q-strs`, and `q-syms` return Vev
keyed rows:

```clojure
(v.q-keys db
  [:find ?name ?age :keys name age
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
   [$ ?e :user/email]
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
  [:find ?younger ?older
   :where
   [?a :user/age ?younger]
   [?b :user/age ?older]
   [(< ?younger 40 ?older)]])
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
  [:find ?e
   :in [[?name _]]
   :where
   [?e :user/name ?name]]
  [["Ada" 1] ["Grace" 2]])
```

```clojure
(v.q db
  [:find (pull ?e [:db/id :user/name])
   :where
   [?e :user/email "ada@example.com"]])
```

```clojure
(v.q db
  [:find (pull $ ?e [:db/id :user/name])
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
  [:find ?email
   :where
   [?e :user/name "Ada"]
   [(get-else $ ?e :user/email "none") ?email]])
```

```clojure
(v.q db
  [:find ?attr ?value
   :where
   [?e :user/name "Ada"]
   [(get-some $ ?e :user/email :user/age) [?attr ?value]]])
```

```clojure
(v.q db
  [:find ?name
   :where
   [(ground ["Ada" "Grace"]) [?name ...]]
   [?e :user/name ?name]])
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

Basic clauses use in-memory indexes. Text parsing, rules, and predicate/function
clauses share the same typed query representation as Kvist literals. Results
are deduped by returned values, with `:with` vars included in the dedupe key
but not returned.
Aggregates currently support `count`, `count-distinct`, `min`, `max`, `sum`,
`avg`, `median`, `variance`, `stddev`, and named custom aggregate functions
through `(aggregate ?f ?x)`.

## Pull model

Pull is part of the active query surface, including pull find expressions,
pattern inputs, lookup refs, reverse refs, recursion, defaults, limits, aliases,
component expansion, and named xforms.

The pull syntax itself should stay close to Datomic/DataScript pull syntax.
The preferred project stance is:

- pull patterns at the boundary should look like familiar Datomic/DataScript patterns
- internal representation can be a typed pull AST
- any unsupported pull feature should be documented explicitly, not replaced
  with a different surface syntax

## Literal Syntax Role

The Kvist literal macro is useful for native Kvist callers and for exercising
Kvist's macro system, but it should remain syntax sugar over the same typed
query representation that text/EDN parsing will eventually produce.

The project should treat the EDN parser and prepared APIs as the primary
compatibility surface. The Kvist literal API should stay aligned, but a feature
that only works through the macro is not done for Vev's long-term goals.
