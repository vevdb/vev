# Vev Odin

Vev is authored in Kvist and lowers through Odin, but generated Odin is build
output, not the public Odin package surface.

For Odin applications, the supported direction is a small wrapper over the C
ABI:

- link against the platform library under `build/lib`
- import functions matching `include/vev.h`
- manage Vev handles explicitly, mirroring the C ABI ownership rules

`clients/odin/smoke.odin` is the first concrete proof of that direction. It
uses `core:dynlib` to load the Vev native library, opens an in-memory
connection, transacts EDN, runs a Datalog query, and frees returned C ABI
strings.

Run it from the repo root after building the native library:

```sh
scripts/build_c_abi.sh
odin run clients/odin -file
```

Or pass an explicit native library path:

```sh
odin run clients/odin -file -- build/lib/libvev.dylib
```

A fuller Odin package can wrap the dynamic ABI table with Odin-native `Conn`,
`DB`, prepared-query, and result types. Directly depending on generated Odin
should remain a development/debug escape hatch rather than the documented
integration path.

Durable stores are opened through Vev APIs with paths such as `app.vev`. The
current native library depends on the platform SQLite runtime; Odin application
code should not configure SQLite directly.
