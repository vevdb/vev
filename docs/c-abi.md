# C ABI

Vev now has an initial C ABI surface for non-Kvist consumers.

The current shape is intentionally narrow:

- opaque in-memory connection handles
- immutable DB snapshot handles
- EDN transaction strings
- EDN query strings
- EDN input vectors for `:in` values
- prepared query handles
- reusable statement handles with typed scalar and collection bindings
- rendered result strings owned by the caller
- opaque result handles for typed row/value access
- borrowed value handles for nested vector/map/pull traversal
- callback traversal for nested value trees
- callback traversal for typed result rows

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
program, and runs the Python smoke program through the thin
`examples/python/vev.py` adapter. When `rustc` is available, it also compiles
and runs `examples/rust/smoke.rs`. When Java 21 and Clojure are available, it
also compiles the Java Foreign Function & Memory wrapper and runs Java and
Clojure smoke programs against the same shared library.

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
- `with-tx-report-text`: immutable DB value plus EDN transaction text, rendered
  transaction report text
- `with-tx-report-value`: immutable DB value plus EDN transaction text, typed
  transaction report value handle

Latest local run:

```text
comparison workload=prepared-email-input-text c_abi_over_native=1.13x
comparison workload=prepared-email-result c_abi_over_native=1.00x
comparison workload=prepared-email-bound-result c_abi_over_native=1.00x
comparison workload=db-snapshot c_abi_over_native=1.06x
comparison workload=prepared-email-db-result c_abi_over_native=1.00x
comparison workload=prepared-email-db-bound-result c_abi_over_native=1.00x
comparison workload=with-tx-report-text c_abi_over_native=1.00x
comparison workload=with-tx-report-value c_abi_over_native=1.15x
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

vev_tx_report_t tx = vev_transact_edn_report(
    conn,
    "[{:db/id 1 :user/name \"Ada\"}]");
vev_value_t tx_value = vev_tx_report_value(tx);
const char *tx_edn = vev_value_edn(tx_value);
vev_string_free(tx_edn);
vev_tx_report_free(tx);

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

vev_stmt_bind_lookup_ref_string(stmt, ":user/email", "ada@example.com");
vev_result_t lookup_rows = vev_query_stmt_result(conn, stmt);
vev_result_free(lookup_rows);
vev_stmt_clear(stmt);

vev_stmt_bind_string(stmt, "grace@example.com");
vev_result_t rebound_rows = vev_query_stmt_result(conn, stmt);
vev_result_free(rebound_rows);

vev_result_t rows =
    vev_query_prepared_result_with_inputs(conn, query, "[\"ada@example.com\"]");
for (int row = 0; row < vev_result_row_count(rows); row++) {
    int values = vev_result_value_count(rows, row);
    for (int column = 0; column < values; column++) {
        vev_value_t value = vev_result_value(rows, row, column);
        int kind = vev_value_kind(value);
        const char *text = vev_value_edn(value);
        vev_string_free(text);
    }
}
vev_result_free(rows);

vev_prepared_query_t pull_query =
    vev_prepare_query_edn("[:find (pull ?e [:user/name]) :where [?e :user/email ?email]]");
vev_stmt_t pull_stmt = vev_stmt_create(pull_query);
vev_stmt_bind_string(pull_stmt, "ada@example.com");
vev_result_t pull_rows = vev_query_stmt_result(conn, pull_stmt);

vev_value_t pulled = vev_result_pull(pull_rows, 0, 0);
for (int i = 0; i < vev_value_map_count(pulled); i++) {
    vev_value_t key = vev_value_map_key(pulled, i);
    vev_value_t value = vev_value_map_value(pulled, i);
    const char *key_text = vev_value_text(key);
    const char *value_text = vev_value_edn(value);
    vev_string_free(key_text);
    vev_string_free(value_text);
}

// For generic host adapters, nested result and pull values can also be streamed.
// The callback receives borrowed value handles; copy text/EDN if it must outlive
// the owning result or transaction report.
bool ok = vev_value_visit(pulled, visit_value, user_data);

// Result rows can be consumed through callbacks too. Row callbacks preserve row
// boundaries and distinguish scalar find values from pull results.
bool rows_ok = vev_result_visit(rows, visit_row, user_data);

vev_result_free(pull_rows);
vev_stmt_free(pull_stmt);
vev_prepared_query_free(pull_query);

vev_db_t snapshot = vev_conn_db(conn);
vev_db_t retained_snapshot = vev_db_retain(snapshot);
vev_db_release(snapshot);
const char *new_tx = vev_transact_edn(conn, "[{:db/id 2 :user/name \"Grace\"}]");
vev_string_free(new_tx);

vev_result_t old_rows =
    vev_query_db_prepared_result_with_inputs(retained_snapshot, query, "[\"ada@example.com\"]");
vev_result_free(old_rows);

vev_result_t old_stmt_rows = vev_query_db_stmt_result(retained_snapshot, stmt);
vev_result_free(old_stmt_rows);

vev_tx_report_t with_report =
    vev_with_edn_report(retained_snapshot, "[{:db/id 3 :user/name \"Barbara\"}]");
vev_value_t with_value = vev_tx_report_value(with_report);
const char *with_edn = vev_value_edn(with_value);
vev_string_free(with_edn);
vev_tx_report_free(with_report);

vev_db_t next_db =
    vev_db_with_edn(retained_snapshot, "[{:db/id 3 :user/name \"Barbara\"}]");
vev_conn_t derived = vev_conn_from_db(next_db);
const char *derived_report =
    vev_transact_edn(derived, "[{:db/id 4 :user/name \"Dorothy\"}]");
vev_string_free(derived_report);
vev_conn_close(derived);
vev_db_release(next_db);

vev_db_release(retained_snapshot);

vev_stmt_free(stmt);
vev_prepared_query_free(query);

vev_conn_close(conn);
```

