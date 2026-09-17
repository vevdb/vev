#!/usr/bin/env bash

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KVIST_BIN="${KVIST_BIN:-kvist}"

case "$(uname -s)" in
  Darwin) LIB_NAME="libvev.dylib" ;;
  Linux) LIB_NAME="libvev.so" ;;
  MINGW*|MSYS*|CYGWIN*) LIB_NAME="vev.dll" ;;
  *) echo "unsupported OS: $(uname -s)" >&2; exit 1 ;;
esac

VEV_LIB="${VEV_LIB:-$ROOT/build/lib/$LIB_NAME}"
if [[ ! -f "$VEV_LIB" ]]; then
  VEV_LIB="$($ROOT/scripts/build_native_library.sh)"
fi
export VEV_LIB

if [[ "$#" -ne 0 ]]; then
  echo "usage: $0" >&2
  exit 2
fi

# These suites have explicit ownership cleanup and are expected to pass Odin's
# per-test allocator accounting. The complete functional suite is run by
# test_unit.sh without memory tracking because several integration tests retain
# shared fixtures or allocate from worker threads beyond a single test scope.
tests=(
  kvist_report_value_boundary_test.kvist
  query_page_test.kvist
  storage_architecture_test.kvist
  vev_test.kvist
)

for test_file in "${tests[@]}"; do
  relative="src/vev_tests/$test_file"
  echo "memory-test=$relative" >&2
  "$KVIST_BIN" test "$ROOT/$relative" --track-memory
done
