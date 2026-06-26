from __future__ import annotations

import ctypes
import pathlib
from dataclasses import dataclass
from typing import Any


VEV_VALUE_NIL = 0
VEV_VALUE_ENTITY = 1
VEV_VALUE_STRING = 2
VEV_VALUE_INT = 3
VEV_VALUE_FLOAT = 4
VEV_VALUE_BOOL = 5
VEV_VALUE_KEYWORD = 6
VEV_VALUE_SYMBOL = 7
VEV_VALUE_VECTOR = 8
VEV_VALUE_MAP = 9

VEV_RESULT_VISIT_ROW_BEGIN = 1
VEV_RESULT_VISIT_VALUE = 2
VEV_RESULT_VISIT_PULL = 3
VEV_RESULT_VISIT_ROW_END = 4

RESULT_VISIT_FN = ctypes.CFUNCTYPE(
    ctypes.c_bool,
    ctypes.c_void_p,
    ctypes.c_int,
    ctypes.c_int,
    ctypes.c_int,
    ctypes.c_void_p,
)


@dataclass(frozen=True)
class Entity:
    id: int


@dataclass(frozen=True)
class LookupRef:
    attr: str
    value: object


@dataclass(frozen=True)
class TupleInput:
    values: tuple[object, ...]


@dataclass(frozen=True)
class TypedCollection:
    value_type: str
    values: tuple[object, ...] = ()


@dataclass(frozen=True)
class TypedTuple:
    value_type: str
    values: tuple[object, ...] = ()


@dataclass(frozen=True)
class Relation:
    rows: tuple[tuple[object, ...], ...]


@dataclass(frozen=True)
class TypedRelation:
    value_type: str
    width: int
    rows: tuple[tuple[object, ...], ...] = ()


@dataclass(frozen=True)
class PullPattern:
    edn: str


@dataclass(frozen=True)
class DBSource:
    name: str
    db: "DB"


class VevError(RuntimeError):
    pass


def _default_library_path() -> pathlib.Path:
    root = pathlib.Path(__file__).resolve().parents[2]
    return root / "build" / "lib" / "libvev.dylib"


def _bytes(text: str) -> bytes:
    return text.encode("utf-8")


def _value_matches_type(value: object, value_type: str) -> bool:
    if value_type == "entity":
        return isinstance(value, Entity)
    if value_type == "bool":
        return isinstance(value, bool)
    if value_type == "int":
        return isinstance(value, int) and not isinstance(value, bool)
    if value_type == "string":
        return isinstance(value, str)
    return False


