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

The Java artifact is intended to be a one-dependency entry point for Java
applications. It depends on the matching platform native artifact, so Java
users should not also list a separate `vev-native-*` dependency in normal
project setup.

Basic usage:

```java
try (Vev vev = Vev.load();
     Vev.Connection conn = vev.createConn()) {
    conn.transact("[{:db/id 1 :user/name \"Ada\"}]");
    try (Vev.DB db = conn.db()) {
        System.out.println(vev.queryRows(Map.of(
            "query", "[:find ?name :where [?e :user/name ?name]]",
            "args", List.of(db))));
    }
}
```

Parser tooling can inspect a single where clause with
`vev.parseClauseEdn("[?e :user/name ?name]")`.

Transaction functions use a host registry plus a Datomic-style installed ident
in the DB:

```java
try (Vev.TxFunctionRegistry fns = vev.txFunctionRegistry()) {
    fns.register(":user/set-age", (db, args) ->
        "[[:db/add " + args.get(0) + " :user/age " + args.get(1) + "]]");
    conn.transact("[[:db/add 100 :db/ident :user/set-age]]");
    try (Vev.TxReport report =
             conn.transactReport("[[:user/set-age 1 42]]", fns)) {
        System.out.println(report.value());
    }
}
```

The Java callback returns EDN tx-data text. Higher-level clients such as
Clojure can wrap this and let callbacks return ordinary host data.

Successful transaction reports can be observed with a listener registration:

```java
try (Vev.TxReportListenerRegistration listener =
         conn.listen("audit", report -> System.out.println(report))) {
    conn.transact("[{:db/id 2 :user/name \"Grace\"}]");
}
```

The callback receives the decoded report value while the native report handle is
still valid. Keep data you need by copying it into ordinary Java structures
inside the callback.

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

It also writes a local Maven-style repository under `build/m2`. Those artifacts
are not published yet, but they verify the intended Maven split and
bundled-native loading path.

Run `scripts/smoke_jvm_package.sh` to test the local Maven repo from temporary
projects with only `dev.vevdb:vev-java` for the Java path and only
`dev.vevdb/vev-clj` for the Clojure path.

Durable stores are opened through Vev APIs with paths such as `app.vev`. The
current native library depends on the platform SQLite runtime. Java and Clojure
applications do not configure SQLite directly; they load Vev and call
`connect`.
