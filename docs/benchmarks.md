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

Latest local public Clojure comparison after borrowed typed relation
product/hash-join operators:

| Query | DataScript ms | Vev ms | DataScript/Vev |
|---|---:|---:|---:|
| `q1` | 0.27 | 0.28 | 0.96x |
| `q2` | 1.40 | 1.00 | 1.40x |
| `q2-switch` | 3.00 | 1.10 | 2.73x |
| `q3` | 2.10 | 0.87 | 2.41x |
| `q4` | 3.20 | 1.60 | 2.00x |
| `q5` | 142.00 | 16.40 | 8.66x |

The `people-name-age` row was requested in the same run, but the DataScript
side did not emit a numeric baseline for that workload, so it is omitted from
the comparison table.

The current q2/q3/q4 same-entity star-query work follows Datalevin's planning
idea: scan a selective AVET range, then advance through the sorted EAVT entity
starts while fetching same-entity cardinality-one attrs. This avoids restarting
an entity lookup for every candidate row and is the general operator direction
for native Vev, not a benchmark-specific shortcut.

The current qpred path follows Datalevin's predicate-pushdown idea for typed
long attrs: `[(> ?s 50000)]` lowers to integer AVET range bounds, not a full
attribute scan plus generic predicate filtering. DB values now also carry small
schema caches for value type and cardinality-many attrs so hot optimized
queries do not repeatedly rediscover basic schema metadata through datom
queries.

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

## MusicBrainz Import Smoke

The MusicBrainz phase has a real-data import smoke for the restored Datomic
1968-1973 sample. It is not a final benchmark yet; it exists to reveal import,
schema, EDN, and transaction-indexing bottlenecks on Datomic-shaped data.

Generate staged schema/value EDN from the restored Datomic sample:

```sh
scripts/musicbrainz_sample.sh export-subset-split build/musicbrainz/vev-mbrainz-subset-5000 5000
```

For larger imports, generate chunked value files so the loader does not keep
the entire prepared transaction in memory:

```sh
scripts/musicbrainz_sample.sh export-subset-chunks build/musicbrainz/vev-mbrainz-subset-full-chunked 0 100000
```

Build and run the Vev import smoke:

```sh
cd /Users/andreas/Projects/kvist
./kvist build /Users/andreas/Projects/vev/bench/musicbrainz_import_subset.kvist \
  --out /Users/andreas/Projects/vev/build/bench/musicbrainz_import_subset

/Users/andreas/Projects/vev/build/bench/musicbrainz_import_subset \
  --schema /Users/andreas/Projects/vev/build/musicbrainz/vev-mbrainz-subset-5000-schema.edn \
  --values /Users/andreas/Projects/vev/build/musicbrainz/vev-mbrainz-subset-5000-values.edn

/Users/andreas/Projects/vev/build/bench/musicbrainz_import_subset \
  --schema /Users/andreas/Projects/vev/build/musicbrainz/vev-mbrainz-subset-full-chunked-schema.edn \
  --values-prefix /Users/andreas/Projects/vev/build/musicbrainz/vev-mbrainz-subset-full-chunked \
  --values-chunks 8
```

Add `--sqlite-output <path>` to persist the imported DB into the current SQLite
store format after the native import completes. This is the preferred setup for
host-language MusicBrainz query benchmarks, because it keeps Clojure/Java setup
out of query timing:

```sh
/Users/andreas/Projects/vev/build/bench/musicbrainz_import_subset \
  --schema /Users/andreas/Projects/vev/build/musicbrainz/vev-mbrainz-subset-full-chunked-schema.edn \
  --values-prefix /Users/andreas/Projects/vev/build/musicbrainz/vev-mbrainz-subset-full-chunked \
  --values-chunks 8 \
  --sqlite-output /Users/andreas/Projects/vev/build/musicbrainz/vev-mbrainz-full.sqlite
```

Current local 5k staged result:

```text
engine=vev workload=musicbrainz-import ok=true mode=split datoms=5293 current=5293 parse_us=10858 tx_us=506981 import_us=517839 artist_rows=0 artist_us=168 release_rows=0 release_us=152
```

The relevant signal is the ratio inside Vev: EDN parse is already small for
this slice, and the generic explicit-id bulk transaction path is now fast enough
for routine MusicBrainz query-matrix work. The importer now stores owned DB
datom attr/value payloads for parsed EDN tx data, so chunked imports no longer
depend on the lifetime of each input file buffer.

Larger local staged results:

