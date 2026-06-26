# Benchmarks

Vev has a small benchmark harness for comparing the native engine with the
local DataScript checkout. These are not exhaustive microbenchmarks yet; they
are a repeatable feedback loop for query planning and recursive rule work.

## Benchmark Ladder

Vev should use external benchmark shapes as compatibility and architecture
checks, not only local microbenchmarks.

Current order:

1. Datalevin `datascript-bench`: add Vev beside Datomic, DataScript, and
   Datalevin for the common in-memory read queries q1/q2/q2-switch/q3/q4/q5
   and predicate variants, plus the inherited DataScript write/rule workloads
   when the API shape is ready. This exercises Vev through the public Clojure
   API and native ABI, so it is a better host-language benchmark than direct
   Kvist calls.
2. Datalevin `math-bench`: use next for realistic Datalog rule processing over
   the Math Genealogy dataset. This is the most relevant external benchmark for
   validating the generic recursive rule engine after the current synthetic
   reachability stress harness.
3. Datalevin `openrulebench`: use after the component/SCC-local semi-naive rule
   engine exists. This should stress a broader set of Datalog rule workloads
   and help keep rule work general instead of reachability-specific.
4. Datalevin `JOB-bench`: use after Vev has a real planner/operator layer.
   This benchmark stresses join ordering, predicates, ranges, aggregates, and
   large import behavior over an IMDB-shaped dataset.
5. Datalevin `LDBC-SNB-bench`: use after planner/import work can support large
   graph-shaped datasets. This should validate interactive short reads and
   complex graph queries against a recognized graph workload.
6. Datalevin `idoc-bench`: use if Vev leans into document-style nested values
   and query shapes. It stresses YCSB-style A/C/F workloads, nested paths,
   ranges, wildcards, and arrays.
7. Datalevin `write-bench`: use after durable SQLite-backed storage exists.
   This benchmark should measure transaction throughput, commit latency,
   batching, WAL/sync choices, and mixed read/write behavior.
8. Datalevin `search-bench`: use only if Vev owns a full-text search story.
   Otherwise full-text should likely be delegated to SQLite FTS or external
   indexes, and this benchmark remains optional.

The q2/q2-switch rows from `datascript-bench` are especially important. They
represent same-entity star queries where Datalevin wins by using a general
merge-scan operator instead of clause-order-sensitive hash joins. Vev should
use these rows to drive reusable star-query and planner work rather than adding
query-name-specific fast paths.

Published Datalevin target latencies for this benchmark shape should be treated
as Vev's near-term read-query performance target, not merely DataScript parity:

| Query | Datomic ms | DataScript ms | Datalevin ms | Required Vev lesson |
|---|---:|---:|---:|---|
| `q1` | 1.0 | 0.25 | 0.22 | Bound AVET lookup plus cheap result materialization |
| `q2` | 2.0 | 1.1 | 0.25 | Same-entity merge scan for additional attrs |
| `q2-switch` | 9.6 | 2.2 | 0.24 | Clause-order-independent planning |
| `q3` | 2.7 | 1.7 | 0.13 | Push low-selectivity filters into the same star scan |
| `q4` | 3.7 | 2.5 | 0.14 | Add extra projected attrs with negligible overhead |
| `qpred1` | 5.4 | 3.7 | 1.0 | Rewrite value predicates into AVET range scans |
| `qpred2` | 6.6 | 6.1 | 0.99 | Substitute scalar inputs before range planning |

The local benchmark fixture currently defaults to 20k people, matching the
checked-out Datalevin `datascript-bench` fixture. With the five benchmark attrs
this is a 100k-datom database, which is why the benchmark code uses `db100k`
names. Local result tables should state the actual fixture shape when it
matters. The performance goal is the Datalevin planning shape: ordered
index/range scans, same-entity merge scans, predicate pushdown, and minimal
host result materialization.

There are two useful read-benchmark views:

- `bench/datascript_bench/run_compare.sh` measures the public Clojure API
  against DataScript and optionally Datalevin/Datomic.
- `bench/datascript_read.kvist` measures native engine column paths directly
  on a 20k entity / 100k datom fixture. Use this when deciding whether a
  slowdown belongs to the query engine or to C ABI / Java / Clojure result
  materialization.

The current q2/q3/q4 same-entity star-query work follows Datalevin's planning
idea: scan a selective AVET range, then advance through the sorted EAVT entity
starts while fetching same-entity cardinality-one attrs. This avoids restarting
an entity lookup for every candidate row and is the general operator direction
for native Vev, not a benchmark-specific shortcut.

The q5 row is the next read-query pressure point. It joins two entity variables
through a shared value (`:age`) and should drive reusable hash/merge join
operators rather than another star-query recognizer.

