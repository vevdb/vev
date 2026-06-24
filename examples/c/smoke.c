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
    vev_result_free(pull_result);
    vev_prepared_query_free(pull_query);

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
