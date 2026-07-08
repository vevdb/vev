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
   reachability stress harness. Vev now has an adapter under
   `bench/math_bench`.
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

For durable write/read storage work, `bench/write_bench.kvist` also has
storage-specific workloads. The most important current row is:

```sh
build/write-bench --workload manifest-chain-read --batch 1 --total 1000 --read-samples 3
```

For durable group-commit work, use:

```sh
build/write-bench --workload sqlite-store-db-heavy-group-commit --batch 10 --total 1000
```

This workload is intentionally different from `sqlite-store-db-heavy --batch
10`. The ordinary `sqlite-store-db-heavy` batch mode creates one logical
transaction with more tx-data. `sqlite-store-db-heavy-group-commit` creates
several logical transactions, keeps their distinct tx ids/reports/retained
`db-after` handles, and commits them through one SQLite transaction. It should
be used to validate that group commit improves durable ingest without weakening
Datomic-like transaction semantics.

Latest local group-commit comparison on July 5, 2026, with `--total 1000`,
retaining every `db-after` handle:

| Workload | Group | Durability | Throughput | Commit/call latency | Commit fsync/profile cost | Main remaining cost |
|---|---:|---|---:|---:|---:|---|
| `sqlite-store-db-heavy --batch 1` | 1 | `:normal` | 1714 writes/s | 0.579ms/write | 0.383ms/write | one SQLite commit per logical tx |
| `sqlite-store-db-heavy-group-commit --batch 10` | 10 | `:normal` | 4226 writes/s | 2.368ms/group | 0.038ms/write | per-logical root append/persist setup |
| `sqlite-store-db-heavy-group-commit --batch 50` | 50 | `:normal` | 5185 writes/s | 10.505ms/group | 0.014ms/write | per-logical root append/persist setup |

The important result is architectural: logical group commit reduces durable
commit cost while preserving distinct tx ids/reports and retained `db-after`
values. It is not done. The group loop now carries the current basis forward
with same-handle SQLite snapshots, so later logical transactions in the same
SQLite transaction do not reopen the source-backed view through a second
connection. On the local batch-50 gate, `open_before_ms` dropped from about
`0.069ms/write` to about `0.002ms/write`; on batch 10 it is about
`0.008ms/write`. The loop also skips opening a carried source snapshot after
the final logical transaction. The next storage engine target is group-local
root/write-state publication: avoid redoing per-index root append/persist setup
for each logical transaction while still publishing one durable basis and report
per logical tx.

This writes batch-1 source-backed commits that publish chained manifest roots,
measures open/query before compaction, runs explicit index compaction, and
measures open/query again. On July 3, 2026, after resumable merge/manifest
cursor state, the 1000-row run without automatic maintenance showed:

| Row | Median |
|---|---:|
| uncompacted broad source query | 1.18s |
| explicit compaction | 0.79s |
| compacted broad source query | 66.2ms |

The same run with bounded automatic manifest maintenance:

```sh
build/write-bench --workload manifest-chain-read --batch 1 --total 1000 --read-samples 3 --auto-maintain-steps 8 --auto-maintain-trigger 5
```

showed:

| Row | Median |
|---|---:|
| write latency | 2.88ms/write |
| max visible merge/manifest runs | 5 |
| bounded-manifest broad source query | 8.1ms |
| bounded-manifest selective source query | 3.3ms |
| explicit compaction after bounded maintenance | 0.15s |
| compacted broad source query | 11.8ms |
| compacted selective source query | 5.1ms |

The lesson is storage-architecture, not a query shortcut: manifest publication
keeps tiny commits cheap, compacted persisted roots can be queried through
`DB-Read-Source` without resident rebuild, and automatic bounded manifest
maintenance now prevents unchecked run-chain growth. Broad prefix source scans
over manifest roots now scan child runs directly instead of binary-searching
random positions in a merged cursor. Resumable merge/manifest cursor state
removed the worst repeated-page replay cost in sequential scans, the SQLite DB
snapshot read cursor now uses a larger page window than the write leaf chunk
size, append-only SQLite snapshots skip redundant currentness materialization,
and merge/manifest pages retain datoms that were already loaded for k-way
ordering. A sampled 5000-write run with the same automatic maintenance policy
shows bounded broad queries around 36ms before explicit compaction and 55ms
after compaction. The old bounded selective gap is closed beyond the
1000-row gate: AEVT/AVET run manifests carry attr-segment value bounds and
first/last segment ordinals derived from direct runs and compacted child roots,
and the source planner drives literal attr/value joins through the filtered
entity+attr operator instead of the broad two-attr scan. Lazy manifest
run-range streams avoid materializing an intermediate datom array and skip
redundant prefix checks for exact bounds. Single-column source projections now
dedupe through a keyed set instead of linearly scanning already-emitted rows,
removing an O(n^2) result-construction cost from broad queries. Non-manifest
merge roots now also use lazy run ranges instead of building an owned datom
array before streaming results. On July 3, 2026, a 5000-row run with
`--auto-maintain-steps 8 --auto-maintain-trigger 5` measured:

| Row | Median |
|---|---:|
| open | 1.97ms |
| bounded-manifest broad source query | 36.0ms |
| bounded-manifest broad `Store-DB` query | 35.9ms |
| bounded-manifest selective source query | 11.5ms |
| bounded-manifest selective `Store-DB` query | 10.3ms |
| explicit compaction after bounded maintenance | 2.17s |
| compacted broad source query | 54.8ms |
| compacted broad `Store-DB` query | 55.1ms |
| compacted selective source query | 16.7ms |
| compacted selective `Store-DB` query | 17.0ms |