```text
engine=vev workload=musicbrainz-import ok=true mode=split datoms=50293 current=50293 parse_us=96954 tx_us=1252737 import_us=1349691 artist_rows=1 artist_us=224 release_rows=0 release_us=109
engine=vev workload=musicbrainz-import ok=true mode=split datoms=100293 current=100293 parse_us=180390 tx_us=1866802 import_us=2047192 artist_rows=1 artist_us=63 release_rows=0 release_us=58
engine=vev workload=musicbrainz-import ok=true mode=split datoms=200293 current=200293 parse_us=353777 tx_us=3343986 import_us=3697763 artist_rows=1 artist_us=68 release_rows=16 release_us=343
engine=vev workload=musicbrainz-import ok=true mode=split datoms=400293 current=400293 parse_us=697118 tx_us=6133631 import_us=6830749 artist_rows=1 artist_us=269 release_rows=16 release_us=459
engine=vev workload=musicbrainz-import ok=true mode=split datoms=763274 current=763274 parse_us=1399660 tx_us=15109942 import_us=16509602 artist_rows=1 artist_us=13 release_rows=16 release_us=413
```

The full row is the chunk-file import path with 8 value chunks. It validates
that Vev can import the current Datomic-derived subset without retaining one
huge prepared transaction. Progress output also checks the `"The Beatles"`
artist query after each chunk; this caught the previous parsed-string lifetime
bug.

The real-data query matrix compares Vev row fingerprints with a local restored
Datomic MusicBrainz database. Latest single-sample local run:

| Workload | Vev us | Datomic us | Rows | Fingerprint |
|---|---:|---:|---:|---|
| `musicbrainz-real-release-first` | 2967 | 82193 | 96 | `0ea8943f9ef3eb03` |
| `musicbrainz-real-track-first` | 3644 | 105229 | 89 | `9902d35f51335e40` |
| `musicbrainz-real-john-lennon-pre-1970-tracks` | 1636 | 69184 | 18 | `4598839c2af58631` |
| `musicbrainz-real-beatles-releases` | 454 | 1300 | 16 | `c57b012eecfd45ed` |
| `musicbrainz-real-beatles-short-track-collection` | 3535 | 76983 | 140 | `50498f806d973af7` |
| `musicbrainz-real-abbey-road-release-date-tuple` | 402 | 34482 | 1 | `732a43b4c30e7be0` |
| `musicbrainz-real-beatles-start-year-scalar` | 117 | 21233 | 1 | `0000000363d12a10` |
| `musicbrainz-real-abbey-road-track-minutes` | 545 | 53262 | 17 | `0f96f2c36020d31d` |
| `musicbrainz-real-miles-enum-id` | 177 | 31441 | 1 | `732425642d9c79c5` |
| `musicbrainz-real-beatles-track-count` | 3531 | 3150 | 1 | `0000000007068a26` |
| `musicbrainz-real-beatles-min-max-duration` | 2967 | 10409 | 1 | `9c45e54f061af2f6` |
| `musicbrainz-real-beatles-duration-stats` | 5975 | 83762 | 4 | `9880798d00baf3e0` |
| `musicbrainz-real-beatles-duration-sum` | 4405 | 86912 | 4 | `773afe226788bffa` |
| `musicbrainz-real-lookup-country` | 51 | 3371 | 1 | `4167e0bf9abd1220` |
| `musicbrainz-real-selected-artists-releases` | 520 | 5625 | 28 | `4887ecaa409643d2` |
| `musicbrainz-real-release-date` | 496 | 59046 | 3 | `8853c19c0b82edfa` |
| `musicbrainz-real-fallback-start-month` | 186 | 62173 | 2 | `ea197a760bcc6589` |
| `musicbrainz-real-get-some-country` | 379 | 22529 | 1 | `7e762f127575592a` |
| `musicbrainz-real-missing-start-year` | 7710 | 12922 | 1637 | `f5e245cdd9911040` |
| `musicbrainz-real-dynamic-attr` | 2750 | 56087 | 482 | `ffee4f7469006cd3` |
| `musicbrainz-real-top-duration` | 17 | 32496 | 1 | `949eb8db5ef70199` |
| `musicbrainz-real-not-beatles-male` | 359 | 5810 | 1 | `ea45bdc7e8b8201b` |
| `musicbrainz-real-or-two-artists` | 177 | 2231 | 2 | `de67eb0f77cf6b42` |
| `musicbrainz-real-relation-artist-release` | 134 | 9320 | 2 | `cb2f30e6783d093d` |
| `musicbrainz-real-not-join-release` | 256 | 3917 | 1 | `b6368059dfc36ef8` |
| `musicbrainz-real-or-join-release` | 271 | 1222 | 2 | `5f5db031e99d9c11` |
| `musicbrainz-real-map-beatles-releases` | 330 | 565 | 16 | `c57b012eecfd45ed` |
| `musicbrainz-real-keys-beatles-releases` | 731 | 45501 | 16 | `2476cdd6c54275f1` |
| `musicbrainz-real-strs-beatles-releases` | 759 | 42006 | 16 | `3fda7a2cf91332d1` |
| `musicbrainz-real-syms-beatles-releases` | 562 | 39944 | 16 | `18143fc0c84f8091` |
| `musicbrainz-real-rule-track-info` | 48552 | 320417 | 90 | `5f20ceb057e27418` |
| `musicbrainz-real-rule-short-track` | 14817 | 69136 | 135 | `ea50e22c017fe85c` |
| `musicbrainz-real-pull-release` | 545 | 4042 | 5 | `974ce160e8be7539` |
| `musicbrainz-real-dynamic-pull-release` | 1381 | 46875 | 17 | `16930ebda61a7b2c` |
| `musicbrainz-real-pull-release-nested` | 485 | 95232 | 5 | `f4f5c38625cab0c7` |
| `musicbrainz-real-direct-pull-artist` | 51 | 4899 | 1 | `0a11a6da90ea3115` |
| `musicbrainz-real-direct-pull-artist-wildcard` | 167 | 13568 | 1 | `996526d8caead8bb` |
| `musicbrainz-real-direct-pull-artist-releases` | 516 | 57976 | 1 | `78d748d66cc33844` |
| `musicbrainz-real-direct-pull-artist-releases-limit` | 161 | 24115 | 1 | `cc1f2e27d4ab3f6f` |
| `musicbrainz-real-direct-pull-artist-default` | 132 | 17613 | 1 | `90667f13a2d95b66` |
| `musicbrainz-real-direct-pull-artist-alias` | 119 | 12645 | 1 | `b9c707e2c34fb125` |
| `musicbrainz-real-direct-pull-many-artists` | 45 | 2539 | 2 | `3b0d165020d81f40` |
| `musicbrainz-real-direct-pull-release` | 147 | 21235 | 1 | `4e62d7d5775bd426` |

