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

if [[ ! -f "$ROOT/build/lib/$LIB_NAME" ]]; then
  "$ROOT/scripts/build_c_abi.sh"
fi

TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/vev-rust-package.XXXXXX")"
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

cat > "$TMP_DIR/Cargo.toml" <<EOF
[package]
name = "vev-rust-package-smoke"
version = "0.1.0"
edition = "2021"
publish = false

[dependencies]
vev = { path = "$ROOT/clients/rust" }
EOF

mkdir -p "$TMP_DIR/src"
cat > "$TMP_DIR/src/main.rs" <<'EOF'
use vev::{Conn, DurableConn, Value};

fn remove_store(path: &str) {
    let _ = std::fs::remove_file(path);
    let _ = std::fs::remove_file(format!("{path}-wal"));
    let _ = std::fs::remove_file(format!("{path}-shm"));
}

fn main() -> Result<(), String> {
    let conn = Conn::open_memory()?;
    conn.transact(r#"[{:db/id 1 :user/name "Ada"}]"#);
    let db = conn.db()?;
    let rows = db.q("[:find ?name :where [?e :user/name ?name]]", "[]")?;
    if rows != vec![vec![Value::String("Ada".to_string())]] {
        return Err(format!("unexpected in-memory rows: {rows:?}"));
    }

    let path = "vev-rust-package.vev";
    remove_store(path);
    {
        let durable = DurableConn::open(path)?;
        durable.transact(r#"[{:db/id 1 :user/name "Durable Ada"}]"#)?;
    }
    {
        let durable = DurableConn::open(path)?;
        let rows = durable.q("[:find ?name :where [?e :user/name ?name]]", "[]")?;
        if rows != vec![vec![Value::String("Durable Ada".to_string())]] {
            remove_store(path);
            return Err(format!("unexpected durable rows: {rows:?}"));
        }
    }
    remove_store(path);
    println!(":vev-rust-package-ok");
    Ok(())
}
EOF

DYLD_LIBRARY_PATH="$ROOT/build/lib:${DYLD_LIBRARY_PATH:-}" \
LD_LIBRARY_PATH="$ROOT/build/lib:${LD_LIBRARY_PATH:-}" \
VEV_LIB_DIR="$ROOT/build/lib" \
  cargo run --quiet --manifest-path "$TMP_DIR/Cargo.toml"
