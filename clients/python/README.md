# Vev Python

This is a pure `ctypes` client over Vev's C ABI.

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

`scripts/smoke_python_package.sh` verifies that future shape by checking the
package metadata, copying `vev.py` and the platform native library to a
temporary package-like directory, and importing it without `VEV_LIB`.

Planned package shape:

- a small Python package under the `vev` import name
- project metadata in `clients/python/pyproject.toml` for the future `vev`
  wheel
- explicit native library path support for local development and embedding
- later bundled platform wheels if the API stabilizes enough to justify them

Basic usage:

```python
import vev

with vev.create_conn() as conn:
    conn.transact('[{:db/id 1 :user/name "Ada"}]')
    print(conn.query_text('[:find ?name :where [?e :user/name ?name]]'))

with vev.connect("app.vev") as conn:
    conn.transact('[{:db/id 1 :user/name "Durable Ada"}]')
```

For an explicit native library path, use a library instance:

```python
lib = vev.Library("/path/to/libvev.dylib")
with lib.create_conn() as conn:
    ...
```

Parser tooling can inspect a single where clause with
`vev.parse_clause_edn('[?e :user/name ?name]')`.

`open_memory()` remains as a compatibility alias for `create_conn()`.

The current API already wraps native handles with context managers for
connections, DB snapshots, prepared queries, statements, transaction reports,
and durable Vev connections.

Durable stores are opened through Vev APIs with paths such as `app.vev`. The
Python package loads `libvev`; the current native library depends on the
platform SQLite runtime, but Python application code does not set up SQLite.
