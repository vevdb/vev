# Vev Codebase Improvements

This is the current ownership document for codebase findings. It is not a historical log; items are grouped by current disposition.

Status labels:

- `done`: implemented in Vev.
- `todo`: worthwhile Vev work, not blocked on Kvist.
- `kvist`: better solved in Kvist compiler/packages/tooling.
- `kvist-done`: implemented in Kvist compiler/packages/tooling.
- `later`: real issue, but larger design work or lower priority than DataScript parity, C ABI, and performance.
- `not-now`: valid observation, but not worth changing currently.

## Done

- `done` Avoid eager `arr.range` in hot ordinary loops.
  Runtime and ABI hot paths now use explicit counted `while` loops where Vev needs zero allocation. See commit `76cb603`.

- `done` Replace duplicate query-step builder logic.
  Query step indexing was consolidated so each step kind no longer repeats the same push/counter shape.

- `done` Keep macro and EDN frontend parity visible.
  The test suite now has canaries that exercise equivalent literal macro and EDN string paths.

- `done` Keep benchmark status current.
  `bench/datascript_bench/README.md` and `docs/benchmarks.md` contain current DataScript comparison results and known baseline caveats.

- `done` Avoid extra `Value` array copies in parser rendering paths.
  Parser-rendered vectors now return direct `Value` literals around owned dynamic arrays when the caller already owns the array.

- `done` Use `arr.sort!` for primitive local sorts.
  `sort-strings!` and `sort-i64!` now delegate to Kvist's array sort instead of carrying local insertion sorts.

- `done` Expose public shallow cleanup for values and results.
  `delete-owned-value`, `delete-value-containers-shallow`, `delete-pull-result-shallow`, `delete-result-set-shallow`, and `delete-profiled-result-set-shallow` are now available in the `vev` package. ABI result cleanup delegates to these helpers, and query rule benchmarks use them instead of deleting only `rows`.

- `done` Define duplicate transaction function registration semantics.
  Native transaction execution now rejects duplicate tx-fn idents with a clear failed report, and the C ABI registration API rejects duplicates before allocating a callback slot.

- `done` Remove ABI transaction listener tombstones.
  ABI `unlisten` now deletes the owned listener name and removes the listener entry, matching pure Kvist listener behavior.

- `done` Add typed empty statement collections and lookup-ref collection binds.
  The C ABI now exposes string, keyword, entity, and int lookup-ref collection bind functions. Python statement bindings support explicit typed empty collections/tuples/relations and non-string lookup-ref collections.

- `done` Introduce ordered, indexed query variable collection.
  The main query variable collectors now preserve stable order while using a temporary string membership index. The older linear `append-var-once!` remains for smaller validation/update paths where carrying an index would add more complexity than it removes.

- `done` Use `set[T]` in high-volume pure membership paths.
  Recursive transitive traversal, entity-column de-duplication, and result-row de-duplication now use Kvist sets instead of `map[T]bool` where no boolean payload exists.

- `done` Add a tempid lookup table during transaction resolution.
  Transaction resolution now keeps a parallel `map[string]u64` while preserving the ordered tempid report vector, avoiding repeated scans for resolved tempids in larger transactions.

- `done` Add direct entity read helpers.
  `entity-first-value`, `entity-first-value-kw`, `entity-has-value?`, `entity-has-value-kw?`, `entity-first-ref`, and `entity-first-ref-kw` provide allocation-free first/existence reads. `entity-get`, `entity-contains?`, and `entity-ref` now delegate to those helpers instead of building full arrays.

- `done` Validate negative integer query entity terms.
  EDN text query parsing now rejects negative integer literals in entity-term positions before they can be cast to `u64`, while preserving negative integers in value positions.

- `done` Make tuple schema transaction helpers slice-based.
  `tuple-attrs-schema-tx-from` now accepts an arbitrary `[]string` of component attrs, and the existing two-attr `tuple-attrs-schema-tx` delegates to it.

