# Vev Rust

This is currently a Rust smoke client over Vev's C ABI. It is intentionally kept
in `clients/rust` so it can grow into the Rust package without moving paths
again.

Current local development:

```sh
scripts/build_c_abi.sh
```

The build script links the smoke binary against the platform library under
`build/lib` and runs it.

Likely package split before publishing:

- `vev-sys`: raw generated or hand-maintained C ABI bindings over `include/vev.h`
- `vev`: safe RAII wrapper with `Connection`, immutable `DB` snapshots,
  prepared queries, statements, pull patterns, and typed result batches

The current smoke already follows that wrapper direction: native handles are
owned by Rust structs and released through `Drop`.
