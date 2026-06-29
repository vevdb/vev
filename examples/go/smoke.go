// Copyright (c) Andreas Flakstad and Vev contributors
// SPDX-License-Identifier: EPL-2.0

package main

/*
#cgo CFLAGS: -I../../include
#cgo LDFLAGS: -L../../build/lib -lvev
#include "vev.h"
#include <stdlib.h>
*/
import "C"

import (
	"fmt"
	"os"
	"reflect"
	"sort"
	"strings"
	"unsafe"
)

func cstring(text string) *C.char {
	return C.CString(text)
}

func ownedString(ptr *C.char) string {
	if ptr == nil {
		return ""
	}
	defer C.vev_string_free(ptr)
	return C.GoString(ptr)
}

type Conn struct {
	raw C.vev_conn_t
}

func OpenMemory() (*Conn, error) {
	raw := C.vev_conn_open_memory()
	if raw == nil {
		return nil, fmt.Errorf("failed to open Vev connection")
	}
	return &Conn{raw: raw}, nil
}

func ConnFromDB(db *DB) (*Conn, error) {
	raw := C.vev_conn_from_db(db.raw)
	if raw == nil {
		return nil, fmt.Errorf("failed to create connection from DB snapshot")
	}
	return &Conn{raw: raw}, nil
}

func (c *Conn) Close() {
	if c.raw != nil {
		C.vev_conn_close(c.raw)
		c.raw = nil
	}
}

func (c *Conn) Transact(tx string) string {
	txText := cstring(tx)
	defer C.free(unsafe.Pointer(txText))
	return ownedString(C.vev_transact_edn(c.raw, txText))
}

func (c *Conn) QueryText(query string, inputs string) string {
	queryText := cstring(query)
	inputsText := cstring(inputs)
	defer C.free(unsafe.Pointer(queryText))
	defer C.free(unsafe.Pointer(inputsText))
	return ownedString(C.vev_query_edn_with_inputs(c.raw, queryText, inputsText))
}

func (c *Conn) DB() (*DB, error) {
	raw := C.vev_conn_db(c.raw)
	if raw == nil {
		return nil, fmt.Errorf("failed to retain DB snapshot")
	}
	return &DB{raw: raw}, nil
}

type DurableConn struct {
	raw C.vev_connection_t
}

func Connect(uri string) (*DurableConn, error) {
	uriText := cstring(uri)
	defer C.free(unsafe.Pointer(uriText))
	raw := C.vev_connect(uriText)
	if raw == nil {
		return nil, fmt.Errorf("failed to connect Vev durable connection")
	}
	if !bool(C.vev_connection_ok(raw)) {
		err := ownedString(C.vev_connection_error(raw))
		C.vev_connection_close(raw)
		return nil, fmt.Errorf("%s", err)
	}
	return &DurableConn{raw: raw}, nil
}

func (c *DurableConn) Close() {
	if c.raw != nil {
		C.vev_connection_close(c.raw)
		c.raw = nil
	}
}

func (c *DurableConn) Backend() string {
	return ownedString(C.vev_connection_backend(c.raw))
}

func (c *DurableConn) Path() string {
	return ownedString(C.vev_connection_path(c.raw))
}

func (c *DurableConn) BasisT() uint64 {
	return uint64(C.vev_connection_basis_t(c.raw))
}

func (c *DurableConn) TxCount() uint64 {
	return uint64(C.vev_connection_tx_count(c.raw))
}

func (c *DurableConn) InfoEDN() string {
	return ownedString(C.vev_connection_info_edn(c.raw))
}

func (c *DurableConn) Transact(tx string) (*TxReport, error) {
	txText := cstring(tx)
	defer C.free(unsafe.Pointer(txText))
	raw := C.vev_connection_transact_edn_report(c.raw, txText)
	if raw == nil {
		return nil, fmt.Errorf("failed to transact durable connection")
	}
	return &TxReport{raw: raw}, nil
}

func (c *DurableConn) DB() (*DB, error) {
	raw := C.vev_connection_db(c.raw)
	if raw == nil {
		return nil, fmt.Errorf("failed to retain durable DB snapshot")
	}
	return &DB{raw: raw}, nil
}

type DB struct {
	raw C.vev_db_t
}

func (db *DB) Close() {
	if db.raw != nil {
		C.vev_db_release(db.raw)
		db.raw = nil
	}
}

