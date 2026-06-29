# Vev Java

This package is the Java 21 Foreign Function & Memory wrapper for Vev's native
C ABI. It is the lower JVM layer used by the Clojure package.

Current local development:

```sh
scripts/build_c_abi.sh
scripts/stage_jvm_native.sh
scripts/package_jvm.sh
```

That builds the platform native library under `build/lib`, compiles the Java
wrapper into `build/examples/java`, runs the Java smoke, and stages the native
library under `build/jvm-native` using the resource layout below.

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

`scripts/stage_jvm_native.sh` creates that resource tree for the current
platform. A published native artifact can package the staged files as jar
resources.

`scripts/package_jvm.sh` builds local proof jars under `build/jvm`:

```text
vev-java-0.1.0-SNAPSHOT.jar
vev-native-<platform>-0.1.0-SNAPSHOT.jar
vev-clj-0.1.0-SNAPSHOT.jar
```

Those jars are not published artifacts yet, but they verify the intended Maven
split and bundled-native loading path.
