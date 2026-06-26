package vev;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Smoke {
    private static void deleteSqliteFiles(Path path) throws Exception {
        Files.deleteIfExists(path);
        Files.deleteIfExists(Path.of(path.toString() + "-wal"));
        Files.deleteIfExists(Path.of(path.toString() + "-shm"));
    }

    public static void main(String[] args) throws Throwable {
        if (args.length != 1) {
            throw new IllegalArgumentException("usage: Smoke <path-to-libvev.dylib>");
        }

        Vev vev = Vev.load(Path.of(args[0]));
        try (Vev.Connection conn = vev.createConn()) {
            String tx = conn.transact("""
                [{:db/id 1 :user/name "Ada" :user/email "ada@example.com"}
                 {:db/id 2 :user/name "Grace" :user/email "grace@example.com"}]
                """);
            System.out.println("tx: " + tx);

            String collectionText = conn.queryText("""
                [:find ?name
                 :in [?email ...]
                 :where [?e :user/email ?email]
                        [?e :user/name ?name]]
                """, "[[\"ada@example.com\" \"grace@example.com\"]]");
            System.out.println("input-collection: " + collectionText);
            if (!collectionText.contains("\"Ada\"") || !collectionText.contains("\"Grace\"")) {
                throw new IllegalStateException("unexpected collection query output");
            }

            conn.transact("""
                [[:db/add 90 :db/ident :user/email]
                 [:db/add 90 :db/unique :db.unique/identity]]
                """);

            conn.transact("""
                [[:db/add 91 :db/ident :user/status]
                 [:db/add 91 :db/unique :db.unique/identity]
                 [:db/add 92 :db/ident :user/code]
                 [:db/add 92 :db/unique :db.unique/identity]
                 [:db/add 1 :user/status :active]
                 [:db/add 2 :user/status :inactive]
                 [:db/add 1 :user/code 1001]
                 [:db/add 2 :user/code 1002]]
                """);

            conn.transact("""
                [[:db/add 100 :db/ident :user/friend]
                 [:db/add 100 :db/valueType :db.type/ref]
                 [:db/add 1 :user/friend 2]]
                """);

            try (Vev.PreparedQuery emailQuery = vev.prepare("""
                    [:find ?e ?email
                     :in ?needle
                     :where [?e :user/email ?email]
                            [(= ?email ?needle)]]
                    """);
                 Vev.Statement stmt = emailQuery.statement()) {
                try (Vev.ResultSet result = conn.query(stmt.bindString("grace@example.com"))) {
                    List<List<Object>> rows = result.rows();
                    System.out.println("statement rows: " + rows);
                    if (!rows.equals(List.of(List.of(new Vev.Entity(2), "grace@example.com")))) {
                        throw new IllegalStateException("unexpected statement rows");
                    }
                }

                try (Vev.PreparedQuery collectionQuery = vev.prepare("""
                        [:find ?name
                         :in [?email ...]
                         :where [?e :user/email ?email]
                                [?e :user/name ?name]]
                        """);
                     Vev.Statement collectionStmt = collectionQuery.statement()) {
                    try (Vev.ResultSet result = conn
                            .query(collectionStmt.bindStringCollection("ada@example.com", "grace@example.com"))) {
                        List<List<Object>> collectionRows = result.rows();
                        List<String> names = new ArrayList<>();
                        for (List<Object> row : collectionRows) {
                            names.add((String) row.get(0));
                        }
                        Collections.sort(names);
                        System.out.println("statement collection names: " + names);
                        if (!names.equals(List.of("Ada", "Grace"))) {
                            throw new IllegalStateException("unexpected collection statement rows");
                        }
                    }
                }

                try (Vev.PreparedQuery pullQuery = vev.prepare("""
                        [:find (pull ?e [:user/name {:user/friend [:user/name]}])
                         :where [?e :user/name "Ada"]]
                        """)) {
                    try (Vev.ResultSet result = conn.query(pullQuery, "[]")) {
                        Vev.MapValue pulled = (Vev.MapValue) result.scalar();
                        System.out.println("pull: " + pulled);
                        Object friend = pulled.get(":user/friend");
                        if (!"Ada".equals(pulled.get(":user/name"))
                            || !(friend instanceof Vev.MapValue friendMap)
                            || !"Grace".equals(friendMap.get(":user/name"))) {
                            throw new IllegalStateException("unexpected pull result");
                        }
                    }
                }

                try (Vev.DB pullDb = conn.db()) {
                    Vev.MapValue directPull = (Vev.MapValue) pullDb.pull(
                        "[:user/name {:user/friend [:user/name]}]",
                        1);
                    System.out.println("direct pull: " + directPull);
                    Object friend = directPull.get(":user/friend");
                    if (!"Ada".equals(directPull.get(":user/name"))
                        || !(friend instanceof Vev.MapValue friendMap)
                        || !"Grace".equals(friendMap.get(":user/name"))) {
                        throw new IllegalStateException("unexpected direct pull");
                    }

                    Vev.MapValue lookupPull = (Vev.MapValue) pullDb.pullLookupRefString(
                        "[:user/name]",
                        ":user/email",
                        "ada@example.com");
                    System.out.println("lookup pull: " + lookupPull);
                    if (!"Ada".equals(lookupPull.get(":user/name"))) {
                        throw new IllegalStateException("unexpected lookup-ref pull");
                    }

                    Vev.MapValue keywordLookupPull = (Vev.MapValue) pullDb.pullLookupRefKeyword(
                        "[:user/name]",
                        ":user/status",
                        ":active");
                    Vev.MapValue intLookupPull = (Vev.MapValue) pullDb.pullLookupRefInt(
                        "[:user/name]",
                        ":user/code",
                        1001);
                    if (!"Ada".equals(keywordLookupPull.get(":user/name"))
                        || !"Ada".equals(intLookupPull.get(":user/name"))) {
                        throw new IllegalStateException("unexpected typed lookup-ref pull");
                    }

                    @SuppressWarnings("unchecked")
                    List<Object> manyPull = (List<Object>) pullDb.pullMany("[:user/name]", 1, 2);
                    List<String> pullNames = new ArrayList<>();
                    for (Object item : manyPull) {
                        pullNames.add((String) ((Vev.MapValue) item).get(":user/name"));
                    }
                    Collections.sort(pullNames);
                    System.out.println("pull many: " + manyPull);
                    if (!pullNames.equals(List.of("Ada", "Grace"))) {
                        throw new IllegalStateException("unexpected pull-many");
                    }
                }

                try (Vev.PreparedQuery pullPatternQuery = vev.prepare("""
                        [:find (pull ?e ?pattern)
                         :in ?pattern ?name
                         :where [?e :user/name ?name]]
                        """);
                     Vev.Statement pullPatternStmt = pullPatternQuery.statement();
                     Vev.ResultSet result = conn.query(
                         pullPatternStmt.bindPullPatternAndString(
                             "[:user/name {:user/friend [:user/name]}]",
                             "Ada"))) {
                    Vev.MapValue pulled = (Vev.MapValue) result.scalar();
                    System.out.println("statement pull pattern: " + pulled);
                    Object friend = pulled.get(":user/friend");
                    if (!"Ada".equals(pulled.get(":user/name"))
                        || !(friend instanceof Vev.MapValue friendMap)
                        || !"Grace".equals(friendMap.get(":user/name"))) {
                        throw new IllegalStateException("unexpected statement pull pattern");
                    }
                }

                try (Vev.PreparedQuery allEmails = vev.prepare("[:find ?e ?email :where [?e :user/email ?email]]");
                     Vev.DB snapshot = conn.db()) {
                    conn.transact("[{:db/id 3 :user/name \"Alan\" :user/email \"alan@example.com\"}]");
                    try (Vev.ResultSet current = conn.query(allEmails, "[]");
                         Vev.ResultSet old = snapshot.query(allEmails, "[]")) {
                        int currentRows = current.rowCount();
                        int snapshotRows = old.rowCount();
                        System.out.println("current-db rows: " + currentRows);
                        System.out.println("snapshot-db rows: " + snapshotRows);
                        if (currentRows != 3 || snapshotRows != 2) {
                            throw new IllegalStateException("unexpected snapshot row counts");
                        }
                    }

                    try (Vev.ResultSet result = snapshot.query(stmt.bindString("ada@example.com"))) {
                        List<List<Object>> snapshotStmtRows = result.rows();
                        if (!snapshotStmtRows.equals(List.of(List.of(new Vev.Entity(1), "ada@example.com")))) {
                            throw new IllegalStateException("unexpected snapshot statement rows");
                        }
                    }
                }
            }

            Path sqlitePath = Path.of("tmp.vev.java.sqlite");
            deleteSqliteFiles(sqlitePath);
            try {
                try (Vev.DurableConnection durable = vev.connect(sqlitePath)) {
                    if (!"sqlite".equals(durable.backend()) || !sqlitePath.toString().equals(durable.path())) {
                        throw new IllegalStateException("unexpected durable connection metadata");
                    }
                    try (Vev.TxReport report = durable.transactReport("""
                            [{:db/id 1
                              :user/name "Durable Ada"
                              :user/email "durable-ada@example.com"}]
                            """)) {
                        Vev.MapValue reportValue = (Vev.MapValue) report.value();
                        if (!Boolean.TRUE.equals(reportValue.get(":ok"))) {
                            throw new IllegalStateException("unexpected SQLite transaction report");
                        }
                    }
                    try (Vev.PreparedQuery durableQuery = vev.prepare("[:find ?e ?email :where [?e :user/email ?email]]");
                         Vev.DB durableDb = durable.db();
                         Vev.ResultSet rows = durableDb.query(durableQuery, "[]")) {
                        System.out.println("sqlite-live rows: " + rows.rowCount());
                        if (rows.rowCount() != 1) {
                            throw new IllegalStateException("unexpected SQLite live row count");
                        }
                    }
                }

                try (Vev.DurableConnection durable = vev.connect(sqlitePath);
                     Vev.PreparedQuery durableQuery = vev.prepare("[:find ?e ?email :where [?e :user/email ?email]]");
                     Vev.DB durableDb = durable.db();
                     Vev.ResultSet rows = durableDb.query(durableQuery, "[]")) {
                    System.out.println("sqlite-reopened rows: " + rows.rowCount());
                    if (rows.rowCount() != 1) {
                        throw new IllegalStateException("unexpected SQLite reopened row count");
                    }
                }
            } finally {
                deleteSqliteFiles(sqlitePath);
            }
        } finally {
            vev.close();
        }
    }
}