class Library:
    def __init__(self, path: str | pathlib.Path | None = None):
        self.path = pathlib.Path(path) if path is not None else _default_library_path()
        self.lib = ctypes.CDLL(str(self.path))
        self._configure()

    def _configure(self) -> None:
        lib = self.lib

        lib.vev_version.restype = ctypes.c_char_p

        lib.vev_conn_open_memory.restype = ctypes.c_void_p
        lib.vev_conn_close.argtypes = [ctypes.c_void_p]
        lib.vev_conn_db.argtypes = [ctypes.c_void_p]
        lib.vev_conn_db.restype = ctypes.c_void_p
        lib.vev_conn_from_db.argtypes = [ctypes.c_void_p]
        lib.vev_conn_from_db.restype = ctypes.c_void_p
        lib.vev_connect.argtypes = [ctypes.c_char_p]
        lib.vev_connect.restype = ctypes.c_void_p
        lib.vev_connection_ok.argtypes = [ctypes.c_void_p]
        lib.vev_connection_ok.restype = ctypes.c_bool
        lib.vev_connection_error.argtypes = [ctypes.c_void_p]
        lib.vev_connection_error.restype = ctypes.c_void_p
        lib.vev_connection_backend.argtypes = [ctypes.c_void_p]
        lib.vev_connection_backend.restype = ctypes.c_void_p
        lib.vev_connection_path.argtypes = [ctypes.c_void_p]
        lib.vev_connection_path.restype = ctypes.c_void_p
        lib.vev_connection_close.argtypes = [ctypes.c_void_p]
        lib.vev_connection_db.argtypes = [ctypes.c_void_p]
        lib.vev_connection_db.restype = ctypes.c_void_p
        lib.vev_connection_transact_edn_report.argtypes = [
            ctypes.c_void_p,
            ctypes.c_char_p,
        ]
        lib.vev_connection_transact_edn_report.restype = ctypes.c_void_p
        lib.vev_sqlite_conn_open.argtypes = [ctypes.c_char_p]
        lib.vev_sqlite_conn_open.restype = ctypes.c_void_p
        lib.vev_sqlite_conn_ok.argtypes = [ctypes.c_void_p]
        lib.vev_sqlite_conn_ok.restype = ctypes.c_bool
        lib.vev_sqlite_conn_error.argtypes = [ctypes.c_void_p]
        lib.vev_sqlite_conn_error.restype = ctypes.c_void_p
        lib.vev_sqlite_conn_close.argtypes = [ctypes.c_void_p]
        lib.vev_sqlite_conn_db.argtypes = [ctypes.c_void_p]
        lib.vev_sqlite_conn_db.restype = ctypes.c_void_p
        lib.vev_sqlite_conn_transact_edn_report.argtypes = [
            ctypes.c_void_p,
            ctypes.c_char_p,
        ]
        lib.vev_sqlite_conn_transact_edn_report.restype = ctypes.c_void_p
        lib.vev_db_release.argtypes = [ctypes.c_void_p]
        lib.vev_with_edn.argtypes = [ctypes.c_void_p, ctypes.c_char_p]
        lib.vev_with_edn.restype = ctypes.c_void_p
        lib.vev_with_edn_report.argtypes = [ctypes.c_void_p, ctypes.c_char_p]
        lib.vev_with_edn_report.restype = ctypes.c_void_p
        lib.vev_db_with_edn.argtypes = [ctypes.c_void_p, ctypes.c_char_p]
        lib.vev_db_with_edn.restype = ctypes.c_void_p

        lib.vev_string_free.argtypes = [ctypes.c_void_p]

        lib.vev_transact_edn.argtypes = [ctypes.c_void_p, ctypes.c_char_p]
        lib.vev_transact_edn.restype = ctypes.c_void_p
        lib.vev_transact_edn_report.argtypes = [ctypes.c_void_p, ctypes.c_char_p]
        lib.vev_transact_edn_report.restype = ctypes.c_void_p
        lib.vev_tx_report_free.argtypes = [ctypes.c_void_p]
        lib.vev_tx_report_value.argtypes = [ctypes.c_void_p]
        lib.vev_tx_report_value.restype = ctypes.c_void_p
        lib.vev_tx_report_edn.argtypes = [ctypes.c_void_p]
        lib.vev_tx_report_edn.restype = ctypes.c_void_p
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
        lib.vev_prepare_query_edn_with_sources.argtypes = [
            ctypes.c_char_p,
            ctypes.POINTER(ctypes.c_char_p),
            ctypes.c_int,
        ]
        lib.vev_prepare_query_edn_with_sources.restype = ctypes.c_void_p
        lib.vev_prepared_query_ok.argtypes = [ctypes.c_void_p]
        lib.vev_prepared_query_ok.restype = ctypes.c_bool
        lib.vev_prepared_query_error.argtypes = [ctypes.c_void_p]
        lib.vev_prepared_query_error.restype = ctypes.c_void_p
        lib.vev_prepared_query_free.argtypes = [ctypes.c_void_p]

        lib.vev_stmt_create.argtypes = [ctypes.c_void_p]
        lib.vev_stmt_create.restype = ctypes.c_void_p
        lib.vev_stmt_clear.argtypes = [ctypes.c_void_p]
        lib.vev_stmt_free.argtypes = [ctypes.c_void_p]
        lib.vev_stmt_error.argtypes = [ctypes.c_void_p]
        lib.vev_stmt_error.restype = ctypes.c_void_p
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
        lib.vev_stmt_bind_lookup_ref_string.argtypes = [
            ctypes.c_void_p,
            ctypes.c_char_p,
            ctypes.c_char_p,
        ]
        lib.vev_stmt_bind_lookup_ref_string.restype = ctypes.c_bool
        lib.vev_stmt_bind_lookup_ref_keyword.argtypes = [
            ctypes.c_void_p,
            ctypes.c_char_p,
            ctypes.c_char_p,
        ]
        lib.vev_stmt_bind_lookup_ref_keyword.restype = ctypes.c_bool
        lib.vev_stmt_bind_lookup_ref_entity.argtypes = [
            ctypes.c_void_p,
            ctypes.c_char_p,
            ctypes.c_ulonglong,
        ]
        lib.vev_stmt_bind_lookup_ref_entity.restype = ctypes.c_bool
        lib.vev_stmt_bind_lookup_ref_int.argtypes = [
            ctypes.c_void_p,
            ctypes.c_char_p,
            ctypes.c_longlong,
        ]
        lib.vev_stmt_bind_lookup_ref_int.restype = ctypes.c_bool
        lib.vev_stmt_bind_string_collection.argtypes = [
            ctypes.c_void_p,
            ctypes.POINTER(ctypes.c_char_p),
            ctypes.c_int,
        ]
        lib.vev_stmt_bind_string_collection.restype = ctypes.c_bool
        lib.vev_stmt_bind_entity_collection.argtypes = [
            ctypes.c_void_p,
            ctypes.POINTER(ctypes.c_ulonglong),
            ctypes.c_int,
        ]
        lib.vev_stmt_bind_entity_collection.restype = ctypes.c_bool
        lib.vev_stmt_bind_int_collection.argtypes = [
            ctypes.c_void_p,
            ctypes.POINTER(ctypes.c_longlong),
            ctypes.c_int,
        ]
        lib.vev_stmt_bind_int_collection.restype = ctypes.c_bool
        lib.vev_stmt_bind_bool_collection.argtypes = [
            ctypes.c_void_p,
            ctypes.POINTER(ctypes.c_bool),
            ctypes.c_int,
        ]
        lib.vev_stmt_bind_bool_collection.restype = ctypes.c_bool
        lib.vev_stmt_bind_string_tuple.argtypes = [
            ctypes.c_void_p,
            ctypes.POINTER(ctypes.c_char_p),
            ctypes.c_int,
        ]
        lib.vev_stmt_bind_string_tuple.restype = ctypes.c_bool
        lib.vev_stmt_bind_entity_tuple.argtypes = [
            ctypes.c_void_p,
            ctypes.POINTER(ctypes.c_ulonglong),
            ctypes.c_int,
        ]
        lib.vev_stmt_bind_entity_tuple.restype = ctypes.c_bool
        lib.vev_stmt_bind_int_tuple.argtypes = [
            ctypes.c_void_p,
            ctypes.POINTER(ctypes.c_longlong),
            ctypes.c_int,
        ]
        lib.vev_stmt_bind_int_tuple.restype = ctypes.c_bool
        lib.vev_stmt_bind_bool_tuple.argtypes = [
            ctypes.c_void_p,
            ctypes.POINTER(ctypes.c_bool),
            ctypes.c_int,
        ]
        lib.vev_stmt_bind_bool_tuple.restype = ctypes.c_bool
        lib.vev_stmt_bind_string_relation.argtypes = [
            ctypes.c_void_p,
            ctypes.POINTER(ctypes.c_char_p),
            ctypes.c_int,
            ctypes.c_int,
        ]
        lib.vev_stmt_bind_string_relation.restype = ctypes.c_bool
        lib.vev_stmt_bind_entity_relation.argtypes = [
            ctypes.c_void_p,
            ctypes.POINTER(ctypes.c_ulonglong),
            ctypes.c_int,
            ctypes.c_int,
        ]
        lib.vev_stmt_bind_entity_relation.restype = ctypes.c_bool
        lib.vev_stmt_bind_int_relation.argtypes = [
            ctypes.c_void_p,
            ctypes.POINTER(ctypes.c_longlong),
            ctypes.c_int,
            ctypes.c_int,
        ]
        lib.vev_stmt_bind_int_relation.restype = ctypes.c_bool
        lib.vev_stmt_bind_bool_relation.argtypes = [
            ctypes.c_void_p,
            ctypes.POINTER(ctypes.c_bool),
            ctypes.c_int,
            ctypes.c_int,
        ]
        lib.vev_stmt_bind_bool_relation.restype = ctypes.c_bool
        lib.vev_stmt_bind_lookup_ref_string_collection.argtypes = [
            ctypes.c_void_p,
            ctypes.c_char_p,
            ctypes.POINTER(ctypes.c_char_p),
            ctypes.c_int,
        ]
        lib.vev_stmt_bind_lookup_ref_string_collection.restype = ctypes.c_bool
        lib.vev_stmt_bind_lookup_ref_keyword_collection.argtypes = [
            ctypes.c_void_p,
            ctypes.c_char_p,
            ctypes.POINTER(ctypes.c_char_p),
            ctypes.c_int,
        ]
        lib.vev_stmt_bind_lookup_ref_keyword_collection.restype = ctypes.c_bool
        lib.vev_stmt_bind_lookup_ref_entity_collection.argtypes = [
            ctypes.c_void_p,
            ctypes.c_char_p,
            ctypes.POINTER(ctypes.c_ulonglong),
            ctypes.c_int,
        ]
        lib.vev_stmt_bind_lookup_ref_entity_collection.restype = ctypes.c_bool
        lib.vev_stmt_bind_lookup_ref_int_collection.argtypes = [
            ctypes.c_void_p,
            ctypes.c_char_p,
            ctypes.POINTER(ctypes.c_longlong),
            ctypes.c_int,
        ]
        lib.vev_stmt_bind_lookup_ref_int_collection.restype = ctypes.c_bool
        lib.vev_stmt_bind_pull_pattern_edn.argtypes = [
            ctypes.c_void_p,
            ctypes.c_char_p,
        ]
        lib.vev_stmt_bind_pull_pattern_edn.restype = ctypes.c_bool
        lib.vev_stmt_bind_db_source.argtypes = [
            ctypes.c_void_p,
            ctypes.c_char_p,
            ctypes.c_void_p,
        ]
        lib.vev_stmt_bind_db_source.restype = ctypes.c_bool

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
        lib.vev_query_stmt_visit.argtypes = [
            ctypes.c_void_p,
            ctypes.c_void_p,
            RESULT_VISIT_FN,
            ctypes.c_void_p,
        ]
        lib.vev_query_stmt_visit.restype = ctypes.c_bool
        lib.vev_query_db_stmt_visit.argtypes = [
            ctypes.c_void_p,
            ctypes.c_void_p,
            RESULT_VISIT_FN,
            ctypes.c_void_p,
        ]
        lib.vev_query_db_stmt_visit.restype = ctypes.c_bool
        lib.vev_query_db_prepared_result_with_inputs.argtypes = [
            ctypes.c_void_p,
            ctypes.c_void_p,
            ctypes.c_char_p,
        ]
        lib.vev_query_db_prepared_result_with_inputs.restype = ctypes.c_void_p
        lib.vev_pull_edn.argtypes = [
            ctypes.c_void_p,
            ctypes.c_char_p,
            ctypes.c_ulonglong,
        ]
        lib.vev_pull_edn.restype = ctypes.c_void_p
        lib.vev_pull_lookup_ref_string_edn.argtypes = [
            ctypes.c_void_p,
            ctypes.c_char_p,
            ctypes.c_char_p,
            ctypes.c_char_p,
        ]
        lib.vev_pull_lookup_ref_string_edn.restype = ctypes.c_void_p
        lib.vev_pull_many_edn.argtypes = [
            ctypes.c_void_p,
            ctypes.c_char_p,
            ctypes.POINTER(ctypes.c_ulonglong),
            ctypes.c_int,
        ]
        lib.vev_pull_many_edn.restype = ctypes.c_void_p
        lib.vev_value_handle_free.argtypes = [ctypes.c_void_p]
        lib.vev_value_handle_value.argtypes = [ctypes.c_void_p]
        lib.vev_value_handle_value.restype = ctypes.c_void_p
        lib.vev_value_handle_edn.argtypes = [ctypes.c_void_p]
        lib.vev_value_handle_edn.restype = ctypes.c_void_p

        lib.vev_result_free.argtypes = [ctypes.c_void_p]
        lib.vev_result_ok.argtypes = [ctypes.c_void_p]
        lib.vev_result_ok.restype = ctypes.c_bool
        lib.vev_result_error.argtypes = [ctypes.c_void_p]
        lib.vev_result_error.restype = ctypes.c_void_p
        lib.vev_result_row_count.argtypes = [ctypes.c_void_p]
        lib.vev_result_row_count.restype = ctypes.c_int
        lib.vev_result_value_count.argtypes = [ctypes.c_void_p, ctypes.c_int]
        lib.vev_result_value_count.restype = ctypes.c_int
        lib.vev_result_value.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.c_int]
        lib.vev_result_value.restype = ctypes.c_void_p
        lib.vev_result_pull_count.argtypes = [ctypes.c_void_p, ctypes.c_int]
        lib.vev_result_pull_count.restype = ctypes.c_int
        lib.vev_result_pull.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.c_int]
        lib.vev_result_pull.restype = ctypes.c_void_p

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

    def owned_text(self, ptr: int) -> str:
        if not ptr:
            return ""
        try:
            return ctypes.cast(ptr, ctypes.c_char_p).value.decode("utf-8")
        finally:
            self.lib.vev_string_free(ptr)

    def value_to_python(self, value: int) -> Any:
        kind = self.lib.vev_value_kind(value)
        if kind == VEV_VALUE_NIL:
            return None
        if kind == VEV_VALUE_ENTITY:
            return Entity(int(self.lib.vev_value_entity(value)))
        if kind == VEV_VALUE_STRING:
            return self.owned_text(self.lib.vev_value_text(value))
        if kind == VEV_VALUE_INT:
            return int(self.lib.vev_value_int(value))
        if kind == VEV_VALUE_FLOAT:
            return float(self.lib.vev_value_float(value))
        if kind == VEV_VALUE_BOOL:
            return bool(self.lib.vev_value_bool(value))
        if kind == VEV_VALUE_KEYWORD:
            return self.owned_text(self.lib.vev_value_text(value))
        if kind == VEV_VALUE_SYMBOL:
            return self.owned_text(self.lib.vev_value_text(value))
        if kind == VEV_VALUE_VECTOR:
            return [
                self.value_to_python(self.lib.vev_value_item(value, index))
                for index in range(self.lib.vev_value_item_count(value))
            ]
        if kind == VEV_VALUE_MAP:
            return {
                self.value_to_python(
                    self.lib.vev_value_map_key(value, index)
                ): self.value_to_python(
                    self.lib.vev_value_map_value(value, index)
                )
                for index in range(self.lib.vev_value_map_count(value))
            }
        return self.owned_text(self.lib.vev_value_edn(value))