This snapshot says Vev is already strong on indexed lookup, bounded relation
input, direct lookup-ref pull, direct lookup-ref pull-many, nested release/media
pull, selected collection-input joins, `get-else`, dynamic attr inputs, ordinary
multi-hop clause/predicate joins, and pure
non-recursive rule bodies made from data clauses plus rule calls. The
restored-sample `release-first`/`track-first` rows use dependency-aware clause
planning with lazy candidate-count tie-breaking instead of eager full-relation
materialization. The `rule-track-info` row uses the same idea inside pure rule
bodies while preserving DataScript source-order behavior for predicates,
functions, `not`, `or`, and other effectful/error-sensitive rule steps.
Bounded `not`, `not-join`, `or`, and `or-join` rows now reuse the same
dependency-aware data-clause group planner. Single-attr top-n min/max aggregate
queries now use AVET range scans and stop after the requested distinct values.
Broad cardinality-one `missing?` projections now scan the projected attr range
directly and filter missing attrs through current indexes, avoiding full
relation materialization. The next planner/index work should come from this
kind of real row or larger Datalevin benchmark families, rather than
workload-specific shortcuts.

The next import-performance work is no longer basic feasibility. The remaining
write-side architecture issue is whole-array DB/index ownership and publication
as DB values grow. The likely direction is a shared/chunked immutable index
representation or a bulk builder that can apply several value chunks and publish
one DB snapshot.

## MusicBrainz Clojure Host Wrapper

`scripts/musicbrainz_clojure_vev_matrix.sh` runs MusicBrainz-shaped queries
through the public Clojure wrapper (`vev.core`), Java FFM wrapper, C ABI, and
`libvev`. This is a host API benchmark, not the main Datomic comparison.

The default command intentionally uses the small staged 500-value export and a
positive-result smoke query:

```sh
scripts/musicbrainz_clojure_vev_matrix.sh --samples 3 --warmups 1
```

Latest local smoke:

```text
engine=clojure-vev workload=musicbrainz-real-load ok=true stage=schema tx_us=486336
engine=clojure-vev workload=musicbrainz-real-load ok=true stage=values tx_us=3239488
engine=clojure-vev workload=musicbrainz-real-load ok=true total_us=3726538
engine=clojure-vev workload=musicbrainz-smoke-country-names ok=true rows=257 fingerprint=fd7d4b4cd5dd243c min_us=2055 median_us=2409 p90_us=2409 max_us=3194
```

The important current signal is the boundary: query execution through the public
Clojure API works and produces stable fingerprints, but setup through wrapper
EDN transaction text is not a realistic full-size MusicBrainz benchmark path. A
5k staged export loaded through `vev/transact-text!` took about 140s locally,
while native Vev imports the full 763k-datom chunked subset in about 16.5s. The
full host benchmark should therefore open a prebuilt durable Vev database, or
use a native bulk-load step, before timing Clojure query calls.

Representative real workloads are still available with `--workload` and the
full chunked export arguments:

```sh
scripts/musicbrainz_clojure_vev_matrix.sh \
  --workload beatles-releases \
  --schema build/musicbrainz/vev-mbrainz-subset-full-chunked-schema.edn \
  --values "" \
  --values-prefix build/musicbrainz/vev-mbrainz-subset-full-chunked \
  --values-chunks 8
```

