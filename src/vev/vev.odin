package vev

Value_Kind :: enum {
  Entity,
  String,
  Int,
  Bool,
  Keyword,
}

Value :: struct {
  kind:   Value_Kind,
  entity: u64,
  text:   string,
  int:    i64,
  bool:   bool,
}

Term_Kind :: enum {
  Value,
  Var,
}

Term :: struct {
  kind:  Term_Kind,
  value: Value,
  name:  string,
}

Datom :: struct {
  e:     u64,
  a:     string,
  v:     Value,
  tx:    u64,
  added: bool,
}

DB :: struct {
  datoms: [dynamic]Datom,
}

Conn :: struct {
  db:      DB,
  next_tx: u64,
}

Tx_Op_Kind :: enum {
  Add,
  Retract,
}

Tx_Op :: struct {
  kind: Tx_Op_Kind,
  e:    u64,
  a:    string,
  v:    Value,
}

Tx_Meta :: struct {
  a: string,
  v: Value,
}

Tx_Report :: struct {
  db_before: DB,
  db_after:  DB,
  tx_data:   [dynamic]Datom,
  tx:        u64,
  tx_meta:   [dynamic]Tx_Meta,
}

Clause :: struct {
  e: Term,
  a: Term,
  v: Term,
}

Query :: struct {
  find:    [dynamic]string,
  clauses: []Clause,
}

Var_Binding :: struct {
  name:  string,
  value: Value,
}

Binding :: [dynamic]Var_Binding

Result_Row :: struct {
  values: [dynamic]Value,
}

Result_Set :: struct {
  rows: [dynamic]Result_Row,
}

create_conn :: proc() -> Conn {
  return Conn{
    db      = DB{},
    next_tx = 1,
  }
}

value_entity :: proc(id: u64) -> Value {
  return Value{kind = .Entity, entity = id}
}

value_string :: proc(text: string) -> Value {
  return Value{kind = .String, text = text}
}

value_int :: proc(value: i64) -> Value {
  return Value{kind = .Int, int = value}
}

value_bool :: proc(value: bool) -> Value {
  return Value{kind = .Bool, bool = value}
}

value_keyword :: proc(text: string) -> Value {
  return Value{kind = .Keyword, text = text}
}

term_value :: proc(value: Value) -> Term {
  return Term{kind = .Value, value = value}
}

term_entity :: proc(id: u64) -> Term {
  return term_value(value_entity(id))
}

term_string :: proc(text: string) -> Term {
  return term_value(value_string(text))
}

term_int :: proc(value: i64) -> Term {
  return term_value(value_int(value))
}

term_bool :: proc(value: bool) -> Term {
  return term_value(value_bool(value))
}

term_keyword :: proc(text: string) -> Term {
  return term_value(value_keyword(text))
}

term_var :: proc(name: string) -> Term {
  return Term{kind = .Var, name = name}
}

attr :: proc(name: string) -> Term {
  return term_keyword(name)
}

clause :: proc(e: Term, a: string, v: Term) -> Clause {
  return Clause{
    e = e,
    a = attr(a),
    v = v,
  }
}

tx_add :: proc(e: u64, a: string, v: Value) -> Tx_Op {
  return Tx_Op{kind = .Add, e = e, a = a, v = v}
}

tx_retract :: proc(e: u64, a: string, v: Value) -> Tx_Op {
  return Tx_Op{kind = .Retract, e = e, a = a, v = v}
}

query :: proc(find: []string, clauses: []Clause) -> Query {
  owned_find, _ := make([dynamic]string, 0, len(find))
  if len(find) > 0 {
    append(&owned_find, ..find)
  }
  return Query{find = owned_find, clauses = clauses}
}

clone_datoms :: proc(datoms: [dynamic]Datom) -> [dynamic]Datom {
  out, _ := make([dynamic]Datom, 0, len(datoms))
  if len(datoms) > 0 {
    append(&out, ..datoms[:])
  }
  return out
}

clone_tx_meta :: proc(meta: []Tx_Meta) -> [dynamic]Tx_Meta {
  out, _ := make([dynamic]Tx_Meta, 0, len(meta))
  if len(meta) > 0 {
    append(&out, ..meta)
  }
  return out
}

clone_binding :: proc(binding: Binding) -> Binding {
  out, _ := make(Binding, 0, len(binding))
  if len(binding) > 0 {
    append(&out, ..binding[:])
  }
  return out
}

