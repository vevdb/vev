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

Example:

```js
const vev = require("@vevdb/vev");

const conn = vev.createConn();
conn.transact('[{:db/id 1 :user/name "Ada"}]');
console.log(conn.queryText('[:find ?name :where [?e :user/name ?name]]'));
```

The package should continue to expose explicit native-addon loading for local
development while supporting bundled platform binaries. `scripts/build_c_abi.sh`
links the addon with both repo-local and addon-relative library rpaths, so a
future package can place `vev_native.node` and the platform `libvev` in the same
`native/<platform>` directory.

`scripts/smoke_node_package.sh` verifies that package-like layout from a
temporary directory without `VEV_NODE_NATIVE`.

Durable stores are opened through Vev APIs with paths such as `app.vev`. The
Node addon loads the Vev native library; the current native library depends on
the platform SQLite runtime, but Node application code does not set up SQLite.
