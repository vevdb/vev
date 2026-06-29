// Copyright (c) Andreas Flakstad and Vev contributors
// SPDX-License-Identifier: EPL-2.0

package main

import "core:dynlib"
import "core:fmt"
import "core:os"
import "core:strings"

Vev_API :: struct {
	version:       proc "c" () -> cstring `dynlib:"vev_version"`,
	open_memory:   proc "c" () -> rawptr `dynlib:"vev_conn_open_memory"`,
	close_conn:    proc "c" (conn: rawptr) `dynlib:"vev_conn_close"`,
	transact_edn:  proc "c" (conn: rawptr, tx_text: cstring) -> cstring `dynlib:"vev_transact_edn"`,
	query_edn:     proc "c" (conn: rawptr, query_text: cstring) -> cstring `dynlib:"vev_query_edn"`,
	string_free:   proc "c" (text: cstring) `dynlib:"vev_string_free"`,
	__handle:      dynlib.Library,
}

platform_library_name :: proc() -> string {
	when ODIN_OS == .Darwin {
		return "libvev.dylib"
	} else when ODIN_OS == .Linux {
		return "libvev.so"
	} else when ODIN_OS == .Windows {
		return "vev.dll"
	}
	return "libvev"
}

default_library_path :: proc() -> string {
	path, err := os.join_path({"build", "lib", platform_library_name()}, context.allocator)
	if err != nil {
		return strings.clone(platform_library_name())
	}
	return path
}

to_cstring :: proc(text: string) -> cstring {
	return strings.clone_to_cstring(text, context.allocator)
}

main :: proc() {
	library_path := ""
	if len(os.args) > 1 {
		library_path = strings.clone(os.args[1])
	} else {
		library_path = default_library_path()
	}
	defer delete(library_path)

	api := Vev_API{}
	_, ok := dynlib.initialize_symbols(&api, library_path)
	if !ok {
		fmt.eprintln("failed to load Vev native library:", library_path)
		os.exit(1)
	}
	defer dynlib.unload_library(api.__handle)

	if api.version == nil || api.open_memory == nil || api.close_conn == nil || api.transact_edn == nil || api.query_edn == nil || api.string_free == nil {
		fmt.eprintln("loaded library does not expose the expected Vev ABI")
		os.exit(1)
	}

	conn := api.open_memory()
	if conn == nil {
		fmt.eprintln("failed to create Vev connection")
		os.exit(1)
	}
	defer api.close_conn(conn)

	tx := to_cstring(`[
		{:db/id 1 :user/name "Ada" :user/email "ada@example.com"}
		{:db/id 2 :user/name "Grace" :user/email "grace@example.com"}
	]`)
	defer delete(tx)

	tx_result := api.transact_edn(conn, tx)
	if tx_result == nil {
		fmt.eprintln("transaction returned nil")
		os.exit(1)
	}
	defer api.string_free(tx_result)
	fmt.println("tx:", tx_result)

	query := to_cstring(`[:find ?name :where [?e :user/name ?name]]`)
	defer delete(query)

	query_result := api.query_edn(conn, query)
	if query_result == nil {
		fmt.eprintln("query returned nil")
		os.exit(1)
	}
	defer api.string_free(query_result)

	result := string(query_result)
	fmt.println("query:", result)
	if !strings.contains(result, `"Ada"`) || !strings.contains(result, `"Grace"`) {
		fmt.eprintln("unexpected query result:", result)
		os.exit(1)
	}
}