_default_library: Library | None = None


def default_library() -> Library:
    global _default_library
    if _default_library is None:
        _default_library = Library()
    return _default_library


def open_memory() -> "Connection":
    return Connection(default_library())


def connect(uri: str | pathlib.Path) -> "DurableConnection":
    return DurableConnection(default_library(), uri)


def open_sqlite(path: str | pathlib.Path) -> "SQLiteConnection":
    return SQLiteConnection(default_library(), path)


class Connection:
    def __init__(self, library: Library, handle: int | None = None):
        self._library = library
        self._handle = handle if handle is not None else library.lib.vev_conn_open_memory()
        if not self._handle:
            raise VevError("failed to open Vev connection")

    def close(self) -> None:
        if self._handle:
            self._library.lib.vev_conn_close(self._handle)
            self._handle = None

    def __enter__(self) -> "Connection":
        return self

    def __exit__(self, _type: object, _value: object, _traceback: object) -> None:
        self.close()

    def __del__(self) -> None:
        self.close()

    def transact(self, tx_edn: str) -> str:
        self._require_open()
        return self._library.owned_text(
            self._library.lib.vev_transact_edn(self._handle, _bytes(tx_edn))
        )

    def transact_report(self, tx_edn: str) -> "TxReport":
        self._require_open()
        handle = self._library.lib.vev_transact_edn_report(
            self._handle, _bytes(tx_edn)
        )
        if not handle:
            raise VevError("failed to transact")
        return TxReport(self._library, handle)

    def query_text(self, query_edn: str, inputs_edn: str | None = None) -> str:
        self._require_open()
        if inputs_edn is None:
            return self._library.owned_text(
                self._library.lib.vev_query_edn(self._handle, _bytes(query_edn))
            )
        return self._library.owned_text(
            self._library.lib.vev_query_edn_with_inputs(
                self._handle, _bytes(query_edn), _bytes(inputs_edn)
            )
        )

    def prepare(
        self, query_edn: str, source_names: list[str] | None = None
    ) -> "PreparedQuery":
        return PreparedQuery(self._library, query_edn, source_names)

    def db(self) -> "DB":
        self._require_open()
        handle = self._library.lib.vev_conn_db(self._handle)
        if not handle:
            raise VevError("failed to retain DB snapshot")
        return DB(self._library, handle)

    @classmethod
    def from_db(cls, db: "DB") -> "Connection":
        db._require_open()
        handle = db._library.lib.vev_conn_from_db(db._handle)
        if not handle:
            raise VevError("failed to create connection from DB snapshot")
        return cls(db._library, handle)

    def _query_result(self, prepared: "PreparedQuery", inputs_edn: str = "[]") -> "Result":
        self._require_open()
        handle = self._library.lib.vev_query_prepared_result_with_inputs(
            self._handle, prepared._handle, _bytes(inputs_edn)
        )
        return Result(self._library, handle)

    def _query_stmt(self, stmt: "Statement") -> "Result":
        self._require_open()
        handle = self._library.lib.vev_query_stmt_result(self._handle, stmt._handle)
        return Result(self._library, handle)

    def _require_open(self) -> None:
        if not self._handle:
            raise VevError("connection is closed")


