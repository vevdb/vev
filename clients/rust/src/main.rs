// Copyright (c) Andreas Flakstad and Vev contributors
// SPDX-License-Identifier: EPL-2.0

use std::ffi::{CStr, CString};
use std::os::raw::{c_char, c_double, c_int, c_ulonglong, c_void};
use std::ptr;
use std::slice;

type VevConn = *mut c_void;
type VevConnection = *mut c_void;
type VevColumnBatch = *mut c_void;
type VevDb = *mut c_void;
type VevPreparedQuery = *mut c_void;
type VevResult = *mut c_void;
type VevStmt = *mut c_void;
type VevTxReport = *mut c_void;
type VevU64Array = *mut c_void;
type VevValue = *const c_void;
type VevValueHandle = *mut c_void;

const VEV_VALUE_NIL: c_int = 0;
const VEV_VALUE_ENTITY: c_int = 1;
const VEV_VALUE_STRING: c_int = 2;
const VEV_VALUE_INT: c_int = 3;
const VEV_VALUE_FLOAT: c_int = 4;
const VEV_VALUE_BOOL: c_int = 5;
const VEV_VALUE_KEYWORD: c_int = 6;
const VEV_VALUE_SYMBOL: c_int = 7;
const VEV_VALUE_VECTOR: c_int = 8;
const VEV_VALUE_MAP: c_int = 9;
const VEV_VALUE_UUID: c_int = 10;

const VEV_COLUMN_BATCH_ENTITY: c_int = 1;
const VEV_COLUMN_BATCH_STRING: c_int = 2;
const VEV_COLUMN_BATCH_ENTITY_INT: c_int = 3;
const VEV_COLUMN_BATCH_ENTITY_STRING_INT: c_int = 4;

const VEV_COLUMN_ENTITY: c_int = 1;
const VEV_COLUMN_STRING: c_int = 2;
const VEV_COLUMN_INT: c_int = 3;

#[link(name = "vev")]
unsafe extern "C" {
    fn vev_version() -> *const c_char;

    fn vev_conn_open_memory() -> VevConn;
    fn vev_conn_close(conn: VevConn);
    fn vev_conn_db(conn: VevConn) -> VevDb;
    fn vev_conn_from_db(db: VevDb) -> VevConn;
    fn vev_connect(path: *const c_char) -> VevConnection;
    fn vev_connection_ok(conn: VevConnection) -> bool;
    fn vev_connection_error(conn: VevConnection) -> *const c_char;
    fn vev_connection_backend(conn: VevConnection) -> *const c_char;
    fn vev_connection_path(conn: VevConnection) -> *const c_char;
    fn vev_connection_basis_t(conn: VevConnection) -> c_ulonglong;
    fn vev_connection_tx_count(conn: VevConnection) -> c_ulonglong;
    fn vev_connection_tx_ids(conn: VevConnection) -> VevU64Array;
    fn vev_connection_info_edn(conn: VevConnection) -> *const c_char;
    fn vev_connection_close(conn: VevConnection);
    fn vev_connection_db(conn: VevConnection) -> VevDb;
    fn vev_connection_transact_edn_report(
        conn: VevConnection,
        tx_text: *const c_char,
    ) -> VevTxReport;
    fn vev_db_release(db: VevDb);
    fn vev_u64_array_free(array: VevU64Array);
    fn vev_u64_array_count(array: VevU64Array) -> c_int;
    fn vev_u64_array_value(array: VevU64Array, index: c_int) -> c_ulonglong;
    fn vev_with_edn_report(db: VevDb, tx_text: *const c_char) -> VevTxReport;
    fn vev_db_with_edn(db: VevDb, tx_text: *const c_char) -> VevDb;

    fn vev_string_free(text: *const c_char);

    fn vev_transact_edn(conn: VevConn, tx_text: *const c_char) -> *const c_char;
    fn vev_transact_edn_report(conn: VevConn, tx_text: *const c_char) -> VevTxReport;
    fn vev_tx_report_free(report: VevTxReport);
    fn vev_tx_report_value(report: VevTxReport) -> VevValue;
    fn vev_tx_report_edn(report: VevTxReport) -> *const c_char;
    fn vev_query_edn_with_inputs(
        conn: VevConn,
        query_text: *const c_char,
        inputs_text: *const c_char,
    ) -> *const c_char;

    fn vev_prepare_query_edn(query_text: *const c_char) -> VevPreparedQuery;
    fn vev_prepared_query_edn(query: VevPreparedQuery) -> *const c_char;
    fn vev_parse_clause_edn(clause_text: *const c_char) -> *const c_char;
    fn vev_prepared_query_free(query: VevPreparedQuery);

    fn vev_stmt_create(query: VevPreparedQuery) -> VevStmt;
    fn vev_stmt_clear(stmt: VevStmt);
    fn vev_stmt_free(stmt: VevStmt);
    fn vev_stmt_bind_string(stmt: VevStmt, value: *const c_char) -> bool;
    fn vev_stmt_bind_string_collection(
        stmt: VevStmt,
        values: *const *const c_char,
        value_count: c_int,
    ) -> bool;
    fn vev_stmt_bind_pull_pattern_edn(stmt: VevStmt, pattern_text: *const c_char) -> bool;

    fn vev_query_stmt_result(conn: VevConn, stmt: VevStmt) -> VevResult;
    fn vev_query_db_stmt_result(db: VevDb, stmt: VevStmt) -> VevResult;
    fn vev_query_stmt_column_batch(conn: VevConn, stmt: VevStmt) -> VevColumnBatch;
    fn vev_query_db_stmt_column_batch(db: VevDb, stmt: VevStmt) -> VevColumnBatch;
    fn vev_query_prepared_result_with_inputs(
        conn: VevConn,
        query: VevPreparedQuery,
        inputs_text: *const c_char,
    ) -> VevResult;
    fn vev_query_db_prepared_result_with_inputs(
        db: VevDb,
        query: VevPreparedQuery,
        inputs_text: *const c_char,
    ) -> VevResult;
    fn vev_query_db_prepared_column_batch_with_inputs(
        db: VevDb,
        query: VevPreparedQuery,
        inputs_text: *const c_char,
    ) -> VevColumnBatch;
    fn vev_column_batch_free(batch: VevColumnBatch);
    fn vev_column_batch_kind(batch: VevColumnBatch) -> c_int;
    fn vev_column_batch_count(batch: VevColumnBatch) -> c_int;
    fn vev_column_batch_entities_data(batch: VevColumnBatch) -> *const c_ulonglong;
    fn vev_column_batch_ints_data(batch: VevColumnBatch) -> *const i64;
    fn vev_column_batch_string_data_array(batch: VevColumnBatch) -> *const *const c_void;
    fn vev_column_batch_string_lengths_data(batch: VevColumnBatch) -> *const c_int;
    fn vev_column_batch_string_dictionary_count(batch: VevColumnBatch) -> c_int;
    fn vev_column_batch_string_dictionary_data_array(batch: VevColumnBatch)
        -> *const *const c_void;
    fn vev_column_batch_string_dictionary_lengths_data(batch: VevColumnBatch) -> *const c_int;
    fn vev_column_batch_string_indices_data(batch: VevColumnBatch) -> *const c_int;
    fn vev_pull_edn(db: VevDb, pattern_text: *const c_char, entity: c_ulonglong) -> VevValueHandle;
    fn vev_pull_lookup_ref_string_edn(
        db: VevDb,
        pattern_text: *const c_char,
        attr: *const c_char,
        value: *const c_char,
    ) -> VevValueHandle;
    fn vev_pull_many_edn(
        db: VevDb,
        pattern_text: *const c_char,
        entities: *const c_ulonglong,
        entity_count: c_int,
    ) -> VevValueHandle;
    fn vev_value_handle_free(handle: VevValueHandle);
    fn vev_value_handle_value(handle: VevValueHandle) -> VevValue;
    fn vev_value_handle_edn(handle: VevValueHandle) -> *const c_char;

    fn vev_result_free(result: VevResult);
    fn vev_result_ok(result: VevResult) -> bool;
    fn vev_result_error(result: VevResult) -> *const c_char;
    fn vev_result_row_count(result: VevResult) -> c_int;
    fn vev_result_value_count(result: VevResult, row: c_int) -> c_int;
    fn vev_result_value(result: VevResult, row: c_int, column: c_int) -> VevValue;
    fn vev_result_pull_count(result: VevResult, row: c_int) -> c_int;
    fn vev_result_pull(result: VevResult, row: c_int, pull: c_int) -> VevValue;

    fn vev_value_kind(value: VevValue) -> c_int;
    fn vev_value_entity(value: VevValue) -> c_ulonglong;
    fn vev_value_int(value: VevValue) -> i64;
    fn vev_value_float(value: VevValue) -> c_double;
    fn vev_value_bool(value: VevValue) -> bool;
    fn vev_value_text(value: VevValue) -> *const c_char;
    fn vev_value_edn(value: VevValue) -> *const c_char;
    fn vev_value_item_count(value: VevValue) -> c_int;
    fn vev_value_item(value: VevValue, index: c_int) -> VevValue;
    fn vev_value_map_count(value: VevValue) -> c_int;
    fn vev_value_map_key(value: VevValue, index: c_int) -> VevValue;
    fn vev_value_map_value(value: VevValue, index: c_int) -> VevValue;
}