On July 7, 2026, the manifest-chain read benchmark gained retained `Store-DB`
rows. Each retained row warms one root-backed immutable DB value, then queries a
retained copy that shares the same SQLite index page cache. The mutable
`Store-Conn` read path also keeps a current-root cache, so repeated read-only
store queries reuse the same root-backed page cache until the store writes or
republishes roots. A 1000-row run with `--auto-maintain-steps 8
--auto-maintain-trigger 5 --read-samples 3` measured:

| Row | Median | Cache After Warm |
|---|---:|---:|
| bounded-manifest broad source query | 8.8ms | n/a |
| bounded-manifest broad `Store-DB` query | 5.4ms | n/a |
| bounded-manifest broad retained `Store-DB` query | 7.4ms | 3 |
| bounded-manifest selective source query | 3.4ms | n/a |
| bounded-manifest selective `Store-DB` query | 2.4ms | n/a |
| bounded-manifest selective retained `Store-DB` query | 2.4ms | 2 |
| compacted broad source query | 8.5ms | n/a |
| compacted broad `Store-DB` query | 7.6ms | n/a |
| compacted broad retained `Store-DB` query | 7.9ms | 3 |
| compacted selective source query | 5.8ms | n/a |
| compacted selective `Store-DB` query | 4.9ms | n/a |
| compacted selective retained `Store-DB` query | 5.1ms | 8 |

The retained rows validate that root-backed DB values share loaded page state
across retains, including manifest/merge output pages. The current-root cache
also closes the previous large mutable `Store-Conn` read gap. The remaining
measured gap is now narrower: mutable store reads still pay a small wrapper
cost over direct immutable `Store-DB` reads, and broad result materialization
dominates once page/cache reuse is working.

The next benchmark target is reducing per-returned-value materialization and
compacted broad-root read cost with typed/column result batches. The manifest
run stream still triggers a Kvist ownership warning for owned arrays
transferred into the returned stream struct; runtime cleanup is handled by
`delete-source-index-datom-stream`, but the warning remains a compiler
ergonomics issue.

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

## Math Bench

Vev has a first adapter for Datalevin's `math-bench` under
`bench/math_bench`. The exporter mirrors Datalevin's JSON-to-datoms mapping for
the Mathematics Genealogy dataset and writes Vev EDN transaction chunks.

Run the exporter:

```sh
bench/math_bench/run_export.sh
```

Run one workload:

```sh
MATH_BENCH_WARMUPS=0 MATH_BENCH_SAMPLES=1 bench/math_bench/run_vev.sh q1
```

The first checked full-data Vev results use 1,837,904 transaction items and
1,837,918 current datoms after schema. Import currently takes about 69-72s from
EDN chunks, so query timings should be read separately from import timings.
The Vev math benchmark harness treats the documented Q1/Q2/Q3/Q4 row counts as
correctness guards: if an optimizer changes those counts, the workload prints
`ok=false` even if the query got faster.

| Workload | Vev query time | Rows | Current status |
|---|---:|---:|---|
| `q1` | 0.42ms | 2 | Good selective/bound rule path |
| `q2` | 3.86s | 34,073 | Broad materialized-rule joins remain slow, but projection, repeated materialization, final bound lookup, numeric join keys, single-rule typed projection rebuilds, and entity-leading compound joins are reduced |
| `q3` | 3.47s | 29,317 | Same remaining broad join/dedupe cost plus predicate filtering; binary typed var equality avoids value-wrapper comparison |
| `q4` | 1.01s | 135 | Completes through derived transitive closure over a derived two-hop edge |

Important result: Q4 originally did not finish within several minutes because
`anc` recurses over derived rule `adv`, not over a direct DB ref attr. Vev now
recognizes this general shape:

```clojure
[(edge ?x ?y) [?x :left/ref ?m] (leaf ?m ?y)]
[(leaf ?m ?y) [?m :right/ref ?y]]
[(tc ?x ?y) (edge ?x ?y)]
[(tc ?x ?y) (edge ?x ?z) (tc ?z ?y)]
```

The planner lowers that to a derived two-hop adjacency and then reuses the
existing linear transitive operator. This is not benchmark-name-specific, but
it is still a narrow physical rule operator.

Q2/Q3 are now past the first rule-dispatch problem. Supported normal `$`
queries can run through the relation engine, clauses are applied in where-step
order, small bound relations use direct bound-clause expansion, and
non-recursive helper rules can materialize as relations. The materialized rule
path recognizes two important general physical shapes:

- derived two-hop edges such as `[(adv ?x ?y) [?x :person/advised ?d] (author ?d ?y)]`
- same-entity cardinality-one projections such as `[(univ ?c ?u) [?d :cid ?c] [?d :univ ?u]]`

The remaining Q2/Q3 cost is not repeated rule invocation anymore
(`rule_calls=3`). It is broad materialized joins and final dedupe over hundreds
of thousands of rows. The current join layer now uses typed hash joins for
single-column joins too, with entity/int key normalization matching Vev query
equality, and moderately sized bound clause steps use indexed bind joins instead
of materializing a whole attr relation. Direct physical rule builders fill typed
relation columns while producing compatibility binding rows.

