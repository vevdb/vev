#!/usr/bin/env bash

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KVIST_BIN="${KVIST_BIN:-kvist}"

if [[ "$#" -ne 0 ]]; then
  echo "usage: $0" >&2
  exit 2
fi

# These suites have explicit ownership cleanup and are expected to pass Odin's
# per-test allocator accounting. The complete functional suite is run by
# test_unit.sh without memory tracking because several integration tests retain
# shared fixtures or allocate from worker threads beyond a single test scope.
tests=(
  query_page_test.kvist
  storage_architecture_test.kvist
  vev_test.kvist
)

for test_file in "${tests[@]}"; do
  relative="src/vev_tests/$test_file"
  echo "memory-test=$relative" >&2
  "$KVIST_BIN" test "$ROOT/$relative" --track-memory
done