- `done` Add public shallow lifecycle cleanup for prepared/query/report containers.
  `delete-query-shallow`, `delete-prepared-query-shallow`, `delete-prepared-rules-shallow`, `delete-prepared-tx-data-shallow`, and `delete-tx-report-shallow` are now canonical Vev helpers. The C ABI prepared-query free path delegates to the package helper instead of carrying a duplicate local copy.

- `done` Tighten temporary text-query cleanup.
  Direct `q-text` variants that parse a short-lived query now defer `delete-query-shallow` after execution, so temporary query container arrays are not leaked on the main EDN string API path.

- `done` Broaden Clojure/Java pull lookup-ref API shapes beyond string values.
  Direct pull lookup-ref APIs now support string, keyword, entity, and int values through the C ABI and Java wrapper. The Clojure wrapper dispatches Datomic-style lookup refs such as `[:user/email "ada@example.com"]`, `[:user/status :active]`, and `[:user/code 1001]`.

- `done` Improve C ABI value inspection helpers.
  `vev_value_map_get` and `vev_value_text_equals` now cover the common C-client map lookup/text comparison boilerplate used by pull and tx-report inspection. The C smoke delegates to those helpers instead of hand-scanning map entries.

- `done` Tighten ABI transaction builder cleanup.
  `vev_tx_free` now deletes the C-owned attr strings and value payloads held by `vev_tx_create` builders before releasing the raw wrapper array. The cleanup stays builder-specific because generic `Tx-Data` can contain borrowed package literals.

- `done` Remove global Clojure prepared-query cache.
  Ad hoc Clojure `q`, `rows`, and `scalar` calls now prepare a temporary native query handle and close it after use. Explicit `vev/prepare` remains the reuse path, avoiding cross-engine prepared handle sharing and unbounded native cache lifetime.

- `done` Add shared benchmark timing helpers.
  `bench/support` now owns elapsed-time and sample percentile helpers. Query-rule, stress, ABI-native, and index benchmarks use the shared `Timing` helpers instead of carrying local copies.

- `done` Add local builders for pull spec option variants.
  Pull spec constructors, EDN pull option parsing, and recursion-depth rewriting now use shared `pull-spec-with-*` helpers instead of manually copying every `Pull-Spec` field for each option variant.

- `done` Use a local set for cardinality-one entity/attr tracking.
  The transaction path now uses `set[string]` for the local entity/attr membership table. Helper-heavy membership paths still use `map[string]bool` until pointer-shaped set mutation is usable end-to-end in Vev.

## Vev TODO

- `todo` Finish parser-owned AST/value cleanup.
  The current EDN parser still stores many AST strings as borrowed slices from source text, while container values can own nested arrays. The next cleanup step is an explicit parser ownership model: either clone AST strings and provide deep destructors for `Query`, `Rule`, pull specs, tx data, and parsed inputs, or keep borrowed text handles deliberately and only deep-delete value containers known not to escape into results.

- `todo` Make host result decoding less duplicated.
  Java and Clojure duplicate scalar conversion and optimized result projection cascades. Prefer `vev_result_value` plus value accessors as the primary path, then generate or de-emphasize narrow compatibility accessors.

- `todo` Use set-backed visited state where linear arrays are still used for cycle tracking.
  Recursive retract and pull recursion still have linear `u64` visited scans in some paths.

- `todo` Replace remaining `map[T]bool` set emulation where semantics are pure membership.
  Binding de-dupe keys and ordered query-variable indexes still use pointer-to-map helpers because mutating set parameters by value lowers to non-addressable Odin map assignments. Revisit after the pointer-to-set helper pattern compiles and mutates correctly in Vev.

- `todo` Consider a map-backed `Binding` index.
  Binding lookup is order-preserving but scan-heavy. A binding could keep ordered items plus `map[string]int`, or relation join code could build a temporary lookup map for join keys.