The rule benchmark order is `datascript-bench`, then `math-bench`, then
`openrulebench`. `datascript-bench` keeps Vev honest against the DataScript API
surface; `math-bench` introduces realistic recursive data; `openrulebench`
should validate the generic semi-naive engine once it exists.

Vev now exposes the optional `datascript-bench` rule rows by name through the
Clojure adapter using the same DataScript-style `:in $ %` rules input shape as
the upstream benchmark.

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
larger chain/tree closures and one mutually recursive rule shape. The Vev side
also includes larger Vev-only rows such as 1000-node bound chains, 1093-node
trees, dense DAGs, and a filtered recursive rule. The DataScript comparison
side uses smaller overlapping rows and fewer samples so the run stays
practical. Dense DAG and filtered generic-recursion rows are kept out of the
routine DataScript comparison for now because local DataScript/JVM runs either
run out of memory or take too long at useful sizes.

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
| `chain-root n=3` | 16.7x | 41.0x |
| `chain-root n=10` | 23.6x | 40.8x |
| `chain-root n=30` | 54.9x | 72.5x |
| `chain-root n=100` | 270.7x | 288.1x |
| `chain-leaf n=10` | 16.1x | 24.4x |
| `chain-leaf n=30` | 66.9x | 83.3x |
| `chain-leaf n=100` | 497.1x | 535.6x |
| `chain-all n=10` | 8.8x | 11.9x |
| `chain-all n=30` | 12.6x | 12.8x |
| `chain-all n=100` | 19.9x | 19.1x |
| `tree-root n=4` | 2.9x | 7.1x |
| `tree-root n=13` | 3.2x | 5.4x |
| `tree-root n=40` | 2.9x | 3.5x |
| `tree-root n=121` | 2.4x | 2.6x |
| `bad-order-join n=1000` | 7.0x | 11.3x |
| `distinct-age n=1000` | 3.4x | 4.1x |
| `people-name-age n=1000` | 0.8x | 0.8x |

## Stress Comparison

The stress comparison uses fewer samples and larger recursive-rule workloads.
It is intended for scaling direction, not stable microbenchmark numbers.

| Workload | Vev text | Vev prepared |
|---|---:|---:|
| `stress-chain-root n=300` | 1370.3x | 1411.0x |
| `stress-chain-leaf n=300` | 3971.5x | 4167.7x |
| `stress-chain-all n=200` | 32.9x | 32.4x |
| `stress-tree-root n=364` | 1.8x | 2.0x |
| `stress-mutual-root n=30` | 17.0x | 21.3x |

The stress harness also emits Vev-only rows for workloads that are currently
too expensive for routine DataScript comparison:

| Workload | Vev text median | Vev prepared median | Notes |
|---|---:|---:|---|
| `stress-dense-root n=60` | 240us | 220us | Dense DAG, width 8 |
| `stress-dense-root n=160` | 622us | 605us | Dense DAG, width 8 |
| `stress-filtered-root n=10` | 71us | 44us | Linear recursive rule with a target-node data filter |

## Current Findings

Ordered text queries now plan contiguous data-pattern runs. The initial
`bad-order-join` shape materialized 1000 intermediate bindings; after planning
the same query materializes 1 and inspects 3 candidates.

Binary transitive closure now has a specialized rule path. It recognizes the
common DataScript/Datomic reachability rule shape:

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

The same recognizer also supports linear recursive rules with identical
target-node data filters in the base and recursive branch, for example:

```clojure
[[(active-reachable ?x ?y) [?x :follows ?y] [?y :active true]]
 [(active-reachable ?x ?y) [?x :follows ?t] [?t :active true]
  (active-reachable ?t ?y)]]
```

Those filters are applied while constructing the adjacency view, so the
filtered recursive stress row now executes as one rule iteration instead of one
iteration per chain depth. In the latest local run, `stress-filtered-root n=10`
moved from roughly 957us/871us text/prepared to 49us/30us.

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

Generic rule-result dedupe now has the same shape: primitive bindings get a
stable binding key and use a map-backed seen set, while bindings containing
complex values still fall back to structural comparison. This removes one
avoidable O(rows squared) scan from recursive fixpoint accumulation, but it is
not a full semi-naive evaluator.

Two-rule alternating linear recursion now has its own graph path as well. It
recognizes shapes like:

```clojure
[[(f1 ?x ?y) [?x :f1 ?y]]
 [(f1 ?x ?y) [?x :f1 ?t] (f2 ?t ?y)]
 [(f2 ?x ?y) [?x :f2 ?y]]
 [(f2 ?x ?y) [?x :f2 ?t] (f1 ?t ?y)]]
```

For bound-start calls, Vev builds one adjacency per rule attr and traverses
`(entity,next-rule)` states. That moves `stress-mutual-root n=30` from roughly
29461us/29795us text/prepared on the generic fixpoint path to roughly
112us/83us locally, and it is now part of the DataScript stress comparison.

