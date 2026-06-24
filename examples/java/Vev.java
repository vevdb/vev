package vev;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.lang.ref.Cleaner;

public final class Vev {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final Cleaner CLEANER = Cleaner.create();

    private final Arena arena;
    private final Cleaner.Cleanable cleanable;
    private final SymbolLookup symbols;

    private final MethodHandle connOpenMemory;
    private final MethodHandle connClose;
    private final MethodHandle connDb;
    private final MethodHandle dbRetain;
    private final MethodHandle dbRelease;
    private final MethodHandle stringFree;
    private final MethodHandle transactEdn;
    private final MethodHandle queryEdnWithInputs;
    private final MethodHandle prepareQueryEdn;
    private final MethodHandle preparedQueryFree;
    private final MethodHandle stmtCreate;
    private final MethodHandle stmtClear;
    private final MethodHandle stmtFree;
    private final MethodHandle stmtBindString;
    private final MethodHandle stmtBindStringCollection;
    private final MethodHandle queryStmtResult;
    private final MethodHandle queryDbStmtResult;
    private final MethodHandle queryPreparedResultWithInputs;
    private final MethodHandle queryDbPreparedResultWithInputs;
    private final MethodHandle resultFree;
    private final MethodHandle resultOk;
    private final MethodHandle resultError;
    private final MethodHandle resultRowCount;
    private final MethodHandle resultValueCount;
    private final MethodHandle resultValue;
    private final MethodHandle resultPullCount;
    private final MethodHandle resultPull;
    private final MethodHandle valueKind;
    private final MethodHandle valueText;
    private final MethodHandle valueEntity;
    private final MethodHandle valueMapCount;
    private final MethodHandle valueMapKey;
    private final MethodHandle valueMapValue;

    public static Vev load(Path libraryPath) {
        return new Vev(libraryPath);
    }