#[derive(Debug, Clone, PartialEq)]
enum Value {
    Nil,
    Entity(u64),
    String(String),
    Int(i64),
    Float(f64),
    Bool(bool),
    Keyword(String),
    Symbol(String),
    Uuid(String),
    Vector(Vec<Value>),
    Map(Vec<(Value, Value)>),
}

impl Value {
    fn map_get(&self, key: &str) -> Option<&Value> {
        match self {
            Value::Map(items) => items.iter().find_map(|(k, v)| match k {
                Value::Keyword(text)
                | Value::String(text)
                | Value::Symbol(text)
                | Value::Uuid(text)
                    if text == key =>
                {
                    Some(v)
                }
                _ => None,
            }),
            _ => None,
        }
    }
}

struct Library;

impl Library {
    unsafe fn owned_string(ptr: *const c_char) -> String {
        if ptr.is_null() {
            return String::new();
        }
        let out = unsafe { CStr::from_ptr(ptr) }
            .to_string_lossy()
            .into_owned();
        unsafe { vev_string_free(ptr) };
        out
    }

    unsafe fn borrowed_string(ptr: *const c_char) -> String {
        if ptr.is_null() {
            return String::new();
        }
        unsafe { CStr::from_ptr(ptr) }
            .to_string_lossy()
            .into_owned()
    }

    unsafe fn borrowed_utf8(ptr: *const c_void, len: c_int) -> String {
        if ptr.is_null() || len <= 0 {
            return String::new();
        }
        let bytes = unsafe { slice::from_raw_parts(ptr as *const u8, len as usize) };
        String::from_utf8_lossy(bytes).into_owned()
    }

