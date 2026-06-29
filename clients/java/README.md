# Vev Java

This package is the Java 21 Foreign Function & Memory wrapper for Vev's native
C ABI. It is the lower JVM layer used by the Clojure package.

Current local development:

```sh
scripts/build_c_abi.sh
```

That builds the platform native library under `build/lib`, compiles the Java
wrapper into `build/examples/java`, and runs the Java smoke.

The wrapper loads the native library in this order:

1. `-Dvev.library=/path/to/libvev.dylib`
2. `VEV_LIB=/path/to/libvev.dylib`
3. the platform library under `build/lib`
4. bundled classpath resource:
   `dev/vevdb/vev/native/<platform>/<mapped-library-name>`

Java FFM is still a preview API, so local runs need:

```sh
--enable-preview --enable-native-access=ALL-UNNAMED
```

Planned Maven coordinate:

```text
dev.vevdb:vev-java
```

The first published package should still support explicit native library paths.
Bundled platform native artifacts should be published as separate
`dev.vevdb:vev-native-<platform>` packages or merged into the runtime classpath
by the Clojure/Java distribution. The Java loader already supports classpath
resources such as:

```text
dev/vevdb/vev/native/darwin-aarch64/libvev.dylib
dev/vevdb/vev/native/darwin-x86_64/libvev.dylib
dev/vevdb/vev/native/linux-x86_64/libvev.so
```
