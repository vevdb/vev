#!/usr/bin/env bash
# Copyright (c) Andreas Flakstad and Vev contributors
# SPDX-License-Identifier: EPL-2.0

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAVA_OUT="$ROOT/build/examples/java"
STORE="$ROOT/build/musicbrainz/vev-mbrainz-tutorial.sqlite"
DATOMIC_URI="${DATOMIC_URI:-datomic:dev://localhost:4334/mbrainz-1968-1973}"
ENGINE="all"
RUN_KVIST="true"

usage() {
  cat <<EOF
usage: scripts/compare_musicbrainz_workshop.sh [options]

Runs the exact upstream mbrainz-sample data-query workshop slice against Vev
Clojure, Vev Kvist, and Datomic when requested.

options:
  --engine all|vev|datomic  Clojure comparison engine; default: all
  --skip-datomic            run only Vev Clojure plus Kvist validation
  --skip-kvist              skip the Kvist workshop validation
  --datomic-uri uri         Datomic URI; default: $DATOMIC_URI
  -h, --help                show this help

Before running Datomic comparison, ensure the local sample is available:
  scripts/musicbrainz_sample.sh prepare
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --engine) ENGINE="$2"; shift 2 ;;
    --skip-datomic) ENGINE="vev"; shift ;;
    --skip-kvist) RUN_KVIST="false"; shift ;;
    --datomic-uri) DATOMIC_URI="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *)
      usage >&2
      exit 1
      ;;
  esac
done

if [[ ! -f "$STORE" && "$ENGINE" != "datomic" ]]; then
  echo "missing persistent MusicBrainz Vev store: $STORE" >&2
  echo "build it with bench/musicbrainz_import_subset.kvist and --sqlite-output" >&2
  exit 1
fi

if [[ ! -f "$JAVA_OUT/dev/vevdb/vev/Vev.class" || "$ROOT/clients/java/src/main/java/dev/vevdb/vev/Vev.java" -nt "$JAVA_OUT/dev/vevdb/vev/Vev.class" ]]; then
  javac \
    --enable-preview \
    --release 21 \
    -d "$JAVA_OUT" \
    "$ROOT/clients/java/src/main/java/dev/vevdb/vev/Vev.java"
fi

if [[ "$RUN_KVIST" == "true" && "$ENGINE" != "datomic" ]]; then
  "$ROOT/scripts/musicbrainz_workshop_kvist.sh"
fi

if [[ "$ENGINE" != "vev" ]]; then
  "$ROOT/scripts/musicbrainz_sample.sh" start
  trap "\"$ROOT/scripts/musicbrainz_sample.sh\" stop" EXIT
fi

clojure \
  -J--enable-preview \
  -J--enable-native-access=ALL-UNNAMED \
  -Sdeps "{:deps {com.datomic/peer {:mvn/version \"1.0.7277\"}}
           :paths [\"$JAVA_OUT\" \"$ROOT/clients/clojure/src\" \"$ROOT/examples/clojure\" \"$ROOT/scripts\"]}" \
  -M "$ROOT/scripts/compare_musicbrainz_workshop.clj" \
  --engine "$ENGINE" \
  --datomic-uri "$DATOMIC_URI"