    public Vev(Path libraryPath) {
        this.arena = Arena.ofShared();
        this.cleanable = CLEANER.register(this, arena::close);
        this.symbols = SymbolLookup.libraryLookup(libraryPath, arena);

        this.connOpenMemory = downcall("vev_conn_open_memory", FunctionDescriptor.of(ValueLayout.ADDRESS));
        this.connClose = downcall("vev_conn_close", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        this.connDb = downcall("vev_conn_db", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.dbRetain = downcall("vev_db_retain", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.dbRelease = downcall("vev_db_release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        this.stringFree = downcall("vev_string_free", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        this.transactEdn = downcall("vev_transact_edn", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.queryEdnWithInputs = downcall("vev_query_edn_with_inputs", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.prepareQueryEdn = downcall("vev_prepare_query_edn", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.preparedQueryFree = downcall("vev_prepared_query_free", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        this.stmtCreate = downcall("vev_stmt_create", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.stmtClear = downcall("vev_stmt_clear", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        this.stmtFree = downcall("vev_stmt_free", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        this.stmtBindString = downcall("vev_stmt_bind_string", FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.stmtBindStringCollection = downcall("vev_stmt_bind_string_collection", FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.queryStmtResult = downcall("vev_query_stmt_result", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.queryDbStmtResult = downcall("vev_query_db_stmt_result", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.queryPreparedResultWithInputs = downcall("vev_query_prepared_result_with_inputs", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.queryDbPreparedResultWithInputs = downcall("vev_query_db_prepared_result_with_inputs", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.resultFree = downcall("vev_result_free", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        this.resultOk = downcall("vev_result_ok", FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS));
        this.resultError = downcall("vev_result_error", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.resultRowCount = downcall("vev_result_row_count", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.resultValueCount = downcall("vev_result_value_count", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.resultValue = downcall("vev_result_value", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.resultPullCount = downcall("vev_result_pull_count", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.resultPull = downcall("vev_result_pull", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.valueKind = downcall("vev_value_kind", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.valueText = downcall("vev_value_text", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.valueEntity = downcall("vev_value_entity", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
        this.valueMapCount = downcall("vev_value_map_count", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.valueMapKey = downcall("vev_value_map_key", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.valueMapValue = downcall("vev_value_map_value", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    }

    public Connection openMemory() throws Throwable {
        return createConn();
    }

    public Connection createConn() throws Throwable {
        MemorySegment raw = (MemorySegment) connOpenMemory.invoke();
        if (isNull(raw)) throw new IllegalStateException("failed to open Vev connection");
        return new Connection(raw);
    }

    public PreparedQuery prepare(String query) throws Throwable {
        try (Arena local = Arena.ofConfined()) {
            MemorySegment raw = (MemorySegment) prepareQueryEdn.invoke(local.allocateUtf8String(query));
            if (isNull(raw)) throw new IllegalStateException("failed to prepare query");
            return new PreparedQuery(raw);
        }
    }

    public void close() {
        cleanable.clean();
    }

    private MethodHandle downcall(String name, FunctionDescriptor descriptor) {
        MemorySegment symbol = symbols.find(name).orElseThrow(() -> new IllegalStateException("missing symbol: " + name));
        return LINKER.downcallHandle(symbol, descriptor);
    }

    private String ownedString(MemorySegment ptr) throws Throwable {
        if (isNull(ptr)) return "";
        String out = ptr.reinterpret(Long.MAX_VALUE).getUtf8String(0);
        stringFree.invoke(ptr);
        return out;
    }

    private String textOf(MemorySegment value) throws Throwable {
        return ownedString((MemorySegment) valueText.invoke(value));
    }

    private Object valueToJava(MemorySegment value) throws Throwable {
        int kind = (int) valueKind.invoke(value);
        return switch (kind) {
            case 0 -> null;
            case 1 -> new Entity((long) valueEntity.invoke(value));
            case 2, 6, 7 -> textOf(value);
            case 9 -> {
                int count = (int) valueMapCount.invoke(value);
                List<Entry> entries = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    Object key = valueToJava((MemorySegment) valueMapKey.invoke(value, i));
                    Object item = valueToJava((MemorySegment) valueMapValue.invoke(value, i));
                    entries.add(new Entry(key, item));
                }
                yield new MapValue(entries);
            }
            default -> textOf(value);
        };
    }

    private void closeHandle(MethodHandle handle, MemorySegment segment) {
        try {
            handle.invoke(segment);
        } catch (Throwable error) {
            throw new RuntimeException(error);
        }
    }

    private static boolean isNull(MemorySegment segment) {
        return segment == null || segment.equals(MemorySegment.NULL);
    }

    private static final class NativeHandle implements Runnable {
        private final MethodHandle closeHandle;
        private MemorySegment raw;

        private NativeHandle(MethodHandle closeHandle, MemorySegment raw) {
            this.closeHandle = closeHandle;
            this.raw = raw;
        }

        @Override
        public void run() {
            if (!isNull(raw)) {
                try {
                    closeHandle.invoke(raw);
                } catch (Throwable error) {
                    throw new RuntimeException(error);
                } finally {
                    raw = MemorySegment.NULL;
                }
            }
        }
    }

    public record Entity(long id) {}
    public record Entry(Object key, Object value) {}

    public record MapValue(List<Entry> entries) {
        public Object get(String key) {
            for (Entry entry : entries) {
                if (key.equals(entry.key())) return entry.value();
            }
            return null;
        }
    }

    public final class Connection implements AutoCloseable {
        private MemorySegment raw;

        private Connection(MemorySegment raw) {
            this.raw = raw;
        }

        public String transact(String tx) throws Throwable {
            try (Arena local = Arena.ofConfined()) {
                return ownedString((MemorySegment) transactEdn.invoke(raw, local.allocateUtf8String(tx)));
            }
        }

        public String queryText(String query, String inputs) throws Throwable {
            try (Arena local = Arena.ofConfined()) {
                return ownedString((MemorySegment) queryEdnWithInputs.invoke(raw, local.allocateUtf8String(query), local.allocateUtf8String(inputs)));
            }
        }

        public ResultSet query(PreparedQuery query, String inputs) throws Throwable {
            try (Arena local = Arena.ofConfined()) {
                return new ResultSet((MemorySegment) queryPreparedResultWithInputs.invoke(raw, query.raw, local.allocateUtf8String(inputs)));
            }
        }

        public ResultSet query(Statement stmt) throws Throwable {
            return new ResultSet((MemorySegment) queryStmtResult.invoke(raw, stmt.raw));
        }

        public DB db() throws Throwable {
            MemorySegment db = (MemorySegment) connDb.invoke(raw);
            if (isNull(db)) throw new IllegalStateException("failed to retain DB snapshot");
            return new DB(db);
        }

        @Override
        public void close() {
            if (!isNull(raw)) {
                closeHandle(connClose, raw);
                raw = MemorySegment.NULL;
            }
        }
    }

    public final class DB implements AutoCloseable {
        private final NativeHandle handle;
        private final Cleaner.Cleanable cleanable;

        private DB(MemorySegment raw) {
            this.handle = new NativeHandle(dbRelease, raw);
            this.cleanable = CLEANER.register(this, handle);
        }

        public DB retain() throws Throwable {
            requireOpen();
            MemorySegment retained = (MemorySegment) dbRetain.invoke(handle.raw);
            if (isNull(retained)) throw new IllegalStateException("failed to retain DB snapshot");
            return new DB(retained);
        }

        public ResultSet query(PreparedQuery query, String inputs) throws Throwable {
            requireOpen();
            try (Arena local = Arena.ofConfined()) {
                return new ResultSet((MemorySegment) queryDbPreparedResultWithInputs.invoke(handle.raw, query.raw, local.allocateUtf8String(inputs)));
            }
        }

        public ResultSet query(Statement stmt) throws Throwable {
            requireOpen();
            return new ResultSet((MemorySegment) queryDbStmtResult.invoke(handle.raw, stmt.raw));
        }

        private void requireOpen() {
            if (isNull(handle.raw)) throw new IllegalStateException("DB snapshot is closed");
        }

        @Override
        public void close() {
            cleanable.clean();
        }
    }

    public final class PreparedQuery implements AutoCloseable {
        private MemorySegment raw;

        private PreparedQuery(MemorySegment raw) {
            this.raw = raw;
        }

        public Statement statement() throws Throwable {
            MemorySegment stmt = (MemorySegment) stmtCreate.invoke(raw);
            if (isNull(stmt)) throw new IllegalStateException("failed to create statement");
            return new Statement(stmt);
        }

        @Override
        public void close() {
            if (!isNull(raw)) {
                closeHandle(preparedQueryFree, raw);
                raw = MemorySegment.NULL;
            }
        }
    }

    public final class Statement implements AutoCloseable {
        private MemorySegment raw;

        private Statement(MemorySegment raw) {
            this.raw = raw;
        }

        public Statement bindString(String value) throws Throwable {
            try (Arena local = Arena.ofConfined()) {
                stmtClear.invoke(raw);
                boolean ok = (boolean) stmtBindString.invoke(raw, local.allocateUtf8String(value));
                if (!ok) throw new IllegalStateException("failed to bind string");
                return this;
            }
        }

        public Statement bindStringCollection(String... values) throws Throwable {
            try (Arena local = Arena.ofConfined()) {
                stmtClear.invoke(raw);
                MemorySegment array = local.allocateArray(ValueLayout.ADDRESS, values.length);
                for (int i = 0; i < values.length; i++) {
                    array.setAtIndex(ValueLayout.ADDRESS, i, local.allocateUtf8String(values[i]));
                }
                boolean ok = (boolean) stmtBindStringCollection.invoke(raw, array, values.length);
                if (!ok) throw new IllegalStateException("failed to bind string collection");
                return this;
            }
        }

        @Override
        public void close() {
            if (!isNull(raw)) {
                closeHandle(stmtFree, raw);
                raw = MemorySegment.NULL;
            }
        }
    }

    public final class ResultSet implements AutoCloseable {
        private MemorySegment raw;

        private ResultSet(MemorySegment raw) throws Throwable {
            if (isNull(raw)) throw new IllegalStateException("query returned null result");
            if (!((boolean) resultOk.invoke(raw))) {
                String error = ownedString((MemorySegment) resultError.invoke(raw));
                resultFree.invoke(raw);
                throw new IllegalStateException(error);
            }
            this.raw = raw;
        }

        public int rowCount() throws Throwable {
            return (int) resultRowCount.invoke(raw);
        }

        public List<List<Object>> rows() throws Throwable {
            int rowCount = rowCount();
            List<List<Object>> rows = new ArrayList<>(rowCount);
            for (int row = 0; row < rowCount; row++) {
                List<Object> values = new ArrayList<>();
                int valueCount = (int) resultValueCount.invoke(raw, row);
                for (int column = 0; column < valueCount; column++) {
                    values.add(valueToJava((MemorySegment) resultValue.invoke(raw, row, column)));
                }
                int pullCount = (int) resultPullCount.invoke(raw, row);
                for (int pull = 0; pull < pullCount; pull++) {
                    values.add(valueToJava((MemorySegment) resultPull.invoke(raw, row, pull)));
                }
                rows.add(values);
            }
            return rows;
        }

        public Object scalar() throws Throwable {
            List<List<Object>> rows = rows();
            if (rows.size() != 1 || rows.get(0).size() != 1) {
                throw new IllegalStateException("expected one scalar result, got " + rows);
            }
            return rows.get(0).get(0);
        }

        @Override
        public void close() {
            if (!isNull(raw)) {
                closeHandle(resultFree, raw);
                raw = MemorySegment.NULL;
            }
        }
    }
}
