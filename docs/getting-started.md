# Getting Started

Vev is currently easiest to try through the native library and host clients.

Build the native library and run the available client smoke tests:

```sh
scripts/smoke_clients.sh
```

This builds:

- `build/lib/libvev.dylib`
- `build/lib/pkgconfig/vev.pc`
- Java classes under `build/examples/java`
- native smoke artifacts under `build/examples/*`

The same command runs the available C, Python, Rust, Go, Node/TypeScript, Java,
and Clojure smoke clients.

## Clojure

The Clojure API is the most Datomic-shaped public wrapper today:

```clojure
(require '[vev.core :as vev])

(def conn (vev/connect "build/lib/libvev.dylib" "app.vev.sqlite"))

(vev/transact! conn
  [{:db/id 1
    :artist/name "John Lennon"}
   {:db/id 2
    :artist/name "Yoko Ono"}])

(def db (vev/db conn))

(vev/q
  '[:find ?name
    :where [?e :artist/name ?name]]
  db)

(vev/pull db [:artist/name] 1)
```

For local development from this repo:

```sh
clojure -M:clj-dev examples/clojure/getting_started.clj build/lib/libvev.dylib
```

DB snapshots are passable immutable values. The wrapper has JVM cleanup
fallbacks, so examples use normal Clojure binding style. Long-running services
or tight loops that create many connections, prepared queries, or DB snapshots
can still call `.close` explicitly.

## Java

Java uses the Java 21 Foreign Function & Memory wrapper in `clients/java`:

```java
import dev.vevdb.vev.Vev;

Vev vev = Vev.load();
Vev.Connection conn = vev.createConn();
conn.transact("[{:db/id 1 :user/name \"Ada\"}]");
Vev.DB db = conn.db();
System.out.println(vev.queryRows(java.util.Map.of(
    "query", "[:find ?name :where [?e :user/name ?name]]",
    "args", java.util.List.of(db))));
```

Local Java runs need Java 21 preview FFM flags:

```sh
--enable-preview --enable-native-access=ALL-UNNAMED
```

Planned Maven coordinate: `dev.vevdb:vev-java`.

## C

The raw C ABI is the stable lowest-level integration surface:

```sh
PKG_CONFIG_PATH="$PWD/build/lib/pkgconfig" \
  clang clients/c/smoke.c $(pkg-config --cflags --libs vev) \
  -Wl,-rpath,"$PWD/build/lib" \
  -o build/examples/c/vev_c_smoke
```

Use `include/vev.h` for the current handle and ownership rules.

## Other Clients

Current smoke clients:

- Python: `clients/python`
- Rust: `clients/rust`
- Go: `clients/go`
- Node/TypeScript: `clients/node`
- Odin: `clients/odin` documents the planned C ABI wrapper direction

These are not polished published packages yet. They are kept under `clients/*`
so the public host-language story can evolve in place while the engine and ABI
stabilize.

## Current Limitations

- Vev is still pre-production.
- Durable SQLite storage works, but the next storage milestone is shared
  immutable/chunked index storage rather than whole DB/index copying.
- The C ABI is the portability boundary; higher-level clients are still being
  shaped around it.
- Exact parser diagnostic compatibility is still Vev-shaped in some cases.