For query-only host timing against a prebuilt durable Vev DB, pass `--uri`:

```sh
scripts/musicbrainz_clojure_vev_matrix.sh \
  --uri build/musicbrainz/vev-mbrainz-full.sqlite \
  --workload beatles-releases
```

Rebuild `build/lib/libvev.dylib` before host comparisons:

```sh
scripts/build_c_abi.sh
```

Latest 500-value durable-open smoke:

```text
engine=vev workload=musicbrainz-import ok=true mode=split datoms=793 current=793 parse_us=4805 tx_us=496293 import_us=501098 artist_rows=0 artist_us=211 release_rows=0 release_us=95
engine=vev workload=musicbrainz-import-persist ok=true path=build/musicbrainz/tmp-vev-host-smoke.sqlite persist_us=15306 error=
engine=clojure-vev workload=musicbrainz-real-open ok=true uri=build/musicbrainz/tmp-vev-host-smoke.sqlite info={:backend :sqlite, :path "build/musicbrainz/tmp-vev-host-smoke.sqlite", :basis-t 2, :tx-count 2, :tx-ids [1 2]}
engine=clojure-vev workload=musicbrainz-smoke-country-names ok=true rows=257 fingerprint=fd7d4b4cd5dd243c min_us=2131 median_us=2714 p90_us=2714 max_us=3003
```

Latest full durable-open Clojure wrapper correctness pass after rebuilding
`libvev`:

```text
engine=vev workload=musicbrainz-import ok=true mode=split datoms=763274 current=763274 parse_us=1427493 tx_us=15500566 import_us=16928059 artist_rows=1 artist_us=103 release_rows=16 release_us=365
engine=vev workload=musicbrainz-import-persist ok=true path=build/musicbrainz/vev-mbrainz-full-host.sqlite persist_us=4606362 error=
engine=vev workload=musicbrainz-import-reopened-release-first ok=true rows=96 query_us=3279 steps=8 clauses=425 candidates=708 max_bindings=265 output_rows=96
engine=clojure-vev workload=musicbrainz-real-open ok=true uri=build/musicbrainz/vev-mbrainz-full-host.sqlite open_us=9375021 info={:backend :sqlite, :path "build/musicbrainz/vev-mbrainz-full-host.sqlite", :basis-t 9, :tx-count 9, :tx-ids [1 2 3 4 5 6 7 8 9]}
engine=clojure-vev workload=musicbrainz-smoke-country-names ok=true rows=257 fingerprint=9b79e6c0b6bf748a
engine=clojure-vev workload=musicbrainz-real-release-first ok=true rows=96 fingerprint=0ea8943f9ef3eb03
engine=clojure-vev workload=musicbrainz-real-dynamic-pull-release ok=true rows=17 fingerprint=16930ebda61a7b2c
engine=clojure-vev workload=musicbrainz-real-direct-pull-release ok=true rows=1 fingerprint=4e62d7d5775bd426
```

The full wrapper command was:

```sh
scripts/musicbrainz_clojure_vev_matrix.sh \
  --uri build/musicbrainz/vev-mbrainz-full-host.sqlite \
  --workload all \
  --samples 1 \
  --warmups 0
```

It completed all 43 MusicBrainz query/pull rows with the same portable
fingerprints as the native Vev/Datomic matrix. The most important host-path
fix from this pass was dynamic pull pattern input ownership: pull patterns
supplied through EDN input text are now parsed into owned `Pull-Spec` values
before the transient parser document is released.

The same-process Clojure Datomic peer run with `--samples 3 --warmups 1`
provides the current host-to-host timing comparison for these representative
rows:

| Workload | Clojure Vev median us | Datomic median us | Vev/Datomic signal |
| --- | ---: | ---: | ---: |
| `release-first` | 3,895 | 5,061 | 1.3x faster |
| `track-first` | 4,056 | 47,961 | 11.8x faster |
| `beatles-releases` | 538 | 561 | parity |
| `beatles-duration-sum` | 4,338 | 2,913 | 0.7x |
| `missing-start-year` | 11,801 | 12,922 | parity/faster in latest focused run |
| `top-duration` | 481 | 32,496 | 67.6x faster |
| `rule-track-info` | 49,161 | 181,332 | 3.7x faster |
| `pull-release` | 519 | 326 | 0.6x |
| `direct-pull-artist` | 225 | 43 | host wrapper overhead remains visible |
| `direct-pull-artist-releases` | 2,203 | 289 | broad host pull materialization remains visible |
| `direct-pull-many-artists` | 408 | 23 | host wrapper path now uses prepared same-attr UUID lookup-ref batch |

