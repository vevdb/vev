# vev

An embeddable Datalog database.

Vev weaves immutable facts into a durable fabric of attributed entities and
values, with append-only growth and declarative, time-aware querying.

It follows Datomic/DataScript semantics end-to-end, and Datomic/DataScript
syntax tutorials should be followed for the most direct path to learning its
query and transaction model.

The intended identity is:

- native
- embedded
- embedded-first, not embedded-only
- local-first
- immutable snapshot reads
- Datomic-flavored Datalog query syntax
- Datomic/DataScript transaction and pull syntax where practical
- durable storage behind a narrow adapter boundary
- future multi-language consumption through a stable native ABI

Vev follows Datomic/DataScript semantics as a practical compatibility target.

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
- functional semantics at the boundary, local mutation allowed in implementation
- semantic boundaries should stay transportable as plain data
- SQLite first for durable storage
- C ABI later as a packaging boundary
- Clojure/JVM wrapper later on top of the native boundary

## Compatibility rule

Vev should preserve Datomic/DataScript syntax and mental model wherever
practical.

If you are learning Vev's Datalog/query model, start with Datomic/DataScript
syntax tutorials and apply that model directly.

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
- [docs/datomic-syntax.md](docs/datomic-syntax.md)
- [docs/transactions.md](docs/transactions.md)
- [docs/query-model.md](docs/query-model.md)
- [docs/pull-model.md](docs/pull-model.md)
- [docs/interop.md](docs/interop.md)
- [docs/roadmap.md](docs/roadmap.md)
