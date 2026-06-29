# Vev Java

This package is the Java 21 Foreign Function & Memory wrapper for Vev's native
C ABI. It is the lower JVM layer used by the Clojure package.

Current local development:

```sh
scripts/build_c_abi.sh
```

That builds `build/lib/libvev.dylib`, compiles the Java wrapper into
`build/examples/java`, and runs the Java smoke.

The wrapper loads the native library in this order:

1. `-Dvev.library=/path/to/libvev.dylib`
2. `VEV_LIB=/path/to/libvev.dylib`
3. `build/lib/libvev.dylib`

Java FFM is still a preview API, so local runs need:

```sh
--enable-preview --enable-native-access=ALL-UNNAMED
```

Planned Maven coordinate:

```text
dev.vevdb:vev-java
```

The first published package should still support explicit native library paths.
Bundled platform native artifacts can come later as separate
`dev.vevdb:vev-native-<platform>` packages.