- `todo` Consider reshaping `Query-Relation` attrs.
  Parallel `attrs` / `attr-sources` arrays couple indexes manually. A small `{name source}` record or an auxiliary source map would be clearer.

- `todo` Replace rule dependency repeated reachability with a graph/SCC pass.
  Current dependency checks recompute reachability over array graphs. This is likely a correctness-preserving performance win for larger rule sets.

- `todo` Add a `Value-Map-Builder`.
  Pull/result rendering manually tracks duplicate map keys by scanning `Value` pairs. A builder can centralize duplicate handling and ownership.

- `todo` Normalize transaction macro entity dispatch.
  Literal transaction macros repeat the same entity-ref matrix for lookup refs, idents, tempids, and ints. A macro helper should classify and emit it once.

- `todo` Consolidate ABI exported query/bind variants.
  Several exported collection/query functions repeat null checks, prepared-query checks, input parsing, cleanup, and result dispatch. Add local helpers or Vev macros before extending the matrix further.

- `todo` Add generic query/parser AST visitors.
  Source validation, source-input validation, relation-DB query rewriting, and EDN query section parsing all hand-walk the same query or EDN shapes. A reusable visitor/mapper plus single-pass section indexing would reduce duplicate traversal logic.

- `todo` Build reusable physical query operators.
  Entity-column scans, profiled row rendering, same-entity star plans, relation-source row matching, and specialized typed result paths duplicate scan/filter/project logic. Indexed scan, row matcher, star/merge-scan, and column materialization operators should feed both rows and typed columns.

- `todo` Cache prepared rule planning data.
  Rule-call planning still rebuilds dependency graphs, reachable rule sets, recursion classification, and transitive-shape recognition from raw rules. Prepared queries should own reusable rule plans instead of deriving them during execution.

- `todo` Add a rule lookup/index structure.
  Rule validation, arity checks, required-binding checks, and planning repeatedly scan all rules by name and arity. A rule index keyed by name and arity would centralize those checks and avoid repeated scans.

- `todo` Improve schema property access.
  Schema extraction and hot schema predicates repeat similar EAVT probes for keyword and boolean properties. A schema property accessor or cached schema map/view would centralize these scans and make predicate paths cheaper.

- `todo` Make index order a typed helper.
  Seek/rseek/range paths repeatedly branch on string index names to select `eavt`, `aevt`, `avet`, or `vaet`. A typed index-order value plus `db-index-slice` helper would remove string dispatch from core scan code.

- `todo` Fix partial owned cleanup on parse failures.
  EDN value conversion, serialized DB parsing, and datom parsing delete container arrays shallowly on some error paths. Previously parsed nested `Value` containers and datoms need owned cleanup helpers on failure.

- `todo` Add structured parser diagnostics and malformed-input suites.
  Parser parity work is now broad enough that tests should assert portable structured error categories for malformed query, pull, rule, return-map, and tx-data shapes instead of only checking `not ok` or exact strings.

- `todo` Add test support helpers for Vev values and results.
  Tests build verbose nested `Value` fixtures and carry local result/pull search helpers. EDN-to-`Value` fixture helpers, `value-get-in`, and row/pull matchers would make compatibility tests easier to read and extend.

- `todo` Materialize pull values more cleanly for ABI results.
  ABI results currently keep pull structures in the result plus a side array of rendered pull `Value`s. A single owned materialized result representation would simplify pull result access and cleanup.

## Kvist / Compiler / Package Work

- `kvist-done` Optimize ordinary `for` over eager bounded `arr` producers.
  Kvist now lowers ordinary direct `for` sources over `arr.range`, `arr.repeat`, `arr.repeatedly`, `arr.iterate`, `arr.cycle`, and `arr.take-nth` to direct loops without allocating helper arrays. Vev's existing `while` workarounds can stay where they are clearer, but new ordinary loops no longer pay the eager-array cost for these source forms.

