# Runtime Dependencies

Vev applications use Vev APIs and Vev store files. They do not set up SQLite
schemas, run SQLite migrations, or issue SQL.

The current local durable backend is implemented on top of SQLite. Today the
native Vev library links to the platform SQLite library at runtime. On macOS
this is normally already present. On Linux and other deployment targets, the
runtime image needs a compatible SQLite shared library installed unless the Vev
package for that target is later changed to bundle or statically link SQLite.

This is intentionally similar to using Datomic with SQLite as the durable
backend: SQLite is part of the storage setup, but application code still talks
to Datomic/Vev, not to SQLite directly.

## What Users Install

For the current local build:

- the Vev native library, such as `libvev.dylib` or `libvev.so`
- the host wrapper for the language being used
- a system SQLite runtime library
- for Java/Clojure, Java 21 with the required FFM flags while the wrapper uses
  preview FFM

For a published package, the intended shape is:

- package managers install the host wrapper and Vev native library
- package docs state whether SQLite is expected from the system or bundled in
  the native artifact
- application code opens a Vev store with `connect`, for example `app.vev`

## Checking The Native Library

On macOS, inspect the linked runtime libraries with:

```sh
otool -L build/lib/libvev.dylib
```

On Linux:

```sh
ldd build/lib/libvev.so
```

If SQLite is dynamically linked, these commands should show a SQLite library.

## Store Paths

Use ordinary file paths for local durable stores:

```text
app.vev
data/app.vev
```

The file currently contains SQLite-managed storage internally, but the file is
a Vev store from the application's point of view. The extension is not
semantically important; `.vev` is the recommended convention for examples and
application docs.

## Client Notes

C and Odin users link or dynamically load `libvev` and therefore need both
`libvev` and the SQLite runtime library available to the process.

Python loads `libvev` through `ctypes`. The package can locate an explicit
library path, `VEV_LIB`, a repo-local `build/lib`, or a future bundled native
resource. SQLite is still a native dependency of `libvev` unless that package
bundles it.

Rust and Go link through the C ABI. Their build/package setup must make
`libvev` available and ensure SQLite can be found by the dynamic linker.

Java and Clojure use the Java FFM wrapper. The Java loader can use an explicit
library path, `VEV_LIB`, a repo-local `build/lib`, or a bundled classpath
native resource. The loaded Vev native library still needs SQLite available
unless the native artifact bundles it.

Node loads a native addon which loads/links `libvev`. A package can bundle the
addon and `libvev` side by side, but SQLite remains a runtime dependency of
`libvev` unless bundled into that native artifact.

## Future Packaging Decision

The acceptable baseline is a documented system SQLite dependency. The more
polished distribution option is to bundle or statically link SQLite per
platform so a language package can be installed without a separate OS package.

That packaging decision is independent from the public API. Either way, users
should write:

```clojure
(def conn (d/connect "app.vev"))
```

not SQLite setup code.
