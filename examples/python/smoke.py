#!/usr/bin/env python3
import sys

import vev


def main() -> int:
    with vev.open_memory() as conn:
        tx = conn.transact(
            """
            [{:db/id 1 :user/name "Ada" :user/email "ada@example.com"}
             {:db/id 2 :user/name "Grace" :user/email "grace@example.com"}]
            """
        )
        print(f"tx: {tx}")

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
            finally:
                all_emails.close()
        finally:
            email_query.close()

    return 0


if __name__ == "__main__":
    sys.exit(main())
