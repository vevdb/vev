;; Copyright (c) Andreas Flakstad and Vev contributors
;; SPDX-License-Identifier: EPL-2.0

(ns vev.core
  (:require [clojure.edn :as edn])
  (:import [java.nio.file Path]
           [dev.vevdb.vev Vev Vev$ColumnResult Vev$Entity Vev$MapValue Vev$PreparedPullPattern]))

(defn- path [value]
  (cond
    (instance? Path value) value
    (string? value) (Path/of value (make-array String 0))
    :else (throw (ex-info "expected native library path" {:value value}))))

(defn- edn-text [value]
  (cond
    (string? value) value
    (keyword? value) (str value)
    (symbol? value) (str value)
    :else
    (binding [*print-namespace-maps* false]
      (pr-str value))))

(defn- inputs-text [inputs]
  (binding [*print-namespace-maps* false]
    (pr-str (vec inputs))))

(defn- load-engine
  ([]
   (Vev/load))
  ([lib-path]
   (Vev/load (path lib-path))))

(defn- keyword-text? [value]
  (and (string? value)
       (.startsWith ^String value ":")))

(defn- key-value [value]
  (if (keyword-text? value)
    (keyword (subs value 1))
    value))

(defn- clj-value [value]
  (cond
    (instance? Vev$Entity value)
    (.id ^Vev$Entity value)

    (instance? Vev$MapValue value)
    (persistent!
     (reduce (fn [out entry]
               (assoc! out
                       (key-value (clj-value (.key entry)))
                       (clj-value (.value entry))))
             (transient {})
             (.entries ^Vev$MapValue value)))

    (instance? java.util.List value)
    (mapv clj-value value)

    :else
    value))

(defrecord Conn [^Vev engine native]
  java.lang.AutoCloseable
  (close [_]
    (.close ^java.lang.AutoCloseable native)))

(defrecord DurableConn [^Vev engine native]
  java.lang.AutoCloseable
  (close [_]
    (.close ^java.lang.AutoCloseable native)))

(defrecord SQLiteConn [^Vev engine native]
  java.lang.AutoCloseable
  (close [_]
    (.close ^java.lang.AutoCloseable native)))

(defrecord DB [^Vev engine native]
  java.lang.AutoCloseable
  (close [_]
    (.close ^java.lang.AutoCloseable native)))

(defn retain-db
  "Return another owned handle to the same immutable DB value."
  [^DB db]
  (->DB (:engine db) (.retain (:native db))))

(defrecord PreparedQuery [^Vev engine native]
  java.lang.AutoCloseable
  (close [_]
    (.close ^java.lang.AutoCloseable native)))

(defrecord PreparedPullPattern [^Vev engine native]
  java.lang.AutoCloseable
  (close [_]
    (.close ^java.lang.AutoCloseable native)))

(defrecord TxBuilder [^Vev engine native]
  java.lang.AutoCloseable
  (close [_]
    (.close ^java.lang.AutoCloseable native)))

(defn create-conn
  "Create an in-memory Vev connection.

  With no arguments, uses the Java wrapper's native library resolution:
  `vev.library`, `VEV_LIB`, local `build/lib`, then bundled native resource."
  ([]
   (let [engine (load-engine)]
     (try
       (->Conn engine (.createConn engine))
       (catch Throwable error
         (.close engine)
         (throw error)))))
  ([lib-path]
   (let [engine (load-engine lib-path)]
     (try
       (->Conn engine (.createConn engine))
       (catch Throwable error
         (.close engine)
         (throw error))))))

(def open create-conn)

(defn connect
  "Open a durable Vev connection.

  The current backend is SQLite. A plain filesystem path and `sqlite://...` URI
  both select the SQLite backend."
  ([uri]
   (let [engine (load-engine)]
     (try
       (->DurableConn engine (.connect engine (str uri)))
       (catch Throwable error
         (.close engine)
         (throw error)))))
  ([lib-path uri]
   (let [engine (load-engine lib-path)]
     (try
       (->DurableConn engine (.connect engine (str uri)))
       (catch Throwable error
         (.close engine)
         (throw error))))))

