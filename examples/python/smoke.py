#!/usr/bin/env python3
import sys

import vev


def main() -> int:
    with vev.open_memory() as conn:
        with conn.transact_report(
            """
            [{:db/id 1 :user/name "Ada" :user/email "ada@example.com"}
             {:db/id 2 :user/name "Grace" :user/email "grace@example.com"}]
            """
        ) as tx:
            tx_value = tx.value()
            print(f"tx: {tx.edn()}")
            if not tx_value.get(":ok") or len(tx_value.get(":tx-data", [])) != 4:
                raise RuntimeError("unexpected typed transaction report")

        conn.transact(
            """
            [[:db/add 90 :db/ident :user/email]
             [:db/add 90 :db/unique :db.unique/identity]]
            """
        )

        collection = conn.query_text(
            """
            [:find ?name
             :in [?email ...]
             :where [?e :user/email ?email]
                    [?e :user/name ?name]]
            """,
            '[["ada@example.com" "grace@example.com"]]',
        )
        print(f"input-collection: {collection}")
        if '"Ada"' not in collection or '"Grace"' not in collection:
            raise RuntimeError("unexpected collection query output")

        conn.transact(
            """
            [[:db/add 100 :db/ident :user/friend]
             [:db/add 100 :db/valueType :db.type/ref]
             [:db/add 1 :user/friend 2]]
            """
        )

        email_query = conn.prepare(
            """
            [:find ?e ?email
             :in ?needle
             :where [?e :user/email ?email]
                    [(= ?email ?needle)]]
            """
        )
        try:
            with email_query.statement() as stmt:
                rows = stmt.bind("grace@example.com").rows(conn)
                print(f"statement rows: {rows}")
                if rows != [[vev.Entity(2), "grace@example.com"]]:
                    raise RuntimeError("unexpected statement rows")

                rows = stmt.bind("ada@example.com").rows(conn)
                print(f"statement rebound rows: {rows}")
                if rows != [[vev.Entity(1), "ada@example.com"]]:
                    raise RuntimeError("unexpected rebound statement rows")

            collection_query = conn.prepare(
                """
                [:find ?name
                 :in [?email ...]
                 :where [?e :user/email ?email]
                        [?e :user/name ?name]]
                """
            )
            try:
                with collection_query.statement() as stmt:
                    rows = stmt.bind(["ada@example.com", "grace@example.com"]).rows(conn)
                    names = sorted(row[0] for row in rows)
                    print(f"statement collection names: {names}")
                    if names != ["Ada", "Grace"]:
                        raise RuntimeError("unexpected collection statement rows")
            finally:
                collection_query.close()

            tuple_query = conn.prepare(
                """
                [:find ?e
                 :in [?name ?email]
                 :where [?e :user/name ?name]
                        [?e :user/email ?email]]
                """
            )
            try:
                with tuple_query.statement() as stmt:
                    rows = stmt.bind(vev.TupleInput(("Ada", "ada@example.com"))).rows(conn)
                    print(f"tuple statement rows: {rows}")
                    if rows != [[vev.Entity(1)]]:
                        raise RuntimeError("unexpected tuple statement rows")
            finally:
                tuple_query.close()

            relation_query = conn.prepare(
                """
                [:find ?name ?label
                 :in [[?email ?label]]
                 :where [?e :user/email ?email]
                        [?e :user/name ?name]]
                """
            )
            try:
                with relation_query.statement() as stmt:
                    rows = stmt.bind(
                        vev.Relation(
                            (
                                ("ada@example.com", "primary"),
                                ("missing@example.com", "missing"),
                            )
                        )
                    ).rows(conn)
                    print(f"relation statement rows: {rows}")
                    if rows != [["Ada", "primary"]]:
                        raise RuntimeError("unexpected relation statement rows")
            finally:
                relation_query.close()

            lookup_query = conn.prepare(
                """
                [:find ?name
                 :in ?person
                 :where [?person :user/name ?name]]
                """
            )
            try:
                with lookup_query.statement() as stmt:
                    rows = stmt.bind(vev.LookupRef(":user/email", "ada@example.com")).rows(conn)
                    print(f"lookup-ref statement rows: {rows}")
                    if rows != [["Ada"]]:
                        raise RuntimeError("unexpected lookup-ref statement rows")
            finally:
                lookup_query.close()

            lookup_collection_query = conn.prepare(
                """
                [:find ?name
                 :in [?person ...]
                 :where [?person :user/name ?name]]
                """
            )
            try:
                with lookup_collection_query.statement() as stmt:
                    rows = stmt.bind(
                        [
                            vev.LookupRef(":user/email", "ada@example.com"),
                            vev.LookupRef(":user/email", "grace@example.com"),
                        ]
                    ).rows(conn)
                    names = sorted(row[0] for row in rows)
                    print(f"lookup-ref collection statement names: {names}")
                    if names != ["Ada", "Grace"]:
                        raise RuntimeError("unexpected lookup-ref collection statement rows")
            finally:
                lookup_collection_query.close()

            pull_query = conn.prepare(
                """
                [:find (pull ?e [:user/name {:user/friend [:user/name]}])
                 :where [?e :user/name "Ada"]]
                """
            )
            try:
                pulled = pull_query.scalar(conn)
                print(f"pull: {pulled}")
                if (
                    pulled.get(":user/name") != "Ada"
                    or pulled.get(":user/friend", {}).get(":user/name") != "Grace"
                ):
                    raise RuntimeError("unexpected pull result")
            finally:
                pull_query.close()

            with conn.db() as pull_db:
                direct_pull = pull_db.pull(
                    "[:user/name {:user/friend [:user/name]}]", vev.Entity(1)
                )
                print(f"direct pull: {direct_pull}")
                if (
                    direct_pull.get(":user/name") != "Ada"
                    or direct_pull.get(":user/friend", {}).get(":user/name") != "Grace"
                ):
                    raise RuntimeError("unexpected direct pull")

                lookup_pull = pull_db.pull_lookup_ref(
                    "[:user/name]",
                    vev.LookupRef(":user/email", "ada@example.com"),
                )
                print(f"lookup pull: {lookup_pull}")
                if lookup_pull.get(":user/name") != "Ada":
                    raise RuntimeError("unexpected lookup-ref pull")

                many_pull = pull_db.pull_many("[:user/name]", [vev.Entity(1), vev.Entity(2)])
                print(f"pull many: {many_pull}")
                names = sorted(item.get(":user/name") for item in many_pull)
                if names != ["Ada", "Grace"]:
                    raise RuntimeError("unexpected pull-many")

            all_emails = conn.prepare(
                "[:find ?e ?email :where [?e :user/email ?email]]"
            )
            try:
                with conn.db() as snapshot:
                    conn.transact(
                        '[{:db/id 3 :user/name "Alan" :user/email "alan@example.com"}]'
                    )

                    current_rows = len(all_emails.rows(conn))
                    snapshot_rows = len(all_emails.rows(snapshot))
                    print(f"current-db rows: {current_rows}")
                    print(f"snapshot-db rows: {snapshot_rows}")
                    if current_rows != 3 or snapshot_rows != 2:
                        raise RuntimeError("unexpected snapshot row counts")

                    with conn.prepare(
                        '[:find ?e :where [?e :user/name "Barbara"]]'
                    ) as barbara_query, conn.prepare(
                        '[:find ?e :where [?e :user/name "Dorothy"]]'
                    ) as dorothy_query:
                        with snapshot.with_report(
                            '[{:db/id 4 :user/name "Barbara"}]'
                        ) as report:
                            report_value = report.value()
                            print(f"with-db: {report.edn()}")
                            if not report_value.get(":ok"):
                                raise RuntimeError("unexpected with report")
                        with snapshot.db_with(
                            '[{:db/id 4 :user/name "Barbara"}]'
                        ) as next_db:
                            if len(barbara_query.rows(snapshot)) != 0:
                                raise RuntimeError("db-with mutated source DB")
                            if len(barbara_query.rows(next_db)) != 1:
                                raise RuntimeError("db-with missing new fact")
                            with vev.Connection.from_db(next_db) as derived:
                                derived.transact(
                                    '[{:db/id 5 :user/name "Dorothy"}]'
                                )
                                if (
                                    len(barbara_query.rows(derived)) != 1
                                    or len(dorothy_query.rows(derived)) != 1
                                ):
                                    raise RuntimeError(
                                        "conn-from-db did not initialize from DB"
                                    )
            finally:
                all_emails.close()
        finally:
            email_query.close()

    return 0


if __name__ == "__main__":
    sys.exit(main())
