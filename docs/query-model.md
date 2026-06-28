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

Prepared queries now also own a cached `Rule-Call-Plan` array. When rules are
attached at prepare time, or when an already-prepared query is combined with a
prepared rules value for execution, Vev plans each rule call once and reuses the
plan by query-step index. This keeps recursive-rule execution on the same
generic engine path, but avoids rebuilding dependency graphs and transitive-rule
recognizers for every prepared run.

Rule-call planning now also builds a reusable rule-name index for the planning
batch. Dependency graphs are still ordinary rule-name graphs, and execution
keeps the original rule order, but the planner no longer rescans the full rule
array just to discover which names exist or which rule branches share a name.
The same dependency graph now produces explicit strongly connected component
metadata. Recursive checks use those components instead of pairwise
mutual-reachability probes, which is the planner-side structure needed before a
component-local semi-naive rule evaluator. Plain positive recursive rules now
have a generic component-local delta slice: each recursive rule-call position is
evaluated against the current per-iteration delta table while the other rule
calls read the accumulated memo tables. It handles data clauses plus rule calls,
with no source-qualified calls and no predicates/functions/negation/disjunction
inside the component. Memo tables keep set-backed primitive binding keys for
duplicate detection, with structural scans only as a fallback for non-keyable
outputs. The specialized linear and alternating transitive paths remain faster
physical operators and run before the generic memo/delta path.
Inside the generic memo/delta evaluator, plain DB data-clause steps now execute
through the relation engine: the current rule body relation is joined with the
clause relation and deduped at the existing per-step boundary. Rule-call memo
and delta tables still keep binding rows as their semantic source of truth, but
each memo entry now owns cached typed relation views for the accumulated rows
and current delta rows. Broad rule-call application can reuse those cached views,
project them into the call's output variables, and join that relation against
the current body relation. Small current relations keep the row-wise matcher
because that avoids touching a large memo table for a handful of rows.
Distinct-var rule calls use a typed projection path, so the broad path can stay
in typed columns through the join. This remains an intermediate state rather
than a fully columnar recursive-rule engine: row tables remain authoritative,
but valid typed memo/delta views are appended in place for primitive-compatible
outputs and invalidated only when a new output cannot fit the typed column
layout. Multi-branch broad rule-call application now also has a typed union
accumulator: compatible typed branch joins append into one typed output relation,
and the evaluator materializes to row bindings only when a branch falls off the
typed path or has an incompatible layout.
The linear transitive recognizer also handles a common derived-edge shape where
the recursive edge is a non-recursive two-hop rule, such as `adv` in
Datalevin's math-bench:

```clojure
[(adv ?x ?y) [?x :person/advised ?d] (author ?d ?y)]
[(author ?d ?c) [?d :dissertation/cid ?c]]
[(anc ?x ?y) (adv ?x ?y)]
[(anc ?x ?y) (adv ?x ?z) (anc ?z ?y)]
```

This lowers to a derived adjacency list and then uses the same transitive
operator as direct ref attrs. It is a physical rule operator, not a complete
solution for rule planning.

Non-recursive helper rules now also have an initial relation-native
materialization path. The relation engine can materialize pure clause/rule-call
rules once and join them into the current relation instead of invoking the rule
per input row. Two important general shapes have direct builders:

- derived two-hop edges, where one attr points to an intermediate entity and a
  second attr supplies the output value
- same-entity cardinality-one two-attr projections, using a merge scan over the
  sorted attr/entity index when all datoms are current

