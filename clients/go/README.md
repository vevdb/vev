# Vev Go

This is a cgo smoke client over Vev's C ABI. It currently lives as one file so
the ABI shape can keep moving without pretending the Go package is finished.

Current local development:

```sh
scripts/build_c_abi.sh
```

The script builds and runs `clients/go/smoke.go` against
`build/lib/libvev.dylib`.

Planned module path:

```text
github.com/vevdb/vev/clients/go
```

The first real package should keep the same basic shape:

- `Conn`, `DB`, durable connection, prepared query, statement, and result types
- explicit `Close` methods for native handles
- EDN text APIs for parity with other hosts
- prepared queries and typed statement bindings for repeated work