Recent broad-rule work removed two more generic costs. Rule-call projection now
has a typed fast path for distinct variable calls such as `(univ ?x ?u)`, and a
query-local materialized-rule cache reuses single-branch helper rule relations
such as `univ`/`area` when the same unprojected rule is called with different
variable names. Bound data clauses of the common `[?e :attr ?v]` shape can now
also use typed relation columns to do direct `eavt` lookups instead of entering
the generic per-binding clause matcher; this is a modest win on math-bench
because final lookup is no longer dominant. Single-column typed entity/int joins
now also hash on numeric keys instead of formatted strings, which removes a
generic allocation-heavy part of the broad join path. Single-branch cached or
direct physical rule relations can also project distinct-variable rule calls
back as typed relations directly, avoiding an immediate projection-to-bindings,
dedupe, and typed-column reconstruction pass. Compound typed joins whose first
shared variable is an entity id can now hash that leading entity key and verify
the remaining shared columns in the candidate bucket, avoiding formatted
compound string keys for Datomic-style entity joins. The next engine work should
make these rule relations more fully columnar/streamed and remove the remaining
generic `Binding` row construction and final dedupe costs from the broad path.
Binary `=` / `!=` predicates over two typed variables can also compare relation
columns directly, avoiding per-row `Value` wrapper resolution for common
filters such as q3's `(!= ?a1 ?a2)`.

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
| `musicbrainz-real-beatles-duration-stddev` | 4267 | 81619 | 4 | `1de88b32d5193758` |
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
5k staged export loaded through `vev/transact-text` took about 140s locally,
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

Rebuild the platform library under `build/lib` before host comparisons:

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
| `missing-start-year` | 7,742 | 12,922 | 1.7x faster in latest focused run |
| `top-duration` | 491 | 32,496 | 66.2x faster |
| `rule-track-info` | 49,161 | 181,332 | 3.7x faster |
| `pull-release` | 519 | 326 | 0.6x |
| `direct-pull-artist` | 225 | 43 | host wrapper overhead remains visible |
| `direct-pull-artist-releases` | 2,203 | 289 | broad host pull materialization remains visible |
| `direct-pull-many-artists` | 366 | 23 | host wrapper path now uses prepared same-attr UUID lookup-ref batch |

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
fragments also bypass the generic EDN printer. Single string-column query
results use a typed C ABI array path, and broad `missing?` string projections
avoid native `ResultSet` row/value materialization before Java reads borrowed
UTF-8 slices. Remaining host-performance work is mostly result materialization
and tiny-call overhead around direct pull and pull-many, not query-engine
correctness.

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

Current sample output on June 29, 2026:

```text
engine=vev-sqlite workload=single-append n=1 min_us=115 median_us=273 p90_us=665 max_us=954 samples=50
engine=vev-sqlite workload=batch-append n=100 min_us=2421 median_us=8422 p90_us=13346 max_us=18791 samples=20
engine=vev-sqlite workload=batch-transact-memory n=100 min_us=963 median_us=1932 p90_us=2568 max_us=2605 samples=20
engine=vev-sqlite workload=batch-append-sqlite n=100 min_us=1205 median_us=5901 p90_us=10028 max_us=13593 samples=20
engine=vev-sqlite workload=batch-before-snapshot n=100 min_us=1 median_us=37 p90_us=79 max_us=89 samples=20
engine=vev-sqlite workload=batch-resolve-tx n=100 min_us=144 median_us=147 p90_us=156 max_us=162 samples=20
engine=vev-sqlite workload=batch-apply-resolved n=100 min_us=876 median_us=1776 p90_us=2251 max_us=2317 samples=20
engine=vev-sqlite workload=append-log-copy n=100 min_us=184 median_us=223 p90_us=262 max_us=374 samples=30
engine=vev-sqlite workload=append-index-build n=100 min_us=1517 median_us=1548 p90_us=1623 max_us=1642 samples=30
engine=vev-sqlite workload=persisted-index-load n=2000 min_us=6760 median_us=7056 p90_us=7760 max_us=13029 samples=30
engine=vev-sqlite workload=persisted-index-page-load n=2000 min_us=10357 median_us=10678 p90_us=10983 max_us=11039 samples=30
engine=vev-sqlite workload=persisted-index-cursor-scan n=2000 min_us=7235 median_us=7340 p90_us=7464 max_us=7553 samples=30
engine=vev-sqlite workload=reopen-rebuild n=2000 min_us=57509 median_us=59757 p90_us=61672 max_us=62516 samples=30
engine=vev-sqlite workload=reopened-query n=2000 min_us=18 median_us=19 p90_us=23 max_us=251 samples=30
```

This is not yet the final write benchmark. It establishes a repeatable baseline
for SQLite-backed single-transaction appends, multi-entity transaction batches,
full persisted DB reopen cost, and query performance after reopen. The current
batch row measures the whole `transact-sqlite-*` path. The split rows show the
current bottleneck: SQLite append for a 100-entity / 300-datom transaction is
around 5.8ms median in this run, while in-memory transaction/index maintenance
is around 1.9ms median as the DB grows. The main fixes so far: ordinary non-schema
transactions skip unnecessary full-schema validation, transaction DB snapshots
clone existing indexes/schema caches instead of rebuilding every index from
datoms, append-only eligibility avoids current-DB lookups for new-entity bulk
imports, ordered new-entity imports avoid formatted entity/attr eligibility
keys, and `eavt` entity metadata is extended instead of rebuilt when new entity
ids sort after existing ids. The pipeline split shows snapshot creation is now
tens of microseconds, resolution is around 0.14ms, and applying
already-resolved append ops is around 1.7ms. The append-only core rows show
that copying the current datom log is sub-millisecond and incremental index
construction is around 1.5ms. The persisted-index-load,
persisted-index-page-load, and persisted-index-cursor-scan rows are the first
storage-architecture measurements for chunk-backed reopen: they follow latest
root pointers and materialize persisted index entries, either as whole logical
indexes, bounded persisted pages, or through the cached cursor abstraction,
without parsing datom rows or building a full `DB`. The remaining write/open
work is now mostly index ownership/copy structure, chunk-backed `DB` snapshots,
and the SQLite commit.

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

