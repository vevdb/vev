# C ABI

Vev now has an initial C ABI surface for non-Kvist consumers.

The current shape is intentionally narrow:

- opaque in-memory connection handles
- EDN transaction strings
- EDN query strings
- EDN input vectors for `:in` values
- prepared query handles
- rendered result strings owned by the caller
- opaque result handles for typed row/value access

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
`build/lib/libvev.dylib`, compiles `examples/c/smoke.c`, runs the C smoke
program, and runs the Python ctypes smoke program.

## ABI Benchmark

The ABI benchmark compares a native Kvist prepared query using EDN input text
against the C ABI prepared query using the same EDN input text:

```sh
bench/compare_abi.sh
```

The output includes both medians and a `c_abi_over_native` ratio for the
boundary overhead. On the initial prepared email lookup workload, the C ABI path
has been around `1.13x` the native Kvist path on this machine when rendering
text results. Treat that as a smoke signal, not a stable performance claim.

The benchmark also measures `prepared-email-result`, which returns an opaque
result handle instead of rendering the whole result as text. On the same
machine, that path measured around `1.00x` native for the initial prepared email
lookup workload.

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

const char *with_inputs = vev_query_edn_with_inputs(
    conn,
    "[:find ?name :in ?email :where [?e :user/name ?name] [?e :user/email ?email]]",
    "[\"ada@example.com\"]");
vev_string_free(with_inputs);

vev_prepared_query_t query =
    vev_prepare_query_edn("[:find ?e :in ?email :where [?e :user/email ?email]]");
const char *prepared_result =
    vev_query_prepared_with_inputs(conn, query, "[\"ada@example.com\"]");
vev_string_free(prepared_result);

vev_result_t rows =
    vev_query_prepared_result_with_inputs(conn, query, "[\"ada@example.com\"]");
for (int row = 0; row < vev_result_row_count(rows); row++) {
    int values = vev_result_value_count(rows, row);
    for (int column = 0; column < values; column++) {
        int kind = vev_result_value_kind(rows, row, column);
        const char *text = vev_result_value_edn(rows, row, column);
        vev_string_free(text);
    }
}
vev_result_free(rows);

vev_prepared_query_free(query);

vev_conn_close(conn);
```

## Ownership

Returned strings from `vev_transact_edn`, `vev_query_edn`, and
`vev_query_prepared` must be released with `vev_string_free`. The same is true
for strings returned by the input-bearing query functions.

Connections are released with `vev_conn_close`. Prepared queries are released
with `vev_prepared_query_free`.

Result handles from `vev_query_prepared_result_with_inputs` are released with
`vev_result_free`. Strings returned from result accessors such as
`vev_result_error`, `vev_result_value_text`, and `vev_result_value_edn` are
released with `vev_string_free`.

The current parser stores references into transaction and query source text in
some internal structures. The ABI wrappers therefore keep transaction source
strings alive on the connection and prepared-query source strings alive on the
prepared-query handle. This should eventually be replaced by explicit engine
copying or interned immutable values, but the ABI boundary already has the right
handle ownership shape.

## Query Inputs

The input-bearing functions take an EDN vector whose elements correspond to the
query's non-database `:in` bindings.

Examples:

```c
vev_query_edn_with_inputs(
    conn,
    "[:find ?name :in ?email :where [?e :user/email ?email] [?e :user/name ?name]]",
    "[\"ada@example.com\"]");

vev_query_edn_with_inputs(
    conn,
    "[:find ?name :in [?email ...] :where [?e :user/email ?email] [?e :user/name ?name]]",
    "[[\"ada@example.com\" \"grace@example.com\"]]");

vev_query_edn_with_inputs(
    conn,
    "[:find ?e :in [?name ?email] :where [?e :user/name ?name] [?e :user/email ?email]]",
    "[[\"Ada\" \"ada@example.com\"]]");

vev_query_edn_with_inputs(
    conn,
    "[:find ?name :in [[?email ?label]] :where [?e :user/email ?email] [?e :user/name ?name]]",
    "[[[\"ada@example.com\" :primary]]]");
```

The wrapper parses the query first and uses the query's `:in` shape to coerce
each EDN input value into Vev's internal scalar, collection, tuple, or relation
input representation.

## Result Handles

The string-returning query helpers are convenient, but host libraries should use
result handles for normal row consumption.

Current result-handle accessors:

- `vev_result_ok`
- `vev_result_error`
- `vev_result_row_count`
- `vev_result_value_count`
- `vev_result_value_kind`
- `vev_result_value_entity`
- `vev_result_value_int`
- `vev_result_value_bool`
- `vev_result_value_text`
- `vev_result_value_edn`

The first handle API intentionally starts with row values only. Pull results,
aggregates that produce nested values, and efficient vector/map traversal should
grow out of the same handle shape rather than adding a parallel API.

## Next ABI Work

The next useful steps are:

- add nested value traversal and streaming callbacks so callers do not have to
  render large or nested results as text
- add explicit error/result status APIs
- add pull-specific entry points
- add Rust smoke wrapper
- add broader result-shape benchmarks for nested values, pull results, and
  larger row sets
