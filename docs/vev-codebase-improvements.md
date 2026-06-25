# Vev Codebase Improvements

This is the current ownership document for codebase findings. It is not a historical log; items are grouped by current disposition.

Status labels:

- `done`: implemented in Vev.
- `todo`: worthwhile Vev work, not blocked on Kvist.
- `kvist`: better solved in Kvist compiler/packages/tooling.
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

## Vev TODO

- `todo` Add public deep cleanup/destructor APIs for prepared/query/transaction-owned values.
  Native Kvist callers still need canonical cleanup for `Prepared-Query`, `Prepared-Rules`, `Prepared-Tx-Data`, `Query`, `Rule`, and `Tx-Report`. This needs a careful shallow/deep ownership split for rules, not/or groups, pull specs, tx reports, and prepared containers.

- `todo` Tighten prepared/query lifecycle cleanup in text APIs.
  Text query execution parses owned `Query` containers, then delegates. The ownership split between shallow and deep cleanup should be explicit.

- `todo` Fix or define duplicate transaction function registration semantics.
  Registering the same transaction function ident twice currently appends a second entry while lookup uses the first. This should either reject duplicates or replace the existing function.

- `todo` Replace ABI transaction listener tombstones with removal or documented slot reuse.
  Pure Kvist listeners remove entries. ABI listeners nil callbacks but keep owned names until connection close.

- `todo` Add typed empty sequence and lookup-ref sequence support to the ABI.
  Python/Clojure wrappers expose a real limitation: empty typed collections cannot be represented, and lookup-ref collection binding is string-only.

- `todo` Make host result decoding less duplicated.
  Java and Clojure duplicate scalar conversion and optimized result projection cascades. Prefer `vev_result_value` plus value accessors as the primary path, then generate or de-emphasize narrow compatibility accessors.

- `todo` Introduce an `Ordered-String-Set` helper.
  Query variable collection and similar visitors need stable order plus fast membership. This should replace repeated `append-var-once!` / linear `contains?` loops.

- `todo` Use set-backed visited state where linear arrays are still used for cycle tracking.
  Recursive retract and pull recursion still have linear `u64` visited scans in some paths.

- `todo` Replace `map[T]bool` set emulation where semantics are pure membership.
  Use Kvist `set[T]` when no stored boolean meaning exists.

- `todo` Consider a map-backed `Binding` index.
  Binding lookup is order-preserving but scan-heavy. A binding could keep ordered items plus `map[string]int`, or relation join code could build a temporary lookup map for join keys.

- `todo` Consider reshaping `Query-Relation` attrs.
  Parallel `attrs` / `attr-sources` arrays couple indexes manually. A small `{name source}` record or an auxiliary source map would be clearer.

- `todo` Replace rule dependency repeated reachability with a graph/SCC pass.
  Current dependency checks recompute reachability over array graphs. This is likely a correctness-preserving performance win for larger rule sets.

- `todo` Add a `Value-Map-Builder`.
  Pull/result rendering manually tracks duplicate map keys by scanning `Value` pairs. A builder can centralize duplicate handling and ownership.

- `todo` Add local builders for pull spec option variants.
  `Pull-Spec` construction repeats full records for small field changes. Until Kvist has record update syntax, local `pull-spec-with-*` helpers would reduce drift.

- `todo` Normalize transaction macro entity dispatch.
  Literal transaction macros repeat the same entity-ref matrix for lookup refs, idents, tempids, and ints. A macro helper should classify and emit it once.

- `todo` Consolidate ABI exported query/bind variants.
  Several exported collection/query functions repeat null checks, prepared-query checks, input parsing, cleanup, and result dispatch. Add local helpers or Vev macros before extending the matrix further.

- `todo` Scope Clojure prepared-query caching to native library/connection lifetime.
  The current cache key is query form only. Prepared native handles should not be shared across unrelated native engine/library instances.

- `todo` Add benchmark helper code.
  Query/rule and ABI benchmarks repeat timing, warmup, sample collection, and reporting loops. A small benchmark helper package would make further benchmark work cheaper.

## Kvist / Compiler / Package Work

- `kvist` Optimize ordinary `for` over `arr.range`.
  Vev has worked around hot cases with `while`, but the language should probably lower ordinary range loops to counted loops when the source is syntactically `arr.range`.

- `kvist` Comparator/key sorting with captures.
  Vev still needs custom datom and `Value` sorting because `arr.sort-by!` cannot close over comparator context.

- `kvist` ABI metadata/header generation.
  `include/vev.h`, Python signatures, Java/JNA bindings, and Rust declarations mirror the Kvist ABI manually. A sidecar generator from exported declarations would reduce drift.

- `kvist` Better C ABI glue ergonomics.
  Vev's ABI layer needs raw Odin for wrapper structs, callback proc types, pointer casts, and repeated `runtime.default_context()` setup.

- `kvist` Captured C callback ergonomics.
  ABI transaction callbacks currently need a fixed trampoline table. First-class host callback wrapping would remove this limit.

- `kvist` Shared macro/runtime parser descriptions.
  Query, pull, and tx syntax must exist both as Kvist literal macros and runtime EDN text parsing. A shared parser description or codegen story would reduce parity drift.

- `kvist` Record update syntax.
  Pull spec variants and similar record-copy-with-one-field-changed code are verbose.

- `kvist` Ownership-transfer annotations or inference for consuming helper functions.
  An ordinary helper like `value-vector-owned(items: [dynamic]Value) -> Value` is the shape Vev wants, but today it triggers owned-local warnings at call sites. Direct `Value` literals work because the compiler can see the ownership move.

- `kvist` Static lookup tables shared between macro and runtime code.
  Predicate/function/aggregate operator tables exist as macro allow-lists and runtime string dispatch.

- `kvist` Macro string/number helpers for source parsing.
  Macro parsing still has places that would benefit from robust `parse-int`, digit predicates, and similar helpers.

- `kvist` EDN child traversal via `defiter` may need better ergonomics.
  A Vev-local iterator for `EDN-Doc` children would be useful, but whether this is clean today depends on iterator ergonomics over linked child lists.

- `kvist` String builder/unescape helpers.
  EDN rendering and string unescaping build arrays of string parts. A standard builder/unescape package would be a cleaner long-term fit.

## Later Architecture

- `later` Revisit manual tagged structs.
  `Term`, tx EDN entities, input bindings, query inputs, pull visits, and native query functions use kind enums plus payload flags. Some can become `defunion`, but broad conversion is not urgent and may affect ABI or serialization expectations.

- `later` Add stronger tx data invariants.
  `Tx-Data` is a wide struct with `op` string and many flags. At minimum it wants an op enum; longer-term it may want smaller entity/value-ref variants.

- `later` Use `keyword` or domain-specific distinct string types more deeply.
  First-class Kvist keywords are now available, but Vev still stores many symbolic DB values as strings for compatibility with EDN text and ABI surfaces. Convert only where it improves invariants without adding wrapper churn.

- `later` Replace string dedupe keys with comparable key structs.
  Row and binding dedupe use length-prefixed strings. Struct keys would avoid formatting and make collisions structurally impossible, but this needs careful map-key support and benchmarking.

- `later` Use an EDN writer/builder.
  Serialization currently builds string parts and concatenates. This is real cleanup, but not on the critical path unless serialization becomes hot or harder to maintain.

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
