package vev

// A query term is either:
// - a concrete entity id
// - a concrete string value
// - a variable name such as "e" or "name"
//
// This keeps the first proof extremely small while still being enough to
// express a two-clause DataScript-style lookup.
Term_Kind :: enum {
  Entity,
  String,
  Var,
}

// Term is used in query clauses before matching against actual datoms.
Term :: struct {
  kind:   Term_Kind,
  entity: u64,
  text:   string,
}

// During matching we normalize concrete values into one runtime shape.
// We only support entity ids and strings for now.
Resolved_Value_Kind :: enum {
  Entity,
  String,
}

Resolved_Value :: struct {
  kind:   Resolved_Value_Kind,
  entity: u64,
  text:   string,
}

// Datom is the fundamental fact unit:
// entity, attribute, value, and the transaction id that asserted it.
//
// We intentionally omit `added` for this first proof because we only support
// adds and not retracts.
Datom :: struct {
  e:  u64,
  a:  string,
  v:  string,
  tx: u64,
}

// DB is an immutable snapshot from the caller's point of view.
// The current implementation achieves that simply by cloning the dynamic
// array on transact. This is not efficient yet, but it is direct and easy
// to reason about.
DB :: struct {
  datoms: [dynamic]Datom,
}

// Conn is the one mutable handle in the system.
// Transactions update `db` and advance the transaction counter.
Conn :: struct {
  db: DB,
  next_tx: u64,
}

// We only support adds for the first proof.
Tx_Op_Kind :: enum {
  Add,
}

// A transaction op is deliberately flat for now.
// We are not parsing DataScript tx data yet.
Tx_Op :: struct {
  kind: Tx_Op_Kind,
  e:    u64,
  a:    string,
  v:    string,
}

// Tx_Report mirrors the shape we will want long term:
// callers can inspect both snapshots and the datoms produced by the tx.
Tx_Report :: struct {
  db_before: DB,
  db_after:  DB,
  tx_data:   [dynamic]Datom,
}

// A clause is the smallest Datalog-shaped piece we need:
// [?e :user/email "a@example.com"]
Clause :: struct {
  e: Term,
  a: string,
  v: Term,
}

// The first query engine supports only a single `find` variable and a list
// of where clauses.
Query :: struct {
  find:    string,
  clauses: []Clause,
}

// Bindings are accumulated variable assignments while evaluating clauses.
// Example:
//   "e"    -> entity 1
//   "name" -> string "Andreas"
Var_Binding :: struct {
  name:  string,
  value: Resolved_Value,
}

// [dynamic] means growable storage in Odin.
// We use it wherever we append during evaluation.
Binding :: [dynamic]Var_Binding

create_conn :: proc() -> Conn {
  return Conn{
    db = DB{},
    next_tx = 1,
  }
}

term_entity :: proc(id: u64) -> Term {
  return Term{
    kind   = .Entity,
    entity = id,
  }
}

term_string :: proc(text: string) -> Term {
  return Term{
    kind = .String,
    text = text,
  }
}

term_var :: proc(name: string) -> Term {
  return Term{
    kind = .Var,
    text = name,
  }
}

// We clone arrays explicitly because the proof treats DB values as immutable.
// A later version can replace this with structural sharing or indexes.
clone_datoms :: proc(datoms: [dynamic]Datom) -> [dynamic]Datom {
  out, _ := make([dynamic]Datom, 0, len(datoms))
  if len(datoms) > 0 {
    append(&out, ..datoms[:])
  }
  return out
}

// Query evaluation forks bindings frequently, so we need a small helper
// to copy the current variable assignments before trying another match.
clone_binding :: proc(binding: Binding) -> Binding {
  out, _ := make(Binding, 0, len(binding))
  if len(binding) > 0 {
    append(&out, ..binding[:])
  }
  return out
}

