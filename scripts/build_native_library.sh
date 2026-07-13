#!/usr/bin/env bash
# Copyright (c) Andreas Flakstad and Vev contributors
# SPDX-License-Identifier: EPL-2.0

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KVIST_BIN="${KVIST_BIN:-kvist}"
GENERATED_DIR="$ROOT/build/generated/vev_abi"
LIB_DIR="$ROOT/build/lib"
PKGCONFIG_DIR="$LIB_DIR/pkgconfig"
IF_NEEDED="false"

if [[ "${1:-}" == "--if-needed" ]]; then
  IF_NEEDED="true"
  shift
fi

if [[ $# -ne 0 ]]; then
  echo "usage: scripts/build_native_library.sh [--if-needed]" >&2
  exit 1
fi

case "$(uname -s)" in
  Darwin) LIB_NAME="libvev.dylib" ;;
  Linux) LIB_NAME="libvev.so" ;;
  MINGW*|MSYS*|CYGWIN*) LIB_NAME="vev.dll" ;;
  *) echo "unsupported OS: $(uname -s)" >&2; exit 1 ;;
esac

LIB_PATH="$LIB_DIR/$LIB_NAME"
mkdir -p "$GENERATED_DIR" "$LIB_DIR" "$PKGCONFIG_DIR"

if [[ "$IF_NEEDED" == "true" && -f "$LIB_PATH" ]] &&
   ! find "$ROOT/src/vev" "$ROOT/src/vev_abi" -type f -newer "$LIB_PATH" -print -quit | grep -q .; then
  echo "$LIB_PATH"
  exit 0
fi

if [[ -n "${KVIST_REPO_DIR:-}" ]]; then
  (
    cd "$KVIST_REPO_DIR"
    "$KVIST_BIN" compile "$ROOT/src/vev_abi/vev_abi.kvist" -o "$GENERATED_DIR/vev_abi.odin"
  )
else
  "$KVIST_BIN" compile "$ROOT/src/vev_abi/vev_abi.kvist" -o "$GENERATED_DIR/vev_abi.odin"
fi

odin build "$GENERATED_DIR" -build-mode:dll -out:"$LIB_PATH"

cat > "$PKGCONFIG_DIR/vev.pc" <<EOF
prefix=$ROOT/build
exec_prefix=\${prefix}
libdir=\${prefix}/lib
includedir=$ROOT/include

Name: Vev
Description: Embedded native Datalog database
Version: 0.1.0
Libs: -L\${libdir} -lvev
Cflags: -I\${includedir}
EOF

echo "$LIB_PATH"