    unsafe fn string_column(batch: VevColumnBatch, count: usize) -> Vec<String> {
        let dictionary_count = unsafe { vev_column_batch_string_dictionary_count(batch) };
        let dictionary_data = unsafe { vev_column_batch_string_dictionary_data_array(batch) };
        let dictionary_lengths = unsafe { vev_column_batch_string_dictionary_lengths_data(batch) };
        let string_indices = unsafe { vev_column_batch_string_indices_data(batch) };
        if dictionary_count > 0
            && !dictionary_data.is_null()
            && !dictionary_lengths.is_null()
            && !string_indices.is_null()
        {
            let data = unsafe { slice::from_raw_parts(dictionary_data, dictionary_count as usize) };
            let lengths =
                unsafe { slice::from_raw_parts(dictionary_lengths, dictionary_count as usize) };
            let indices = unsafe { slice::from_raw_parts(string_indices, count) };
            let dictionary: Vec<String> = data
                .iter()
                .zip(lengths.iter())
                .map(|(text, len)| unsafe { Self::borrowed_utf8(*text, *len) })
                .collect();
            return indices
                .iter()
                .map(|index| dictionary[*index as usize].clone())
                .collect();
        }

        let string_data = unsafe { vev_column_batch_string_data_array(batch) };
        let string_lengths = unsafe { vev_column_batch_string_lengths_data(batch) };
        if string_data.is_null() || string_lengths.is_null() {
            return Vec::new();
        }
        let data = unsafe { slice::from_raw_parts(string_data, count) };
        let lengths = unsafe { slice::from_raw_parts(string_lengths, count) };
        data.iter()
            .zip(lengths.iter())
            .map(|(text, len)| unsafe { Self::borrowed_utf8(*text, *len) })
            .collect()
    }

    unsafe fn value_to_rust(value: VevValue) -> Value {
        match unsafe { vev_value_kind(value) } {
            VEV_VALUE_NIL => Value::Nil,
            VEV_VALUE_ENTITY => Value::Entity(unsafe { vev_value_entity(value) } as u64),
            VEV_VALUE_STRING => Value::String(unsafe { Self::owned_string(vev_value_text(value)) }),
            VEV_VALUE_INT => Value::Int(unsafe { vev_value_int(value) }),
            VEV_VALUE_FLOAT => Value::Float(unsafe { vev_value_float(value) }),
            VEV_VALUE_BOOL => Value::Bool(unsafe { vev_value_bool(value) }),
            VEV_VALUE_KEYWORD => {
                Value::Keyword(unsafe { Self::owned_string(vev_value_text(value)) })
            }
            VEV_VALUE_SYMBOL => Value::Symbol(unsafe { Self::owned_string(vev_value_text(value)) }),
            VEV_VALUE_UUID => Value::Uuid(unsafe { Self::owned_string(vev_value_text(value)) }),
            VEV_VALUE_VECTOR => {
                let count = unsafe { vev_value_item_count(value) };
                let mut out = Vec::with_capacity(count as usize);
                for index in 0..count {
                    out.push(unsafe { Self::value_to_rust(vev_value_item(value, index)) });
                }
                Value::Vector(out)
            }
            VEV_VALUE_MAP => {
                let count = unsafe { vev_value_map_count(value) };
                let mut out = Vec::with_capacity(count as usize);
                for index in 0..count {
                    let key = unsafe { Self::value_to_rust(vev_value_map_key(value, index)) };
                    let value = unsafe { Self::value_to_rust(vev_value_map_value(value, index)) };
                    out.push((key, value));
                }
                Value::Map(out)
            }
            _ => Value::String(unsafe { Self::owned_string(vev_value_edn(value)) }),
        }
    }
}

fn cstring(text: &str) -> CString {
    CString::new(text).expect("CString input cannot contain NUL")
}

struct Conn {
    raw: VevConn,
}

impl Conn {
    fn open_memory() -> Result<Self, String> {
        let raw = unsafe { vev_conn_open_memory() };
        if raw.is_null() {
            Err("failed to open Vev connection".to_string())
        } else {
            Ok(Self { raw })
        }
    }

    fn from_db(db: &Db) -> Result<Self, String> {
        let raw = unsafe { vev_conn_from_db(db.raw) };
        if raw.is_null() {
            Err("failed to create connection from DB snapshot".to_string())
        } else {
            Ok(Self { raw })
        }
    }

    fn transact(&self, tx: &str) -> String {
        let tx = cstring(tx);
        unsafe { Library::owned_string(vev_transact_edn(self.raw, tx.as_ptr())) }
    }

    fn transact_report(&self, tx: &str) -> Result<TxReport, String> {
        let tx = cstring(tx);
        let raw = unsafe { vev_transact_edn_report(self.raw, tx.as_ptr()) };
        if raw.is_null() {
            Err("failed to transact".to_string())
        } else {
            Ok(TxReport { raw })
        }
    }

    fn query_text_with_inputs(&self, query: &str, inputs: &str) -> String {
        let query = cstring(query);
        let inputs = cstring(inputs);
        unsafe {
            Library::owned_string(vev_query_edn_with_inputs(
                self.raw,
                query.as_ptr(),
                inputs.as_ptr(),
            ))
        }
    }

    fn prepare(&self, query: &str) -> Result<PreparedQuery, String> {
        PreparedQuery::new(query)
    }

    fn parse_clause_edn(&self, clause: &str) -> String {
        let clause = cstring(clause);
        unsafe { Library::owned_string(vev_parse_clause_edn(clause.as_ptr())) }
    }

    fn db(&self) -> Result<Db, String> {
        let raw = unsafe { vev_conn_db(self.raw) };
        if raw.is_null() {
            Err("failed to retain DB snapshot".to_string())
        } else {
            Ok(Db { raw })
        }
    }
}

impl Drop for Conn {
    fn drop(&mut self) {
        if !self.raw.is_null() {
            unsafe { vev_conn_close(self.raw) };
            self.raw = ptr::null_mut();
        }
    }
}

struct DurableConn {
    raw: VevConnection,
}

impl DurableConn {
    fn open(path: &str) -> Result<Self, String> {
        let path = cstring(path);
        let raw = unsafe { vev_connect(path.as_ptr()) };
        if raw.is_null() {
            return Err("failed to connect Vev durable connection".to_string());
        }
        if unsafe { vev_connection_ok(raw) } {
            Ok(Self { raw })
        } else {
            let error = unsafe { Library::owned_string(vev_connection_error(raw)) };
            unsafe { vev_connection_close(raw) };
            Err(error)
        }
    }

    fn transact_report(&self, tx: &str) -> Result<TxReport, String> {
        let tx = cstring(tx);
        let raw = unsafe { vev_connection_transact_edn_report(self.raw, tx.as_ptr()) };
        if raw.is_null() {
            Err("failed to transact".to_string())
        } else {
            Ok(TxReport { raw })
        }
    }

