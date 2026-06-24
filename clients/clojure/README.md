# Vev Clojure

This is the first Clojure package layer for Vev. It depends on the JVM wrapper
in `examples/java`, which uses Java 21 Foreign Function & Memory to call the
native Vev shared library.

The public API accepts ordinary Clojure data and serializes it to the same EDN
text frontend used by C, Python, Rust, and Java callers.

```clojure
(require '[vev.core :as vev])

(with-open [conn (vev/create-conn "build/lib/libvev.dylib")]
  (vev/transact! conn
    [{:db/id 1
      :user/name "Ada"
      :user/email "ada@example.com"}])

  (let [db (vev/db conn)]
    (vev/q
      db
      '[:find ?name
        :where [?e :user/name ?name]])))
```

`q` accepts both DB-first Vev style and query-first Datomic/DataScript style:

```clojure
(vev/q db '[:find ?name :where [?e :user/name ?name]])
(vev/q '[:find ?name :where [?e :user/name ?name]] db)
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

Prepared queries are reusable:

```clojure
(with-open [query (vev/prepare conn
                    '[:find ?e ?email
                      :in ?needle
                      :where [?e :user/email ?email]
                             [(= ?email ?needle)]])]
  (vev/q db query "ada@example.com"))
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
(with-open [conn (vev/conn-from-db next-db)]
  (vev/transact! conn [{:db/id 4 :user/name "Dorothy"}])
  (vev/q (vev/db conn) '[:find ?name :where [?e :user/name ?name]]))
```

The current package is deliberately thin:

- `transact!` and `with` return parsed transaction report maps
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