- `--workload`: `both` for the local default, `pure`, `pure-store-report`,
  `pure-store-report-deferred`, `manifest-chain-read`,
  `sqlite-store-db-heavy`, `mixed`, `snapshot-heavy`,
  `shared-snapshot-heavy`, `shared-snapshot-heavy-direct`, or
  `shared-store-db-heavy`
- `--path`: SQLite file for single-workload runs; `both` uses derived temp paths
- `--total`: pure-write rows to transact and mixed-read/write seed size
- `--report-every`: row interval for progress samples
- `--mixed-operations`: total alternating read/write operations
- `--batch`: run a single pure-write batch size; omit to run 1, 10, 100, 1000
- `--seed-batch`: mixed-workload seed import batch size
- `--compact-every`: for `pure-store-report`, run explicit index compaction
  every N writes and report it as maintenance latency
- `--maintain-every`: for `pure-store-report`, run one bounded incremental
  index-maintenance step every N writes and report it as maintenance latency
- `--maintain-steps`: for `pure-store-report`, run up to N queued/index
  maintenance work units at each `--maintain-every` interval; default is 1
- `--maintain-target`: for `pure-store-report`, drain queued maintenance until
  the pending queue is at or below this size, bounded by `--maintain-steps`.
  The default `-1` keeps the older blind fixed-budget behavior.
- `--auto-maintain-steps`: for `pure-store-report`, configure the store itself
  to run up to N maintenance units after each successful durable commit.
- `--auto-maintain-target`: pending-queue target for automatic store
  maintenance; default is 0.
- `--auto-maintain-trigger`: pending-queue depth that triggers automatic store
  maintenance. If omitted, automatic maintenance runs whenever pending work is
  above the target, preserving the eager policy.
- `--deferred-maintain-steps`: for `pure-store-report-deferred`, run normal
  `Store-Tx-Report` writes with foreground auto-maintenance disabled, then
  reopen the store and drain queued maintenance in batches of this size.

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
- pure durable writes through the current storage-neutral
  `Store-Tx-Report` API via `--workload pure-store-report`
- durable SQLite-backed writes that retain a public `Store-DB` value from each
  `Store-Tx-Report` via `--workload sqlite-store-db-heavy`
- mixed read/write behavior against an existing SQLite-backed Vev store
- snapshot-heavy writes that retain an ordinary immutable DB snapshot after
  each successful commit
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

The first legacy `snapshot-heavy` run confirms the same architectural target.
It uses the resident `open-sqlite-conn` compatibility path and ordinary `DB`
snapshots, so it is now a comparison harness rather than the active durable
DB-value gate. With 500
batch-1 durable writes and 500 retained old DB values, commit latency grows from
about 0.33 ms near 100 writes to about 2.07 ms near 500 writes. The explicit
post-commit `db` clone measured by the harness remains around 0.003-0.008 ms at
that scale, so the next fix is not a reportless or host-handle shortcut. The
engine needs a DB/index representation that can publish immutable snapshots
without copying and rebuilding full resident arrays on every commit.

The `sqlite-store-db-heavy` workload is the active retained durable DB-value
smoke. It writes through `open-store` / `transact-store-report`, retains a
`Store-DB` from each report, deletes the report itself, and closes all retained
DB handles at the end. A tiny July 5, 2026 smoke run:

```text
engine=vev-store workload=sqlite-store-db-heavy batch=1 total=20 columns=writes,retained_snapshots,elapsed_s,throughput_writes_per_s,call_latency_ms,commit_latency_ms,snapshot_latency_ms
10,10,0.010,1032.63,0.960,0.959,0.001
20,20,0.013,1487.43,0.372,0.372,0.000
```

This is not a meaningful performance result, but it verifies the harness and
shows that retaining the public durable handle is measured separately from the
transaction report lifetime.

The same workload now reports direct-write phase timings. A July 5, 2026
batch-1, 5000-write run shows the retained handle itself is not the bottleneck:

```text
engine=vev-store workload=sqlite-store-db-heavy batch=1 total=5000 columns=writes,retained_snapshots,elapsed_s,throughput_writes_per_s,call_latency_ms,commit_latency_ms,snapshot_latency_ms,direct_call_ms,open_before_ms,read_source_ms,resolve_ms,overlay_ms,append_ms,report_open_ms,append_roots_ms,append_root_build_ms,append_root_persist_ms,append_root_publish_ms,append_commit_ms
1000,1000,0.661,1513.63,0.656,0.656,0.001,0.648,0.130,0.000,0.001,0.001,0.508,0.001,0.180,0.168,0.154,0.011,0.310
2000,2000,1.362,1468.83,0.697,0.697,0.001,0.689,0.207,0.000,0.001,0.001,0.473,0.001,0.188,0.176,0.162,0.011,0.265
3000,3000,2.100,1428.25,0.733,0.733,0.001,0.726,0.277,0.000,0.001,0.001,0.439,0.001,0.190,0.178,0.165,0.011,0.228
4000,4000,2.946,1357.89,0.842,0.842,0.001,0.834,0.354,0.000,0.001,0.001,0.470,0.001,0.210,0.197,0.183,0.012,0.237
5000,5000,3.854,1297.42,0.901,0.901,0.001,0.894,0.409,0.000,0.001,0.001,0.475,0.001,0.219,0.207,0.193,0.012,0.233
```

