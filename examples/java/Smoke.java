package vev;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Smoke {
    public static void main(String[] args) throws Throwable {
        if (args.length != 1) {
            throw new IllegalArgumentException("usage: Smoke <path-to-libvev.dylib>");
        }

        Vev vev = new Vev(Path.of(args[0]));
        try (Vev.Connection conn = vev.openMemory()) {
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
        } finally {
            vev.close();
        }
    }
}
