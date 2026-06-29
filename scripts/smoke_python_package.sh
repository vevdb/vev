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
cp "$ROOT/clients/python/pyproject.toml" "$TMP_DIR/pyproject.toml"
cp "$ROOT/clients/python/README.md" "$TMP_DIR/README.md"
cp "$ROOT/build/lib/$LIB_NAME" "$TMP_DIR/native/$OS-$ARCH/$LIB_NAME"

(
  cd "$TMP_DIR"
  python3 - <<'PY'
import pathlib
import tomllib

metadata = tomllib.loads(pathlib.Path("pyproject.toml").read_text())
assert metadata["project"]["name"] == "vev", metadata
assert metadata["tool"]["setuptools"]["py-modules"] == ["vev"], metadata
PY
  env -u VEV_LIB python3 - <<'PY'
import vev

with vev.create_conn() as conn:
    conn.transact('[{:db/id 1 :user/name "Ada"}]')
    result = conn.query_text('[:find ?name :where [?e :user/name ?name]]')
    assert '"Ada"' in result, result
with vev.Library().create_conn() as conn:
    conn.transact('[{:db/id 2 :user/name "Grace"}]')
    result = conn.query_text('[:find ?name :where [?e :user/name ?name]]')
    assert '"Grace"' in result, result
print(":vev-python-package-ok")
PY
)