This is enough to turn math-bench Q2/Q3 from repeated rule expansion into three
materialized rule calls. Direct physical builders now populate typed relation
columns while keeping compatibility binding rows. Distinct-variable rule-call
projection can read those typed columns directly, and single-branch
materialized helper rules are cached per query execution so repeated calls such
as `(univ ?x ?u)` / `(univ ?y ?u)` do not rebuild the same unprojected relation.
Typed relations can also apply fixed-attribute bound data clauses such as
`[?e :person/name ?n]` through direct `eavt` lookups from typed entity columns.
Single-column typed entity/int joins hash on numeric keys, keeping common id
joins out of the allocation-heavy string-key path. For single-branch cached or
direct physical helper rules, distinct-variable rule-call projection can return
a typed relation directly instead of projecting to generic bindings, deduping,
and rebuilding typed columns immediately. Compound typed joins whose first
shared variable is an entity id can hash that leading entity and verify the
remaining shared columns in candidate buckets, which is useful for Datomic-style
joins such as `?entity/?attribute-value` pairs without relying on formatted
compound string keys. Binary `=` / `!=` predicates over two typed variables can
compare relation columns directly, avoiding per-row `Value` wrapper resolution
for common filters. Predicate and `not` filters now move surviving compatibility
`Binding` rows into the filtered relation instead of cloning each row, matching
the existing dedupe/memo ownership pattern. Multi-branch materialized helper
rules now also have a typed unique accumulator for compatible projected branch
relations, so branch output can dedupe in typed columns instead of always
materializing through generic `Binding` rows before rebuilding a relation. The
initial query relation now also has a typed-first path for no-input, scalar
`:in`, and simple collection `:in` queries. Ordered scalar/collection input
specs and legacy scalar/collection inputs build typed rows directly for the
common one-collection product shape, while source, lookup-ref collection,
multi-collection product, tuple, relation, duplicate, and non-columnar inputs
fall back to the compatibility binding expansion path. The broad path still
pays for generic `Binding` construction in joins/results when an operator falls
off the typed path. The next performance step is typed-first memo storage and
more operators that can consume typed columns without materializing
compatibility rows.
Typed joins now also detect provably empty cases before falling back to generic
binding joins: if either side is already an empty typed relation, or if common
typed columns have incompatible primitive kinds, the engine returns an empty
typed joined relation directly.
Nested tuple destructuring for `ground` and typed function outputs also has a
typed path for non-fanout binding patterns; collection destructuring still uses
the compatibility path because it expands one input row into multiple rows.

## Query Engine Strategy

Vev should follow DataScript's query architecture as the semantic baseline:

- clauses produce relations
- the query context is a set of relations, sources, and rules
- repeated variables are resolved by relation joins
- input bindings, DB datom patterns, predicates, functions, `or`, `not`, and
  rules should all lower to relation operations
- physical optimizations should live under that relation layer

The first relation-engine path is now implemented for the main DataScript query
operators: data clauses, predicates, function clauses, `not`, `or`, rule calls,
`ground`, `get-else`, `get-some`, and aggregates. This includes ordinary
scalar, collection, tuple, relation `:in` inputs, and relation-source clauses
over `:in` sources such as `$rows`, and named DB source clauses. It now runs
ordinary supported `$` queries through the same path, not only named-source
queries. Execution is step-driven: each where step transforms the current
relation, so selective bound clauses can run before broad clauses or rules when
the query order says so. Current relations that share variables with a later
datom clause can use indexed bind-clause expansion instead of materializing a
full datom-pattern relation and joining it back.
Relations carry per-attribute source metadata so lookup refs in joins resolve
against the appropriate named DB source when needed. This is intentionally
conservative: source-qualified synthetic primary collection DB
rule/predicate/function queries still use the older binding-expansion evaluator
until their DataScript-style source-aware relation handlers are ported.

Relation joins now include a DataScript-shaped hash join for primitive common
variables. The join path supports compound keys across one or more shared
variables, normalizes entity IDs and integer IDs consistently with Vev query
equality, and falls back to the semantic nested join when values are not safely
representable as primitive join keys. Typed relation joins use the same
entity/int normalization, including single-column joins, so the fast typed path
can handle common Datomic-style joins where entity ids appear as either entity
values or integer ids.
The compatibility binding evaluator uses the same predicate equality when it
merges existing variable bindings, including rule-call outputs. This matters
for DataScript/Datomic-style ref values: a rule can bind `?x` from an integer
attribute in one clause and from a ref/entity-valued attribute in another, and
unification must treat those as equal when they identify the same entity.

The older query-shape recognizers are not the long-term query strategy. They
are useful prototypes for physical operators that should be folded under the
relation engine:

- primitive entity/int/string result columns become typed relation/result
  storage
- same-entity star query paths become a generic star/merge-scan operator
- threshold and predicate paths become generic predicate/filter operators
- recursive rule fast paths become planned recursive/semi-naive relation
  operators

## Data-Oriented Relation Storage

The next physical query milestone is to bring an Odin/game-engine style,
data-oriented layout into Vev's query intermediates. The current `Binding`
representation is flexible, but it stores rows as named value bags and pays for
string lookups, per-row metadata, and generic `Value` materialization in hot
paths.

Priority order:

1. Struct-of-arrays typed relation columns. Store hot relation data as
   contiguous typed columns such as `[]u64`, `[]i64`, `[]string`, and `[]Value`
   instead of arrays of row-shaped bindings.
2. Small integer variable IDs in execution plans. Resolve query symbols once,
   then let operators address columns by integer indexes rather than repeated
   string comparisons.
3. Scratch/query-local allocation. Allocate per-query temporary columns,
   hash tables, and work buffers from an explicit query scratch lifetime where
   possible, so cleanup is bulk and predictable.
4. Typed hash joins without string compound keys. Keep compound string keys as
   the conservative first step, but move hot joins toward typed key structs or
   parallel key columns.
5. Dense bitsets/boolean arrays for bounded visited and de-duplication paths,
   especially rule traversal and entity-ID keyed workloads.

The migration should be incremental: keep the logical `Query-Relation` API,
add typed storage behind it, port one operator family end-to-end, benchmark,
then fold existing q1/q2/q3/q4 typed fast paths into the general relation result
path.

First slice implemented: `Typed-Relation` stores primitive relation columns in
struct-of-arrays form and the compound primitive hash join can build/probe join
keys from those columns for multi-variable joins. The relation engine can now
also render simple no-pull/no-aggregate result rows directly from typed
relation columns, with the old `Binding` renderer retained as the semantic
fallback. This is still an incremental migration: relation operators continue
to carry `Binding` tuples as the compatibility representation, so the larger
performance work remains keeping scans, joins, filters, projection, and
deduplication typed end-to-end.

Second slice implemented: `Query-Relation` and `Typed-Relation` now carry
relation-local attr indexes, so relation operators can resolve vars to column
positions without repeated linear attr scans. The typed primitive hash join
also uses a fast merge after typed key equality, appending only right-side
non-common vars instead of re-running generic binding agreement checks. This
is still a general operator improvement, not a benchmark-specific recognizer.

Third slice implemented: ordinary DB data clauses in the relation engine now
derive their relation attrs directly from the clause shape and build
`Query-Relation` rows directly from candidate datoms. This avoids the previous
extra pass where clause bindings were materialized first and relation attrs
were rediscovered by scanning row bindings. The semantic matcher still owns
the per-datom correctness checks, so this is a relation construction change,
not a shortcut around Datalog semantics.

Fourth slice implemented: `Query-Relation` can now carry optional cached typed
columns alongside the compatibility `Binding` tuples. Direct DB data-clause
relations populate those columns when all emitted values are representable in
the typed column layout; unsupported values simply invalidate the cache and
continue through the ordinary tuple path. Typed joins and result projection can
reuse cached columns instead of rebuilding a `Typed-Relation` by scanning every
binding row.

Fifth slice implemented: typed primitive hash joins now preserve the typed
column cache on their result relation. The join still emits compatibility
`Binding` tuples, but it appends each joined row to typed output columns at the
same time, so downstream typed joins and simple result projection can stay on
the cached-column path instead of rebuilding typed data from row bindings.

Sixth slice implemented: predicate filters now preserve cached typed columns
when the input relation already has them. Matching rows are copied into fresh
typed columns in lockstep with the compatibility tuple output. If a row cannot
be represented in the typed layout, the operator drops back to the ordinary
tuple-only relation, so the cache remains an optimization rather than a semantic
requirement.