func (db *DB) QueryPrepared(query *PreparedQuery, inputs string) string {
	inputsText := cstring(inputs)
	defer C.free(unsafe.Pointer(inputsText))
	return ownedString(C.vev_query_db_prepared_with_inputs(db.raw, query.raw, inputsText))
}

func (db *DB) QueryRows(query *PreparedQuery, inputs string) (*ResultSet, error) {
	inputsText := cstring(inputs)
	defer C.free(unsafe.Pointer(inputsText))
	raw := C.vev_query_db_prepared_result_with_inputs(db.raw, query.raw, inputsText)
	return newResultSet(raw)
}

func (db *DB) QueryColumns(query *PreparedQuery, inputs string) (*ColumnResult, error) {
	inputsText := cstring(inputs)
	defer C.free(unsafe.Pointer(inputsText))
	raw := C.vev_query_db_prepared_column_batch_with_inputs(db.raw, query.raw, inputsText)
	return columnResultFromRaw(raw)
}

func (db *DB) QueryStatementRows(stmt *Statement) (*ResultSet, error) {
	raw := C.vev_query_db_stmt_result(db.raw, stmt.raw)
	return newResultSet(raw)
}

func (db *DB) QueryStatementColumns(stmt *Statement) (*ColumnResult, error) {
	raw := C.vev_query_db_stmt_column_batch(db.raw, stmt.raw)
	return columnResultFromRaw(raw)
}

func columnResultFromRaw(raw C.vev_column_batch_t) (*ColumnResult, error) {
	if raw == nil {
		return nil, nil
	}
	defer C.vev_column_batch_free(raw)

	count := int(C.vev_column_batch_count(raw))
	switch C.vev_column_batch_kind(raw) {
	case C.VEV_COLUMN_BATCH_ENTITY:
		return &ColumnResult{
			Count:   count,
			Kinds:   []int{ColumnEntity},
			Columns: []any{entityColumn(C.vev_column_batch_entities_data(raw), count)},
		}, nil
	case C.VEV_COLUMN_BATCH_STRING:
		return &ColumnResult{
			Count:   count,
			Kinds:   []int{ColumnString},
			Columns: []any{stringColumn(raw, count)},
		}, nil
	case C.VEV_COLUMN_BATCH_ENTITY_INT:
		return &ColumnResult{
			Count: count,
			Kinds: []int{ColumnEntity, ColumnInt},
			Columns: []any{
				entityColumn(C.vev_column_batch_entities_data(raw), count),
				intColumn(C.vev_column_batch_ints_data(raw), count),
			},
		}, nil
	case C.VEV_COLUMN_BATCH_ENTITY_STRING_INT:
		return &ColumnResult{
			Count: count,
			Kinds: []int{ColumnEntity, ColumnString, ColumnInt},
			Columns: []any{
				entityColumn(C.vev_column_batch_entities_data(raw), count),
				stringColumn(raw, count),
				intColumn(C.vev_column_batch_ints_data(raw), count),
			},
		}, nil
	default:
		return nil, nil
	}
}

func (db *DB) Pull(pattern string, entity uint64) (Value, error) {
	patternText := cstring(pattern)
	defer C.free(unsafe.Pointer(patternText))
	raw := C.vev_pull_edn(db.raw, patternText, C.ulonglong(entity))
	handle, err := newValueHandle(raw)
	if err != nil {
		return nil, err
	}
	defer handle.Close()
	return handle.Value(), nil
}

func (db *DB) PullLookupRefString(pattern string, attr string, value string) (Value, error) {
	patternText := cstring(pattern)
	attrText := cstring(attr)
	valueText := cstring(value)
	defer C.free(unsafe.Pointer(patternText))
	defer C.free(unsafe.Pointer(attrText))
	defer C.free(unsafe.Pointer(valueText))
	raw := C.vev_pull_lookup_ref_string_edn(db.raw, patternText, attrText, valueText)
	handle, err := newValueHandle(raw)
	if err != nil {
		return nil, err
	}
	defer handle.Close()
	return handle.Value(), nil
}

