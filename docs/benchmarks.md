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
engine=vev workload=chain-root-text n=3 ok=true rows=2 min_us=23 median_us=24 p90_us=26 max_us=26 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=2
engine=vev workload=chain-root-prepared n=3 ok=true rows=2 min_us=8 median_us=9 p90_us=10 max_us=10 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=2
engine=vev workload=chain-root-text n=10 ok=true rows=9 min_us=43 median_us=44 p90_us=46 max_us=47 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=9
engine=vev workload=chain-root-prepared n=10 ok=true rows=9 min_us=26 median_us=28 p90_us=30 max_us=46 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=9
engine=vev workload=chain-root-text n=30 ok=true rows=29 min_us=102 median_us=105 p90_us=111 max_us=118 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=29
engine=vev workload=chain-root-prepared n=30 ok=true rows=29 min_us=87 median_us=90 p90_us=93 max_us=112 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=29
engine=vev workload=chain-root-text n=100 ok=true rows=99 min_us=363 median_us=370 p90_us=380 max_us=382 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=99
engine=vev workload=chain-root-prepared n=100 ok=true rows=99 min_us=355 median_us=361 p90_us=376 max_us=379 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=99
engine=vev workload=tree-root-text n=4 ok=true rows=3 min_us=25 median_us=27 p90_us=29 max_us=40 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=3
engine=vev workload=tree-root-prepared n=4 ok=true rows=3 min_us=10 median_us=11 p90_us=12 max_us=14 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=3
engine=vev workload=tree-root-text n=13 ok=true rows=12 min_us=52 median_us=55 p90_us=58 max_us=92 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=12
engine=vev workload=tree-root-prepared n=13 ok=true rows=12 min_us=36 median_us=38 p90_us=41 max_us=42 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=12
engine=vev workload=tree-root-text n=40 ok=true rows=39 min_us=130 median_us=134 p90_us=137 max_us=151 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=39
engine=vev workload=tree-root-prepared n=40 ok=true rows=39 min_us=115 median_us=119 p90_us=124 max_us=131 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=39
engine=vev workload=bad-order-join-text n=1000 ok=true rows=1 min_us=19 median_us=23 p90_us=24 max_us=26 steps=3 clauses=3 candidates=3 rule_calls=0 rule_iterations=0 max_bindings=1
engine=vev workload=bad-order-join-prepared n=1000 ok=true rows=1 min_us=11 median_us=12 p90_us=14 max_us=16 steps=3 clauses=3 candidates=3 rule_calls=0 rule_iterations=0 max_bindings=1

engine=datascript workload=chain-root n=3 ok=true rows=2 min_us=353 median_us=451 p90_us=691 max_us=1024 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
engine=datascript workload=chain-root n=10 ok=true rows=9 min_us=764 median_us=912 p90_us=1107 max_us=2678 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
engine=datascript workload=chain-root n=30 ok=true rows=29 min_us=3673 median_us=3923 p90_us=4426 max_us=5498 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
engine=datascript workload=chain-root n=100 ok=true rows=99 min_us=49463 median_us=90891 p90_us=269222 max_us=578282 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
engine=datascript workload=tree-root n=4 ok=true rows=3 min_us=88 median_us=96 p90_us=160 max_us=334 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
engine=datascript workload=tree-root n=13 ok=true rows=12 min_us=144 median_us=151 p90_us=201 max_us=849 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
engine=datascript workload=tree-root n=40 ok=true rows=39 min_us=235 median_us=282 p90_us=448 max_us=2215 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
engine=datascript workload=bad-order-join n=1000 ok=true rows=1 min_us=175 median_us=199 p90_us=286 max_us=1412 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
```

The important Vev-specific counters are:

- `candidates`: total datom/relation candidates inspected by data patterns.
- `rule_calls` and `rule_iterations`: recursive rule/fixpoint pressure.
- `max_bindings`: largest intermediate binding set materialized by the query.

Both harnesses report repeated execution samples. Vev currently uses 10 warmup
runs and 25 measured samples; DataScript uses 100 warmup runs and 100 measured
samples to reduce JVM warmup noise. Vev emits `*-text` rows, which include EDN
query/rule parsing, and `*-prepared` rows, which reuse parsed query values. For
recursive-rule workloads, prepared rows use `prepare-query-text-with-rules`, so
rules are attached to the prepared query once instead of copied in on every
execution. These are still local development benchmarks, not JMH/Criterium-grade
measurements.

## DataScript Comparison

Speedups below use median timings from the sample output above. They are
DataScript median divided by Vev median, so larger is better for Vev.

| Workload | Vev text | Vev prepared |
|---|---:|---:|
| `chain-root n=3` | 18.8x | 50.1x |
| `chain-root n=10` | 20.7x | 32.6x |
| `chain-root n=30` | 37.4x | 43.6x |
| `chain-root n=100` | 245.7x | 251.8x |
| `tree-root n=4` | 3.6x | 8.7x |
| `tree-root n=13` | 2.7x | 4.0x |
| `tree-root n=40` | 2.1x | 2.4x |
| `bad-order-join n=1000` | 8.7x | 16.6x |

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

The large chain-root gap should be read narrowly: it compares Vev's specialized
root-bound transitive closure path against DataScript's general recursive rule
evaluator. It is a useful signal for the native engine design, but not a broad
claim that every Vev query is faster than every DataScript query. The prepared
rows also show that parsing overhead is visible on tiny queries but becomes
secondary once result traversal dominates.

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
