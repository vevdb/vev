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

LIB_PATH="$ROOT/build/lib/$LIB_NAME"
if [[ ! -f "$LIB_PATH" ]]; then
  "$ROOT/scripts/build_native_library.sh" >/dev/null
fi

usage() {
  echo "usage: $0" >&2
}

if [[ "$#" -ne 0 ]]; then
  usage
  exit 2
fi

tests=(
  app_durable_test.kvist
  durable_dedupe_ownership_test.kvist
  index_storage_test.kvist
  kvist_report_value_boundary_test.kvist
  managed_ownership_test.kvist
  musicbrainz_test.kvist
  parser_input_test.kvist
  prepared_ownership_test.kvist
  query_page_test.kvist
  relation_db_test.kvist
  rule_engine_test.kvist
  source_branch_test.kvist
  storage_architecture_test.kvist
  storage_manifest_prefix_test.kvist
  storage_with_test.kvist
  transaction_incremental_test.kvist
  typed_relation_test.kvist
  vev_test.kvist
)

for test_file in "${tests[@]}"; do
  relative="src/vev_tests/$test_file"
  echo "unit-test=$relative" >&2
  VEV_LIB="$LIB_PATH" "$KVIST_BIN" test "$ROOT/$relative"
done
