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
engine=vev workload=chain-root n=3 ok=true rows=2 elapsed_us=242 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=3 max_bindings=2
engine=vev workload=bad-order-join n=1000 ok=true rows=1 elapsed_us=103 steps=3 clauses=3 candidates=3 rule_calls=0 rule_iterations=0 max_bindings=1

engine=datascript workload=chain-root n=3 ok=true rows=2 elapsed_us=1246 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
engine=datascript workload=bad-order-join n=1000 ok=true rows=1 elapsed_us=708 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
```

The important Vev-specific counters are:

- `candidates`: total datom/relation candidates inspected by data patterns.
- `rule_calls` and `rule_iterations`: recursive rule/fixpoint pressure.
- `max_bindings`: largest intermediate binding set materialized by the query.

## Current Findings

Ordered text queries now plan contiguous data-pattern runs. The initial
`bad-order-join` shape materialized 1000 intermediate bindings; after planning
the same query materializes 1 and inspects 3 candidates.

Recursive rule benchmarks are intentionally tiny. Larger chain/tree reachability
cases are the next performance target: the current evaluator is correct but
needs semi-naive or memoized recursive evaluation before those cases should be
expanded in the default harness.
