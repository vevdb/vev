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
const char *vev_query_prepared(vev_conn_t conn, vev_prepared_query_t query);
const char *vev_query_prepared_with_inputs(
    vev_conn_t conn,
    vev_prepared_query_t query,
    const char *inputs_text);

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
unsigned long long vev_result_value_entity(vev_result_t result, int row, int column);
long long vev_result_value_int(vev_result_t result, int row, int column);
bool vev_result_value_bool(vev_result_t result, int row, int column);
const char *vev_result_value_text(vev_result_t result, int row, int column);
const char *vev_result_value_edn(vev_result_t result, int row, int column);

#ifdef __cplusplus
}
#endif

#endif