    fn db(&self) -> Result<Db, String> {
        let raw = unsafe { vev_connection_db(self.raw) };
        if raw.is_null() {
            Err("failed to retain DB snapshot".to_string())
        } else {
            Ok(Db { raw })
        }
    }

    fn backend(&self) -> String {
        unsafe { Library::owned_string(vev_connection_backend(self.raw)) }
    }

    fn path(&self) -> String {
        unsafe { Library::owned_string(vev_connection_path(self.raw)) }
    }

    fn basis_t(&self) -> u64 {
        unsafe { vev_connection_basis_t(self.raw) as u64 }
    }

    fn tx_count(&self) -> u64 {
        unsafe { vev_connection_tx_count(self.raw) as u64 }
    }

    fn tx_ids(&self) -> Vec<u64> {
        let raw = unsafe { vev_connection_tx_ids(self.raw) };
        if raw.is_null() {
            return Vec::new();
        }
        let count = unsafe { vev_u64_array_count(raw) };
        let mut out = Vec::with_capacity(count.max(0) as usize);
        for index in 0..count {
            out.push(unsafe { vev_u64_array_value(raw, index) as u64 });
        }
        unsafe { vev_u64_array_free(raw) };
        out
    }

    fn info_edn(&self) -> String {
        unsafe { Library::owned_string(vev_connection_info_edn(self.raw)) }
    }
}

impl Drop for DurableConn {
    fn drop(&mut self) {
        if !self.raw.is_null() {
            unsafe { vev_connection_close(self.raw) };
            self.raw = ptr::null_mut();
        }
    }
}

struct Db {
    raw: VevDb,
}

impl Db {
    fn query_columns(
        &self,
        query: &PreparedQuery,
        inputs: &str,
    ) -> Result<Option<ColumnResult>, String> {
        let inputs = cstring(inputs);
        let raw = unsafe {
            vev_query_db_prepared_column_batch_with_inputs(self.raw, query.raw, inputs.as_ptr())
        };
        if raw.is_null() {
            return Ok(None);
        }
        let result = unsafe { ColumnResult::from_raw(raw) };
        unsafe { vev_column_batch_free(raw) };
        result
    }

    fn query_stmt_columns(&self, stmt: &Statement<'_>) -> Result<Option<ColumnResult>, String> {
        let raw = unsafe { vev_query_db_stmt_column_batch(self.raw, stmt.raw) };
        if raw.is_null() {
            return Ok(None);
        }
        let result = unsafe { ColumnResult::from_raw(raw) };
        unsafe { vev_column_batch_free(raw) };
        result
    }

    fn with_report(&self, tx: &str) -> Result<TxReport, String> {
        let tx = cstring(tx);
        let raw = unsafe { vev_with_edn_report(self.raw, tx.as_ptr()) };
        if raw.is_null() {
            Err("failed to transact against DB snapshot".to_string())
        } else {
            Ok(TxReport { raw })
        }
    }

    fn db_with(&self, tx: &str) -> Result<Db, String> {
        let tx = cstring(tx);
        let raw = unsafe { vev_db_with_edn(self.raw, tx.as_ptr()) };
        if raw.is_null() {
            Err("failed to create DB snapshot".to_string())
        } else {
            Ok(Db { raw })
        }
    }

    fn pull(&self, pattern: &str, entity: u64) -> Result<Value, String> {
        let pattern = cstring(pattern);
        let raw = unsafe { vev_pull_edn(self.raw, pattern.as_ptr(), entity as c_ulonglong) };
        let handle = ValueHandle::new(raw)?;
        Ok(handle.value())
    }

    fn pull_lookup_ref_string(
        &self,
        pattern: &str,
        attr: &str,
        value: &str,
    ) -> Result<Value, String> {
        let pattern = cstring(pattern);
        let attr = cstring(attr);
        let value = cstring(value);
        let raw = unsafe {
            vev_pull_lookup_ref_string_edn(
                self.raw,
                pattern.as_ptr(),
                attr.as_ptr(),
                value.as_ptr(),
            )
        };
        let handle = ValueHandle::new(raw)?;
        Ok(handle.value())
    }

    fn pull_many(&self, pattern: &str, entities: &[u64]) -> Result<Value, String> {
        let pattern = cstring(pattern);
        let raw = unsafe {
            vev_pull_many_edn(
                self.raw,
                pattern.as_ptr(),
                entities.as_ptr(),
                entities.len() as c_int,
            )
        };
        let handle = ValueHandle::new(raw)?;
        Ok(handle.value())
    }
}

impl Drop for Db {
    fn drop(&mut self) {
        if !self.raw.is_null() {
            unsafe { vev_db_release(self.raw) };
            self.raw = ptr::null_mut();
        }
    }
}

#[derive(Debug, Clone, PartialEq)]
enum Column {
    Entity(Vec<u64>),
    String(Vec<String>),
    Int(Vec<i64>),
}

#[derive(Debug, Clone, PartialEq)]
struct ColumnResult {
    count: usize,
    kinds: Vec<c_int>,
    columns: Vec<Column>,
}

