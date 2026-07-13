#!/usr/bin/env bash
# Copyright (c) Andreas Flakstad and Vev contributors
# SPDX-License-Identifier: EPL-2.0

vev_repo_root() {
  cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd
}

vev_version() {
  local root="${1:-$(vev_repo_root)}"
  local version

  version="$(tr -d '[:space:]' < "$root/VERSION")"
  if [[ ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][0-9A-Za-z.-]+)?$ ]]; then
    echo "invalid Vev version in $root/VERSION: $version" >&2
    return 1
  fi
  printf '%s\n' "$version"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  vev_version
fi