class DurableConnection:
    def __init__(self, library: Library, uri: str | pathlib.Path):
        self._library = library
        self._handle = library.lib.vev_connect(_bytes(str(uri)))
        if not self._handle:
            raise VevError("failed to connect Vev durable connection")
        if not library.lib.vev_connection_ok(self._handle):
            error = library.owned_text(library.lib.vev_connection_error(self._handle))
            self.close()
            raise VevError(error)

    def close(self) -> None:
        if self._handle:
            self._library.lib.vev_connection_close(self._handle)
            self._handle = None

    def __enter__(self) -> "DurableConnection":
        return self

    def __exit__(self, _type: object, _value: object, _traceback: object) -> None:
        self.close()

    def __del__(self) -> None:
        self.close()

    def transact_report(self, tx_edn: str) -> "TxReport":
        self._require_open()
        handle = self._library.lib.vev_connection_transact_edn_report(
            self._handle, _bytes(tx_edn)
        )
        if not handle:
            raise VevError("failed to transact")
        return TxReport(self._library, handle)

    def db(self) -> "DB":
        self._require_open()
        handle = self._library.lib.vev_connection_db(self._handle)
        if not handle:
            raise VevError("failed to retain DB snapshot")
        return DB(self._library, handle)

    def backend(self) -> str:
        self._require_open()
        return self._library.owned_text(
            self._library.lib.vev_connection_backend(self._handle)
        )

    def path(self) -> str:
        self._require_open()
        return self._library.owned_text(
            self._library.lib.vev_connection_path(self._handle)
        )

    def prepare(
        self, query_edn: str, source_names: list[str] | None = None
    ) -> "PreparedQuery":
        return PreparedQuery(self._library, query_edn, source_names)

    def _require_open(self) -> None:
        if not self._handle:
            raise VevError("durable connection is closed")