value_equal :: proc(left, right: Value) -> bool {
  if left.kind != right.kind {
    return false
  }

  switch left.kind {
  case .Entity:
    return left.entity == right.entity
  case .String:
    return left.text == right.text
  case .Int:
    return left.int == right.int
  case .Bool:
    return left.bool == right.bool
  case .Keyword:
    return left.text == right.text
  }

  return false
}

binding_lookup :: proc(binding: Binding, name: string) -> (Value, bool) {
  for item in binding {
    if item.name == name {
      return item.value, true
    }
  }
  return Value{}, false
}

binding_store :: proc(binding: ^Binding, name: string, value: Value) {
  append(binding, Var_Binding{name = name, value = value})
}

match_term :: proc(term: Term, actual: Value, binding: ^Binding) -> bool {
  switch term.kind {
  case .Value:
    return value_equal(term.value, actual)
  case .Var:
    existing, ok := binding_lookup(binding^, term.name)
    if ok {
      return value_equal(existing, actual)
    }
    binding_store(binding, term.name, actual)
    return true
  }
  return false
}

same_fact :: proc(left, right: Datom) -> bool {
  return left.e == right.e && left.a == right.a && value_equal(left.v, right.v)
}

datom_is_current_at :: proc(datoms: []Datom, index: int) -> bool {
  datom := datoms[index]
  if !datom.added {
    return false
  }

  for later_index := index + 1; later_index < len(datoms); later_index += 1 {
    if same_fact(datom, datoms[later_index]) {
      return false
    }
  }

  return true
}

transact_with_meta :: proc(conn: ^Conn, ops: []Tx_Op, tx_meta: []Tx_Meta) -> Tx_Report {
  before := DB{datoms = clone_datoms(conn.db.datoms)}
  after_datoms := clone_datoms(conn.db.datoms)
  tx_data, _ := make([dynamic]Datom, 0, len(ops))
  tx := conn.next_tx

  for op in ops {
    datom := Datom{
      e     = op.e,
      a     = op.a,
      v     = op.v,
      tx    = tx,
      added = op.kind == .Add,
    }
    append(&after_datoms, datom)
    append(&tx_data, datom)
  }

  after := DB{datoms = after_datoms}

  conn.db = after
  conn.next_tx += 1

  return Tx_Report{
    db_before = before,
    db_after  = after,
    tx_data   = tx_data,
    tx        = tx,
    tx_meta   = clone_tx_meta(tx_meta),
  }
}

transact :: proc(conn: ^Conn, ops: []Tx_Op) -> Tx_Report {
  return transact_with_meta(conn, ops, []Tx_Meta{})
}

datom_clause_matches :: proc(datom: Datom, clause: Clause, binding: ^Binding) -> bool {
  if !match_term(clause.e, value_entity(datom.e), binding) {
    return false
  }
  if !match_term(clause.a, value_keyword(datom.a), binding) {
    return false
  }
  if !match_term(clause.v, datom.v, binding) {
    return false
  }
  return true
}

q :: proc(db: DB, query: Query) -> Result_Set {
  bindings, _ := make([dynamic]Binding, 0, 1)
  append(&bindings, Binding{})

  for clause in query.clauses {
    next_bindings, _ := make([dynamic]Binding)

    for binding in bindings {
      for datom_index := 0; datom_index < len(db.datoms); datom_index += 1 {
        if !datom_is_current_at(db.datoms[:], datom_index) {
          continue
        }

        candidate := clone_binding(binding)
        if datom_clause_matches(db.datoms[datom_index], clause, &candidate) {
          append(&next_bindings, candidate)
        }
      }
    }

    bindings = next_bindings
  }

  rows, _ := make([dynamic]Result_Row, 0, len(bindings))
  for binding in bindings {
    values, _ := make([dynamic]Value, 0, len(query.find))
    complete := true

    for name in query.find {
      value, ok := binding_lookup(binding, name)
      if !ok {
        complete = false
        break
      }
      append(&values, value)
    }

    if complete {
      append(&rows, Result_Row{values = values})
    }
  }

  return Result_Set{rows = rows}
}

q_strings :: proc(db: DB, query: Query) -> []string {
  result := q(db, query)
  out, _ := make([dynamic]string, 0, len(result.rows))

  for row in result.rows {
    if len(row.values) != 1 || row.values[0].kind != .String {
      continue
    }
    append(&out, row.values[0].text)
  }

  return out[:]
}
