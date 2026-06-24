#include <stdio.h>
#include <string.h>
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

static vev_value_t map_get(vev_value_t map, const char *key) {
    int count = vev_value_map_count(map);
    for (int i = 0; i < count; i++) {
        vev_value_t item_key = vev_value_map_key(map, i);
        const char *text = vev_value_text(item_key);
        int matches = text != NULL && strcmp(text, key) == 0;
        vev_string_free(text);
        if (matches) {
            return vev_value_map_value(map, i);
        }
    }
    return NULL;
}

static int value_text_equals(vev_value_t value, const char *expected) {
    const char *text = vev_value_text(value);
    int matches = text != NULL && strcmp(text, expected) == 0;
    vev_string_free(text);
    return matches;
}

struct value_visit_stats {
    int values;
    int ends;
    int depth;
    int max_depth;
};

static bool count_value_visit(void *user, int event, vev_value_t value) {
    struct value_visit_stats *stats = (struct value_visit_stats *)user;
    if (event == VEV_VALUE_VISIT_VALUE) {
        stats->values++;
        int kind = vev_value_kind(value);
        if (kind == VEV_VALUE_VECTOR || kind == VEV_VALUE_MAP) {
            stats->depth++;
            if (stats->depth > stats->max_depth) {
                stats->max_depth = stats->depth;
            }
        }
    } else if (event == VEV_VALUE_VISIT_END) {
        stats->ends++;
        stats->depth--;
    }
    return true;
}

struct result_visit_stats {
    int row_begins;
    int row_ends;
    int values;
    int pulls;
};

static bool count_result_visit(void *user, int event, int row, int index, vev_value_t value) {
    (void)row;
    (void)index;
    struct result_visit_stats *stats = (struct result_visit_stats *)user;
    if (event == VEV_RESULT_VISIT_ROW_BEGIN) {
        stats->row_begins++;
    } else if (event == VEV_RESULT_VISIT_ROW_END) {
        stats->row_ends++;
    } else if (event == VEV_RESULT_VISIT_VALUE) {
        if (value == NULL) {
            return false;
        }
        stats->values++;
    } else if (event == VEV_RESULT_VISIT_PULL) {
        if (value == NULL) {
            return false;
        }
        stats->pulls++;
    }
    return true;
}

static bool cancel_result_visit(void *user, int event, int row, int index, vev_value_t value) {
    (void)user;
    (void)event;
    (void)row;
    (void)index;
    (void)value;
    return false;
}

static const char *mark_seen_tx_fn(void *user, vev_db_t db, int argc, vev_tx_fn_args_t args) {
    (void)user;
    (void)db;
    static char out[256];
    if (argc != 2) {
        return NULL;
    }
    vev_value_t entity = vev_tx_fn_arg(args, 0);
    vev_value_t label = vev_tx_fn_arg(args, 1);
    if (vev_value_kind(entity) != VEV_VALUE_INT || vev_value_kind(label) != VEV_VALUE_STRING) {
        return NULL;
    }
    const char *label_text = vev_value_text(label);
    snprintf(
        out,
        sizeof(out),
        "[:db/add %lld :user/seen-label \"%s\"]",
        vev_value_int(entity),
        label_text);
    vev_string_free(label_text);
    return out;
}

static int tx_report_ok_or_error(const char *label, vev_tx_report_t report) {
    if (report == NULL) {
        fprintf(stderr, "%s: null transaction report\n", label);
        return 0;
    }
    vev_value_t value = vev_tx_report_value(report);
    vev_value_t ok = map_get(value, ":ok");
    if (vev_value_kind(ok) == VEV_VALUE_BOOL && vev_value_bool(ok)) {
        return 1;
    }
    const char *edn = vev_tx_report_edn(report);
    fprintf(stderr, "%s: transaction failed: %s\n", label, edn);
    vev_string_free(edn);
    return 0;
}

