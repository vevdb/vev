** Round 1
Main Improvement Candidates

    1. arr.range in ordinary for loops is probably the biggest Kvist-idiom/perf issue.

    Kvist docs say arr.range is eager and owned normally, and only lowers to a direct counted loop in transform positions. Vev uses ordinary for [i
    (arr.range ...)] in hot paths, for example src/vev/runtime.kvist:1526, src/vev/runtime.kvist:7529, src/vev_abi/vev_abi.kvist:1657, and benches. These
  are
    cases where Vev wants a counted loop, not an allocated dynamic array. If Kvist does not special-case ordinary for sources, this is exactly where Vev is
    working around a missing “counted range loop” form. For hot index scans, use while today or add/ask for Kvist support for optimized ordinary range
  loops.

    2. Simple “build output array from input slice” code could use arr.map or transforms.

    Examples: parser rendering in src/vev/edn_text.kvist:6, src/vev/edn_text.kvist:64, and result projection helpers in src/vev/runtime.kvist:8664. These
  are
    idiomatic candidates for eager collection helpers or into with (map ...) / (filter ...). I would not apply that everywhere: many query loops need early
    returns, index bounds, dedupe maps, ownership cleanup, or mutable stats, where manual loops are clearer.

    3. query_model.kvist has duplicated step-building logic that would fit a small local abstraction or macro.

    src/vev/query_model.kvist:98 and src/vev/query_model.kvist:141 repeat the same case/counter/push logic. This is not a transform fit because each kind
    mutates a different counter, but it is a good Kvist macro/helper fit: generate the per-kind branch table once.

    4. The ABI layer shows a real Kvist surface gap.

    src/vev_abi/vev_abi.kvist:8 contains a large raw Odin block for wrapper structs, raw pointer helpers, C callback proc types, and low-level casts. Later
    exported functions use normal Kvist @export/:abi "c", but nearly every exported function repeats (odin "context = runtime.default_context()"), e.g.
  src/
    vev_abi/vev_abi.kvist:1138. This suggests Kvist can express exported C functions, but not enough of the surrounding C ABI glue ergonomically: C proc
  type
    aliases, callback casts, wrapper pointer helpers, and per-export context reset.

    5. Literal macros and EDN text parsing duplicate compatibility work because macro-time and runtime worlds are separate.

    Vev has a rich literal macro parser in src/vev/macros.kvist:1204 and a runtime EDN parser in edn_text.kvist. That split is expected in Kvist: macros
    transform source forms and cannot reuse runtime parser logic directly. The cost is that query/tx/pull support must be kept aligned manually. This is a
    language-model limitation more than a bug, but Vev is a strong example where a shared parser description or macro/runtime codegen story would help.