- `kvist-done` Comparator/key sorting with captures.
  Kvist now supports captured inline `fn` key functions for `arr.sort-by` and `arr.sort-by!`, using explicit capture-aware sort helpers. Vev may still keep custom datom and `Value` sorting where it needs non-key comparator semantics, but captured key sorting no longer blocks ordinary package use.

- `kvist` ABI metadata/header generation.
  `include/vev.h`, Python signatures, Java/JNA bindings, and Rust declarations mirror the Kvist ABI manually. A sidecar generator from exported declarations would reduce drift.

- `kvist` Better C ABI glue ergonomics.
  Vev's ABI layer needs raw Odin for wrapper structs, callback proc types, pointer casts, and repeated `runtime.default_context()` setup.

- `kvist` Captured C callback ergonomics.
  ABI transaction callbacks currently need a fixed trampoline table. First-class host callback wrapping would remove this limit.

- `kvist` Shared macro/runtime parser descriptions.
  Query, pull, and tx syntax must exist both as Kvist literal macros and runtime EDN text parsing. A shared parser description or codegen story would reduce parity drift.

- `kvist-done` Record update syntax.
  Kvist already has `assoc` and `update` for shallow struct copy-with-field-changed code, including threaded forms such as `(-> spec (assoc .children children))`. Pull spec variants and similar record updates can use those helpers instead of hand-copying records.

- `kvist` Ownership-transfer annotations or inference for consuming helper functions.
  An ordinary helper like `value-vector-owned(items: [dynamic]Value) -> Value` is the shape Vev wants, but today it triggers owned-local warnings at call sites. Direct `Value` literals work because the compiler can see the ownership move.

- `kvist` Static lookup tables shared between macro and runtime code.
  Predicate/function/aggregate operator tables exist as macro allow-lists and runtime string dispatch.

- `kvist-done` Macro string/number helpers for source parsing.
  Kvist macros now have `parse-int` / `str.parse-int` and `digit?` / `str.digit?` helpers for source-string parsing. `parse-int` returns an integer on success and `nil` on failure, preserving `0` as a truthy parsed value in macro conditionals.

- `kvist` EDN child traversal via `defiter` may need better ergonomics.
  A Vev-local iterator for `EDN-Doc` children would be useful, but whether this is clean today depends on iterator ergonomics over linked child lists.

- `kvist` String builder/unescape helpers.
  EDN rendering and string unescaping build arrays of string parts. A standard builder/unescape package would be a cleaner long-term fit.

- `kvist` Standard Option/Maybe type.
  Vev uses value-plus-`has-*` fields and raw sentinel values in schema attrs, index args, input binding parsing, and absent `Value` results. A standard option type would make those shapes explicit.

- `kvist-done` Array fill/repeat helpers.
  `arr.repeat` already covers owned repeated arrays, and Kvist now has `arr.fill!` for in-place slice/dynamic-array initialization. Dense indexes and benchmark/sample setup can use the package helper instead of manual fill loops where it improves readability.

- `kvist` Pointer-to-set helper signatures usable for mutation.
  Vev can use local `set[string]` values, but helper-heavy membership paths still need an addressable mutable set parameter. The current Vev test pass works with local set mutation only; pointer-shaped set helpers should be rechecked once the installed compiler accepts and lowers them end-to-end.

- `kvist-done` Better macro-time collection utilities.
  Kvist macros now have a macro-time `reduce` helper over source form collections, plus macro-time `+` for numeric accumulators. Literal tx/pull-style macros can fold over option and clause forms instead of enumerating every ordering by hand.

- `kvist` Scoped owned aggregate helpers.
  Benchmarks and ABI execution paths often allocate several related owned arrays/values and then carry long `defer delete` blocks. Language or package support for scoped owned aggregate records would make this pattern less fragile.

## Later Architecture

