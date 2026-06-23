# Benchmarks

Vev has a small benchmark harness for comparing the native engine with the
local DataScript checkout. These are not exhaustive microbenchmarks yet; they
are a repeatable feedback loop for query planning and recursive rule work.

## Query And Rule Baseline

Run Vev from the Kvist repo root so macro loading uses the normal compiler
layout:

```sh
cd /Users/andreas/Projects/kvist
KVIST_PACKAGES_DIR=/Users/andreas/Projects/kvist/packages \
  /Users/andreas/Projects/kvist/.worktrees/codex-item-kvist-case-default/kvist \
  run /Users/andreas/Projects/vev/.worktrees/codex-item-vev-datalog/bench/query_rules.kvist
```

Run DataScript from this repo, using the local source checkout:

```sh
clojure \
  -Sdeps '{:deps {datascript/datascript {:local/root "/Users/andreas/Projects/datascript"}}}' \
  -M bench/datascript_query_rules.clj
```

Current sample output on June 23, 2026:

```text
engine=vev workload=chain-root n=3 ok=true rows=2 elapsed_us=255 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=2
engine=vev workload=chain-root n=10 ok=true rows=9 elapsed_us=52 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=9
engine=vev workload=chain-root n=30 ok=true rows=29 elapsed_us=106 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=29
engine=vev workload=chain-root n=100 ok=true rows=99 elapsed_us=421 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=99
engine=vev workload=tree-root n=4 ok=true rows=3 elapsed_us=36 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=3
engine=vev workload=tree-root n=13 ok=true rows=12 elapsed_us=56 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=12
engine=vev workload=tree-root n=40 ok=true rows=39 elapsed_us=164 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=39
engine=vev workload=bad-order-join n=1000 ok=true rows=1 elapsed_us=90 steps=3 clauses=3 candidates=3 rule_calls=0 rule_iterations=0 max_bindings=1

engine=datascript workload=chain-root n=3 ok=true rows=2 elapsed_us=1203 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
engine=datascript workload=chain-root n=10 ok=true rows=9 elapsed_us=4258 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
engine=datascript workload=chain-root n=30 ok=true rows=29 elapsed_us=9239 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
engine=datascript workload=chain-root n=100 ok=true rows=99 elapsed_us=55157 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
engine=datascript workload=tree-root n=4 ok=true rows=3 elapsed_us=222 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
engine=datascript workload=tree-root n=13 ok=true rows=12 elapsed_us=340 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
engine=datascript workload=tree-root n=40 ok=true rows=39 elapsed_us=435 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
engine=datascript workload=bad-order-join n=1000 ok=true rows=1 elapsed_us=408 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
```

The important Vev-specific counters are:

- `candidates`: total datom/relation candidates inspected by data patterns.
- `rule_calls` and `rule_iterations`: recursive rule/fixpoint pressure.
- `max_bindings`: largest intermediate binding set materialized by the query.

## Current Findings

Ordered text queries now plan contiguous data-pattern runs. The initial
`bad-order-join` shape materialized 1000 intermediate bindings; after planning
the same query materializes 1 and inspects 3 candidates.

Root-bound binary transitive closure now has a specialized rule path. It
recognizes the common DataScript/Datomic reachability rule shape:

```clojure
[[(reachable ?x ?y) [?x :follows ?y]]
 [(reachable ?x ?y) [?x :follows ?t] (reachable ?t ?y)]]
```

When the source argument is bound, Vev walks the matching AVET/EAVT candidates
with a queue and a hash-backed seen set instead of recursively re-running the
rule body to a fixed depth. That turns the benchmarked chain-root workload into
a single rule iteration with one output binding per reached entity.

The benchmark installs `:db/cardinality :db.cardinality/many` and
`:db/valueType :db.type/ref` for `:follows`; without that, Vev correctly applies
unschematized cardinality-one semantics and the tree workload would not match
DataScript.

Remaining performance work:

- Generalize this from a shape-specific transitive closure path into a measured
  semi-naive/memoized rule evaluator.
- Add a separate stress harness for larger chain/tree workloads once default
  benchmark runs stay short.
- Profile tree/branching closure and all-pairs closure separately; the current
  root-bound path is good enough for baseline comparison, but it is not a full
  recursive query planner.
