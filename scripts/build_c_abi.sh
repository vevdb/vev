#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KVIST_BIN="${KVIST_BIN:-kvist}"

GENERATED_DIR="$ROOT/build/generated/vev_abi"
LIB_DIR="$ROOT/build/lib"
EXAMPLE_DIR="$ROOT/build/examples/c"
RUST_EXAMPLE_DIR="$ROOT/build/examples/rust"

mkdir -p "$GENERATED_DIR" "$LIB_DIR" "$EXAMPLE_DIR" "$RUST_EXAMPLE_DIR"

if [[ -n "${KVIST_REPO_DIR:-}" ]]; then
  (
    cd "$KVIST_REPO_DIR"
    "$KVIST_BIN" compile "$ROOT/src/vev_abi/vev_abi.kvist" -o "$GENERATED_DIR/vev_abi.odin"
  )
else
  "$KVIST_BIN" compile "$ROOT/src/vev_abi/vev_abi.kvist" -o "$GENERATED_DIR/vev_abi.odin"
fi
odin build "$GENERATED_DIR" -build-mode:dll -out:"$LIB_DIR/libvev.dylib"

clang \
  -I"$ROOT/include" \
  "$ROOT/examples/c/smoke.c" \
  -L"$LIB_DIR" \
  -lvev \
  -Wl,-rpath,"$LIB_DIR" \
  -o "$EXAMPLE_DIR/vev_c_smoke"

"$EXAMPLE_DIR/vev_c_smoke"

python3 "$ROOT/examples/python/smoke.py"

if command -v rustc >/dev/null 2>&1; then
  rustc \
    "$ROOT/examples/rust/smoke.rs" \
    -L "$LIB_DIR" \
    -l dylib=vev \
    -C "link-arg=-Wl,-rpath,$LIB_DIR" \
    -o "$RUST_EXAMPLE_DIR/vev_rust_smoke"

  "$RUST_EXAMPLE_DIR/vev_rust_smoke"
else
  echo "rustc not found; skipping Rust smoke"
fi