class SQLiteConnection:
    def __init__(self, library: Library, path: str | pathlib.Path):
        self._library = library
        self._handle = library.lib.vev_sqlite_conn_open(_bytes(str(path)))
        if not self._handle:
            raise VevError("failed to open SQLite-backed Vev connection")
        if not library.lib.vev_sqlite_conn_ok(self._handle):
            error = library.owned_text(library.lib.vev_sqlite_conn_error(self._handle))
            self.close()
            raise VevError(error)

    def close(self) -> None:
        if self._handle:
            self._library.lib.vev_sqlite_conn_close(self._handle)
            self._handle = None

    def __enter__(self) -> "SQLiteConnection":
        return self

    def __exit__(self, _type: object, _value: object, _traceback: object) -> None:
        self.close()

    def __del__(self) -> None:
        self.close()

    def transact_report(self, tx_edn: str) -> "TxReport":
        self._require_open()
        handle = self._library.lib.vev_sqlite_conn_transact_edn_report(
            self._handle, _bytes(tx_edn)
        )
        if not handle:
            raise VevError("failed to transact")
        return TxReport(self._library, handle)

    def db(self) -> "DB":
        self._require_open()
        handle = self._library.lib.vev_sqlite_conn_db(self._handle)
        if not handle:
            raise VevError("failed to retain DB snapshot")
        return DB(self._library, handle)

    def prepare(
        self, query_edn: str, source_names: list[str] | None = None
    ) -> "PreparedQuery":
        return PreparedQuery(self._library, query_edn, source_names)

    def _require_open(self) -> None:
        if not self._handle:
            raise VevError("SQLite-backed connection is closed")


class DB:
    def __init__(self, library: Library, handle: int):
        self._library = library
        self._handle = handle

    def close(self) -> None:
        if self._handle:
            self._library.lib.vev_db_release(self._handle)
            self._handle = None

    def __enter__(self) -> "DB":
        return self

    def __exit__(self, _type: object, _value: object, _traceback: object) -> None:
        self.close()

    def __del__(self) -> None:
        self.close()

    def query(self, prepared: "PreparedQuery", inputs_edn: str = "[]") -> "Result":
        self._require_open()
        handle = self._library.lib.vev_query_db_prepared_result_with_inputs(
            self._handle, prepared._handle, _bytes(inputs_edn)
        )
        return Result(self._library, handle)

    def query_stmt(self, stmt: "Statement") -> "Result":
        self._require_open()
        handle = self._library.lib.vev_query_db_stmt_result(self._handle, stmt._handle)
        return Result(self._library, handle)

    def with_text(self, tx_edn: str) -> str:
        self._require_open()
        return self._library.owned_text(
            self._library.lib.vev_with_edn(self._handle, _bytes(tx_edn))
        )

    def with_report(self, tx_edn: str) -> "TxReport":
        self._require_open()
        handle = self._library.lib.vev_with_edn_report(self._handle, _bytes(tx_edn))
        if not handle:
            raise VevError("failed to transact against DB snapshot")
        return TxReport(self._library, handle)

    def db_with(self, tx_edn: str) -> "DB":
        self._require_open()
        handle = self._library.lib.vev_db_with_edn(self._handle, _bytes(tx_edn))
        if not handle:
            raise VevError("failed to create DB snapshot")
        return DB(self._library, handle)

    def pull(self, pattern_edn: str, entity: int | Entity) -> Any:
        self._require_open()
        entity_id = entity.id if isinstance(entity, Entity) else int(entity)
        handle = self._library.lib.vev_pull_edn(
            self._handle, _bytes(pattern_edn), entity_id
        )
        with ValueHandle(self._library, handle) as value:
            return value.value()

    def pull_lookup_ref(self, pattern_edn: str, ref: LookupRef) -> Any:
        self._require_open()
        if not isinstance(ref.value, str):
            raise TypeError("pull_lookup_ref currently supports string lookup values")
        handle = self._library.lib.vev_pull_lookup_ref_string_edn(
            self._handle, _bytes(pattern_edn), _bytes(ref.attr), _bytes(ref.value)
        )
        with ValueHandle(self._library, handle) as value:
            return value.value()

    def pull_many(self, pattern_edn: str, entities: list[int | Entity]) -> list[Any]:
        self._require_open()
        ids = [entity.id if isinstance(entity, Entity) else int(entity) for entity in entities]
        array_type = ctypes.c_ulonglong * len(ids)
        array = array_type(*ids)
        handle = self._library.lib.vev_pull_many_edn(
            self._handle, _bytes(pattern_edn), array, len(ids)
        )
        with ValueHandle(self._library, handle) as value:
            result = value.value()
        if not isinstance(result, list):
            raise VevError(f"expected pull_many vector, got {result!r}")
        return result

    def _require_open(self) -> None:
        if not self._handle:
            raise VevError("DB snapshot is closed")


