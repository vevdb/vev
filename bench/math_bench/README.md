# Math Bench Adapter

This adapter ports Datalevin's `benchmarks/math-bench` workload to Vev. It is
intended for rule-engine validation after the DataScript read benchmark and the
MusicBrainz/Datomic work.

The upstream benchmark uses the Mathematics Genealogy Project dataset and four
rule-heavy queries:

- `q1`: grand-advisors of David Scott Warren.
- `q2`: candidates who got degrees from the same university as their advisor.
- `q3`: candidates who worked in a different area than at least one advisor.
- `q4`: academic ancestors of David Scott Warren through recursive `anc`.

Export the upstream JSON data into Vev EDN transaction chunks:

```sh
bench/math_bench/run_export.sh
```

Run one selected Vev workload:

```sh
bench/math_bench/run_vev.sh q1
bench/math_bench/run_vev.sh q4
```

Run all four:

```sh
bench/math_bench/run_vev.sh
```

The exporter mirrors Datalevin's mapping:

- person ids are the original math genealogy ids
- dissertation ids start at `1000001`
- `:person/advised` points from advisor person to dissertation entity
- `author`, `adv`, `univ`, `area`, and `anc` are supplied as query rules

This is deliberately separate from MusicBrainz. MusicBrainz tests Datomic-style
host usage and realistic pull/query API shape; math-bench stresses Datalog rule
planning, non-recursive rule expansion, and recursive ancestry.