Seventh slice implemented: `not` / `not-join` now execute as direct relation
filters instead of materializing a temporary binding array and rebuilding the
relation from scratch. Since `not` cannot introduce new attributes, the output
relation preserves the input attr/source layout and keeps the typed column cache
alive for rows that survive the anti-join.

Eighth slice implemented: the binding-to-relation constructors now attempt to
materialize typed columns for the completed relation rows. This gives operators
that still rebuild through `Binding` arrays, such as function clauses, `ground`,
`get-else`, `get-some`, `or`, and rule-call outputs, a way back into the typed
relation path when their output values fit the primitive column layout.

Ninth slice implemented: join fallback paths now also re-materialize typed
columns for their tuple output. Cartesian product, primitive hash-join fallback,
and semantic nested-join fallback still use the existing correctness logic, but
their results no longer permanently lose the typed column cache when the output
values fit the primitive relation layout.

Tenth slice implemented: typed result rendering now resolves `:find` and
`:with` variables to relation column indexes once before scanning result rows.
The row loop reads values directly from typed columns by integer index instead
of doing per-row variable-name lookups through the relation attr map.

Eleventh slice implemented: typed primitive hash joins now precompute output
column sources and fill typed result columns directly from left/right typed
input columns by row index. The compatibility `Binding` row is still emitted
for callers and fallback paths, but it is built from the same column-source
table. Typed output no longer has to scan the merged row by variable name to
populate the column cache.

Twelfth slice implemented: predicate filters can now evaluate built-in
predicate operators directly against cached typed relation columns by row and
column index. Native callback predicates and dynamic operator vars still fall
back to the compatibility `Binding` path, but ordinary comparison, boolean,
numeric, regex, and string predicates no longer need per-row variable-name
lookup when typed columns are present.

Thirteenth slice implemented: Cartesian/product joins with no shared variables
now have a typed operator path. When both inputs have cached typed columns, the
product precomputes output column sources and fills typed output columns from
left/right row indexes directly. The compatibility `Binding` row is still
emitted from the same column-source table, and the older semantic product
remains as fallback.

Fourteenth slice implemented: final typed result rendering now borrows the
`Query-Relation` typed column cache directly instead of cloning it into a
temporary `Typed-Relation` just to read `:find` and `:with` values. This keeps
the final projection on column indexes while removing one whole-column copy
from the successful typed result path.

Fifteenth slice implemented: the indexed star-query prototypes now include a
borrowed entity-stream merge helper for all-current cardinality-one workloads.
It aligns fixed-value `AVET` filter ranges and projected `AEVT` attr ranges by
entity id, then emits q3/q4-style projected rows from the aligned streams. This
started in the indexed prototype layer and now also has a relation-producing
operator for the same-entity one-output and two-output shapes. The relation
engine can return a normal typed `Query-Relation` from the aligned streams, so
ordinary result rendering and host paths no longer need a separate
result-specialized shortcut to benefit from this scan shape.

Sixteenth slice implemented: the shared-value entity join prototype now
materializes the projected cardinality-one output attr once as an entity-keyed
table, then scans the right-side join-value `AVET` ranges and probes that table
instead of doing a fresh entity/attr lookup for every right candidate. This is
the q5 pressure-point shape from `datascript-bench`, but the operator lesson is
general: separate index scans and projected attr materialization should feed a
join/projection operator instead of repeating random per-row index probes.

Seventeenth slice implemented: typed product and typed hash-join operators now
borrow `Query-Relation` typed columns directly when both inputs already have a
typed column cache. The older `Typed-Relation` conversion path remains as
fallback for untyped relation inputs, but the common typed path no longer clones
whole columns just to build join keys and output rows.

Eighteenth slice implemented: typed product and typed hash joins now produce
typed-only rows. Both the borrowed `Query-Relation` path and the converted
`Typed-Relation` fallback path fill output columns directly and leave
compatibility `Binding` rows empty until a binding-only consumer explicitly
materializes through `query-relation-materialized-bindings`. This is no longer
an ad hoc join patch: the relation representation now has the basic invariant
and API boundary needed for typed-only rows:

- `query-relation-row-count` reports the logical row count, independent of
  physical storage
