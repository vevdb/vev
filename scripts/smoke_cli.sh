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

if [[ ! -x "$ROOT/build/vev" || ! -f "$ROOT/build/lib/$LIB_NAME" ]]; then
  "$ROOT/scripts/build_c_abi.sh" >/dev/null
fi

DB="${TMPDIR:-/tmp}/vev-cli-smoke.sqlite"
rm -f "$DB" "$DB-shm" "$DB-wal"
cleanup() {
  rm -f "$DB" "$DB-shm" "$DB-wal"
}
trap cleanup EXIT

tx="$("$ROOT/build/vev" transact "$DB" '[{:db/id 1 :user/name "Ada"}]')"
query="$("$ROOT/build/vev" query "$DB" '[:find ?name :where [?e :user/name ?name]]')"
pull="$("$ROOT/build/vev" pull "$DB" '[:user/name]' 1)"
info="$("$ROOT/build/vev" info "$DB")"

case "$tx" in *":ok true"*) ;; *) echo "unexpected tx: $tx" >&2; exit 1 ;; esac
case "$query" in *'"Ada"'*) ;; *) echo "unexpected query: $query" >&2; exit 1 ;; esac
case "$pull" in *'"Ada"'*) ;; *) echo "unexpected pull: $pull" >&2; exit 1 ;; esac
case "$info" in *":backend :sqlite"*":tx-count 1"*) ;; *) echo "unexpected info: $info" >&2; exit 1 ;; esac

echo ":vev-cli-ok"
