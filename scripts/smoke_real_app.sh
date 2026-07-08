#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STORE="${TMPDIR:-/tmp}/vev-contact-book.vev"

python3 "$ROOT/examples/python/contact_book.py" "$STORE"