- operators declare whether they consume typed rows, binding rows, or either
- binding-only operators materialize through one audited helper
- typed joins, typed predicates, typed bound clauses, and typed result
  rendering can pass typed-only relations end-to-end
- aggregate, function, `or`, `not`, pull, and fallback rule paths must either
  gain typed handlers or call the materialization helper explicitly

The broad-rule math workloads are the right proving ground for this work:
Q2 stresses a large unfiltered typed relation followed by a bound lookup, while
Q3 proves the value of keeping the relation typed through a selective predicate
before that final lookup.

Initial typed-only groundwork is now in place. `query-relation-row-count`
reports logical relation size from cached typed rows when available, and the
main relation-to-bindings bridge goes through `query-relation-materialized-bindings`.
This removes hidden row-count assumptions before introducing typed-only
producers.

The first audited typed-only producer is distinct-variable rule-call projection.
When a rule call already returns typed columns and the call output variables are
distinct, projection now forwards cloned typed columns without also emitting one
compatibility `Binding` per row. Downstream binding consumers must use
`query-relation-materialized-bindings`, while typed consumers can stay columnar.
This is intentionally narrower than the reverted typed-join experiment: it keeps
the typed-only boundary at a well-defined projection operator and is guarded by
the math benchmark row-count checks.

Predicate filtering now honors that boundary. Supported predicates can evaluate
directly against typed rows and return typed-only output; unsupported predicates
materialize through `query-relation-materialized-bindings` before using the
binding predicate path. This keeps correctness explicit while allowing rule
projection followed by selective predicates to avoid compatibility row
allocation.

The profiled query result boundary also treats compatibility bindings as an
optional representation. Typed result rendering runs directly from typed
columns. Simple aggregate rendering can also read group keys, `:with` keys,
dedupe keys, and aggregate input values directly from typed relation columns
for no-pull queries; custom aggregate callbacks, function-var aggregates,
limit-var aggregates, pull find expressions, and unsupported term shapes still
fall back through the audited binding materialization helper. When the planner
can trust relation uniqueness, typed result rendering now also skips the
per-row dedupe value array and pushes projected result values directly.
Pull find rendering uses the same result boundary: the renderer can resolve
pull entity vars from typed relation rows and render pull results directly from
the chosen DB/source without first materializing the whole relation as
`Binding` rows. If a pull pattern, source, or entity cannot be resolved from the
typed row, the query falls back to the binding renderer.

Binding-oriented relation-engine fallback operators now have an explicit
typed-row boundary. Unsupported or final API paths still materialize through
`query-relation-materialized-bindings`, while common row-local operators stream
one typed input row at a time and write their outputs back into typed columns.

Function clauses now have a streaming typed fallback replacement for scalar
built-in output. When the input relation is typed, the operator evaluates
built-in function semantics directly from typed input columns, writes typed
output columns, and falls back to materialization only if the produced values
cannot be represented columnarly or the function shape needs destructuring,
tuple output, dynamic op vars, or native callbacks. This keeps common scalar
function clauses such as `(count ?name) ?len`, string helpers, arithmetic
helpers, and simple `keyword` / `name` / `str` transforms on the typed path
after rule projection.

`get-else` also has a streaming typed operator. It resolves the entity term
directly from typed input columns, applies the existing default-value semantics
per row, and appends only newly produced output values as typed columns. That
keeps Datomic-style fallback attribute lookups on the typed path after rule
projection without converting each input row to a `Binding`.

`get-some` follows the same pattern: it resolves the input entity directly from
typed columns, finds the first present attribute in the declared order, checks
already-bound output vars against typed row values, and appends the selected
attribute identity and value as typed output columns.

`not` now streams over typed rows as a filter. Single ordinary DB-clause
negative groups are tested as a typed anti-join: the clause scan resolves
directly from typed row values and checks candidate datoms without building a
`Binding`. Plain multi-clause unsourced DB negative groups now run each outer
typed row through a branch-local typed relation, so inner clause matching can
continue to use typed clause scans. Nested, `not-join`, and relation-source
negative groups still materialize one binding at a time to reuse existing
semantics, but the surviving output stays typed instead of allocating a full
intermediate binding relation.

