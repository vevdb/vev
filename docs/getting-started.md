# Getting Started

Vev is currently easiest to try through the native library and host clients.

Build the native library and run the available client smoke tests:

```sh
scripts/smoke_clients.sh
scripts/stage_jvm_native.sh
scripts/package_jvm.sh
scripts/smoke_jvm_package.sh
scripts/smoke_python_package.sh
scripts/smoke_node_package.sh
scripts/smoke_go_package.sh
```

Together, these build:

- the platform native library under `build/lib`
- JVM native-resource staging under `build/jvm-native`
- local JVM proof jars under `build/jvm`
- a local JVM Maven-style repository under `build/m2`
- `build/lib/pkgconfig/vev.pc`
- Java classes under `build/examples/java`
- native smoke artifacts under `build/examples/*`

`scripts/smoke_clients.sh` runs the available C, Python, Rust, Go,
Node/TypeScript, Java, Clojure, and Odin smoke clients. The package smoke
scripts then verify the current local JVM, Python, Node, and Go package shapes
from temporary projects/directories.

## Clojure

The Clojure API is the most Datomic-shaped public wrapper today:

```clojure
(require '[vev.core :as d])

(def conn (d/create-conn))

(d/transact! conn
  [{:db/id 1
    :artist/name "John Lennon"}
   {:db/id 2
    :artist/name "Yoko Ono"}])

(def db (d/db conn))

(d/q
  '[:find ?name
    :where [?e :artist/name ?name]]
  db)

(d/pull db [:artist/name] 1)
```

Durability is a separate step:

```clojure
(def durable (d/connect "app.vev.sqlite"))
```

For local development from this repo:

```sh
clojure -M:clj-dev
```

Then open `examples/clojure/getting_started.clj` in an editor and evaluate the
forms inside its `(comment ...)` block one at a time. This local alias uses the
Java loader, which looks for the platform library under `build/lib`. The
intended published Clojure experience is a normal deps.edn dependency that
pulls in and loads the platform native library itself:

```clojure
{:deps {dev.vevdb/vev-clj {:mvn/version "0.1.0"}}}
```

The same shape can be tested locally after `scripts/package_jvm.sh`:

```clojure
{:mvn/local-repo "/path/to/vev/build/m2"
 :deps {dev.vevdb/vev-clj {:mvn/version "0.1.0-SNAPSHOT"}}}
```

`scripts/smoke_jvm_package.sh` automates that local dependency check from a
temporary project.

## Python

The Python client is a pure `ctypes` wrapper today:

```python
import vev

with vev.Library().create_conn() as conn:
    conn.transact('[{:db/id 1 :user/name "Ada"}]')
    print(conn.db().query('[:find ?name :where [?e :user/name ?name]]'))
```

`scripts/smoke_python_package.sh` simulates a future wheel layout by loading a
bundled `native/<platform>/<library>` next to `vev.py`.

## Node/TypeScript

The Node wrapper loads a native N-API addon and exposes a small CommonJS API:

```js
const vev = require("@vevdb/vev");
const conn = vev.openMemory();
conn.transact('[{:db/id 1 :user/name "Ada"}]');
console.log(conn.queryText('[:find ?name :where [?e :user/name ?name]]'));
```

`scripts/smoke_node_package.sh` simulates a future npm package by loading
`native/<platform>/vev_native.node` and the adjacent platform `libvev` from a
temporary package directory.

## Go

The Go wrapper is importable at the planned module path:

```go
import vev "github.com/vevdb/vev/clients/go"
```

`scripts/smoke_go_package.sh` verifies that shape from a temporary Go module
using a local `replace` to this checkout.

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
- Odin: `clients/odin`

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
