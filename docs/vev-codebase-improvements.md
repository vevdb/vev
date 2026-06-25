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

- `done` Avoid repeated reachability scans in rule-call planning.
  Rule planning now computes the reachable rule-name set once per call plan and reuses it for call-rule filtering, recursive dependency detection, and acyclic depth. This keeps the general dependency-graph path while removing repeated `rule-reaches-name?` scans from the hot planning path.

- `done` Add a `Value-Map-Builder` for pull rendering.
  Pull map rendering now uses a small builder to centralize duplicate-key checks and map materialization instead of open-coding key scans against the raw `Value` item array.

- `done` Centralize schema property lookup.
  Keyword, boolean, and ident schema helpers now share `schema-property-value-for-entity`, giving hot schema predicate paths one EAVT property accessor that can later be backed by a cached schema view.

- `done` Use `kvist:str` helpers for EDN string parsing/rendering.
  Runtime EDN string parsing now delegates escape decoding to `kstr.unescape`, and EDN string rendering uses `kstr.builder` instead of building an intermediate dynamic array of string fragments.

- `done` Use `defiter` for EDN child traversal.
  `EDN-Doc` now has local `edn-siblings`, `edn-children`, and `edn-child-pairs` iterators for linked child-list traversal. Query and EDN text decoding paths can use direct `for` loops over child indexes instead of hand-rolled sibling cursor loops.

- `done` Use `set[T]` for ordered query-variable and binding membership indexes.
  Binding de-dupe keys, ordered query-variable collection, and primitive projection de-dupe now use `set[string]` for pure membership state instead of `map[string]bool` payload emulation.

- `done` Use `arr.repeat` for dense boolean index initialization.
  Dense transitive-rule helper arrays now use the package repeated-array helper instead of a manual fill loop.

## Vev TODO

- `done` Finish parser-owned AST/value cleanup.
  The EDN parser keeps AST strings as borrowed source text deliberately and now cleans parser-owned nested value containers on failed recursive value, serialized DB, datom, transaction argument, lookup-ref, CAS, and partial tx-data parse paths. Failed tx text parsing also rolls back entries appended to the caller's output array.

- `done` Make host result decoding less duplicated.
  The Clojure wrapper now funnels `rows` and `q` through shared optimized-result dispatch helpers instead of repeating the entity-column, entity/int-pair, entity/string/int-triple, and generic result fallback cascade in each call shape. Java and C ABI compatibility accessors remain intentionally available.

- `todo` Use set-backed visited state where linear arrays are still used for cycle tracking.
  Recursive retract and pull recursion still have linear `u64` visited scans in some paths.

- `todo` Consider a map-backed `Binding` index.
  Binding lookup is order-preserving but scan-heavy. A binding could keep ordered items plus `map[string]int`, or relation join code could build a temporary lookup map for join keys.

- `todo` Consider reshaping `Query-Relation` attrs.
  Parallel `attrs` / `attr-sources` arrays couple indexes manually. A small `{name source}` record or an auxiliary source map would be clearer.

- `todo` Add explicit SCC metadata for rule components.
  Rule planning now reuses single-start reachability, but recursive component detection still checks mutual reachability on demand. A Tarjan/Kosaraju pass would make component recursion, rule grouping, and future semi-naive planning more direct.

- `done` Normalize transaction macro entity dispatch.
  Literal transaction macro paths for add/retract, value-less attr retract, and entity retract now share one entity dispatch helper for lookup refs, current-tx/tempid strings, idents, and numeric entity ids instead of repeating the same matrix in each macro branch.

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

- `todo` Make index order a typed helper.
  Seek/rseek/range paths repeatedly branch on string index names to select `eavt`, `aevt`, `avet`, or `vaet`. A typed index-order value plus `db-index-slice` helper would remove string dispatch from core scan code.

- `todo` Add structured parser diagnostics and malformed-input suites.
  Parser parity work is now broad enough that tests should assert portable structured error categories for malformed query, pull, rule, return-map, and tx-data shapes instead of only checking `not ok` or exact strings.

- `done` Add test support helpers for Vev values and results.
  Vev tests now have row-level `Value` matchers for entity, string, integer, and arbitrary expected values. Existing result search helpers delegate through those matchers, giving compatibility tests a less verbose pattern for result-row assertions without introducing a larger fixture DSL.

- `todo` Materialize pull values more cleanly for ABI results.
  ABI results currently keep pull structures in the result plus a side array of rendered pull `Value`s. A single owned materialized result representation would simplify pull result access and cleanup.

## Kvist / Compiler / Package Work

- `kvist-done` Optimize ordinary `for` over eager bounded `arr` producers.
  Kvist now lowers ordinary direct `for` sources over `arr.range`, `arr.repeat`, `arr.repeatedly`, `arr.iterate`, `arr.cycle`, and `arr.take-nth` to direct loops without allocating helper arrays. Vev's existing `while` workarounds can stay where they are clearer, but new ordinary loops no longer pay the eager-array cost for these source forms.

