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

static int result_row_count_or_error(const char *label, vev_result_t result) {
    if (result == NULL) {
        fprintf(stderr, "%s: null result\n", label);
        return -1;
    }
    if (!vev_result_ok(result)) {
        const char *error = vev_result_error(result);
        fprintf(stderr, "%s: %s\n", label, error);
        vev_string_free(error);
        return -1;
    }
    return vev_result_row_count(result);
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

    vev_stmt_t stmt = vev_stmt_create(query);
    if (stmt == NULL) {
        fprintf(stderr, "failed to create statement\n");
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    if (!vev_stmt_bind_string(stmt, "ada@example.com")) {
        fprintf(stderr, "failed to bind statement string\n");
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    vev_result_t stmt_result = vev_query_stmt_result(conn, stmt);
    int stmt_rows = result_row_count_or_error("stmt", stmt_result);
    printf("stmt rows: %d\n", stmt_rows);
    vev_result_free(stmt_result);
    vev_stmt_clear(stmt);
    if (!vev_stmt_bind_string(stmt, "grace@example.com")) {
        fprintf(stderr, "failed to rebind statement string\n");
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    vev_result_t rebound = vev_query_stmt_result(conn, stmt);
    int rebound_rows = result_row_count_or_error("stmt-rebound", rebound);
    printf("stmt-rebound rows: %d\n", rebound_rows);
    vev_result_free(rebound);
    if (stmt_rows != 1 || rebound_rows != 1) {
        fprintf(stderr, "unexpected statement row counts\n");
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }

    vev_prepared_query_t all_emails =
        vev_prepare_query_edn("[:find ?e ?email :where [?e :user/email ?email]]");
    if (all_emails == NULL) {
        fprintf(stderr, "failed to prepare snapshot query\n");
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }

    vev_db_t snapshot = vev_conn_db(conn);
    if (snapshot == NULL) {
        fprintf(stderr, "failed to retain DB snapshot\n");
        vev_prepared_query_free(all_emails);
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }

    print_and_free(
        "tx-after-snapshot",
        vev_transact_edn(
            conn,
            "[{:db/id 3 :user/name \"Alan\" :user/email \"alan@example.com\"}]"));

    vev_result_t current = vev_query_prepared_result_with_inputs(conn, all_emails, "[]");
    int current_rows = result_row_count_or_error("current-db", current);
    printf("current-db rows: %d\n", current_rows);
    vev_result_free(current);

    vev_conn_close(conn);
    conn = NULL;

    vev_result_t old = vev_query_db_prepared_result_with_inputs(snapshot, all_emails, "[]");
    int old_rows = result_row_count_or_error("snapshot-db", old);
    printf("snapshot-db rows: %d\n", old_rows);
    vev_result_free(old);

    if (current_rows != 3 || old_rows != 2) {
        fprintf(stderr, "unexpected snapshot row counts\n");
        vev_db_release(snapshot);
        vev_prepared_query_free(all_emails);
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        return 1;
    }

    vev_db_release(snapshot);
    vev_prepared_query_free(all_emails);

    vev_stmt_free(stmt);
    vev_prepared_query_free(query);
    if (conn != NULL) {
        vev_conn_close(conn);
    }
    return 0;
}