This host-wrapper comparison is deliberately separate from the stronger native
Vev versus Datomic table above. The `--sqlite-output` plus `--uri` path removes
wrapper EDN loading from query timing, but Clojure/Java FFM materialization and
plain Clojure value equality still matter. The wrapper harness now preserves
query pull-expression rows with `vev/rows` so repeated pull maps do not collapse
under Clojure set equality. Direct `:pull` and `:pull-many` workloads now
prepare the pull pattern once per workload run through the public
Clojure/Java/C ABI prepared pull-pattern handle. Same-attribute UUID lookup-ref
`pull-many` calls also use a batch host entry point, so repeated direct pulls no
longer include pull-pattern EDN parsing or one native call per lookup ref.
Java value-tree conversion now reads strings through borrowed value text views
instead of allocating/freeing a native C string for every pull key and scalar
text, and the Clojure wrapper now builds pull maps directly instead of through
a lazy pair sequence. Lookup-ref attrs and other simple keyword/symbol EDN
fragments also bypass the generic EDN printer. Remaining host-performance work
is mostly result materialization and tiny-call overhead around direct pull and
pull-many, not query-engine correctness.

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

## SQLite Storage Baseline

Run the durable-storage harness from the Kvist repo root:

```sh
cd /Users/andreas/Projects/kvist
/Users/andreas/Projects/kvist/kvist \
  run /Users/andreas/Projects/vev/.worktrees/codex-item-vev-datalog/bench/sqlite_storage.kvist
```

Current sample output on June 26, 2026:

```text
engine=vev-sqlite workload=single-append n=1 min_us=73 median_us=86 p90_us=111 max_us=219 samples=50
engine=vev-sqlite workload=batch-append n=100 min_us=1595 median_us=4835 p90_us=5484 max_us=5517 samples=20
engine=vev-sqlite workload=batch-transact-memory n=100 min_us=913 median_us=3790 p90_us=4503 max_us=4697 samples=20
engine=vev-sqlite workload=batch-append-sqlite n=100 min_us=730 median_us=1022 p90_us=1095 max_us=1101 samples=20
engine=vev-sqlite workload=batch-before-snapshot n=100 min_us=2 median_us=36 p90_us=91 max_us=118 samples=20
engine=vev-sqlite workload=batch-resolve-tx n=100 min_us=171 median_us=1180 p90_us=1291 max_us=1327 samples=20
engine=vev-sqlite workload=batch-apply-resolved n=100 min_us=743 median_us=2622 p90_us=3221 max_us=3260 samples=20
engine=vev-sqlite workload=append-log-copy n=100 min_us=181 median_us=193 p90_us=225 max_us=263 samples=30
engine=vev-sqlite workload=append-index-build n=100 min_us=1376 median_us=1396 p90_us=1417 max_us=1476 samples=30
engine=vev-sqlite workload=reopen-rebuild n=2000 min_us=43157 median_us=43740 p90_us=44350 max_us=45349 samples=30
engine=vev-sqlite workload=reopened-query n=2000 min_us=17 median_us=18 p90_us=22 max_us=212 samples=30
```

This is not yet the final write benchmark. It establishes a repeatable baseline
for SQLite-backed single-transaction appends, multi-entity transaction batches,
full persisted DB reopen cost, and query performance after reopen. The current
batch row measures the whole `transact-sqlite-*` path. The split rows show the
current bottleneck: SQLite append for a 100-entity / 300-datom transaction is
around 1ms median, while in-memory transaction/index maintenance is around
3.8ms median as the DB grows. The main fixes so far: ordinary non-schema
transactions skip unnecessary full-schema validation, transaction DB snapshots
clone existing indexes/schema caches instead of rebuilding every index from
datoms, append-only eligibility avoids current-DB lookups for new-entity bulk
imports, ordered new-entity imports avoid formatted entity/attr eligibility
keys, and `eavt` entity metadata is extended instead of rebuilt when new entity
ids sort after existing ids. The pipeline split shows snapshot creation is now
tens of microseconds, resolution is around 1.2ms, and applying already-resolved
append ops is around 2.6ms. The append-only core rows show that copying the
current datom log is sub-millisecond and incremental index construction is
around 1.4ms, so the remaining write work is now mostly index ownership/copy
structure plus the SQLite commit.

## SQLite Write Bench

Run the first Datalevin-style write harness from the Kvist repo root:

```sh
cd /Users/andreas/Projects/kvist
/Users/andreas/Projects/kvist/kvist \
  run /Users/andreas/Projects/vev/.worktrees/codex-item-vev-datalog/bench/write_bench.kvist
```

The default run is intentionally small. Scale and select workloads with:

```sh
/Users/andreas/Projects/kvist/kvist \
  build /Users/andreas/Projects/vev/.worktrees/codex-item-vev-datalog/bench/write_bench.kvist \
  --out /Users/andreas/Projects/vev/.worktrees/codex-item-vev-datalog/build/write-bench

/Users/andreas/Projects/vev/.worktrees/codex-item-vev-datalog/build/write-bench \
  --total 10000 --report-every 1000 --mixed-operations 10000 --batch 100
```

To mirror Datalevin's upstream sequence, run pure writes into a durable store
and then run mixed read/write against that same store:

```sh
/Users/andreas/Projects/vev/.worktrees/codex-item-vev-datalog/build/write-bench \
  --workload pure --path /tmp/vev-write-bench.sqlite \
  --total 1000000 --report-every 10000 --batch 100

/Users/andreas/Projects/vev/.worktrees/codex-item-vev-datalog/build/write-bench \
  --workload mixed --path /tmp/vev-write-bench.sqlite \
  --total 1000000 --mixed-operations 2000000 --report-every 10000
```

Options:

- `--workload`: `both` for the local default, `pure`, or `mixed`
- `--path`: SQLite file for single-workload runs; `both` uses derived temp paths
- `--total`: pure-write rows to transact and mixed-read/write seed size
- `--report-every`: row interval for progress samples
- `--mixed-operations`: total alternating read/write operations
- `--batch`: run a single pure-write batch size; omit to run 1, 10, 100, 1000
- `--seed-batch`: mixed-workload seed import batch size

Current sample output on June 26, 2026:

```text
engine=vev-sqlite workload=pure-write batch=1 total=200 columns=writes,elapsed_s,throughput_writes_per_s,call_latency_ms,commit_latency_ms
100,0.011,8872.33,0.110,0.110
200,0.031,6508.09,0.192,0.192
engine=vev-sqlite workload=pure-write batch=10 total=200 columns=writes,elapsed_s,throughput_writes_per_s,call_latency_ms,commit_latency_ms
100,0.005,21132.71,0.461,0.461
200,0.011,18885.74,0.572,0.572
engine=vev-sqlite workload=pure-write batch=100 total=200 columns=writes,elapsed_s,throughput_writes_per_s,call_latency_ms,commit_latency_ms
100,0.003,31446.54,3.066,3.066
200,0.008,26034.89,4.382,4.382
engine=vev-sqlite workload=pure-write batch=1000 total=200 columns=writes,elapsed_s,throughput_writes_per_s,call_latency_ms,commit_latency_ms
200,0.006,31152.65,6.190,6.190
engine=vev-sqlite workload=mixed-read-write batch=1 total=400 columns=writes,elapsed_s,throughput_writes_per_s,call_latency_ms,commit_latency_ms
100,0.010,4959.83,0.199,0.187
200,0.021,4781.26,0.214,0.205
300,0.036,4173.16,0.298,0.288
400,0.049,4107.87,0.252,0.242
```

This is a first Vev-native harness for the Datalevin `write-bench` family, not
yet a direct apples-to-apples run of the upstream benchmark. It measures:

- pure durable writes at batch sizes 1, 10, 100, and 1000
- mixed read/write behavior against an existing SQLite-backed Vev store
- end-to-end `transact-sqlite-*` call latency and commit-path latency

The default dataset is deliberately small so it can run during ordinary
development. The schema intentionally matches Datalevin's write-bench shape:
`:item/key` is a plain long attribute, not `:db.unique/identity`. Modeling the
key as unique identity sent the harness through upsert/uniqueness work and made
the benchmark measure a different workload. With the corrected plain-key shape,
the small pure-write harness is in the same broad throughput range as the
published Datalevin write-bench discussion. A larger 10k local run shows the
next real bottleneck: batch-100 pure write is still around 13k writes/second,
but batch-1 pure write drops to roughly 544 writes/second by 10k rows and mixed
read/write drops to roughly 184 writes/second. That points at per-commit
immutable DB/index copying, not EDN parsing or SQLite binding. The next storage
performance work should therefore introduce shared immutable DB/index storage
before chasing smaller loop-level optimizations. After that, wire Vev into
Datalevin's upstream `write-bench` shape for direct comparison.

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

Prepared queries now cache per-rule-call plans on the parsed query value, and
plain positive recursive rule bodies use a conservative delta iteration across
each recursive rule-call position. Rule memo entries also keep set-backed
dedupe keys for primitive output bindings. The small `datascript-bench` rule
rows measured after those changes are:

| Workload | DataScript median | Vev median | DataScript/Vev |
|---|---:|---:|---:|
| `rules-wide-3x3` | 0.45ms | 0.25ms | 1.80x |
| `rules-long-10x3` | 0.96ms | 0.31ms | 3.10x |

## Stress Comparison

The stress comparison uses fewer samples and larger recursive-rule workloads.
It is intended for scaling direction, not stable microbenchmark numbers.

