# Vev Node/TypeScript

This is a Node N-API smoke client over Vev's C ABI. It contains a small native
addon plus a CommonJS wrapper and TypeScript declarations.

Current local development:

```sh
scripts/build_c_abi.sh
```

The build script compiles `clients/node/vev_native.cc` into
`build/examples/node/vev_native.node` and runs `clients/node/smoke.js` with
`VEV_NODE_NATIVE` pointing at that addon.

Planned package name:

```text
@vevdb/vev
```

The package should continue to expose explicit native-library/addon loading for
local development before adding bundled platform binaries.
