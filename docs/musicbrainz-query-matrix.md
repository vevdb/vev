# MusicBrainz Query Matrix

This matrix tracks Vev coverage against the MusicBrainz parts of the Datomic
sample material:

- `Datomic/day-of-datomic-conj/src/music_brainz.clj`
- `Datomic/day-of-datomic/tutorial/query.clj`
- `Datomic/mbrainz-sample/schema.edn`
- `Datomic/mbrainz-sample/resources/rules.edn`

The goal is tutorial compatibility first: a Datomic user should be able to read
the workshop query and write the same query against Vev's EDN text API, with
only host wrapper syntax changed.

## Current Harness

`src/vev_tests/musicbrainz_test.kvist` is the active mini workload. It runs the
same assertions against:

- an in-memory connection
- a SQLite-backed connection after commit, close, reopen, and query

The mini dataset includes countries, artist enum idents, artists, releases,
media, and tracks. It intentionally uses the same core attr names as
`mbrainz-sample`, including:

- `:artist/name`, `:artist/gid`, `:artist/type`, `:artist/gender`,
  `:artist/country`, `:artist/startYear`, `:artist/startMonth`,
  `:artist/startDay`
- `:release/name`, `:release/year`, `:release/artists`, `:release/country`,
  `:release/media`, `:release/artistCredit`
- `:medium/tracks`, `:medium/trackCount`, `:medium/position`, `:medium/name`
- `:track/name`, `:track/duration`, `:track/artists`, `:track/position`

## Covered

These workshop shapes are covered by passing Vev tests:

| Shape | Upstream example | Vev coverage |
| --- | --- | --- |
| Basic release-by-artist join | `query.clj`, `music_brainz.clj` | `q-text` |
| Reverse refs | mbrainz-style release artists | `:release/_artists` |
| Prepared scalar input | `:in $ ?artist-name` | `prepare-query-text` plus EDN input text |
| Collection binding | `:in $ [?artist-name ...]` | `query-input-collection` |
| Tuple binding | `:in $ [?artist-name ?release-name]` | `query-input-collection` tuple value |
| Relation binding | `:in $ [[?artist-name ?release-name]]` | `query-input-relation` |
| Relation find spec | `:find ?artist-name ?release-name` | `q-text` result rows |
| Collection find spec | `:find [?release-name ...]` | `q-text-collection` |
| Tuple find spec | `:find [?year ?month ?day]` | `q-text-tuple` |
| Scalar find spec | `:find ?year .` | `q-text-scalar` |
| Return maps | `:keys artist release` | `q-text-keys` |
| Predicate expressions | `[(< ?year 1970)]`, `[(> ?duration ...)]` | query predicates |
| Function expressions | `[(quot ?millis 60000) ?minutes]` | query functions |
| `get-else` | workshop query examples | EDN text query |
| Enum refs through `:db/ident` | artist type/gender query | ident entity joins |
| Aggregates | min/max, sum, count/count-distinct | EDN text aggregate queries |
| Statistics aggregates | median, avg, stddev by release year | EDN text aggregate query |
| Nested pull | release media and tracks | `pull-text` |
| Rule input `%` | `track-release`, `track-info`, `short-track` | `q-text-with-rules` |

## Pending Tutorial Coverage

These should be ported next using the mini fixture first, then the restored
1968-1973 sample:

| Shape | Source | Notes |
| --- | --- | --- |
| Pull all `[*]` | `music_brainz.clj` | Vev supports wildcard pull; add exact tutorial form and expected normalized shape |
| `d/query` map form with `:query`/`:args` | `music_brainz.clj` | Vev supports map query text, but needs a tutorial-level host-facing helper shape |
| Query-stats walkthrough | `music_brainz.clj` | Vev has profiling APIs; port as Vev profile assertions rather than Datomic `:query-stats` object parity |
| Clause-order performance comparison | `music_brainz.clj` | Should become a benchmark/profiling fixture, not only a correctness test |
| Full `track-artist` + `track-release` + composed `track-info` rules | `music_brainz.clj` | Current mini rules cover equivalent semantics with one combined rule set; add exact split/composed rule shape |
| `not`, `not-join`, `or`, `or-join` mbrainz examples | original `query.clj` | Core engine supports these; add mbrainz-shaped examples |
| `get-some` | original `query.clj` | Core engine supports this; add country/artist lookup style case |
| Lookup-ref inputs | original `query.clj` country examples | Covered elsewhere; add mbrainz-specific `[:country/name "..."]` query |
| Dynamic attr input | original `query.clj` | Supported in core tests; add mbrainz-specific property/reference examples |
| Top-n aggregates | original `query.clj` | Core aggregate support exists; add duration top-n form |

## Host Or Datomic-Specific Later

These are not current blockers for the Vev engine:

| Shape | Reason |
| --- | --- |
| Clojure destructuring of return-map rows | Host wrapper ergonomics, not engine semantics |
| Java `HashSet` result identity/rendering | Host API shape |
| Datomic peer cache/io-stats details | Datomic implementation-specific |
| Datomic log APIs: `d/log`, `tx-ids`, `tx-data` | Future time/log/durability phase |
| Fulltext query `(fulltext $ :attr q)` | Requires a fulltext index design |
| Arbitrary JVM calls such as `System/getProperties` or method calls | Host/JVM-specific |
| Datomic timeout object shape | Host/API behavior; Vev can expose a native timeout later |

## Next Batch

1. Port pending tutorial forms that the mini dataset can already support:
   wildcard pull, map query form, split/composed rules, `not`/`or`, `get-some`,
   lookup refs, dynamic attrs, and top-n aggregates.
2. Add a small benchmark/profile fixture for the clause-order examples once
   correctness forms are stable.
3. Restore the 1968-1973 mbrainz sample into local Datomic and record the exact
   URI/path in `docs/musicbrainz.md`.
4. Build the Vev importer for the restored sample and run the matrix against
   both Datomic and Vev.
