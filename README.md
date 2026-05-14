# odinlog

An embedded Datalog database written in Odin.

The intended identity is:

- native
- embedded
- local-first
- immutable snapshot reads
- Datomic-flavored Datalog query syntax
- durable storage behind a narrow adapter boundary
- future multi-language consumption through a stable native ABI

This is not a port of DataScript source.
It is a native engine that uses DataScript and Datomic as semantic references.

## Initial direction

The first credible target is:

- open or create a local database
- transact facts
- query with Datomic-flavored Datalog
- pull entities
- close and reopen with state intact

The first implementation should optimize for:

- clear semantics
- simple local deployment
- boring failure modes
- inspectable internals

## Design stance

- engine core in plain Odin
- Datalog syntax as text at the API boundary
- parsed query AST inside the engine
- SQLite first for durable storage
- C ABI later as a packaging boundary
- Clojure/JVM wrapper later on top of the native boundary

## Documents

- [ODIN_NOTES.md](ODIN_NOTES.md)
- [docs/scope.md](docs/scope.md)
- [docs/architecture.md](docs/architecture.md)
- [docs/data-model.md](docs/data-model.md)
- [docs/indexes.md](docs/indexes.md)
- [docs/query-model.md](docs/query-model.md)
- [docs/interop.md](docs/interop.md)
- [docs/roadmap.md](docs/roadmap.md)
