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

The restored Datomic 1968-1973 sample is also available locally through
`scripts/musicbrainz_sample.sh`. The current real-data bridge exports
Vev-compatible EDN from Datomic with compact remapped entity ids and UUID
values preserved as UUID literals. `bench/musicbrainz_import_subset.kvist`
imports either a single tx file or staged schema/value tx files.

Real-data import status:

| Workload | Status | Notes |
| --- | --- | --- |
| 100-item single-file subset | Passing | Confirms compact eid remap and UUID/value parsing |
| 500-value staged subset | Passing | Confirms schema-first/value-second import path |
| 5,000-value staged subset | Passing | Bulk explicit-id transaction path is practical; latest local run is about 0.55s total |
| 50k/100k/200k/400k staged subsets | Passing | Latest 400k local run is about 6.9s total |
| Full 763,274-item subset | Partial | Full value tx parses/resolves quickly; one huge prepared tx and repeated chunk commits expose the next DB/index publication bottleneck |

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
| Pull all `[*]` | `music_brainz.clj` | wildcard `pull-text` |
| Rule input `%` | `track-release`, `track-info`, `short-track` | `q-text-with-rules` |
| `d/query` map query form | `music_brainz.clj` | EDN map-form query text |
| Split/composed rules | `music_brainz.clj` `track-artist`/`track-release`/`track-info` | `q-text-with-rules` |
| `not` and `not-join` | original `query.clj` | mbrainz-shaped EDN text queries |
| `or` and `or-join` | original `query.clj` | mbrainz-shaped EDN text queries |
| `get-some` | original `query.clj` | country/artist attr query |
| Lookup-ref inputs | original `query.clj` country examples | inline lookup-ref and query input lookup-ref |
| Dynamic attr input | original `query.clj` | `:artist/country` as collection input |
| Top-n aggregates | original `query.clj` | min/max duration vectors |
| Query profiling | `music_brainz.clj` query-stats walkthrough | Vev profile assertions on tutorial-shaped joins |
| Clause-order profiling | `music_brainz.clj` comparison examples | `bench/musicbrainz_query_profile.kvist` |

## Pending Tutorial Coverage

These should be ported next using the mini fixture first, then the restored
1968-1973 sample:

| Shape | Source | Notes |
| --- | --- | --- |
| Host-facing `d/query` equivalent with `:query`/`:args` | `music_brainz.clj` | Engine accepts map-form text; higher-level Clojure/Java wrapper helper still needs Datomic-shaped ergonomics |

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

1. Fix full-import DB/index publication: avoid retaining one huge prepared
   transaction, and avoid repeated whole-index merge/copy work for chunked
   imports. The likely direction is a bulk index builder or shared/chunked
   immutable index storage.
2. Run the existing matrix against the imported real subset and local Datomic,
   comparing result sets before timing.
3. Add Datomic-shaped `d/query` wrapper ergonomics in the host adapters where
   useful, backed by the existing EDN map-query engine path.
