use std::ffi::{CStr, CString};
use std::os::raw::{c_char, c_double, c_int, c_ulonglong, c_void};
use std::ptr;

type VevConn = *mut c_void;
type VevDb = *mut c_void;
type VevPreparedQuery = *mut c_void;
type VevResult = *mut c_void;
type VevStmt = *mut c_void;
type VevTxReport = *mut c_void;
type VevValue = *const c_void;

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

#[link(name = "vev")]
unsafe extern "C" {
    fn vev_version() -> *const c_char;

    fn vev_conn_open_memory() -> VevConn;
    fn vev_conn_close(conn: VevConn);
    fn vev_conn_db(conn: VevConn) -> VevDb;
    fn vev_conn_from_db(db: VevDb) -> VevConn;
    fn vev_db_release(db: VevDb);
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

    fn vev_query_stmt_result(conn: VevConn, stmt: VevStmt) -> VevResult;
    fn vev_query_db_stmt_result(db: VevDb, stmt: VevStmt) -> VevResult;
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
    Vector(Vec<Value>),
    Map(Vec<(Value, Value)>),
}

impl Value {
    fn map_get(&self, key: &str) -> Option<&Value> {
        match self {
            Value::Map(items) => items.iter().find_map(|(k, v)| match k {
                Value::Keyword(text) | Value::String(text) | Value::Symbol(text) if text == key => {
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
        let out = unsafe { CStr::from_ptr(ptr) }.to_string_lossy().into_owned();
        unsafe { vev_string_free(ptr) };
        out
    }

    unsafe fn borrowed_string(ptr: *const c_char) -> String {
        if ptr.is_null() {
            return String::new();
        }
        unsafe { CStr::from_ptr(ptr) }.to_string_lossy().into_owned()
    }

    unsafe fn value_to_rust(value: VevValue) -> Value {
        match unsafe { vev_value_kind(value) } {
            VEV_VALUE_NIL => Value::Nil,
            VEV_VALUE_ENTITY => Value::Entity(unsafe { vev_value_entity(value) } as u64),
            VEV_VALUE_STRING => Value::String(unsafe { Self::owned_string(vev_value_text(value)) }),
            VEV_VALUE_INT => Value::Int(unsafe { vev_value_int(value) }),
            VEV_VALUE_FLOAT => Value::Float(unsafe { vev_value_float(value) }),
            VEV_VALUE_BOOL => Value::Bool(unsafe { vev_value_bool(value) }),
            VEV_VALUE_KEYWORD => Value::Keyword(unsafe { Self::owned_string(vev_value_text(value)) }),
            VEV_VALUE_SYMBOL => Value::Symbol(unsafe { Self::owned_string(vev_value_text(value)) }),
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

struct Db {
    raw: VevDb,
}

impl Db {
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
}

impl Drop for Db {
    fn drop(&mut self) {
        if !self.raw.is_null() {
            unsafe { vev_db_release(self.raw) };
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
            Ok(Statement {
                raw,
                _query: self,
            })
        }
    }

    fn query_conn(&self, conn: &Conn, inputs: &str) -> Result<ResultSet, String> {
        let inputs = cstring(inputs);
        let raw = unsafe { vev_query_prepared_result_with_inputs(conn.raw, self.raw, inputs.as_ptr()) };
        ResultSet::new(raw)
    }

    fn query_db(&self, db: &Db, inputs: &str) -> Result<ResultSet, String> {
        let inputs = cstring(inputs);
        let raw = unsafe { vev_query_db_prepared_result_with_inputs(db.raw, self.raw, inputs.as_ptr()) };
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
        if unsafe {
            vev_stmt_bind_string_collection(self.raw, ptrs.as_ptr(), ptrs.len() as c_int)
        } {
            Ok(self)
        } else {
            Err("failed to bind string collection".to_string())
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
                values.push(unsafe { Library::value_to_rust(vev_result_pull(self.raw, row, pull)) });
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
    let mut stmt = email_query.statement()?;
    let rows = stmt.bind_string("grace@example.com")?.query_conn(&conn)?.rows();
    println!("statement rows: {rows:?}");
    if rows != vec![vec![Value::Entity(2), Value::String("grace@example.com".to_string())]] {
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
    if snapshot_stmt_rows != vec![vec![Value::Entity(1), Value::String("ada@example.com".to_string())]] {
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

    Ok(())
}
