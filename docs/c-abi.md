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
- owned value handles for direct pull entry points
- prepared pull-pattern handles for direct pull and pull-many
- explicit prepared-query and statement error accessors
- callback traversal for nested value trees
- callback traversal for typed result rows
- direct statement query execution through typed result-row callbacks
- registered transaction function callbacks that return EDN tx-data
- transaction report listener callbacks on connection commits

This is the portable baseline for Python, Rust, Java, Clojure, Go,
Node/TypeScript, and other hosts. Host wrappers should build on this surface
first instead of mirroring internal Vev structs.

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
`examples/python/vev.py` adapter. When the relevant toolchains are available,
it also compiles and runs the Rust, Go, Node/TypeScript, Java, and Clojure
smoke programs against the same shared library.

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
- `many-row-result`: 300-row typed result handle materialization
- `many-row-scan`: 300-row direct statement visitor scan
- `pull-many-nested`: direct pull-many over nested pull-shaped values
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
comparison workload=prepared-email-bound-result c_abi_over_native=0.92x
comparison workload=many-row-result c_abi_over_native=0.98x
comparison workload=many-row-scan c_abi_over_native=0.98x
comparison workload=pull-many-nested c_abi_over_native=1.02x
comparison workload=db-snapshot c_abi_over_native=1.03x
comparison workload=prepared-email-db-result c_abi_over_native=1.00x
comparison workload=prepared-email-db-bound-result c_abi_over_native=0.92x
comparison workload=with-tx-report-text c_abi_over_native=0.95x
comparison workload=with-tx-report-value c_abi_over_native=1.07x
```

Statement bindings avoid parsing EDN input text on each call and currently
measure at native parity in this small lookup benchmark. Snapshot creation is
much more expensive than querying because the current ABI snapshot deep-copies
datoms and owned strings to make the handle independent of the connection
lifetime. Direct statement visitors currently measure at native parity on the
300-row scan workload, and nested direct pull-many is close to native.

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
if (!vev_prepared_query_ok(query)) {
    const char *error = vev_prepared_query_error(query);
    vev_string_free(error);
}
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

// Statements can also execute directly into the same visitor protocol. This is
// the preferred shape for large result sets when the host does not need random
// row access through a materialized result handle.
bool streamed_ok = vev_query_stmt_visit(conn, stmt, visit_row, user_data);
if (!streamed_ok) {
    const char *error = vev_stmt_error(stmt);
    vev_string_free(error);
}

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
bool old_streamed_ok =
    vev_query_db_stmt_visit(retained_snapshot, stmt, visit_row, user_data);

vev_value_handle_t direct_pull =
    vev_pull_edn(retained_snapshot, "[:user/name]", 1);
vev_value_t pull_value = vev_value_handle_value(direct_pull);
const char *pull_edn = vev_value_handle_edn(direct_pull);
vev_string_free(pull_edn);
vev_value_handle_free(direct_pull);

vev_value_handle_t lookup_pull =
    vev_pull_lookup_ref_string_edn(
        retained_snapshot,
        "[:user/name]",
        ":user/email",
        "ada@example.com");
vev_value_handle_free(lookup_pull);

vev_value_handle_t keyword_lookup_pull =
    vev_pull_lookup_ref_keyword_edn(
        retained_snapshot,
        "[:user/name]",
        ":user/status",
        ":active");
vev_value_handle_free(keyword_lookup_pull);

vev_prepared_pull_pattern_t pull_pattern =
    vev_prepare_pull_pattern_edn("[:user/name]");
vev_value_handle_t prepared_lookup_pull =
    vev_pull_lookup_ref_string_prepared(
        retained_snapshot,
        pull_pattern,
        ":user/email",
        "ada@example.com");
vev_value_handle_free(prepared_lookup_pull);
vev_prepared_pull_pattern_free(pull_pattern);

unsigned long long entity_ids[] = {1, 2};
vev_value_handle_t many_pull =
    vev_pull_many_edn(retained_snapshot, "[:user/name]", entity_ids, 2);
vev_value_handle_free(many_pull);

vev_prepared_pull_pattern_t many_pattern =
    vev_prepare_pull_pattern_edn("[:artist/name]");
const char *artist_gids[] = {
    "9974da98-8338-3cff-8a28-11b70c224c5b",
    "65f4f0c5-ef9e-490c-aee3-909e7ae6b2ab",
};
vev_value_handle_t many_lookup_pull =
    vev_pull_many_lookup_ref_uuid_prepared(
        retained_snapshot,
        many_pattern,
        ":artist/gid",
        artist_gids,
        2);
vev_value_handle_free(many_lookup_pull);
vev_prepared_pull_pattern_free(many_pattern);

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

Durable connections use a separate opaque handle. The current backend is
SQLite, selected by a plain filesystem path or `sqlite://...` URI:

```c
vev_prepared_query_t durable_query =
    vev_prepare_query_edn("[:find ?e ?email :where [?e :user/email ?email]]");

vev_connection_t durable = vev_connect("app.vev.sqlite");
if (!vev_connection_ok(durable)) {
    const char *error = vev_connection_error(durable);
    vev_string_free(error);
}

const char *backend = vev_connection_backend(durable);
const char *path = vev_connection_path(durable);
unsigned long long basis_t = vev_connection_basis_t(durable);
unsigned long long tx_count = vev_connection_tx_count(durable);
vev_u64_array_t tx_ids = vev_connection_tx_ids(durable);
const char *info = vev_connection_info_edn(durable);
vev_u64_array_free(tx_ids);
vev_string_free(backend);
vev_string_free(path);
vev_string_free(info);

vev_tx_report_t durable_tx = vev_connection_transact_edn_report(
    durable,
    "[{:db/id 1 :user/name \"Ada\" :user/email \"ada@example.com\"}]");
vev_tx_report_free(durable_tx);

vev_db_t durable_db = vev_connection_db(durable);
vev_result_t durable_rows =
    vev_query_db_prepared_result_with_inputs(durable_db, durable_query, "[]");
vev_result_free(durable_rows);
vev_db_release(durable_db);
vev_connection_close(durable);
vev_prepared_query_free(durable_query);
```

The durable handle appends successful transaction datoms and tx metadata rows
to SQLite before returning. DB snapshots from `vev_connection_db` follow the
same immutable owned-handle contract as `vev_conn_db`. The backend-specific
`vev_sqlite_conn_*` functions remain available for storage tests and migration,
but new host APIs should prefer `vev_connect` / `vev_connection_*`.
`vev_connection_backend` and `vev_connection_path` return owned diagnostic
strings; callers free them with `vev_string_free`. `vev_connection_basis_t`
returns the Datomic-style basis transaction visible to the connection.
`vev_connection_tx_count` returns the persisted transaction-log row count.
`vev_connection_tx_ids` returns the persisted transaction ids as an owned
`vev_u64_array_t`; callers free it with `vev_u64_array_free`.
`vev_connection_info_edn` returns the same metadata as one owned EDN map string
for simple C tooling and logging.

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

Durable connections use the same DB-value query path:

```python
with vev.connect("app.vev.sqlite") as durable:
    assert durable.backend() == "sqlite"
    assert durable.basis_t() == 0
    assert durable.tx_count() == 0
    info = durable.info_edn()
    durable.transact_report(
        '[{:db/id 1 :user/name "Ada" :user/email "ada@example.com"}]')
    with durable.db() as db:
        rows = query.rows(db)
```

The smoke also exercises DB-value `with`, `db-with`, and `conn-from-db` so the
Python path follows the same immutable snapshot contract as C, Java, Clojure,
and Rust. Transactions can use `transact_report` / `with_report` for typed
report maps, while `transact` / `with_text` remain string helpers. Prepared
queries expose `edn()` for a portable parser description, and bound statements
can return generic typed column batches without materializing result rows.

## Rust Example

[examples/rust](../examples/rust) is a small Cargo package. It mirrors the
intended safe wrapper shape:

- raw FFI declarations stay private to the module
- `Conn`, `DB`, `PreparedQuery`, `Statement`, and `ResultSet` free their handles
  with `Drop`