- `later` Revisit manual tagged structs.
  `Term`, tx EDN entities, input bindings, query inputs, pull visits, native query functions, rule call strategies, ground clauses, aggregate find specs, tx function callbacks, index args, and EDN nodes use kind enums plus payload flags. Some can become `defunion`, but broad conversion is not urgent and may affect ABI or serialization expectations.

- `later` Add stronger tx data invariants.
  `Tx-Data` is a wide struct with `op` string and many flags. At minimum it wants an op enum; longer-term it may want smaller entity/value-ref variants.

- `later` Use `keyword` or domain-specific distinct string types more deeply.
  First-class Kvist keywords are now available, but Vev still stores many symbolic DB values as strings for compatibility with EDN text and ABI surfaces. Convert only where it improves invariants without adding wrapper churn.

- `later` Replace string dedupe keys with comparable key structs.
  Row and binding dedupe use length-prefixed strings. Struct keys would avoid formatting and make collisions structurally impossible, but this needs careful map-key support and benchmarking.

- `later` Use an EDN writer/builder.
  Serialization currently builds string parts and concatenates. This is real cleanup, but not on the critical path unless serialization becomes hot or harder to maintain.

- `later` Revisit `DB` and entity borrow semantics.
  `DB`, `DB-Source`, and `Entity` are value structs carrying dynamic-array headers. A distinct borrowed DB handle/reference type could make owner vs view semantics clearer, but this needs an API-wide ownership decision.

- `later` Revisit EDN document child storage.
  `EDN-Doc` stores children as linked sibling indexes in one node array, which forces linear `edn-child-at` and cursor loops. Child spans or child-index arrays would better match Kvist slice-heavy traversal.

- `later` Add generic typed result/column batches.
  Typed fast paths and ABI wrappers are currently shape-specific for entity columns, entity/int pairs, and entity/string/int triples. A generic column batch or typed relation result would age better than adding benchmark-shaped accessors.

- `later` Consolidate transitive graph execution.
  Forward/reverse adjacency construction, sparse/dense BFS, unbound-start emission, and alternating traversal all carry similar graph-walk logic. A graph traversal helper should come with the broader physical-operator layer.

- `later` Normalize EDN value decoding modes.
  Normal EDN value conversion and serialized-value conversion are nearly the same recursive decoder with a few tag hooks. A parameterized decoder mode would reduce duplication once the parser surface stabilizes.

- `later` Decide canonical map ordering.
  Map equality is order-insensitive, while map ordering compares stored item arrays. Canonical map storage or a map-specific comparator would make equality and ordering semantics line up.

- `later` Generate ABI handle/accessor declarations.
  The handle/accessor pairs are mechanical. This is a good macro-generation target once the ABI shape stabilizes further.

- `later` Generate value-wrapper APIs.
  `q-*value*` wrappers mostly delegate to result-returning APIs plus `result-set-value`. A macro can generate these once the public surface stops moving.

- `later` Improve tests with table assertions.
  Some long indexed assertions could use `t.are`, but this is cleanup rather than product progress.

## Not Now

- `not-now` Replace all simple build-output loops with transforms.
  Some parser rendering helpers benefit from owned constructors, but many query loops need early returns, ownership cleanup, mutable stats, or index bounds. Manual loops are fine there.

- `not-now` Convert every local membership helper to generic `contains?`.
  Use package helpers where it clarifies code. Keep specialized helpers when they encode typed semantics or avoid repeated conversions.

- `not-now` Broad wrapper-matrix collapse.
  Query and pull variants are intentionally explicit while the API is still being shaped for C, Java, Python, Clojure, Rust, and Kvist. Macro generation should come after the final public API settles.

- `not-now` Replace all raw `core:strings` calls with `kvist:str`.
  Prefer `kvist:str` where it makes code clearer, but mixed `core:strings` usage is not worth a dedicated cleanup pass unless the surrounding code is already changing.
