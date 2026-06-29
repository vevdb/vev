# Vev Rust

This is currently a Rust smoke client over Vev's C ABI. The local Cargo package
is named `vev` with `publish = false`, so it can grow into the Rust crate
without moving paths again.

Current local development:

```sh
scripts/build_c_abi.sh
```

The Rust crate has a `build.rs` that links against `VEV_LIB_DIR` or, by
default, the platform library under the repo's `build/lib`. The top-level build
script sets `VEV_LIB_DIR` explicitly and runs the smoke binary.

Likely package split before publishing:

- `vev-sys`: raw generated or hand-maintained C ABI bindings over `include/vev.h`
- `vev`: safe RAII wrapper with `Connection`, immutable `DB` snapshots,
  prepared queries, statements, pull patterns, and typed result batches

The current smoke already follows that wrapper direction: native handles are
owned by Rust structs and released through `Drop`.

Parser tooling can inspect a single where clause through the wrapper's
`parse_clause_edn` helper.

The current binary target is still `vev-rust-smoke`; the package identity is
the future public crate identity.

Durable stores are opened through Vev APIs with paths such as `app.vev`. The
Rust wrapper links to `libvev`; the current native library depends on the
platform SQLite runtime, but Rust application code does not set up SQLite.
