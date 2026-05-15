package main

import "core:fmt"
import "core:os"
import "vev"

// main is a tiny executable proof rather than a real test harness.
// It demonstrates the smallest end-to-end flow we care about right now:
// create a connection, transact a couple of facts, query them back, and
// assert that snapshot semantics behave as expected.
main :: proc() {
  // Start with an empty mutable connection.
  conn := vev.create_conn()

  // Insert two facts about the same entity.
  // This is the direct in-memory equivalent of a very small `:db/add` batch.
  report := vev.transact(&conn, []vev.Tx_Op{
    {
      kind = .Add,
      e    = 1,
      a    = ":user/email",
      v    = "a@example.com",
    },
    {
      kind = .Add,
      e    = 1,
      a    = ":user/name",
      v    = "Andreas",
    },
  })

  // Build the query as data rather than text for the first proof.
  //
  // Read it as:
  // find ?name
  // where
  //   [?e :user/email "a@example.com"]
  //   [?e :user/name ?name]
  query := vev.Query{
    find = "name",
    clauses = []vev.Clause{
      {
	e = vev.term_var("e"),
	a = ":user/email",
	v = vev.term_string("a@example.com"),
      },
      {
	e = vev.term_var("e"),
	a = ":user/name",
	v = vev.term_var("name"),
      },
    },
  }

  // Evaluate against the connection's current immutable snapshot.
  results := vev.q(conn.db, query)

  // The proof succeeds only if:
  // - the old snapshot was empty
  // - the new snapshot contains both datoms
  // - the tx report includes those produced datoms
  // - the query finds exactly one name
  ok := len(report.db_before.datoms) == 0 &&
    len(report.db_after.datoms) == 2 &&
    len(report.tx_data) == 2 &&
    len(results) == 1 &&
    results[0] == "Andreas"

  if !ok {
    fmt.println("first proof failed")
    fmt.println("db_before:", len(report.db_before.datoms))
    fmt.println("db_after:", len(report.db_after.datoms))
    fmt.println("tx_data:", len(report.tx_data))
    fmt.println("results:", results)
    os.exit(1)
  }

  fmt.println("first proof ok")
  fmt.println(results[0])
}