func (db *DB) PullMany(pattern string, entities []uint64) (Value, error) {
	patternText := cstring(pattern)
	defer C.free(unsafe.Pointer(patternText))
	var ptr *C.ulonglong
	if len(entities) > 0 {
		ptr = (*C.ulonglong)(unsafe.Pointer(&entities[0]))
	}
	raw := C.vev_pull_many_edn(db.raw, patternText, ptr, C.int(len(entities)))
	handle, err := newValueHandle(raw)
	if err != nil {
		return nil, err
	}
	defer handle.Close()
	return handle.Value(), nil
}

func (db *DB) DBWith(tx string) (*DB, error) {
	txText := cstring(tx)
	defer C.free(unsafe.Pointer(txText))
	raw := C.vev_db_with_edn(db.raw, txText)
	if raw == nil {
		return nil, fmt.Errorf("failed to create DB snapshot")
	}
	return &DB{raw: raw}, nil
}

type PreparedQuery struct {
	raw C.vev_prepared_query_t
}

func Prepare(query string) (*PreparedQuery, error) {
	queryText := cstring(query)
	defer C.free(unsafe.Pointer(queryText))
	raw := C.vev_prepare_query_edn(queryText)
	if raw == nil {
		return nil, fmt.Errorf("failed to prepare query")
	}
	if !bool(C.vev_prepared_query_ok(raw)) {
		err := ownedString(C.vev_prepared_query_error(raw))
		C.vev_prepared_query_free(raw)
		return nil, fmt.Errorf("%s", err)
	}
	return &PreparedQuery{raw: raw}, nil
}

func (q *PreparedQuery) Close() {
	if q.raw != nil {
		C.vev_prepared_query_free(q.raw)
		q.raw = nil
	}
}

func (q *PreparedQuery) Query(conn *Conn, inputs string) string {
	inputsText := cstring(inputs)
	defer C.free(unsafe.Pointer(inputsText))
	return ownedString(C.vev_query_prepared_with_inputs(conn.raw, q.raw, inputsText))
}

func (q *PreparedQuery) QueryRows(conn *Conn, inputs string) (*ResultSet, error) {
	inputsText := cstring(inputs)
	defer C.free(unsafe.Pointer(inputsText))
	raw := C.vev_query_prepared_result_with_inputs(conn.raw, q.raw, inputsText)
	return newResultSet(raw)
}

func (q *PreparedQuery) Statement() (*Statement, error) {
	raw := C.vev_stmt_create(q.raw)
	if raw == nil {
		return nil, fmt.Errorf("failed to create statement")
	}
	return &Statement{raw: raw}, nil
}

type Statement struct {
	raw C.vev_stmt_t
}

func (s *Statement) Close() {
	if s.raw != nil {
		C.vev_stmt_free(s.raw)
		s.raw = nil
	}
}

func (s *Statement) BindString(value string) error {
	C.vev_stmt_clear(s.raw)
	valueText := cstring(value)
	defer C.free(unsafe.Pointer(valueText))
	if !bool(C.vev_stmt_bind_string(s.raw, valueText)) {
		return fmt.Errorf("failed to bind string")
	}
	return nil
}

func (s *Statement) QueryRows(conn *Conn) (*ResultSet, error) {
	raw := C.vev_query_stmt_result(conn.raw, s.raw)
	return newResultSet(raw)
}

func (s *Statement) QueryColumns(conn *Conn) (*ColumnResult, error) {
	raw := C.vev_query_stmt_column_batch(conn.raw, s.raw)
	return columnResultFromRaw(raw)
}

type Entity uint64
type Keyword string
type Symbol string
type UUID string

const (
	ColumnEntity = 1
	ColumnString = 2
	ColumnInt    = 3
)

type MapEntry struct {
	Key   Value
	Value Value
}

type MapValue []MapEntry
type Value any

type ColumnResult struct {
	Count   int
	Kinds   []int
	Columns []any
}

func entityColumn(ptr *C.ulonglong, count int) []Entity {
	if ptr == nil || count <= 0 {
		return nil
	}
	values := unsafe.Slice(ptr, count)
	out := make([]Entity, count)
	for index, value := range values {
		out[index] = Entity(value)
	}
	return out
}

func intColumn(ptr *C.longlong, count int) []int64 {
	if ptr == nil || count <= 0 {
		return nil
	}
	values := unsafe.Slice(ptr, count)
	out := make([]int64, count)
	for index, value := range values {
		out[index] = int64(value)
	}
	return out
}

