# Ro-schema transaction resolution

Date: 2026-10-01

## Question

Why did a small resident Vev database using Ro's production schema take about
376 ms to add one Item and its operation receipt, while Vev's small synthetic
transaction benchmarks remained below a millisecond?

This investigation changes Vev's transaction resolver only. It does not move
Ro to direct SQLite or introduce a second source of truth.

## Reproduction

The source database is a disposable Ro authority containing one Workspace,
one Owner Actor, 23 Items, 15 Comments, and Ro's complete bootstrap schema.
The measured transaction adds two tempid entities:

- an operation receipt with attribution, authority, result, and provenance;
- one Task Item with Workspace, Actor, and receipt references.

After map expansion it resolves 58 operations and commits 59 effective datoms.
The connection is resident before measurement. The executable and native
library are built with Odin `-o:speed` on Apple arm64, macOS 15.5.

The Ro-side end-to-end matrix is:

```sh
./scripts/benchmark-read-path-storage.sh
```

For phase-level work against a retained disposable copy of that database:

```sh
kvist compile bench/ro_transaction_profile.kvist \
  -o build/bench/ro-transaction-profile.odin
odin build build/bench/ro-transaction-profile.odin \
  -file -o:speed -out:build/bench/ro-transaction-profile
build/bench/ro-transaction-profile /path/to/disposable-ro.vev \
  --samples 20 --start 1000
```

`--start` supplies a fresh numeric suffix so repeated diagnostic runs do not
reuse the benchmark's unique identities. Never point this benchmark at a user
database.

## Hypotheses and evidence

1. **SQLite commit or canonical append dominates.** Rejected. The original
   profiled transaction spent about 3 ms in canonical append and zero measured
   time in the outer SQLite commit phase.
2. **Kvist/ABI conversion or report materialization dominates.** Rejected.
   Input copy was about 1 microsecond; report handle and materialization were
   sub-millisecond.
3. **The source resolver repeatedly scans persisted tuple schema.** Confirmed.
   Ro has 22 tuple schemas. With `has-tuple-schema` true, tempid upsert,
   ordinary value resolution, and derived tuple maintenance rediscovered
   persisted tuple definitions for nearly every input attribute and generated
   operation.
4. **A regression exists between Ro's locked Vev bundle and current Vev
   `main`.** Rejected for the engine. The only source differences were Kvist
   facade ownership/build changes; the transaction implementation was the
   same.

Original single-sample phase profile:

| Phase | Time |
| --- | ---: |
| total native engine | 737 ms |
| resolution | 732 ms |
| tempid preparation | 249 ms |
| operation generation | 477 ms |
| planning | 0.68 ms |
| canonical append | 3.0 ms |

The phase-instrumented run is slower than the uninstrumented 376 ms median,
but it identifies where time scales; it is not used as the product latency
number.

## Change

Each source-backed transaction now loads the persisted tuple schema inventory
once into its existing resolve index. The resolver then:

- answers whether an attribute is a tuple value or tuple component from that
  inventory;
- reuses the loaded schemas for positive component matches;
- skips tuple-upsert probes when a component is an already-probed, absent
  scalar identity, because no persisted tuple containing that globally unique
  value can exist;
- caches the result of small unique-identity point probes for the rest of the
  transaction.

The optimization is transaction-local. Schema changes in a later transaction
are observed by its fresh inventory, and same-transaction schema declarations
continue through the existing declared-schema path.

## Result

Same-shape Ro matrix, before and after:

| Path | Before median / p95 | After median / p95 |
| --- | ---: | ---: |
| raw two-datom Vev write | 19.963 / 21.225 ms | 1.478 / 1.957 ms |
| raw Item + receipt | 375.865 / 384.344 ms | 8.001 / 10.232 ms |
| Ro semantic Item creation | 446.190 / 460.015 ms | 18.329 / 20.409 ms |

In a 20-sample phase run after the change, the raw Item-plus-receipt median was
9.460 ms and p95 was 22.320 ms. Average resolution fell to 4.023 ms:
tempid preparation 3.734 ms, operation generation 0.192 ms, and lookup refs
0.050 ms. The remaining variance is primarily the one tuple-identity probe
which cannot be skipped safely, plus canonical append/checkpoint work—not
repeated discovery of the complete tuple schema.

The focused `index_storage_test.kvist` suite has regression cases proving both
that an absent scalar identity creates a distinct entity when it participates
in the relevant tuple, and that another tuple which does not contain that
identity is still checked. Existing tuple upsert, conflict, lookup-ref,
same-transaction, and schema-transition tests remain green.

## Separate toolchain findings

Two build issues were observed while reproducing this work; neither explains
the runtime latency:

- current Kvist `main` segfaults while compiling `src/vev_abi/vev_abi.kvist`;
  Vev's pinned Kvist revision compiles it;
- Homebrew Odin `dev-2026-09:a2fb372b7` passes the Darwin initializer as the
  literal linker argument `-Wl,-init,'__odin_entry_point'` without a shell,
  causing an undefined symbol containing quote characters. A manual relink of
  the retained DLL object with `-Wl,-init,__odin_entry_point` succeeds.

These need their own compiler/toolchain regression fixes. They should not be
worked around in Ro's product architecture.
