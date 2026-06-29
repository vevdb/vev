// Copyright (c) Andreas Flakstad and Vev contributors
// SPDX-License-Identifier: EPL-2.0

"use strict";

const path = require("path");

function platformId() {
  let os;
  if (process.platform === "darwin") {
    os = "darwin";
  } else if (process.platform === "linux") {
    os = "linux";
  } else if (process.platform === "win32") {
    os = "windows";
  } else {
    os = process.platform;
  }

  let arch;
  if (process.arch === "arm64") {
    arch = "aarch64";
  } else if (process.arch === "x64") {
    arch = "x86_64";
  } else {
    arch = process.arch;
  }
  return `${os}-${arch}`;
}

function nativePath() {
  if (process.env.VEV_NODE_NATIVE) {
    return process.env.VEV_NODE_NATIVE;
  }
  const local = path.join(__dirname, "vev_native.node");
  try {
    return require.resolve(local);
  } catch (_error) {
    return path.join(__dirname, "native", platformId(), "vev_native.node");
  }
}

const nativePathValue = nativePath();
const native = require(nativePathValue);

class Conn {
  constructor(handle) {
    this._handle = handle || native.openMemory();
  }

  transact(tx) {
    return native.transact(this._handle, String(tx));
  }

  queryText(query, inputs = "[]") {
    return native.queryText(this._handle, String(query), String(inputs));
  }

  prepare(query) {
    return new PreparedQuery(native.prepare(String(query)));
  }

  db() {
    return new DB(native.db(this._handle));
  }
}

class DurableConn {
  constructor(uriOrHandle) {
    this._handle = typeof uriOrHandle === "string"
      ? native.connect(uriOrHandle)
      : uriOrHandle;
  }

  transact(tx) {
    return native.durableTransact(this._handle, String(tx));
  }

  db() {
    return new DB(native.durableDb(this._handle));
  }

  backend() {
    return native.durableBackend(this._handle);
  }

  path() {
    return native.durablePath(this._handle);
  }

  basisT() {
    return native.durableBasisT(this._handle);
  }

  txCount() {
    return native.durableTxCount(this._handle);
  }
}

class DB {
  constructor(handle) {
    this._handle = handle;
  }

  query(query, inputs = "[]") {
    return native.dbQueryPrepared(this._handle, query._handle, String(inputs));
  }

  rows(query, inputs = "[]") {
    return native.dbQueryPreparedRows(this._handle, query._handle, String(inputs));
  }

  pull(pattern, entity) {
    return native.pull(this._handle, String(pattern), Number(entity));
  }

  pullLookupRefString(pattern, attr, value) {
    return native.pullLookupRefString(
      this._handle,
      String(pattern),
      String(attr),
      String(value),
    );
  }

  pullMany(pattern, entities) {
    return native.pullMany(this._handle, String(pattern), entities.map(Number));
  }
}

class PreparedQuery {
  constructor(handle) {
    this._handle = handle;
  }

  query(conn, inputs = "[]") {
    return native.queryPrepared(conn._handle, this._handle, String(inputs));
  }

  rows(conn, inputs = "[]") {
    return native.queryPreparedRows(conn._handle, this._handle, String(inputs));
  }

  edn() {
    return native.preparedQueryEdn(this._handle);
  }
}

function openMemory() {
  return new Conn();
}

function connect(uri) {
  return new DurableConn(String(uri));
}

module.exports = {
  Conn,
  DurableConn,
  DB,
  PreparedQuery,
  connect,
  openMemory,
};
