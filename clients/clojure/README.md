# Vev Clojure

This is the Clojure package layer for Vev. It depends on the JVM wrapper in
`clients/java`, which uses Java 21 Foreign Function & Memory to call the native
Vev shared library.

The public API accepts ordinary Clojure data and serializes it to the same EDN
text frontend used by C, Python, Rust, and Java callers.

## Intended Published Usage

Normal application setup should be one dependency. The package should pull in
the Java wrapper and the right platform native artifact, then load the native
library itself:

```clojure
{:deps {dev.vevdb/vev-clj {:mvn/version "0.1.0"}}}
```

Application code should not pass Java paths or native library paths around:

```clojure
(require '[vev.core :as d])

(def conn (d/create-conn))

(d/transact! conn
  [{:db/id 1
    :user/name "Ada"
    :user/email "ada@example.com"}])

(def db (d/db conn))

(d/q
  '[:find ?name
    :where [?e :user/name ?name]]
  db)
```

Durable usage should be similarly direct:

```clojure
(def conn (d/connect "app.vev.sqlite"))

(d/transact! conn [{:db/id 1 :user/name "Ada"}])
(d/q '[:find ?name :where [?e :user/name ?name]] (d/db conn))
```

The current repo has not published the Java/Clojure/native artifacts yet, so
local development still has extra setup. The Java loader already supports the
future packaged shape by checking for bundled native resources after explicit
local paths.

## Local Development

Current local development usage is path-based:

```clojure
{:deps {vev/vev-clj {:local/root "clients/clojure"}}}
```

Build the native library and Java classes first:

```sh
scripts/build_c_abi.sh
scripts/stage_jvm_native.sh
scripts/package_jvm.sh
```

`scripts/package_jvm.sh` builds local Java, native-resource, and Clojure jars
under `build/jvm` and a local Maven-style repository under `build/m2`. They are
proof artifacts for the eventual deps.edn/Maven path, not a published release.

That local repository can be consumed from a separate test project:

```clojure
{:mvn/local-repo "/path/to/vev/build/m2"
 :deps {dev.vevdb/vev-clj {:mvn/version "0.1.0-SNAPSHOT"}}
 :aliases {:run {:jvm-opts ["--enable-preview"
                            "--enable-native-access=ALL-UNNAMED"]}}}
```

When developing from the repo root, the root `:clj-dev` alias adds the locally
built Java classes and the required JVM flags:

```sh
clojure -M:clj-dev
```

Then open `examples/clojure/getting_started.clj` and evaluate the forms inside
its `(comment ...)` block one at a time. That command is a repo
smoke/development concern, not the desired public API.
Until native artifacts are packaged, no-arg `create-conn` and one-arg
`connect` resolve the native library from the `vev.library` JVM property, then
`VEV_LIB`, then a local `build/lib` library, then a bundled platform resource.

`q` accepts both DB-first Vev style and query-first Datomic/DataScript style:

```clojure
(d/q db '[:find ?name :where [?e :user/name ?name]])
(d/q '[:find ?name :where [?e :user/name ?name]] db)
```

For Datomic `d/query`-style host code, use `query` with a request map:

```clojure
(d/query
  {:query '[:find ?name
            :in $ ?email
            :where [?e :user/email ?email]
                   [?e :user/name ?name]]
   :args [db "ada@example.com"]})
```

Return-map markers produce Clojure maps:

```clojure
(d/q
  '[:find ?name ?email
    :keys name email
    :where [?e :user/name ?name]
           [?e :user/email ?email]]
  db)
;; => #{{:name "Ada", :email "ada@example.com"}}
```

Inputs are passed as ordinary arguments after the query and DB:

```clojure
(d/q
  db
  '[:find ?name
    :in [?email ...]
    :where [?e :user/email ?email]
           [?e :user/name ?name]]
  ["ada@example.com" "grace@example.com"])
```

Plain `q`/`rows` calls prepare a temporary native query handle and close it after
the call. Use `prepare` when the same query should be reused:

```clojure
(def email-query
  (d/prepare conn
    '[:find ?e ?email
      :in ?needle
      :where [?e :user/email ?email]
             [(= ?email ?needle)]]))

(d/prepared-edn email-query)
(d/q db email-query "ada@example.com")
```

`prepared-edn` also works for reusable pull patterns:

```clojure
(def person-pattern
  (d/prepare-pull-pattern db
    [:user/name {:user/friend [:user/name]}]))

(d/prepared-edn person-pattern)
(d/pull db person-pattern 1)
```

Pull follows the same DB-value shape:

```clojure
(d/pull db
  [:user/name {:user/friend [:user/name]}]
  1)

(d/pull-many db [:user/name] [1 2])
```

Immutable DB values support Datomic/DataScript-style `with` operations:

```clojure
(let [report (d/with db [{:db/id 3 :user/name "Barbara"}])
      next-db (d/db-with db [{:db/id 3 :user/name "Barbara"}])]
  [(:ok report)
   (d/q db '[:find ?e :where [?e :user/name "Barbara"]])
   (d/q next-db '[:find ?e :where [?e :user/name "Barbara"]])])
```

A mutable connection can also be initialized from an immutable DB snapshot:

```clojure
(def next-conn (d/conn-from-db next-db))

(d/transact! next-conn [{:db/id 4 :user/name "Dorothy"}])
(d/q (d/db next-conn) '[:find ?name :where [?e :user/name ?name]])
```

Durable connections use the same transaction and DB-value query shape. The
current backend is SQLite:

```clojure
(def durable (d/connect "app.vev.sqlite"))

(d/connection-info durable)
;; => {:backend :sqlite, :path "app.vev.sqlite", :basis-t 0, :tx-count 0, :tx-ids []}

(d/transact! durable [{:db/id 1 :user/name "Ada"}])
(d/q (d/db durable) '[:find ?name :where [?e :user/name ?name]])
```

The wrapper has JVM cleanup fallback for native handles, so examples use normal
Clojure values. In long-running processes or tight loops that create many
connections, DB snapshots, prepared queries, or pull patterns, call `.close`
explicitly when a handle is no longer needed.

The current package is deliberately thin:

- `transact!` and `with` return transaction report maps from typed native report handles
- `transact-text!` and `with-text` return raw EDN report strings
- `q` returns a set of row vectors
- `rows` returns an ordered vector of row vectors
- entity ids are converted to integers
- pull maps are converted to Clojure maps
- immutable DB snapshots are passable values with JVM cleaner fallback; they
  still implement `AutoCloseable` for explicit cleanup in tight loops

Run through the top-level ABI smoke script:

```sh
scripts/build_c_abi.sh
```