func stringColumn(batch C.vev_column_batch_t, count int) []string {
	dictionaryCount := int(C.vev_column_batch_string_dictionary_count(batch))
	dictionaryData := C.vev_column_batch_string_dictionary_data_array(batch)
	dictionaryLengths := C.vev_column_batch_string_dictionary_lengths_data(batch)
	stringIndices := C.vev_column_batch_string_indices_data(batch)
	if dictionaryCount > 0 && dictionaryData != nil && dictionaryLengths != nil && stringIndices != nil {
		data := unsafe.Slice(dictionaryData, dictionaryCount)
		lengths := unsafe.Slice(dictionaryLengths, dictionaryCount)
		indices := unsafe.Slice(stringIndices, count)
		dictionary := make([]string, dictionaryCount)
		for index := range dictionary {
			dictionary[index] = C.GoStringN((*C.char)(data[index]), lengths[index])
		}
		out := make([]string, count)
		for index, dictionaryIndex := range indices {
			out[index] = dictionary[dictionaryIndex]
		}
		return out
	}

	stringData := C.vev_column_batch_string_data_array(batch)
	stringLengths := C.vev_column_batch_string_lengths_data(batch)
	if stringData == nil || stringLengths == nil || count <= 0 {
		return nil
	}
	data := unsafe.Slice(stringData, count)
	lengths := unsafe.Slice(stringLengths, count)
	out := make([]string, count)
	for index := range out {
		out[index] = C.GoStringN((*C.char)(data[index]), lengths[index])
	}
	return out
}

func (r *ColumnResult) Rows() [][]Value {
	rows := make([][]Value, 0, r.Count)
	for row := 0; row < r.Count; row++ {
		values := make([]Value, 0, len(r.Columns))
		for _, column := range r.Columns {
			switch valuesColumn := column.(type) {
			case []Entity:
				values = append(values, valuesColumn[row])
			case []string:
				values = append(values, valuesColumn[row])
			case []int64:
				values = append(values, valuesColumn[row])
			}
		}
		rows = append(rows, values)
	}
	return rows
}

func mapGet(value Value, key string) (Value, bool) {
	items, ok := value.(MapValue)
	if !ok {
		return nil, false
	}
	for _, item := range items {
		switch k := item.Key.(type) {
		case Keyword:
			if string(k) == key {
				return item.Value, true
			}
		case string:
			if k == key {
				return item.Value, true
			}
		case Symbol:
			if string(k) == key {
				return item.Value, true
			}
		case UUID:
			if string(k) == key {
				return item.Value, true
			}
		}
	}
	return nil, false
}

func valueFromC(value C.vev_value_t) Value {
	switch C.vev_value_kind(value) {
	case C.VEV_VALUE_NIL:
		return nil
	case C.VEV_VALUE_ENTITY:
		return Entity(C.vev_value_entity(value))
	case C.VEV_VALUE_STRING:
		return ownedString(C.vev_value_text(value))
	case C.VEV_VALUE_INT:
		return int64(C.vev_value_int(value))
	case C.VEV_VALUE_FLOAT:
		return float64(C.vev_value_float(value))
	case C.VEV_VALUE_BOOL:
		return bool(C.vev_value_bool(value))
	case C.VEV_VALUE_KEYWORD:
		return Keyword(ownedString(C.vev_value_text(value)))
	case C.VEV_VALUE_SYMBOL:
		return Symbol(ownedString(C.vev_value_text(value)))
	case C.VEV_VALUE_UUID:
		return UUID(ownedString(C.vev_value_text(value)))
	case C.VEV_VALUE_VECTOR:
		count := int(C.vev_value_item_count(value))
		out := make([]Value, 0, count)
		for i := 0; i < count; i++ {
			out = append(out, valueFromC(C.vev_value_item(value, C.int(i))))
		}
		return out
	case C.VEV_VALUE_MAP:
		count := int(C.vev_value_map_count(value))
		out := make(MapValue, 0, count)
		for i := 0; i < count; i++ {
			out = append(out, MapEntry{
				Key:   valueFromC(C.vev_value_map_key(value, C.int(i))),
				Value: valueFromC(C.vev_value_map_value(value, C.int(i))),
			})
		}
		return out
	default:
		return ownedString(C.vev_value_edn(value))
	}
}

type TxReport struct {
	raw C.vev_tx_report_t
}