The harness includes `distinct-age`, a simple `[:find ?age :where [?e :age
?age]]` projection over 1000 entities and 100 distinct ages. Vev has a narrow
index-backed path for this shape that walks the `avet` range to collect one row
per distinct value, remembers the first current entity that produced each
value, and sorts that smaller distinct set back into the existing
entity/value-result order before rendering rows. This preserves the current
observable result order while avoiding per-candidate result rows and map-based
dedupe. For databases where every datom is current, the scan also skips the
per-candidate current-index binary search and takes the first datom in each
`avet` value group directly. Databases with retractions or shadowed facts keep
the checked path. The local Vev timing is now roughly 37us text / 33us prepared,
down from roughly 930us before projection-specific work, roughly 210us after
the first fast path, and roughly 160us before the all-current scan branch.

The benchmark installs `:db/cardinality :db.cardinality/many` and
`:db/valueType :db.type/ref` for `:follows`; without that, Vev correctly applies
unschematized cardinality-one semantics and the tree workload would not match
DataScript.

Remaining performance work:

- Move query intermediates toward typed struct-of-arrays relation storage before
  taking on the generic semi-naive rules engine. The current typed result
  columns and specialized q1/q2/q3/q4 paths should become ordinary physical
  relation operators, not permanent side channels. Benchmarks should be rerun
  after each operator migration so the work stays general.
- Generalize this from the current linear transitive closure path into a
  measured semi-naive/memoized rule evaluator. Filtered linear recursion and
  alternating two-rule recursion are now optimized, and primitive binding
  dedupe is map-backed, but arbitrary multi-step recursive bodies still use the
  generic depth/fixpoint evaluator.
- Continue result-projection work beyond the single-attr distinct fast path.
  `distinct-age` is now faster than DataScript locally, but
  `people-name-age` still shows broad two-column projection behind
  DataScript. Vev now keeps pure DB-clause queries on the indexed planner
  rather than the relation-engine path, has typed pair-level dedupe for common
  primitive pairs, and uses a same-entity merge operator over two `aevt` attr
  ranges for all-current cardinality-one projections. Non-pull result rows now
  also avoid allocating an empty pull-result array per row. The remaining work
  is to reduce generic per-row value materialization overhead and make this
  style of star projection available through the broader physical operator
  layer, not only the current two-attr projection path.
- Equality self-join planning has a first indexed operator for the
  `datascript-bench` q5 shape: filter the left side, collect distinct join
  values, then scan the right `avet` range once per join value. On the 20k
  benchmark row this brings Vev to roughly 106 ms versus DataScript at roughly
  139 ms in the short local harness. General relation hash joins are also in
  place for one primitive common variable, with fallback to the older nested
  join when lookup-ref/source semantics require it.
- Same-entity star/projection queries now use a general entity-local EAV span
  lookup inspired by Datalevin's sorted entity-local scans. Vev records each
  entity's contiguous range in `eavt`, then cardinality-one attr fetches search
  that small range instead of running a global `(entity, attr)` lower-bound for
  every candidate. In the latest 20k local `datascript-bench` comparison, q2 is
  at DataScript parity, q2-switch/q3/q4/q5/qpred1/qpred2 are ahead of
  DataScript, and q1 is effectively at parity at roughly 0.30 ms versus
  DataScript at roughly 0.29 ms.
- Same-entity star/projection now has an early all-current merge-stream
  operator for fixed filters plus projected attrs. It aligns entity-sorted
  `AVET(attr,value)` filter ranges and `AEVT(attr)` output ranges together,
  which is the Datalevin-style direction for q2/q3/q4. The current narrow
  implementation improves q2 rows to roughly 0.95 ms and q4 rows to roughly
  2.1 ms in the short local Clojure wrapper harness, but it is still well above
  the published Datalevin target. The next work is to make this the primary
  physical star operator and reduce Clojure/JVM materialization overhead for
  returned row vectors/sets.
- q1's remaining cost is mostly host result-shape overhead. Prepared
  diagnostic rows show q1 improves from roughly 0.30 ms for
  Datomic/DataScript-style `q` to roughly 0.09 ms for prepared `rows`, so the
  next q1 work should target set/vector materialization through the Clojure
  adapter and native result API, not index lookup.
- The relation engine now has a DataScript-shaped compound primitive hash join
  for relations with one or more common primitive variables. It uses
  length-prefixed compound keys and preserves the existing semantic fallback
  for non-primitive, lookup-ref-sensitive, or source-sensitive joins.
- Keep expanding benchmark coverage from real Datomic/DataScript-style
  workloads, including MusicBrainz-shaped queries, so performance work stays
  tied to database behavior rather than isolated microbenchmarks.