`ground` now streams over typed rows too. Scalar ground clauses resolve their
source term directly from typed input columns and append only newly produced
output values as typed columns. Tuple, collection, relation, and input-binding
ground shapes still reuse the existing binding semantics one input row at a
time, then append any produced rows back into typed columns.

`or` now streams over typed rows with fanout. Simple branch groups made of one
ordinary DB clause per branch run as typed clause scans and append branch output
columns directly. Plain multi-clause unsourced DB branches now run as
branch-local typed relation pipelines and append output by attribute name, so
branches can use different clause orders without leaving typed columns.
Branches with `or-join`, nested negatives, relation-source matching,
predicates, functions, or more complex local pipelines reuse the existing
single-binding branch semantics, and branch outputs are appended back into
typed columns so `or` no longer forces a full relation materialization.

Fallback rule calls now use the same streaming typed boundary. The preferred
path is still the materialized rule relation plus typed join when eligible, but
the remaining per-row fallback no longer materializes the whole input relation
first: it converts one typed input row to a binding, reuses existing rule-call
semantics, and appends outputs back into typed columns.

Typed row-producing operators now share a small `Query-Relation-Builder`
runtime helper for column materialization and cleanup. Predicate filters,
`not`, `ground`, function clauses, `get-else`, `get-some`, generic bound-clause
fallbacks, and rule-call fallbacks all use this builder when they can stay on
the typed path. This keeps the logical `Query-Relation` API intact while moving
more operator code away from ad hoc `typed-columns` / `typed-rows` loops and
away from storing compatibility `Binding` rows when typed columns are enough.
The specialized entity/attribute bound-clause scan and the generic DB clause
scan also feed the same builder now, so typed data-clause output no longer
needs to keep compatibility row storage solely for later fallback consumers.
Generic DB clause scans now use a typed row matcher inside the candidate loop:
the operator resolves scan ranges directly from typed columns, and each
candidate datom is checked directly against typed columns and repeated clause
variables before appending typed output. This removes the previous row
`Binding` conversion from both scan-bound selection and candidate matching on
the generic typed DB-clause path while preserving reverse attribute and
same-variable semantics. Bound-clause extension now appends the existing typed
input row plus newly produced clause values directly into the output columns,
so matching candidate datoms no longer require a temporary `Binding` merely to
cross the builder boundary. Filter-only clauses with no newly introduced vars
append the typed input row directly and avoid even an empty produced-value
array.

Rule-call projection itself is also typed for the common non-distinct cases.
Distinct variable projections keep the fast column-clone path, while constants,
wildcards, and repeated variables project row-by-row from typed rule-result
columns with normal unification and dedupe. Projection falls back only when the
shape cannot be represented as typed columns, such as lookup-ref arguments or
non-columnar produced values. The same direct typed projection is used for
memo/delta parameter relations and for materialized helper rule outputs, so
general rule-call projection no longer has to allocate a temporary `Binding`
for every typed result row.

Specialized materialized helper-rule producers are typed-only for two-parameter
same-entity and derived-edge shapes. These producers now dedupe with primitive
row keys and fill relation columns directly instead of storing both typed
columns and compatibility `Binding` rows.

Rule memo/body dedupe also has a typed path. When a `Query-Relation` already
has typed columns, `query-relation-dedupe-step` dedupes directly by typed row
keys and keeps the result typed-only, falling back to binding materialization
only for unsupported row-key shapes.

Generic bound-clause fallback also streams. The specialized entity-bound
attribute clause remains the fastest path, and other bound shapes such as
value-bound joins now convert one typed row to a binding, run the existing
clause matcher, and write matching rows back into typed columns.