impl ColumnResult {
    unsafe fn from_raw(raw: VevColumnBatch) -> Result<Option<Self>, String> {
        let kind = unsafe { vev_column_batch_kind(raw) };
        let count = unsafe { vev_column_batch_count(raw) }.max(0) as usize;
        let entities_data = unsafe { vev_column_batch_entities_data(raw) };
        let ints_data = unsafe { vev_column_batch_ints_data(raw) };
        let entities = || {
            if entities_data.is_null() {
                Vec::new()
            } else {
                unsafe { slice::from_raw_parts(entities_data, count) }
                    .iter()
                    .map(|value| *value as u64)
                    .collect()
            }
        };
        let ints = || {
            if ints_data.is_null() {
                Vec::new()
            } else {
                unsafe { slice::from_raw_parts(ints_data, count) }.to_vec()
            }
        };
        let strings = || unsafe { Library::string_column(raw, count) };

        match kind {
            VEV_COLUMN_BATCH_ENTITY => Ok(Some(Self {
                count,
                kinds: vec![VEV_COLUMN_ENTITY],
                columns: vec![Column::Entity(entities())],
            })),
            VEV_COLUMN_BATCH_STRING => Ok(Some(Self {
                count,
                kinds: vec![VEV_COLUMN_STRING],
                columns: vec![Column::String(strings())],
            })),
            VEV_COLUMN_BATCH_ENTITY_INT => Ok(Some(Self {
                count,
                kinds: vec![VEV_COLUMN_ENTITY, VEV_COLUMN_INT],
                columns: vec![Column::Entity(entities()), Column::Int(ints())],
            })),
            VEV_COLUMN_BATCH_ENTITY_STRING_INT => Ok(Some(Self {
                count,
                kinds: vec![VEV_COLUMN_ENTITY, VEV_COLUMN_STRING, VEV_COLUMN_INT],
                columns: vec![
                    Column::Entity(entities()),
                    Column::String(strings()),
                    Column::Int(ints()),
                ],
            })),
            _ => Ok(None),
        }
    }

    fn rows(&self) -> Vec<Vec<Value>> {
        let mut out = Vec::with_capacity(self.count);
        for row in 0..self.count {
            let mut values = Vec::with_capacity(self.columns.len());
            for column in &self.columns {
                values.push(match column {
                    Column::Entity(values) => Value::Entity(values[row]),
                    Column::String(values) => Value::String(values[row].clone()),
                    Column::Int(values) => Value::Int(values[row]),
                });
            }
            out.push(values);
        }
        out
    }
}

struct ValueHandle {
    raw: VevValueHandle,
}

impl ValueHandle {
    fn new(raw: VevValueHandle) -> Result<Self, String> {
        if raw.is_null() {
            Err("pull returned null value handle".to_string())
        } else {
            Ok(Self { raw })
        }
    }

    fn value(&self) -> Value {
        unsafe { Library::value_to_rust(vev_value_handle_value(self.raw)) }
    }

    #[allow(dead_code)]
    fn edn(&self) -> String {
        unsafe { Library::owned_string(vev_value_handle_edn(self.raw)) }
    }
}

impl Drop for ValueHandle {
    fn drop(&mut self) {
        if !self.raw.is_null() {
            unsafe { vev_value_handle_free(self.raw) };
            self.raw = ptr::null_mut();
        }
    }
}

struct TxReport {
    raw: VevTxReport,
}

impl TxReport {
    fn value(&self) -> Value {
        unsafe { Library::value_to_rust(vev_tx_report_value(self.raw)) }
    }

    fn edn(&self) -> String {
        unsafe { Library::owned_string(vev_tx_report_edn(self.raw)) }
    }
}

impl Drop for TxReport {
    fn drop(&mut self) {
        if !self.raw.is_null() {
            unsafe { vev_tx_report_free(self.raw) };
            self.raw = ptr::null_mut();
        }
    }
}

struct PreparedQuery {
    raw: VevPreparedQuery,
}

impl PreparedQuery {
    fn new(query: &str) -> Result<Self, String> {
        let query = cstring(query);
        let raw = unsafe { vev_prepare_query_edn(query.as_ptr()) };
        if raw.is_null() {
            Err("failed to prepare query".to_string())
        } else {
            Ok(Self { raw })
        }
    }

    fn statement(&self) -> Result<Statement<'_>, String> {
        let raw = unsafe { vev_stmt_create(self.raw) };
        if raw.is_null() {
            Err("failed to create statement".to_string())
        } else {
            Ok(Statement { raw, _query: self })
        }
    }

    fn edn(&self) -> String {
        unsafe { Library::owned_string(vev_prepared_query_edn(self.raw)) }
    }

    fn query_conn(&self, conn: &Conn, inputs: &str) -> Result<ResultSet, String> {
        let inputs = cstring(inputs);
        let raw =
            unsafe { vev_query_prepared_result_with_inputs(conn.raw, self.raw, inputs.as_ptr()) };
        ResultSet::new(raw)
    }

    fn query_db(&self, db: &Db, inputs: &str) -> Result<ResultSet, String> {
        let inputs = cstring(inputs);
        let raw =
            unsafe { vev_query_db_prepared_result_with_inputs(db.raw, self.raw, inputs.as_ptr()) };
        ResultSet::new(raw)
    }
}

impl Drop for PreparedQuery {
    fn drop(&mut self) {
        if !self.raw.is_null() {
            unsafe { vev_prepared_query_free(self.raw) };
            self.raw = ptr::null_mut();
        }
    }
}

struct Statement<'a> {
    raw: VevStmt,
    _query: &'a PreparedQuery,
}

