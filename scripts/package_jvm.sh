#!/usr/bin/env bash
# Copyright (c) Andreas Flakstad and Vev contributors
# SPDX-License-Identifier: EPL-2.0

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION="${VEV_VERSION:-0.1.0-SNAPSHOT}"
OUT_DIR="$ROOT/build/jvm"
JAVA_CLASSES="$OUT_DIR/classes/java"
CLOJURE_CLASSES="$OUT_DIR/classes/clojure"
NATIVE_CLASSES="$OUT_DIR/classes/native"

case "$(uname -s)" in
  Darwin) OS="darwin" ;;
  Linux) OS="linux" ;;
  MINGW*|MSYS*|CYGWIN*) OS="windows" ;;
  *) echo "unsupported OS: $(uname -s)" >&2; exit 1 ;;
esac

case "$(uname -m)" in
  arm64|aarch64) ARCH="aarch64" ;;
  x86_64|amd64) ARCH="x86_64" ;;
  *) echo "unsupported architecture: $(uname -m)" >&2; exit 1 ;;
esac

PLATFORM="$OS-$ARCH"

rm -rf "$OUT_DIR"
mkdir -p "$JAVA_CLASSES" "$CLOJURE_CLASSES" "$NATIVE_CLASSES"

javac \
  --enable-preview \
  --release 21 \
  -d "$JAVA_CLASSES" \
  "$ROOT/clients/java/src/main/java/dev/vevdb/vev/Vev.java"

jar --create \
  --file "$OUT_DIR/vev-java-$VERSION.jar" \
  -C "$JAVA_CLASSES" .

VEV_JVM_NATIVE_DIR="$NATIVE_CLASSES" "$ROOT/scripts/stage_jvm_native.sh" >/dev/null

jar --create \
  --file "$OUT_DIR/vev-native-$PLATFORM-$VERSION.jar" \
  -C "$NATIVE_CLASSES" .

cp -R "$ROOT/clients/clojure/src/." "$CLOJURE_CLASSES/"

jar --create \
  --file "$OUT_DIR/vev-clj-$VERSION.jar" \
  -C "$CLOJURE_CLASSES" .

printf '%s\n' \
  "$OUT_DIR/vev-java-$VERSION.jar" \
  "$OUT_DIR/vev-native-$PLATFORM-$VERSION.jar" \
  "$OUT_DIR/vev-clj-$VERSION.jar"