** Round 2
Second pass found several more candidates worth noting.

  1. Manual tagged unions where defunion may fit better

  Vev uses kind enums plus payload fields for values like Term, Tx-EDN-Entity, Input-Binding, Query-Input-Spec, and Query-Input, e.g. src/vev/core.kvist:34,
  src/vev/core.kvist:203, src/vev/core.kvist:365, src/vev/core.kvist:647.

  Kvist has defunion and case over payload types. Value itself may need the manual layout for ABI/serialization stability, but internal parser/query helper
  shapes look like better union candidates.

  2. Parser child traversal is crying out for defiter

  The EDN AST is a linked child list with first-child / next-sibling, built in src/vev/query_model.kvist:222 and traversed repeatedly by hand, e.g. src/vev/
  edn_text.kvist:303, src/vev/edn_text.kvist:582, src/vev/edn_text.kvist:2614.

  A defiter edn-children / edn-map-entries would make those loops idiomatic Kvist and enable for, into, and possibly transform consumers without exposing
  the linked representation everywhere.

  3. Vev’s [ok, error] convention does not compose with Kvist guard helpers

  Kvist’s :or-return, when-let, and if-let work naturally when the final return value is the success flag. Vev often returns [ok: bool, error: string], for
  example src/vev/edn_text.kvist:1721, src/vev/edn_text.kvist:1975, src/vev/edn_text.kvist:3277.

  That forces manual unpack/check/return patterns. This is either a Vev convention to revisit or a Kvist gap: no ergonomic guard for “first return is bool,
  second is diagnostic”.

  4. Wide positional APIs should use a context/options struct or named arguments

  query, query-from-text-parts, make-rule-with-required, and parser append functions have very wide positional signatures, e.g. src/vev/
  query_model.kvist:44, src/vev/edn_text.kvist:181, src/vev/query_model.kvist:187, src/vev/edn_text.kvist:1398.

  Kvist supports named arguments, but for this many related arrays a Query-Parts / Where-Builder struct would be clearer and less error-prone.

  5. Some manual cleanup can be replaced with defer

  The EDN text paths manually delete doc.nodes on every return path, for example src/vev/edn_text.kvist:270, src/vev/edn_text.kvist:292, src/vev/
  edn_text.kvist:2053, src/vev/edn_text.kvist:3277.

  A local (defer (delete doc.nodes)) after doc creation would make these less fragile. Same idea applies to temporary result arrays like vars/bindings where
  ownership exits only on success.

  6. Constructors for simple owned wrappers are repetitive

  not-group, not-join-group, or-group, and or-join-group all do the same “copy slices into owned dynamic arrays, set flags” dance in src/vev/
  query_model.kvist:3. This is a good small macro/helper candidate, especially because Kvist macros are meant for declaring related forms from compact
  source.

  I would prioritize these after the arr.range issue: defiter for EDN traversal, then cleanup with defer, then the error-return convention.

** Round 3
Third Pass Findings

  1. map[T]bool is being used as a set in several places, but Kvist has a real set[T] type and package.
     Examples: src/vev/runtime.kvist:5471, src/vev/runtime.kvist:6886, src/vev/runtime.kvist:7532. Kvist documents set[T] as lowering to map[T]struct{} and
     ships set.contains? / set.add: /Users/andreas/Projects/kvist/docs/LANGUAGE.md:329, /Users/andreas/Projects/kvist/packages/set/set.kvist:77. This would
     make the intent clearer and avoid dummy boolean values.

  2. Vev hand-rolls sorting and binary search for datom indexes, which looks like a real Kvist library gap rather than just Vev style.
     The custom quicksort is here: src/vev/core.kvist:1168. Binary lower/upper bound logic is here: src/vev/core.kvist:1322. Kvist has arr.sort-by!, but the
     docs note captured callbacks are not supported for sort-by: /Users/andreas/Projects/kvist/docs/SEQUENCES.md:304. Vev needs comparator/key logic that
     closes over datoms and index order, so it falls back to custom code.

  3. The datom index order is stringly typed in hot comparison code.
     datom-index-less? dispatches on order strings like "eavt", "avet", etc.: src/vev/core.kvist:1140. This looks like a good fit for defenum, or even
     separate generated comparator functions. It would remove repeated string comparison and make invalid index orders impossible.

  4. The public API has a wrapper-matrix problem.
     Pull has many variants around prepared/value/text/fns/lookup-ref/many: src/vev/edn_text.kvist:668. Query has the same pattern across inputs/rules/fns/
     sources/profiled: src/vev/edn_text.kvist:2100. The public surface may be intentional, but the implementation is a strong macro-generation or options-
     struct candidate.

  5. slash-index is a small hand-rolled string helper even though kvist:str is already imported.
     Manual scan: src/vev/core.kvist:739. Used by keyword comparison: src/vev/core.kvist:745. Kvist’s str package already has index/search helpers, so this
     could likely become kstr.index-of or equivalent.

  6. There is repeated “add if unseen” logic that wants a tiny helper abstraction.
     The result-row dedupe path maintains several typed seen maps and repeats membership/update logic by value kind: src/vev/runtime.kvist:6878. Even if
     some of these need typed maps rather than a generic set[Value], a helper like seen-value-add? or per-kind Seen-Values API would reduce branching noise
     and centralize the semantics.

