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

For larger recursive-rule workloads, run the separate stress harness:

```sh
bench/compare_query_rules_stress.sh
```

The stress harness keeps the default benchmark short while still measuring
larger chain/tree closures. The Vev side includes larger Vev-only rows such as
1000-node bound chains and 1093-node trees; the DataScript comparison side uses
smaller overlapping rows and fewer samples so the run stays practical.

Current sample output on June 24, 2026:

```text
engine=vev workload=chain-root-text n=3 ok=true rows=2 min_us=23 median_us=24 p90_us=25 max_us=26 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=2
engine=vev workload=chain-root-prepared n=3 ok=true rows=2 min_us=8 median_us=9 p90_us=9 max_us=10 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=2
engine=vev workload=chain-root-text n=10 ok=true rows=9 min_us=39 median_us=41 p90_us=43 max_us=43 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=9
engine=vev workload=chain-root-prepared n=10 ok=true rows=9 min_us=24 median_us=26 p90_us=28 max_us=29 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=9
engine=vev workload=chain-root-text n=30 ok=true rows=29 min_us=91 median_us=94 p90_us=96 max_us=109 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=29
engine=vev workload=chain-root-prepared n=30 ok=true rows=29 min_us=75 median_us=77 p90_us=80 max_us=91 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=29
engine=vev workload=chain-root-text n=100 ok=true rows=99 min_us=236 median_us=244 p90_us=264 max_us=285 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=99
engine=vev workload=chain-root-prepared n=100 ok=true rows=99 min_us=218 median_us=232 p90_us=237 max_us=244 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=99
engine=vev workload=chain-leaf-text n=10 ok=true rows=9 min_us=64 median_us=65 p90_us=67 max_us=68 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=9
engine=vev workload=chain-leaf-prepared n=10 ok=true rows=9 min_us=47 median_us=49 p90_us=54 max_us=55 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=9
engine=vev workload=chain-leaf-text n=30 ok=true rows=29 min_us=173 median_us=176 p90_us=182 max_us=189 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=29
engine=vev workload=chain-leaf-prepared n=30 ok=true rows=29 min_us=158 median_us=162 p90_us=164 max_us=167 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=29
engine=vev workload=chain-leaf-text n=100 ok=true rows=99 min_us=547 median_us=559 p90_us=604 max_us=644 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=99
engine=vev workload=chain-leaf-prepared n=100 ok=true rows=99 min_us=530 median_us=542 p90_us=561 max_us=588 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=99
engine=vev workload=chain-all-text n=10 ok=true rows=45 min_us=52 median_us=56 p90_us=60 max_us=67 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=45
engine=vev workload=chain-all-prepared n=10 ok=true rows=45 min_us=38 median_us=42 p90_us=44 max_us=50 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=45
engine=vev workload=chain-all-text n=30 ok=true rows=435 min_us=332 median_us=342 p90_us=353 max_us=365 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=435
engine=vev workload=chain-all-prepared n=30 ok=true rows=435 min_us=326 median_us=336 p90_us=358 max_us=367 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=435
engine=vev workload=chain-all-text n=100 ok=true rows=4950 min_us=4139 median_us=4282 p90_us=4473 max_us=4552 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=4950
engine=vev workload=chain-all-prepared n=100 ok=true rows=4950 min_us=4173 median_us=4349 p90_us=4465 max_us=4616 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=4950
engine=vev workload=tree-root-text n=4 ok=true rows=3 min_us=25 median_us=27 p90_us=31 max_us=34 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=3
engine=vev workload=tree-root-prepared n=4 ok=true rows=3 min_us=9 median_us=11 p90_us=12 max_us=15 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=3
engine=vev workload=tree-root-text n=13 ok=true rows=12 min_us=45 median_us=49 p90_us=53 max_us=56 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=12
engine=vev workload=tree-root-prepared n=13 ok=true rows=12 min_us=30 median_us=31 p90_us=34 max_us=46 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=12
engine=vev workload=tree-root-text n=40 ok=true rows=39 min_us=118 median_us=123 p90_us=127 max_us=137 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=39
engine=vev workload=tree-root-prepared n=40 ok=true rows=39 min_us=99 median_us=104 p90_us=112 max_us=122 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=39
engine=vev workload=tree-root-text n=121 ok=true rows=120 min_us=241 median_us=254 p90_us=270 max_us=306 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=120
engine=vev workload=tree-root-prepared n=121 ok=true rows=120 min_us=230 median_us=237 p90_us=256 max_us=289 steps=1 clauses=0 candidates=0 rule_calls=1 rule_iterations=1 max_bindings=120
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
| `chain-root n=3` | 19.4x | 58.1x |
| `chain-root n=10` | 22.7x | 40.2x |
| `chain-root n=30` | 48.8x | 63.5x |
| `chain-root n=100` | 240.9x | 254.3x |
| `chain-leaf n=10` | 19.0x | 32.8x |
| `chain-leaf n=30` | 75.8x | 97.0x |
| `chain-leaf n=100` | 521.5x | 570.5x |
| `chain-all n=10` | 11.0x | 15.5x |
| `chain-all n=30` | 15.3x | 16.4x |
| `chain-all n=100` | 23.5x | 23.1x |
| `tree-root n=4` | 3.4x | 8.5x |
| `tree-root n=13` | 3.3x | 5.4x |
| `tree-root n=40` | 2.5x | 3.0x |
| `tree-root n=121` | 1.9x | 2.2x |
| `bad-order-join n=1000` | 7.0x | 11.1x |
| `distinct-age n=1000` | 0.6x | 0.6x |

## Stress Comparison

The stress comparison uses fewer samples and larger recursive-rule workloads.
It is intended for scaling direction, not stable microbenchmark numbers.

| Workload | Vev text | Vev prepared |
|---|---:|---:|
| `stress-chain-root n=300` | 1378.9x | 1369.9x |
| `stress-chain-leaf n=300` | 4032.8x | 4246.1x |
| `stress-chain-all n=200` | 32.4x | 32.0x |
| `stress-tree-root n=364` | 1.8x | 1.7x |

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

When the source argument is bound, Vev builds one forward adjacency view of the
relation and walks it with a queue and compact traversal-local seen sets. When
only the target is bound, Vev builds the corresponding reverse adjacency view
and walks backward to find all sources that can reach it. Both avoid recursively
re-running the rule body to a fixed depth.
That turns the benchmarked chain-root and chain-leaf workloads into a single
rule iteration with one output binding per reached entity. The adjacency view is
built directly from the `aevt` index range for the relation attr, with traversal
scratch arrays pre-sized from the adjacency size, so the specialized path no
longer materializes a generic clause candidate vector during closure setup. For
compact entity-id ranges, bounded closure traversal uses dense boolean bitmaps
for visited/emitted sets instead of repeated linear membership scans, improving
larger chain and branching tree workloads without requiring sparse global
entity-id arrays.

The large chain-root/chain-leaf gap should be read narrowly: it compares Vev's
specialized bound transitive closure path against DataScript's general recursive
rule evaluator. The all-pairs chain benchmark now uses the same closure
recognizer and avoids the generic O(rows squared) result dedupe path when the
optimized rule shape guarantees unique bindings. That moves `chain-all n=100`
from slower than DataScript to roughly 23x faster in this local harness.

Non-rule result projection now has a primitive dedupe path. Single primitive
find values use typed seen sets instead of repeatedly scanning already-emitted
rows, and multi-column primitive rows use length-prefixed dedupe keys before
falling back to structural row comparison. This keeps DataScript-style distinct
find semantics while avoiding the worst quadratic behavior for common primitive
results.

The harness now includes `distinct-age`, a simple `[:find ?age :where [?e :age
?age]]` projection over 1000 entities and 100 distinct ages. Vev has a narrow
index-backed path for this shape that scans the `aevt` range directly,
preserving normal clause result order while avoiding a separate candidate array.
This brought the local Vev timing down from roughly 930us before the batch to
roughly 210us, but DataScript is still faster on this specific workload in the
latest comparison. Treat this as the next concrete projection-performance
target.

The benchmark installs `:db/cardinality :db.cardinality/many` and
`:db/valueType :db.type/ref` for `:follows`; without that, Vev correctly applies
unschematized cardinality-one semantics and the tree workload would not match
DataScript.

Remaining performance work:

- Generalize this from a shape-specific transitive closure path into a measured
  semi-naive/memoized rule evaluator.
- Continue result-projection work for simple distinct queries. The current
  `distinct-age` path is much better than generic row dedupe, but still behind
  DataScript because it allocates full `Result-Row` structures and maintains
  order-preserving seen sets.
- Add dense graph and non-transitive recursive-rule stress workloads. The
  current stress harness covers larger chain/tree closures, but the closure
  recognizer is useful only for one common linear transitive shape; it is not a
  full recursive query planner.
