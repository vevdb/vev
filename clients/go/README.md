# Vev Go

This is a cgo package over Vev's C ABI. The API is still smoke-level, but it is
now importable at the planned module path and has a smoke command under
`cmd/vev-go-smoke`.

Current local development:

```sh
scripts/build_c_abi.sh
```

The script builds and runs `clients/go/cmd/vev-go-smoke` against the platform
library under `build/lib`.

Module path:

```text
github.com/vevdb/vev/clients/go
```

The first polished package should keep the same basic shape:

- `Conn`, `DB`, durable connection, prepared query, statement, and result types
- explicit `Close` methods for native handles
- EDN text APIs for parity with other hosts
- prepared queries and typed statement bindings for repeated work

`scripts/smoke_go_package.sh` verifies that this package can be imported from a
separate Go module using a local `replace` to this checkout.

`cmd/vev` is the first user-facing CLI. It is intentionally thin and uses the
same package API as the Go smoke command.
