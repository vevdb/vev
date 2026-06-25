# DataScript Bench Adapter

This folder starts a Vev adapter for Datalevin's
`benchmarks/datascript-bench` read workload.

The immediate purpose is to compare Vev against the common Datomic,
DataScript, and Datalevin query shapes:

- `q1`: bound attr/value lookup
- `q2`: selective attr plus same-entity value fetch
- `q2-switch`: same as `q2`, reversed clause order
- `q3` / `q4`: wider same-entity star queries
- `qpred1` / `qpred2`: predicate filtering with and without `:in`

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
- Use `VEV_COMPARE_BASELINES=datascript`, `datalevin`, or `datomic` to keep
  comparison runs scoped during optimization work.
- Bulk setup uses the native transaction builder exposed through the C ABI,
  Java wrapper, and Clojure wrapper.
- Direct non-schema, non-unique, non-tuple add/retract batches use a one-pass
  transaction resolver.
- Non-tuple databases skip tuple maintenance during transaction expansion.
- A local 1k-person setup takes about 0.44s after these import-path changes.
- A local full 20k-person Vev-only run completes in about 11s wall time,
  including setup, with the default seven read queries.

Latest checked local comparison:

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

The Datalevin baseline process can still take a long time or stall locally in
this wrapper; the latest verified table above is DataScript-only. Datalevin's
published numbers remain useful for direction, but they are not yet part of a
fresh automated Vev table.

Near-term benchmark work:

1. Investigate why long Datalevin/Datomic baseline comparison runs can stall
   locally even when the Vev and DataScript rows complete.
2. Run the same q1/q2/q2-switch/q3/q4/qpred rows at the Datalevin 20k-person
   scale and record stable comparison tables.
3. Continue using `q2` and `q2-switch` to drive a general same-entity star /
   merge-scan physical operator in Vev.
