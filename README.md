# odinlog

An embedded Datalog database written in Odin.

The intended identity is:

- native
- embedded
- local-first
- immutable snapshot reads
- Datomic-flavored Datalog query syntax
- Datomic/DataScript transaction and pull syntax where practical
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
- Datomic/DataScript-compatible syntax at the API boundary wherever practical
- parsed query AST inside the engine
- SQLite first for durable storage
- C ABI later as a packaging boundary
- Clojure/JVM wrapper later on top of the native boundary

## Compatibility rule

Odinlog should preserve Datomic/DataScript syntax and mental model wherever
practical.

That means:

- transaction input should look like Datomic/DataScript transaction data
- query input should look like Datomic/DataScript Datalog data
- pull input should stay close to Datomic/DataScript pull syntax
- transaction metadata should follow the Datomic transaction-context model

Divergence should only happen when native embedding constraints or implementation
clarity require it, not because a new syntax looks nicer in Odin.

## Documents

- [ODIN_NOTES.md](ODIN_NOTES.md)
- [docs/scope.md](docs/scope.md)
- [docs/architecture.md](docs/architecture.md)
- [docs/data-model.md](docs/data-model.md)
- [docs/indexes.md](docs/indexes.md)
- [docs/transactions.md](docs/transactions.md)
- [docs/query-model.md](docs/query-model.md)
- [docs/pull-model.md](docs/pull-model.md)
- [docs/interop.md](docs/interop.md)
- [docs/roadmap.md](docs/roadmap.md)
