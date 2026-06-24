# Vev Clojure

This is the first Clojure package layer for Vev. It depends on the JVM wrapper
in `examples/java`, which uses Java 21 Foreign Function & Memory to call the
native Vev shared library.

The public API accepts ordinary Clojure data and serializes it to the same EDN
text frontend used by C, Python, Rust, and Java callers.

```clojure
(require '[vev.core :as vev])

(with-open [conn (vev/open "build/lib/libvev.dylib")]
  (vev/transact! conn
    [{:db/id 1
      :user/name "Ada"
      :user/email "ada@example.com"}])

  (vev/q conn
    '[:find ?name
      :where [?e :user/name ?name]]))
```

Inputs are passed as ordinary arguments after the query:

```clojure
(vev/q conn
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
  (vev/q conn query "ada@example.com"))
```

The current package is deliberately thin:

- transaction reports are still returned as rendered EDN strings
- query rows are converted to Clojure vectors
- entity ids are converted to integers
- pull maps are converted to Clojure maps
- immutable DB snapshots implement `AutoCloseable` and can be queried

Run through the top-level ABI smoke script:

```sh
scripts/build_c_abi.sh
```