class ValueHandle:
    def __init__(self, library: Library, handle: int):
        self._library = library
        self._handle = handle
        if not self._handle:
            raise VevError("value handle is null")

    def close(self) -> None:
        if self._handle:
            self._library.lib.vev_value_handle_free(self._handle)
            self._handle = None

    def __enter__(self) -> "ValueHandle":
        return self

    def __exit__(self, _type: object, _value: object, _traceback: object) -> None:
        self.close()

    def __del__(self) -> None:
        self.close()

    def value(self) -> Any:
        self._require_open()
        return self._library.value_to_python(
            self._library.lib.vev_value_handle_value(self._handle)
        )

    def edn(self) -> str:
        self._require_open()
        return self._library.owned_text(
            self._library.lib.vev_value_handle_edn(self._handle)
        )

    def _require_open(self) -> None:
        if not self._handle:
            raise VevError("value handle is closed")


class TxReport:
    def __init__(self, library: Library, handle: int):
        self._library = library
        self._handle = handle

    def close(self) -> None:
        if self._handle:
            self._library.lib.vev_tx_report_free(self._handle)
            self._handle = None

    def __enter__(self) -> "TxReport":
        return self

    def __exit__(self, _type: object, _value: object, _traceback: object) -> None:
        self.close()

    def __del__(self) -> None:
        self.close()

    def value(self) -> Any:
        self._require_open()
        return self._library.value_to_python(
            self._library.lib.vev_tx_report_value(self._handle)
        )

    def edn(self) -> str:
        self._require_open()
        return self._library.owned_text(
            self._library.lib.vev_tx_report_edn(self._handle)
        )

    def _require_open(self) -> None:
        if not self._handle:
            raise VevError("transaction report is closed")


class PreparedQuery:
    def __init__(
        self,
        library: Library,
        query_edn: str,
        source_names: list[str] | None = None,
    ):
        self._library = library
        if source_names is None:
            self._handle = library.lib.vev_prepare_query_edn(_bytes(query_edn))
        else:
            encoded = [_bytes(name) for name in source_names]
            array_type = ctypes.c_char_p * len(encoded)
            array = array_type(*encoded)
            self._handle = library.lib.vev_prepare_query_edn_with_sources(
                _bytes(query_edn), array, len(encoded)
            )
        if not self._handle:
            raise VevError("failed to prepare query")
        if not library.lib.vev_prepared_query_ok(self._handle):
            try:
                error = library.owned_text(
                    library.lib.vev_prepared_query_error(self._handle)
                )
            finally:
                library.lib.vev_prepared_query_free(self._handle)
                self._handle = None
            raise VevError(error)

    def close(self) -> None:
        if self._handle:
            self._library.lib.vev_prepared_query_free(self._handle)
            self._handle = None

    def __enter__(self) -> "PreparedQuery":
        return self

    def __exit__(self, _type: object, _value: object, _traceback: object) -> None:
        self.close()

    def __del__(self) -> None:
        self.close()

    def statement(self) -> "Statement":
        self._require_open()
        return Statement(self._library, self)

    def query(self, conn: Connection | DB, inputs_edn: str = "[]") -> "Result":
        self._require_open()
        if isinstance(conn, Connection):
            return conn._query_result(self, inputs_edn)
        return conn.query(self, inputs_edn)

    def rows(self, conn: Connection | DB, inputs_edn: str = "[]") -> list[list[Any]]:
        with self.query(conn, inputs_edn) as result:
            return result.rows()

    def scalar(self, conn: Connection | DB, inputs_edn: str = "[]") -> Any:
        with self.query(conn, inputs_edn) as result:
            return result.scalar()

    def _require_open(self) -> None:
        if not self._handle:
            raise VevError("prepared query is closed")


