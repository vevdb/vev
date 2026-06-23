#!/usr/bin/env python3
import ctypes
import pathlib
import sys


VEV_VALUE_ENTITY = 1
VEV_VALUE_STRING = 2
VEV_VALUE_MAP = 9


def load_library() -> ctypes.CDLL:
    root = pathlib.Path(__file__).resolve().parents[2]
    lib_path = root / "build" / "lib" / "libvev.dylib"
    return ctypes.CDLL(str(lib_path))


def owned_text(lib: ctypes.CDLL, ptr: int) -> str:
    if not ptr:
        return ""
    try:
        return ctypes.cast(ptr, ctypes.c_char_p).value.decode("utf-8")
    finally:
        lib.vev_string_free(ptr)


def value_text(lib: ctypes.CDLL, value: int) -> str:
    return owned_text(lib, lib.vev_value_text(value))


def map_get(lib: ctypes.CDLL, value: int, key: str) -> int:
    for index in range(lib.vev_value_map_count(value)):
        item_key = lib.vev_value_map_key(value, index)
        if value_text(lib, item_key) == key:
            return lib.vev_value_map_value(value, index)
    return 0


def main() -> int:
    lib = load_library()

    lib.vev_version.restype = ctypes.c_char_p

    lib.vev_conn_open_memory.restype = ctypes.c_void_p
    lib.vev_conn_close.argtypes = [ctypes.c_void_p]
    lib.vev_conn_db.argtypes = [ctypes.c_void_p]
    lib.vev_conn_db.restype = ctypes.c_void_p
    lib.vev_db_release.argtypes = [ctypes.c_void_p]

    lib.vev_string_free.argtypes = [ctypes.c_void_p]

    lib.vev_transact_edn.argtypes = [ctypes.c_void_p, ctypes.c_char_p]
    lib.vev_transact_edn.restype = ctypes.c_void_p

    lib.vev_query_edn.argtypes = [ctypes.c_void_p, ctypes.c_char_p]
    lib.vev_query_edn.restype = ctypes.c_void_p

    lib.vev_query_edn_with_inputs.argtypes = [
        ctypes.c_void_p,
        ctypes.c_char_p,
        ctypes.c_char_p,
    ]
    lib.vev_query_edn_with_inputs.restype = ctypes.c_void_p

    lib.vev_prepare_query_edn.argtypes = [ctypes.c_char_p]
    lib.vev_prepare_query_edn.restype = ctypes.c_void_p
    lib.vev_prepared_query_free.argtypes = [ctypes.c_void_p]
    lib.vev_stmt_create.argtypes = [ctypes.c_void_p]
    lib.vev_stmt_create.restype = ctypes.c_void_p
    lib.vev_stmt_clear.argtypes = [ctypes.c_void_p]
    lib.vev_stmt_free.argtypes = [ctypes.c_void_p]
    lib.vev_stmt_bind_string.argtypes = [ctypes.c_void_p, ctypes.c_char_p]
    lib.vev_stmt_bind_string.restype = ctypes.c_bool
    lib.vev_stmt_bind_keyword.argtypes = [ctypes.c_void_p, ctypes.c_char_p]
    lib.vev_stmt_bind_keyword.restype = ctypes.c_bool
    lib.vev_stmt_bind_symbol.argtypes = [ctypes.c_void_p, ctypes.c_char_p]
    lib.vev_stmt_bind_symbol.restype = ctypes.c_bool
    lib.vev_stmt_bind_entity.argtypes = [ctypes.c_void_p, ctypes.c_ulonglong]
    lib.vev_stmt_bind_entity.restype = ctypes.c_bool
    lib.vev_stmt_bind_int.argtypes = [ctypes.c_void_p, ctypes.c_longlong]
    lib.vev_stmt_bind_int.restype = ctypes.c_bool
    lib.vev_stmt_bind_bool.argtypes = [ctypes.c_void_p, ctypes.c_bool]
    lib.vev_stmt_bind_bool.restype = ctypes.c_bool
    lib.vev_query_prepared_with_inputs.argtypes = [
        ctypes.c_void_p,
        ctypes.c_void_p,
        ctypes.c_char_p,
    ]
    lib.vev_query_prepared_with_inputs.restype = ctypes.c_void_p

    lib.vev_query_prepared_result_with_inputs.argtypes = [
        ctypes.c_void_p,
        ctypes.c_void_p,
        ctypes.c_char_p,
    ]
    lib.vev_query_prepared_result_with_inputs.restype = ctypes.c_void_p
    lib.vev_query_stmt_result.argtypes = [ctypes.c_void_p, ctypes.c_void_p]
    lib.vev_query_stmt_result.restype = ctypes.c_void_p
    lib.vev_query_db_stmt_result.argtypes = [ctypes.c_void_p, ctypes.c_void_p]
    lib.vev_query_db_stmt_result.restype = ctypes.c_void_p
    lib.vev_query_db_prepared_result_with_inputs.argtypes = [
        ctypes.c_void_p,
        ctypes.c_void_p,
        ctypes.c_char_p,
    ]
    lib.vev_query_db_prepared_result_with_inputs.restype = ctypes.c_void_p
    lib.vev_result_free.argtypes = [ctypes.c_void_p]
    lib.vev_result_ok.argtypes = [ctypes.c_void_p]
    lib.vev_result_ok.restype = ctypes.c_bool
    lib.vev_result_row_count.argtypes = [ctypes.c_void_p]
    lib.vev_result_row_count.restype = ctypes.c_int
    lib.vev_result_value_count.argtypes = [ctypes.c_void_p, ctypes.c_int]
    lib.vev_result_value_count.restype = ctypes.c_int
    lib.vev_result_value_kind.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.c_int]
    lib.vev_result_value_kind.restype = ctypes.c_int
    lib.vev_result_value.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.c_int]
    lib.vev_result_value.restype = ctypes.c_void_p
    lib.vev_result_pull_count.argtypes = [ctypes.c_void_p, ctypes.c_int]
    lib.vev_result_pull_count.restype = ctypes.c_int
    lib.vev_result_pull.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.c_int]
    lib.vev_result_pull.restype = ctypes.c_void_p
    lib.vev_result_value_entity.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.c_int]
    lib.vev_result_value_entity.restype = ctypes.c_ulonglong
    lib.vev_result_value_text.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.c_int]
    lib.vev_result_value_text.restype = ctypes.c_void_p
    lib.vev_result_value_edn.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.c_int]
    lib.vev_result_value_edn.restype = ctypes.c_void_p
    lib.vev_value_kind.argtypes = [ctypes.c_void_p]
    lib.vev_value_kind.restype = ctypes.c_int
    lib.vev_value_entity.argtypes = [ctypes.c_void_p]
    lib.vev_value_entity.restype = ctypes.c_ulonglong
    lib.vev_value_int.argtypes = [ctypes.c_void_p]
    lib.vev_value_int.restype = ctypes.c_longlong
    lib.vev_value_float.argtypes = [ctypes.c_void_p]
    lib.vev_value_float.restype = ctypes.c_double
    lib.vev_value_bool.argtypes = [ctypes.c_void_p]
    lib.vev_value_bool.restype = ctypes.c_bool
    lib.vev_value_text.argtypes = [ctypes.c_void_p]
    lib.vev_value_text.restype = ctypes.c_void_p
    lib.vev_value_edn.argtypes = [ctypes.c_void_p]
    lib.vev_value_edn.restype = ctypes.c_void_p
    lib.vev_value_item_count.argtypes = [ctypes.c_void_p]
    lib.vev_value_item_count.restype = ctypes.c_int
    lib.vev_value_item.argtypes = [ctypes.c_void_p, ctypes.c_int]
    lib.vev_value_item.restype = ctypes.c_void_p
    lib.vev_value_map_count.argtypes = [ctypes.c_void_p]
    lib.vev_value_map_count.restype = ctypes.c_int
    lib.vev_value_map_key.argtypes = [ctypes.c_void_p, ctypes.c_int]
    lib.vev_value_map_key.restype = ctypes.c_void_p
    lib.vev_value_map_value.argtypes = [ctypes.c_void_p, ctypes.c_int]
    lib.vev_value_map_value.restype = ctypes.c_void_p

    print(f"version: {lib.vev_version().decode('utf-8')}")

    conn = lib.vev_conn_open_memory()
    if not conn:
        raise RuntimeError("failed to open Vev connection")

    prepared = None
    stmt = None
    all_emails = None
    snapshot = None
    conn_open = True
    try:
        tx = owned_text(
            lib,
            lib.vev_transact_edn(
                conn,
                b"[{:db/id 1 :user/name \"Ada\" :user/email \"ada@example.com\"}"
                b" {:db/id 2 :user/name \"Grace\" :user/email \"grace@example.com\"}]",
            ),
        )
        print(f"tx: {tx}")

        result = owned_text(
            lib,
            lib.vev_query_edn_with_inputs(
                conn,
                b"[:find ?name :in [?email ...] :where [?e :user/email ?email] [?e :user/name ?name]]",
                b"[[\"ada@example.com\" \"grace@example.com\"]]",
            ),
        )
        print(f"input-collection: {result}")

        tx_ref_schema = owned_text(
            lib,
            lib.vev_transact_edn(
                conn,
                b"[[:db/add 100 :db/ident :user/friend]"
                b" [:db/add 100 :db/valueType :db.type/ref]"
                b" [:db/add 1 :user/friend 2]]",
            ),
        )
        print(f"tx-ref-schema: {tx_ref_schema}")

        prepared = lib.vev_prepare_query_edn(
            b"[:find ?e ?email :in ?needle :where [?e :user/email ?email] [(= ?email ?needle)]]"
        )
        if not prepared:
            raise RuntimeError("failed to prepare query")

        prepared_result = owned_text(
            lib,
            lib.vev_query_prepared_with_inputs(
                conn,
                prepared,
                b"[\"grace@example.com\"]",
            ),
        )
        print(f"prepared: {prepared_result}")

        if "\"Ada\"" not in result or "grace@example.com" not in prepared_result:
            raise RuntimeError("unexpected Vev query output")

        handle = lib.vev_query_prepared_result_with_inputs(
            conn,
            prepared,
            b"[\"grace@example.com\"]",
        )
        if not handle:
            raise RuntimeError("failed to create result handle")
        try:
            if not lib.vev_result_ok(handle):
                raise RuntimeError("typed result handle returned an error")
            rows = []
            for row in range(lib.vev_result_row_count(handle)):
                values = []
                for column in range(lib.vev_result_value_count(handle, row)):
                    kind = lib.vev_result_value_kind(handle, row, column)
                    if kind == VEV_VALUE_ENTITY:
                        values.append(lib.vev_result_value_entity(handle, row, column))
                    elif kind == VEV_VALUE_STRING:
                        values.append(owned_text(lib, lib.vev_result_value_text(handle, row, column)))
                    else:
                        values.append(owned_text(lib, lib.vev_result_value_edn(handle, row, column)))
                rows.append(values)
            print(f"result-handle: {rows}")
            if rows != [[2, "grace@example.com"]]:
                raise RuntimeError("unexpected typed result rows")
        finally:
            lib.vev_result_free(handle)

        stmt = lib.vev_stmt_create(prepared)
        if not stmt:
            raise RuntimeError("failed to create statement")
        if not lib.vev_stmt_bind_string(stmt, b"ada@example.com"):
            raise RuntimeError("failed to bind statement")
        stmt_handle = lib.vev_query_stmt_result(conn, stmt)
        try:
            if not lib.vev_result_ok(stmt_handle):
                raise RuntimeError("statement result returned an error")
            stmt_rows = lib.vev_result_row_count(stmt_handle)
        finally:
            lib.vev_result_free(stmt_handle)
        lib.vev_stmt_clear(stmt)
        if not lib.vev_stmt_bind_string(stmt, b"grace@example.com"):
            raise RuntimeError("failed to rebind statement")
        rebound_handle = lib.vev_query_stmt_result(conn, stmt)
        try:
            if not lib.vev_result_ok(rebound_handle):
                raise RuntimeError("rebound statement result returned an error")
            rebound_rows = lib.vev_result_row_count(rebound_handle)
        finally:
            lib.vev_result_free(rebound_handle)
        print(f"stmt rows: {stmt_rows}")
        print(f"stmt-rebound rows: {rebound_rows}")
        if stmt_rows != 1 or rebound_rows != 1:
            raise RuntimeError("unexpected statement row counts")

        pull_query = lib.vev_prepare_query_edn(
            b"[:find (pull ?e [:user/name {:user/friend [:user/name]}]) :where [?e :user/name \"Ada\"]]"
        )
        if not pull_query:
            raise RuntimeError("failed to prepare pull query")
        pull_handle = lib.vev_query_prepared_result_with_inputs(conn, pull_query, b"[]")
        try:
            if not lib.vev_result_ok(pull_handle):
                raise RuntimeError("pull result returned an error")
            pulled = lib.vev_result_pull(pull_handle, 0, 0)
            name = map_get(lib, pulled, ":user/name")
            friend_map = map_get(lib, pulled, ":user/friend")
            friend_name = map_get(lib, friend_map, ":user/name")
            print(
                "pull traversal:",
                {
                    "rows": lib.vev_result_row_count(pull_handle),
                    "pulls": lib.vev_result_pull_count(pull_handle, 0),
                    "name": value_text(lib, name),
                    "friend": value_text(lib, friend_name),
                },
            )
            if (
                lib.vev_result_row_count(pull_handle) != 1
                or lib.vev_result_pull_count(pull_handle, 0) != 1
                or lib.vev_value_kind(pulled) != VEV_VALUE_MAP
                or value_text(lib, name) != "Ada"
                or value_text(lib, friend_name) != "Grace"
            ):
                raise RuntimeError(
                    f"unexpected pull traversal output: {owned_text(lib, lib.vev_value_edn(pulled))}"
                )
        finally:
            lib.vev_result_free(pull_handle)
            lib.vev_prepared_query_free(pull_query)

        all_emails = lib.vev_prepare_query_edn(
            b"[:find ?e ?email :where [?e :user/email ?email]]"
        )
        if not all_emails:
            raise RuntimeError("failed to prepare snapshot query")

        snapshot = lib.vev_conn_db(conn)
        if not snapshot:
            raise RuntimeError("failed to retain DB snapshot")

        tx_after_snapshot = owned_text(
            lib,
            lib.vev_transact_edn(
                conn,
                b"[{:db/id 3 :user/name \"Alan\" :user/email \"alan@example.com\"}]",
            ),
        )
        print(f"tx-after-snapshot: {tx_after_snapshot}")

        current = lib.vev_query_prepared_result_with_inputs(conn, all_emails, b"[]")
        try:
            current_rows = lib.vev_result_row_count(current)
        finally:
            lib.vev_result_free(current)

        lib.vev_conn_close(conn)
        conn_open = False

        old = lib.vev_query_db_prepared_result_with_inputs(snapshot, all_emails, b"[]")
        try:
            old_rows = lib.vev_result_row_count(old)
        finally:
            lib.vev_result_free(old)

        print(f"current-db rows: {current_rows}")
        print(f"snapshot-db rows: {old_rows}")
        if current_rows != 3 or old_rows != 2:
            raise RuntimeError("unexpected snapshot row counts")
    finally:
        if snapshot:
            lib.vev_db_release(snapshot)
        if all_emails:
            lib.vev_prepared_query_free(all_emails)
        if stmt:
            lib.vev_stmt_free(stmt)
        if prepared:
            lib.vev_prepared_query_free(prepared)
        if conn_open:
            lib.vev_conn_close(conn)

    return 0


if __name__ == "__main__":
    sys.exit(main())