- statement methods expose typed scalar and collection bindings
- prepared queries and bound statements can return generic typed column batches
- `PreparedQuery::edn()` returns the portable parser description exposed by the
  C ABI
- result values are converted into a small Rust `Value` enum
- transaction reports use an owned `TxReport` wrapper with typed `Value`
  traversal

The example covers transactions, rendered EDN query output, prepared statement
bindings, homogeneous collection bindings, pull-pattern statement bindings,
pull result traversal, direct pull APIs, DB snapshots, querying a snapshot with
a statement, immutable `db-with`, deriving a connection from a DB value, and
SQLite-backed open/write/close/reopen/query.

The Go and Node smoke wrappers follow the same adapter contract: prepared
query handles are ordinary host objects, can be inspected through
`PreparedQuery.EDN()` / `PreparedQuery.edn()`, and can run against either a live
connection or retained immutable DB snapshot.

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
- `PreparedQuery.edn()` returns the portable parser description exposed by the
  C ABI
- typed results convert entity ids, scalar values, and pull maps into Java
  values
- `DB.queryColumns` exposes a first generic Java column facade for prepared
  queries and bound statements on immutable DB snapshots, over the current
  optimized entity, string, entity/int, and entity/string/int result shapes
- immutable DB snapshots can be queried after the connection has advanced
- `Vev.query(Map.of("query", ..., "args", List.of(db, ...)))` provides a
  Datomic-shaped request-map convenience for host examples

The Clojure adapter still uses the direct shape-specific Java methods on hot
`q`/`rows` paths. The generic Java facade is useful for API direction, but a
future native one-call column batch handle is needed before it should replace
the current optimized Clojure dispatch.

Because it uses the preview FFM API, the examples compile and run with:

```sh
javac --enable-preview --release 21 ...
java --enable-preview --enable-native-access=ALL-UNNAMED ...
```

The Java wrapper exposes `Vev.load(path)` and `createConn()` as the public-ish
example shape. `openMemory()` remains as a low-level compatibility alias for
the underlying ABI operation. Durable connections use `connect(path)` and query
through immutable DB snapshots:

```java
try (Vev.DurableConnection durable = vev.connect("app.vev.sqlite")) {
    String backend = durable.backend();
    long basisT = durable.basisT();
    long txCount = durable.txCount();
    String info = durable.infoEdn();
    try (Vev.TxReport report =
             durable.transactReport("[{:db/id 1 :user/name \"Ada\"}]")) {
        // inspect report.value() if needed
    }
    try (Vev.DB db = durable.db()) {
        // query db with prepared queries
    }
}
```

Java callers that want a Datomic-style request-map shape can run a one-shot
query through `Vev.queryRows`, or use `Vev.queryMaps` for `:keys`/`:strs`/
`:syms` return-map queries:

```java
try (Vev.DB db = conn.db()) {
    List<List<Object>> rows = vev.queryRows(Map.of(
        "query", """
            [:find ?name
             :in $ ?email
             :where [?e :user/email ?email]
                    [?e :user/name ?name]]
            """,
        "args", List.of(db, "ada@example.com")));

    List<Map<Object, Object>> maps = vev.queryMaps(Map.of(
        "query", """
            [:find ?name ?email
             :keys name email
             :in $ ?email
             :where [?e :user/email ?email]
                    [?e :user/name ?name]]
            """,
        "args", List.of(db, "ada@example.com")));
}
```

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

Plain Clojure `q`/`rows` calls prepare and close a temporary native query
handle. Use `vev/prepare` with `with-open` when a query should be reused.
Prepared queries can be inspected with `vev/prepared-edn`, which returns the
portable parser description as Clojure data.

Durable connections use `connect`:

```clojure
(with-open [conn (vev/connect "build/lib/libvev.dylib" "app.vev.sqlite")]
  (vev/connection-info conn)
  (vev/transact! conn [{:db/id 1 :user/name "Ada"}])
  (vev/q (vev/db conn) '[:find ?name :where [?e :user/name ?name]]))
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

Direct pull entry points return owned `vev_value_handle_t` handles. Values
borrowed from `vev_value_handle_value` are valid until `vev_value_handle_free`.
This gives C and host wrappers the same DB-as-a-value shape as the Datomic-style
API: retain a DB snapshot, pull against it, convert or copy the returned value,
then free the pull handle.

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

## Transaction Report Listeners

Host code can register connection listeners that receive successful transaction
reports after a commit. Failed transactions do not notify listeners. Listener
callbacks receive a borrowed report handle that is valid only for the duration
of the callback.

```c
struct listener_state {
    int count;
};