`snapshot_latency_ms` stays around `0.001ms`, so retained durable DB values are
already cheap at this scale. The next durable-write bottleneck is
`open_before_ms`: every direct write still opens the current SQLite-backed
snapshot/root metadata before append, and that grows from about `0.13ms/write`
to about `0.41ms/write`. The next storage fix should introduce a safe
current-source/root metadata cache for direct writes. A naive reused snapshot is
not correct, because the next transaction must see the previous committed
datoms for uniqueness, cardinality, lookup refs, and transaction functions.

The first safe cache slice now keeps SQLite durable-log metadata on the live
connection: datom count and whether any persisted retractions exist. This avoids
re-counting the whole durable log and re-scanning for retractions before every
direct write, while still advancing the cache after successful appends and
invalidating it on rollback/legacy paths. A July 5, 2026 batch-1, 5000-write
run after that change:

```text
engine=vev-store workload=sqlite-store-db-heavy batch=1 total=5000 columns=writes,retained_snapshots,elapsed_s,throughput_writes_per_s,call_latency_ms,commit_latency_ms,snapshot_latency_ms,direct_call_ms,open_before_ms,read_source_ms,resolve_ms,overlay_ms,append_ms,report_open_ms,append_roots_ms,append_root_build_ms,append_root_persist_ms,append_root_publish_ms,append_commit_ms
1000,1000,0.606,1649.07,0.602,0.602,0.001,0.595,0.083,0.000,0.001,0.001,0.501,0.001,0.178,0.167,0.153,0.011,0.304
2000,2000,1.187,1684.63,0.577,0.577,0.001,0.570,0.080,0.000,0.001,0.001,0.481,0.001,0.180,0.169,0.156,0.011,0.282
3000,3000,1.722,1741.90,0.530,0.529,0.001,0.522,0.080,0.000,0.001,0.001,0.433,0.001,0.185,0.174,0.161,0.010,0.228
4000,4000,2.268,1763.54,0.543,0.543,0.001,0.536,0.083,0.000,0.001,0.001,0.445,0.001,0.198,0.186,0.173,0.011,0.225
5000,5000,2.837,1762.28,0.563,0.562,0.001,0.555,0.084,0.000,0.001,0.001,0.463,0.001,0.212,0.200,0.187,0.012,0.228
```

This turns the visible `open_before_ms` growth into a flat cost around
`0.08ms/write` and improves total throughput from about `1297/s` to about
`1762/s` on this local run. The next small root-publication cleanup moved the
`vev_index_roots` insert into the same prepared-statement bundle as chunk and
edge writes. A follow-up run measured `append_root_publish_ms` around
`0.007-0.008ms/write`, down from about `0.011-0.012ms/write`; total throughput
was about `1748/s`, essentially within local run noise. The remaining
durable-write target is therefore root build/persist work, especially
`append_root_persist_ms`, not retained handle creation or root-row publication.

The next storage slice moved run-manifest publication onto prepared statements
and builds manifest attr ranges directly from the just-appended datoms when the
commit already has those datoms in memory. A July 5, 2026 batch-1, 5000-write
run after that change:

```text
engine=vev-store workload=sqlite-store-db-heavy batch=1 total=5000 columns=writes,retained_snapshots,elapsed_s,throughput_writes_per_s,call_latency_ms,commit_latency_ms,snapshot_latency_ms,direct_call_ms,open_before_ms,read_source_ms,resolve_ms,overlay_ms,append_ms,report_open_ms,append_roots_ms,append_root_build_ms,append_root_persist_ms,append_root_publish_ms,append_commit_ms
1000,1000,0.548,1823.66,0.545,0.544,0.001,0.538,0.078,0.000,0.001,0.001,0.450,0.001,0.118,0.109,0.077,0.007,0.315
2000,2000,1.099,1820.26,0.547,0.546,0.000,0.540,0.079,0.000,0.001,0.001,0.452,0.001,0.128,0.118,0.087,0.008,0.306
3000,3000,1.583,1895.57,0.478,0.478,0.001,0.471,0.079,0.000,0.001,0.001,0.384,0.001,0.133,0.123,0.092,0.007,0.231
4000,4000,2.087,1916.78,0.501,0.501,0.001,0.494,0.082,0.000,0.001,0.001,0.403,0.001,0.147,0.137,0.105,0.008,0.236
5000,5000,2.605,1919.04,0.512,0.511,0.001,0.505,0.082,0.000,0.001,0.001,0.414,0.001,0.158,0.147,0.116,0.009,0.235
```

Compared with the prepared-root-only run, `append_root_persist_ms` moved from
about `0.188ms/write` to about `0.116ms/write` at 5000 writes, and local
throughput moved from about `1748/s` to about `1919/s`. This is general storage
work: it removes per-commit SQLite prepare/finalize churn and avoids reloading
datoms that the commit path already owns. Empty per-index appended segments now
also return the existing persisted root directly, which is architectural
groundwork for future per-index delta pruning. It is intentionally not claimed
as a measured win for this workload, because the current workload still appends
datoms to all indexes.