class Statement:
    def __init__(self, library: Library, prepared: PreparedQuery):
        self._library = library
        self._prepared = prepared
        self._handle = library.lib.vev_stmt_create(prepared._handle)
        if not self._handle:
            raise VevError("failed to create statement")

    def close(self) -> None:
        if self._handle:
            self._library.lib.vev_stmt_free(self._handle)
            self._handle = None

    def __enter__(self) -> "Statement":
        return self

    def __exit__(self, _type: object, _value: object, _traceback: object) -> None:
        self.close()

    def __del__(self) -> None:
        self.close()

    def clear(self) -> None:
        self._require_open()
        self._library.lib.vev_stmt_clear(self._handle)

    def bind(self, *values: object) -> "Statement":
        self.clear()
        for value in values:
            self._bind_one(value)
        return self

    def query(self, conn: Connection | DB) -> "Result":
        self._require_open()
        if isinstance(conn, Connection):
            return conn._query_stmt(self)
        return conn.query_stmt(self)

    def rows(self, conn: Connection | DB) -> list[list[Any]]:
        with self.query(conn) as result:
            return result.rows()

    def scalar(self, conn: Connection | DB) -> Any:
        with self.query(conn) as result:
            return result.scalar()

    def error(self) -> str:
        self._require_open()
        return self._library.owned_text(self._library.lib.vev_stmt_error(self._handle))

    def visit(self, conn: Connection | DB, visitor: object) -> None:
        self._require_open()
        if isinstance(conn, Connection):
            conn._require_open()
            fn = self._library.lib.vev_query_stmt_visit
            owner = conn._handle
        else:
            conn._require_open()
            fn = self._library.lib.vev_query_db_stmt_visit
            owner = conn._handle

        def callback(
            _user: int, event: int, row: int, index: int, value: int
        ) -> bool:
            converted = (
                self._library.value_to_python(value)
                if event in (VEV_RESULT_VISIT_VALUE, VEV_RESULT_VISIT_PULL)
                else None
            )
            return bool(visitor(event, row, index, converted))

        c_callback = RESULT_VISIT_FN(callback)
        if not fn(owner, self._handle, c_callback, None):
            raise VevError(self.error() or "statement visitor failed")

    def stream_rows(self, conn: Connection | DB) -> list[list[Any]]:
        rows: list[list[Any]] = []
        current: list[Any] = []

        def collect(event: int, row: int, index: int, value: Any) -> bool:
            nonlocal current
            if event == VEV_RESULT_VISIT_ROW_BEGIN:
                current = []
            elif event in (VEV_RESULT_VISIT_VALUE, VEV_RESULT_VISIT_PULL):
                current.append(value)
            elif event == VEV_RESULT_VISIT_ROW_END:
                rows.append(current)
            return True

        self.visit(conn, collect)
        return rows

    def _bind_one(self, value: object) -> None:
        lib = self._library.lib
        if isinstance(value, Entity):
            ok = lib.vev_stmt_bind_entity(self._handle, value.id)
        elif isinstance(value, LookupRef):
            ok = self._bind_lookup_ref(value)
        elif isinstance(value, TypedTuple):
            ok = self._bind_homogeneous_values(value.values, "tuple", value_type=value.value_type)
        elif isinstance(value, TupleInput):
            ok = self._bind_tuple(value.values)
        elif isinstance(value, TypedRelation):
            ok = self._bind_typed_relation(value)
        elif isinstance(value, Relation):
            ok = self._bind_relation(value.rows)
        elif isinstance(value, TypedCollection):
            ok = self._bind_homogeneous_values(
                value.values, "collection", value_type=value.value_type
            )
        elif isinstance(value, PullPattern):
            ok = lib.vev_stmt_bind_pull_pattern_edn(self._handle, _bytes(value.edn))
        elif isinstance(value, DBSource):
            value.db._require_open()
            ok = lib.vev_stmt_bind_db_source(
                self._handle, _bytes(value.name), value.db._handle
            )
        elif isinstance(value, bool):
            ok = lib.vev_stmt_bind_bool(self._handle, value)
        elif isinstance(value, int):
            ok = lib.vev_stmt_bind_int(self._handle, value)
        elif isinstance(value, Keyword):
            ok = lib.vev_stmt_bind_keyword(self._handle, _bytes(value.text))
        elif isinstance(value, Symbol):
            ok = lib.vev_stmt_bind_symbol(self._handle, _bytes(value.text))
        elif isinstance(value, str):
            ok = lib.vev_stmt_bind_string(self._handle, _bytes(value))
        elif isinstance(value, (list, tuple)):
            ok = self._bind_collection(value)
        else:
            raise TypeError(f"unsupported Vev statement binding: {value!r}")
        if not ok:
            raise VevError(f"failed to bind statement value: {value!r}")

    def _bind_lookup_ref(self, ref: LookupRef) -> bool:
        lib = self._library.lib
        attr = _bytes(ref.attr)
        value = ref.value
        if isinstance(value, Entity):
            return bool(lib.vev_stmt_bind_lookup_ref_entity(self._handle, attr, value.id))
        if isinstance(value, int) and not isinstance(value, bool):
            return bool(lib.vev_stmt_bind_lookup_ref_int(self._handle, attr, value))
        if isinstance(value, Keyword):
            return bool(
                lib.vev_stmt_bind_lookup_ref_keyword(self._handle, attr, _bytes(value.text))
            )
        if isinstance(value, str):
            return bool(lib.vev_stmt_bind_lookup_ref_string(self._handle, attr, _bytes(value)))
        raise TypeError(f"unsupported Vev lookup-ref binding: {ref!r}")

    def _bind_tuple(self, values: tuple[object, ...]) -> bool:
        return self._bind_homogeneous_values(values, "tuple")

    def _bind_typed_relation(self, relation: TypedRelation) -> bool:
        if relation.width <= 0:
            raise TypeError(f"typed relation width must be positive: {relation!r}")
        if len(relation.rows) == 0:
            return self._bind_homogeneous_values(
                (), "relation", relation.width, value_type=relation.value_type
            )
        if any(len(row) != relation.width for row in relation.rows):
            raise TypeError(f"relation rows must match declared width: {relation!r}")
        flat = tuple(value for row in relation.rows for value in row)
        return self._bind_homogeneous_values(
            flat, "relation", relation.width, value_type=relation.value_type
        )

    def _bind_relation(self, rows: tuple[tuple[object, ...], ...]) -> bool:
        if len(rows) == 0:
            raise TypeError("empty relation bindings need an explicit typed wrapper")
        width = len(rows[0])
        if width == 0 or any(len(row) != width for row in rows):
            raise TypeError(f"relation rows must have a stable non-zero width: {rows!r}")
        flat = tuple(value for row in rows for value in row)
        return self._bind_homogeneous_values(flat, "relation", width)

    def _bind_homogeneous_values(
        self,
        values: tuple[object, ...],
        kind: str,
        width: int | None = None,
        value_type: str | None = None,
    ) -> bool:
        lib = self._library.lib
        suffix = "_relation" if kind == "relation" else f"_{kind}"
        if len(values) == 0:
            if value_type is None:
                raise TypeError(f"empty {kind} bindings need an explicit typed wrapper")
            if value_type == "entity":
                fn = getattr(lib, f"vev_stmt_bind_entity{suffix}")
            elif value_type == "bool":
                fn = getattr(lib, f"vev_stmt_bind_bool{suffix}")
            elif value_type == "int":
                fn = getattr(lib, f"vev_stmt_bind_int{suffix}")
            elif value_type == "string":
                fn = getattr(lib, f"vev_stmt_bind_string{suffix}")
            else:
                raise TypeError(f"unsupported typed {kind} value type: {value_type!r}")
            if kind == "relation":
                assert width is not None
                return bool(fn(self._handle, None, 0, width))
            return bool(fn(self._handle, None, 0))
        if value_type is not None and not all(_value_matches_type(value, value_type) for value in values):
            raise TypeError(f"typed {kind} contains values outside {value_type!r}: {values!r}")
        if all(isinstance(value, Entity) for value in values):
            array_type = ctypes.c_ulonglong * len(values)
            array = array_type(*(value.id for value in values))
            fn = getattr(lib, f"vev_stmt_bind_entity{suffix}")
        elif all(isinstance(value, bool) for value in values):
            array_type = ctypes.c_bool * len(values)
            array = array_type(*values)
            fn = getattr(lib, f"vev_stmt_bind_bool{suffix}")
        elif all(isinstance(value, int) and not isinstance(value, bool) for value in values):
            array_type = ctypes.c_longlong * len(values)
            array = array_type(*values)
            fn = getattr(lib, f"vev_stmt_bind_int{suffix}")
        elif all(isinstance(value, str) for value in values):
            encoded = [_bytes(value) for value in values]
            array_type = ctypes.c_char_p * len(encoded)
            array = array_type(*encoded)
            fn = getattr(lib, f"vev_stmt_bind_string{suffix}")
        else:
            raise TypeError(f"unsupported homogeneous {kind} binding: {values!r}")
        if kind == "relation":
            assert width is not None
            return bool(fn(self._handle, array, len(values), width))
        return bool(fn(self._handle, array, len(values)))

    def _bind_collection(self, values: list[object] | tuple[object, ...]) -> bool:
        lib = self._library.lib
        if len(values) == 0:
            raise TypeError("empty collection bindings need an explicit typed wrapper")
        if all(isinstance(value, LookupRef) for value in values):
            attrs = {value.attr for value in values}
            if len(attrs) != 1:
                raise TypeError("lookup-ref collection bindings require one attr")
            lookup_values = tuple(value.value for value in values)
            attr = _bytes(values[0].attr)
            if all(isinstance(value, Entity) for value in lookup_values):
                array_type = ctypes.c_ulonglong * len(lookup_values)
                array = array_type(*(value.id for value in lookup_values))
                return bool(
                    lib.vev_stmt_bind_lookup_ref_entity_collection(
                        self._handle, attr, array, len(lookup_values)
                    )
                )
            if all(isinstance(value, int) and not isinstance(value, bool) for value in lookup_values):
                array_type = ctypes.c_longlong * len(lookup_values)
                array = array_type(*lookup_values)
                return bool(
                    lib.vev_stmt_bind_lookup_ref_int_collection(
                        self._handle, attr, array, len(lookup_values)
                    )
                )
            if all(isinstance(value, Keyword) for value in lookup_values):
                encoded = [_bytes(value.text) for value in lookup_values]
                array_type = ctypes.c_char_p * len(encoded)
                array = array_type(*encoded)
                return bool(
                    lib.vev_stmt_bind_lookup_ref_keyword_collection(
                        self._handle, attr, array, len(encoded)
                    )
                )
            if all(isinstance(value, str) for value in lookup_values):
                encoded = [_bytes(value) for value in lookup_values]
                array_type = ctypes.c_char_p * len(encoded)
                array = array_type(*encoded)
                return bool(
                    lib.vev_stmt_bind_lookup_ref_string_collection(
                        self._handle, attr, array, len(encoded)
                    )
                )
            raise TypeError(f"unsupported lookup-ref collection binding: {values!r}")
        if all(isinstance(value, Entity) for value in values):
            array_type = ctypes.c_ulonglong * len(values)
            array = array_type(*(value.id for value in values))
            return bool(lib.vev_stmt_bind_entity_collection(self._handle, array, len(values)))
        if all(isinstance(value, bool) for value in values):
            array_type = ctypes.c_bool * len(values)
            array = array_type(*values)
            return bool(lib.vev_stmt_bind_bool_collection(self._handle, array, len(values)))
        if all(isinstance(value, int) and not isinstance(value, bool) for value in values):
            array_type = ctypes.c_longlong * len(values)
            array = array_type(*values)
            return bool(lib.vev_stmt_bind_int_collection(self._handle, array, len(values)))
        if all(isinstance(value, str) for value in values):
            encoded = [_bytes(value) for value in values]
            array_type = ctypes.c_char_p * len(encoded)
            array = array_type(*encoded)
            return bool(lib.vev_stmt_bind_string_collection(self._handle, array, len(encoded)))
        raise TypeError(f"unsupported Vev collection binding: {values!r}")

    def _require_open(self) -> None:
        if not self._handle:
            raise VevError("statement is closed")


