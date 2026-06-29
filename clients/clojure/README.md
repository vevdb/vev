# Vev Clojure

This is the first Clojure package layer for Vev. It depends on the JVM wrapper
in `clients/java`, which uses Java 21 Foreign Function & Memory to call the
native Vev shared library.

The public API accepts ordinary Clojure data and serializes it to the same EDN
text frontend used by C, Python, Rust, and Java callers.

Current local development usage is path-based:

```clojure
{:deps {vev/vev-clj {:local/root "clients/clojure"}}}
```

Build the native library and Java classes first:

```sh
scripts/build_c_abi.sh
```

When developing from the repo root, the root `:clj-dev` alias adds the locally
built Java classes and the required JVM flags:

```sh
clojure -M:clj-dev examples/clojure/getting_started.clj build/lib/libvev.dylib
```

The planned published coordinate is:

```clojure
{:deps {dev.vevdb/vev-clj {:mvn/version "0.1.0"}}}
```

The package still expects a locally built native library path today, usually
`build/lib/libvev.dylib`. A later packaged JVM distribution should use
`dev.vevdb/vev-java` plus platform native artifacts.

```clojure
(require '[vev.core :as vev])

(def conn (vev/create-conn "build/lib/libvev.dylib"))

(vev/transact! conn
  [{:db/id 1
    :user/name "Ada"
    :user/email "ada@example.com"}])

(def db (vev/db conn))

(vev/q
  '[:find ?name
    :where [?e :user/name ?name]]
  db)
```

`q` accepts both DB-first Vev style and query-first Datomic/DataScript style:

```clojure
(vev/q db '[:find ?name :where [?e :user/name ?name]])
(vev/q '[:find ?name :where [?e :user/name ?name]] db)
```

For Datomic `d/query`-style host code, use `query` with a request map:

```clojure
(vev/query
  {:query '[:find ?name
            :in $ ?email
            :where [?e :user/email ?email]
                   [?e :user/name ?name]]
   :args [db "ada@example.com"]})
```

Return-map markers produce Clojure maps:

```clojure
(vev/q
  '[:find ?name ?email
    :keys name email
    :where [?e :user/name ?name]
           [?e :user/email ?email]]
  db)
;; => #{{:name "Ada", :email "ada@example.com"}}
```

Inputs are passed as ordinary arguments after the query and DB:

```clojure
(vev/q
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
  (vev/prepare conn
    '[:find ?e ?email
      :in ?needle
      :where [?e :user/email ?email]
             [(= ?email ?needle)]]))

(vev/prepared-edn email-query)
(vev/q db email-query "ada@example.com")
```

`prepared-edn` also works for reusable pull patterns:

```clojure
(def person-pattern
  (vev/prepare-pull-pattern db
    [:user/name {:user/friend [:user/name]}]))

(vev/prepared-edn person-pattern)
(vev/pull db person-pattern 1)
```

Pull follows the same DB-value shape:

```clojure
(vev/pull db
  [:user/name {:user/friend [:user/name]}]
  1)

(vev/pull-many db [:user/name] [1 2])
```

Immutable DB values support Datomic/DataScript-style `with` operations:

```clojure
(let [report (vev/with db [{:db/id 3 :user/name "Barbara"}])
      next-db (vev/db-with db [{:db/id 3 :user/name "Barbara"}])]
  [(:ok report)
   (vev/q db '[:find ?e :where [?e :user/name "Barbara"]])
   (vev/q next-db '[:find ?e :where [?e :user/name "Barbara"]])])
```

A mutable connection can also be initialized from an immutable DB snapshot:

```clojure
(def next-conn (vev/conn-from-db next-db))

(vev/transact! next-conn [{:db/id 4 :user/name "Dorothy"}])
(vev/q (vev/db next-conn) '[:find ?name :where [?e :user/name ?name]])
```

Durable connections use the same transaction and DB-value query shape. The
current backend is SQLite:

```clojure
(def durable (vev/connect "build/lib/libvev.dylib" "app.vev.sqlite"))

(vev/connection-info durable)
;; => {:backend :sqlite, :path "app.vev.sqlite", :basis-t 0, :tx-count 0, :tx-ids []}

(vev/transact! durable [{:db/id 1 :user/name "Ada"}])
(vev/q (vev/db durable) '[:find ?name :where [?e :user/name ?name]])
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
