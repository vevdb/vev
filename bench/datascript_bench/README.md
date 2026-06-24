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

Current caveat:

- The query adapter is wired through the public Clojure API and native C ABI.
- Full 20k-person setup currently goes through EDN transaction text and is too
  slow for tight iteration.
- That setup cost is outside the measured query loop, but it blocks convenient
  full-scale comparison.

Near-term benchmark work:

1. Add a faster benchmark import path, preferably a reusable bulk transaction
   path rather than a benchmark-only shortcut.
2. Add a comparison runner that invokes Datalevin's original
   `datascript-bench` for Datomic/DataScript/Datalevin and this Vev adapter for
   Vev.
3. Use `q2` and `q2-switch` to drive a general same-entity star / merge-scan
   physical operator in Vev.

