#ifndef VEV_H
#define VEV_H

#ifdef __cplusplus
extern "C" {
#endif

typedef void *vev_conn_t;
typedef void *vev_prepared_query_t;

const char *vev_version(void);

vev_conn_t vev_conn_open_memory(void);
void vev_conn_close(vev_conn_t conn);

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

#ifdef __cplusplus
}
#endif

#endif
