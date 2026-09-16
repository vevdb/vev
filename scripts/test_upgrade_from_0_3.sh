#!/usr/bin/env bash

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FROM_VERSION="0.3.0"
REPOSITORY="${VEV_UPGRADE_REPOSITORY:-vevdb/vev}"

case "$(uname -s)" in
  Darwin) OS="darwin"; FORMAT="tar.gz" ;;
  Linux) OS="linux"; FORMAT="tar.gz" ;;
  MINGW*|MSYS*|CYGWIN*) OS="windows"; FORMAT="zip" ;;
  *) echo "unsupported OS: $(uname -s)" >&2; exit 1 ;;
esac

case "$(uname -m)" in
  arm64|aarch64) ARCH="aarch64" ;;
  x86_64|amd64) ARCH="x86_64" ;;
  *) echo "unsupported architecture: $(uname -m)" >&2; exit 1 ;;
esac

if [[ ! -x "$ROOT/build/vevdb" && ! -x "$ROOT/build/vevdb.exe" ]]; then
  "$ROOT/scripts/build_cli.sh" >/dev/null
fi

TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/vevdb-upgrade-0.3.XXXXXX")"
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

platform="$OS-$ARCH"
archive="vevdb-cli-$platform-$FROM_VERSION.$FORMAT"
base_url="https://github.com/$REPOSITORY/releases/download/v$FROM_VERSION"

curl --fail --location --silent --show-error \
  "$base_url/$archive" \
  --output "$TMP_DIR/$archive"
curl --fail --location --silent --show-error \
  "$base_url/SHA256SUMS" \
  --output "$TMP_DIR/SHA256SUMS"

expected="$(awk -v archive="$archive" '$2 == archive {print $1}' "$TMP_DIR/SHA256SUMS")"
if [[ -z "$expected" ]]; then
  echo "missing checksum for $archive" >&2
  exit 1
fi
if command -v shasum >/dev/null 2>&1; then
  actual="$(shasum -a 256 "$TMP_DIR/$archive" | awk '{print $1}')"
else
  actual="$(sha256sum "$TMP_DIR/$archive" | awk '{print $1}')"
fi
if [[ "$actual" != "$expected" ]]; then
  echo "checksum mismatch for $archive" >&2
  exit 1
fi

if [[ "$FORMAT" == "zip" ]]; then
  unzip -q "$TMP_DIR/$archive" -d "$TMP_DIR/old"
  OLD_CLI="$TMP_DIR/old/vevdb-$FROM_VERSION/bin/vevdb.exe"
  CURRENT_CLI="$ROOT/build/vevdb.exe"
else
  mkdir -p "$TMP_DIR/old"
  tar -xzf "$TMP_DIR/$archive" -C "$TMP_DIR/old"
  OLD_CLI="$TMP_DIR/old/vevdb-$FROM_VERSION/bin/vevdb"
  CURRENT_CLI="$ROOT/build/vevdb"
fi

DB="$TMP_DIR/upgrade.db"
old_tx="$("$OLD_CLI" transact "$DB" '[{:db/id 1 :release/name "Aurora" :release/status :planned}]')"
case "$old_tx" in *":ok true"*) ;; *) echo "unexpected 0.3 transaction: $old_tx" >&2; exit 1 ;; esac

before="$("$CURRENT_CLI" query "$DB" '[:find ?name ?status :where [?e :release/name ?name] [?e :release/status ?status]]' --result q)"
case "$before" in *'"Aurora"'*':planned'*) ;; *) echo "unexpected upgraded read: $before" >&2; exit 1 ;; esac

current_tx="$("$CURRENT_CLI" transact "$DB" '[[:db/add 1 :release/status :ready]]')"
case "$current_tx" in *":ok true"*) ;; *) echo "unexpected current transaction: $current_tx" >&2; exit 1 ;; esac

current="$("$CURRENT_CLI" query "$DB" '[:find ?status . :where [1 :release/status ?status]]' --result scalar)"
if [[ "$current" != ":ready" ]]; then
  echo "unexpected current value after upgrade: $current" >&2
  exit 1
fi

history="$("$CURRENT_CLI" datoms "$DB" :eavt '[1 :release/status]' --db '[[:history]]')"
case "$history" in *':planned'*':ready'*) ;; *) echo "unexpected upgraded history: $history" >&2; exit 1 ;; esac

echo ":vevdb-upgrade-0.3-ok"