(defn open-sqlite
  "Open a durable SQLite-backed Vev connection.

  Prefer `connect` for application code; this backend-specific alias remains
  for compatibility and storage tests."
  ([sqlite-path]
   (connect sqlite-path))
  ([lib-path sqlite-path]
   (connect lib-path sqlite-path)))

(defn db
  "Return an immutable DB snapshot from a connection."
  [conn]
  (cond
    (instance? Conn conn)
    (->DB (:engine conn) (.db (:native conn)))

    (instance? DurableConn conn)
    (->DB (:engine conn) (.db (:native conn)))

    (instance? SQLiteConn conn)
    (->DB (:engine conn) (.db (:native conn)))

    :else
    (throw (ex-info "expected Vev connection" {:source conn}))))

(defn connection-info
  "Return storage metadata for a durable connection."
  [conn]
  (cond
    (instance? DurableConn conn)
    {:backend (keyword (.backend (:native conn)))
     :path (.path (:native conn))
     :basis-t (.basisT (:native conn))
     :tx-count (.txCount (:native conn))
     :tx-ids (vec (.txIds (:native conn)))}

    :else
    (throw (ex-info "expected Vev durable connection" {:source conn}))))

(defn conn-from-db
  "Create a mutable connection initialized from an immutable DB value."
  [^DB db]
  (->Conn (:engine db) (.connFromDb (:engine db) (:native db))))

(defn entity
  "Create an explicit entity value for typed native APIs."
  [id]
  (Vev$Entity. (long id)))

(defn transact-text!
  "Transact Clojure data or EDN text and return the raw EDN report text."
  [^Conn conn tx]
  (.transact (:native conn) (edn-text tx)))

(defn transact!
  "Transact Clojure data or EDN text against a connection.

  Returns a Clojure transaction report map."
  [conn tx]
  (with-open [report (if (instance? TxBuilder tx)
                       (.transactReport (:native conn) (:native tx))
                       (.transactReport (:native conn) (edn-text tx)))]
    (clj-value (.value report))))

(def transact transact!)

(defn empty-db
  "Return an owned immutable empty DB value."
  ([]
   (with-open [conn (create-conn)]
     (db conn)))
  ([lib-path]
   (with-open [conn (create-conn lib-path)]
     (db conn))))

(defn with-text
  "Apply EDN tx text to an immutable DB and return the raw EDN report text."
  [^DB db tx]
  (.with (:native db) (edn-text tx)))

(defn with
  "Apply tx data to an immutable DB and return a transaction report map."
  [^DB db tx]
  (with-open [report (.withReport (:native db) (edn-text tx))]
    (clj-value (.value report))))

(defn db-with
  "Apply tx data to an immutable DB and return the resulting immutable DB value."
  [^DB db tx]
  (if (instance? TxBuilder tx)
    (->DB (:engine db) (.dbWith (:native db) (:native tx)))
    (->DB (:engine db) (.dbWith (:native db) (edn-text tx)))))

(defn tx-builder
  "Create a native transaction builder for direct typed bulk tx construction."
  ([source]
   (tx-builder source 0))
  ([source capacity]
   (let [engine (:engine source)]
     (->TxBuilder engine (.txBuilder engine (int capacity))))))

(defn- attr-text [attr]
  (cond
    (keyword? attr) (str attr)
    (string? attr) attr
    :else (throw (ex-info "expected tx attr keyword or string" {:attr attr}))))

