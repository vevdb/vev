#!/usr/bin/env bash
# Copyright (c) Andreas Flakstad and Vev contributors
# SPDX-License-Identifier: EPL-2.0

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

case "$(uname -s)" in
  Darwin) OS="darwin"; LIB_NAME="libvev.dylib" ;;
  Linux) OS="linux"; LIB_NAME="libvev.so" ;;
  MINGW*|MSYS*|CYGWIN*) OS="windows"; LIB_NAME="vev.dll" ;;
  *) echo "unsupported OS: $(uname -s)" >&2; exit 1 ;;
esac

case "$(uname -m)" in
  arm64|aarch64) ARCH="aarch64" ;;
  x86_64|amd64) ARCH="x86_64" ;;
  *) echo "unsupported architecture: $(uname -m)" >&2; exit 1 ;;
esac

if [[ ! -f "$ROOT/build/lib/$LIB_NAME" ]]; then
  "$ROOT/scripts/build_c_abi.sh"
fi

TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/vev-python-package.XXXXXX")"
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

mkdir -p "$TMP_DIR/native/$OS-$ARCH"
cp "$ROOT/clients/python/vev.py" "$TMP_DIR/vev.py"
cp "$ROOT/build/lib/$LIB_NAME" "$TMP_DIR/native/$OS-$ARCH/$LIB_NAME"

(
  cd "$TMP_DIR"
  env -u VEV_LIB python3 - <<'PY'
import vev

with vev.open_memory() as conn:
    conn.transact('[{:db/id 1 :user/name "Ada"}]')
    result = conn.query_text('[:find ?name :where [?e :user/name ?name]]')
    assert '"Ada"' in result, result
print(":vev-python-package-ok")
PY
)
