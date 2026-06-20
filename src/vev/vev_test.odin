package vev

import "core:testing"

when ODIN_TEST {
  query_name_by_email :: proc(email: string) -> Query {
    clauses, _ := make([dynamic]Clause, 0, 2)
    append(&clauses, clause(term_var("e"), ":user/email", term_string(email)))
    append(&clauses, clause(term_var("e"), ":user/name", term_var("name")))

    find := []string{"name"}
    return query(find, clauses[:])
  }

  seed_people :: proc(conn: ^Conn) -> Tx_Report {
    return transact(conn, []Tx_Op{
      tx_add(1, ":user/email", value_string("ada@example.com")),
      tx_add(1, ":user/name", value_string("Ada")),
      tx_add(1, ":user/age", value_int(37)),
      tx_add(1, ":user/active", value_bool(true)),
      tx_add(1, ":user/friend", value_entity(2)),
      tx_add(2, ":user/email", value_string("grace@example.com")),
      tx_add(2, ":user/name", value_string("Grace")),
      tx_add(2, ":user/age", value_int(41)),
      tx_add(2, ":user/active", value_bool(false)),
    })
  }

  @(test)
  test_transact_reports_snapshots_and_metadata :: proc(t: ^testing.T) {
    conn := create_conn()
    defer free_all(context.allocator)

    report := transact_with_meta(
      &conn,
      []Tx_Op{
        tx_add(1, ":user/email", value_string("a@example.com")),
        tx_add(1, ":user/name", value_string("Andreas")),
      },
      []Tx_Meta{
        {a = ":request/id", v = value_string("req-1")},
        {a = ":tx/reason", v = value_keyword(":profile-edit")},
      },
    )

    testing.expect_value(t, report.tx, u64(1))
    testing.expect_value(t, conn.next_tx, u64(2))
    testing.expect_value(t, len(report.db_before.datoms), 0)
    testing.expect_value(t, len(report.db_after.datoms), 2)
    testing.expect_value(t, len(report.tx_data), 2)
    testing.expect_value(t, len(report.tx_meta), 2)
    testing.expect_value(t, report.tx_meta[0].a, ":request/id")
    testing.expect_value(t, report.tx_meta[0].v.text, "req-1")

    results := q_strings(report.db_after, query_name_by_email("a@example.com"))
    testing.expect_value(t, len(results), 1)
    testing.expect_value(t, results[0], "Andreas")
  }

  @(test)
  test_query_joins_and_returns_multiple_columns :: proc(t: ^testing.T) {
    conn := create_conn()
    defer free_all(context.allocator)
    report := seed_people(&conn)

    clauses, _ := make([dynamic]Clause, 0, 3)
    append(&clauses, clause(term_var("e"), ":user/name", term_var("name")))
    append(&clauses, clause(term_var("e"), ":user/age", term_var("age")))
    append(&clauses, clause(term_var("e"), ":user/active", term_bool(true)))

    result := q(report.db_after, query([]string{"e", "name", "age"}, clauses[:]))

    testing.expect_value(t, len(result.rows), 1)
    testing.expect_value(t, result.rows[0].values[0].kind, Value_Kind.Entity)
    testing.expect_value(t, result.rows[0].values[0].entity, u64(1))
    testing.expect_value(t, result.rows[0].values[1].text, "Ada")
    testing.expect_value(t, result.rows[0].values[2].kind, Value_Kind.Int)
    testing.expect_value(t, result.rows[0].values[2].int, i64(37))
  }

  @(test)
  test_query_follows_ref_values :: proc(t: ^testing.T) {
    conn := create_conn()
    defer free_all(context.allocator)
    report := seed_people(&conn)

    clauses, _ := make([dynamic]Clause, 0, 3)
    append(&clauses, clause(term_var("e"), ":user/name", term_string("Ada")))
    append(&clauses, clause(term_var("e"), ":user/friend", term_var("friend")))
    append(&clauses, clause(term_var("friend"), ":user/name", term_var("friend_name")))

    result := q(report.db_after, query([]string{"friend", "friend_name"}, clauses[:]))

    testing.expect_value(t, len(result.rows), 1)
    testing.expect_value(t, result.rows[0].values[0].kind, Value_Kind.Entity)
    testing.expect_value(t, result.rows[0].values[0].entity, u64(2))
    testing.expect_value(t, result.rows[0].values[1].text, "Grace")
  }

  @(test)
  test_query_can_bind_attributes :: proc(t: ^testing.T) {
    conn := create_conn()
    defer free_all(context.allocator)
    report := seed_people(&conn)

    clauses, _ := make([dynamic]Clause, 0, 1)
    append(&clauses, Clause{
      e = term_entity(1),
      a = term_var("attr"),
      v = term_string("Ada"),
    })

    result := q(report.db_after, query([]string{"attr"}, clauses[:]))

    testing.expect_value(t, len(result.rows), 1)
    testing.expect_value(t, result.rows[0].values[0].kind, Value_Kind.Keyword)
    testing.expect_value(t, result.rows[0].values[0].text, ":user/name")
  }

  @(test)
  test_retract_hides_fact_from_current_queries :: proc(t: ^testing.T) {
    conn := create_conn()
    defer free_all(context.allocator)

    add_report := transact(&conn, []Tx_Op{
      tx_add(1, ":user/email", value_string("a@example.com")),
      tx_add(1, ":user/name", value_string("Andreas")),
    })

    retract_report := transact(&conn, []Tx_Op{
      tx_retract(1, ":user/name", value_string("Andreas")),
    })

    old_results := q_strings(add_report.db_after, query_name_by_email("a@example.com"))
    current_results := q_strings(retract_report.db_after, query_name_by_email("a@example.com"))

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
      tx_add(1, ":user/email", value_string("a@example.com")),
      tx_add(1, ":user/name", value_string("Andreas")),
    })

    _ = transact(&conn, []Tx_Op{
      tx_retract(1, ":user/name", value_string("Andreas")),
    })

    report := transact(&conn, []Tx_Op{
      tx_add(1, ":user/name", value_string("Andreas")),
    })

    results := q_strings(report.db_after, query_name_by_email("a@example.com"))

    testing.expect_value(t, len(results), 1)
    testing.expect_value(t, results[0], "Andreas")
    testing.expect_value(t, len(report.db_after.datoms), 4)
  }
}
