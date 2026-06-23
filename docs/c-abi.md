# C ABI

Vev now has an initial C ABI surface for non-Kvist consumers.

The current shape is intentionally narrow:

- opaque in-memory connection handles
- EDN transaction strings
- EDN query strings
- prepared query handles
- rendered result strings owned by the caller

This is the portable baseline for Python, Rust, Java, Clojure, and other hosts.
Host wrappers should build on this surface first instead of mirroring internal
Vev structs.

## Build And Smoke Test

From the repo root:

```sh
scripts/build_c_abi.sh
```

If `kvist` is not on `PATH`, pass it explicitly:

```sh
KVIST_BIN=/path/to/kvist scripts/build_c_abi.sh
```

If the current Kvist compiler needs to be launched from the Kvist repository
for core macro loading, pass that checkout too:

```sh
KVIST_BIN=/path/to/kvist \
KVIST_REPO_DIR=/path/to/kvist-repo \
scripts/build_c_abi.sh
```

The script compiles `src/vev_abi/vev_abi.kvist` to Odin, builds
`build/lib/libvev.dylib`, compiles `examples/c/smoke.c`, and runs the smoke
program.

## Current C Surface

See [vev.h](../include/vev.h).

```c
vev_conn_t conn = vev_conn_open_memory();

const char *tx = vev_transact_edn(
    conn,
    "[{:db/id 1 :user/name \"Ada\"}]");
vev_string_free(tx);

const char *result = vev_query_edn(
    conn,
    "[:find ?e ?name :where [?e :user/name ?name]]");
vev_string_free(result);

vev_prepared_query_t query =
    vev_prepare_query_edn("[:find ?e :where [?e :user/name \"Ada\"]]");
const char *prepared_result = vev_query_prepared(conn, query);
vev_string_free(prepared_result);
vev_prepared_query_free(query);

vev_conn_close(conn);
```

## Ownership

Returned strings from `vev_transact_edn`, `vev_query_edn`, and
`vev_query_prepared` must be released with `vev_string_free`.

Connections are released with `vev_conn_close`. Prepared queries are released
with `vev_prepared_query_free`.

The current parser stores references into transaction and query source text in
some internal structures. The ABI wrappers therefore keep transaction source
strings alive on the connection and prepared-query source strings alive on the
prepared-query handle. This should eventually be replaced by explicit engine
copying or interned immutable values, but the ABI boundary already has the right
handle ownership shape.

## Next ABI Work

The next useful steps are:

- add result handles or streaming callbacks so callers do not have to parse
  rendered text for large result sets
- add explicit error/result status APIs
- add input arguments for prepared queries
- add pull-specific entry points
- add Rust and Python smoke wrappers
- add ABI benchmarks against native Kvist calls