- `kvist-done` Comparator/key sorting with captures.
  Kvist now supports captured inline `fn` key functions for `arr.sort-by` and `arr.sort-by!`, using explicit capture-aware sort helpers. Vev may still keep custom datom and `Value` sorting where it needs non-key comparator semantics, but captured key sorting no longer blocks ordinary package use.

- `not-now` ABI metadata/header generation.
  `include/vev.h`, Python signatures, Java/JNA bindings, and Rust declarations mirror the Kvist ABI manually. A sidecar generator from exported declarations would reduce drift, but the current manual surface is acceptable unless drift becomes a concrete maintenance problem.

- `not-now` Better C ABI glue ergonomics.
  Vev's ABI layer needs raw Odin for wrapper structs, callback proc types, pointer casts, and repeated `runtime.default_context()` setup. This is genuine friction, but the current cases are mostly Odin interop surface area and exported-ABI boilerplate rather than a small package/compiler improvement. Prefer Vev-local helper macros or code generation if the ABI layer keeps growing; do not add new Kvist interop syntax without a separate design.

- `not-now` Captured C callback ergonomics.
  ABI transaction callbacks currently need a fixed trampoline table, while tx listeners already store callback/user pairs directly in raw Odin. Removing the tx-function slot limit is a Vev ABI/runtime representation change unless Kvist gets first-class host callback wrapping. Leave this as-is unless the fixed slot count becomes a concrete product limit.

- `not-now` Shared macro/runtime parser descriptions.
  Query, pull, and tx syntax must exist both as Kvist literal macros over source forms and runtime EDN text parsing over `EDN-Doc` nodes. A shared parser description or codegen story could reduce parity drift, but it would be a new language/tooling design rather than a small compiler/package cleanup. Leave this alone unless concrete parity bugs show that tests are not enough.

- `kvist-done` Record update syntax.
  Kvist already has `assoc` and `update` for shallow struct copy-with-field-changed code, including threaded forms such as `(-> spec (assoc .children children))`. Pull spec variants and similar record updates can use those helpers instead of hand-copying records.

- `kvist-done` Ownership-transfer annotations for consuming helper functions.
  Kvist now supports `(consumes param-name...)` in `defn` declarations. An ordinary helper like `value-vector-owned(items: [dynamic]Value) -> Value` can mark `(consumes items)`, and call sites transfer ownership when passing an owned local to that parameter.

- `not-now` Static lookup tables shared between macro and runtime code.
  Predicate/function/aggregate operator tables exist as macro allow-lists and runtime string dispatch. A Vev-local macro source of truth could reduce recognition-list drift, but it would not remove the runtime behavior dispatch and is not worth the extra abstraction right now.

- `kvist-done` Macro string/number helpers for source parsing.
  Kvist macros now have `parse-int` / `str.parse-int` and `digit?` / `str.digit?` helpers for source-string parsing. `parse-int` returns an integer on success and `nil` on failure, preserving `0` as a truthy parsed value in macro conditionals.

- `kvist-done` String builder/unescape helpers.
  `kvist:str` now has `str.builder`, `str.write!`, `str.finish`, `str.destroy!`, and `str.unescape`. Vev uses these for EDN string escape decoding and scalar string rendering; broader recursive value rendering should wait for a clearer owned-string result convention.

- `not-now` Standard Option/Maybe type.
  Vev uses value-plus-`has-*` fields and raw sentinel values in schema attrs, index args, input binding parsing, and absent `Value` results. Do not introduce a standard Option/Maybe abstraction for this now; Kvist's existing multi-return `[value ok]` style and explicit fields are acceptable.

- `kvist-done` Array fill/repeat helpers.
  `arr.repeat` already covers owned repeated arrays, and Kvist now has `arr.fill!` for in-place slice/dynamic-array initialization. Vev uses `arr.repeat` for dense boolean index initialization.

- `kvist-done` Pointer-to-set helper signatures usable for mutation.
  Kvist now lowers `core.get`, `core.delete!`, `map.dissoc!`, and `contains?` correctly for pointer-to-map targets, which also covers `^set[T]` after set lowering. `set.contains?` forwards through the pointer-aware core membership form, so Vev can use ordinary `set.contains?` spelling with value sets and pointer-shaped helper signatures.

- `kvist-done` Better macro-time collection utilities.
  Kvist macros now have a macro-time `reduce` helper over source form collections, plus macro-time `+` for numeric accumulators. Literal tx/pull-style macros can fold over option and clause forms instead of enumerating every ordering by hand.

- `not-now` Scoped owned aggregate helpers.
  Benchmarks and ABI execution paths often allocate several related owned arrays/values and then carry long cleanup blocks. Kvist already has `:defer` and `:defer-with` for local cleanup, and the remaining hard cases are Vev-specific ownership-transfer paths where arrays are handed to result/statement handles. Prefer small Vev-local builder structs and cleanup functions if a concrete cluster becomes painful; do not add general Kvist support now.

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
