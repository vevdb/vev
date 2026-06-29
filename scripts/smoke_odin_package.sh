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

if ! command -v odin >/dev/null 2>&1; then
  echo "odin not found; skipping Odin package smoke"
  exit 0
fi

if [[ ! -f "$ROOT/build/lib/$LIB_NAME" ]]; then
  "$ROOT/scripts/build_c_abi.sh"
fi

TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/vev-odin-package.XXXXXX")"
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

odin build "$ROOT/clients/odin" -file -out:"$TMP_DIR/vev_odin_smoke"
"$TMP_DIR/vev_odin_smoke" "$ROOT/build/lib/$LIB_NAME" >/dev/null
echo ":vev-odin-package-ok"