## Python Adapter

The raw `ctypes` surface is intentionally hidden behind a small Python adapter
in [vev.py](../examples/python/vev.py). It is still an example, not a packaged
library, but it shows the intended host-language shape:

```python
import vev

with vev.open_memory() as conn:
    conn.transact("""
    [{:db/id 1
      :user/name "Ada"
      :user/email "ada@example.com"}]
    """)

    query = conn.prepare("""
    [:find ?e ?email
     :in ?needle
     :where [?e :user/email ?email]
            [(= ?email ?needle)]]
    """)

    with query.statement() as stmt:
        rows = stmt.bind("ada@example.com").query(conn).rows()
```

Collection inputs are ordinary homogeneous Python lists:

```python
query = conn.prepare("""
[:find ?name
 :in [?email ...]
 :where [?e :user/email ?email]
        [?e :user/name ?name]]
""")

with query.statement() as stmt:
    rows = stmt.bind(["ada@example.com", "grace@example.com"]).rows(conn)
```

Pull results are converted to normal Python dictionaries:

```python
pull = conn.prepare("""
[:find (pull ?e [:user/name {:user/friend [:user/name]}])
 :where [?e :user/name "Ada"]]
""")

user = pull.query(conn).scalar()
name = user[":user/name"]
friend = user[":user/friend"][":user/name"]
```

`vev.Entity` is used for entity ids so Python callers can distinguish Vev
entity values from ordinary integer values. Keywords and symbols currently
convert to their EDN text strings, for example `":user/name"`.

The smoke also exercises DB-value `with`, `db-with`, and `conn-from-db` so the
Python path follows the same immutable snapshot contract as C, Java, Clojure,
and Rust. Transactions can use `transact_report` / `with_report` for typed
report maps, while `transact` / `with_text` remain string helpers.

## Rust Example

[smoke.rs](../examples/rust/smoke.rs) is a direct `rustc`-compiled example, not
a packaged crate yet. It mirrors the intended safe wrapper shape:

- raw FFI declarations stay private to the module
- `Conn`, `DB`, `PreparedQuery`, `Statement`, and `ResultSet` free their handles
  with `Drop`
- statement methods expose typed scalar and collection bindings
- result values are converted into a small Rust `Value` enum
- transaction reports use an owned `TxReport` wrapper with typed `Value`
  traversal

The example covers transactions, rendered EDN query output, prepared statement
bindings, homogeneous collection bindings, pull result traversal, DB snapshots,
querying a snapshot with a statement, immutable `db-with`, and deriving a
connection from a DB value.

## Java And Clojure Examples

[Vev.java](../examples/java/Vev.java) is a small Java 21 Foreign Function &
Memory wrapper over `libvev.dylib`. It is still an example wrapper, not a
published artifact. The wrapper exposes the same core host shape as Python and
Rust:

- `Vev`, `Connection`, `DB`, `PreparedQuery`, `Statement`, and `ResultSet`
  close their native handles explicitly