After empty-delta root-set publication was routed through the same helper in
both source append paths, the retained durable DB-value workload remains in the
same range. A local 5000-write run measured about `1952 writes/s`,
`open_before_ms=0.080ms/write`, `append_root_persist_ms=0.096ms/write`, and
`snapshot_latency_ms=0.001ms/write`. The important conclusion is unchanged:
retained DB handles are cheap; the next real storage target is the
append/root-build and persisted-delta representation for ordinary small writes.

Routing append-mode SQLite-cursor writes through the same run-manifest delta
publication shape makes the ordinary source-backed small-write representation
consistent: old root plus small persisted run. A local 5000-write retained
durable DB-value run measured about `2167 writes/s`,
`open_before_ms=0.081ms/write`, `append_root_persist_ms=0.067ms/write`, and
`snapshot_latency_ms=0.001ms/write`. This is general storage work, not a
benchmark-specific fast path: append-mode and non-append source-backed writes
now publish through the same manifest/delta boundary.

After moving both append root publication paths to build the four ordered
appended delta views once at the root-set boundary, the same 5000-write run
measured about `2203 writes/s`, `open_before_ms=0.080ms/write`,
`append_root_build_ms=0.098ms/write`,
`append_root_persist_ms=0.067ms/write`, and
`snapshot_latency_ms=0.001ms/write`. The value of this slice is structural:
the small-write path now has one explicit ordered-delta handoff into per-index
publication, which is the right boundary for the next persisted segment/root
work.

After making small append-manifest delta runs entry-backed leaf chunks, the
same gate remains essentially flat while the representation becomes more
production-shaped:

```text
engine=vev-store workload=sqlite-store-db-heavy batch=1 total=5000 columns=writes,retained_snapshots,elapsed_s,throughput_writes_per_s,call_latency_ms,commit_latency_ms,snapshot_latency_ms,direct_call_ms,open_before_ms,read_source_ms,resolve_ms,overlay_ms,append_ms,report_open_ms,append_roots_ms,append_root_build_ms,append_root_persist_ms,append_root_publish_ms,append_commit_ms
5000,5000,2.276,2196.59,0.451,0.450,0.001,0.444,0.080,0.000,0.001,0.001,0.355,0.001,0.100,0.097,0.066,0.002,0.236
```

The important point is not the small throughput difference. Small manifest
deltas now persist ordered log indexes in `vev_index_chunk_entries` behind a
leaf `:entries` marker, while existing payload chunks still read normally.
That gives the storage layer a real first-class small-delta segment shape to
reuse for multi-leaf entry-backed trees and future page-level storage.

After extending the same representation to multi-leaf deltas, larger delta
runs build ordinary parent chunks over entry-backed leaves and the page readers
handle mixed old payload leaves and new entry-backed leaves:

```text
engine=vev-store workload=sqlite-store-db-heavy batch=1 total=5000 columns=writes,retained_snapshots,elapsed_s,throughput_writes_per_s,call_latency_ms,commit_latency_ms,snapshot_latency_ms,direct_call_ms,open_before_ms,read_source_ms,resolve_ms,overlay_ms,append_ms,report_open_ms,append_roots_ms,append_root_build_ms,append_root_persist_ms,append_root_publish_ms,append_commit_ms
5000,5000,2.342,2135.15,0.464,0.463,0.001,0.457,0.080,0.000,0.001,0.001,0.368,0.001,0.101,0.097,0.067,0.002,0.247
```

The slight throughput movement is within the current storage-work noise. The
structural improvement is that entry-backed deltas are no longer limited to a
single leaf, which keeps the same persisted segment shape for both tiny and
moderate commits.

The `pure-store-report` row is the active public durable API measurement. A
small July 2, 2026 batch-1 run shows it is semantically on the source-backed
path and no longer rewrites old logical index chunks for common repeated-attr
writes. Normal writes share old ordered roots, publish bounded merge roots, and
compact recent merge runs incrementally. Merge-root compaction now uses
page-cached foreground merging and a small tiered scheduler: four equal-sized
trailing runs are compacted together, while uneven roots are allowed only one
extra direct run before falling back to compaction. Leaf chunks now keep their
entries in the chunk payload and parent page reads select overlapping leaf
payloads directly, so normal writes no longer duplicate every leaf entry into
`vev_index_chunk_entries`. The report output now includes:

- `pending_maintenance`: persisted SQLite maintenance queue depth
- `max_merge_runs`: largest latest-root merge-run count across EAVT/AEVT/AVET/VAET
- `eavt_root_level`, `eavt_root_children`, `eavt_tree_nodes`, and
  `eavt_tree_leaves`: the latest EAVT persisted-tree shape
- direct-write phase timings: `open_before_ms`, `read_source_ms`,
  `resolve_ms`, `overlay_ms`, `append_ms`, `append_begin_ms`,
  `append_tx_ms`, `append_datoms_ms`, `append_meta_ms`,
  `append_roots_ms`, `append_root_build_ms`, `append_root_publish_ms`,
  `append_commit_ms`, and `report_open_ms`
- outer write timings: `direct_call_ms`, `direct_inner_unaccounted_ms`,
  `auto_maintenance_ms`, `listener_ms`, and `outer_unaccounted_ms`

