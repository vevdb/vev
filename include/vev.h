#ifndef VEV_H
#define VEV_H

#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef void *vev_conn_t;
typedef void *vev_db_t;
typedef void *vev_prepared_query_t;
typedef void *vev_result_t;
typedef void *vev_stmt_t;
typedef const void *vev_value_t;

enum {
    VEV_VALUE_NIL = 0,
    VEV_VALUE_ENTITY = 1,
    VEV_VALUE_STRING = 2,
    VEV_VALUE_INT = 3,
    VEV_VALUE_FLOAT = 4,
    VEV_VALUE_BOOL = 5,
    VEV_VALUE_KEYWORD = 6,
    VEV_VALUE_SYMBOL = 7,
    VEV_VALUE_VECTOR = 8,
    VEV_VALUE_MAP = 9,
};

const char *vev_version(void);

vev_conn_t vev_conn_open_memory(void);
void vev_conn_close(vev_conn_t conn);
vev_db_t vev_conn_db(vev_conn_t conn);
vev_db_t vev_db_retain(vev_db_t db);
void vev_db_release(vev_db_t db);

void vev_string_free(const char *text);

const char *vev_transact_edn(vev_conn_t conn, const char *tx_text);
const char *vev_query_edn(vev_conn_t conn, const char *query_text);
const char *vev_query_edn_with_inputs(
    vev_conn_t conn,
    const char *query_text,
    const char *inputs_text);

vev_prepared_query_t vev_prepare_query_edn(const char *query_text);
void vev_prepared_query_free(vev_prepared_query_t query);
vev_stmt_t vev_stmt_create(vev_prepared_query_t query);
void vev_stmt_clear(vev_stmt_t stmt);
void vev_stmt_free(vev_stmt_t stmt);
bool vev_stmt_bind_string(vev_stmt_t stmt, const char *value);
bool vev_stmt_bind_keyword(vev_stmt_t stmt, const char *value);
bool vev_stmt_bind_symbol(vev_stmt_t stmt, const char *value);
bool vev_stmt_bind_entity(vev_stmt_t stmt, unsigned long long value);
bool vev_stmt_bind_int(vev_stmt_t stmt, long long value);
bool vev_stmt_bind_bool(vev_stmt_t stmt, bool value);
bool vev_stmt_bind_string_collection(vev_stmt_t stmt, const char **values, int value_count);
bool vev_stmt_bind_entity_collection(vev_stmt_t stmt, const unsigned long long *values, int value_count);
bool vev_stmt_bind_int_collection(vev_stmt_t stmt, const long long *values, int value_count);
bool vev_stmt_bind_bool_collection(vev_stmt_t stmt, const bool *values, int value_count);
const char *vev_query_prepared(vev_conn_t conn, vev_prepared_query_t query);
const char *vev_query_prepared_with_inputs(
    vev_conn_t conn,
    vev_prepared_query_t query,
    const char *inputs_text);

vev_result_t vev_query_stmt_result(vev_conn_t conn, vev_stmt_t stmt);
vev_result_t vev_query_db_stmt_result(vev_db_t db, vev_stmt_t stmt);
vev_result_t vev_query_prepared_result_with_inputs(
    vev_conn_t conn,
    vev_prepared_query_t query,
    const char *inputs_text);
const char *vev_query_db_prepared_with_inputs(
    vev_db_t db,
    vev_prepared_query_t query,
    const char *inputs_text);
vev_result_t vev_query_db_prepared_result_with_inputs(
    vev_db_t db,
    vev_prepared_query_t query,
    const char *inputs_text);
void vev_result_free(vev_result_t result);
bool vev_result_ok(vev_result_t result);
const char *vev_result_error(vev_result_t result);
int vev_result_row_count(vev_result_t result);
int vev_result_value_count(vev_result_t result, int row);
int vev_result_value_kind(vev_result_t result, int row, int column);
vev_value_t vev_result_value(vev_result_t result, int row, int column);
int vev_result_pull_count(vev_result_t result, int row);
vev_value_t vev_result_pull(vev_result_t result, int row, int pull);
unsigned long long vev_result_value_entity(vev_result_t result, int row, int column);
long long vev_result_value_int(vev_result_t result, int row, int column);
bool vev_result_value_bool(vev_result_t result, int row, int column);
const char *vev_result_value_text(vev_result_t result, int row, int column);
const char *vev_result_value_edn(vev_result_t result, int row, int column);

int vev_value_kind(vev_value_t value);
unsigned long long vev_value_entity(vev_value_t value);
long long vev_value_int(vev_value_t value);
double vev_value_float(vev_value_t value);
bool vev_value_bool(vev_value_t value);
const char *vev_value_text(vev_value_t value);
const char *vev_value_edn(vev_value_t value);
int vev_value_item_count(vev_value_t value);
vev_value_t vev_value_item(vev_value_t value, int index);
int vev_value_map_count(vev_value_t value);
vev_value_t vev_value_map_key(vev_value_t value, int index);
vev_value_t vev_value_map_value(vev_value_t value, int index);

#ifdef __cplusplus
}
#endif

#endif