impl Statement<'_> {
    fn bind_string(&mut self, value: &str) -> Result<&mut Self, String> {
        unsafe { vev_stmt_clear(self.raw) };
        let value = cstring(value);
        if unsafe { vev_stmt_bind_string(self.raw, value.as_ptr()) } {
            Ok(self)
        } else {
            Err("failed to bind string".to_string())
        }
    }

    fn bind_string_collection(&mut self, values: &[&str]) -> Result<&mut Self, String> {
        unsafe { vev_stmt_clear(self.raw) };
        let owned: Vec<CString> = values.iter().map(|value| cstring(value)).collect();
        let ptrs: Vec<*const c_char> = owned.iter().map(|value| value.as_ptr()).collect();
        if unsafe { vev_stmt_bind_string_collection(self.raw, ptrs.as_ptr(), ptrs.len() as c_int) }
        {
            Ok(self)
        } else {
            Err("failed to bind string collection".to_string())
        }
    }

    fn bind_pull_pattern_and_string(
        &mut self,
        pattern: &str,
        value: &str,
    ) -> Result<&mut Self, String> {
        unsafe { vev_stmt_clear(self.raw) };
        let pattern = cstring(pattern);
        let value = cstring(value);
        if unsafe {
            vev_stmt_bind_pull_pattern_edn(self.raw, pattern.as_ptr())
                && vev_stmt_bind_string(self.raw, value.as_ptr())
        } {
            Ok(self)
        } else {
            Err("failed to bind pull pattern and string".to_string())
        }
    }

    fn query_conn(&self, conn: &Conn) -> Result<ResultSet, String> {
        let raw = unsafe { vev_query_stmt_result(conn.raw, self.raw) };
        ResultSet::new(raw)
    }

    fn query_db(&self, db: &Db) -> Result<ResultSet, String> {
        let raw = unsafe { vev_query_db_stmt_result(db.raw, self.raw) };
        ResultSet::new(raw)
    }

    fn columns_conn(&self, conn: &Conn) -> Result<Option<ColumnResult>, String> {
        let raw = unsafe { vev_query_stmt_column_batch(conn.raw, self.raw) };
        if raw.is_null() {
            return Ok(None);
        }
        let result = unsafe { ColumnResult::from_raw(raw) };
        unsafe { vev_column_batch_free(raw) };
        result
    }

    fn columns_db(&self, db: &Db) -> Result<Option<ColumnResult>, String> {
        db.query_stmt_columns(self)
    }
}

impl Drop for Statement<'_> {
    fn drop(&mut self) {
        if !self.raw.is_null() {
            unsafe { vev_stmt_free(self.raw) };
            self.raw = ptr::null_mut();
        }
    }
}

struct ResultSet {
    raw: VevResult,
}

impl ResultSet {
    fn new(raw: VevResult) -> Result<Self, String> {
        if raw.is_null() {
            return Err("query returned null result".to_string());
        }
        if unsafe { vev_result_ok(raw) } {
            Ok(Self { raw })
        } else {
            let error = unsafe { Library::owned_string(vev_result_error(raw)) };
            unsafe { vev_result_free(raw) };
            Err(error)
        }
    }

    fn row_count(&self) -> usize {
        unsafe { vev_result_row_count(self.raw) as usize }
    }

    fn rows(&self) -> Vec<Vec<Value>> {
        let mut out = Vec::with_capacity(self.row_count());
        for row in 0..self.row_count() as c_int {
            let mut values = Vec::new();
            let value_count = unsafe { vev_result_value_count(self.raw, row) };
            for column in 0..value_count {
                values.push(unsafe {
                    Library::value_to_rust(vev_result_value(self.raw, row, column))
                });
            }
            let pull_count = unsafe { vev_result_pull_count(self.raw, row) };
            for pull in 0..pull_count {
                values
                    .push(unsafe { Library::value_to_rust(vev_result_pull(self.raw, row, pull)) });
            }
            out.push(values);
        }
        out
    }

    fn scalar(&self) -> Result<Value, String> {
        let rows = self.rows();
        if rows.len() == 1 && rows[0].len() == 1 {
            Ok(rows[0][0].clone())
        } else {
            Err(format!("expected one scalar result, got {rows:?}"))
        }
    }
}

impl Drop for ResultSet {
    fn drop(&mut self) {
        if !self.raw.is_null() {
            unsafe { vev_result_free(self.raw) };
            self.raw = ptr::null_mut();
        }
    }
}

fn remove_sqlite_files(path: &str) {
    let _ = std::fs::remove_file(path);
    let _ = std::fs::remove_file(format!("{path}-wal"));
    let _ = std::fs::remove_file(format!("{path}-shm"));
}