** Round 4
Fourth Pass Findings

  1. Tx-Data is the biggest remaining “invariants by convention” shape.
     It is a single wide struct with op: string plus many has-* flags: src/vev/core.kvist:113. Constructors then manually set compatible subsets: src/vev/
     core.kvist:886. Runtime code has to revalidate legal combinations later: src/vev/runtime.kvist:2676. This wants at least a Tx-Data-Op enum, and
     possibly smaller unions/structs for entity refs and value refs.

  2. Kvist’s keyword type looks underused for stable symbolic values.
     Kvist lowers keyword to distinct string: /Users/andreas/Projects/kvist/docs/LANGUAGE.md:256. Vev keeps many stable EDN keywords as plain strings,
     especially schema values like ":db.type/ref", ":db.cardinality/many", and tx ops: src/vev/runtime.kvist:1941, src/vev/runtime.kvist:3227. Internally,
     keyword or domain-specific distinct string types could separate “symbolic DB keyword” from arbitrary text.

  3. sort-strings! is a local insertion sort that can likely be deleted.
     Vev implements it here: src/vev/runtime.kvist:907 and uses it for binding dedupe keys: src/vev/runtime.kvist:917. Kvist already has in-place
     arr.sort!: /Users/andreas/Projects/kvist/packages/arr/arr.kvist:818. Unlike the datom-index sort, this one does not need captured comparator context.

  4. Dedupe keys are serialized strings where comparable key structs may be cleaner and cheaper.
     primitive-value-dedupe-key, row-values-dedupe-key, and binding-dedupe-key allocate formatted strings with manual length-prefixing: src/vev/
     runtime.kvist:803. A Primitive-Key / Binding-Key-Part struct, or a small union limited to comparable primitive fields, could avoid formatting and make
     collisions structurally impossible.

  5. Cycle tracking still uses linear dynamic arrays in several non-map cases.
     Examples: recursive retract uses u64-contains? over [dynamic]u64: src/vev/runtime.kvist:1679, src/vev/runtime.kvist:1685. Pull recursion does the same:
     src/vev/runtime.kvist:9105, src/vev/runtime.kvist:9519. This is another good set[u64] or Visited-Entities wrapper candidate.

  6. Pull-Visit repeats the same tagged-struct smell as the other internal variants.
     It stores kind: string, has-e, has-v, and inactive fields: src/vev/core.kvist:602. Constructors manually maintain the combinations: src/vev/
     runtime.kvist:9414. Since this is internal trace data, it seems like a low-risk place to use defenum/defunion without ABI concerns.
** Round 5
Fifth Pass Findings

  1. Vev has local membership helpers that duplicate Kvist contains?.
     Examples: string-slice-contains? src/vev/runtime.kvist:5880, u64-contains? src/vev/runtime.kvist:1679. Kvist core contains? already supports array-
     family scans and map/set membership: /Users/andreas/Projects/kvist/packages/core/core.kvist:217. A lot of call sites could become simpler without
     changing behavior.

  2. Binding is order-preserving but lookup-heavy.
     Bindings are stored as [dynamic]Var-Binding, and binding-lookup scans every time: src/vev/runtime.kvist:168. Joins call that inside nested loops: src/
     vev/runtime.kvist:265. A Binding could keep ordered items plus a map[string]int index, or relation-level code could build a temporary lookup map for
     join keys.

  3. Query-Relation uses parallel attrs / attr-sources arrays.
     Definition: src/vev/core.kvist:498. Source lookup scans attrs and indexes into attr-sources: src/vev/runtime.kvist:224. A small Relation-Attr {name
     source} struct, or a map[string]string alongside ordered attrs, would reduce index coupling.

  4. Query variable collection is a manual “unique vector builder”.
     append-var-once! + string-slice-contains? is used through a large visitor family: src/vev/runtime.kvist:5886. This wants a tiny Ordered-String-Set
     helper: array for order, set/map for membership. It would preserve output order while removing repeated O(n) membership scans.

  5. Rule dependency analysis repeatedly recomputes reachability over array graphs.
     The dependency graph is [dynamic]Rule-Dependency, and rule-reaches-name? scans deps and seen arrays repeatedly: src/vev/runtime.kvist:5267. Later code
     calls it in nested loops: src/vev/runtime.kvist:5329. A map-backed graph plus set-backed visited state, or one SCC pass, would fit Kvist’s map/set
     packages better.

  6. Operator support tables are duplicated as runtime or chains while macros use literal membership.
     Macros use contains? over literal lists: src/vev/macros.kvist:549. Runtime repeats the same knowledge as long chains: src/vev/runtime.kvist:6287. This
     is a good candidate for a generated predicate macro or shared operator table, and it hints at a Kvist gap around cheap static lookup tables/constants
     shared between macro and runtime code.