- transactions and ad hoc queries accept EDN strings
- prepared queries can be reused with EDN input text or typed statement
  bindings
- typed results convert entity ids, scalar values, and pull maps into Java
  values
- immutable DB snapshots can be queried after the connection has advanced

Because it uses the preview FFM API, the examples compile and run with:

```sh
javac --enable-preview --release 21 ...
java --enable-preview --enable-native-access=ALL-UNNAMED ...
```

The Java wrapper exposes `Vev.load(path)` and `createConn()` as the public-ish
example shape. `openMemory()` remains as a low-level compatibility alias for
the underlying ABI operation.

[vev.core](../clients/clojure/src/vev/core.clj) is the first Clojure package
layer on top of that Java wrapper. It accepts ordinary quoted Clojure forms and
serializes them through the same EDN/query path:

```clojure
(require '[vev.core :as vev])

(with-open [conn (vev/create-conn "build/lib/libvev.dylib")]
  (vev/transact! conn
    [{:db/id 1 :user/name "Ada"}])

  (let [db (vev/db conn)]
    (vev/q
      db
      '[:find ?name
        :where [?e :user/name ?name]])))
```

Inputs are ordinary Clojure arguments after the query:

```clojure
(vev/q
  db
  '[:find ?name
    :in [?email ...]
    :where [?e :user/email ?email]
           [?e :user/name ?name]]
  ["ada@example.com" "grace@example.com"])
```

The underlying Java wrapper still exposes EDN strings directly:

```clojure
(.queryText conn
  "[:find ?name
    :in [?email ...]
    :where [?e :user/email ?email]
           [?e :user/name ?name]]"
  "[[\"ada@example.com\" \"grace@example.com\"]]")
```

That string API remains the portable baseline for non-Kvist hosts and mirrors
how SQL libraries start with query text. The Clojure data API is intentionally a
thin host adapter, not a separate engine frontend.

## Ownership

Returned strings from `vev_transact_edn`, `vev_query_edn`, and
`vev_query_prepared` must be released with `vev_string_free`. The same is true
for strings returned by the input-bearing query functions.

Connections are released with `vev_conn_close`. DB snapshots are immutable owned
handles. `vev_conn_db` returns a new owned snapshot handle, `vev_db_retain`
returns another owned handle for the same snapshot value, and every owned DB
handle is released with `vev_db_release`. Prepared queries are released with
`vev_prepared_query_free`. Statement handles are released with `vev_stmt_free`.

Statement handles borrow their prepared query. Free statements before freeing
the prepared query they were created from. Bound string, keyword, and symbol
values are copied into the statement and released by `vev_stmt_clear` or
`vev_stmt_free`.

Result handles from `vev_query_prepared_result_with_inputs` are released with
`vev_result_free`. Strings returned from result accessors such as
`vev_result_error`, `vev_result_value_text`, and `vev_result_value_edn` are
released with `vev_string_free`.

`vev_value_t` handles are borrowed views into an owning `vev_result_t`. Do not
free them, and do not use them after `vev_result_free`. Strings returned from
`vev_value_text` and `vev_value_edn` are owned by the caller and must be released
with `vev_string_free`.

The current parser stores references into transaction and query source text in
some internal structures. The ABI wrappers therefore keep transaction source
strings alive on the connection and prepared-query source strings alive on the
prepared-query handle. This should eventually be replaced by explicit engine
copying or interned immutable values, but the ABI boundary already has the right
handle ownership shape.

DB snapshots returned by `vev_conn_db` copy the current DB into an owned
immutable snapshot storage value. `vev_db_retain` is cheap: it increments the
snapshot storage refcount and returns another owned handle. DB handles can be
queried after later transactions on the connection, and they can outlive the
connection that produced them.

DB values also support immutable transaction operations through the ABI:

```c
vev_tx_report_t report = vev_with_edn_report(db, "[{:db/id 3 :user/name \"Barbara\"}]");
vev_value_t report_value = vev_tx_report_value(report);
/* Inspect report_value before freeing report. */
vev_tx_report_free(report);
vev_db_t next_db = vev_db_with_edn(db, "[{:db/id 3 :user/name \"Barbara\"}]");
vev_conn_t derived = vev_conn_from_db(next_db);
```