| Workload | Vev text | Vev prepared |
|---|---:|---:|
| `stress-chain-root n=300` | 1683.9x | 1779.6x |
| `stress-chain-leaf n=300` | 3857.2x | 4073.5x |
| `stress-chain-all n=200` | 29.0x | 28.9x |
| `stress-tree-root n=364` | 2.6x | 2.6x |
| `stress-mutual-root n=30` | 16.6x | 22.7x |

The stress harness also emits Vev-only rows for workloads that are currently
too expensive for routine DataScript comparison:

| Workload | Vev text median | Vev prepared median | Notes |
|---|---:|---:|---|
| `stress-dense-root n=60` | 203us | 190us | Dense DAG, width 8 |
| `stress-dense-root n=160` | 518us | 501us | Dense DAG, width 8 |
| `stress-filtered-root n=10` | 56us | 31us | Linear recursive rule with a target-node data filter |

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
  alternating two-rule recursion are optimized. Plain positive recursive rule
  groups now have a component-local memoized fixpoint, and recursive bodies use
  per-iteration delta tables rather than re-probing the full memo each
  iteration. Memo insertion now uses set-backed primitive binding keys instead
  of always scanning accumulated outputs. The next step is to move that
  binding-row evaluator into
  relation-native operators and extend semi-naive coverage to richer rule bodies
  beyond the current positive data-clause/rule-call subset.
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
  values, then scan the right `avet` range once per join value. The native q5
  result-set path is around 4ms for 5000 rows. The typed triple-column ABI plus
  batched borrowed string pointer/length metadata brings the public Clojure row
  to around 19ms, and the sampled string-dictionary path brings it to around
  17ms versus DataScript around 144ms. Remaining q5 work should target
  larger-grained Java/Clojure materialization for string/value rows and broader
  physical-operator integration, not the indexed self-join itself. General
  relation hash joins are also in place for one primitive common variable, with
  fallback to the older nested join when lookup-ref/source semantics require it.
- Same-entity star/projection queries use reusable indexed shapes inspired by
  Datalevin's sorted scans. Single-filter star queries such as q2 and
  multi-filter star queries such as q3/q4 now use the all-current merge-stream
  operator: it aligns entity-sorted `AVET(attr,value)` filter ranges and
  `AEVT(attr)` output ranges together. The latest 20k local
  `datascript-bench` comparison has Vev ahead of DataScript on
  q1/q2/q2-switch/q3/q4/qpred1/qpred2, with q2 at about 1.3x, q2-switch at
  about 3.1x, q3 at about 2.6x, and q4 at about 1.8x. This is still above the
  published Datalevin target, so the next work is to fold these paths into the
  normal physical relation operator layer and reduce Clojure/JVM materialization
  overhead for returned row vectors/sets.
- q1 and q5's remaining costs are mostly host result-shape overhead. Prepared
  diagnostic rows show q1 improves from roughly 0.30 ms for
  Datomic/DataScript-style `q` to roughly 0.09 ms for prepared `rows`; q5 still
  shows a native/public gap for string-heavy rows even after the typed
  triple-column ABI and batched borrowed string accessors. The next work should
  target string-heavy Java materialization and set/vector construction through
  the Clojure adapter, not index lookup. Current diagnostic rows show q2/qpred
  pair-column extraction is close to the native path, while q5 column extraction
  is still dominated by string handling across the Java/Clojure boundary. The
  triple-column ABI now exposes an optional per-result string dictionary for
  repeated string-heavy rows, but native result construction is still several
  times faster than the host-facing row shape.
- The relation engine now has a DataScript-shaped compound primitive hash join
  for relations with one or more common primitive variables. It uses
  length-prefixed compound keys and preserves the existing semantic fallback
  for non-primitive, lookup-ref-sensitive, or source-sensitive joins.
- Keep expanding benchmark coverage from real Datomic/DataScript-style
  workloads, including MusicBrainz-shaped queries, so performance work stays
  tied to database behavior rather than isolated microbenchmarks.

## MusicBrainz Query Profile

`bench/musicbrainz_query_profile.kvist` runs the deterministic MusicBrainz mini
fixture from `docs/musicbrainz.md` or the imported restored 1968-1973 subset.
It catches planner regressions, compares clause-order shapes, and now provides
the Vev side of the local Datomic comparison.

Run:

```bash
cd /Users/andreas/Projects/kvist
./kvist run /Users/andreas/Projects/vev/bench/musicbrainz_query_profile.kvist
```

Build and run the real imported subset:

```bash
cd /Users/andreas/Projects/kvist
./kvist build /Users/andreas/Projects/vev/bench/musicbrainz_query_profile.kvist \
  --out /Users/andreas/Projects/vev/build/bench/musicbrainz_query_profile

/Users/andreas/Projects/vev/build/bench/musicbrainz_query_profile \
  --dataset real \
  --schema /Users/andreas/Projects/vev/build/musicbrainz/vev-mbrainz-subset-full-chunked-schema.edn \
  --values-prefix /Users/andreas/Projects/vev/build/musicbrainz/vev-mbrainz-subset-full-chunked \
  --values-chunks 8 \
  --samples 2 \
  --warmups 1
```