** Round 6
Sixth Pass Findings

  1. Value construction often allocates a temporary dynamic array, then value-vector / value-map copies it again.
     Example: src/vev/edn_text.kvist:6, src/vev/edn_text.kvist:21, src/vev/edn_text.kvist:2614. Since value-vector copies with arr.into: src/vev/
     core.kvist:702, Vev could use value-vector-owned / value-map-owned helpers for already-owned dynamic arrays.

  2. EDN parse functions still manually delete doc.nodes on every return path.
     Examples: src/vev/edn_text.kvist:256, src/vev/edn_text.kvist:268, src/vev/edn_text.kvist:3277. This is a direct :defer / with-edn-doc helper candidate.
     It would shrink the parser and reduce cleanup risk.

  3. Serialization builds strings through [dynamic]string parts and strings.concatenate.
     Examples: src/vev/edn_text.kvist:2650, src/vev/edn_text.kvist:2679, src/vev/edn_text.kvist:2722. Kvist packages already use strings.Builder for this
     style of incremental rendering: /Users/andreas/Projects/kvist/packages/html/html.kvist:315. A local EDN writer/builder would be more idiomatic and
     avoid many temporary strings.

  4. Aggregate sorting has one easy stdlib replacement and one Kvist gap.
     sort-i64! can likely become arr.sort!: src/vev/runtime.kvist:6560. sort-values! needs value-less?, so it still needs custom code unless Kvist grows
     comparator-based sorting: src/vev/runtime.kvist:6571.

  5. query-relation-apply-where-ops repeats the same “apply step, increment stats, delete current, observe bindings” block for every step kind.
     See src/vev/runtime.kvist:7267. A helper like replace-relation-step! plus a case returning the next relation would make ownership transfer and stats
     updates less error-prone.

  6. Result/pull rendering has ad hoc map-building logic that wants a reusable Value map builder.
     pull-render-attrs-map manually tracks seen keys by scanning items two slots at a time: src/vev/runtime.kvist:8796. The same file has many value-map
     ([]Value [...]) shapes. A small Value-Map-Builder could centralize key insertion, duplicate handling, and ownership.
** Round 7
1. Transaction macro entity dispatch is copy-pasted in several shapes
     src/vev/macros.kvist:77, src/vev/macros.kvist:124, src/vev/macros.kvist:226, src/vev/macros.kvist:254, src/vev/macros.kvist:282, src/vev/
     macros.kvist:315 all encode the same “entity ref can be lookup ref, ident, tempid, int” matrix. A macro helper that classifies/emits the entity ref
     once would make the literal tx macro much more idiomatic and reduce feature drift.

  2. Pull option handling is duplicated between macro-time and runtime EDN parsing
     The macro has a large nested option matrix at src/vev/macros.kvist:1796, while runtime EDN applies the same option set at src/vev/edn_text.kvist:425.
     This looks like a Kvist macro/runtime boundary pain point: Vev needs equivalent parsing semantics in two worlds. A shared normalized pull-option model
     would be better, if Kvist can make that pleasant.

  3. Pull spec “copy with one field changed” is very verbose
     src/vev/edn_text.kvist:430, src/vev/edn_text.kvist:496, and src/vev/edn_text.kvist:511 rebuild full Pull-Spec records to tweak one field. This is a
     good spot for a Kvist record-update helper/macro, or local pull-spec-with-* helpers for every variant.

  4. Macro source parsing hard-codes $1 through $9
     src/vev/macros.kvist:724 explicitly notes this. It should probably parse “symbol starts with $ and rest is digits” instead of enumerating. If Kvist
     macros lack a convenient parse-int / char predicate path, that is a concrete language/library gap.

  5. ABI handle declarations and pointer accessors are mechanically paired
     src/vev_abi/vev_abi.kvist:704 declares many distinct rawptr handle types, followed by thin pointer unwrap functions at src/vev_abi/vev_abi.kvist:719.
     This wants a small Kvist macro like def-abi-handle to declare the type, ptr accessor, and maybe null guard convention together.

  6. ABI exported collection/query variants repeat the same input parsing skeleton
     The entity column, int-pair, and string-int-triple exports at src/vev_abi/vev_abi.kvist:1984, src/vev_abi/vev_abi.kvist:2036, and src/vev_abi/
     vev_abi.kvist:2096 have the same null-check, prepared-query check, empty-input fast path, parse-inputs path, cleanup path. This is a strong candidate
     for a local helper or generated export macro.

  7. Tests could use Kvist’s table assertion idiom more
     src/vev_tests/parser_input_test.kvist:56 and src/vev_tests/vev_test.kvist:12895 have long runs of similar indexed t.is checks. Kvist already has t.are
     documented at /Users/andreas/Projects/kvist/docs/TESTING.md:76, which would make many of these tighter and more declarative.
