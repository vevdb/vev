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
- Bulk setup uses the native transaction builder exposed through the C ABI,
  Java wrapper, and Clojure wrapper.
- Direct non-schema, non-unique, non-tuple add/retract batches use a one-pass
  transaction resolver.
- Non-tuple databases skip tuple maintenance during transaction expansion.
- A local 1k-person setup takes about 0.44s after these import-path changes.
- A local full 20k-person Vev-only run completes in about 11s wall time,
  including setup, with the default seven read queries.

Near-term benchmark work:

1. Extend the comparison runner to capture and normalize output into a single
   table with ratios against DataScript and Datalevin.
2. Investigate why long baseline comparison runs can stall on local external
   DataScript/Datalevin/Datomic processes even when the Vev row completes.
3. Use `q2` and `q2-switch` to drive a general same-entity star / merge-scan
   physical operator in Vev.