func (r *TxReport) Close() {
	if r.raw != nil {
		C.vev_tx_report_free(r.raw)
		r.raw = nil
	}
}

func (r *TxReport) EDN() string {
	return ownedString(C.vev_tx_report_edn(r.raw))
}

func (r *TxReport) Value() Value {
	return valueFromC(C.vev_tx_report_value(r.raw))
}

type ValueHandle struct {
	raw C.vev_value_handle_t
}

func newValueHandle(raw C.vev_value_handle_t) (*ValueHandle, error) {
	if raw == nil {
		return nil, fmt.Errorf("pull returned null value handle")
	}
	return &ValueHandle{raw: raw}, nil
}

func (h *ValueHandle) Close() {
	if h.raw != nil {
		C.vev_value_handle_free(h.raw)
		h.raw = nil
	}
}

func (h *ValueHandle) Value() Value {
	return valueFromC(C.vev_value_handle_value(h.raw))
}

type ResultSet struct {
	raw C.vev_result_t
}

func newResultSet(raw C.vev_result_t) (*ResultSet, error) {
	if raw == nil {
		return nil, fmt.Errorf("query returned null result")
	}
	if !bool(C.vev_result_ok(raw)) {
		err := ownedString(C.vev_result_error(raw))
		C.vev_result_free(raw)
		return nil, fmt.Errorf("%s", err)
	}
	return &ResultSet{raw: raw}, nil
}

func (r *ResultSet) Close() {
	if r.raw != nil {
		C.vev_result_free(r.raw)
		r.raw = nil
	}
}

func (r *ResultSet) RowCount() int {
	return int(C.vev_result_row_count(r.raw))
}

func (r *ResultSet) Rows() [][]Value {
	rows := make([][]Value, 0, r.RowCount())
	for row := 0; row < r.RowCount(); row++ {
		values := make([]Value, 0)
		for col := 0; col < int(C.vev_result_value_count(r.raw, C.int(row))); col++ {
			values = append(values, valueFromC(C.vev_result_value(r.raw, C.int(row), C.int(col))))
		}
		for pull := 0; pull < int(C.vev_result_pull_count(r.raw, C.int(row))); pull++ {
			values = append(values, valueFromC(C.vev_result_pull(r.raw, C.int(row), C.int(pull))))
		}
		rows = append(rows, values)
	}
	return rows
}

func (r *ResultSet) Scalar() (Value, error) {
	rows := r.Rows()
	if len(rows) == 1 && len(rows[0]) == 1 {
		return rows[0][0], nil
	}
	return nil, fmt.Errorf("expected one scalar result, got %#v", rows)
}

func mustContain(label string, text string, needles ...string) {
	for _, needle := range needles {
		if !strings.Contains(text, needle) {
			panic(fmt.Sprintf("%s missing %q in %s", label, needle, text))
		}
	}
}

func removeSqliteFiles(path string) {
	_ = os.Remove(path)
	_ = os.Remove(path + "-wal")
	_ = os.Remove(path + "-shm")
}

func mustEqual(label string, got any, want any) {
	if !reflect.DeepEqual(got, want) {
		panic(fmt.Sprintf("%s got %#v, want %#v", label, got, want))
	}
}