** Round 8
1. Literal macro frontend is narrower than EDN despite the parity goal
     docs/interop.md:58 says strings and Kvist literals should converge into the same semantics. But src/vev/macros.kvist:1257, src/vev/macros.kvist:1276,
     and src/vev/macros.kvist:1329 still hard-limit source-prefixed/non-data clause shapes, while EDN tests/docs cover richer source-qualified not, or, not-
     join, or-join, and rule calls. This is a concrete macro/front-end gap.

  2. Pull-Spec constructors want a base builder plus option setters
     src/vev/runtime.kvist:9725 through src/vev/runtime.kvist:9878 repeats the full Pull-Spec record for every option combination. A base-pull-spec plus
     pull-spec-with-limit/default/as/xform/recursion would be much more Kvist-shaped. If Kvist grows record update syntax, this whole family becomes
     cleaner.

  3. Keyword convenience wrappers are manually duplicated
     *-kw wrappers repeatedly call keyword-text: src/vev/core.kvist:857, src/vev/core.kvist:889, src/vev/core.kvist:904, src/vev/runtime.kvist:9738. This
     points to either a small macro for paired string/keyword APIs or a Kvist trait/protocol gap for “string-like attr input.”

  4. C ABI statement bind functions form a generation matrix
     src/vev_abi/vev_abi.kvist:1571 starts scalar bind variants, then lookup-ref/collection/tuple/relation variants fan out by value type. The same matrix
     appears in include/vev.h:132. This is a good fit for Kvist macro generation from a value-kind table.

  5. The C header and host bindings are handwritten mirrors of the Kvist ABI
     scripts/build_c_abi.sh compiles the Kvist ABI but does not derive include/vev.h:10, and Python/Rust examples duplicate signatures too. Kvist could use
     an ABI metadata macro or sidecar generator to emit C declarations and reduce drift.

  6. Prepared/query value wrappers repeat result-to-value conversion variants
     The q-*value* family in src/vev/edn_text.kvist:2099 onward mostly delegates to the result-returning equivalent plus result-set-value. A macro like def-
     value-wrapper could generate these mechanically.

  7. Aggregate operation dispatch is still string-driven
     src/vev/runtime.kvist:6694 branches on "count", "sum", "avg", "min", "max". Earlier operator tables came up for predicates/functions; aggregates are
     another instance. An enum or generated dispatch table would make this less fragile and easier to share with macro validation.
