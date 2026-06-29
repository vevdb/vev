#!/usr/bin/env bash
# Copyright (c) Andreas Flakstad and Vev contributors
# SPDX-License-Identifier: EPL-2.0

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

case "$(uname -s)" in
  Darwin) LIB_NAME="libvev.dylib" ;;
  Linux) LIB_NAME="libvev.so" ;;
  MINGW*|MSYS*|CYGWIN*) LIB_NAME="vev.dll" ;;
  *) echo "unsupported OS: $(uname -s)" >&2; exit 1 ;;
esac

if [[ ! -f "$ROOT/build/lib/$LIB_NAME" || ! -f "$ROOT/build/lib/pkgconfig/vev.pc" ]]; then
  "$ROOT/scripts/build_c_abi.sh"
fi

TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/vev-c-package.XXXXXX")"
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

cat > "$TMP_DIR/smoke.c" <<'EOF'
#include "vev.h"
#include <stdio.h>
#include <string.h>

int main(void) {
    vev_conn_t conn = vev_conn_open_memory();
    if (!conn) {
        fprintf(stderr, "failed to open Vev connection\n");
        return 1;
    }

    const char *tx = vev_transact_edn(conn, "[{:db/id 1 :user/name \"Ada\"}]");
    if (!tx || !strstr(tx, ":ok true")) {
        fprintf(stderr, "unexpected tx: %s\n", tx ? tx : "<null>");
        if (tx) vev_string_free(tx);
        vev_conn_close(conn);
        return 1;
    }
    vev_string_free(tx);

    const char *result = vev_query_edn(conn, "[:find ?name :where [?e :user/name ?name]]");
    if (!result || !strstr(result, "\"Ada\"")) {
        fprintf(stderr, "unexpected query result: %s\n", result ? result : "<null>");
        if (result) vev_string_free(result);
        vev_conn_close(conn);
        return 1;
    }
    vev_string_free(result);
    vev_conn_close(conn);
    puts(":vev-c-package-ok");
    return 0;
}
EOF

PKG_CONFIG_PATH="$ROOT/build/lib/pkgconfig" \
  clang "$TMP_DIR/smoke.c" \
  $(PKG_CONFIG_PATH="$ROOT/build/lib/pkgconfig" pkg-config --cflags --libs vev) \
  -Wl,-rpath,"$ROOT/build/lib" \
  -o "$TMP_DIR/smoke"

"$TMP_DIR/smoke"