(defn tx-add!
  "Append one :db/add datom to a native transaction builder."
  [^TxBuilder tx e attr value]
  (let [native (:native tx)
        e (long e)
        attr (attr-text attr)]
    (cond
      (string? value)
      (.addString native e attr value)

      (keyword? value)
      (.addKeyword native e attr (str value))

      (integer? value)
      (.addInt native e attr (long value))

      (boolean? value)
      (.addBool native e attr value)

      (instance? Vev$Entity value)
      (.addEntity native e attr (.id ^Vev$Entity value))

      :else
      (throw (ex-info "unsupported native tx builder value" {:value value}))))
  tx)

(defn bulk-add!
  "Append Datomic/DataScript-style entity maps to a native transaction builder."
  [^TxBuilder tx entities]
  (doseq [entity entities]
    (let [e (:db/id entity)]
      (when-not (integer? e)
        (throw (ex-info "bulk entity map requires integer :db/id" {:entity entity})))
      (doseq [[attr value] entity]
        (when-not (= attr :db/id)
          (tx-add! tx e attr value)))))
  tx)

(defn init-db
  "Create an immutable DB initialized by applying tx data to an empty DB."
  ([tx]
   (with-open [initial (empty-db)]
     (db-with initial tx)))
  ([lib-path tx]
   (with-open [initial (empty-db lib-path)]
     (db-with initial tx))))

(defn prepare
  "Prepare a query from Clojure data or EDN text."
  [source query]
  (let [engine (:engine source)]
    (->PreparedQuery engine (.prepare engine (edn-text query)))))

(defn prepared-edn
  "Return the portable EDN-ish parser value for a prepared query or pull pattern."
  [prepared]
  (edn/read-string
   (cond
     (instance? PreparedQuery prepared)
     (.edn (:native prepared))

     (instance? PreparedPullPattern prepared)
     (.edn (:native prepared))

     :else
     (throw (ex-info "prepared-edn expects a prepared query or pull pattern"
                     {:value prepared})))))

(defn prepare-pull-pattern
  "Prepare a pull pattern from Clojure data or EDN text."
  [source pattern]
  (let [engine (:engine source)]
    (->PreparedPullPattern engine (.preparePullPattern engine (edn-text pattern)))))

(defn- source? [value]
  (or (instance? Conn value)
      (instance? SQLiteConn value)
      (instance? DB value)))

(defn- normalize-query-call [first-arg second-arg inputs]
  (if (source? first-arg)
    {:source first-arg :query second-arg :inputs inputs}
    {:source second-arg :query first-arg :inputs inputs}))

