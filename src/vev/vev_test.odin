package vev

import "core:testing"
when ODIN_TEST {
  query_name_by_email :: proc(email: string) -> Query {
    clauses, _ := make([dynamic]Clause, 0, 2)
    append(&clauses, Clause{
      e = term_var("e"),
      a = ":user/email",
      v = term_string(email),
    })
    append(&clauses, Clause{
      e = term_var("e"),
      a = ":user/name",
      v = term_var("name"),
    })

    return Query{
      find = "name",
      clauses = clauses[:],
    }
  }

  @(test)
  test_transact_reports_snapshots_and_metadata :: proc(t: ^testing.T) {
    conn := create_conn()
    defer free_all(context.allocator)

    report := transact_with_meta(
        &conn,
      []Tx_Op{
        {
          kind = .Add,
          e = 1,
          a = ":user/email",
          v = "a@example.com",
        },
        {
          kind = .Add,
          e = 1,
          a = ":user/name",
          v = "Andreas",
        },
      },
      []Tx_Meta{
        {
          a = ":request/id",
          v = "req-1",
        },
        {
          a = ":tx/reason",
          v = ":profile-edit",
        },
      },
    )

    testing.expect_value(t, report.tx, u64(1))
    testing.expect_value(t, conn.next_tx, u64(2))
    testing.expect_value(t, len(report.db_before.datoms), 0)
    testing.expect_value(t, len(report.db_after.datoms), 2)
    testing.expect_value(t, len(report.tx_data), 2)
    testing.expect_value(t, len(report.tx_meta), 2)
    testing.expect_value(t, report.tx_meta[0].a, ":request/id")
    testing.expect_value(t, report.tx_meta[0].v, "req-1")

    results := q(report.db_after, query_name_by_email("a@example.com"))
    testing.expect_value(t, len(results), 1)
    testing.expect_value(t, results[0], "Andreas")
  }

  @(test)
  test_retract_hides_fact_from_current_queries :: proc(t: ^testing.T) {
    conn := create_conn()
    defer free_all(context.allocator)

    add_report := transact(&conn, []Tx_Op{
      {
        kind = .Add,
        e = 1,
        a = ":user/email",
        v = "a@example.com",
      },
      {
        kind = .Add,
        e = 1,
        a = ":user/name",
        v = "Andreas",
      },
    })

    retract_report := transact(&conn, []Tx_Op{
      {
        kind = .Retract,
        e = 1,
        a = ":user/name",
        v = "Andreas",
      },
    })

    old_results := q(add_report.db_after, query_name_by_email("a@example.com"))
    current_results := q(retract_report.db_after, query_name_by_email("a@example.com"))

    testing.expect_value(t, len(old_results), 1)
    testing.expect_value(t, old_results[0], "Andreas")
    testing.expect_value(t, len(current_results), 0)
    testing.expect_value(t, len(retract_report.db_after.datoms), 3)
    testing.expect_value(t, retract_report.tx_data[0].added, false)
  }

  @(test)
  test_readd_after_retract_makes_fact_current_once :: proc(t: ^testing.T) {
    conn := create_conn()
    defer free_all(context.allocator)

    _ = transact(&conn, []Tx_Op{
      {
        kind = .Add,
        e = 1,
        a = ":user/email",
        v = "a@example.com",
      },
      {
        kind = .Add,
        e = 1,
        a = ":user/name",
        v = "Andreas",
      },
    })

    _ = transact(&conn, []Tx_Op{
      {
        kind = .Retract,
        e = 1,
        a = ":user/name",
        v = "Andreas",
      },
    })

    report := transact(&conn, []Tx_Op{
      {
        kind = .Add,
        e = 1,
        a = ":user/name",
        v = "Andreas",
      },
    })

    results := q(report.db_after, query_name_by_email("a@example.com"))

    testing.expect_value(t, len(results), 1)
    testing.expect_value(t, results[0], "Andreas")
    testing.expect_value(t, len(report.db_after.datoms), 4)
  }
}
