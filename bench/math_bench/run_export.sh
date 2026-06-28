#!/usr/bin/env bash
# Copyright (c) Andreas Flakstad and Vev contributors
# SPDX-License-Identifier: EPL-2.0

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
INPUT="${MATH_BENCH_JSON:-/Users/andreas/Projects/datalevin/benchmarks/math-bench/data.json.gz}"
OUTPUT_DIR="${MATH_BENCH_BUILD:-$ROOT/build/math_bench}"
CHUNK_SIZE="${MATH_BENCH_CHUNK_SIZE:-50000}"

mkdir -p "$OUTPUT_DIR"

clojure \
  -Sdeps "{:paths [\"$ROOT/bench/math_bench/scripts\"] :deps {metosin/jsonista {:mvn/version \"0.3.13\"}}}" \
  -M \
  -m vev-math-bench.export-data \
  --input "$INPUT" \
  --output-dir "$OUTPUT_DIR" \
  --chunk-size "$CHUNK_SIZE"
