#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include "vev.h"

static double now_us(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ((double)ts.tv_sec * 1000000.0) + ((double)ts.tv_nsec / 1000.0);
}

static int compare_double(const void *a, const void *b) {
    double left = *(const double *)a;
    double right = *(const double *)b;
    return (left > right) - (left < right);
}

static char *people_tx(int n) {
    size_t cap = (size_t)n * 96 + 2;
    char *out = malloc(cap);
    if (out == NULL) {
        return NULL;
    }

    size_t len = 0;
    out[len++] = '[';
    for (int i = 1; i <= n; i++) {
        int written = snprintf(
            out + len,
            cap - len,
            "%s{:db/id %d :email \"user-%d@example.com\" :name \"User %d\" :age %d}",
            i == 1 ? "" : " ",
            i,
            i,
            i,
            i % 100);
        if (written < 0 || (size_t)written >= cap - len) {
            free(out);
            return NULL;
        }
        len += (size_t)written;
    }
    out[len++] = ']';
    out[len] = '\0';
    return out;
}

int main(void) {
    const int n = 1000;
    const int warmups = 100;
    const int sample_count = 1000;
    const char *query =
        "[:find ?name ?age :in ?email :where [?e :email ?email] [?e :name ?name] [?e :age ?age]]";
    const char *inputs = "[\"user-500@example.com\"]";

    vev_conn_t conn = vev_conn_open_memory();
    if (conn == NULL) {
        fprintf(stderr, "failed to open Vev connection\n");
        return 1;
    }

    char *tx = people_tx(n);
    if (tx == NULL) {
        fprintf(stderr, "failed to build transaction text\n");
        vev_conn_close(conn);
        return 1;
    }

    const char *tx_result = vev_transact_edn(conn, tx);
    vev_string_free(tx_result);
    free(tx);

    vev_prepared_query_t prepared = vev_prepare_query_edn(query);
    if (prepared == NULL) {
        fprintf(stderr, "failed to prepare query\n");
        vev_conn_close(conn);
        return 1;
    }

    for (int i = 0; i < warmups; i++) {
        const char *result = vev_query_prepared_with_inputs(conn, prepared, inputs);
        vev_string_free(result);
    }

    double *samples = malloc(sizeof(double) * (size_t)sample_count);
    if (samples == NULL) {
        fprintf(stderr, "failed to allocate samples\n");
        vev_prepared_query_free(prepared);
        vev_conn_close(conn);
        return 1;
    }

    for (int i = 0; i < sample_count; i++) {
        double start = now_us();
        const char *result = vev_query_prepared_with_inputs(conn, prepared, inputs);
        double elapsed = now_us() - start;
        vev_string_free(result);
        samples[i] = elapsed;
    }

    qsort(samples, (size_t)sample_count, sizeof(double), compare_double);
    printf(
        "engine=c-abi workload=prepared-email-input-text n=%d median_us=%.0f samples=%d\n",
        n,
        samples[sample_count / 2],
        sample_count);

    free(samples);
    vev_prepared_query_free(prepared);
    vev_conn_close(conn);
    return 0;
}
