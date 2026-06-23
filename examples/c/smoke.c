#include <stdio.h>
#include "vev.h"

static void print_and_free(const char *label, const char *text) {
    printf("%s: %s\n", label, text);
    vev_string_free(text);
}

static void print_result_rows(vev_result_t result) {
    if (!vev_result_ok(result)) {
        const char *error = vev_result_error(result);
        fprintf(stderr, "result error: %s\n", error);
        vev_string_free(error);
        return;
    }

    int rows = vev_result_row_count(result);
    printf("result-handle rows: %d\n", rows);
    for (int row = 0; row < rows; row++) {
        int values = vev_result_value_count(result, row);
        printf("  row %d:", row);
        for (int column = 0; column < values; column++) {
            int kind = vev_result_value_kind(result, row, column);
            if (kind == VEV_VALUE_ENTITY) {
                printf(" entity=%llu", vev_result_value_entity(result, row, column));
            } else if (kind == VEV_VALUE_STRING) {
                const char *text = vev_result_value_text(result, row, column);
                printf(" string=%s", text);
                vev_string_free(text);
            } else {
                const char *text = vev_result_value_edn(result, row, column);
                printf(" value=%s", text);
                vev_string_free(text);
            }
        }
        printf("\n");
    }
}

int main(void) {
    printf("version: %s\n", vev_version());

    vev_conn_t conn = vev_conn_open_memory();
    if (conn == NULL) {
        fprintf(stderr, "failed to open Vev connection\n");
        return 1;
    }

    print_and_free(
        "tx",
        vev_transact_edn(
            conn,
            "[{:db/id 1 :user/name \"Ada\" :user/email \"ada@example.com\"}"
            " {:db/id 2 :user/name \"Grace\" :user/email \"grace@example.com\"}]"));

    print_and_free(
        "query",
        vev_query_edn(
            conn,
            "[:find ?e ?name :where [?e :user/name ?name]]"));

    print_and_free(
        "input-scalar",
        vev_query_edn_with_inputs(
            conn,
            "[:find ?name :in ?email :where [?e :user/email ?email] [?e :user/name ?name]]",
            "[\"ada@example.com\"]"));

    print_and_free(
        "input-collection",
        vev_query_edn_with_inputs(
            conn,
            "[:find ?name :in [?email ...] :where [?e :user/email ?email] [?e :user/name ?name]]",
            "[[\"ada@example.com\" \"grace@example.com\"]]"));

    print_and_free(
        "input-tuple",
        vev_query_edn_with_inputs(
            conn,
            "[:find ?e :in [?name ?email] :where [?e :user/name ?name] [?e :user/email ?email]]",
            "[[\"Ada\" \"ada@example.com\"]]"));

    print_and_free(
        "input-relation",
        vev_query_edn_with_inputs(
            conn,
            "[:find ?name :in [[?email ?label]] :where [?e :user/email ?email] [?e :user/name ?name]]",
            "[[[\"ada@example.com\" :primary] [\"missing@example.com\" :missing]]]"));

    vev_prepared_query_t query =
        vev_prepare_query_edn("[:find ?e ?email :in ?needle :where [?e :user/email ?email] [(= ?email ?needle)]]");
    if (query == NULL) {
        fprintf(stderr, "failed to prepare query\n");
        vev_conn_close(conn);
        return 1;
    }

    print_and_free("prepared", vev_query_prepared_with_inputs(conn, query, "[\"grace@example.com\"]"));

    vev_result_t result = vev_query_prepared_result_with_inputs(conn, query, "[\"grace@example.com\"]");
    if (result == NULL) {
        fprintf(stderr, "failed to query typed result handle\n");
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    print_result_rows(result);
    vev_result_free(result);

    vev_prepared_query_free(query);
    vev_conn_close(conn);
    return 0;
}
