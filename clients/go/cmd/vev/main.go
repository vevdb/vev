// Copyright (c) Andreas Flakstad and Vev contributors
// SPDX-License-Identifier: EPL-2.0

package main

import (
	"fmt"
	"io"
	"os"
	"strconv"
	"strings"

	vev "github.com/vevdb/vev/clients/go"
)

const usage = `usage:
  vev info <db-path>
  vev transact <db-path> <tx-edn | @file | ->
  vev query <db-path> <query-edn | @file | -> [inputs-edn | @file]
  vev pull <db-path> <pattern-edn | @file | -> <entity-id>

examples:
  vev transact app.vev.sqlite '[{:db/id 1 :user/name "Ada"}]'
  vev query app.vev.sqlite '[:find ?name :where [?e :user/name ?name]]'
  vev pull app.vev.sqlite '[:user/name]' 1
`

func main() {
	if err := run(os.Args[1:]); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

func run(args []string) error {
	if len(args) == 0 || args[0] == "-h" || args[0] == "--help" {
		fmt.Print(usage)
		return nil
	}

	switch args[0] {
	case "info":
		if len(args) != 2 {
			return usageError("info expects <db-path>")
		}
		conn, err := vev.Connect(args[1])
		if err != nil {
			return err
		}
		defer conn.Close()
		fmt.Println(conn.InfoEDN())
		return nil

	case "transact":
		if len(args) != 3 {
			return usageError("transact expects <db-path> <tx-edn | @file | ->")
		}
		tx, err := readTextArg(args[2])
		if err != nil {
			return err
		}
		conn, err := vev.Connect(args[1])
		if err != nil {
			return err
		}
		defer conn.Close()
		report, err := conn.Transact(tx)
		if err != nil {
			return err
		}
		defer report.Close()
		fmt.Println(report.EDN())
		return nil

	case "query":
		if len(args) != 3 && len(args) != 4 {
			return usageError("query expects <db-path> <query-edn | @file | -> [inputs-edn | @file]")
		}
		query, err := readTextArg(args[2])
		if err != nil {
			return err
		}
		inputs := "[]"
		if len(args) == 4 {
			inputs, err = readTextArg(args[3])
			if err != nil {
				return err
			}
		}
		conn, err := vev.Connect(args[1])
		if err != nil {
			return err
		}
		defer conn.Close()
		db, err := conn.DB()
		if err != nil {
			return err
		}
		defer db.Close()
		result, err := db.QueryText(query, inputs)
		if err != nil {
			return err
		}
		fmt.Println(result)
		return nil

	case "pull":
		if len(args) != 4 {
			return usageError("pull expects <db-path> <pattern-edn | @file | -> <entity-id>")
		}
		pattern, err := readTextArg(args[2])
		if err != nil {
			return err
		}
		entity, err := strconv.ParseUint(args[3], 10, 64)
		if err != nil {
			return fmt.Errorf("invalid entity id %q: %w", args[3], err)
		}
		conn, err := vev.Connect(args[1])
		if err != nil {
			return err
		}
		defer conn.Close()
		db, err := conn.DB()
		if err != nil {
			return err
		}
		defer db.Close()
		result, err := db.PullEDN(pattern, entity)
		if err != nil {
			return err
		}
		fmt.Println(result)
		return nil

	default:
		return usageError("unknown command: " + args[0])
	}
}

func usageError(message string) error {
	return fmt.Errorf("%s\n\n%s", message, usage)
}

func readTextArg(arg string) (string, error) {
	if arg == "-" {
		data, err := io.ReadAll(os.Stdin)
		if err != nil {
			return "", err
		}
		return strings.TrimSpace(string(data)), nil
	}
	if strings.HasPrefix(arg, "@") {
		data, err := os.ReadFile(strings.TrimPrefix(arg, "@"))
		if err != nil {
			return "", err
		}
		return strings.TrimSpace(string(data)), nil
	}
	return arg, nil
}