func main() {
	conn, err := OpenMemory()
	if err != nil {
		panic(err)
	}
	defer conn.Close()

	tx := conn.Transact(`
		[{:db/id 1 :user/name "Ada" :user/email "ada@example.com"}
		 {:db/id 2 :user/name "Grace" :user/email "grace@example.com"}]
	`)
	fmt.Println("tx:", tx)
	mustContain("tx", tx, ":ok true")

	result := conn.QueryText(`
		[:find ?name
		 :in [?email ...]
		 :where [?e :user/email ?email]
		        [?e :user/name ?name]]
	`, `[["ada@example.com" "grace@example.com"]]`)
	fmt.Println("input-collection:", result)
	mustContain("collection query", result, `"Ada"`, `"Grace"`)

	query, err := Prepare(`
		[:find ?e ?email
		 :in ?needle
		 :where [?e :user/email ?email]
		        [(= ?email ?needle)]]
	`)
	if err != nil {
		panic(err)
	}
	defer query.Close()

	prepared := query.Query(conn, `["grace@example.com"]`)
	fmt.Println("prepared:", prepared)
	mustContain("prepared query", prepared, "2", `"grace@example.com"`)

	rows, err := query.QueryRows(conn, `["grace@example.com"]`)
	if err != nil {
		panic(err)
	}
	typedRows := rows.Rows()
	rows.Close()
	fmt.Println("typed rows:", typedRows)
	mustEqual("typed rows", typedRows, [][]Value{{Entity(2), "grace@example.com"}})

	stmt, err := query.Statement()
	if err != nil {
		panic(err)
	}
	defer stmt.Close()
	if err := stmt.BindString("grace@example.com"); err != nil {
		panic(err)
	}
	stmtRows, err := stmt.QueryRows(conn)
	if err != nil {
		panic(err)
	}
	statementRows := stmtRows.Rows()
	stmtRows.Close()
	mustEqual("statement rows", statementRows, [][]Value{{Entity(2), "grace@example.com"}})

	conn.Transact(`
		[[:db/add 90 :db/ident :user/email]
		 [:db/add 90 :db/unique :db.unique/identity]]
	`)

	conn.Transact(`
		[[:db/add 100 :db/ident :user/friend]
		 [:db/add 100 :db/valueType :db.type/ref]
		 [:db/add 1 :user/friend 2]]
	`)

	pullQuery, err := Prepare(`[:find (pull ?e [:user/name {:user/friend [:user/name]}]) :where [?e :user/name "Ada"]]`)
	if err != nil {
		panic(err)
	}
	defer pullQuery.Close()
	pullRows, err := pullQuery.QueryRows(conn, "[]")
	if err != nil {
		panic(err)
	}
	pulled, err := pullRows.Scalar()
	pullRows.Close()
	if err != nil {
		panic(err)
	}
	fmt.Println("pull:", pulled)
	friend, _ := mapGet(pulled, ":user/friend")
	friendName, _ := mapGet(friend, ":user/name")
	name, _ := mapGet(pulled, ":user/name")
	mustEqual("pull name", name, "Ada")
	mustEqual("pull friend", friendName, "Grace")

	snapshot, err := conn.DB()
	if err != nil {
		panic(err)
	}
	defer snapshot.Close()

	directPull, err := snapshot.Pull("[:user/name {:user/friend [:user/name]}]", 1)
	if err != nil {
		panic(err)
	}
	fmt.Println("direct pull:", directPull)
	lookupPull, err := snapshot.PullLookupRefString("[:user/name]", ":user/email", "ada@example.com")
	if err != nil {
		panic(err)
	}
	lookupName, _ := mapGet(lookupPull, ":user/name")
	mustEqual("lookup pull", lookupName, "Ada")
	manyPull, err := snapshot.PullMany("[:user/name]", []uint64{1, 2})
	if err != nil {
		panic(err)
	}
	manyNames := make([]string, 0)
	for _, item := range manyPull.([]Value) {
		if name, ok := mapGet(item, ":user/name"); ok {
			manyNames = append(manyNames, name.(string))
		}
	}
	sort.Strings(manyNames)
	mustEqual("pull many", manyNames, []string{"Ada", "Grace"})

	allEmailTexts, err := Prepare(`[:find ?email :where [?e :user/email ?email]]`)
	if err != nil {
		panic(err)
	}
	defer allEmailTexts.Close()
	columns, err := snapshot.QueryColumns(allEmailTexts, "[]")
	if err != nil {
		panic(err)
	}
	if columns == nil {
		panic("expected string column batch")
	}
	columnRows := columns.Rows()
	sort.Slice(columnRows, func(i, j int) bool {
		return fmt.Sprintf("%#v", columnRows[i]) < fmt.Sprintf("%#v", columnRows[j])
	})
	fmt.Println("column batch rows:", columnRows)
	mustEqual("column batch kinds", columns.Kinds, []int{ColumnString})
	mustEqual("column batch rows", columnRows, [][]Value{{"ada@example.com"}, {"grace@example.com"}})

	allEmailStmt, err := allEmailTexts.Statement()
	if err != nil {
		panic(err)
	}
	defer allEmailStmt.Close()
	liveColumns, err := allEmailStmt.QueryColumns(conn)
	if err != nil {
		panic(err)
	}
	if liveColumns == nil {
		panic("expected live statement column batch")
	}
	liveColumnRows := liveColumns.Rows()
	sort.Slice(liveColumnRows, func(i, j int) bool {
		return fmt.Sprintf("%#v", liveColumnRows[i]) < fmt.Sprintf("%#v", liveColumnRows[j])
	})
	mustEqual("live statement column batch kinds", liveColumns.Kinds, []int{ColumnString})
	mustEqual("live statement column batch rows", liveColumnRows, columnRows)
	snapshotColumns, err := snapshot.QueryStatementColumns(allEmailStmt)
	if err != nil {
		panic(err)
	}
	if snapshotColumns == nil {
		panic("expected snapshot statement column batch")
	}
	snapshotColumnRows := snapshotColumns.Rows()
	sort.Slice(snapshotColumnRows, func(i, j int) bool {
		return fmt.Sprintf("%#v", snapshotColumnRows[i]) < fmt.Sprintf("%#v", snapshotColumnRows[j])
	})
	mustEqual("snapshot statement column batch kinds", snapshotColumns.Kinds, []int{ColumnString})
	mustEqual("snapshot statement column batch rows", snapshotColumnRows, columnRows)

	conn.Transact(`[{:db/id 3 :user/name "Alan" :user/email "alan@example.com"}]`)
	current := query.Query(conn, `["alan@example.com"]`)
	old := snapshot.QueryPrepared(query, `["alan@example.com"]`)
	fmt.Println("current-db:", current)
	fmt.Println("snapshot-db:", old)
	mustContain("current DB query", current, "3", `"alan@example.com"`)
	if strings.Contains(old, "alan@example.com") {
		panic("snapshot unexpectedly observed later transaction")
	}

	currentRows, err := query.QueryRows(conn, `["alan@example.com"]`)
	if err != nil {
		panic(err)
	}
	oldRows, err := snapshot.QueryRows(query, `["alan@example.com"]`)
	if err != nil {
		panic(err)
	}
	if currentRows.RowCount() != 1 || oldRows.RowCount() != 0 {
		panic("unexpected typed snapshot row counts")
	}
	currentRows.Close()
	oldRows.Close()

	nextDB, err := snapshot.DBWith(`[{:db/id 4 :user/name "Barbara" :user/email "barbara@example.com"}]`)
	if err != nil {
		panic(err)
	}
	defer nextDB.Close()
	derived, err := ConnFromDB(nextDB)
	if err != nil {
		panic(err)
	}
	defer derived.Close()
	barbara := query.Query(derived, `["barbara@example.com"]`)
	mustContain("conn-from-db", barbara, "4", `"barbara@example.com"`)

	sqlitePath := "tmp.vev.go.sqlite"
	removeSqliteFiles(sqlitePath)
	defer removeSqliteFiles(sqlitePath)
	durable, err := Connect(sqlitePath)
	if err != nil {
		panic(err)
	}
	if durable.Backend() != "sqlite" || durable.Path() != sqlitePath || durable.BasisT() != 0 || durable.TxCount() != 0 {
		panic(fmt.Sprintf("unexpected durable metadata: backend=%s path=%s basis=%d tx-count=%d info=%s", durable.Backend(), durable.Path(), durable.BasisT(), durable.TxCount(), durable.InfoEDN()))
	}
	report, err := durable.Transact(`[{:db/id 1 :user/name "Durable Ada" :user/email "durable-ada@example.com"}]`)
	if err != nil {
		panic(err)
	}
	report.Close()
	if durable.BasisT() != 1 || durable.TxCount() != 1 {
		panic("unexpected durable metadata after transact")
	}
	durableDB, err := durable.DB()
	if err != nil {
		panic(err)
	}
	durable.Close()
	reopened, err := Connect(sqlitePath)
	if err != nil {
		panic(err)
	}
	defer reopened.Close()
	reopenedDB, err := reopened.DB()
	if err != nil {
		panic(err)
	}
	defer reopenedDB.Close()
	durableRows, err := reopenedDB.QueryRows(query, `["durable-ada@example.com"]`)
	if err != nil {
		panic(err)
	}
	if durableRows.RowCount() != 1 {
		panic("unexpected reopened durable rows")
	}
	durableRows.Close()
	durableDB.Close()

	if len(os.Args) > 1 && os.Args[1] == "--version" {
		fmt.Println("version:", ownedString(C.vev_version()))
	}
}
