#!/usr/bin/env bash
# Copyright (c) Andreas Flakstad and Vev contributors
# SPDX-License-Identifier: EPL-2.0

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION="${VEV_VERSION:-0.1.0-SNAPSHOT}"

case "$(uname -s)" in
  Darwin) LIB_NAME="libvev.dylib" ;;
  Linux) LIB_NAME="libvev.so" ;;
  MINGW*|MSYS*|CYGWIN*) LIB_NAME="vev.dll" ;;
  *) echo "unsupported OS: $(uname -s)" >&2; exit 1 ;;
esac

if [[ ! -f "$ROOT/build/lib/$LIB_NAME" ]]; then
  "$ROOT/scripts/build_c_abi.sh"
fi

"$ROOT/scripts/package_jvm.sh" >/dev/null

TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/vev-jvm-package.XXXXXX")"
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

cat > "$TMP_DIR/deps.edn" <<EOF
{:mvn/local-repo "$ROOT/build/m2"
 :deps {dev.vevdb/vev-clj {:mvn/version "$VERSION"}}
 :aliases {:run {:jvm-opts ["--enable-preview"
                            "--enable-native-access=ALL-UNNAMED"]}}}
EOF

(
  cd "$TMP_DIR"
  clojure -M:run -e "(require '[vev.core :as d])
(let [conn (d/create-conn)]
  (d/transact conn [{:db/id 1 :user/name \"Ada\"}])
  (assert (= #{[\"Ada\"]}
             (d/q '[:find ?name :where [?e :user/name ?name]]
                  (d/db conn))))
  (println :vev-jvm-package-ok))"
)