static void tx_listener(void *user, vev_tx_report_t report) {
    struct listener_state *state = (struct listener_state *)user;
    state->count++;

    const char *report_text = vev_tx_report_edn(report);
    vev_string_free(report_text);
}

struct listener_state state = {0};
vev_conn_listen_tx_report(conn, "app-listener", tx_listener, &state);
vev_transact_edn(conn, "[{:db/id 1 :user/name \"Ada\"}]");
vev_conn_unlisten_tx_report(conn, "app-listener");
```

The callback must copy any strings or values it wants to keep. It must not free
the borrowed report handle. Listener callback failures do not roll back a
transaction; application code should treat listeners as post-commit observers.

## Transaction Function Callbacks

Host code can register transaction functions by ident and call them from EDN
tx-data with `:db.fn/call` or shorthand ident transaction forms.

```c
static const char *mark_seen(void *user, vev_db_t db, int argc, vev_tx_fn_args_t args) {
    (void)user;
    (void)db;
    if (argc != 2) {
        return NULL;
    }

    vev_value_t entity = vev_tx_fn_arg(args, 0);
    vev_value_t label = vev_tx_fn_arg(args, 1);
    if (vev_value_kind(entity) != VEV_VALUE_INT ||
        vev_value_kind(label) != VEV_VALUE_STRING) {
        return NULL;
    }

    const char *label_text = vev_value_text(label);
    static char out[256];
    snprintf(
        out,
        sizeof(out),
        "[:db/add %lld :user/seen-label \"%s\"]",
        vev_value_int(entity),
        label_text);
    vev_string_free(label_text);
    return out;
}

vev_tx_fn_registry_t registry = vev_tx_fn_registry_create();
vev_tx_fn_registry_register_edn(registry, ":mark-seen", mark_seen, NULL);

vev_tx_report_t report = vev_transact_edn_report_with_tx_fns(
    conn,
    "[[:db.fn/call :mark-seen 1 \"from-c\"]]",
    registry);

