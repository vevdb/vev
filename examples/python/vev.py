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


@dataclass(frozen=True)
class Entity:
    id: int


class VevError(RuntimeError):
    pass


def _default_library_path() -> pathlib.Path:
    root = pathlib.Path(__file__).resolve().parents[2]
    return root / "build" / "lib" / "libvev.dylib"


def _bytes(text: str) -> bytes:
    return text.encode("utf-8")


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

    def prepare(self, query_edn: str) -> "PreparedQuery":
        return PreparedQuery(self._library, query_edn)

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

    def _require_open(self) -> None:
        if not self._handle:
            raise VevError("DB snapshot is closed")


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
    def __init__(self, library: Library, query_edn: str):
        self._library = library
        self._handle = library.lib.vev_prepare_query_edn(_bytes(query_edn))
        if not self._handle:
            raise VevError("failed to prepare query")

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

    def _bind_one(self, value: object) -> None:
        lib = self._library.lib
        if isinstance(value, Entity):
            ok = lib.vev_stmt_bind_entity(self._handle, value.id)
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

    def _bind_collection(self, values: list[object] | tuple[object, ...]) -> bool:
        lib = self._library.lib
        if len(values) == 0:
            raise TypeError("empty collection bindings need an explicit typed wrapper")
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