@dataclass(frozen=True)
class Keyword:
    text: str


@dataclass(frozen=True)
class Symbol:
    text: str


class Result:
    def __init__(self, library: Library, handle: int):
        self._library = library
        self._handle = handle
        if not self._handle:
            raise VevError("query returned a null result")
        if not library.lib.vev_result_ok(self._handle):
            try:
                error = library.owned_text(library.lib.vev_result_error(self._handle))
            finally:
                library.lib.vev_result_free(self._handle)
                self._handle = None
            raise VevError(error)

    def close(self) -> None:
        if self._handle:
            self._library.lib.vev_result_free(self._handle)
            self._handle = None

    def __enter__(self) -> "Result":
        return self

    def __exit__(self, _type: object, _value: object, _traceback: object) -> None:
        self.close()

    def __del__(self) -> None:
        self.close()

    def rows(self) -> list[list[Any]]:
        self._require_open()
        out = []
        for row in range(self._library.lib.vev_result_row_count(self._handle)):
            values = [
                self._library.value_to_python(
                    self._library.lib.vev_result_value(self._handle, row, column)
                )
                for column in range(self._library.lib.vev_result_value_count(self._handle, row))
            ]
            values.extend(self.pulls(row))
            out.append(values)
        return out

    def pulls(self, row: int) -> list[Any]:
        self._require_open()
        return [
            self._library.value_to_python(
                self._library.lib.vev_result_pull(self._handle, row, index)
            )
            for index in range(self._library.lib.vev_result_pull_count(self._handle, row))
        ]

    def scalar(self) -> Any:
        rows = self.rows()
        if len(rows) != 1 or len(rows[0]) != 1:
            raise VevError(f"expected one scalar result, got {rows!r}")
        return rows[0][0]

    def _require_open(self) -> None:
        if not self._handle:
            raise VevError("result is closed")
