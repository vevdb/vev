# Interop

## Principle

Interop should come after the semantic engine is coherent.

Vev's primary source language is Kvist, with readable Odin output as a quality
gate. Kvist users may consume Vev as a source package when that is the most
direct integration path.

For other native platforms and host languages, the primary integration artifact
should be a native library with a narrow C ABI. The CLI binary is for tooling,
inspection, import/export, smoke tests, and operational workflows; it is not the
main application integration model.

Future server packaging, if it exists, should wrap the same semantic core
rather than force a different internal model.

The right order is:

1. native Kvist API
2. stable internal semantic model
3. native library packaging
4. narrow C ABI
5. CLI binary over the same engine
6. host-specific wrappers
7. only later, if justified, server/daemon packaging
8. only later, if justified, transactor/peer-style packaging

## Native API

The native Kvist API should be the first-class source-level surface.

It should expose:

- open/create connection
- close
- transact
- get current DB snapshot
- query
- pull

Kvist callers should get the best syntax Vev can offer: literal query,
transaction, and pull forms that lower directly to the same typed internal
structures used by every other frontend.

## Query Frontends

Vev should have one engine with multiple front doors:

- Kvist literal macros for Kvist applications
- EDN string APIs for C/Odin/other host languages
- prepared queries for repeated execution

These frontends must converge early into the same internal `Query`, tx data,
and pull representations. Strings should not have separate semantics from
Kvist literals.

The EDN string API is required for broad consumption. It is the portable
baseline: every host can pass text through a narrow ABI, queries can be logged
and stored, and Datomic/DataScript examples stay recognizable.

Prepared queries are the production form of the same idea:

```text
vev_prepare_query(conn, "[:find ?e :where [?e :name ?name]]") -> query_handle
vev_query_prepared(db, query_handle, inputs) -> result
```

A parse-and-run helper is still useful:

```text
vev_query_text(db, "[:find ?e :where [?e :name \"Ivan\"]]", inputs) -> result
```

Direct host-side AST builders should wait until a real caller proves they are
needed. A large struct-building ABI is more surface area than Vev needs now.

## Native library and CLI

The native library should be the primary binary artifact for non-Kvist
consumers. It can link SQLite internally or depend on a platform SQLite library,
but SQLite should remain an implementation detail behind Vev's storage adapter.

The CLI binary should stay thin:

- inspect local databases
- run smoke tests
- import/export data
- support debugging and operational workflows
- exercise the same engine path as embedded users

The CLI should not become the only supported way to use Vev from applications.

## C ABI

The C ABI should be intentionally narrow.

Good candidates:

- opaque connection handle
- opaque DB snapshot handle or snapshot token
- transact from text or binary payload
- query from text
- prepared query handles
- pull from text pattern
- results in a simple encoded representation
- release/free functions for every owned handle/result

Avoid exposing:

- internal structs directly
- storage handles
- backend-specific details
- pointer ownership rules that leak deep engine internals

## Clojure/JVM future

If future Clojure consumption matters, the easiest path is likely:

- native engine
- C ABI or stable native shared library boundary
- thin JVM wrapper
- Clojure library on top of the JVM wrapper

That is easier to reason about than making Java/JNI the engine architecture
from day one.

## Java-side options later

Likely candidates:

- Java Foreign Function & Memory API wrapper
- JNI wrapper if needed for compatibility
- a small Java library exposing a friendlier object API
- a Clojure namespace layer on top of that Java library

The important point is:

- Clojure should consume a wrapper
- the engine should not become JVM-shaped internally

## Public data shape

For foreign consumers, the safest boundary is:

- queries as EDN text, plus prepared query handles
- pull patterns as EDN text, plus prepared pattern handles if needed
- tx data as EDN text or encoded values
- results as a portable encoded format

This keeps the ABI stable even if internal ASTs evolve.

It also helps preserve a possible future transport boundary if Vev later runs
out of process.

If Vev ever explores a transactor/peer split, these same explicit plain-data
boundaries will still be valuable. They should make the model more plausible
without forcing the project to commit to it today.
