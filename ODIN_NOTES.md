# Odin Notes For This Proof

This project is intentionally starting with a very small subset of Odin.
The goal of this note is to explain the specific Odin features used in the
first in-memory `odinlog` proof and why they appear in the code.

The two files to read alongside this note are:

- [src/odinlog/odinlog.odin](src/odinlog/odinlog.odin)
- [src/main.odin](src/main.odin)

## Packages

Odin code is organized into packages.

```odin
package odinlog
```

This is similar to a module/namespace declaration. Everything in the same
folder with the same `package` name belongs to that package.

Why used here:
- `package odinlog` holds the library code
- `package main` holds the tiny executable proof

## Imports

Imports bring other packages into scope.

```odin
import "core:fmt"
import "core:os"
import "odinlog"
```

`core:fmt` and `core:os` come from Odin's standard/core library.
`odinlog` is the local package we wrote.

Why used here:
- `fmt` prints proof output
- `os.exit` exits with failure if the proof does not match expectations
- `odinlog` is the package under test

## Declarations With `::`

Odin uses `::` for constant-like declarations of procedures, types, and values.

```odin
main :: proc() {
    ...
}
```

Read this as "`main` is defined as this procedure".

Why used here:
- it is the normal style for naming procedures and types
- it keeps definitions explicit and uniform

## Procedures

Procedures are functions.

```odin
create_conn :: proc() -> Conn {
    ...
}
```

Parts:
- `proc()` means "procedure with no parameters"
- `-> Conn` means it returns a `Conn`

Why used here:
- the code is organized as small explicit procedures instead of methods or
  trait-like abstractions

## Structs

Structs are records with named fields.

```odin
Datom :: struct {
    e:  u64,
    a:  string,
    v:  string,
    tx: u64,
}
```

Why used here:
- the database is data-oriented
- structs make the engine shape obvious
- they map naturally to future storage and FFI boundaries

## Enums

Enums represent a closed set of variants.

```odin
Term_Kind :: enum {
    Entity,
    String,
    Var,
}
```

Why used here:
- `Term` can mean different things in a query
- the enum makes those cases explicit
- `switch` over enums reads directly and safely

## Basic Types Used Here

The proof uses only a few built-in types:

- `u64`: unsigned 64-bit integer
- `string`: immutable string value
- `bool`: true/false

Why used here:
- entity ids and tx ids use `u64`
- attributes and string values use `string`

## Struct Literals

You construct structs with named fields.

```odin
Datom{
    e  = op.e,
    a  = op.a,
    v  = op.v,
    tx = conn.next_tx,
}
```

Why used here:
- named fields make the code easier to read than positional construction
- it is clearer for data-heavy code like databases

## Slices

A slice is a view over a sequence of values.

```odin
[]Clause
[]string
```

Why used here:
- `Query.clauses` is a plain slice because queries are read-only after
  construction in this proof
- the result of `q` is returned as `[]string`

A slice is not automatically growable. It is just a span of elements.

## Dynamic Arrays

A dynamic array is Odin's growable array type.

```odin
[dynamic]Datom
[dynamic]Var_Binding
```

Why used here:
- we append datoms during transact
- we append bindings during query evaluation
- we append result strings while collecting matches

This is one of the most important distinctions in the proof:

- use `[]T` when the value is a read-only/view-like sequence
- use `[dynamic]T` when the code needs to grow it with `append`

## `make`

`make` allocates slices, dynamic arrays, maps, and related container values.

```odin
out, _ := make([dynamic]Datom, 0, len(datoms))
```

Read this as:
- create a dynamic array of `Datom`
- initial length `0`
- capacity `len(datoms)`

Why used here:
- cloning and accumulation need new growable arrays
- pre-sizing capacity avoids some reallocations even in this tiny proof

Why the `, _`:
- `make` returns the container and an allocation error
- `_` means "ignore that second result"
- fine for this tiny prototype, but later code may want to handle allocation
  failure more carefully

## `append`

`append` grows a dynamic array.

```odin
append(&out, datum)
```

Why the `&`:
- `append` needs a pointer to the dynamic array so it can modify its length
  and possibly its backing storage