Current batch-1 smoke after SQLite write-state metadata caching, direct
attr-schema caching, payload-backed packed leaf chunks, lazy
`Store-Tx-Report` DB handles, cursor-root metadata reuse, prepared chunk/edge
insert statements, persisted chunk `child_count` metadata, bulk edge-copy
statements, and right-spine segmented append roots for ordinary non-schema
new-entity writes:

```text
auto_maintain_steps=32 auto_maintain_target=0 auto_maintain_trigger=16
columns=writes,elapsed_s,throughput_writes_per_s,call_latency_ms,commit_latency_ms,maintenance_latency_ms,maintenance_runs,pending_maintenance,max_merge_runs,eavt_root_level,eavt_root_children,eavt_tree_nodes,eavt_tree_leaves,outer_unaccounted_ms,direct_call_ms,direct_inner_unaccounted_ms,auto_maintenance_ms,listener_ms,open_before_ms,read_source_ms,resolve_ms,overlay_ms,append_ms,append_begin_ms,append_tx_ms,append_datoms_ms,append_meta_ms,append_roots_ms,append_root_build_ms,append_root_publish_ms,append_commit_ms,report_open_ms
250,0.370,675.68,1.476,1.476,0.000,0,13,5,1,4,5,4,0.000,0.949,0.006,0.527,0.000,0.072,0.000,0.005,0.005,0.860,0.002,0.002,0.012,0.000,0.706,0.632,0.073,0.137,0.001
500,0.875,571.29,2.017,2.017,0.000,0,0,3,1,8,9,8,0.000,0.985,0.006,1.032,0.000,0.074,0.000,0.001,0.001,0.902,0.002,0.002,0.014,0.000,0.720,0.647,0.072,0.164,0.001
750,1.450,517.26,2.295,2.295,0.000,0,2,4,1,12,13,12,0.000,0.941,0.006,1.353,0.000,0.076,0.000,0.001,0.001,0.856,0.003,0.002,0.014,0.000,0.733,0.659,0.073,0.104,0.001
1000,2.129,469.74,2.712,2.712,0.000,0,4,5,1,16,17,16,0.000,0.931,0.007,1.780,0.000,0.077,0.000,0.001,0.001,0.845,0.002,0.003,0.014,0.000,0.741,0.668,0.072,0.084,0.001
```

For context, immediately before the direct attr-schema cache, the same
auto-maintained 200-write run was about `103.539ms` per commit and the phase
profile showed `overlay_ms=100.786`. After the cache, the 200-write
auto-maintained row is about `2.750ms` per commit with `overlay_ms=0.001`.
The remaining visible costs are now more specific. Report snapshot opening has
moved off the commit path: report handles are lazy durable DB values and
`report_open_ms` is effectively zero until a caller actually reads one of
those DB values. Source-root publication now reuses cursor root metadata, and
the direct durable root build reuses prepared chunk/edge insert statements
across all four index roots. New index chunk rows also persist `child_count`,
so append-root flattening can use root metadata directly and older migrated
parent rows fall back to edge counting only when needed. Small appends now
fill the rightmost leaf chunk up to the configured leaf chunk size before
appending a new leaf. After a leaf is full, Vev appends into the right-spine
segmented tree and increases the root level only when the whole root is full.
Storage architecture tests assert this path crosses fanout while staying
query-correct. A 5000-write run validates the shape:

```text
1000,1.921,520.58,1.918,1.918,0.000,0,4,5,1,16,17,16,0.000,0.720,0.006,1.198,0.000,0.072,0.000,0.002,0.002,0.638,0.002,0.002,0.013,0.000,0.500,0.475,0.023,0.121,0.001
2000,5.649,354.04,3.725,3.725,0.000,0,12,6,1,32,33,32,0.000,1.517,0.006,2.208,0.000,0.083,0.000,0.001,0.001,1.425,0.002,0.003,0.015,0.000,1.311,1.280,0.030,0.094,0.001
3000,11.093,270.45,5.440,5.440,0.000,0,6,9,1,47,48,47,0.000,1.541,0.006,3.899,0.000,0.088,0.000,0.001,0.001,1.443,0.002,0.003,0.018,0.000,1.322,1.288,0.034,0.097,0.001
4000,17.195,232.63,6.098,6.098,0.000,0,0,10,1,63,64,63,0.000,1.374,0.006,4.724,0.000,0.091,0.000,0.001,0.001,1.273,0.002,0.003,0.019,0.000,1.150,1.113,0.036,0.099,0.001
5000,21.977,227.51,4.937,4.937,0.000,0,12,12,2,2,82,79,0.000,1.923,0.006,3.013,0.000,0.094,0.000,0.001,0.001,1.820,0.002,0.003,0.019,0.000,1.689,1.651,0.037,0.106,0.001
```