Unbound ordinary DB clauses now have a typed-first producer too. The operator
streams `Clause-Index-Scan` candidates, checks wildcard/value/lookup-ref,
reverse-attribute, tx/op, and repeated-variable semantics directly against the
datom, and appends projected clause variables into typed columns without first
building `Binding` rows. If a projected value cannot fit the columnar relation
layout, the operator falls back to the older binding-backed constructor.
Binding-only fallback joins now explicitly materialize typed-only inputs
through `query-relation-materialized-bindings`, so typed-only producers can feed
conservative product/hash/nested-loop joins without depending on stale
compatibility tuples.

Same-entity star scans now have an initial relation-native operator as well.
For all-current cardinality-one shapes with fixed-value filters and one or two
projected attrs, the relation engine aligns `AVET` filter streams with `AEVT`
output streams by entity id and fills typed relation columns directly. This is
still a conservative shape recognizer, but it returns the normal relation
representation rather than bypassing the relation engine with a specialized
result API.

Primary collection DB queries now preserve the full ordered query model when
rewritten onto their synthetic relation source. The rewrite carries over
`where-steps`, appends the synthetic source as an explicit input spec, and
source-input clause relations project only query variables instead of carrying
the whole source collection as a join column. This lets predicate, function,
and rule queries over relation DB inputs use the same relation-engine path as
ordinary DB-backed queries.

Named relation-source inputs use the same direct source operator. Source clauses
scan the source `Value` and emit only the variables introduced by the clause,
instead of first binding the whole source collection and stripping it later.
This covers ordinary named source clauses and source-qualified rule calls whose
rule bodies read from the same relation source. When the source values fit the
typed column representation, the initial source-input `Query-Relation` now
stores zero compatibility `Binding` rows; unsupported value kinds fall back to
the binding-backed representation.

The singleton relation is typed-only too: it is represented as one logical row
with zero columns and no compatibility `Binding` tuple. Binding-oriented
fallbacks still see the same empty binding through
`query-relation-materialized-bindings`, keeping the row/binding boundary
explicit.

Bound relation-source input clauses now extend typed rows directly too. When a
typed relation is already in flight and the next clause reads from a source
input such as `$rows`, Vev matches source rows against the typed row values and
appends only newly produced source variables as typed columns. This keeps
multi-clause relation-source joins on the columnar path instead of converting
each input row to a compatibility `Binding`.

Rule execution now has dependency analysis for rule-call graphs. Acyclic rule
graphs are recognized and evaluated with a single bounded pass instead of the
generic recursive fixpoint loop. The dependency graph also exposes strongly
connected component metadata for recursive checks and rule grouping. Plain
positive recursive rule groups can use component-local memoized execution with
delta-driven iteration over each recursive rule-call position. Specialized
transitive paths remain the fast path for common reachability shapes. The
generic memo/delta evaluator is now partially relation-native: DB clause steps
run as relation joins, broad rule-call steps reuse cached typed memo/delta
relation views before joining, and primitive-compatible memo outputs append to
those valid relation views as they are discovered. Compatible typed branch joins
for the same broad rule call are unioned in typed columns instead of always
materializing through `Binding` rows. Materialized non-recursive helper rules use
the same typed unique accumulation when projected branch layouts are compatible.
Recursive rule-body results now stream directly from the resulting
`Query-Relation` into memo insertion, avoiding a full materialized
`[dynamic]Binding` array between body evaluation and memo append. The memo/delta
row tables remain the source of truth, so fully typed-first memo storage is now
backlog/next-phase work rather than the active phase gate.

Near-term query work should expand the relation engine in this order:

1. Physical storage: replace generic `Binding` tuples with compact typed
   relation columns while keeping the same logical relation API.
2. Source-qualified collection operators: extend the direct source-aware
   relation representation to deeper nested source-qualified groups and broader
   named source combinations.
3. Rules: continue moving the positive-rule memo/delta evaluator from binding
   rows toward relation-native semi-naive behavior. DB clause steps and broad
   rule-call joins are now relation-native, and valid memo/delta relation caches
   append primitive-compatible outputs incrementally. Multi-branch broad rule
   calls and materialized helper rules can union compatible branch joins in typed
   columns. Rule body results stream into memo insertion instead of allocating a
   full intermediate binding array. Typed-first memo/delta storage remains
   backlog work.

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