int main(void) {
    printf("version: %s\n", vev_version());

    vev_conn_t conn = vev_conn_open_memory();
    if (conn == NULL) {
        fprintf(stderr, "failed to open Vev connection\n");
        return 1;
    }

    vev_tx_report_t tx_report = vev_transact_edn_report(
        conn,
        "[{:db/id 1 :user/name \"Ada\" :user/email \"ada@example.com\"}"
        " {:db/id 2 :user/name \"Grace\" :user/email \"grace@example.com\"}]");
    print_and_free("tx", vev_tx_report_edn(tx_report));
    if (!tx_report_ok_or_error("tx", tx_report)) {
        vev_tx_report_free(tx_report);
        vev_conn_close(conn);
        return 1;
    }
    vev_tx_report_free(tx_report);

    print_and_free(
        "tx-email-unique",
        vev_transact_edn(
            conn,
            "[[:db/add 90 :db/ident :user/email]"
            " [:db/add 90 :db/unique :db.unique/identity]]"));

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

    print_and_free(
        "tx-ref-schema",
        vev_transact_edn(
            conn,
            "[[:db/add 100 :db/ident :user/friend]"
            " [:db/add 100 :db/valueType :db.type/ref]"
            " [:db/add 1 :user/friend 2]]"));

    print_and_free(
        "tx-fn-ident",
        vev_transact_edn(
            conn,
            "[[:db/add 120 :db/ident :mark-seen]"
            " [:db/add 121 :db/ident :user/seen-label]]"));
    vev_tx_fn_registry_t tx_fns = vev_tx_fn_registry_create();
    if (tx_fns == NULL ||
        !vev_tx_fn_registry_register_edn(tx_fns, ":mark-seen", mark_seen_tx_fn, NULL)) {
        fprintf(stderr, "failed to register transaction callback\n");
        if (tx_fns != NULL) {
            vev_tx_fn_registry_free(tx_fns);
        }
        vev_conn_close(conn);
        return 1;
    }
    vev_tx_report_t tx_fn_report = vev_transact_edn_report_with_tx_fns(
        conn,
        "[[:db.fn/call :mark-seen 1 \"from-c\"]]",
        tx_fns);
    if (!tx_report_ok_or_error("tx-fn-callback", tx_fn_report)) {
        vev_tx_report_free(tx_fn_report);
        vev_tx_fn_registry_free(tx_fns);
        vev_conn_close(conn);
        return 1;
    }
    vev_tx_report_free(tx_fn_report);
    const char *tx_fn_check = vev_query_edn(
        conn,
        "[:find ?label :where [1 :user/seen-label ?label]]");
    printf("tx-fn-callback-check: %s\n", tx_fn_check);
    if (strstr(tx_fn_check, "from-c") == NULL) {
        fprintf(stderr, "transaction callback did not apply returned tx-data\n");
        vev_string_free(tx_fn_check);
        vev_tx_fn_registry_free(tx_fns);
        vev_conn_close(conn);
        return 1;
    }
    vev_string_free(tx_fn_check);
    vev_tx_fn_registry_free(tx_fns);

    vev_prepared_query_t query =
        vev_prepare_query_edn("[:find ?e ?email :in ?needle :where [?e :user/email ?email] [(= ?email ?needle)]]");
    if (query == NULL) {
        fprintf(stderr, "failed to prepare query\n");
        vev_conn_close(conn);
        return 1;
    }
    if (!vev_prepared_query_ok(query)) {
        const char *error = vev_prepared_query_error(query);
        fprintf(stderr, "unexpected prepared query error: %s\n", error);
        vev_string_free(error);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    vev_prepared_query_t invalid_query = vev_prepare_query_edn("[:find ?e :where [?e");
    if (invalid_query == NULL || vev_prepared_query_ok(invalid_query)) {
        fprintf(stderr, "invalid prepared query unexpectedly succeeded\n");
        if (invalid_query != NULL) {
            vev_prepared_query_free(invalid_query);
        }
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    const char *invalid_error = vev_prepared_query_error(invalid_query);
    if (invalid_error == NULL || strlen(invalid_error) == 0) {
        fprintf(stderr, "invalid prepared query did not expose an error\n");
        vev_string_free(invalid_error);
        vev_prepared_query_free(invalid_query);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    vev_string_free(invalid_error);
    vev_prepared_query_free(invalid_query);

    print_and_free("prepared", vev_query_prepared_with_inputs(conn, query, "[\"grace@example.com\"]"));

    vev_result_t result = vev_query_prepared_result_with_inputs(conn, query, "[\"grace@example.com\"]");
    if (result == NULL) {
        fprintf(stderr, "failed to query typed result handle\n");
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    print_result_rows(result);
    struct result_visit_stats result_stats = {0, 0, 0, 0};
    if (!vev_result_visit(result, count_result_visit, &result_stats) ||
        result_stats.row_begins != 1 ||
        result_stats.row_ends != 1 ||
        result_stats.values != 2 ||
        result_stats.pulls != 0) {
        fprintf(
            stderr,
            "unexpected result visitor stats: rows=%d/%d values=%d pulls=%d\n",
            result_stats.row_begins,
            result_stats.row_ends,
            result_stats.values,
            result_stats.pulls);
        vev_result_free(result);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
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
    struct result_visit_stats streamed_stmt_stats = {0, 0, 0, 0};
    if (!vev_query_stmt_visit(conn, stmt, count_result_visit, &streamed_stmt_stats) ||
        streamed_stmt_stats.row_begins != 1 ||
        streamed_stmt_stats.row_ends != 1 ||
        streamed_stmt_stats.values != 2 ||
        streamed_stmt_stats.pulls != 0) {
        fprintf(
            stderr,
            "unexpected streamed statement stats: rows=%d/%d values=%d pulls=%d\n",
            streamed_stmt_stats.row_begins,
            streamed_stmt_stats.row_ends,
            streamed_stmt_stats.values,
            streamed_stmt_stats.pulls);
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    if (vev_query_stmt_visit(conn, stmt, cancel_result_visit, NULL)) {
        fprintf(stderr, "cancelled statement visitor unexpectedly succeeded\n");
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    const char *stmt_error = vev_stmt_error(stmt);
    int cancelled = stmt_error != NULL && strstr(stmt_error, "cancelled") != NULL;
    vev_string_free(stmt_error);
    if (!cancelled) {
        fprintf(stderr, "cancelled statement visitor did not expose an error\n");
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

    vev_prepared_query_t collection_query =
        vev_prepare_query_edn("[:find ?name :in [?email ...] :where [?e :user/email ?email] [?e :user/name ?name]]");
    if (collection_query == NULL) {
        fprintf(stderr, "failed to prepare collection query\n");
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    vev_stmt_t collection_stmt = vev_stmt_create(collection_query);
    if (collection_stmt == NULL) {
        fprintf(stderr, "failed to create collection statement\n");
        vev_prepared_query_free(collection_query);
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    const char *emails[] = {"ada@example.com", "grace@example.com"};
    if (!vev_stmt_bind_string_collection(collection_stmt, emails, 2)) {
        fprintf(stderr, "failed to bind collection statement\n");
        vev_stmt_free(collection_stmt);
        vev_prepared_query_free(collection_query);
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    vev_result_t collection_stmt_result = vev_query_stmt_result(conn, collection_stmt);
    int collection_stmt_rows = result_row_count_or_error("stmt-collection", collection_stmt_result);
    printf("stmt-collection rows: %d\n", collection_stmt_rows);
    vev_result_free(collection_stmt_result);
    if (collection_stmt_rows != 2) {
        fprintf(stderr, "unexpected collection statement row count\n");
        vev_stmt_free(collection_stmt);
        vev_prepared_query_free(collection_query);
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    vev_stmt_free(collection_stmt);
    vev_prepared_query_free(collection_query);

    vev_prepared_query_t tuple_query =
        vev_prepare_query_edn("[:find ?e :in [?name ?email] :where [?e :user/name ?name] [?e :user/email ?email]]");
    if (tuple_query == NULL) {
        fprintf(stderr, "failed to prepare tuple query\n");
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    vev_stmt_t tuple_stmt = vev_stmt_create(tuple_query);
    if (tuple_stmt == NULL) {
        fprintf(stderr, "failed to create tuple statement\n");
        vev_prepared_query_free(tuple_query);
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    const char *tuple_values[] = {"Ada", "ada@example.com"};
    if (!vev_stmt_bind_string_tuple(tuple_stmt, tuple_values, 2)) {
        fprintf(stderr, "failed to bind tuple statement\n");
        vev_stmt_free(tuple_stmt);
        vev_prepared_query_free(tuple_query);
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    vev_result_t tuple_result = vev_query_stmt_result(conn, tuple_stmt);
    int tuple_rows = result_row_count_or_error("stmt-tuple", tuple_result);
    printf("stmt-tuple rows: %d\n", tuple_rows);
    vev_result_free(tuple_result);
    if (tuple_rows != 1) {
        fprintf(stderr, "unexpected tuple statement row count\n");
        vev_stmt_free(tuple_stmt);
        vev_prepared_query_free(tuple_query);
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    vev_stmt_free(tuple_stmt);
    vev_prepared_query_free(tuple_query);

    vev_prepared_query_t relation_query =
        vev_prepare_query_edn("[:find ?name ?label :in [[?email ?label]] :where [?e :user/email ?email] [?e :user/name ?name]]");
    if (relation_query == NULL) {
        fprintf(stderr, "failed to prepare relation query\n");
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    vev_stmt_t relation_stmt = vev_stmt_create(relation_query);
    if (relation_stmt == NULL) {
        fprintf(stderr, "failed to create relation statement\n");
        vev_prepared_query_free(relation_query);
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    const char *relation_values[] = {
        "ada@example.com",
        "primary",
        "missing@example.com",
        "missing",
    };
    if (!vev_stmt_bind_string_relation(relation_stmt, relation_values, 4, 2)) {
        fprintf(stderr, "failed to bind relation statement\n");
        vev_stmt_free(relation_stmt);
        vev_prepared_query_free(relation_query);
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    vev_result_t relation_result = vev_query_stmt_result(conn, relation_stmt);
    int relation_rows = result_row_count_or_error("stmt-relation", relation_result);
    vev_value_t relation_name = vev_result_value(relation_result, 0, 0);
    vev_value_t relation_label = vev_result_value(relation_result, 0, 1);
    printf("stmt-relation rows: %d\n", relation_rows);
    if (relation_rows != 1 ||
        !value_text_equals(relation_name, "Ada") ||
        !value_text_equals(relation_label, "primary")) {
        fprintf(stderr, "unexpected relation statement result\n");
        vev_result_free(relation_result);
        vev_stmt_free(relation_stmt);
        vev_prepared_query_free(relation_query);
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    vev_result_free(relation_result);
    vev_stmt_free(relation_stmt);
    vev_prepared_query_free(relation_query);

    vev_prepared_query_t lookup_query =
        vev_prepare_query_edn("[:find ?name :in ?person :where [?person :user/name ?name]]");
    if (lookup_query == NULL) {
        fprintf(stderr, "failed to prepare lookup-ref query\n");
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    vev_stmt_t lookup_stmt = vev_stmt_create(lookup_query);
    if (lookup_stmt == NULL) {
        fprintf(stderr, "failed to create lookup-ref statement\n");
        vev_prepared_query_free(lookup_query);
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    if (!vev_stmt_bind_lookup_ref_string(lookup_stmt, ":user/email", "ada@example.com")) {
        fprintf(stderr, "failed to bind lookup-ref statement\n");
        vev_stmt_free(lookup_stmt);
        vev_prepared_query_free(lookup_query);
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    vev_result_t lookup_result = vev_query_stmt_result(conn, lookup_stmt);
    int lookup_rows = result_row_count_or_error("stmt-lookup-ref", lookup_result);
    vev_value_t lookup_name = vev_result_value(lookup_result, 0, 0);
    printf("stmt-lookup-ref rows: %d\n", lookup_rows);
    if (lookup_rows != 1 || !value_text_equals(lookup_name, "Ada")) {
        fprintf(stderr, "unexpected lookup-ref statement result\n");
        vev_result_free(lookup_result);
        vev_stmt_free(lookup_stmt);
        vev_prepared_query_free(lookup_query);
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    vev_result_free(lookup_result);
    vev_stmt_free(lookup_stmt);
    vev_prepared_query_free(lookup_query);

    vev_prepared_query_t lookup_collection_query =
        vev_prepare_query_edn("[:find ?name :in [?person ...] :where [?person :user/name ?name]]");
    if (lookup_collection_query == NULL) {
        fprintf(stderr, "failed to prepare lookup-ref collection query\n");
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    vev_stmt_t lookup_collection_stmt = vev_stmt_create(lookup_collection_query);
    if (lookup_collection_stmt == NULL) {
        fprintf(stderr, "failed to create lookup-ref collection statement\n");
        vev_prepared_query_free(lookup_collection_query);
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    if (!vev_stmt_bind_lookup_ref_string_collection(lookup_collection_stmt, ":user/email", emails, 2)) {
        fprintf(stderr, "failed to bind lookup-ref collection statement\n");
        vev_stmt_free(lookup_collection_stmt);
        vev_prepared_query_free(lookup_collection_query);
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    vev_result_t lookup_collection_result = vev_query_stmt_result(conn, lookup_collection_stmt);
    int lookup_collection_rows = result_row_count_or_error("stmt-lookup-ref-collection", lookup_collection_result);
    printf("stmt-lookup-ref-collection rows: %d\n", lookup_collection_rows);
    vev_result_free(lookup_collection_result);
    if (lookup_collection_rows != 2) {
        fprintf(stderr, "unexpected lookup-ref collection statement row count\n");
        vev_stmt_free(lookup_collection_stmt);
        vev_prepared_query_free(lookup_collection_query);
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    vev_stmt_free(lookup_collection_stmt);
    vev_prepared_query_free(lookup_collection_query);

    vev_prepared_query_t pull_query =
        vev_prepare_query_edn("[:find (pull ?e [:user/name {:user/friend [:user/name]}]) :where [?e :user/name \"Ada\"]]");
    if (pull_query == NULL) {
        fprintf(stderr, "failed to prepare pull query\n");
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    vev_result_t pull_result = vev_query_prepared_result_with_inputs(conn, pull_query, "[]");
    int pull_rows = result_row_count_or_error("pull", pull_result);
    int pull_count = vev_result_pull_count(pull_result, 0);
    vev_value_t pulled = vev_result_pull(pull_result, 0, 0);
    vev_value_t name = map_get(pulled, ":user/name");
    vev_value_t friend_map = map_get(pulled, ":user/friend");
    vev_value_t friend_name = map_get(friend_map, ":user/name");
    printf("pull rows: %d pulls: %d\n", pull_rows, pull_count);
    if (pull_rows != 1 ||
        pull_count != 1 ||
        vev_value_kind(pulled) != VEV_VALUE_MAP ||
        !value_text_equals(name, "Ada") ||
        !value_text_equals(friend_name, "Grace")) {
        const char *edn = vev_value_edn(pulled);
        fprintf(stderr, "unexpected pull traversal output: %s\n", edn);
        vev_string_free(edn);
        vev_result_free(pull_result);
        vev_prepared_query_free(pull_query);
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    struct value_visit_stats visit_stats = {0, 0, 0, 0};
    if (!vev_value_visit(pulled, count_value_visit, &visit_stats) ||
        visit_stats.values < 7 ||
        visit_stats.ends != 2 ||
        visit_stats.max_depth < 2 ||
        visit_stats.depth != 0) {
        fprintf(
            stderr,
            "unexpected pull visitor stats: values=%d ends=%d max_depth=%d depth=%d\n",
            visit_stats.values,
            visit_stats.ends,
            visit_stats.max_depth,
            visit_stats.depth);
        vev_result_free(pull_result);
        vev_prepared_query_free(pull_query);
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    struct result_visit_stats pull_result_stats = {0, 0, 0, 0};
    if (!vev_result_visit(pull_result, count_result_visit, &pull_result_stats) ||
        pull_result_stats.row_begins != 1 ||
        pull_result_stats.row_ends != 1 ||
        pull_result_stats.values != 0 ||
        pull_result_stats.pulls != 1) {
        fprintf(
            stderr,
            "unexpected pull result visitor stats: rows=%d/%d values=%d pulls=%d\n",
            pull_result_stats.row_begins,
            pull_result_stats.row_ends,
            pull_result_stats.values,
            pull_result_stats.pulls);
        vev_result_free(pull_result);
        vev_prepared_query_free(pull_query);
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    vev_result_free(pull_result);
    vev_prepared_query_free(pull_query);

    vev_db_t pull_db = vev_conn_db(conn);
    if (pull_db == NULL) {
        fprintf(stderr, "failed to retain DB for pull API\n");
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }

    vev_value_handle_t direct_pull =
        vev_pull_edn(pull_db, "[:user/name {:user/friend [:user/name]}]", 1);
    if (direct_pull == NULL) {
        fprintf(stderr, "failed direct pull API\n");
        vev_db_release(pull_db);
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    vev_value_t direct_value = vev_value_handle_value(direct_pull);
    const char *direct_edn = vev_value_handle_edn(direct_pull);
    printf("direct-pull: %s\n", direct_edn);
    vev_string_free(direct_edn);
    if (!value_text_equals(map_get(direct_value, ":user/name"), "Ada") ||
        !value_text_equals(map_get(map_get(direct_value, ":user/friend"), ":user/name"), "Grace")) {
        fprintf(stderr, "unexpected direct pull output\n");
        vev_value_handle_free(direct_pull);
        vev_db_release(pull_db);
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    vev_value_handle_free(direct_pull);

    vev_value_handle_t lookup_pull =
        vev_pull_lookup_ref_string_edn(pull_db, "[:user/name]", ":user/email", "ada@example.com");
    if (lookup_pull == NULL) {
        fprintf(stderr, "failed lookup-ref pull API\n");
        vev_db_release(pull_db);
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    vev_value_t lookup_value = vev_value_handle_value(lookup_pull);
    if (!value_text_equals(map_get(lookup_value, ":user/name"), "Ada")) {
        fprintf(stderr, "unexpected lookup-ref pull output\n");
        vev_value_handle_free(lookup_pull);
        vev_db_release(pull_db);
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    vev_value_handle_free(lookup_pull);

    unsigned long long pull_many_ids[] = {1, 2};
    vev_value_handle_t many_pull =
        vev_pull_many_edn(pull_db, "[:user/name]", pull_many_ids, 2);
    if (many_pull == NULL) {
        fprintf(stderr, "failed pull-many API\n");
        vev_db_release(pull_db);
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    vev_value_t many_value = vev_value_handle_value(many_pull);
    if (vev_value_kind(many_value) != VEV_VALUE_VECTOR ||
        vev_value_item_count(many_value) != 2 ||
        !value_text_equals(map_get(vev_value_item(many_value, 0), ":user/name"), "Ada") ||
        !value_text_equals(map_get(vev_value_item(many_value, 1), ":user/name"), "Grace")) {
        const char *many_edn = vev_value_handle_edn(many_pull);
        fprintf(stderr, "unexpected pull-many output: %s\n", many_edn);
        vev_string_free(many_edn);
        vev_value_handle_free(many_pull);
        vev_db_release(pull_db);
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    vev_value_handle_free(many_pull);
    vev_db_release(pull_db);

    vev_prepared_query_t pull_pattern_query =
        vev_prepare_query_edn("[:find (pull ?e ?pattern) :in ?pattern ?name :where [?e :user/name ?name]]");
    if (pull_pattern_query == NULL) {
        fprintf(stderr, "failed to prepare pull-pattern statement query\n");
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    vev_stmt_t pull_pattern_stmt = vev_stmt_create(pull_pattern_query);
    if (pull_pattern_stmt == NULL ||
        !vev_stmt_bind_pull_pattern_edn(pull_pattern_stmt, "[:user/name {:user/friend [:user/name]}]") ||
        !vev_stmt_bind_string(pull_pattern_stmt, "Ada")) {
        fprintf(stderr, "failed to bind pull-pattern statement\n");
        if (pull_pattern_stmt != NULL) vev_stmt_free(pull_pattern_stmt);
        vev_prepared_query_free(pull_pattern_query);
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    vev_result_t pull_pattern_result = vev_query_stmt_result(conn, pull_pattern_stmt);
    int pull_pattern_rows = result_row_count_or_error("stmt-pull-pattern", pull_pattern_result);
    int pull_pattern_count = vev_result_pull_count(pull_pattern_result, 0);
    vev_value_t bound_pull = vev_result_pull(pull_pattern_result, 0, 0);
    if (pull_pattern_rows != 1 ||
        pull_pattern_count != 1 ||
        !value_text_equals(map_get(bound_pull, ":user/name"), "Ada") ||
        !value_text_equals(map_get(map_get(bound_pull, ":user/friend"), ":user/name"), "Grace")) {
        const char *bound_edn = vev_value_edn(bound_pull);
        fprintf(stderr, "unexpected pull-pattern statement output: %s\n", bound_edn);
        vev_string_free(bound_edn);
        vev_result_free(pull_pattern_result);
        vev_stmt_free(pull_pattern_stmt);
        vev_prepared_query_free(pull_pattern_query);
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    vev_result_free(pull_pattern_result);
    vev_stmt_free(pull_pattern_stmt);
    vev_prepared_query_free(pull_pattern_query);

    vev_conn_t right_conn = vev_conn_open_memory();
    if (right_conn == NULL) {
        fprintf(stderr, "failed to open right source connection\n");
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    print_and_free(
        "right-source-tx",
        vev_transact_edn(
            right_conn,
            "[{:db/id 1 :user/name \"Ada Right\"}"
            " {:db/id 2 :user/name \"Grace Right\"}]"));
    vev_db_t left_source = vev_conn_db(conn);
    vev_db_t right_source = vev_conn_db(right_conn);
    const char *source_names[] = {"$left", "$right"};
    vev_prepared_query_t source_query =
        vev_prepare_query_edn_with_sources(
            "[:find ?e ?left-name ?right-name"
            " :in $left $right [?e ...]"
            " :where [$left ?e :user/name ?left-name]"
            "        [$right ?e :user/name ?right-name]]",
            source_names,
            2);
    vev_stmt_t source_stmt = source_query == NULL ? NULL : vev_stmt_create(source_query);
    unsigned long long source_ids[] = {1, 2};
    if (left_source == NULL ||
        right_source == NULL ||
        source_query == NULL ||
        source_stmt == NULL ||
        !vev_stmt_bind_db_source(source_stmt, "$left", left_source) ||
        !vev_stmt_bind_db_source(source_stmt, "$right", right_source) ||
        !vev_stmt_bind_entity_collection(source_stmt, source_ids, 2)) {
        fprintf(stderr, "failed to bind source statement\n");
        if (source_stmt != NULL) vev_stmt_free(source_stmt);
        if (source_query != NULL) vev_prepared_query_free(source_query);
        if (left_source != NULL) vev_db_release(left_source);
        if (right_source != NULL) vev_db_release(right_source);
        vev_conn_close(right_conn);
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    vev_result_t source_result = vev_query_stmt_result(conn, source_stmt);
    int source_rows = result_row_count_or_error("stmt-db-sources", source_result);
    vev_value_t source_left_name = vev_result_value(source_result, 0, 1);
    vev_value_t source_right_name = vev_result_value(source_result, 0, 2);
    printf("stmt-db-sources rows: %d\n", source_rows);
    if (source_rows != 2 ||
        !value_text_equals(source_left_name, "Ada") ||
        !value_text_equals(source_right_name, "Ada Right")) {
        fprintf(stderr, "unexpected DB source statement output\n");
        vev_result_free(source_result);
        vev_stmt_free(source_stmt);
        vev_prepared_query_free(source_query);
        vev_db_release(left_source);
        vev_db_release(right_source);
        vev_conn_close(right_conn);
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        vev_conn_close(conn);
        return 1;
    }
    vev_result_free(source_result);
    vev_stmt_free(source_stmt);
    vev_prepared_query_free(source_query);
    vev_db_release(left_source);
    vev_db_release(right_source);
    vev_conn_close(right_conn);

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
    vev_db_t retained_snapshot = vev_db_retain(snapshot);
    vev_db_release(snapshot);
    snapshot = retained_snapshot;
    if (snapshot == NULL) {
        fprintf(stderr, "failed to retain DB snapshot copy\n");
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

    vev_prepared_query_t barbara_query =
        vev_prepare_query_edn("[:find ?e :where [?e :user/name \"Barbara\"]]");
    vev_prepared_query_t dorothy_query =
        vev_prepare_query_edn("[:find ?e :where [?e :user/name \"Dorothy\"]]");
    if (barbara_query == NULL || dorothy_query == NULL) {
        fprintf(stderr, "failed to prepare DB value queries\n");
        if (barbara_query != NULL) vev_prepared_query_free(barbara_query);
        if (dorothy_query != NULL) vev_prepared_query_free(dorothy_query);
        vev_db_release(snapshot);
        vev_prepared_query_free(all_emails);
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        return 1;
    }

    vev_tx_report_t with_report =
        vev_with_edn_report(snapshot, "[{:db/id 4 :user/name \"Barbara\"}]");
    print_and_free("with-db", vev_tx_report_edn(with_report));
    if (!tx_report_ok_or_error("with-db", with_report)) {
        vev_tx_report_free(with_report);
        vev_prepared_query_free(dorothy_query);
        vev_prepared_query_free(barbara_query);
        vev_db_release(snapshot);
        vev_prepared_query_free(all_emails);
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        return 1;
    }
    vev_tx_report_free(with_report);

    vev_db_t next_db = vev_db_with_edn(snapshot, "[{:db/id 4 :user/name \"Barbara\"}]");
    if (next_db == NULL) {
        fprintf(stderr, "failed to create DB value with tx data\n");
        vev_prepared_query_free(dorothy_query);
        vev_prepared_query_free(barbara_query);
        vev_db_release(snapshot);
        vev_prepared_query_free(all_emails);
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        return 1;
    }

    vev_result_t source_barbara = vev_query_db_prepared_result_with_inputs(snapshot, barbara_query, "[]");
    vev_result_t next_barbara = vev_query_db_prepared_result_with_inputs(next_db, barbara_query, "[]");
    int source_barbara_rows = result_row_count_or_error("source-barbara", source_barbara);
    int next_barbara_rows = result_row_count_or_error("next-barbara", next_barbara);
    vev_result_free(source_barbara);
    vev_result_free(next_barbara);
    if (source_barbara_rows != 0 || next_barbara_rows != 1) {
        fprintf(stderr, "unexpected db-with rows: source=%d next=%d\n", source_barbara_rows, next_barbara_rows);
        vev_db_release(next_db);
        vev_prepared_query_free(dorothy_query);
        vev_prepared_query_free(barbara_query);
        vev_db_release(snapshot);
        vev_prepared_query_free(all_emails);
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        return 1;
    }

    vev_conn_t derived = vev_conn_from_db(next_db);
    if (derived == NULL) {
        fprintf(stderr, "failed to create derived connection\n");
        vev_db_release(next_db);
        vev_prepared_query_free(dorothy_query);
        vev_prepared_query_free(barbara_query);
        vev_db_release(snapshot);
        vev_prepared_query_free(all_emails);
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        return 1;
    }
    print_and_free(
        "derived-tx",
        vev_transact_edn(derived, "[{:db/id 5 :user/name \"Dorothy\"}]"));
    vev_result_t derived_barbara = vev_query_prepared_result_with_inputs(derived, barbara_query, "[]");
    vev_result_t derived_dorothy = vev_query_prepared_result_with_inputs(derived, dorothy_query, "[]");
    int derived_barbara_rows = result_row_count_or_error("derived-barbara", derived_barbara);
    int derived_dorothy_rows = result_row_count_or_error("derived-dorothy", derived_dorothy);
    vev_result_free(derived_barbara);
    vev_result_free(derived_dorothy);
    vev_conn_close(derived);
    if (derived_barbara_rows != 1 || derived_dorothy_rows != 1) {
        fprintf(stderr, "unexpected derived connection rows\n");
        vev_db_release(next_db);
        vev_prepared_query_free(dorothy_query);
        vev_prepared_query_free(barbara_query);
        vev_db_release(snapshot);
        vev_prepared_query_free(all_emails);
        vev_stmt_free(stmt);
        vev_prepared_query_free(query);
        return 1;
    }

    vev_db_release(next_db);
    vev_prepared_query_free(dorothy_query);
    vev_prepared_query_free(barbara_query);
    vev_db_release(snapshot);
    vev_prepared_query_free(all_emails);

    vev_stmt_free(stmt);
    vev_prepared_query_free(query);
    if (conn != NULL) {
        vev_conn_close(conn);
    }
    return 0;
}