** Round 9
1. Recursive input-binding helpers leak ownership intent
     src/vev/query_model.kvist:551 recursively calls input-binding-vars and then iterates the returned dynamic array without :defer/delete. src/vev/
     query_model.kvist:517 does the same pattern correctly with :defer. This is a concrete Kvist ownership idiom miss.

  2. Query-step indexing is a hand-written enum/counter matrix
     src/vev/query_model.kvist:98 defines one counter field per step kind, and src/vev/query_model.kvist:110 repeats the same push/increment shape for each
     enum value. This wants either an enum-indexed small array helper or a macro that generates the counter struct and case.

  3. Not-Group / Or-Group constructors repeat ownership boilerplate
     src/vev/query_model.kvist:3, src/vev/query_model.kvist:12, src/vev/query_model.kvist:21, src/vev/query_model.kvist:30, and src/vev/query_model.kvist:37
     are mostly “copy slices into owned dynamic arrays, set has-join.” A small builder/helper would make these less error-prone.

  4. EDN string unescaping builds one-character string parts
     src/vev/query_model.kvist:267 pushes many tiny strings and then concatenates. Same builder point as serialization, but on the parse side: this wants
     strings.Builder or a Kvist string-unescape helper.

  5. Numeric token parsing is split awkwardly
     src/vev/query_model.kvist:236 names edn-int-token?, but it really means “starts like a number,” because floats then flow through src/vev/
     query_model.kvist:324. A single parse-edn-number-token helper returning int/float/symbol would be clearer and less fragile.

  6. Benchmark timing harnesses duplicate almost exactly
     bench/query_rules.kvist:56 and bench/query_rules_stress.kvist:56 carry near-identical timing/sample/profile code. A tiny benchmark helper package would
     reduce drift and make benchmark additions cheaper.

  7. Native ABI benchmark repeats sample-loop structure by hand
     bench/abi_native.kvist:54 creates many sample arrays and then repeats warmup/sample/push/print blocks. This is a good use for a local measure-samples
     helper that takes a workload callback, if Kvist callback ergonomics are acceptable.
** Round 10
1. Clojure prepared-query cache is keyed only by query form
     clients/clojure/src/vev/core.clj:73 and clients/clojure/src/vev/core.clj:216 cache prepared handles globally by query. Prepared handles are tied to a
     native engine/library instance, so this can mix handles across connections/libraries. A source-scoped cache or engine-owned prepare cache would be
     safer.

  2. Empty typed collections are not representable through the Python statement API
     examples/python/vev.py:909 and examples/python/vev.py:937 reject empty tuple/collection/relation bindings because the ABI infers type from values. This
     is a concrete ABI expressiveness gap: Vev needs either typed empty bind calls or a generic typed value builder.

  3. Lookup-ref collection binding only supports string values
     examples/python/vev.py:939 enforces one attr plus string values, because the ABI only exposes the string collection variant. That leaves entity/int/
     keyword lookup-ref collections unsupported even though scalar lookup refs support those value kinds.

  4. Clojure pull lookup refs have the same string-only limitation
     clients/clojure/src/vev/core.clj:424 recognizes lookup refs only when the second item is a string. This is another downstream sign that the C ABI
     should expose typed lookup-ref pull entry points or a generic Value argument path.

  5. Result/value scalar conversion is duplicated in Java because the ABI has two accessor families
     examples/java/Vev.java:258 converts generic vev_value_t; examples/java/Vev.java:289 repeats a partial conversion for result cell accessors. This argues
     for making vev_result_value plus vev_value_* the primary path and de-emphasizing/generated compatibility wrappers for vev_result_value_*.

  6. Python binding dispatch repeats the same homogeneous-type matrix twice
     examples/python/vev.py:904 handles tuple/relation homogeneous arrays, while examples/python/vev.py:935 reimplements collection dispatch. A single ABI-
     level “bind typed sequence” would simplify every host wrapper.

  7. Clojure fast result paths are hardcoded as a nested projection cascade
     clients/clojure/src/vev/core.clj:361 and clients/clojure/src/vev/core.clj:379 try entity-column, entity-int-pair, then entity-string-int-triple before
     falling back. This wants either a small table of projection decoders or a tagged columnar result API so host wrappers do not need to know every
     optimized shape.
