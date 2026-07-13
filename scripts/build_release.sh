#!/usr/bin/env bash
# Copyright (c) Andreas Flakstad and Vev contributors
# SPDX-License-Identifier: EPL-2.0

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

"$ROOT/scripts/build_native_library.sh"
"$ROOT/scripts/package_jvm.sh" >/dev/null
"$ROOT/scripts/release_manifest.sh"
