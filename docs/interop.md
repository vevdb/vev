# Interop

## Principle

Interop should come after the semantic engine is coherent.

Future server packaging, if it exists, should wrap the same semantic core
rather than force a different internal model.

The right order is:

1. native Odin API
2. stable internal semantic model
3. narrow C ABI
4. host-specific wrappers
5. only later, if justified, server/daemon packaging
6. only later, if justified, transactor/peer-style packaging

## Native API

The native Odin API should be the first-class surface.

It should expose:

- open/create connection
- close
- transact
- get current DB snapshot
- query
- pull

## C ABI

The C ABI should be intentionally narrow.

Good candidates:

- opaque connection handle
- opaque DB snapshot handle or snapshot token
- transact from text or binary payload
- query from text
- pull from text pattern
- results in a simple encoded representation

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

- queries as text
- pull patterns as text
- tx data as text or encoded values
- results as a portable encoded format

This keeps the ABI stable even if internal ASTs evolve.

It also helps preserve a possible future transport boundary if Spor later runs
out of process.

If Spor ever explores a transactor/peer split, these same explicit plain-data
boundaries will still be valuable. They should make the model more plausible
without forcing the project to commit to it today.
