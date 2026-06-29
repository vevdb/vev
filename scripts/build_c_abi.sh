#!/usr/bin/env bash
# Copyright (c) Andreas Flakstad and Vev contributors
# SPDX-License-Identifier: EPL-2.0

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KVIST_BIN="${KVIST_BIN:-kvist}"

GENERATED_DIR="$ROOT/build/generated/vev_abi"
LIB_DIR="$ROOT/build/lib"
PKGCONFIG_DIR="$LIB_DIR/pkgconfig"
EXAMPLE_DIR="$ROOT/build/examples/c"
RUST_EXAMPLE_DIR="$ROOT/build/examples/rust"
JAVA_EXAMPLE_DIR="$ROOT/build/examples/java"
GO_EXAMPLE_DIR="$ROOT/build/examples/go"
NODE_EXAMPLE_DIR="$ROOT/build/examples/node"
ODIN_EXAMPLE_DIR="$ROOT/build/examples/odin"

case "$(uname -s)" in
  Darwin) LIB_NAME="libvev.dylib" ;;
  Linux) LIB_NAME="libvev.so" ;;
  MINGW*|MSYS*|CYGWIN*) LIB_NAME="vev.dll" ;;
  *) echo "unsupported OS: $(uname -s)" >&2; exit 1 ;;
esac

LIB_PATH="$LIB_DIR/$LIB_NAME"

mkdir -p "$GENERATED_DIR" "$LIB_DIR" "$PKGCONFIG_DIR" "$EXAMPLE_DIR" "$RUST_EXAMPLE_DIR" "$JAVA_EXAMPLE_DIR" "$GO_EXAMPLE_DIR" "$NODE_EXAMPLE_DIR" "$ODIN_EXAMPLE_DIR"

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

clang \
  -I"$ROOT/include" \
  "$ROOT/clients/c/smoke.c" \
  -L"$LIB_DIR" \
  -lvev \
  -Wl,-rpath,"$LIB_DIR" \
  -o "$EXAMPLE_DIR/vev_c_smoke"

"$EXAMPLE_DIR/vev_c_smoke"

python3 "$ROOT/clients/python/smoke.py"

if command -v cargo >/dev/null 2>&1; then
  CARGO_TARGET_DIR="$RUST_EXAMPLE_DIR/target" \
  VEV_LIB_DIR="$LIB_DIR" \
    cargo run \
      --quiet \
      --manifest-path "$ROOT/clients/rust/Cargo.toml"
elif command -v rustc >/dev/null 2>&1; then
  rustc \
    "$ROOT/clients/rust/src/main.rs" \
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
    cd "$ROOT/clients/go"
    go build -o "$GO_EXAMPLE_DIR/vev_go_smoke" ./cmd/vev-go-smoke
    go build -o "$ROOT/build/vev" ./cmd/vev
  )
  DYLD_LIBRARY_PATH="$LIB_DIR:${DYLD_LIBRARY_PATH:-}" \
    LD_LIBRARY_PATH="$LIB_DIR:${LD_LIBRARY_PATH:-}" \
    "$GO_EXAMPLE_DIR/vev_go_smoke"
else
  echo "go not found; skipping Go smoke"
fi

if command -v odin >/dev/null 2>&1; then
  odin build "$ROOT/clients/odin" -file -out:"$ODIN_EXAMPLE_DIR/vev_odin_smoke"
  "$ODIN_EXAMPLE_DIR/vev_odin_smoke" "$LIB_PATH"
else
  echo "odin not found; skipping Odin smoke"
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
        "$ROOT/clients/node/vev_native.cc" \
        -L"$LIB_DIR" \
        -lvev \
        -Wl,-rpath,"$LIB_DIR" \
        -Wl,-rpath,@loader_path \
        -o "$NODE_EXAMPLE_DIR/vev_native.node"
    else
      clang++ \
        -std=c++17 \
        -shared \
        -fPIC \
        -I"$ROOT/include" \
        -I"$NODE_INCLUDE_DIR" \
        "$ROOT/clients/node/vev_native.cc" \
        -L"$LIB_DIR" \
        -lvev \
        -Wl,-rpath,"$LIB_DIR" \
        -Wl,-rpath,'$ORIGIN' \
        -o "$NODE_EXAMPLE_DIR/vev_native.node"
    fi

    VEV_NODE_NATIVE="$NODE_EXAMPLE_DIR/vev_native.node" \
      node "$ROOT/clients/node/smoke.js"
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
    "$ROOT/clients/java/src/main/java/dev/vevdb/vev/Vev.java" \
    "$ROOT/examples/java/Smoke.java"

  java \
    --enable-preview \
    --enable-native-access=ALL-UNNAMED \
    -cp "$JAVA_EXAMPLE_DIR" \
    dev.vevdb.vev.examples.Smoke "$LIB_PATH"

  if command -v clojure >/dev/null 2>&1; then
    clojure \
      -J--enable-preview \
      -J--enable-native-access=ALL-UNNAMED \
      -Sdeps "{:paths [\"$JAVA_EXAMPLE_DIR\" \"$ROOT/clients/clojure/src\"]}" \
      -M \
      "$ROOT/examples/clojure/smoke.clj" \
      "$LIB_PATH"
  else
    echo "clojure not found; skipping Clojure smoke"
  fi
else
  echo "javac/java not found; skipping Java smoke"
fi