fn main() -> Result<(), String> {
    let version = unsafe { Library::borrowed_string(vev_version()) };
    println!("version: {version}");

    let conn = Conn::open_memory()?;
    let tx = conn.transact_report(
        r#"[{:db/id 1 :user/name "Ada" :user/email "ada@example.com"}
            {:db/id 2 :user/name "Grace" :user/email "grace@example.com"}]"#,
    )?;
    println!("tx: {}", tx.edn());
    let tx_value = tx.value();
    let tx_data_count = tx_value
        .map_get(":tx-data")
        .and_then(|value| match value {
            Value::Vector(items) => Some(items.len()),
            _ => None,
        })
        .unwrap_or(0);
    if tx_value.map_get(":ok") != Some(&Value::Bool(true)) || tx_data_count != 4 {
        return Err("unexpected typed transaction report".to_string());
    }

    let collection_text = conn.query_text_with_inputs(
        r#"[:find ?name
           :in [?email ...]
           :where [?e :user/email ?email]
                  [?e :user/name ?name]]"#,
        r#"[["ada@example.com" "grace@example.com"]]"#,
    );
    println!("input-collection: {collection_text}");
    if !collection_text.contains("\"Ada\"") || !collection_text.contains("\"Grace\"") {
        return Err("unexpected collection query output".to_string());
    }

    conn.transact(
        r#"[[:db/add 90 :db/ident :user/email]
            [:db/add 90 :db/unique :db.unique/identity]]"#,
    );

    conn.transact(
        r#"[[:db/add 100 :db/ident :user/friend]
            [:db/add 100 :db/valueType :db.type/ref]
            [:db/add 1 :user/friend 2]]"#,
    );

    let email_query = conn.prepare(
        r#"[:find ?e ?email
           :in ?needle
           :where [?e :user/email ?email]
                  [(= ?email ?needle)]]"#,
    )?;
    let prepared_ast = email_query.edn();
    if !prepared_ast.contains(":clauses") || !prepared_ast.contains(":input-specs") {
        return Err("prepared query AST did not expose parser keys".to_string());
    }
    let clause_ast = conn.parse_clause_edn("[?e :user/email ?email]");
    if !clause_ast.contains(":clauses") || !clause_ast.contains(":user/email") {
        return Err("parse-clause AST did not expose parser keys".to_string());
    }
    let mut stmt = email_query.statement()?;
    let rows = stmt
        .bind_string("grace@example.com")?
        .query_conn(&conn)?
        .rows();
    println!("statement rows: {rows:?}");
    if rows
        != vec![vec![
            Value::Entity(2),
            Value::String("grace@example.com".to_string()),
        ]]
    {
        return Err("unexpected statement rows".to_string());
    }

    let collection_query = conn.prepare(
        r#"[:find ?name
           :in [?email ...]
           :where [?e :user/email ?email]
                  [?e :user/name ?name]]"#,
    )?;
    let mut collection_stmt = collection_query.statement()?;
    let rows = collection_stmt
        .bind_string_collection(&["ada@example.com", "grace@example.com"])?
        .query_conn(&conn)?
        .rows();
    println!("statement collection rows: {rows:?}");
    let mut names: Vec<String> = rows
        .iter()
        .filter_map(|row| match row.first() {
            Some(Value::String(name)) => Some(name.clone()),
            _ => None,
        })
        .collect();
    names.sort();
    if names != vec!["Ada".to_string(), "Grace".to_string()] {
        return Err("unexpected collection statement rows".to_string());
    }

    let all_email_texts = conn.prepare(r#"[:find ?email :where [?e :user/email ?email]]"#)?;
    let column_db = conn.db()?;
    let columns = column_db
        .query_columns(&all_email_texts, "[]")?
        .ok_or_else(|| "expected string column batch".to_string())?;
    let mut column_rows = columns.rows();
    column_rows.sort_by(|left, right| format!("{left:?}").cmp(&format!("{right:?}")));
    println!("column batch rows: {column_rows:?}");
    if columns.kinds != vec![VEV_COLUMN_STRING]
        || column_rows
            != vec![
                vec![Value::String("ada@example.com".to_string())],
                vec![Value::String("grace@example.com".to_string())],
            ]
    {
        return Err("unexpected column batch rows".to_string());
    }
    let all_email_stmt = all_email_texts.statement()?;
    let live_columns = all_email_stmt
        .columns_conn(&conn)?
        .ok_or_else(|| "expected live statement column batch".to_string())?;
    let mut live_rows = live_columns.rows();
    live_rows.sort_by(|left, right| format!("{left:?}").cmp(&format!("{right:?}")));
    if live_columns.kinds != vec![VEV_COLUMN_STRING] || live_rows != column_rows {
        return Err("unexpected live statement column batch rows".to_string());
    }
    let snapshot_columns = all_email_stmt
        .columns_db(&column_db)?
        .ok_or_else(|| "expected snapshot statement column batch".to_string())?;
    let mut snapshot_column_rows = snapshot_columns.rows();
    snapshot_column_rows.sort_by(|left, right| format!("{left:?}").cmp(&format!("{right:?}")));
    if snapshot_columns.kinds != vec![VEV_COLUMN_STRING] || snapshot_column_rows != column_rows {
        return Err("unexpected snapshot statement column batch rows".to_string());
    }

    let pull_query = conn.prepare(
        r#"[:find (pull ?e [:user/name {:user/friend [:user/name]}])
           :where [?e :user/name "Ada"]]"#,
    )?;
    let pulled = pull_query.query_conn(&conn, "[]")?.scalar()?;
    println!("pull: {pulled:?}");
    let friend_name = pulled
        .map_get(":user/friend")
        .and_then(|friend| friend.map_get(":user/name"));
    if pulled.map_get(":user/name") != Some(&Value::String("Ada".to_string()))
        || friend_name != Some(&Value::String("Grace".to_string()))
    {
        return Err("unexpected pull result".to_string());
    }

    let pull_db = conn.db()?;
    let direct_pull = pull_db.pull("[:user/name {:user/friend [:user/name]}]", 1)?;
    println!("direct pull: {direct_pull:?}");
    let direct_friend = direct_pull
        .map_get(":user/friend")
        .and_then(|friend| friend.map_get(":user/name"));
    if direct_pull.map_get(":user/name") != Some(&Value::String("Ada".to_string()))
        || direct_friend != Some(&Value::String("Grace".to_string()))
    {
        return Err("unexpected direct pull".to_string());
    }

    let lookup_pull =
        pull_db.pull_lookup_ref_string("[:user/name]", ":user/email", "ada@example.com")?;
    println!("lookup pull: {lookup_pull:?}");
    if lookup_pull.map_get(":user/name") != Some(&Value::String("Ada".to_string())) {
        return Err("unexpected lookup-ref pull".to_string());
    }

    let many_pull = pull_db.pull_many("[:user/name]", &[1, 2])?;
    println!("pull many: {many_pull:?}");
    let mut many_names: Vec<String> = match many_pull {
        Value::Vector(items) => items
            .iter()
            .filter_map(|item| match item.map_get(":user/name") {
                Some(Value::String(name)) => Some(name.clone()),
                _ => None,
            })
            .collect(),
        _ => Vec::new(),
    };
    many_names.sort();
    if many_names != vec!["Ada".to_string(), "Grace".to_string()] {
        return Err("unexpected pull-many".to_string());
    }

    let pull_pattern_query = conn.prepare(
        r#"[:find (pull ?e ?pattern)
           :in ?pattern ?name
           :where [?e :user/name ?name]]"#,
    )?;
    let mut pull_pattern_stmt = pull_pattern_query.statement()?;
    let bound_pull = pull_pattern_stmt
        .bind_pull_pattern_and_string("[:user/name {:user/friend [:user/name]}]", "Ada")?
        .query_conn(&conn)?
        .scalar()?;
    println!("statement pull pattern: {bound_pull:?}");
    let bound_friend = bound_pull
        .map_get(":user/friend")
        .and_then(|friend| friend.map_get(":user/name"));
    if bound_pull.map_get(":user/name") != Some(&Value::String("Ada".to_string()))
        || bound_friend != Some(&Value::String("Grace".to_string()))
    {
        return Err("unexpected statement pull pattern".to_string());
    }

    let all_emails = conn.prepare(r#"[:find ?e ?email :where [?e :user/email ?email]]"#)?;
    let snapshot = conn.db()?;
    conn.transact(r#"[{:db/id 3 :user/name "Alan" :user/email "alan@example.com"}]"#);
    let current_rows = all_emails.query_conn(&conn, "[]")?.row_count();
    let snapshot_rows = all_emails.query_db(&snapshot, "[]")?.row_count();
    println!("current-db rows: {current_rows}");
    println!("snapshot-db rows: {snapshot_rows}");
    if current_rows != 3 || snapshot_rows != 2 {
        return Err("unexpected snapshot row counts".to_string());
    }

    let mut snapshot_stmt = email_query.statement()?;
    let snapshot_stmt_rows = snapshot_stmt
        .bind_string("ada@example.com")?
        .query_db(&snapshot)?
        .rows();
    if snapshot_stmt_rows
        != vec![vec![
            Value::Entity(1),
            Value::String("ada@example.com".to_string()),
        ]]
    {
        return Err("unexpected snapshot statement rows".to_string());
    }

    let barbara_query = conn.prepare(r#"[:find ?e :where [?e :user/name "Barbara"]]"#)?;
    let dorothy_query = conn.prepare(r#"[:find ?e :where [?e :user/name "Dorothy"]]"#)?;
    let with_report = snapshot.with_report(r#"[{:db/id 4 :user/name "Barbara"}]"#)?;
    println!("with-db: {}", with_report.edn());
    if with_report.value().map_get(":ok") != Some(&Value::Bool(true)) {
        return Err("unexpected typed with report".to_string());
    }
    let next_db = snapshot.db_with(r#"[{:db/id 4 :user/name "Barbara"}]"#)?;
    let source_barbara_rows = barbara_query.query_db(&snapshot, "[]")?.row_count();
    let next_barbara_rows = barbara_query.query_db(&next_db, "[]")?.row_count();
    if source_barbara_rows != 0 || next_barbara_rows != 1 {
        return Err(format!(
            "unexpected db-with rows: source={source_barbara_rows} next={next_barbara_rows}"
        ));
    }

    let derived = Conn::from_db(&next_db)?;
    derived.transact(r#"[{:db/id 5 :user/name "Dorothy"}]"#);
    let derived_barbara_rows = barbara_query.query_conn(&derived, "[]")?.row_count();
    let derived_dorothy_rows = dorothy_query.query_conn(&derived, "[]")?.row_count();
    if derived_barbara_rows != 1 || derived_dorothy_rows != 1 {
        return Err("conn-from-db did not initialize from DB value".to_string());
    }

    let sqlite_path = "tmp.vev.rust.sqlite";
    remove_sqlite_files(sqlite_path);
    {
        let durable = DurableConn::open(sqlite_path)?;
        if durable.backend() != "sqlite" || durable.path() != sqlite_path {
            remove_sqlite_files(sqlite_path);
            return Err("unexpected durable connection metadata".to_string());
        }
        if durable.basis_t() != 0 {
            remove_sqlite_files(sqlite_path);
            return Err("unexpected initial durable basis".to_string());
        }
        if durable.tx_count() != 0 {
            remove_sqlite_files(sqlite_path);
            return Err("unexpected initial durable tx count".to_string());
        }
        if durable.tx_ids() != Vec::<u64>::new() {
            remove_sqlite_files(sqlite_path);
            return Err("unexpected initial durable tx ids".to_string());
        }
        let info = durable.info_edn();
        if !info.contains(":backend :sqlite")
            || !info.contains(":basis-t 0")
            || !info.contains(":tx-count 0")
        {
            remove_sqlite_files(sqlite_path);
            return Err("unexpected durable connection info".to_string());
        }
        let report = durable.transact_report(
            r#"[{:db/id 1 :user/name "Durable Ada" :user/email "durable-ada@example.com"}]"#,
        )?;
        if report.value().map_get(":ok") != Some(&Value::Bool(true)) {
            remove_sqlite_files(sqlite_path);
            return Err("unexpected SQLite transaction report".to_string());
        }
        if durable.basis_t() != 1 {
            remove_sqlite_files(sqlite_path);
            return Err("unexpected durable basis after first tx".to_string());
        }
        if durable.tx_count() != 1 {
            remove_sqlite_files(sqlite_path);
            return Err("unexpected durable tx count after first tx".to_string());
        }
        if durable.tx_ids() != vec![1] {
            remove_sqlite_files(sqlite_path);
            return Err("unexpected durable tx ids after first tx".to_string());
        }
        let durable_query =
            PreparedQuery::new(r#"[:find ?e ?email :where [?e :user/email ?email]]"#)?;
        let durable_db = durable.db()?;
        let live_rows = durable_query.query_db(&durable_db, "[]")?.row_count();
        println!("sqlite-live rows: {live_rows}");
        if live_rows != 1 {
            remove_sqlite_files(sqlite_path);
            return Err("unexpected SQLite live row count".to_string());
        }
    }
    {
        let durable = DurableConn::open(sqlite_path)?;
        if durable.basis_t() != 1 {
            remove_sqlite_files(sqlite_path);
            return Err("unexpected reopened durable basis".to_string());
        }
        if durable.tx_count() != 1 {
            remove_sqlite_files(sqlite_path);
            return Err("unexpected reopened durable tx count".to_string());
        }
        if durable.tx_ids() != vec![1] {
            remove_sqlite_files(sqlite_path);
            return Err("unexpected reopened durable tx ids".to_string());
        }
        let durable_query =
            PreparedQuery::new(r#"[:find ?e ?email :where [?e :user/email ?email]]"#)?;
        let durable_db = durable.db()?;
        let reopened_rows = durable_query.query_db(&durable_db, "[]")?.row_count();
        println!("sqlite-reopened rows: {reopened_rows}");
        if reopened_rows != 1 {
            remove_sqlite_files(sqlite_path);
            return Err("unexpected SQLite reopened row count".to_string());
        }
    }
    remove_sqlite_files(sqlite_path);

    Ok(())
}