Why used here:
- cloning datoms
- building transaction output
- accumulating query bindings
- accumulating final result rows

There is also a second form used in the copy helpers:

```odin
append(&out, ..datoms[:])
```

The `..` means "pass each element as its own argument". The `[:]` turns the
dynamic array into a plain slice view first.

Why used here:
- it is a concise way to copy all elements from one growable array into
  another
- it is more idiomatic than writing a manual loop when you just want a clone

## Pointers With `^` and `&`

There are two pointer-related forms used here:

- `^T` means "pointer to `T`" in a type
- `&value` means "take the address of `value`"

Examples:

```odin
transact :: proc(conn: ^Conn, ops: []Tx_Op) -> Tx_Report
append(&out, datum)
```

Why used here:
- `transact` mutates the connection, so it receives `^Conn`
- `append` mutates dynamic arrays, so it receives pointers to them
- `binding_store` and `match_term` update a binding in place

There is also:

```odin
binding^
```

This dereferences a pointer, meaning "the value pointed to by `binding`".

Why used here:
- `binding_lookup` expects a `Binding`, not `^Binding`
- so `binding^` passes the pointed-to value

## `:=`

`:=` declares a new local variable and infers its type.

```odin
conn := odinlog.create_conn()
```

Why used here:
- it keeps local code compact
- the inferred type is obvious from the right-hand side

## `=` In Struct Literals vs Assignment

In normal assignment:

```odin
conn.current = after
```

In struct literals:

```odin
Conn{
    current = DB{},
    next_tx = 1,
}
```

Odin uses `=` inside struct literals for field initialization rather than `:`.

Why used here:
- that is just Odin syntax, but it is worth noticing because it differs from
  many languages

## `for` Loops

The proof uses only simple `for` loops over collections.

```odin
for datum in datoms {
    append(&out, datum)
}
```

Why used here:
- the first proof is intentionally direct
- query evaluation is easiest to read as nested loops

This is the heart of the current query engine:
- for each clause
- for each current binding
- for each datom
- keep only the matches

## `switch`

`switch` is used to branch on enum variants.

```odin
switch term.kind {
case .Entity:
    ...
case .String:
    ...
case .Var:
    ...
}
```

Why used here:
- `Term` matching is variant-based
- `switch` over an enum is clearer than a chain of `if`s

Note the `.Entity` style. Odin lets you use the enum member name directly
when the enum type is already known from context.

## Multiple Return Values

Odin supports returning more than one value.

```odin
value, ok := binding_lookup(binding, query.find)
```

Why used here:
- lookup naturally returns both the value and whether it was found
- this avoids sentinel values or option wrappers in the first proof

The same idea appears with:

```odin
out, _ := make([dynamic]Datom, 0, len(datoms))
```

## Boolean Conditions

Conditions look straightforward:

```odin
if !ok || value.kind != .String {
    continue
}
```

Why used here:
- the proof filters bad matches early
- the code stays flat instead of building nested condition trees

## `continue`

`continue` skips to the next loop iteration.

Why used here:
- query matching has several cheap failure cases
- early `continue` keeps the success path easy to read

## Returning A Slice From A Dynamic Array

At the end of `q`:

```odin
return results[:]
```

`results` is a dynamic array, but the public return type is `[]string`.
`results[:]` creates a slice view over the whole dynamic array.

Why used here:
- callers only need the result sequence
- they do not need the growable container representation

## Why This Style Was Chosen

This first proof uses Odin in a deliberately plain way:

- structs for data
- enums for small tagged variants
- procedures instead of methods-heavy design
- dynamic arrays where we append
- slices at the boundaries
- explicit mutation only in `Conn` and local accumulators

That style is useful for a database core because:
- the data layout stays visible
- control flow is easy to follow
- there is little hidden machinery
- future storage/index work can grow from it without a redesign

## What To Notice When Reading The Proof

If you are new to Odin, the most important reading guide for this code is:

1. `Conn` is mutable, `DB` is treated as immutable.
2. `[dynamic]T` means "this thing grows".
3. `[]T` means "this is a sequence view".
4. `^T` and `&value` are used only where mutation is intentional.
5. The query engine is just nested loops plus variable bindings.

That is enough to understand nearly all of the current code.