// Variable unification needs equality on the small value domain we support.
resolved_value_equal :: proc(left, right: Resolved_Value) -> bool {
  if left.kind != right.kind {
    return false
  }
  if left.kind == .Entity {
    return left.entity == right.entity
  }
  return left.text == right.text
}

binding_lookup :: proc(binding: Binding, name: string) -> (Resolved_Value, bool) {
  for item in binding {
    if item.name == name {
      return item.value, true
    }
  }
  return Resolved_Value{}, false
}

// Odin's append works on dynamic arrays, so we pass a pointer to the binding
// we want to grow.
binding_store :: proc(binding: ^Binding, name: string, value: Resolved_Value) {
  append(binding, Var_Binding{
    name  = name,
    value = value,
  })
}

// Match one query term against one concrete value.
//
// Entity and String terms are literal matches.
// Var terms either:
// - confirm an existing binding
// - or capture a new binding if the variable is still unbound
match_term :: proc(term: Term, actual: Resolved_Value, binding: ^Binding) -> bool {
  switch term.kind {
  case .Entity:
    return actual.kind == .Entity && actual.entity == term.entity
  case .String:
    return actual.kind == .String && actual.text == term.text
  case .Var:
    existing, ok := binding_lookup(binding^, term.text)
    if ok {
      return resolved_value_equal(existing, actual)
    }
    binding_store(binding, term.text, actual)
    return true
  }
  return false
}

// Apply a batch of add operations and replace the connection's DB
// with a new snapshot. This is intentionally the simplest possible model:
// clone the old datoms, append the new ones, then swap the snapshot.
transact :: proc(conn: ^Conn, ops: []Tx_Op) -> Tx_Report {
  before := DB{
    datoms = clone_datoms(conn.db.datoms),
  }

  after_datoms := clone_datoms(conn.db.datoms)
  tx_data, _ := make([dynamic]Datom, 0, len(ops))

  for op in ops {
    switch op.kind {
    case .Add:
      datom := Datom{
	e  = op.e,
	a  = op.a,
	v  = op.v,
	tx = conn.next_tx,
      }
      append(&after_datoms, datom)
      append(&tx_data, datom)
    }
  }

  after := DB{
    datoms = after_datoms,
  }

  conn.db = after
  conn.next_tx += 1

  return Tx_Report{
    db_before = before,
    db_after  = after,
    tx_data   = tx_data,
  }
}

// q is a tiny naive query engine:
// 1. start with one empty binding
// 2. for each clause, try every datom against every current binding
// 3. carry forward only the bindings that still match
// 4. read the requested `find` variable from the surviving bindings
//
// This is deliberately not optimized. The point is to prove the semantics
// before we introduce indexes or a parser.
q :: proc(db: DB, query: Query) -> []string {
  bindings, _ := make([dynamic]Binding, 0, 1)
  append(&bindings, Binding{})

  for clause in query.clauses {
    // Each clause narrows or extends the set of possible bindings.
    next_bindings, _ := make([dynamic]Binding)

    for binding in bindings {
      for datom in db.datoms {
	// Attributes are stored as plain strings in this first pass,
	// so the cheap pre-filter is just string equality.
	if datom.a != clause.a {
	  continue
	}

	// Clone before matching so each possible datom match gets its
	// own independent variable assignment path.
	candidate := clone_binding(binding)
	if !match_term(clause.e, Resolved_Value{kind = .Entity, entity = datom.e}, &candidate) {
	  continue
	}
	if !match_term(clause.v, Resolved_Value{kind = .String, text = datom.v}, &candidate) {
	  continue
	}

	append(&next_bindings, candidate)
      }
    }

    bindings = next_bindings
  }

  results, _ := make([dynamic]string, 0, len(bindings))
  for binding in bindings {
    value, ok := binding_lookup(binding, query.find)
    if !ok || value.kind != .String {
      continue
    }
    append(&results, value.text)
  }

  // The public result type is a plain slice. `results[:]` views the dynamic
  // array as an ordinary slice for callers.
  return results[:]
}