`vev_transact_edn_report` and `vev_with_edn_report` return owned report
handles. `vev_tx_report_value` gives a borrowed typed `vev_value_t` map for the
report; release the handle with `vev_tx_report_free`. The older
`vev_transact_edn` and `vev_with_edn` string helpers remain useful for quick
debugging. `vev_db_with_edn` returns a new owned DB handle. The source DB is not
mutated.

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

`vev_stmt_t` is the SQLite-like path for repeated inputs:

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

Collection `:in` bindings use host arrays:

```c
vev_prepared_query_t query =
    vev_prepare_query_edn("[:find ?name :in [?email ...] :where [?e :user/email ?email] [?e :user/name ?name]]");

vev_stmt_t stmt = vev_stmt_create(query);
const char *emails[] = {"ada@example.com", "grace@example.com"};
vev_stmt_bind_string_collection(stmt, emails, 2);

vev_result_t rows = vev_query_stmt_result(conn, stmt);
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
- `vev_stmt_bind_lookup_ref_string`
- `vev_stmt_bind_lookup_ref_keyword`
- `vev_stmt_bind_lookup_ref_entity`
- `vev_stmt_bind_lookup_ref_int`
- `vev_stmt_bind_string_collection`
- `vev_stmt_bind_entity_collection`
- `vev_stmt_bind_int_collection`
- `vev_stmt_bind_bool_collection`
- `vev_stmt_bind_string_tuple`
- `vev_stmt_bind_entity_tuple`
- `vev_stmt_bind_int_tuple`
- `vev_stmt_bind_bool_tuple`
- `vev_stmt_bind_string_relation`
- `vev_stmt_bind_entity_relation`
- `vev_stmt_bind_int_relation`
- `vev_stmt_bind_bool_relation`
- `vev_stmt_bind_lookup_ref_string_collection`

The statement API currently covers scalar bindings and homogeneous collection
bindings, homogeneous tuple bindings, homogeneous relation bindings, and
same-attribute lookup-ref string collections. EDN input text still handles
source and pull-pattern inputs. Those should be added to statement bindings as
typed APIs instead of forcing host wrappers to construct EDN strings.

## Result Handles

The string-returning query helpers are convenient, but host libraries should use
result handles for normal row consumption.

Current result-handle accessors:

- `vev_result_ok`
- `vev_result_error`
- `vev_result_row_count`
- `vev_result_value_count`
- `vev_result_value`
- `vev_result_value_kind`
- `vev_result_value_entity`
- `vev_result_value_int`
- `vev_result_value_bool`
- `vev_result_value_text`
- `vev_result_value_edn`
- `vev_result_pull_count`
- `vev_result_pull`
- `vev_result_visit`

The `vev_result_value_*` scalar accessors are convenience functions for direct
row values. New host wrappers should prefer `vev_result_value` plus the generic
`vev_value_*` traversal functions, because the same path works for nested values
and rendered pull results.

Current value-handle accessors:

- `vev_value_kind`
- `vev_value_entity`
- `vev_value_int`
- `vev_value_float`
- `vev_value_bool`
- `vev_value_text`
- `vev_value_edn`
- `vev_value_item_count`
- `vev_value_item`
- `vev_value_map_count`
- `vev_value_map_key`
- `vev_value_map_value`
- `vev_value_visit`

Pull results are rendered into map-shaped values and stored on the result handle
when the query runs. This keeps pull traversal independent of the original
connection or DB snapshot lifetime.

`vev_value_visit` streams any nested `vev_value_t` tree through a C callback.
It emits `VEV_VALUE_VISIT_VALUE` for every node and `VEV_VALUE_VISIT_END` after
each vector/map container. The callback receives borrowed handles; callers must
copy strings or EDN text they want to retain after the owning result/report is
freed.

`vev_result_visit` streams a typed result handle by row. It emits
`VEV_RESULT_VISIT_ROW_BEGIN`, `VEV_RESULT_VISIT_VALUE`,
`VEV_RESULT_VISIT_PULL`, and `VEV_RESULT_VISIT_ROW_END`. Value and pull events
carry borrowed `vev_value_t` handles with the same lifetime as values returned
by `vev_result_value` and `vev_result_pull`.

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

- add direct query-execution callbacks so callers can consume very large query
  results without first materializing full result handles
- add explicit error/result status APIs
- add typed statement bindings for source and pull-pattern inputs
- add pull-specific entry points
- turn the Rust smoke wrapper into a small crate
- add broader result-shape benchmarks for nested values, pull results, and
  larger row sets
