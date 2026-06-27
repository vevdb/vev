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
`bench/musicbrainz_query_profile.kvist` can now run either the deterministic
mini fixture or the imported real subset.

Real-data import status:

| Workload | Status | Notes |
| --- | --- | --- |
| 100-item single-file subset | Passing | Confirms compact eid remap and UUID/value parsing |
| 500-value staged subset | Passing | Confirms schema-first/value-second import path |
| 5,000-value staged subset | Passing | Bulk explicit-id transaction path is practical; latest local run is about 0.55s total |
| 50k/100k/200k/400k staged subsets | Passing | Latest 400k local run is about 6.9s total |
| Full 763,274-item subset | Passing | Chunked staged import preserves expected tutorial rows; latest local import is about 16.5s |

Real-data query comparison against local Datomic is active for the first
tutorial-shaped batches:

| Workload | Vev rows/fingerprint | Datomic rows/fingerprint | Status | Current signal |
| --- | --- | --- | --- | --- |
| `musicbrainz-real-release-first` | `96 / 0ea8943f9ef3eb03` | `96 / 0ea8943f9ef3eb03` | Equal rows | Dependency-aware clause planning now keeps this selective and fast |
| `musicbrainz-real-track-first` | `89 / 9902d35f51335e40` | `89 / 9902d35f51335e40` | Equal rows | Clause order no longer creates the large track/release cross product |
| `musicbrainz-real-beatles-releases` | `16 / c57b012eecfd45ed` | `16 / c57b012eecfd45ed` | Equal rows | Constant artist lookup plus release join is fast in Vev |
| `musicbrainz-real-beatles-track-count` | `1 / 0000000007068a26` | `1 / 0000000007068a26` | Equal rows | Bounded aggregate over real imported data |
| `musicbrainz-real-beatles-min-max-duration` | `1 / 9c45e54f061af2f6` | `1 / 9c45e54f061af2f6` | Equal rows | Bounded min/max aggregate over real imported data |
| `musicbrainz-real-lookup-country` | `1 / 4167e0bf9abd1220` | `1 / 4167e0bf9abd1220` | Equal rows | Vev uses inline lookup-ref syntax; Datomic side uses equivalent entity pattern |
| `musicbrainz-real-selected-artists-releases` | `28 / 4887ecaa409643d2` | `28 / 4887ecaa409643d2` | Equal rows | Collection input binding over two artist names |
| `musicbrainz-real-not-beatles-male` | `1 / ea45bdc7e8b8201b` | `1 / ea45bdc7e8b8201b` | Equal rows | Bounded `not` query |
| `musicbrainz-real-or-two-artists` | `2 / de67eb0f77cf6b42` | `2 / de67eb0f77cf6b42` | Equal rows | Bounded `or` query |
| `musicbrainz-real-relation-artist-release` | `2 / cb2f30e6783d093d` | `2 / cb2f30e6783d093d` | Equal rows | Relation tuple input for artist/release pairs |
| `musicbrainz-real-not-join-release` | `1 / b6368059dfc36ef8` | `1 / b6368059dfc36ef8` | Equal rows | Bounded `not-join` over selected releases |
| `musicbrainz-real-or-join-release` | `2 / 5f5db031e99d9c11` | `2 / 5f5db031e99d9c11` | Equal rows | Bounded `or-join` over selected releases |
| `musicbrainz-real-map-beatles-releases` | `16 / c57b012eecfd45ed` | `16 / c57b012eecfd45ed` | Equal rows | Vev uses EDN map query text; Datomic harness uses the equivalent vector query |
| `musicbrainz-real-rule-track-info` | `90 / 5f20ceb057e27418` | `90 / 5f20ceb057e27418` | Equal rows | Pure rule-body planner keeps the composed track/release join selective |
| `musicbrainz-real-pull-release` | `5 / 974ce160e8be7539` | `5 / 974ce160e8be7539` | Equal rows | Pull expression in query result over selected release names |
| `musicbrainz-real-direct-pull-artist` | `1 / 0a11a6da90ea3115` | `1 / 0a11a6da90ea3115` | Equal rows | Direct pull by `:artist/gid` lookup ref |
| `musicbrainz-real-direct-pull-many-artists` | `2 / 3b0d165020d81f40` | `2 / 3b0d165020d81f40` | Equal rows | Direct pull-many by `:artist/gid` lookup refs |

The row fingerprints are generated from sorted projected EDN-ish row keys. Pull
maps are rendered into canonical value text before fingerprinting so Datomic map
iteration order does not affect comparisons. Both initial clause-order queries
have also been checked with explicit sorted row dumps and `diff`.

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
| Host-facing `d/query` equivalent with `:query`/`:args` | `music_brainz.clj` | Clojure `vev/query` and Java `Vev.queryRows(Map.of(...))` request-map wrappers |
| Clojure return-map rows | `music_brainz.clj` | Clojure `q`/`rows`/`query` return maps for `:keys`, `:strs`, and `:syms` on plain query forms |
| Java return-map rows | `music_brainz.clj` | Java `Vev.queryMaps(Map.of(...))` return maps for `:keys`, `:strs`, and `:syms` query text |

## Pending Tutorial Coverage

These should be ported next using the mini fixture first, then the restored
1968-1973 sample:

| Shape | Source | Notes |
| --- | --- | --- |
| Additional Day-of-Datomic host snippets | `music_brainz.clj` | Keep porting examples that exercise host presentation rather than engine syntax |

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

1. Take on the remaining real-data planner gaps: bounded `or`/`or-join` and
   bounded `not`/`not-join`.
2. Expand the real Datomic comparison matrix beyond the current seventeen rows:
   richer direct pull examples and additional Day-of-Datomic host snippets.
3. Keep full-import storage architecture work on the roadmap: the next write
   milestone is shared/chunked immutable DB indexes or a bulk builder, not basic
   import feasibility.
4. Keep Datomic-shaped request-map ergonomics backed by the existing EDN
   map-query engine path as the host wrappers grow.
