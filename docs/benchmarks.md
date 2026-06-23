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

To run both harnesses and print only the DataScript-relative speedup table:

```sh
bench/compare_query_rules.sh
```

The comparison script accepts `KVIST_ROOT`, `KVIST_BIN`,
`KVIST_PACKAGES_DIR`, and `DATASCRIPT_ROOT` environment overrides.

Current sample output on June 23, 2026:

```text
engine=vev workload=chain-root-text n=3 ok=true rows=2 min_us=23 median_us=25 p90_us=26 max_us=27 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=2
engine=vev workload=chain-root-prepared n=3 ok=true rows=2 min_us=8 median_us=10 p90_us=11 max_us=12 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=2
engine=vev workload=chain-root-text n=10 ok=true rows=9 min_us=46 median_us=47 p90_us=48 max_us=49 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=9
engine=vev workload=chain-root-prepared n=10 ok=true rows=9 min_us=29 median_us=32 p90_us=34 max_us=36 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=9
engine=vev workload=chain-root-text n=30 ok=true rows=29 min_us=112 median_us=117 p90_us=123 max_us=132 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=29
engine=vev workload=chain-root-prepared n=30 ok=true rows=29 min_us=97 median_us=99 p90_us=104 max_us=111 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=29
engine=vev workload=chain-root-text n=100 ok=true rows=99 min_us=402 median_us=411 p90_us=445 max_us=456 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=99
engine=vev workload=chain-root-prepared n=100 ok=true rows=99 min_us=378 median_us=385 p90_us=394 max_us=412 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=99
engine=vev workload=chain-all-text n=10 ok=true rows=45 min_us=185 median_us=193 p90_us=201 max_us=210 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=45
engine=vev workload=chain-all-prepared n=10 ok=true rows=45 min_us=174 median_us=177 p90_us=183 max_us=191 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=45
engine=vev workload=chain-all-text n=30 ok=true rows=435 min_us=2939 median_us=3033 p90_us=3138 max_us=3313 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=435
engine=vev workload=chain-all-prepared n=30 ok=true rows=435 min_us=2853 median_us=3030 p90_us=3117 max_us=3219 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=435
engine=vev workload=tree-root-text n=4 ok=true rows=3 min_us=26 median_us=28 p90_us=32 max_us=35 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=3
engine=vev workload=tree-root-prepared n=4 ok=true rows=3 min_us=11 median_us=12 p90_us=13 max_us=15 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=3
engine=vev workload=tree-root-text n=13 ok=true rows=12 min_us=54 median_us=56 p90_us=61 max_us=65 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=12
engine=vev workload=tree-root-prepared n=13 ok=true rows=12 min_us=38 median_us=40 p90_us=43 max_us=45 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=12
engine=vev workload=tree-root-text n=40 ok=true rows=39 min_us=139 median_us=142 p90_us=146 max_us=154 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=39
engine=vev workload=tree-root-prepared n=40 ok=true rows=39 min_us=121 median_us=126 p90_us=133 max_us=140 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=39
engine=vev workload=bad-order-join-text n=1000 ok=true rows=1 min_us=20 median_us=21 p90_us=22 max_us=23 steps=3 clauses=3 candidates=3 rule_calls=0 rule_iterations=0 max_bindings=1
engine=vev workload=bad-order-join-prepared n=1000 ok=true rows=1 min_us=11 median_us=12 p90_us=13 max_us=14 steps=3 clauses=3 candidates=3 rule_calls=0 rule_iterations=0 max_bindings=1

engine=datascript workload=chain-root n=3 ok=true rows=2 min_us=339 median_us=486 p90_us=621 max_us=1363 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
engine=datascript workload=chain-root n=10 ok=true rows=9 min_us=743 median_us=880 p90_us=1023 max_us=2598 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
engine=datascript workload=chain-root n=30 ok=true rows=29 min_us=3643 median_us=3800 p90_us=4352 max_us=5409 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
engine=datascript workload=chain-root n=100 ok=true rows=99 min_us=47910 median_us=49737 p90_us=51453 max_us=53906 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
engine=datascript workload=chain-all n=10 ok=true rows=45 min_us=589 median_us=618 p90_us=648 max_us=1637 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
engine=datascript workload=chain-all n=30 ok=true rows=435 min_us=5056 median_us=5216 p90_us=5491 max_us=6383 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
engine=datascript workload=tree-root n=4 ok=true rows=3 min_us=81 median_us=83 p90_us=86 max_us=103 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
engine=datascript workload=tree-root n=13 ok=true rows=12 min_us=129 median_us=136 p90_us=149 max_us=229 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
engine=datascript workload=tree-root n=40 ok=true rows=39 min_us=213 median_us=219 p90_us=235 max_us=339 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
engine=datascript workload=bad-order-join n=1000 ok=true rows=1 min_us=159 median_us=164 p90_us=185 max_us=352 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
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
| `chain-root n=3` | 19.4x | 48.6x |
| `chain-root n=10` | 18.7x | 27.5x |
| `chain-root n=30` | 32.5x | 38.4x |
| `chain-root n=100` | 121.0x | 129.2x |
| `chain-all n=10` | 3.2x | 3.5x |
| `chain-all n=30` | 1.7x | 1.7x |
| `tree-root n=4` | 3.0x | 6.9x |
| `tree-root n=13` | 2.4x | 3.4x |
| `tree-root n=40` | 1.5x | 1.7x |
| `bad-order-join n=1000` | 7.8x | 13.7x |

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