vev_tx_report_free(report);
vev_tx_fn_registry_free(registry);
```

The callback receives a borrowed immutable DB snapshot handle for the
transaction point and borrowed argument value handles. `vev_tx_fn_arg` returns
borrowed handles valid only during the callback. Text returned by
`vev_value_text` is caller-owned and must be released with `vev_string_free`.

The callback returns an EDN tx-data string. Vev parses and copies the returned
tx-data before the callback frame is released, so returning a stable static
buffer or a user-managed buffer is enough for the current call. Returning
`NULL` fails the transaction. The current raw ABI implementation has 16 global
callback slots; host adapters should treat that as an implementation limit, not
as the long-term API shape.

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
- `vev_stmt_bind_pull_pattern_edn`
- `vev_stmt_bind_db_source`

The statement API currently covers scalar bindings and homogeneous collection
bindings, homogeneous tuple bindings, homogeneous relation bindings,
same-attribute lookup-ref string collections, pull-pattern inputs, and named DB
source inputs. Source-aware prepared queries are created with
`vev_prepare_query_edn_with_sources`, then statements bind actual immutable DB
handles with `vev_stmt_bind_db_source`.

Prepared query status is available through `vev_prepared_query_ok` and
`vev_prepared_query_error`. `vev_prepared_query_edn` returns an owned EDN-ish
description of the prepared parser value, including input specs, clauses,
predicates, function clauses, rule calls, structured `not`/`or` groups, `ground`, `get-else`, `get-some`, pull finds, and aggregate finds.
Prepared pull patterns have the same status shape, and
`vev_prepared_pull_pattern_edn` returns an owned EDN-ish description with
parsed attrs, nested patterns, limits, defaults, aliases, transforms, and
recursion markers.
Prepared parser descriptions include both human-readable `:error` text and a
stable `:error-code` keyword for host-facing malformed-input handling.
Statement execution failures from direct visitor calls are available through
`vev_stmt_error`.

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
- `vev_result_value_text_data`
- `vev_result_value_text_len`
- `vev_result_value_edn`
- `vev_result_pull_count`
- `vev_result_pull`
- `vev_result_visit`
- `vev_query_stmt_column_batch`
- `vev_query_db_stmt_column_batch`
- `vev_query_stmt_visit`
- `vev_query_db_stmt_visit`

The `vev_result_value_*` scalar accessors are convenience functions for direct
row values. New host wrappers should prefer `vev_result_value` plus the generic
`vev_value_*` traversal functions, because the same path works for nested values
and rendered pull results.

For hot flat query shapes, the ABI also exposes column-oriented handles:

- `vev_query_db_prepared_column_batch_with_inputs`
- `vev_query_stmt_column_batch`
- `vev_query_db_stmt_column_batch`
- `vev_column_batch_kind`
- `vev_column_batch_count`
- `vev_column_batch_entities_data`
- `vev_column_batch_ints_data`
- `vev_column_batch_string_data_array`
- `vev_column_batch_string_lengths_data`
- `vev_column_batch_string_dictionary_count`
- `vev_column_batch_string_dictionary_data_array`
- `vev_column_batch_string_dictionary_lengths_data`
- `vev_column_batch_string_indices_data`
- `vev_query_db_prepared_entity_column_with_inputs`
- `vev_u64_array_count`
- `vev_u64_array_data`
- `vev_query_db_prepared_string_column_with_inputs`
- `vev_string_array_count`
- `vev_string_array_data_array`
- `vev_string_array_lengths_data`
- `vev_query_db_prepared_entity_int_pairs_with_inputs`
- `vev_entity_int_pairs_count`
- `vev_entity_int_pairs_entities_data`
- `vev_entity_int_pairs_values_data`
- `vev_query_db_prepared_entity_string_int_triples_with_inputs`
- `vev_entity_string_int_triples_count`
- `vev_entity_string_int_triples_entities_data`
- `vev_entity_string_int_triples_ints_data`
- `vev_entity_string_int_triples_string_data_array`
- `vev_entity_string_int_triples_string_lengths_data`
- `vev_entity_string_int_triples_string_dictionary_count`
- `vev_entity_string_int_triples_string_dictionary_data_array`
- `vev_entity_string_int_triples_string_dictionary_lengths_data`
- `vev_entity_string_int_triples_string_indices_data`
- `vev_entity_string_int_triples_string_data`
- `vev_entity_string_int_triples_string_len`

These are lower-level than the generic result API, but are the right shape for
language adapters that want to avoid per-cell value handles for common
`[:find ?e]`, `[:find ?text]`, `[:find ?e ?n]`, and `[:find ?e ?s ?n]`
queries. `vev_query_db_prepared_column_batch_with_inputs` is the preferred
one-call entry point for host adapters that want the fastest currently available
flat representation without probing every exact-shape function. Its kind is one
of `VEV_COLUMN_BATCH_ENTITY`, `VEV_COLUMN_BATCH_STRING`,
`VEV_COLUMN_BATCH_ENTITY_INT`, `VEV_COLUMN_BATCH_ENTITY_STRING_INT`, or
`VEV_COLUMN_BATCH_NONE`. The exact-shape functions remain public for callers
that already know the expected result shape.

Statement column batches use the same representation after inputs have been
bound with `vev_stmt_bind_*`. `vev_query_stmt_column_batch` runs against the
current connection DB, while `vev_query_db_stmt_column_batch` runs against a
retained immutable `vev_db_t`. Statements without named DB sources use the
current optimized column extractors. Source-bound statements fall back through
the normal result engine and then build an owned flat column batch for supported
entity, string, entity/int, and entity/string/int shapes. Entity positions in
source relation rows may be represented as integer ids; the fallback accepts
those as entity column values when they are non-negative.

Column pointers are borrowed and remain valid until the corresponding column
handle or column batch is freed. Single string-column results use
`vev_string_array_data_array` plus `vev_string_array_lengths_data`. For
entity/string/int columns, prefer
`vev_entity_string_int_triples_string_data_array` plus
`vev_entity_string_int_triples_string_lengths_data` so host adapters can read
all borrowed UTF-8 byte pointers and lengths without one ABI call per cell.
Optimized entity/string/int batches borrow strings from the DB. Fallback
entity/string/int batches created from generic result rows copy string contents
into the batch and release them when `vev_column_batch_free` is called.
For repeated string-heavy results, adapters can instead use the optional string
dictionary accessors: decode the dictionary entries once, then map each row
through `vev_entity_string_int_triples_string_indices_data`. Vev only builds
that dictionary when a small result sample shows repeated strings, so mostly
unique string columns stay on the direct pointer/length path.
The per-index `vev_entity_string_int_triples_string_data` and
`vev_entity_string_int_triples_string_len` accessors are retained for simpler
callers. The convenience `vev_entity_string_int_triples_string` returns an
owned C string that must be released with `vev_string_free`.

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

Direct pull entry points use owned value handles:

- `vev_pull_edn`
- `vev_prepare_pull_pattern_edn`
- `vev_prepared_pull_pattern_ok`
- `vev_prepared_pull_pattern_error`
- `vev_prepared_pull_pattern_edn`
- `vev_prepared_pull_pattern_free`
- `vev_pull_prepared`
- `vev_pull_lookup_ref_string_edn`
- `vev_pull_lookup_ref_string_prepared`
- `vev_pull_lookup_ref_keyword_edn`
- `vev_pull_lookup_ref_keyword_prepared`
- `vev_pull_lookup_ref_uuid_edn`
- `vev_pull_lookup_ref_uuid_prepared`
- `vev_pull_lookup_ref_entity_edn`
- `vev_pull_lookup_ref_entity_prepared`
- `vev_pull_lookup_ref_int_edn`
- `vev_pull_lookup_ref_int_prepared`
- `vev_pull_many_edn`
- `vev_pull_many_prepared`
- `vev_pull_many_lookup_ref_uuid_prepared`
- `vev_value_handle_value`
- `vev_value_handle_edn`
- `vev_value_handle_free`
- `vev_value_text_data`
- `vev_value_text_len`

`vev_value_visit` streams any nested `vev_value_t` tree through a C callback.
It emits `VEV_VALUE_VISIT_VALUE` for every node and `VEV_VALUE_VISIT_END` after
each vector/map container. The callback receives borrowed handles; callers must
copy strings or EDN text they want to retain after the owning result/report is
freed.

`vev_value_text` returns an owned C string for string-like values. Hosts that
are walking many nested pull values can avoid that allocation by using the
borrowed `vev_value_text_data` plus `vev_value_text_len` pair. That byte view is
valid only while the owning result, report, or value handle remains alive.

`vev_result_visit` streams a typed result handle by row. It emits
`VEV_RESULT_VISIT_ROW_BEGIN`, `VEV_RESULT_VISIT_VALUE`,
`VEV_RESULT_VISIT_PULL`, and `VEV_RESULT_VISIT_ROW_END`. Value and pull events
carry borrowed `vev_value_t` handles with the same lifetime as values returned
by `vev_result_value` and `vev_result_pull`.

`vev_query_stmt_visit` and `vev_query_db_stmt_visit` execute a typed statement
and stream rows through the same event protocol. They return `false` for null
handles, query errors, or visitor cancellation. Use materialized result handles
when the host needs detailed error text until the explicit status API is added.

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

The initial C ABI shape is now covered for C, Python, Rust, Java, and Clojure,
including immutable DB handles, typed statement bindings, direct pull handles,
direct row visitors, registered transaction function callbacks, status/error
accessors, transaction report listeners, storage-neutral durable connection
handles backed by SQLite, and baseline ABI-vs-native benchmarks. Basic Python,
Rust, Java, and Clojure wrappers now smoke-test durable
open/write/close/reopen/query. The next
interop work should be driven by concrete host adapter needs, especially
packaging and richer host-specific APIs, rather than expanding the raw C surface
speculatively.