** Round 11
1. No public deleter for Value trees
     Value can own dynamic items for vectors/maps at src/vev/core.kvist:24, but recursive cleanup exists only privately in the ABI at src/vev_abi/
     vev_abi.kvist:829. Native Kvist callers need a public delete-owned-value/delete-value-containers API.

  2. No public deleter for Result-Set / Profiled-Result-Set
     Results own nested row.values and row.pulls at src/vev/core.kvist:508. ABI has private delete-result-shallow at src/vev_abi/vev_abi.kvist:1076, while
     benchmarks manually do only (delete profiled.result.rows) at bench/query_rules.kvist:58. That is a sign the public Kvist API is missing a canonical
     cleanup function.

  3. Prepared structs expose owned dynamic fields without matching cleanup APIs
     src/vev/core.kvist:453 through src/vev/core.kvist:487 define prepared query/rules/tx data containers with owned dynamic arrays. Callers get these from
     src/vev/edn_text.kvist:1597, src/vev/edn_text.kvist:2073, and src/vev/edn_text.kvist:3302, but there is no public delete-prepared-*.

  4. Rule/query cleanup is shallow despite nested owned arrays
     Rule, Not-Group, and Or-Group contain nested dynamic arrays at src/vev/core.kvist:283 and src/vev/core.kvist:330. The ABI delete-query-shallow at src/
     vev_abi/vev_abi.kvist:1054 deletes only top-level arrays. This needs a clearly named shallow/deep ownership split.

  5. Tx-Report also needs a public owned cleanup helper
     Reports own db-before, db-after, tx-data, tx-meta, and tempids; see construction at src/vev/runtime.kvist:3305 and src/vev/runtime.kvist:3332. Listener
     delivery clones reports at src/vev/runtime.kvist:154, but there is no obvious public delete-tx-report.

  6. Value {} is used as a silent absent-value sentinel
     Value-Kind defaults to .Nil at src/vev/core.kvist:11, and many failures return (Value {}) false, e.g. src/vev/runtime.kvist:168 and src/vev/
     runtime.kvist:1040. Prefer (value-nil) for intent, or a small none-value helper if the distinction matters.

  7. Tests and benches lean on free_all as a substitute for domain cleanup
     The test suite repeatedly uses free_all context.allocator, and benches do the same at bench/query_rules.kvist:220. That is fine for tests, but it masks
     whether the public Kvist API has enough destructors for real long-running native callers.

 1. ABI transaction listeners use tombstones instead of removal. unlisten sets callback/user to nil but keeps the owned name until connection close, while
     pure Kvist listeners remove the entry outright.
     src/vev_abi/vev_abi.kvist:137, src/vev/runtime.kvist:147

  2. C transaction callbacks are limited by a fixed global 16-slot trampoline table. This looks like a Kvist/ABI expressiveness gap: host callbacks cannot
     become ordinary captured Kvist functions without predeclared wrappers.
     src/vev_abi/vev_abi.kvist:301, src/vev_abi/vev_abi.kvist:999

  3. Registering the same transaction function ident twice appends a second entry and allocates another slot, but lookup returns the first entry. That is
     either a bug or should be explicit duplicate rejection/upsert.
     src/vev_abi/vev_abi.kvist:1305, src/vev/runtime.kvist:3364

  4. Native-Query-Fn is another manual tagged-union shape: one struct with target plus five has-* callback flags. defunion would make invalid combinations
     unrepresentable and simplify dispatch.
     src/vev/core.kvist:188, src/vev/runtime.kvist:715

  5. Native query function registries are linear slices everywhere, with first-match semantics. For named extension points, a map[string]Native-Query-Fn] or
     builder that rejects duplicates would fit better.
     src/vev/runtime.kvist:739, src/vev/runtime.kvist:6679

  6. Text query execution parses into owned Query containers and then returns without an obvious cleanup path. This is a more concrete instance of the
     missing owned-query lifecycle issue.
     src/vev/edn_text.kvist:2237, src/vev/edn_text.kvist:2351

  7. Value-returning query helpers build a full Result-Set, convert it to Value, and do not consume/delete the intermediate result containers. A consuming
     helper or scoped with-result pattern would make this less fragile.
     src/vev/runtime.kvist:8879, src/vev/edn_text.kvist:2107
