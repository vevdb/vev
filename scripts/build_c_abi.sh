#!/usr/bin/env bash
# Copyright (c) Andreas Flakstad and Vev contributors
# SPDX-License-Identifier: EPL-2.0

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KVIST_BIN="${KVIST_BIN:-kvist}"

GENERATED_DIR="$ROOT/build/generated/vev_abi"
LIB_DIR="$ROOT/build/lib"
EXAMPLE_DIR="$ROOT/build/examples/c"
RUST_EXAMPLE_DIR="$ROOT/build/examples/rust"
JAVA_EXAMPLE_DIR="$ROOT/build/examples/java"
GO_EXAMPLE_DIR="$ROOT/build/examples/go"
NODE_EXAMPLE_DIR="$ROOT/build/examples/node"

mkdir -p "$GENERATED_DIR" "$LIB_DIR" "$EXAMPLE_DIR" "$RUST_EXAMPLE_DIR" "$JAVA_EXAMPLE_DIR" "$GO_EXAMPLE_DIR" "$NODE_EXAMPLE_DIR"

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

if command -v cargo >/dev/null 2>&1; then
  CARGO_TARGET_DIR="$RUST_EXAMPLE_DIR/target" \
  RUSTFLAGS="-L native=$LIB_DIR -l dylib=vev -C link-arg=-Wl,-rpath,$LIB_DIR" \
    cargo run \
      --quiet \
      --manifest-path "$ROOT/examples/rust/Cargo.toml"
elif command -v rustc >/dev/null 2>&1; then
  rustc \
    "$ROOT/examples/rust/smoke.rs" \
    -L "$LIB_DIR" \
    -l dylib=vev \
    -C "link-arg=-Wl,-rpath,$LIB_DIR" \
    -o "$RUST_EXAMPLE_DIR/vev_rust_smoke"

  "$RUST_EXAMPLE_DIR/vev_rust_smoke"
else
  echo "cargo/rustc not found; skipping Rust smoke"
fi

if command -v go >/dev/null 2>&1; then
  (
    cd "$ROOT/examples/go"
    go build -o "$GO_EXAMPLE_DIR/vev_go_smoke" smoke.go
  )
  DYLD_LIBRARY_PATH="$LIB_DIR:${DYLD_LIBRARY_PATH:-}" \
    LD_LIBRARY_PATH="$LIB_DIR:${LD_LIBRARY_PATH:-}" \
    "$GO_EXAMPLE_DIR/vev_go_smoke"
else
  echo "go not found; skipping Go smoke"
fi

if command -v node >/dev/null 2>&1 && command -v clang++ >/dev/null 2>&1; then
  NODE_INCLUDE_DIR="${NODE_INCLUDE_DIR:-}"
  if [[ -z "$NODE_INCLUDE_DIR" ]]; then
    NODE_PREFIX="$(cd "$(dirname "$(command -v node)")/.." && pwd)"
    if [[ -f "$NODE_PREFIX/include/node/node_api.h" ]]; then
      NODE_INCLUDE_DIR="$NODE_PREFIX/include/node"
    fi
  fi

  if [[ -n "$NODE_INCLUDE_DIR" && -f "$NODE_INCLUDE_DIR/node_api.h" ]]; then
    if [[ "$(uname -s)" == "Darwin" ]]; then
      clang++ \
        -std=c++17 \
        -bundle \
        -undefined dynamic_lookup \
        -I"$ROOT/include" \
        -I"$NODE_INCLUDE_DIR" \
        "$ROOT/examples/node/vev_native.cc" \
        -L"$LIB_DIR" \
        -lvev \
        -Wl,-rpath,"$LIB_DIR" \
        -o "$NODE_EXAMPLE_DIR/vev_native.node"
    else
      clang++ \
        -std=c++17 \
        -shared \
        -fPIC \
        -I"$ROOT/include" \
        -I"$NODE_INCLUDE_DIR" \
        "$ROOT/examples/node/vev_native.cc" \
        -L"$LIB_DIR" \
        -lvev \
        -Wl,-rpath,"$LIB_DIR" \
        -o "$NODE_EXAMPLE_DIR/vev_native.node"
    fi

    VEV_NODE_NATIVE="$NODE_EXAMPLE_DIR/vev_native.node" \
      node "$ROOT/examples/node/smoke.js"
  else
    echo "node_api.h not found; skipping Node smoke"
  fi
else
  echo "node/clang++ not found; skipping Node smoke"
fi

if command -v javac >/dev/null 2>&1 && command -v java >/dev/null 2>&1; then
  rm -rf "$JAVA_EXAMPLE_DIR"
  mkdir -p "$JAVA_EXAMPLE_DIR"

  javac \
    --enable-preview \
    --release 21 \
    -d "$JAVA_EXAMPLE_DIR" \
    "$ROOT/examples/java/Vev.java" \
    "$ROOT/examples/java/Smoke.java"

  java \
    --enable-preview \
    --enable-native-access=ALL-UNNAMED \
    -cp "$JAVA_EXAMPLE_DIR" \
    vev.Smoke "$LIB_DIR/libvev.dylib"

  if command -v clojure >/dev/null 2>&1; then
    clojure \
      -J--enable-preview \
      -J--enable-native-access=ALL-UNNAMED \
      -Sdeps "{:paths [\"$JAVA_EXAMPLE_DIR\" \"$ROOT/clients/clojure/src\"]}" \
      -M \
      "$ROOT/examples/clojure/smoke.clj" \
      "$LIB_DIR/libvev.dylib"
  else
    echo "clojure not found; skipping Clojure smoke"
  fi
else
  echo "javac/java not found; skipping Java smoke"
fi