(defn- query-in-forms [query]
  (cond
    (map? query)
    (:in query)

    (vector? query)
    (let [tail (drop-while #(not= :in %) query)]
      (when (seq tail)
        (take-while #(not (#{:where :with :keys :strs :syms} %)) (rest tail))))

    :else
    nil))

(defn- marker-key [marker value]
  (case marker
    :keys (cond
            (keyword? value) value
            (symbol? value) (keyword (name value))
            (string? value) (keyword value)
            :else value)
    :strs (cond
            (keyword? value) (name value)
            (symbol? value) (name value)
            :else value)
    :syms (cond
            (keyword? value) (symbol (name value))
            (string? value) (symbol value)
            :else value)))

(defn- query-return-map [query]
  (let [from-map (fn [query]
                   (some (fn [marker]
                           (when-let [items (get query marker)]
                             {:marker marker
                              :keys (mapv #(marker-key marker %) items)}))
                         [:keys :strs :syms]))
        from-vector (fn [query]
                      (loop [items (seq query)]
                        (when-let [item (first items)]
                          (if (#{:keys :strs :syms} item)
                            {:marker item
                             :keys (mapv #(marker-key item %)
                                         (take-while #(not (#{:in :where :with} %))
                                                     (rest items)))}
                            (recur (rest items))))))]
    (cond
      (map? query) (from-map query)
      (vector? query) (from-vector query)
      (string? query) (try
                        (query-return-map (edn/read-string query))
                        (catch Exception _ nil))
      :else nil)))

(defn- keyed-rows [return-map rows]
  (let [keys (:keys return-map)]
    (mapv (fn [row] (zipmap keys row)) rows)))

(defn- keyed-set [return-map rows]
  (set (keyed-rows return-map rows)))

(defn- split-rules-input [query inputs]
  (let [in-forms (query-in-forms query)
        inputv (vec inputs)]
    (if-not (seq in-forms)
      {:inputs inputs}
      (loop [forms in-forms
             input-index 0]
        (if-let [form (first forms)]
          (cond
            (= '$ form)
            (recur (rest forms) input-index)

            (= '% form)
            (if (< input-index (count inputv))
              {:rules (nth inputv input-index)
               :inputs (vec (concat (subvec inputv 0 input-index)
                                    (subvec inputv (inc input-index))))}
              {:inputs inputs})

            :else
            (recur (rest forms) (inc input-index)))
          {:inputs inputs})))))

(defn query-result
  "Run a prepared query and return the native result handle. Caller owns it."
  [source ^PreparedQuery prepared & inputs]
  (let [input-edn (inputs-text inputs)]
    (cond
      (instance? Conn source)
      (.query (:native source) (:native prepared) input-edn)

      (instance? SQLiteConn source)
      (with-open [snapshot (db source)]
        (.query (:native snapshot) (:native prepared) input-edn))

      (instance? DB source)
      (.query (:native source) (:native prepared) input-edn)

      :else
      (throw (ex-info "expected Vev connection or DB" {:source source})))))

(defn query-result-with-rules
  "Run a prepared query with rules and return the native result handle. Caller owns it."
  [source ^PreparedQuery prepared rules & inputs]
  (let [rules-edn (edn-text rules)
        input-edn (inputs-text inputs)]
    (cond
      (instance? Conn source)
      (.query (:native source) (:native prepared) rules-edn input-edn)

      (instance? SQLiteConn source)
      (with-open [snapshot (db source)]
        (.query (:native snapshot) (:native prepared) rules-edn input-edn))

      (instance? DB source)
      (.query (:native source) (:native prepared) rules-edn input-edn)

      :else
      (throw (ex-info "expected Vev connection or DB" {:source source})))))

(defn- single-entity-rows [result]
  (when-let [ids (.singleEntityColumn result)]
    (let [^longs ids ids
          n (alength ids)]
      (loop [index 0
             out (transient [])]
        (if (< index n)
          (recur (inc index)
                 (conj! out [(long (aget ids index))]))
          (persistent! out))))))

(defn- rows-from-result [result]
  (or (single-entity-rows result)
      (mapv clj-value (.rows result))))

(defn- q-from-result [result]
  (if-let [ids (.singleEntityColumn result)]
    (let [^longs ids ids
          n (alength ids)]
      (loop [index 0
             out (transient #{})]
        (if (< index n)
          (recur (inc index)
                 (conj! out [(long (aget ids index))]))
          (persistent! out))))
    (set (mapv clj-value (.rows result)))))

(defn- entity-column [source ^PreparedQuery prepared inputs]
  (let [input-edn (inputs-text inputs)]
    (cond
      (instance? DB source)
      (.queryEntityColumn (:native source) (:native prepared) input-edn)

      (instance? Conn source)
      (with-open [snapshot (db source)]
        (.queryEntityColumn (:native snapshot) (:native prepared) input-edn))

      (instance? SQLiteConn source)
      (with-open [snapshot (db source)]
        (.queryEntityColumn (:native snapshot) (:native prepared) input-edn))

      :else
      nil)))

(defn- string-column [source ^PreparedQuery prepared inputs]
  (let [input-edn (inputs-text inputs)]
    (cond
      (instance? DB source)
      (.queryStringColumn (:native source) (:native prepared) input-edn)

      (instance? Conn source)
      (with-open [snapshot (db source)]
        (.queryStringColumn (:native snapshot) (:native prepared) input-edn))

      (instance? SQLiteConn source)
      (with-open [snapshot (db source)]
        (.queryStringColumn (:native snapshot) (:native prepared) input-edn))

      :else
      nil)))

(defn- entity-int-pair-columns [source ^PreparedQuery prepared inputs]
  (let [input-edn (inputs-text inputs)]
    (cond
      (instance? DB source)
      (.queryEntityIntPairColumns (:native source) (:native prepared) input-edn)

      (instance? Conn source)
      (with-open [snapshot (db source)]
        (.queryEntityIntPairColumns (:native snapshot) (:native prepared) input-edn))

      (instance? SQLiteConn source)
      (with-open [snapshot (db source)]
        (.queryEntityIntPairColumns (:native snapshot) (:native prepared) input-edn))

      :else
      nil)))

(defn- entity-string-int-triples [source ^PreparedQuery prepared inputs]
  (let [input-edn (inputs-text inputs)]
    (cond
      (instance? DB source)
      (.queryEntityStringIntTripleColumns (:native source) (:native prepared) input-edn)

      (instance? Conn source)
      (with-open [snapshot (db source)]
        (.queryEntityStringIntTripleColumns (:native snapshot) (:native prepared) input-edn))

      (instance? SQLiteConn source)
      (with-open [snapshot (db source)]
        (.queryEntityStringIntTripleColumns (:native snapshot) (:native prepared) input-edn))

      :else
      nil)))

(defn- column-result [source ^PreparedQuery prepared inputs]
  (let [input-edn (inputs-text inputs)]
    (cond
      (instance? DB source)
      (.queryColumns (:native source) (:native prepared) input-edn)

      (instance? Conn source)
      (with-open [snapshot (db source)]
        (.queryColumns (:native snapshot) (:native prepared) input-edn))

      (instance? SQLiteConn source)
      (with-open [snapshot (db source)]
        (.queryColumns (:native snapshot) (:native prepared) input-edn))

      :else
      nil)))

(defn- column-kind [kind]
  (cond
    (= kind Vev/COLUMN_ENTITY) :entity
    (= kind Vev/COLUMN_STRING) :string
    (= kind Vev/COLUMN_INT) :int
    (= kind Vev/COLUMN_BOOL) :bool
    :else :unknown))

(defn- column-result->map [^Vev$ColumnResult result]
  (let [^ints kinds (.kinds result)
        ^objects columns (.columns result)
        width (alength kinds)]
    {:row-count (.rowCount result)
     :kinds (mapv #(column-kind (aget kinds %)) (range width))
     :columns (mapv #(vec (aget columns %)) (range width))}))

(defn column-batch
  "Run a query and return Vev's native column batch when the query shape has a
  specialized typed result path.

  Returns nil when the query needs the general row/result representation. This
  is the low-overhead host API for callers that want column-oriented data."
  [query source & inputs]
  (let [{:keys [query source inputs]} (normalize-query-call query source inputs)]
    (if (instance? PreparedQuery query)
      (column-result source query inputs)
      (let [{rules :rules inputs :inputs} (split-rules-input query (vec inputs))]
        (when-not rules
          (with-open [prepared (prepare source query)]
            (column-result source prepared inputs)))))))

(defn columns
  "Run a query and return an ergonomic column result map:

  `{:row-count n :kinds [:entity :string ...] :columns [[...]
  [...]]}`.

  Returns nil when the query does not have a specialized typed column path."
  [query source & inputs]
  (when-let [result (apply column-batch query source inputs)]
    (column-result->map result)))

(defn- entity-column-rows [ids]
  (let [^longs ids ids
        n (alength ids)]
    (loop [index 0
           out (transient [])]
      (if (< index n)
        (recur (inc index)
               (conj! out [(long (aget ids index))]))
        (persistent! out)))))

(defn- entity-column-set [ids]
  (let [^longs ids ids
        n (alength ids)]
    (loop [index 0
           out (transient #{})]
      (if (< index n)
        (recur (inc index)
               (conj! out [(long (aget ids index))]))
        (persistent! out)))))

(defn- string-column-rows [values]
  (let [^objects values values
        n (alength values)]
    (loop [index 0
           out (transient [])]
      (if (< index n)
        (recur (inc index)
               (conj! out [(aget values index)]))
        (persistent! out)))))

(defn- string-column-set [values]
  (let [^objects values values
        n (alength values)]
    (loop [index 0
           out (transient #{})]
      (if (< index n)
        (recur (inc index)
               (conj! out [(aget values index)]))
        (persistent! out)))))

(defn- entity-int-pair-rows [columns]
  (let [^objects columns columns
        ^longs entities (aget columns 0)
        ^longs values (aget columns 1)
        n (alength entities)]
    (loop [index 0
           out (transient [])]
      (if (< index n)
        (recur (inc index)
               (conj! out [(long (aget entities index)) (long (aget values index))]))
        (persistent! out)))))

(defn- entity-int-pair-set [columns]
  (let [^objects columns columns
        ^longs entities (aget columns 0)
        ^longs values (aget columns 1)
        n (alength entities)]
    (loop [index 0
           out (transient #{})]
      (if (< index n)
        (recur (inc index)
               (conj! out [(long (aget entities index)) (long (aget values index))]))
        (persistent! out)))))

(defn- entity-string-int-triple-rows [columns]
  (let [^objects columns columns
        ^longs entities (aget columns 0)
        ^objects strings (aget columns 1)
        ^longs values (aget columns 2)
        n (alength entities)]
    (loop [index 0
           out (transient [])]
      (if (< index n)
        (recur (inc index)
               (conj! out [(long (aget entities index))
                           (aget strings index)
                           (long (aget values index))]))
        (persistent! out)))))

(defn- entity-string-int-triple-set [columns]
  (let [^objects columns columns
        ^longs entities (aget columns 0)
        ^objects strings (aget columns 1)
        ^longs values (aget columns 2)
        n (alength entities)]
    (loop [index 0
           out (transient #{})]
      (if (< index n)
        (recur (inc index)
               (conj! out [(long (aget entities index))
                           (aget strings index)
                           (long (aget values index))]))
        (persistent! out)))))

(defn- optimized-query-output [source prepared inputs entity-fn string-fn pair-fn triple-fn]
  (if-let [columns (column-result source prepared inputs)]
    (let [^Vev$ColumnResult columns columns
          ^ints kinds (.kinds columns)
          ^objects values (.columns columns)]
      (cond
        (and (= (alength kinds) 1)
             (= (aget kinds 0) Vev/COLUMN_ENTITY))
        (entity-fn (aget values 0))

        (and (= (alength kinds) 1)
             (= (aget kinds 0) Vev/COLUMN_STRING))
        (string-fn (aget values 0))

        (and (= (alength kinds) 2)
             (= (aget kinds 0) Vev/COLUMN_ENTITY)
             (= (aget kinds 1) Vev/COLUMN_INT))
        (pair-fn values)

        (and (= (alength kinds) 3)
             (= (aget kinds 0) Vev/COLUMN_ENTITY)
             (= (aget kinds 1) Vev/COLUMN_STRING)
             (= (aget kinds 2) Vev/COLUMN_INT))
        (triple-fn values)

        :else
        nil))
    (if-let [ids (entity-column source prepared inputs)]
      (entity-fn ids)
      (if-let [values (string-column source prepared inputs)]
        (string-fn values)
        (if-let [columns (entity-int-pair-columns source prepared inputs)]
          (pair-fn columns)
          (when-let [columns (entity-string-int-triples source prepared inputs)]
            (triple-fn columns)))))))

(defn- prepared-query-output [source prepared inputs result-fn entity-fn string-fn pair-fn triple-fn]
  (or (optimized-query-output source prepared inputs entity-fn string-fn pair-fn triple-fn)
      (with-open [result (apply query-result source prepared inputs)]
        (result-fn result))))

(defn profile
  "Run a prepared query against a DB and return Vev's native query stats."
  [^PreparedQuery prepared ^DB db & inputs]
  (edn/read-string (.profileEdn (:native db) (:native prepared) (inputs-text inputs))))

(defn- query-output [source prepared rules inputs result-fn entity-fn string-fn pair-fn triple-fn]
  (if rules
    (with-open [result (apply query-result-with-rules source prepared rules inputs)]
      (result-fn result))
    (prepared-query-output source prepared inputs result-fn entity-fn string-fn pair-fn triple-fn)))

(defn rows
  "Run a query and return rows as a vector of Clojure vectors.

  Accepts both DB-first Vev style and query-first Datomic/DataScript style."
  [query source & inputs]
  (let [{:keys [query source inputs]} (normalize-query-call query source inputs)]
    (if (instance? PreparedQuery query)
      (prepared-query-output source query inputs
                             rows-from-result
                             entity-column-rows
                             string-column-rows
                             entity-int-pair-rows
                             entity-string-int-triple-rows)
      (let [{rules :rules inputs :inputs} (split-rules-input query (vec inputs))]
        (with-open [prepared (prepare source query)]
          (let [result (query-output source prepared rules inputs
                                     rows-from-result
                                     entity-column-rows
                                     string-column-rows
                                     entity-int-pair-rows
                                     entity-string-int-triple-rows)]
            (if-let [return-map (query-return-map query)]
              (keyed-rows return-map result)
              result)))))))

(defn q
  "Run a query and return a set of row vectors, or maps for :keys/:strs/:syms queries."
  [query source & inputs]
  (let [{:keys [query source inputs]} (normalize-query-call query source inputs)]
    (if (instance? PreparedQuery query)
      (prepared-query-output source query inputs
                             q-from-result
                             entity-column-set
                             string-column-set
                             entity-int-pair-set
                             entity-string-int-triple-set)
      (let [{rules :rules inputs :inputs} (split-rules-input query (vec inputs))]
        (with-open [prepared (prepare source query)]
          (let [result (query-output source prepared rules inputs
                                     rows-from-result
                                     entity-column-rows
                                     string-column-rows
                                     entity-int-pair-rows
                                     entity-string-int-triple-rows)]
            (if-let [return-map (query-return-map query)]
              (keyed-set return-map result)
              (set result))))))))

(defn query
  "Run a Datomic-style query.

  With one argument, accepts a map shaped like `{:query q :args [db ...]}` and
  returns the same set-of-row-vectors shape as `q`."
  ([request]
   (when-not (map? request)
     (throw (ex-info "expected query request map" {:request request})))
   (let [{query :query args :args} request]
     (when-not query
       (throw (ex-info "query request requires :query" {:request request})))
     (when-not (vector? args)
       (throw (ex-info "query request requires vector :args" {:request request})))
     (apply q query args)))
  ([query source & inputs]
   (apply q query source inputs)))

(defn scalar
  "Run a query expected to return one value."
  [query source & inputs]
  (let [{:keys [query source inputs]} (normalize-query-call query source inputs)]
    (if (instance? PreparedQuery query)
      (with-open [result (apply query-result source query inputs)]
        (clj-value (.scalar result)))
      (let [{rules :rules inputs :inputs} (split-rules-input query (vec inputs))]
        (with-open [prepared (prepare source query)]
          (if rules
            (with-open [result (apply query-result-with-rules source prepared rules inputs)]
              (clj-value (.scalar result)))
            (with-open [result (apply query-result source prepared inputs)]
              (clj-value (.scalar result)))))))))

(defn- with-db-source [source f]
  (cond
    (instance? DB source)
    (f source)

    (instance? Conn source)
    (with-open [snapshot (db source)]
      (f snapshot))

    :else
    (throw (ex-info "expected Vev connection or DB" {:source source}))))

(defn pull
  "Pull one entity or lookup-ref from a DB or connection."
  [source pattern eid]
  (with-db-source
    source
    (fn [db]
      (clj-value
       (if (and (vector? eid)
                (= 2 (count eid))
                (keyword? (first eid)))
         (let [attr-text (edn-text (first eid))
               value (second eid)]
           (cond
             (string? value)
             (if (instance? PreparedPullPattern pattern)
               (.pullLookupRefString (:native db) ^Vev$PreparedPullPattern (:native pattern) attr-text value)
               (.pullLookupRefString (:native db) (edn-text pattern) attr-text value))

             (keyword? value)
             (if (instance? PreparedPullPattern pattern)
               (.pullLookupRefKeyword (:native db) ^Vev$PreparedPullPattern (:native pattern) attr-text (str value))
               (.pullLookupRefKeyword (:native db) (edn-text pattern) attr-text (str value)))

             (instance? java.util.UUID value)
             (if (instance? PreparedPullPattern pattern)
               (.pullLookupRefUuid (:native db) ^Vev$PreparedPullPattern (:native pattern) attr-text value)
               (.pullLookupRefUuid (:native db) (edn-text pattern) attr-text value))

             (integer? value)
             (if (instance? PreparedPullPattern pattern)
               (.pullLookupRefInt (:native db) ^Vev$PreparedPullPattern (:native pattern) attr-text (long value))
               (.pullLookupRefInt (:native db) (edn-text pattern) attr-text (long value)))

             (instance? Vev$Entity value)
             (if (instance? PreparedPullPattern pattern)
               (.pullLookupRefEntity (:native db) ^Vev$PreparedPullPattern (:native pattern) attr-text (.id ^Vev$Entity value))
               (.pullLookupRefEntity (:native db) (edn-text pattern) attr-text (.id ^Vev$Entity value)))

             :else
             (throw (ex-info "unsupported lookup-ref pull value"
                             {:value value
                              :supported #{:string :keyword :uuid :integer :entity}}))))
         (if (instance? PreparedPullPattern pattern)
           (.pull (:native db) ^Vev$PreparedPullPattern (:native pattern) (long eid))
           (.pull (:native db) (edn-text pattern) (long eid))))))))

(defn- same-uuid-lookup-refs [eids]
  (when (seq eids)
    (let [attr (ffirst eids)]
      (when (and (keyword? attr)
                 (every? #(and (vector? %)
                               (= 2 (count %))
                               (= attr (first %))
                               (instance? java.util.UUID (second %)))
                         eids))
        {:attr attr
         :values (mapv second eids)}))))

(defn pull-many
  "Pull several entities, preserving input order."
  [source pattern eids]
  (if (every? integer? eids)
    (with-db-source
      source
      (fn [db]
        (clj-value
         (if (instance? PreparedPullPattern pattern)
           (.pullMany (:native db)
                      ^Vev$PreparedPullPattern (:native pattern)
                      (long-array eids))
           (.pullMany (:native db)
                      (edn-text pattern)
                      (long-array eids))))))
    (if-let [{:keys [attr values]} (and (instance? PreparedPullPattern pattern)
                                        (same-uuid-lookup-refs eids))]
      (with-db-source
        source
        (fn [db]
          (clj-value
           (.pullManyLookupRefUuid (:native db)
                                   ^Vev$PreparedPullPattern (:native pattern)
                                   (edn-text attr)
                                   (into-array java.util.UUID values)))))
      (mapv #(pull source pattern %) eids))))

(defn rows-legacy
  "Deprecated connection-first row helper kept for early examples."
  [source query & inputs]
  (apply rows source query inputs))

(comment
  ;; Early wrapper shape retained by normalize-query-call:
  (with-open [conn (create-conn)]
    (rows conn '[:find ?e :where [?e :name ?name]])))

(defn q-text
  "Run an EDN string query through the rendered text API."
  ([^Conn conn query]
   (q-text conn query []))
  ([^Conn conn query inputs]
   (.queryText (:native conn) query (pr-str inputs))))
