package vev;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.lang.ref.Cleaner;
import java.util.UUID;

public final class Vev {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final Cleaner CLEANER = Cleaner.create();
    public static final int COLUMN_ENTITY = 1;
    public static final int COLUMN_STRING = 2;
    public static final int COLUMN_INT = 3;
    public static final int COLUMN_BOOL = 5;

    private final Arena arena;
    private final Cleaner.Cleanable cleanable;
    private final SymbolLookup symbols;

    private final MethodHandle connOpenMemory;
    private final MethodHandle connClose;
    private final MethodHandle connDb;
    private final MethodHandle connFromDb;
    private final MethodHandle connectionOpen;
    private final MethodHandle connectionOk;
    private final MethodHandle connectionError;
    private final MethodHandle connectionBackend;
    private final MethodHandle connectionPath;
    private final MethodHandle connectionBasisT;
    private final MethodHandle connectionTxCount;
    private final MethodHandle connectionTxIds;
    private final MethodHandle connectionInfoEdn;
    private final MethodHandle connectionClose;
    private final MethodHandle connectionDb;
    private final MethodHandle connectionTransactEdnReport;
    private final MethodHandle sqliteConnOpen;
    private final MethodHandle sqliteConnOk;
    private final MethodHandle sqliteConnError;
    private final MethodHandle sqliteConnClose;
    private final MethodHandle sqliteConnDb;
    private final MethodHandle sqliteConnTransactEdnReport;
    private final MethodHandle dbRetain;
    private final MethodHandle dbRelease;
    private final MethodHandle withEdn;
    private final MethodHandle withEdnReport;
    private final MethodHandle dbWithEdn;
    private final MethodHandle stringFree;
    private final MethodHandle transactEdn;
    private final MethodHandle transactEdnReport;
    private final MethodHandle txReportFree;
    private final MethodHandle txReportValue;
    private final MethodHandle txReportEdn;
    private final MethodHandle txCreate;
    private final MethodHandle txFree;
    private final MethodHandle txAddString;
    private final MethodHandle txAddKeyword;
    private final MethodHandle txAddSymbol;
    private final MethodHandle txAddEntity;
    private final MethodHandle txAddInt;
    private final MethodHandle txAddBool;
    private final MethodHandle txCommitReport;
    private final MethodHandle txDbWith;
    private final MethodHandle queryEdnWithInputs;
    private final MethodHandle prepareQueryEdn;
    private final MethodHandle preparedQueryFree;
    private final MethodHandle queryPreparedResultWithRulesTextAndInputs;
    private final MethodHandle queryDbPreparedResultWithRulesTextAndInputs;
    private final MethodHandle stmtCreate;
    private final MethodHandle stmtClear;
    private final MethodHandle stmtFree;
    private final MethodHandle stmtBindString;
    private final MethodHandle stmtBindStringCollection;
    private final MethodHandle stmtBindPullPatternEdn;
    private final MethodHandle queryStmtResult;
    private final MethodHandle queryDbStmtResult;
    private final MethodHandle queryPreparedResultWithInputs;
    private final MethodHandle queryDbPreparedResultWithInputs;
    private final MethodHandle queryDbPreparedProfileEdnWithInputs;
    private final MethodHandle queryDbPreparedEntityColumnWithInputs;
    private final MethodHandle u64ArrayFree;
    private final MethodHandle u64ArrayCount;
    private final MethodHandle u64ArrayValue;
    private final MethodHandle u64ArrayData;
    private final MethodHandle queryDbPreparedEntityIntPairsWithInputs;
    private final MethodHandle entityIntPairsFree;
    private final MethodHandle entityIntPairsCount;
    private final MethodHandle entityIntPairsEntity;
    private final MethodHandle entityIntPairsValue;
    private final MethodHandle entityIntPairsEntitiesData;
    private final MethodHandle entityIntPairsValuesData;
    private final MethodHandle queryDbPreparedEntityStringIntTriplesWithInputs;
    private final MethodHandle entityStringIntTriplesFree;
    private final MethodHandle entityStringIntTriplesCount;
    private final MethodHandle entityStringIntTriplesEntitiesData;
    private final MethodHandle entityStringIntTriplesIntsData;
    private final MethodHandle entityStringIntTriplesStringDataArray;
    private final MethodHandle entityStringIntTriplesStringLengthsData;
    private final MethodHandle entityStringIntTriplesStringDictionaryCount;
    private final MethodHandle entityStringIntTriplesStringDictionaryDataArray;
    private final MethodHandle entityStringIntTriplesStringDictionaryLengthsData;
    private final MethodHandle entityStringIntTriplesStringIndicesData;
    private final MethodHandle entityStringIntTriplesString;
    private final MethodHandle entityStringIntTriplesStringData;
    private final MethodHandle entityStringIntTriplesStringLen;
    private final MethodHandle pullEdn;
    private final MethodHandle pullLookupRefStringEdn;
    private final MethodHandle pullLookupRefKeywordEdn;
    private final MethodHandle pullLookupRefEntityEdn;
    private final MethodHandle pullLookupRefIntEdn;
    private final MethodHandle pullManyEdn;
    private final MethodHandle valueHandleFree;
    private final MethodHandle valueHandleValue;
    private final MethodHandle valueHandleEdn;
    private final MethodHandle resultFree;
    private final MethodHandle resultOk;
    private final MethodHandle resultError;
    private final MethodHandle resultRowCount;
    private final MethodHandle resultValueCount;
    private final MethodHandle resultValueKind;
    private final MethodHandle resultValue;
    private final MethodHandle resultPullCount;
    private final MethodHandle resultPull;
    private final MethodHandle resultValueEntity;
    private final MethodHandle resultValueInt;
    private final MethodHandle resultValueBool;
    private final MethodHandle resultValueText;
    private final MethodHandle valueKind;
    private final MethodHandle valueText;
    private final MethodHandle valueEntity;
    private final MethodHandle valueInt;
    private final MethodHandle valueFloat;
    private final MethodHandle valueBool;
    private final MethodHandle valueItemCount;
    private final MethodHandle valueItem;
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
        this.connFromDb = downcall("vev_conn_from_db", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.connectionOpen = downcall("vev_connect", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.connectionOk = downcall("vev_connection_ok", FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS));
        this.connectionError = downcall("vev_connection_error", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.connectionBackend = downcall("vev_connection_backend", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.connectionPath = downcall("vev_connection_path", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.connectionBasisT = downcall("vev_connection_basis_t", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
        this.connectionTxCount = downcall("vev_connection_tx_count", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
        this.connectionTxIds = downcall("vev_connection_tx_ids", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.connectionInfoEdn = downcall("vev_connection_info_edn", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.connectionClose = downcall("vev_connection_close", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        this.connectionDb = downcall("vev_connection_db", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.connectionTransactEdnReport = downcall("vev_connection_transact_edn_report", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.sqliteConnOpen = downcall("vev_sqlite_conn_open", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.sqliteConnOk = downcall("vev_sqlite_conn_ok", FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS));
        this.sqliteConnError = downcall("vev_sqlite_conn_error", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.sqliteConnClose = downcall("vev_sqlite_conn_close", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        this.sqliteConnDb = downcall("vev_sqlite_conn_db", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.sqliteConnTransactEdnReport = downcall("vev_sqlite_conn_transact_edn_report", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.dbRetain = downcall("vev_db_retain", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.dbRelease = downcall("vev_db_release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        this.withEdn = downcall("vev_with_edn", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.withEdnReport = downcall("vev_with_edn_report", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.dbWithEdn = downcall("vev_db_with_edn", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.stringFree = downcall("vev_string_free", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        this.transactEdn = downcall("vev_transact_edn", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.transactEdnReport = downcall("vev_transact_edn_report", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.txReportFree = downcall("vev_tx_report_free", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        this.txReportValue = downcall("vev_tx_report_value", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.txReportEdn = downcall("vev_tx_report_edn", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.txCreate = downcall("vev_tx_create", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.txFree = downcall("vev_tx_free", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        this.txAddString = downcall("vev_tx_add_string", FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.txAddKeyword = downcall("vev_tx_add_keyword", FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.txAddSymbol = downcall("vev_tx_add_symbol", FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.txAddEntity = downcall("vev_tx_add_entity", FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        this.txAddInt = downcall("vev_tx_add_int", FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        this.txAddBool = downcall("vev_tx_add_bool", FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_BOOLEAN));
        this.txCommitReport = downcall("vev_tx_commit_report", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.txDbWith = downcall("vev_tx_db_with", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.queryEdnWithInputs = downcall("vev_query_edn_with_inputs", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.prepareQueryEdn = downcall("vev_prepare_query_edn", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.preparedQueryFree = downcall("vev_prepared_query_free", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        this.queryPreparedResultWithRulesTextAndInputs = downcall("vev_query_prepared_result_with_rules_text_and_inputs", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.queryDbPreparedResultWithRulesTextAndInputs = downcall("vev_query_db_prepared_result_with_rules_text_and_inputs", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.stmtCreate = downcall("vev_stmt_create", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.stmtClear = downcall("vev_stmt_clear", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        this.stmtFree = downcall("vev_stmt_free", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        this.stmtBindString = downcall("vev_stmt_bind_string", FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.stmtBindStringCollection = downcall("vev_stmt_bind_string_collection", FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.stmtBindPullPatternEdn = downcall("vev_stmt_bind_pull_pattern_edn", FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.queryStmtResult = downcall("vev_query_stmt_result", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.queryDbStmtResult = downcall("vev_query_db_stmt_result", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.queryPreparedResultWithInputs = downcall("vev_query_prepared_result_with_inputs", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.queryDbPreparedResultWithInputs = downcall("vev_query_db_prepared_result_with_inputs", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.queryDbPreparedProfileEdnWithInputs = downcall("vev_query_db_prepared_profile_edn_with_inputs", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.queryDbPreparedEntityColumnWithInputs = downcall("vev_query_db_prepared_entity_column_with_inputs", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.u64ArrayFree = downcall("vev_u64_array_free", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        this.u64ArrayCount = downcall("vev_u64_array_count", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.u64ArrayValue = downcall("vev_u64_array_value", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.u64ArrayData = downcall("vev_u64_array_data", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.queryDbPreparedEntityIntPairsWithInputs = downcall("vev_query_db_prepared_entity_int_pairs_with_inputs", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.entityIntPairsFree = downcall("vev_entity_int_pairs_free", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        this.entityIntPairsCount = downcall("vev_entity_int_pairs_count", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.entityIntPairsEntity = downcall("vev_entity_int_pairs_entity", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.entityIntPairsValue = downcall("vev_entity_int_pairs_value", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.entityIntPairsEntitiesData = downcall("vev_entity_int_pairs_entities_data", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.entityIntPairsValuesData = downcall("vev_entity_int_pairs_values_data", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.queryDbPreparedEntityStringIntTriplesWithInputs = downcall("vev_query_db_prepared_entity_string_int_triples_with_inputs", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.entityStringIntTriplesFree = downcall("vev_entity_string_int_triples_free", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        this.entityStringIntTriplesCount = downcall("vev_entity_string_int_triples_count", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.entityStringIntTriplesEntitiesData = downcall("vev_entity_string_int_triples_entities_data", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.entityStringIntTriplesIntsData = downcall("vev_entity_string_int_triples_ints_data", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.entityStringIntTriplesStringDataArray = downcall("vev_entity_string_int_triples_string_data_array", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.entityStringIntTriplesStringLengthsData = downcall("vev_entity_string_int_triples_string_lengths_data", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.entityStringIntTriplesStringDictionaryCount = downcall("vev_entity_string_int_triples_string_dictionary_count", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.entityStringIntTriplesStringDictionaryDataArray = downcall("vev_entity_string_int_triples_string_dictionary_data_array", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.entityStringIntTriplesStringDictionaryLengthsData = downcall("vev_entity_string_int_triples_string_dictionary_lengths_data", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.entityStringIntTriplesStringIndicesData = downcall("vev_entity_string_int_triples_string_indices_data", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.entityStringIntTriplesString = downcall("vev_entity_string_int_triples_string", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.entityStringIntTriplesStringData = downcall("vev_entity_string_int_triples_string_data", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.entityStringIntTriplesStringLen = downcall("vev_entity_string_int_triples_string_len", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.pullEdn = downcall("vev_pull_edn", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        this.pullLookupRefStringEdn = downcall("vev_pull_lookup_ref_string_edn", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.pullLookupRefKeywordEdn = downcall("vev_pull_lookup_ref_keyword_edn", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.pullLookupRefEntityEdn = downcall("vev_pull_lookup_ref_entity_edn", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        this.pullLookupRefIntEdn = downcall("vev_pull_lookup_ref_int_edn", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        this.pullManyEdn = downcall("vev_pull_many_edn", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.valueHandleFree = downcall("vev_value_handle_free", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        this.valueHandleValue = downcall("vev_value_handle_value", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.valueHandleEdn = downcall("vev_value_handle_edn", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.resultFree = downcall("vev_result_free", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        this.resultOk = downcall("vev_result_ok", FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS));
        this.resultError = downcall("vev_result_error", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.resultRowCount = downcall("vev_result_row_count", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.resultValueCount = downcall("vev_result_value_count", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.resultValueKind = downcall("vev_result_value_kind", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.resultValue = downcall("vev_result_value", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.resultPullCount = downcall("vev_result_pull_count", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.resultPull = downcall("vev_result_pull", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.resultValueEntity = downcall("vev_result_value_entity", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.resultValueInt = downcall("vev_result_value_int", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.resultValueBool = downcall("vev_result_value_bool", FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.resultValueText = downcall("vev_result_value_text", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        this.valueKind = downcall("vev_value_kind", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.valueText = downcall("vev_value_text", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.valueEntity = downcall("vev_value_entity", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
        this.valueInt = downcall("vev_value_int", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
        this.valueFloat = downcall("vev_value_float", FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS));
        this.valueBool = downcall("vev_value_bool", FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS));
        this.valueItemCount = downcall("vev_value_item_count", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.valueItem = downcall("vev_value_item", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
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

    public DurableConnection connect(Path path) throws Throwable {
        return connect(path.toString());
    }

    public DurableConnection connect(String uri) throws Throwable {
        try (Arena local = Arena.ofConfined()) {
            MemorySegment raw = (MemorySegment) connectionOpen.invoke(local.allocateUtf8String(uri));
            if (isNull(raw)) throw new IllegalStateException("failed to connect Vev durable connection");
            boolean ok = (boolean) connectionOk.invoke(raw);
            if (!ok) {
                String error = ownedString((MemorySegment) connectionError.invoke(raw));
                closeHandle(connectionClose, raw);
                throw new IllegalStateException(error);
            }
            return new DurableConnection(raw);
        }
    }

    public SQLiteConnection openSqlite(Path path) throws Throwable {
        try (Arena local = Arena.ofConfined()) {
            MemorySegment raw = (MemorySegment) sqliteConnOpen.invoke(local.allocateUtf8String(path.toString()));
            if (isNull(raw)) throw new IllegalStateException("failed to open SQLite-backed Vev connection");
            boolean ok = (boolean) sqliteConnOk.invoke(raw);
            if (!ok) {
                String error = ownedString((MemorySegment) sqliteConnError.invoke(raw));
                closeHandle(sqliteConnClose, raw);
                throw new IllegalStateException(error);
            }
            return new SQLiteConnection(raw);
        }
    }

    public SQLiteConnection openSqlite(String path) throws Throwable {
        return openSqlite(Path.of(path));
    }

    public Connection connFromDb(DB db) throws Throwable {
        db.requireOpen();
        MemorySegment raw = (MemorySegment) connFromDb.invoke(db.handle.raw);
        if (isNull(raw)) throw new IllegalStateException("failed to create Vev connection from DB");
        return new Connection(raw);
    }

    public PreparedQuery prepare(String query) throws Throwable {
        try (Arena local = Arena.ofConfined()) {
            MemorySegment raw = (MemorySegment) prepareQueryEdn.invoke(local.allocateUtf8String(query));
            if (isNull(raw)) throw new IllegalStateException("failed to prepare query");
            return new PreparedQuery(raw);
        }
    }

    public TxBuilder txBuilder(int capacity) throws Throwable {
        MemorySegment raw = (MemorySegment) txCreate.invoke(Math.max(0, capacity));
        if (isNull(raw)) throw new IllegalStateException("failed to create transaction builder");
        return new TxBuilder(raw);
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

    private String borrowedUtf8String(MemorySegment data, int length) {
        if (length <= 0) return "";
        if (isNull(data)) return "";
        byte[] bytes = data.reinterpret(length).toArray(ValueLayout.JAVA_BYTE);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private Object valueToJava(MemorySegment value) throws Throwable {
        int kind = (int) valueKind.invoke(value);
        return switch (kind) {
            case 0 -> null;
            case 1 -> new Entity((long) valueEntity.invoke(value));
            case 3 -> (long) valueInt.invoke(value);
            case 4 -> (double) valueFloat.invoke(value);
            case 5 -> (boolean) valueBool.invoke(value);
            case 2, 6, 7 -> textOf(value);
            case 10 -> UUID.fromString(textOf(value));
            case 8 -> {
                int count = (int) valueItemCount.invoke(value);
                List<Object> items = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    items.add(valueToJava((MemorySegment) valueItem.invoke(value, i)));
                }
                yield items;
            }
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

    private Object resultValueToJava(MemorySegment result, int row, int column, int kind) throws Throwable {
        return switch (kind) {
            case 0 -> null;
            case 1 -> new Entity((long) resultValueEntity.invoke(result, row, column));
            case 2, 6, 7 -> ownedString((MemorySegment) resultValueText.invoke(result, row, column));
            case 10 -> UUID.fromString(ownedString((MemorySegment) resultValueText.invoke(result, row, column)));
            case 3 -> (long) resultValueInt.invoke(result, row, column);
            case 5 -> (boolean) resultValueBool.invoke(result, row, column);
            default -> valueToJava((MemorySegment) resultValue.invoke(result, row, column));
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

    private static MemorySegment longArray(Arena arena, long[] values) {
        MemorySegment array = arena.allocateArray(ValueLayout.JAVA_LONG, values.length);
        for (int i = 0; i < values.length; i++) {
            array.setAtIndex(ValueLayout.JAVA_LONG, i, values[i]);
        }
        return array;
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

    public record ColumnResult(int rowCount, int[] kinds, Object[] columns) {
        public List<List<Object>> rows() {
            List<List<Object>> out = new ArrayList<>(rowCount);
            for (int row = 0; row < rowCount; row++) {
                List<Object> values = new ArrayList<>(kinds.length);
                for (int column = 0; column < kinds.length; column++) {
                    values.add(valueAt(column, row));
                }
                out.add(values);
            }
            return out;
        }

        private Object valueAt(int column, int row) {
            Object values = columns[column];
            return switch (kinds[column]) {
                case COLUMN_ENTITY -> new Entity(((long[]) values)[row]);
                case COLUMN_INT -> ((long[]) values)[row];
                case COLUMN_STRING -> ((String[]) values)[row];
                case COLUMN_BOOL -> ((boolean[]) values)[row];
                default -> throw new IllegalStateException("unsupported column kind: " + kinds[column]);
            };
        }
    }

    public record MapValue(List<Entry> entries) {
        public Object get(String key) {
            for (Entry entry : entries) {
                if (key.equals(entry.key())) return entry.value();
            }
            return null;
        }
    }

    public final class TxReport implements AutoCloseable {
        private MemorySegment raw;

        private TxReport(MemorySegment raw) {
            if (isNull(raw)) throw new IllegalStateException("transaction returned null report");
            this.raw = raw;
        }

        public Object value() throws Throwable {
            requireOpen();
            return valueToJava((MemorySegment) txReportValue.invoke(raw));
        }

        public String edn() throws Throwable {
            requireOpen();
            return ownedString((MemorySegment) txReportEdn.invoke(raw));
        }

        private void requireOpen() {
            if (isNull(raw)) throw new IllegalStateException("transaction report is closed");
        }

        @Override
        public void close() {
            if (!isNull(raw)) {
                closeHandle(txReportFree, raw);
                raw = MemorySegment.NULL;
            }
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

        public TxReport transactReport(String tx) throws Throwable {
            try (Arena local = Arena.ofConfined()) {
                return new TxReport((MemorySegment) transactEdnReport.invoke(raw, local.allocateUtf8String(tx)));
            }
        }

        public TxReport transactReport(TxBuilder tx) throws Throwable {
            tx.requireOpen();
            return new TxReport((MemorySegment) txCommitReport.invoke(raw, tx.raw));
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

        public ResultSet query(PreparedQuery query, String rules, String inputs) throws Throwable {
            try (Arena local = Arena.ofConfined()) {
                return new ResultSet((MemorySegment) queryPreparedResultWithRulesTextAndInputs.invoke(
                    raw,
                    query.raw,
                    local.allocateUtf8String(rules),
                    local.allocateUtf8String(inputs)));
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

    public final class DurableConnection implements AutoCloseable {
        private MemorySegment raw;

        private DurableConnection(MemorySegment raw) {
            this.raw = raw;
        }

        public TxReport transactReport(String tx) throws Throwable {
            requireOpen();
            try (Arena local = Arena.ofConfined()) {
                return new TxReport((MemorySegment) connectionTransactEdnReport.invoke(raw, local.allocateUtf8String(tx)));
            }
        }

        public DB db() throws Throwable {
            requireOpen();
            MemorySegment db = (MemorySegment) connectionDb.invoke(raw);
            if (isNull(db)) throw new IllegalStateException("failed to retain DB snapshot");
            return new DB(db);
        }

        public String backend() throws Throwable {
            requireOpen();
            return ownedString((MemorySegment) connectionBackend.invoke(raw));
        }

        public String path() throws Throwable {
            requireOpen();
            return ownedString((MemorySegment) connectionPath.invoke(raw));
        }

        public long basisT() throws Throwable {
            requireOpen();
            return (long) connectionBasisT.invoke(raw);
        }

        public long txCount() throws Throwable {
            requireOpen();
            return (long) connectionTxCount.invoke(raw);
        }

        public long[] txIds() throws Throwable {
            requireOpen();
            MemorySegment ids = (MemorySegment) connectionTxIds.invoke(raw);
            if (isNull(ids)) return new long[0];
            try {
                int count = (int) u64ArrayCount.invoke(ids);
                if (count == 0) return new long[0];
                MemorySegment data = (MemorySegment) u64ArrayData.invoke(ids);
                if (isNull(data)) return new long[0];
                return data.reinterpret((long) count * Long.BYTES).toArray(ValueLayout.JAVA_LONG);
            } finally {
                u64ArrayFree.invoke(ids);
            }
        }

        public String infoEdn() throws Throwable {
            requireOpen();
            return ownedString((MemorySegment) connectionInfoEdn.invoke(raw));
        }

        private void requireOpen() {
            if (isNull(raw)) throw new IllegalStateException("durable connection is closed");
        }

        @Override
        public void close() {
            if (!isNull(raw)) {
                closeHandle(connectionClose, raw);
                raw = MemorySegment.NULL;
            }
        }
    }

    public final class SQLiteConnection implements AutoCloseable {
        private MemorySegment raw;

        private SQLiteConnection(MemorySegment raw) {
            this.raw = raw;
        }

        public TxReport transactReport(String tx) throws Throwable {
            requireOpen();
            try (Arena local = Arena.ofConfined()) {
                return new TxReport((MemorySegment) sqliteConnTransactEdnReport.invoke(raw, local.allocateUtf8String(tx)));
            }
        }

        public DB db() throws Throwable {
            requireOpen();
            MemorySegment db = (MemorySegment) sqliteConnDb.invoke(raw);
            if (isNull(db)) throw new IllegalStateException("failed to retain DB snapshot");
            return new DB(db);
        }

        private void requireOpen() {
            if (isNull(raw)) throw new IllegalStateException("SQLite-backed connection is closed");
        }

        @Override
        public void close() {
            if (!isNull(raw)) {
                closeHandle(sqliteConnClose, raw);
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

        public String profileEdn(PreparedQuery query, String inputs) throws Throwable {
            requireOpen();
            try (Arena local = Arena.ofConfined()) {
                return ownedString((MemorySegment) queryDbPreparedProfileEdnWithInputs.invoke(
                    handle.raw,
                    query.raw,
                    local.allocateUtf8String(inputs)));
            }
        }

        public ResultSet query(PreparedQuery query, String rules, String inputs) throws Throwable {
            requireOpen();
            try (Arena local = Arena.ofConfined()) {
                return new ResultSet((MemorySegment) queryDbPreparedResultWithRulesTextAndInputs.invoke(
                    handle.raw,
                    query.raw,
                    local.allocateUtf8String(rules),
                    local.allocateUtf8String(inputs)));
            }
        }

        public long[] queryEntityColumn(PreparedQuery query, String inputs) throws Throwable {
            requireOpen();
            try (Arena local = Arena.ofConfined()) {
                MemorySegment raw = (MemorySegment) queryDbPreparedEntityColumnWithInputs.invoke(
                    handle.raw,
                    query.raw,
                    local.allocateUtf8String(inputs));
                if (isNull(raw)) return null;
                try {
                    int count = (int) u64ArrayCount.invoke(raw);
                    if (count == 0) return new long[0];
                    MemorySegment data = (MemorySegment) u64ArrayData.invoke(raw);
                    if (isNull(data)) return new long[0];
                    return data.reinterpret((long) count * Long.BYTES).toArray(ValueLayout.JAVA_LONG);
                } finally {
                    u64ArrayFree.invoke(raw);
                }
            }
        }

        public long[][] queryEntityIntPairs(PreparedQuery query, String inputs) throws Throwable {
            requireOpen();
            try (Arena local = Arena.ofConfined()) {
                MemorySegment raw = (MemorySegment) queryDbPreparedEntityIntPairsWithInputs.invoke(
                    handle.raw,
                    query.raw,
                    local.allocateUtf8String(inputs));
                if (isNull(raw)) return null;
                try {
                    int count = (int) entityIntPairsCount.invoke(raw);
                    if (count == 0) return new long[0][2];
                    MemorySegment entityData = (MemorySegment) entityIntPairsEntitiesData.invoke(raw);
                    MemorySegment valueData = (MemorySegment) entityIntPairsValuesData.invoke(raw);
                    if (isNull(entityData) || isNull(valueData)) return new long[0][2];
                    long[] entities = entityData.reinterpret((long) count * Long.BYTES).toArray(ValueLayout.JAVA_LONG);
                    long[] values = valueData.reinterpret((long) count * Long.BYTES).toArray(ValueLayout.JAVA_LONG);
                    long[][] out = new long[count][2];
                    for (int index = 0; index < count; index++) {
                        out[index][0] = entities[index];
                        out[index][1] = values[index];
                    }
                    return out;
                } finally {
                    entityIntPairsFree.invoke(raw);
                }
            }
        }

        public long[][] queryEntityIntPairColumns(PreparedQuery query, String inputs) throws Throwable {
            requireOpen();
            try (Arena local = Arena.ofConfined()) {
                MemorySegment raw = (MemorySegment) queryDbPreparedEntityIntPairsWithInputs.invoke(
                    handle.raw,
                    query.raw,
                    local.allocateUtf8String(inputs));
                if (isNull(raw)) return null;
                try {
                    int count = (int) entityIntPairsCount.invoke(raw);
                    if (count == 0) return new long[][] { new long[0], new long[0] };
                    MemorySegment entityData = (MemorySegment) entityIntPairsEntitiesData.invoke(raw);
                    MemorySegment valueData = (MemorySegment) entityIntPairsValuesData.invoke(raw);
                    if (isNull(entityData) || isNull(valueData)) return new long[][] { new long[0], new long[0] };
                    long[] entities = entityData.reinterpret((long) count * Long.BYTES).toArray(ValueLayout.JAVA_LONG);
                    long[] values = valueData.reinterpret((long) count * Long.BYTES).toArray(ValueLayout.JAVA_LONG);
                    return new long[][] { entities, values };
                } finally {
                    entityIntPairsFree.invoke(raw);
                }
            }
        }

        public Object[] queryEntityStringIntTripleColumns(PreparedQuery query, String inputs) throws Throwable {
            requireOpen();
            try (Arena local = Arena.ofConfined()) {
                MemorySegment raw = (MemorySegment) queryDbPreparedEntityStringIntTriplesWithInputs.invoke(
                    handle.raw,
                    query.raw,
                    local.allocateUtf8String(inputs));
                if (isNull(raw)) return null;
                try {
                    int count = (int) entityStringIntTriplesCount.invoke(raw);
                    if (count == 0) return new Object[] { new long[0], new String[0], new long[0] };
                    MemorySegment entityData = (MemorySegment) entityStringIntTriplesEntitiesData.invoke(raw);
                    MemorySegment intData = (MemorySegment) entityStringIntTriplesIntsData.invoke(raw);
                    if (isNull(entityData) || isNull(intData)) return new Object[] { new long[0], new String[0], new long[0] };
                    long[] entities = entityData.reinterpret((long) count * Long.BYTES).toArray(ValueLayout.JAVA_LONG);
                    long[] ints = intData.reinterpret((long) count * Long.BYTES).toArray(ValueLayout.JAVA_LONG);
                    String[] strings = new String[count];
                    int dictionaryCount = (int) entityStringIntTriplesStringDictionaryCount.invoke(raw);
                    MemorySegment dictionaryDataArray = (MemorySegment) entityStringIntTriplesStringDictionaryDataArray.invoke(raw);
                    MemorySegment dictionaryLengthsData = (MemorySegment) entityStringIntTriplesStringDictionaryLengthsData.invoke(raw);
                    MemorySegment stringIndicesData = (MemorySegment) entityStringIntTriplesStringIndicesData.invoke(raw);
                    if (dictionaryCount > 0
                            && !isNull(dictionaryDataArray)
                            && !isNull(dictionaryLengthsData)
                            && !isNull(stringIndicesData)) {
                        MemorySegment dataArray = dictionaryDataArray.reinterpret((long) dictionaryCount * ValueLayout.ADDRESS.byteSize());
                        int[] lengths = dictionaryLengthsData.reinterpret((long) dictionaryCount * Integer.BYTES).toArray(ValueLayout.JAVA_INT);
                        int[] indices = stringIndicesData.reinterpret((long) count * Integer.BYTES).toArray(ValueLayout.JAVA_INT);
                        String[] dictionary = new String[dictionaryCount];
                        for (int index = 0; index < dictionaryCount; index++) {
                            MemorySegment data = dataArray.get(ValueLayout.ADDRESS, (long) index * ValueLayout.ADDRESS.byteSize());
                            dictionary[index] = borrowedUtf8String(data, lengths[index]);
                        }
                        for (int index = 0; index < count; index++) {
                            strings[index] = dictionary[indices[index]];
                        }
                    } else {
                        MemorySegment stringDataArray = (MemorySegment) entityStringIntTriplesStringDataArray.invoke(raw);
                        MemorySegment stringLengthsData = (MemorySegment) entityStringIntTriplesStringLengthsData.invoke(raw);
                        if (!isNull(stringDataArray) && !isNull(stringLengthsData)) {
                            MemorySegment dataArray = stringDataArray.reinterpret((long) count * ValueLayout.ADDRESS.byteSize());
                            int[] lengths = stringLengthsData.reinterpret((long) count * Integer.BYTES).toArray(ValueLayout.JAVA_INT);
                            for (int index = 0; index < count; index++) {
                                MemorySegment data = dataArray.get(ValueLayout.ADDRESS, (long) index * ValueLayout.ADDRESS.byteSize());
                                strings[index] = borrowedUtf8String(data, lengths[index]);
                            }
                        } else {
                            for (int index = 0; index < count; index++) {
                                int length = (int) entityStringIntTriplesStringLen.invoke(raw, index);
                                MemorySegment data = (MemorySegment) entityStringIntTriplesStringData.invoke(raw, index);
                                strings[index] = borrowedUtf8String(data, length);
                            }
                        }
                    }
                    return new Object[] { entities, strings, ints };
                } finally {
                    entityStringIntTriplesFree.invoke(raw);
                }
            }
        }

        public ColumnResult queryColumns(PreparedQuery query, String inputs) throws Throwable {
            long[] entities = queryEntityColumn(query, inputs);
            if (entities != null) {
                return new ColumnResult(
                    entities.length,
                    new int[] { COLUMN_ENTITY },
                    new Object[] { entities });
            }
            long[][] pairs = queryEntityIntPairColumns(query, inputs);
            if (pairs != null) {
                return new ColumnResult(
                    pairs[0].length,
                    new int[] { COLUMN_ENTITY, COLUMN_INT },
                    new Object[] { pairs[0], pairs[1] });
            }
            Object[] triples = queryEntityStringIntTripleColumns(query, inputs);
            if (triples != null) {
                long[] tripleEntities = (long[]) triples[0];
                return new ColumnResult(
                    tripleEntities.length,
                    new int[] { COLUMN_ENTITY, COLUMN_STRING, COLUMN_INT },
                    triples);
            }
            return null;
        }

        public ResultSet query(Statement stmt) throws Throwable {
            requireOpen();
            return new ResultSet((MemorySegment) queryDbStmtResult.invoke(handle.raw, stmt.raw));
        }

        public String with(String tx) throws Throwable {
            requireOpen();
            try (Arena local = Arena.ofConfined()) {
                return ownedString((MemorySegment) withEdn.invoke(handle.raw, local.allocateUtf8String(tx)));
            }
        }

        public TxReport withReport(String tx) throws Throwable {
            requireOpen();
            try (Arena local = Arena.ofConfined()) {
                return new TxReport((MemorySegment) withEdnReport.invoke(handle.raw, local.allocateUtf8String(tx)));
            }
        }

        public DB dbWith(String tx) throws Throwable {
            requireOpen();
            try (Arena local = Arena.ofConfined()) {
                MemorySegment db = (MemorySegment) dbWithEdn.invoke(handle.raw, local.allocateUtf8String(tx));
                if (isNull(db)) throw new IllegalStateException("failed to create DB snapshot");
                return new DB(db);
            }
        }

        public DB dbWith(TxBuilder tx) throws Throwable {
            requireOpen();
            tx.requireOpen();
            MemorySegment db = (MemorySegment) txDbWith.invoke(handle.raw, tx.raw);
            if (isNull(db)) throw new IllegalStateException("failed to create DB snapshot");
            return new DB(db);
        }

        public Object pull(String pattern, long entity) throws Throwable {
            requireOpen();
            try (Arena local = Arena.ofConfined();
                 ValueHandle value = new ValueHandle((MemorySegment) pullEdn.invoke(
                     handle.raw,
                     local.allocateUtf8String(pattern),
                     entity))) {
                return value.value();
            }
        }

        public Object pullLookupRefString(String pattern, String attr, String value) throws Throwable {
            requireOpen();
            try (Arena local = Arena.ofConfined();
                 ValueHandle pulled = new ValueHandle((MemorySegment) pullLookupRefStringEdn.invoke(
                     handle.raw,
                     local.allocateUtf8String(pattern),
                     local.allocateUtf8String(attr),
                     local.allocateUtf8String(value)))) {
                return pulled.value();
            }
        }

        public Object pullLookupRefKeyword(String pattern, String attr, String value) throws Throwable {
            requireOpen();
            try (Arena local = Arena.ofConfined();
                 ValueHandle pulled = new ValueHandle((MemorySegment) pullLookupRefKeywordEdn.invoke(
                     handle.raw,
                     local.allocateUtf8String(pattern),
                     local.allocateUtf8String(attr),
                     local.allocateUtf8String(value)))) {
                return pulled.value();
            }
        }

        public Object pullLookupRefEntity(String pattern, String attr, long value) throws Throwable {
            requireOpen();
            try (Arena local = Arena.ofConfined();
                 ValueHandle pulled = new ValueHandle((MemorySegment) pullLookupRefEntityEdn.invoke(
                     handle.raw,
                     local.allocateUtf8String(pattern),
                     local.allocateUtf8String(attr),
                     value))) {
                return pulled.value();
            }
        }

        public Object pullLookupRefInt(String pattern, String attr, long value) throws Throwable {
            requireOpen();
            try (Arena local = Arena.ofConfined();
                 ValueHandle pulled = new ValueHandle((MemorySegment) pullLookupRefIntEdn.invoke(
                     handle.raw,
                     local.allocateUtf8String(pattern),
                     local.allocateUtf8String(attr),
                     value))) {
                return pulled.value();
            }
        }

        public Object pullMany(String pattern, long... entities) throws Throwable {
            requireOpen();
            try (Arena local = Arena.ofConfined();
                 ValueHandle value = new ValueHandle((MemorySegment) pullManyEdn.invoke(
                     handle.raw,
                     local.allocateUtf8String(pattern),
                     longArray(local, entities),
                     entities.length))) {
                return value.value();
            }
        }

        private void requireOpen() {
            if (isNull(handle.raw)) throw new IllegalStateException("DB snapshot is closed");
        }

        @Override
        public void close() {
            cleanable.clean();
        }
    }

    public final class ValueHandle implements AutoCloseable {
        private MemorySegment raw;

        private ValueHandle(MemorySegment raw) {
            if (isNull(raw)) throw new IllegalStateException("value handle is null");
            this.raw = raw;
        }

        public Object value() throws Throwable {
            requireOpen();
            return valueToJava((MemorySegment) valueHandleValue.invoke(raw));
        }

        public String edn() throws Throwable {
            requireOpen();
            return ownedString((MemorySegment) valueHandleEdn.invoke(raw));
        }

        private void requireOpen() {
            if (isNull(raw)) throw new IllegalStateException("value handle is closed");
        }

        @Override
        public void close() {
            if (!isNull(raw)) {
                closeHandle(valueHandleFree, raw);
                raw = MemorySegment.NULL;
            }
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

    public final class TxBuilder implements AutoCloseable {
        private MemorySegment raw;

        private TxBuilder(MemorySegment raw) {
            this.raw = raw;
        }

        public TxBuilder addString(long e, String attr, String value) throws Throwable {
            try (Arena local = Arena.ofConfined()) {
                boolean ok = (boolean) txAddString.invoke(raw, e, local.allocateUtf8String(attr), local.allocateUtf8String(value));
                if (!ok) throw new IllegalStateException("failed to add string tx datom");
                return this;
            }
        }

        public TxBuilder addKeyword(long e, String attr, String value) throws Throwable {
            try (Arena local = Arena.ofConfined()) {
                boolean ok = (boolean) txAddKeyword.invoke(raw, e, local.allocateUtf8String(attr), local.allocateUtf8String(value));
                if (!ok) throw new IllegalStateException("failed to add keyword tx datom");
                return this;
            }
        }

        public TxBuilder addSymbol(long e, String attr, String value) throws Throwable {
            try (Arena local = Arena.ofConfined()) {
                boolean ok = (boolean) txAddSymbol.invoke(raw, e, local.allocateUtf8String(attr), local.allocateUtf8String(value));
                if (!ok) throw new IllegalStateException("failed to add symbol tx datom");
                return this;
            }
        }

        public TxBuilder addEntity(long e, String attr, long value) throws Throwable {
            try (Arena local = Arena.ofConfined()) {
                boolean ok = (boolean) txAddEntity.invoke(raw, e, local.allocateUtf8String(attr), value);
                if (!ok) throw new IllegalStateException("failed to add entity tx datom");
                return this;
            }
        }

        public TxBuilder addInt(long e, String attr, long value) throws Throwable {
            try (Arena local = Arena.ofConfined()) {
                boolean ok = (boolean) txAddInt.invoke(raw, e, local.allocateUtf8String(attr), value);
                if (!ok) throw new IllegalStateException("failed to add int tx datom");
                return this;
            }
        }

        public TxBuilder addBool(long e, String attr, boolean value) throws Throwable {
            try (Arena local = Arena.ofConfined()) {
                boolean ok = (boolean) txAddBool.invoke(raw, e, local.allocateUtf8String(attr), value);
                if (!ok) throw new IllegalStateException("failed to add bool tx datom");
                return this;
            }
        }

        private void requireOpen() {
            if (isNull(raw)) throw new IllegalStateException("transaction builder is closed");
        }

        @Override
        public void close() {
            if (!isNull(raw)) {
                closeHandle(txFree, raw);
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

        public Statement bindPullPatternAndString(String pattern, String value) throws Throwable {
            try (Arena local = Arena.ofConfined()) {
                stmtClear.invoke(raw);
                boolean ok = (boolean) stmtBindPullPatternEdn.invoke(raw, local.allocateUtf8String(pattern));
                ok = ok && (boolean) stmtBindString.invoke(raw, local.allocateUtf8String(value));
                if (!ok) throw new IllegalStateException("failed to bind pull pattern and string");
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

        public long[] singleEntityColumn() throws Throwable {
            int rowCount = rowCount();
            if (rowCount == 0) return new long[0];
            if ((int) resultValueCount.invoke(raw, 0) != 1
                    || (int) resultPullCount.invoke(raw, 0) != 0
                    || (int) resultValueKind.invoke(raw, 0, 0) != 1) {
                return null;
            }
            long[] out = new long[rowCount];
            for (int row = 0; row < rowCount; row++) {
                out[row] = (long) resultValueEntity.invoke(raw, row, 0);
            }
            return out;
        }

        public List<List<Object>> rows() throws Throwable {
            int rowCount = rowCount();
            List<List<Object>> rows = new ArrayList<>(rowCount);
            long[] entityColumn = singleEntityColumn();
            if (entityColumn != null && rowCount > 0) {
                for (long entity : entityColumn) {
                    List<Object> values = new ArrayList<>(1);
                    values.add(new Entity(entity));
                    rows.add(values);
                }
                return rows;
            }
            for (int row = 0; row < rowCount; row++) {
                List<Object> values = new ArrayList<>();
                int valueCount = (int) resultValueCount.invoke(raw, row);
                for (int column = 0; column < valueCount; column++) {
                    int kind = (int) resultValueKind.invoke(raw, row, column);
                    values.add(resultValueToJava(raw, row, column, kind));
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
