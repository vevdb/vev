# Next Steps

This is the current Vev execution plan, not a changelog.

## Active Gate

Turn the verified local Vev artifacts into reproducible, publishable packages.
The MusicBrainz workshop and real-application gates are complete in Clojure and
Kvist. The next user-visible milestone is installing Vev through normal host
package mechanisms without repository-local build paths.

## Current Status

`examples/python/contact_book.py` is now the shared application source for
parallel Clojure and Kvist implementations. Both host versions exercise:

- in-memory operation without SQLite
- schema and data transactions
- immutable DB values passed to query and pull
- `db-with` isolation from the original DB value
- durable create, close, reopen, transact, and second reopen

The combined source-tree check is:

```sh
scripts/contact_book.sh
```

`src/vev_app` is the normal Kvist application surface. It presents opaque
`Conn` and immutable `DB` values for both in-memory and durable operation, with
literal `transact`, query-first `q`, `pull`, and `db-with` operations. The
Kvist contact book uses only this surface; storage types and SQLite remain
internal. Owning native handles have explicit close operations, while DB
values can be passed through application code as ordinary values until their
owner closes them.

Compiling a facade package that itself imports Vev exposed a general Kvist
nested-source-package issue. The fix is isolated in Kvist commit `e73ae52` and
passes the full Kvist compiler suite. It must be integrated into Kvist main and
the installed compiler rebuilt before the normal `kvist` command can compile
`src/vev_app`.

The Clojure contact book also passes from a temporary project with only this
dependency:

```clojure
dev.vevdb/vev-clj {:mvn/version "0.1.0-SNAPSHOT"}
```

`scripts/contact_book_package_clojure.sh` builds the local Java, Clojure, and
platform-native artifacts and verifies that dependency-only path. The app does
not use repository source classpaths, `VEV_LIB`, or SQLite APIs.

## Hard Constraints

- In-memory mode remains first-class and must not require SQLite.
- Durable mode may require system SQLite, but application code only sees Vev
  store paths and Vev APIs.
- Clojure and Kvist APIs should remain Datomic-like, including query-first
  `q` calls with the DB value after the query.
- Non-Kvist consumers are primary product surfaces.
- Published coordinates must not be documented as available until publication
  has actually succeeded.
- Performance changes must be generic engine/storage work, never
  application-specific benchmark handling.

## Remaining Work

1. Integrate Kvist commit `e73ae52`, rebuild the installed compiler, and run
   the high-level Kvist contact-book smoke with the normal `kvist` command.
2. Define one release version input and artifact manifest covering the native
   library, C headers, Java, Clojure, Python, Rust, Node, Odin, and the Kvist
   source package. Remove version drift between client manifests and scripts.
3. Make release builds reproducible for each supported OS/architecture. At
   minimum, produce and smoke macOS arm64 first, then add Linux x86-64 before
   calling the release generally available.
4. Publish or stage the native platform artifact first, then Java and Clojure
   artifacts that depend on it. Verify fresh temporary Clojure and Maven
   projects using normal coordinates and no repository-local Maven path.
5. Define Kvist package installation explicitly. Until Kvist has a registry,
   document a stable source dependency/vendor workflow that does not require
   copying arbitrary internal files.
6. Extend the dependency-only application smoke to every packaged host before
   publishing that host as supported.

## Exit Criteria

The packaging gate is done when:

- the installed Kvist compiler builds the normal application API and contact
  book
- one version command builds a manifest of all artifacts for a release
- macOS arm64 and Linux x86-64 native artifacts are reproducible and tested
- Clojure users can add one real published dependency and run the contact book
- Java users can add one real Maven dependency and run the equivalent smoke
- Kvist users have a documented stable package/source dependency path
- package consumers never need repository build paths or SQLite application
  code

## Regression Commands

- `scripts/contact_book.sh`
- `scripts/contact_book_package_clojure.sh`
- `scripts/smoke_jvm_package.sh`
- `scripts/musicbrainz_workshop_setup.sh --validate`
- `scripts/compare_musicbrainz_workshop.sh --skip-datomic`

Performance is not the active workstream. Resume it only for a measured real
application blocker or a regression in an already covered workload, and consult
Datalevin before changing durable query execution.
