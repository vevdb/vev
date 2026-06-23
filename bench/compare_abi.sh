#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

KVIST_ROOT="${KVIST_ROOT:-/Users/andreas/Projects/kvist/.worktrees/codex-item-kvist-case-default}"
KVIST_BIN="${KVIST_BIN:-$KVIST_ROOT/kvist}"
KVIST_PACKAGES_DIR="${KVIST_PACKAGES_DIR:-/Users/andreas/Projects/kvist/packages}"

KVIST_BIN="$KVIST_BIN" \
KVIST_REPO_DIR="$KVIST_ROOT" \
KVIST_PACKAGES_DIR="$KVIST_PACKAGES_DIR" \
"$REPO_ROOT/scripts/build_c_abi.sh" >/dev/null

mkdir -p "$REPO_ROOT/build/bench"
clang \
  -I"$REPO_ROOT/include" \
  "$REPO_ROOT/bench/c_abi.c" \
  -L"$REPO_ROOT/build/lib" \
  -lvev \
  -Wl,-rpath,"$REPO_ROOT/build/lib" \
  -o "$REPO_ROOT/build/bench/c_abi"

NATIVE_OUT="$(mktemp)"
C_OUT="$(mktemp)"
trap 'rm -f "$NATIVE_OUT" "$C_OUT"' EXIT

(
  cd "$KVIST_ROOT"
  KVIST_PACKAGES_DIR="$KVIST_PACKAGES_DIR" "$KVIST_BIN" run "$REPO_ROOT/bench/abi_native.kvist"
) > "$NATIVE_OUT"

"$REPO_ROOT/build/bench/c_abi" > "$C_OUT"

cat "$NATIVE_OUT"
cat "$C_OUT"

awk '
function field_value(prefix,    i) {
  for (i = 1; i <= NF; i++) {
    if (index($i, prefix "=") == 1) {
      return substr($i, length(prefix) + 2)
    }
  }
  return ""
}

/^engine=vev-native / {
  native = field_value("median_us") + 0
}

/^engine=c-abi / {
  cabi = field_value("median_us") + 0
}

END {
  if (native > 0 && cabi > 0) {
    printf "comparison workload=prepared-email-input-text c_abi_over_native=%.2fx\n", cabi / native
  }
}
' "$NATIVE_OUT" "$C_OUT"
