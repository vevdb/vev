# Vev Python

This is a pure `ctypes` smoke client over Vev's C ABI.

Current local development:

```sh
scripts/build_c_abi.sh
python3 clients/python/smoke.py
```

The adapter loads the native library from:

```text
build/lib/libvev.dylib
```

or from an explicit path when constructing `vev.Library`.

Planned package shape:

- a small Python package under the `vev` import name
- explicit native library path support for local development and embedding
- later bundled platform wheels if the API stabilizes enough to justify them

The current API already wraps native handles with context managers for
connections, DB snapshots, prepared queries, statements, transaction reports,
and durable SQLite-backed connections.
