# Native ABI index maintenance

Date: 2026-10-01

## Finding

Durable writes through Vev's native ABI did not run the bounded automatic
index maintenance used by the in-process `Store-Conn` facade. The ABI owns a
`SQLite-Conn` directly and called the lower-level durable transaction
functions, bypassing `run-store-auto-maintenance!` after every successful
transaction.

This matters for applications such as Ro which use the packaged native
library through the Kvist client. Each small transaction could remain as a
separate immutable run even though no explicit maintenance queue row was
pending. Query work then grew with database age rather than current data size.

## Reproduction

A development Ro Workspace with approximately 30 Items and 167 transactions
had 11,301 canonical datoms and 165 runs in every current Vev index. A direct
warm Vev query for the current Item IDs and titles took approximately 1.92
seconds.

The source database remained open only through Ro. Investigation used a
disposable SQLite online backup of the Vev file:

```text
before maintenance: 165 runs per index
warm direct query:   1.92 s
maintenance:         216 steps total
after maintenance:  4 runs per public index
warm direct query:   0.31 s
```

The disposable database temporarily grew from about 14 MB to 220 MB while
retaining newly compacted and obsolete derived index artifacts. Explicit
index reclaim then reduced it to about 13 MB. Canonical datoms and transaction
history were unchanged.

These measurements include process startup and query parsing and are not a
general Vev benchmark. They demonstrate the run-fanout regression and the
effect of maintenance on the same database shape.

## Change

The native ABI now invokes the same default automatic-maintenance policy after
successful durable transactions as the `Store-Conn` facade:

- trigger when the explicit maintenance queue or current index run fanout
  reaches the configured threshold;
- perform at most four foreground steps;
- compact at most 512 rows per step;
- target four pending units;
- prune obsolete derived index artifacts after successful maintenance;
- invalidate the current-root cache when maintenance changes the published
  root.

Maintenance remains derived and best effort. Failure never changes an already
durable transaction result, but a stale root cache is never retained.

The binary Kvist package smoke now performs 24 successive small durable writes
through the native ABI and requires EAVT, AEVT, AVET, and VAET run counts to
remain below the automatic-maintenance trigger. This fails on the previous ABI
path, where run count grows once per transaction.

## Tradeoffs and existing databases

Automatic maintenance adds bounded periodic work to the durable write path.
That is intentional: small predictable write work prevents unbounded read
amplification. Work remains capped by step and row budgets rather than turning
an ordinary write into full compaction.

The change prevents new accumulation. It does not synchronously drain a large
pre-existing backlog on open, because doing so would make startup latency and
temporary disk use unbounded. An existing affected database should receive a
separately controlled `maintain-indexes` catch-up followed by `reclaim-indexes`
when operationally appropriate. Back up the database first and do not run a
second writer concurrently.

