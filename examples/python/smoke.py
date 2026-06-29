#!/usr/bin/env python3
# Copyright (c) Andreas Flakstad and Vev contributors
# SPDX-License-Identifier: EPL-2.0

import pathlib
import sys

import vev


def remove_sqlite_files(path: pathlib.Path) -> None:
    for suffix in ("", "-wal", "-shm"):
        try:
            pathlib.Path(f"{path}{suffix}").unlink()
        except FileNotFoundError:
            pass


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

        try:
            conn.prepare("[:find ?e :where [?e")
            raise RuntimeError("invalid query unexpectedly prepared")
        except vev.VevError as error:
            if not str(error):
                raise RuntimeError("invalid query did not expose an error") from error

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

                streamed = stmt.stream_rows(conn)
                print(f"streamed statement rows: {streamed}")
                if streamed != rows:
                    raise RuntimeError("unexpected streamed statement rows")

                try:
                    stmt.visit(conn, lambda event, row, index, value: False)
                    raise RuntimeError("cancelled visitor unexpectedly succeeded")
                except vev.VevError as error:
                    if "cancelled" not in str(error):
                        raise RuntimeError("unexpected visitor cancellation error") from error

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
                with collection_query.statement() as stmt:
                    rows = stmt.bind(vev.TypedCollection("string")).rows(conn)
                    if rows != []:
                        raise RuntimeError("unexpected empty typed collection rows")
            finally:
                collection_query.close()

            all_emails_query = conn.prepare(
                """
                [:find ?email
                 :where [?e :user/email ?email]]
                """
            )
            try:
                with conn.db() as db:
                    columns = db.query_columns(all_emails_query)
                    if columns is None:
                        raise RuntimeError("expected string column batch")
                    emails = sorted(row[0] for row in columns.rows())
                    print(f"column batch emails: {emails}")
                    if columns.kinds != (vev.VEV_COLUMN_STRING,):
                        raise RuntimeError("unexpected column batch kind")
                    if emails != ["ada@example.com", "grace@example.com"]:
                        raise RuntimeError("unexpected column batch rows")

                with all_emails_query.statement() as stmt:
                    columns = stmt.columns(conn)
                    if columns is None:
                        raise RuntimeError("expected live statement column batch")
                    emails = sorted(row[0] for row in columns.rows())
                    if columns.kinds != (vev.VEV_COLUMN_STRING,):
                        raise RuntimeError("unexpected live statement column batch kind")
                    if emails != ["ada@example.com", "grace@example.com"]:
                        raise RuntimeError("unexpected live statement column batch rows")

                    with conn.db() as db:
                        columns = stmt.columns(db)
                        if columns is None:
                            raise RuntimeError("expected snapshot statement column batch")
                        emails = sorted(row[0] for row in columns.rows())
                        if columns.kinds != (vev.VEV_COLUMN_STRING,):
                            raise RuntimeError(
                                "unexpected snapshot statement column batch kind"
                            )
                        if emails != ["ada@example.com", "grace@example.com"]:
                            raise RuntimeError(
                                "unexpected snapshot statement column batch rows"
                            )
            finally:
                all_emails_query.close()

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

            conn.transact(
                """
                [[:db/add 91 :db/ident :user/code]
                 [:db/add 91 :db/unique :db.unique/identity]
                 [:db/add 1 :user/code 101]
                 [:db/add 2 :user/code 202]]
                """
            )

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
                with lookup_collection_query.statement() as stmt:
                    rows = stmt.bind(
                        [
                            vev.LookupRef(":user/code", 101),
                            vev.LookupRef(":user/code", 202),
                        ]
                    ).rows(conn)
                    names = sorted(row[0] for row in rows)
                    if names != ["Ada", "Grace"]:
                        raise RuntimeError("unexpected int lookup-ref collection rows")
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

            pull_pattern_query = conn.prepare(
                """
                [:find (pull ?e ?pattern)
                 :in ?pattern ?name
                 :where [?e :user/name ?name]]
                """
            )
            try:
                with pull_pattern_query.statement() as stmt:
                    pulled = stmt.bind(
                        vev.PullPattern("[:user/name {:user/friend [:user/name]}]"),
                        "Ada",
                    ).scalar(conn)
                    print(f"statement pull pattern: {pulled}")
                    if (
                        pulled.get(":user/name") != "Ada"
                        or pulled.get(":user/friend", {}).get(":user/name") != "Grace"
                    ):
                        raise RuntimeError("unexpected statement pull pattern")
            finally:
                pull_pattern_query.close()

            with vev.open_memory() as right_conn:
                right_conn.transact(
                    """
                    [{:db/id 1 :user/name "Ada Right"}
                     {:db/id 2 :user/name "Grace Right"}]
                    """
                )
                with conn.db() as left_db, right_conn.db() as right_db:
                    source_query = conn.prepare(
                        """
                        [:find ?e ?left-name ?right-name
                         :in $left $right [?e ...]
                         :where [$left ?e :user/name ?left-name]
                                [$right ?e :user/name ?right-name]]
                        """,
                        ["$left", "$right"],
                    )
                    try:
                        with source_query.statement() as stmt:
                            rows = stmt.bind(
                                vev.DBSource("$left", left_db),
                                vev.DBSource("$right", right_db),
                                [vev.Entity(1), vev.Entity(2)],
                            ).rows(conn)
                            print(f"statement DB sources: {rows}")
                            first = rows[0]
                            if (
                                len(rows) != 2
                                or first[1] != "Ada"
                                or first[2] != "Ada Right"
                            ):
                                raise RuntimeError("unexpected statement DB sources")
                    finally:
                        source_query.close()

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

    sqlite_path = pathlib.Path("tmp.vev.python.sqlite")
    remove_sqlite_files(sqlite_path)
    try:
        with vev.connect(sqlite_path) as durable:
            if durable.backend() != "sqlite" or durable.path() != str(sqlite_path):
                raise RuntimeError("unexpected durable connection metadata")
            if durable.basis_t() != 0:
                raise RuntimeError("unexpected initial durable basis")
            if durable.tx_count() != 0:
                raise RuntimeError("unexpected initial durable tx count")
            if durable.tx_ids() != []:
                raise RuntimeError("unexpected initial durable tx ids")
            info = durable.info_edn()
            if (
                ":backend :sqlite" not in info
                or ":basis-t 0" not in info
                or ":tx-count 0" not in info
            ):
                raise RuntimeError("unexpected durable connection info")
            with durable.transact_report(
                '[{:db/id 1 :user/name "Durable Ada" :user/email "durable-ada@example.com"}]'
            ) as report:
                if not report.value().get(":ok"):
                    raise RuntimeError("unexpected SQLite transaction report")
            if durable.basis_t() != 1:
                raise RuntimeError("unexpected durable basis after first tx")
            if durable.tx_count() != 1:
                raise RuntimeError("unexpected durable tx count after first tx")
            if durable.tx_ids() != [1]:
                raise RuntimeError("unexpected durable tx ids after first tx")
            with durable.prepare(
                "[:find ?e ?email :where [?e :user/email ?email]]"
            ) as all_emails, durable.db() as db:
                rows = all_emails.rows(db)
                print(f"sqlite-live rows: {rows}")
                if len(rows) != 1:
                    raise RuntimeError("unexpected SQLite live row count")

        with vev.connect(sqlite_path) as durable:
            if durable.basis_t() != 1:
                raise RuntimeError("unexpected reopened durable basis")
            if durable.tx_count() != 1:
                raise RuntimeError("unexpected reopened durable tx count")
            if durable.tx_ids() != [1]:
                raise RuntimeError("unexpected reopened durable tx ids")
            with durable.prepare(
                "[:find ?e ?email :where [?e :user/email ?email]]"
            ) as all_emails, durable.db() as db:
                rows = all_emails.rows(db)
                print(f"sqlite-reopened rows: {rows}")
                if len(rows) != 1:
                    raise RuntimeError("unexpected SQLite reopened row count")
    finally:
        remove_sqlite_files(sqlite_path)

    return 0


if __name__ == "__main__":
    sys.exit(main())
