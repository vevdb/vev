#!/usr/bin/env python3
import ctypes
import pathlib
import sys


VEV_VALUE_ENTITY = 1
VEV_VALUE_STRING = 2


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


def main() -> int:
    lib = load_library()

    lib.vev_version.restype = ctypes.c_char_p

    lib.vev_conn_open_memory.restype = ctypes.c_void_p
    lib.vev_conn_close.argtypes = [ctypes.c_void_p]

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
    lib.vev_result_free.argtypes = [ctypes.c_void_p]
    lib.vev_result_ok.argtypes = [ctypes.c_void_p]
    lib.vev_result_ok.restype = ctypes.c_bool
    lib.vev_result_row_count.argtypes = [ctypes.c_void_p]
    lib.vev_result_row_count.restype = ctypes.c_int
    lib.vev_result_value_count.argtypes = [ctypes.c_void_p, ctypes.c_int]
    lib.vev_result_value_count.restype = ctypes.c_int
    lib.vev_result_value_kind.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.c_int]
    lib.vev_result_value_kind.restype = ctypes.c_int
    lib.vev_result_value_entity.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.c_int]
    lib.vev_result_value_entity.restype = ctypes.c_ulonglong
    lib.vev_result_value_text.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.c_int]
    lib.vev_result_value_text.restype = ctypes.c_void_p
    lib.vev_result_value_edn.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.c_int]
    lib.vev_result_value_edn.restype = ctypes.c_void_p

    print(f"version: {lib.vev_version().decode('utf-8')}")

    conn = lib.vev_conn_open_memory()
    if not conn:
        raise RuntimeError("failed to open Vev connection")

    prepared = None
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
    finally:
        if prepared:
            lib.vev_prepared_query_free(prepared)
        lib.vev_conn_close(conn)

    return 0


if __name__ == "__main__":
    sys.exit(main())
