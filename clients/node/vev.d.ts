// Copyright (c) Andreas Flakstad and Vev contributors
// SPDX-License-Identifier: EPL-2.0

export class Conn {
  transact(tx: string): string;
  queryText(query: string, inputs?: string): string;
  prepare(query: string): PreparedQuery;
  db(): DB;
}

export class DurableConn {
  constructor(uri: string);
  transact(tx: string): string;
  db(): DB;
  backend(): string;
  path(): string;
  basisT(): number;
  txCount(): number;
}

export class DB {
  query(query: PreparedQuery, inputs?: string): string;
  rows(query: PreparedQuery, inputs?: string): unknown[][];
  withReport(tx: string): { edn: string; dbBefore: DB; dbAfter: DB };
  pull(pattern: string, entity: number): unknown;
  pullLookupRefString(pattern: string, attr: string, value: string): unknown;
  pullMany(pattern: string, entities: number[]): unknown[];
}

export class PreparedQuery {
  query(conn: Conn, inputs?: string): string;
  rows(conn: Conn, inputs?: string): unknown[][];
}

export function connect(uri: string): DurableConn;
export function createConn(): Conn;
export function openMemory(): Conn;
