#!/usr/bin/env python3

import json
import pathlib
import re
import sys
import tomllib
import xml.etree.ElementTree as ET


ROOT = pathlib.Path(__file__).resolve().parent.parent


def fail(message: str) -> None:
    print(message, file=sys.stderr)
    raise SystemExit(1)


def read(path: str) -> str:
    return (ROOT / path).read_text()


version = read("VERSION").strip()
if not re.fullmatch(r"\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?", version):
    fail(f"invalid VERSION: {version!r}")

checks: list[tuple[str, str]] = []

pom = ET.parse(ROOT / "clients/java/pom.xml").getroot()
namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
checks.append(("clients/java/pom.xml", pom.findtext("m:version", namespaces=namespace) or ""))

node = json.loads(read("clients/node/package.json"))
checks.append(("clients/node/package.json", node.get("version", "")))

python_package = tomllib.loads(read("clients/python/pyproject.toml"))
checks.append(("clients/python/pyproject.toml", python_package["project"]["version"]))

rust_package = tomllib.loads(read("clients/rust/Cargo.toml"))
checks.append(("clients/rust/Cargo.toml", rust_package["package"]["version"]))

rust_lock = tomllib.loads(read("clients/rust/Cargo.lock"))
lock_versions = [
    package["version"]
    for package in rust_lock["package"]
    if package.get("name") == "vevdb"
]
if len(lock_versions) != 1:
    fail(f"expected one vevdb package in clients/rust/Cargo.lock, found {len(lock_versions)}")
checks.append(("clients/rust/Cargo.lock", lock_versions[0]))

for path, actual in checks:
    if actual != version:
        fail(f"{path} version {actual!r} does not match VERSION {version!r}")

expected_references = {
    "README.md": f'com.vevdb/vev-clj {{:mvn/version "{version}"}}',
    "docs/getting-started.md": f'com.vevdb/vev-clj {{:mvn/version "{version}"}}',
    "clients/clojure/README.md": f'com.vevdb/vev-clj {{:mvn/version "{version}"}}',
    "clients/clojure/deps.edn": f'com.vevdb/vev-java {{:mvn/version "{version}"}}',
}
for path, expected in expected_references.items():
    if expected not in read(path):
        fail(f"{path} does not contain the expected {version} coordinate")

print(version)