Index root work inside append remains the dominant cost. In the 1000-write
run, `append_roots_ms` is about `0.76ms/write`, with most of that in
root/chunk construction (`append_root_build_ms`, about `0.69ms/write`), not
latest-root publication or maintenance enqueue (`append_root_publish_ms`, about
`0.07ms/write`). SQLite commit is about `0.09ms/write` at the 1000-write row.
The 5000-write smoke now splits the previous unaccounted time. The packed-leaf
tree shape is good, `outer_unaccounted_ms` is effectively zero, and
`listener_ms` / `report_open_ms` are negligible. Automatic post-commit
maintenance now uses a bounded foreground row budget. Non-inline merge
publication now nests the previous merge root plus the new run instead of
flattening and republishing every retained run on each write. Merge roots also
persist flattened run-count metadata in the chunk checksum field during parent
chunk insertion, so normal compaction scheduling and non-inline publication can
decide from local metadata without an extra metadata update or recursive
flattening of retained merge roots. Together those changes lower the
5000-write row from the earlier `10.936ms/write` to `4.937ms/write`, with
`auto_maintenance_ms` at `3.013ms/write` and direct root/chunk construction at
`1.923ms/write`. The tradeoff is explicit: retained merge runs remain visible
(`max_merge_runs=12`), and actual inline compaction still has to flatten run
roots because the merge needs the child roots. The next storage batch should
move repeated tiny commits toward a page-delta/LSM representation with less
root/chunk publication work rather than chasing report wrapper overhead.

The `pure-store-report-deferred` workload separates commit-path cost from
background maintenance. It writes through the same public `Store-Tx-Report`
path, disables automatic foreground maintenance, then reopens the store and
drains the maintenance queue explicitly. A July 3, 2026 5000-write batch-1 run
after chained non-append run manifests shows the current split:

```text
engine=vev-store workload=pure-store-report batch=1 total=5000 compact_every=0 maintain_every=0 maintain_steps=1 maintain_target=-1 auto_maintain_steps=0 auto_maintain_target=0 auto_maintain_trigger=-1 columns=writes,elapsed_s,throughput_writes_per_s,call_latency_ms,commit_latency_ms,maintenance_latency_ms,maintenance_runs,pending_maintenance,max_merge_runs,eavt_root_level,eavt_root_children,eavt_tree_nodes,eavt_tree_leaves,outer_unaccounted_ms,direct_call_ms,direct_inner_unaccounted_ms,auto_maintenance_ms,listener_ms,open_before_ms,read_source_ms,resolve_ms,overlay_ms,append_ms,append_begin_ms,append_tx_ms,append_datoms_ms,append_meta_ms,append_roots_ms,append_root_build_ms,append_root_sort_ms,append_root_check_ms,append_root_persist_ms,append_eavt_root_build_ms,append_aevt_root_build_ms,append_avet_root_build_ms,append_vaet_root_build_ms,append_root_publish_ms,append_commit_ms,report_open_ms
1000,0.482,2073.41,0.480,0.480,0.000,0,0,0,1,16,17,16,0.000,0.475,0.004,0.005,0.000,0.075,0.000,0.002,0.002,0.393,0.002,0.002,0.011,0.000,0.086,0.077,0.001,0.000,0.065,0.029,0.014,0.012,0.012,0.009,0.291,0.000
2000,0.974,2053.39,0.489,0.489,0.000,0,0,0,1,32,33,32,0.000,0.484,0.004,0.005,0.000,0.079,0.000,0.001,0.001,0.399,0.002,0.002,0.011,0.000,0.095,0.085,0.001,0.000,0.073,0.036,0.014,0.013,0.012,0.009,0.289,0.001
3000,1.397,2147.12,0.421,0.421,0.000,0,0,0,1,47,48,47,0.000,0.416,0.004,0.005,0.000,0.083,0.000,0.001,0.001,0.327,0.002,0.002,0.013,0.000,0.101,0.091,0.001,0.000,0.079,0.042,0.014,0.013,0.012,0.009,0.208,0.000
4000,1.841,2172.72,0.441,0.441,0.000,0,0,0,1,63,64,63,0.000,0.436,0.004,0.005,0.000,0.089,0.000,0.001,0.001,0.342,0.002,0.002,0.014,0.000,0.111,0.101,0.001,0.000,0.089,0.050,0.015,0.013,0.012,0.009,0.212,0.000
5000,2.296,2177.32,0.453,0.453,0.000,0,0,0,2,2,82,79,0.000,0.448,0.004,0.005,0.000,0.093,0.000,0.001,0.001,0.349,0.002,0.002,0.015,0.000,0.125,0.114,0.001,0.000,0.102,0.061,0.016,0.014,0.013,0.009,0.205,0.001
engine=vev-store workload=pure-store-report-deferred-maintenance batch=1 total=5000 maintain_steps=128 columns=pending_before,pending_after,passes,steps,elapsed_ms
0,0,0,0,0.003
```

This is the first accepted non-append storage representation slice. Before
chained manifests, the same workload was about `3.08ms/write`, with
`append_roots_ms` about `2.78ms/write`, `append_root_persist_ms` about
`2.683ms/write`, three queued maintenance tasks, and about `1.2s` of explicit
deferred drain work. With chained manifests, the 5000-write row is about
`0.453ms/write`, `append_roots_ms` is about `0.125ms/write`,
`append_root_persist_ms` is about `0.102ms/write`, and the deferred queue is
empty. Appendability checking is no longer on the hot publication path
(`append_root_check_ms=0.000`), because direct source-backed commits publish
known non-append indexes as manifest runs immediately.

The next benchmark question is read-side behavior after bounded compaction.
Chained manifests make tiny commits cheap, and automatic maintenance now keeps
the visible run count bounded. Source cursors now resume sequential
merge/manifest scans instead of replaying from zero for every page, and broad
prefix scans over manifest roots walk child runs directly. In the 1000-row
benchmark, bounded manifest broad reads are now roughly equal to compacted-root
broad reads. Before calling this production-shaped, Vev needs range-aware
child-run scans for selective prefixes plus 5k/20k query/reopen measurements.

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
