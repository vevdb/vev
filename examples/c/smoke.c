#include <stdio.h>
#include "vev.h"

static void print_and_free(const char *label, const char *text) {
    printf("%s: %s\n", label, text);
    vev_string_free(text);
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

    vev_prepared_query_free(query);
    vev_conn_close(conn);
    return 0;
}
