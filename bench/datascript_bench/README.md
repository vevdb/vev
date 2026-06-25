# DataScript Bench Adapter

This folder starts a Vev adapter for Datalevin's
`benchmarks/datascript-bench` read workload.

The immediate purpose is to compare Vev against the common Datomic,
DataScript, and Datalevin query shapes:

- `q1`: bound attr/value lookup
- `q2`: selective attr plus same-entity value fetch
- `q2-switch`: same as `q2`, reversed clause order
- `q3` / `q4`: wider same-entity star queries
- `q5`: shared-value join over two entity variables
- `qpred1` / `qpred2`: predicate filtering with and without `:in`
- `rules-wide-*` / `rules-long-*`: optional recursive rule rows from the
  upstream benchmark, using DataScript-style `:in $ %` rules input

Run a quick smoke:

```sh
VEV_BENCH_PEOPLE=100 \
VEV_BENCH_WARMUP_MS=5 \
VEV_BENCH_MS=10 \
VEV_BENCH_REPEATS=1 \
VEV_BENCH_STEP=1 \
  bench/datascript_bench/run_vev.sh
```

Run at the Datalevin benchmark scale:

```sh
bench/datascript_bench/run_vev.sh
```

Run one query against Vev and DataScript. This is the recommended inner-loop
workflow while optimizing one query shape:

```sh
VEV_COMPARE_BASELINES=datascript \
VEV_BENCH_PEOPLE=1000 \
VEV_BENCH_WARMUP_MS=10 \
VEV_BENCH_MS=20 \
VEV_BENCH_REPEATS=1 \
  bench/datascript_bench/run_compare.sh q1
```

Run one query against Vev only:

```sh
VEV_COMPARE_SKIP_BASELINES=1 \
  bench/datascript_bench/run_compare.sh q1
```

Run Vev next to the upstream DataScript/Datalevin read benchmarks:

```sh
bench/datascript_bench/run_compare.sh
```

By default this runs the upstream benchmark namespaces from
`/Users/andreas/Projects/datalevin/benchmarks/datascript-bench` with published
DataScript/Datalevin dependencies, then runs Vev for the same query names.
Useful options:

```sh
DATALEVIN_BENCH=/path/to/datalevin/benchmarks/datascript-bench \
VEV_COMPARE_BASELINES="datascript datalevin" \
VEV_COMPARE_DATOMIC=1 \
  bench/datascript_bench/run_compare.sh q1 q2 q2-switch

VEV_COMPARE_SKIP_BASELINES=1 \
VEV_BENCH_PEOPLE=1000 \
VEV_BENCH_WARMUP_MS=10 \
VEV_BENCH_MS=20 \
VEV_BENCH_REPEATS=1 \
VEV_BENCH_STEP=1 \
  bench/datascript_bench/run_compare.sh

VEV_COMPARE_USE_UPSTREAM_SCRIPT=1 \
  bench/datascript_bench/run_compare.sh q1
```

Current status:

- The query adapter is wired through the public Clojure API and native C ABI.
- `run_compare.sh` accepts query names, so `run_compare.sh q1` runs only `q1`.
- `q5` and the optional upstream recursive rule rows are available by name.
- Use `VEV_COMPARE_BASELINES=datascript`, `datalevin`, or `datomic` to keep
  comparison runs scoped during optimization work.
- Bulk setup uses the native transaction builder exposed through the C ABI,
  Java wrapper, and Clojure wrapper.
- Direct non-schema, non-unique, non-tuple add/retract batches use a one-pass
  transaction resolver.
- Non-tuple databases skip tuple maintenance during transaction expansion.
- A local 1k-person setup takes about 0.44s after these import-path changes.
- A local full 20k-person Vev-only run completes in about 11s wall time,
  including setup, with the default seven read queries. `q5` is intentionally
  opt-in because it is a heavier shared-value join row that should drive future
  planner/operator work.

Latest checked 1k local comparison:

```sh
VEV_COMPARE_BASELINES=datascript \
VEV_BENCH_PEOPLE=1000 \
VEV_BENCH_WARMUP_MS=10 \
VEV_BENCH_MS=20 \
VEV_BENCH_REPEATS=1 \
  bench/datascript_bench/run_compare.sh q1 q2 q2-switch q3 q4 qpred1 qpred2
```

| Query | DataScript ms | Vev ms | DataScript / Vev |
|---|---:|---:|---:|
| `q1` | 0.28 | 0.05 | 5.60x |
| `q2` | 1.4 | 0.10 | 14.00x |
| `q2-switch` | 3.0 | 0.08 | 37.50x |
| `q3` | 2.1 | 0.11 | 19.09x |
| `q4` | 3.2 | 0.21 | 15.24x |
| `qpred1` | 4.2 | 0.09 | 46.67x |
| `qpred2` | 8.0 | 0.12 | 66.67x |

Additional scoped comparison after porting the missing upstream rows:

```sh
VEV_COMPARE_BASELINES=datascript \
VEV_BENCH_PEOPLE=1000 \
VEV_BENCH_WARMUP_MS=10 \
VEV_BENCH_MS=20 \
VEV_BENCH_REPEATS=1 \
  bench/datascript_bench/run_compare.sh q5 rules-wide-3x3 rules-long-10x3
```

| Query | DataScript ms | Vev ms | DataScript / Vev |
|---|---:|---:|---:|
| `q5` | 133.4 | 10.1 | 13.21x |
| `rules-wide-3x3` | 0.46 | 0.27 | 1.70x |
| `rules-long-10x3` | 0.96 | 0.36 | 2.67x |

The rule rows now use the same DataScript-style `%` rules input shape through
the Clojure wrapper:
`(v/q '[:find ... :in $ % :where ...] db rules)`.

The Datalevin baseline process can still take a long time or stall locally in
this wrapper; the latest verified table above is DataScript-only. Datalevin's
published numbers remain useful for direction, but they are not yet part of a
fresh automated Vev table.

Latest checked 20k local comparison:

```sh
VEV_COMPARE_BASELINES=datascript \
VEV_BENCH_WARMUP_MS=2 \
VEV_BENCH_MS=5 \
VEV_BENCH_REPEATS=1 \
  bench/datascript_bench/run_compare.sh q1 q2 q2-switch q3 q4 q5 qpred1 qpred2
```

| Query | DataScript ms | Vev ms | DataScript / Vev |
|---|---:|---:|---:|
| `q1` | 0.28 | 0.65 | 0.43x |
| `q2` | 1.3 | 2.5 | 0.52x |
| `q2-switch` | 2.9 | 2.1 | 1.38x |
| `q3` | 2.2 | 2.4 | 0.92x |
| `q4` | 3.1 | 3.4 | 0.91x |
| `q5` | 138.9 | 106.0 | 1.31x |
| `qpred1` | 3.9 | 2.5 | 1.56x |
| `qpred2` | 7.8 | 3.2 | 2.44x |

`q5` is now handled by an indexed equality self-join operator: it collects
distinct left-side join values, then scans the right `avet` range once per
join value. This is a semi-join shape, not a benchmark-name special case, and
it should generalize to Datomic/DataScript queries where an unreturned left
entity only constrains a shared value.

Near-term benchmark work:

1. Investigate why long Datalevin/Datomic baseline comparison runs can stall
   locally even when the Vev and DataScript rows complete.
2. Continue using q1/q2/q3/q4 to drive same-entity star/projection work at the
   full 20k scale. Those rows still show Vev behind DataScript despite the
   native backend.
3. Keep broadening equality-join planning from the current self-join operator
   into the general relation planner, including multi-common-variable joins and
   non-all-current DBs.