Run the Datomic side:

```bash
scripts/musicbrainz_sample.sh query-matrix-datomic --samples 2 --warmups 1
```

Current workloads:

- `musicbrainz-mini-release-first` / `musicbrainz-real-release-first`: starts
  from artist, release, year, medium, track
- `musicbrainz-mini-track-first` / `musicbrainz-real-track-first`: starts from
  artist and track, then joins release,
  medium, and track

The output includes timing samples plus Vev profile counters:
`steps`, `clauses`, `candidates`, `max_bindings`, and `output_rows`. The real
runner also prints a portable sorted-row fingerprint; `--print-rows true` dumps
the normalized row keys for direct comparison.

Current local correctness/performance snapshot:

```text
engine=vev workload=musicbrainz-real-load ok=true datoms=763274 current=763274 import_us=16740120
engine=vev workload=musicbrainz-real-release-first ok=true rows=96 fingerprint=0ea8943f9ef3eb03 min_us=2967 median_us=2967 p90_us=2967 max_us=2967 steps=8 clauses=425 candidates=708 max_bindings=265 output_rows=96
engine=vev workload=musicbrainz-real-track-first ok=true rows=89 fingerprint=9902d35f51335e40 min_us=3644 median_us=3644 p90_us=3644 max_us=3644 steps=9 clauses=669 candidates=931 max_bindings=265 output_rows=89
engine=vev workload=musicbrainz-real-beatles-releases ok=true rows=16 fingerprint=c57b012eecfd45ed min_us=412 steps=3 clauses=55 candidates=107 max_bindings=53 output_rows=16
engine=vev workload=musicbrainz-real-beatles-track-count ok=true rows=1 fingerprint=0000000007068a26 min_us=3600 steps=3 clauses=490 candidates=977 max_bindings=488 output_rows=1
engine=vev workload=musicbrainz-real-beatles-min-max-duration ok=true rows=1 fingerprint=9c45e54f061af2f6 min_us=2934 steps=3 clauses=490 candidates=976 max_bindings=488 output_rows=1
engine=vev workload=musicbrainz-real-lookup-country ok=true rows=1 fingerprint=4167e0bf9abd1220 min_us=40 steps=1 clauses=1 candidates=1 max_bindings=1 output_rows=1
engine=vev workload=musicbrainz-real-selected-artists-releases ok=true rows=28 fingerprint=4887ecaa409643d2 min_us=513 steps=3 clauses=69 candidates=132 max_bindings=65 output_rows=28
engine=vev workload=musicbrainz-real-not-beatles-male ok=true rows=1 fingerprint=ea45bdc7e8b8201b min_us=359 steps=3 clauses=2 candidates=2 max_bindings=1 output_rows=1
engine=vev workload=musicbrainz-real-or-two-artists ok=true rows=2 fingerprint=de67eb0f77cf6b42 min_us=177 steps=2 clauses=2 candidates=2 max_bindings=2 output_rows=2

engine=datomic workload=musicbrainz-real-release-first ok=true rows=96 fingerprint=0ea8943f9ef3eb03
engine=datomic workload=musicbrainz-real-track-first ok=true rows=89 fingerprint=9902d35f51335e40
engine=datomic workload=musicbrainz-real-beatles-releases ok=true rows=16 fingerprint=c57b012eecfd45ed
engine=datomic workload=musicbrainz-real-beatles-track-count ok=true rows=1 fingerprint=0000000007068a26
engine=datomic workload=musicbrainz-real-beatles-min-max-duration ok=true rows=1 fingerprint=9c45e54f061af2f6
engine=datomic workload=musicbrainz-real-lookup-country ok=true rows=1 fingerprint=4167e0bf9abd1220
engine=datomic workload=musicbrainz-real-selected-artists-releases ok=true rows=28 fingerprint=4887ecaa409643d2
engine=datomic workload=musicbrainz-real-not-beatles-male ok=true rows=1 fingerprint=ea45bdc7e8b8201b
engine=datomic workload=musicbrainz-real-or-two-artists ok=true rows=2 fingerprint=de67eb0f77cf6b42
```

The first restored-sample query batch matches Datomic exactly after
normalization. The aggregate rows are bounded to Beatles tracks rather than
global track scans so the default matrix stays useful during normal development;
global aggregate scans can be added later as explicit stress workloads. Vev now
keeps the multi-join clause-order rows selective through dependency-aware
clause/predicate planning, and pure rule-expanded joins use the same planning
idea inside rule bodies. The remaining MusicBrainz query-performance pressure
is bounded `or`/`or-join` and `not`/`not-join` planning.
