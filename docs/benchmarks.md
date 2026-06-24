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

Current sample output on June 24, 2026:

```text
engine=vev workload=chain-root-text n=3 ok=true rows=2 min_us=23 median_us=24 p90_us=25 max_us=26 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=2
engine=vev workload=chain-root-prepared n=3 ok=true rows=2 min_us=8 median_us=9 p90_us=9 max_us=10 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=2
engine=vev workload=chain-root-text n=10 ok=true rows=9 min_us=39 median_us=41 p90_us=43 max_us=43 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=9
engine=vev workload=chain-root-prepared n=10 ok=true rows=9 min_us=24 median_us=26 p90_us=28 max_us=29 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=9
engine=vev workload=chain-root-text n=30 ok=true rows=29 min_us=91 median_us=94 p90_us=96 max_us=109 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=29
engine=vev workload=chain-root-prepared n=30 ok=true rows=29 min_us=75 median_us=77 p90_us=80 max_us=91 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=29
engine=vev workload=chain-root-text n=100 ok=true rows=99 min_us=349 median_us=354 p90_us=367 max_us=378 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=99
engine=vev workload=chain-root-prepared n=100 ok=true rows=99 min_us=332 median_us=341 p90_us=350 max_us=358 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=99
engine=vev workload=chain-leaf-text n=10 ok=true rows=9 min_us=64 median_us=65 p90_us=67 max_us=68 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=9
engine=vev workload=chain-leaf-prepared n=10 ok=true rows=9 min_us=47 median_us=49 p90_us=54 max_us=55 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=9
engine=vev workload=chain-leaf-text n=30 ok=true rows=29 min_us=173 median_us=176 p90_us=182 max_us=189 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=29
engine=vev workload=chain-leaf-prepared n=30 ok=true rows=29 min_us=158 median_us=162 p90_us=164 max_us=167 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=29
engine=vev workload=chain-leaf-text n=100 ok=true rows=99 min_us=625 median_us=655 p90_us=699 max_us=699 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=99
engine=vev workload=chain-leaf-prepared n=100 ok=true rows=99 min_us=630 median_us=642 p90_us=664 max_us=689 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=99
engine=vev workload=chain-all-text n=10 ok=true rows=45 min_us=113 median_us=117 p90_us=125 max_us=131 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=45
engine=vev workload=chain-all-prepared n=10 ok=true rows=45 min_us=95 median_us=102 p90_us=105 max_us=108 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=45
engine=vev workload=chain-all-text n=30 ok=true rows=435 min_us=2248 median_us=2352 p90_us=2473 max_us=2526 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=435
engine=vev workload=chain-all-prepared n=30 ok=true rows=435 min_us=2138 median_us=2346 p90_us=2377 max_us=2504 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=435
engine=vev workload=chain-all-text n=100 ok=true rows=4950 min_us=216210 median_us=219489 p90_us=228385 max_us=230385 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=4950
engine=vev workload=chain-all-prepared n=100 ok=true rows=4950 min_us=201879 median_us=209913 p90_us=217967 max_us=224496 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=4950
engine=vev workload=tree-root-text n=4 ok=true rows=3 min_us=25 median_us=27 p90_us=31 max_us=34 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=3
engine=vev workload=tree-root-prepared n=4 ok=true rows=3 min_us=9 median_us=11 p90_us=12 max_us=15 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=3
engine=vev workload=tree-root-text n=13 ok=true rows=12 min_us=45 median_us=49 p90_us=53 max_us=56 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=12
engine=vev workload=tree-root-prepared n=13 ok=true rows=12 min_us=30 median_us=31 p90_us=34 max_us=46 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=12
engine=vev workload=tree-root-text n=40 ok=true rows=39 min_us=118 median_us=123 p90_us=127 max_us=137 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=39
engine=vev workload=tree-root-prepared n=40 ok=true rows=39 min_us=99 median_us=104 p90_us=112 max_us=122 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=39
engine=vev workload=tree-root-text n=121 ok=true rows=120 min_us=362 median_us=370 p90_us=377 max_us=383 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=120
engine=vev workload=tree-root-prepared n=121 ok=true rows=120 min_us=340 median_us=348 p90_us=364 max_us=374 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=120
engine=vev workload=bad-order-join-text n=1000 ok=true rows=1 min_us=20 median_us=21 p90_us=22 max_us=23 steps=3 clauses=3 candidates=3 rule_calls=0 rule_iterations=0 max_bindings=1
engine=vev workload=bad-order-join-prepared n=1000 ok=true rows=1 min_us=11 median_us=12 p90_us=13 max_us=14 steps=3 clauses=3 candidates=3 rule_calls=0 rule_iterations=0 max_bindings=1

engine=datascript workload=chain-root n=3 ok=true rows=2 min_us=339 median_us=486 p90_us=621 max_us=1363 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
engine=datascript workload=chain-root n=10 ok=true rows=9 min_us=743 median_us=880 p90_us=1023 max_us=2598 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
engine=datascript workload=chain-root n=30 ok=true rows=29 min_us=3643 median_us=3800 p90_us=4352 max_us=5409 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
engine=datascript workload=chain-root n=100 ok=true rows=99 min_us=47892 median_us=50780 p90_us=52756 max_us=54337 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
engine=datascript workload=chain-leaf n=10 ok=true rows=9 min_us=712 median_us=749 p90_us=807 max_us=1967 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
engine=datascript workload=chain-leaf n=30 ok=true rows=29 min_us=6137 median_us=6329 p90_us=6837 max_us=7387 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
engine=datascript workload=chain-leaf n=100 ok=true rows=99 min_us=120848 median_us=123715 p90_us=125354 max_us=130738 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
engine=datascript workload=chain-all n=10 ok=true rows=45 min_us=594 median_us=618 p90_us=648 max_us=768 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
engine=datascript workload=chain-all n=30 ok=true rows=435 min_us=5314 median_us=5491 p90_us=5652 max_us=6675 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
engine=datascript workload=chain-all n=100 ok=true rows=4950 min_us=106027 median_us=108017 p90_us=109772 max_us=113436 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
engine=datascript workload=tree-root n=4 ok=true rows=3 min_us=78 median_us=82 p90_us=85 max_us=100 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
engine=datascript workload=tree-root n=13 ok=true rows=12 min_us=129 median_us=136 p90_us=149 max_us=229 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
engine=datascript workload=tree-root n=40 ok=true rows=39 min_us=223 median_us=232 p90_us=242 max_us=290 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
engine=datascript workload=tree-root n=121 ok=true rows=120 min_us=436 median_us=451 p90_us=494 max_us=1521 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
engine=datascript workload=bad-order-join n=1000 ok=true rows=1 min_us=165 median_us=170 p90_us=180 max_us=231 steps= clauses= candidates= rule_calls= rule_iterations= max_bindings=
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

Speedups below use median timings from the latest `compare_query_rules.sh` run.
They are DataScript median divided by Vev median, so larger is better for Vev.

| Workload | Vev text | Vev prepared |
|---|---:|---:|
| `chain-root n=3` | 21.7x | 62.5x |
| `chain-root n=10` | 22.5x | 38.1x |
| `chain-root n=30` | 43.6x | 51.9x |
| `chain-root n=100` | 150.8x | 158.1x |
| `chain-leaf n=10` | 11.0x | 14.9x |
| `chain-leaf n=30` | 33.8x | 36.5x |
| `chain-leaf n=100` | 183.2x | 182.0x |
| `chain-all n=10` | 5.0x | 5.7x |
| `chain-all n=30` | 2.3x | 2.2x |
| `chain-all n=100` | 0.5x | 0.5x |
| `tree-root n=4` | 3.0x | 8.2x |
| `tree-root n=13` | 3.0x | 5.1x |
| `tree-root n=40` | 2.2x | 2.5x |
| `tree-root n=121` | 1.2x | 1.2x |
| `bad-order-join n=1000` | 7.2x | 13.8x |

## Current Findings

Ordered text queries now plan contiguous data-pattern runs. The initial
`bad-order-join` shape materialized 1000 intermediate bindings; after planning
the same query materializes 1 and inspects 3 candidates.

Binary transitive closure now has a specialized rule path. It
recognizes the common DataScript/Datomic reachability rule shape:

```clojure
[[(reachable ?x ?y) [?x :follows ?y]]
 [(reachable ?x ?y) [?x :follows ?t] (reachable ?t ?y)]]
```

When the source argument is bound, Vev builds one adjacency view of the relation
and walks it with a queue and compact traversal-local seen sets. When only the
target is bound, Vev walks the same relation in reverse to find all sources that
can reach it. Both avoid recursively re-running the rule body to a fixed depth.
That turns the benchmarked chain-root and chain-leaf workloads into a single
rule iteration with one output binding per reached entity.

The large chain-root/chain-leaf gap should be read narrowly: it compares Vev's
specialized bound transitive closure path against DataScript's general recursive
rule evaluator. It is a useful signal for the native engine design, but not a
broad claim that every Vev query is faster than every DataScript query. The
`chain-all n=100` row is the important counterexample: Vev currently loses to
DataScript on all-pairs transitive closure because it still materializes thousands
of normal query bindings. That is the next recursive-rule performance target.

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
