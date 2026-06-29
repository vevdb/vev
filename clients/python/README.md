# Vev Python

This is a pure `ctypes` smoke client over Vev's C ABI.

Current local development:

```sh
scripts/build_c_abi.sh
python3 clients/python/smoke.py
```

The adapter loads the native library in this order:

- explicit path passed to `vev.Library`
- `VEV_LIB`
- local repo `build/lib/<platform-library-name>`
- future bundled `native/<platform>/<platform-library-name>` package resource

The bundled-resource path mirrors the JVM native artifact layout and is intended
for future wheels.

`scripts/smoke_python_package.sh` verifies that future shape by copying
`vev.py` and the platform native library to a temporary package-like directory
and importing it without `VEV_LIB`.

Planned package shape:

- a small Python package under the `vev` import name
- explicit native library path support for local development and embedding
- later bundled platform wheels if the API stabilizes enough to justify them

The current API already wraps native handles with context managers for
connections, DB snapshots, prepared queries, statements, transaction reports,
and durable SQLite-backed connections.
