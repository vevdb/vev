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

The wrapper loads the native addon in this order:

- `VEV_NODE_NATIVE`
- `vev_native.node` next to `vev.js`
- `native/<platform>/vev_native.node` next to `vev.js`

Planned package name:

```text
@vevdb/vev
```

The package should continue to expose explicit native-addon loading for local
development while supporting bundled platform binaries. `scripts/build_c_abi.sh`
links the addon with both repo-local and addon-relative library rpaths, so a
future package can place `vev_native.node` and the platform `libvev` in the same
`native/<platform>` directory.

`scripts/smoke_node_package.sh` verifies that package-like layout from a
temporary directory without `VEV_NODE_NATIVE`.
