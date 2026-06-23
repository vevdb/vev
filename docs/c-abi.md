# C ABI

Vev now has an initial C ABI surface for non-Kvist consumers.

The current shape is intentionally narrow:

- opaque in-memory connection handles
- immutable DB snapshot handles
- EDN transaction strings
- EDN query strings
- EDN input vectors for `:in` values
- prepared query handles
- reusable statement handles with typed scalar bindings
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

The ABI benchmark compares native Kvist prepared queries against equivalent C
ABI calls:

```sh
bench/compare_abi.sh
```

The output includes both medians and a `c_abi_over_native` ratio for the
boundary overhead. Treat these as smoke signals, not stable performance claims.

Current workloads:

- `prepared-email-input-text`: prepared query plus EDN input text, rendered text
  result
- `prepared-email-result`: prepared query plus EDN input text, typed result
  handle
- `prepared-email-bound-result`: prepared query plus typed statement binding,
  typed result handle
- `db-snapshot`: retaining a DB value through `vev_conn_db`
- `prepared-email-db-result`: immutable DB value plus EDN input text, typed
  result handle
- `prepared-email-db-bound-result`: immutable DB value plus typed statement
  binding, typed result handle

Latest local run:

```text
comparison workload=prepared-email-input-text c_abi_over_native=1.13x
comparison workload=prepared-email-result c_abi_over_native=1.00x
comparison workload=prepared-email-bound-result c_abi_over_native=1.00x
comparison workload=db-snapshot c_abi_over_native=1.06x
comparison workload=prepared-email-db-result c_abi_over_native=1.00x
comparison workload=prepared-email-db-bound-result c_abi_over_native=1.00x
```

Statement bindings avoid parsing EDN input text on each call and currently
measure at native parity in this small lookup benchmark. Snapshot creation is
much more expensive than querying because the current ABI snapshot deep-copies
datoms and owned strings to make the handle independent of the connection
lifetime.

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

vev_stmt_t stmt = vev_stmt_create(query);
vev_stmt_bind_string(stmt, "ada@example.com");
vev_result_t stmt_rows = vev_query_stmt_result(conn, stmt);
vev_result_free(stmt_rows);
vev_stmt_clear(stmt);

vev_stmt_bind_string(stmt, "grace@example.com");
vev_result_t rebound_rows = vev_query_stmt_result(conn, stmt);
vev_result_free(rebound_rows);

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

vev_db_t snapshot = vev_conn_db(conn);
vev_transact_edn(conn, "[{:db/id 2 :user/name \"Grace\"}]");

vev_result_t old_rows =
    vev_query_db_prepared_result_with_inputs(snapshot, query, "[\"ada@example.com\"]");
vev_result_free(old_rows);

vev_result_t old_stmt_rows = vev_query_db_stmt_result(snapshot, stmt);
vev_result_free(old_stmt_rows);

vev_db_release(snapshot);

vev_stmt_free(stmt);
vev_prepared_query_free(query);

vev_conn_close(conn);
```

## Ownership

Returned strings from `vev_transact_edn`, `vev_query_edn`, and
`vev_query_prepared` must be released with `vev_string_free`. The same is true
for strings returned by the input-bearing query functions.

Connections are released with `vev_conn_close`. DB snapshots are released with
`vev_db_release`. Prepared queries are released with `vev_prepared_query_free`.
Statement handles are released with `vev_stmt_free`.

Statement handles borrow their prepared query. Free statements before freeing
the prepared query they were created from. Bound string, keyword, and symbol
values are copied into the statement and released by `vev_stmt_clear` or
`vev_stmt_free`.

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

DB snapshots returned by `vev_conn_db` deep-copy the current DB into an owned
handle. They can be queried after later transactions on the connection, and they
can outlive the connection that produced them.

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

## Statement Bindings

`vev_stmt_t` is the SQLite-like path for repeated scalar inputs:

```c
vev_prepared_query_t query =
    vev_prepare_query_edn("[:find ?name :in ?email :where [?e :user/email ?email] [?e :user/name ?name]]");

vev_stmt_t stmt = vev_stmt_create(query);
vev_stmt_bind_string(stmt, "ada@example.com");

vev_result_t rows = vev_query_stmt_result(conn, stmt);
vev_result_free(rows);

vev_stmt_clear(stmt);
vev_stmt_bind_string(stmt, "grace@example.com");
rows = vev_query_stmt_result(conn, stmt);
vev_result_free(rows);

vev_stmt_free(stmt);
vev_prepared_query_free(query);
```

Current bind functions:

- `vev_stmt_bind_string`
- `vev_stmt_bind_keyword`
- `vev_stmt_bind_symbol`
- `vev_stmt_bind_entity`
- `vev_stmt_bind_int`
- `vev_stmt_bind_bool`

The first statement API intentionally covers scalar bindings. EDN input text
still handles collection, tuple, relation, lookup-ref, source, and pull-pattern
inputs. Those should be added to statement bindings as typed APIs instead of
forcing host wrappers to construct EDN strings.

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

## DB Snapshots

`vev_conn_t` is the mutable root. `vev_db_t` is an immutable retained database
value.

The intended pattern is:

```c
vev_conn_t conn = vev_conn_open_memory();
vev_transact_edn(conn, "[{:db/id 1 :user/name \"Ada\"}]");

vev_db_t db1 = vev_conn_db(conn);

vev_transact_edn(conn, "[{:db/id 2 :user/name \"Grace\"}]");
vev_db_t db2 = vev_conn_db(conn);

// db1 sees only Ada; db2 sees Ada and Grace.
vev_result_t r1 = vev_query_db_prepared_result_with_inputs(db1, query, "[]");
vev_result_t r2 = vev_query_db_prepared_result_with_inputs(db2, query, "[]");

vev_result_free(r1);
vev_result_free(r2);
vev_db_release(db1);
vev_db_release(db2);
vev_conn_close(conn);
```

This is the C ABI version of Vev's Datomic/DataScript-style DB-as-a-value
model.

## Next ABI Work

The next useful steps are:

- add nested value traversal and streaming callbacks so callers do not have to
  render large or nested results as text
- add explicit error/result status APIs
- add typed statement bindings for collection, tuple, relation, lookup-ref,
  source, and pull-pattern inputs
- add pull-specific entry points
- add Rust smoke wrapper
- add broader result-shape benchmarks for nested values, pull results, and
  larger row sets
