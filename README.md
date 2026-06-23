# Vev

**A native, embedded Datalog database for immutable database values.**

Vev weaves immutable facts into a durable fabric of attributed entities and values. Facts accumulate through append-only transactions, producing immutable database snapshots that applications can query declaratively and pass around as ordinary values.

The project follows Datomic/DataScript semantics as a practical compatibility target. If you are familiar with Datomic or DataScript, their query, transaction, and pull tutorials provide the most direct path to understanding Vev's programming model.

Vev is not trying to be a Datomic clone, a SQL database, or a database server first. Its central bet is that immutable database values, flexible facts, and Datalog-as-data are useful well beyond the Clojure ecosystem when packaged as a small, embedded native library.

## Identity

Vev is designed around a few core principles:

* Native and embedded by default.
* Embedded-first, not embedded-only.
* Immutable snapshot reads.
* Datomic-flavored Datalog queries.
* Datomic/DataScript transaction and pull syntax where practical.
* Durable storage behind a narrow adapter boundary.
* Distributed as a native library for consumption from multiple host languages.
* Supported by a CLI for inspection, import/export, and operational tooling.
* Future-friendly through a stable native ABI.
* Kvist-first implementation with readable Odin output.

The goal is to make the database feel like an ordinary part of the host program rather than an external service that everything revolves around.

## Thesis

Vev exists to make immutable database values a normal programming model outside the Clojure ecosystem.

The core idea is simple:

* Information is represented as facts.
* Facts accumulate over time.
* Reads happen against immutable database values.
* Writes produce new database values.
* Application logic can treat the database as ordinary data.

The irreducible core is:

* Datoms
* Transactions
* Immutable database snapshots
* Datalog-as-data

Everything else is optional, replaceable, or can arrive later:

* Durable storage engines
* File formats
* SQL views
* Full-text search
* Vector indexes
* Synchronization
* Server packaging
* Hosted services

Vev should compromise on implementation technique before compromising on the database-as-value model.

## Design Filter

The target experience is Datomic's ideas with SQLite's accessibility.

That means preserving facts, time, and immutable snapshots as the semantic center while keeping deployment simple and unsurprising. The embedded path should remain small, portable, and easy to adopt. Kvist should be an implementation advantage, not an adoption requirement.

Examples and documentation should make the programming model obvious as quickly as possible. Features that obscure the core abstraction, expand the surface area unnecessarily, or turn the project into a kitchen sink should be treated with skepticism.

When evaluating a feature, ask:

> Does this make immutable database values easier to understand, use, preserve, or distribute?

If not, it probably belongs outside the core.
