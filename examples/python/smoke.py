#!/usr/bin/env python3
import ctypes
import pathlib
import sys


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
    finally:
        if prepared:
            lib.vev_prepared_query_free(prepared)
        lib.vev_conn_close(conn)

    return 0


if __name__ == "__main__":
    sys.exit(main())
