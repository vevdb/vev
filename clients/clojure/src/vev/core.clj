(ns vev.core
  (:import [java.nio.file Path]
           [vev Vev Vev$Entity Vev$MapValue]))

(defn- path [value]
  (cond
    (instance? Path value) value
    (string? value) (Path/of value (make-array String 0))
    :else (throw (ex-info "expected native library path" {:value value}))))

(defn- edn-text [value]
  (if (string? value)
    value
    (binding [*print-namespace-maps* false]
      (pr-str value))))

(defn- inputs-text [inputs]
  (binding [*print-namespace-maps* false]
    (pr-str (vec inputs))))

(defn- default-library-path []
  (or (System/getProperty "vev.library")
      (System/getenv "VEV_LIB")
      "build/lib/libvev.dylib"))

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
    (into {}
          (map (fn [entry]
                 [(key-value (clj-value (.key entry)))
                  (clj-value (.value entry))]))
          (.entries ^Vev$MapValue value))

    (instance? java.util.List value)
    (mapv clj-value value)

    :else
    value))

(defrecord Conn [^Vev engine native]
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

(defonce ^:private prepared-query-cache (atom {}))

(defrecord TxBuilder [^Vev engine native]
  java.lang.AutoCloseable
  (close [_]
    (.close ^java.lang.AutoCloseable native)))

(defn create-conn
  "Create an in-memory Vev connection.

  With no arguments, uses the `vev.library` system property, then `VEV_LIB`,
  then `build/lib/libvev.dylib`."
  ([]
   (create-conn (default-library-path)))
  ([lib-path]
   (let [engine (Vev. (path lib-path))]
     (try
       (->Conn engine (.createConn engine))
       (catch Throwable error
         (.close engine)
         (throw error))))))

(def open create-conn)

(defn db
  "Return an immutable DB snapshot from a connection."
  [^Conn conn]
  (->DB (:engine conn) (.db (:native conn))))

(defn conn-from-db
  "Create a mutable connection initialized from an immutable DB value."
  [^DB db]
  (->Conn (:engine db) (.connFromDb (:engine db) (:native db))))

(defn transact-text!
  "Transact Clojure data or EDN text and return the raw EDN report text."
  [^Conn conn tx]
  (.transact (:native conn) (edn-text tx)))

(defn transact!
  "Transact Clojure data or EDN text against a connection.

  Returns a Clojure transaction report map."
  [^Conn conn tx]
  (with-open [report (if (instance? TxBuilder tx)
                       (.transactReport (:native conn) (:native tx))
                       (.transactReport (:native conn) (edn-text tx)))]
    (clj-value (.value report))))

(def transact transact!)

(defn empty-db
  "Return an owned immutable empty DB value."
  ([]
   (empty-db (default-library-path)))
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
   (init-db (default-library-path) tx))
  ([lib-path tx]
   (with-open [initial (empty-db lib-path)]
     (db-with initial tx))))

(defn prepare
  "Prepare a query from Clojure data or EDN text."
  [source query]
  (let [engine (:engine source)]
    (->PreparedQuery engine (.prepare engine (edn-text query)))))

(defn- cached-prepare [source query]
  (locking prepared-query-cache
    (if-let [prepared (get @prepared-query-cache query)]
      prepared
      (let [prepared (prepare source query)]
        (swap! prepared-query-cache assoc query prepared)
        prepared))))

(defn- source? [value]
  (or (instance? Conn value)
      (instance? DB value)))

(defn- normalize-query-call [first-arg second-arg inputs]
  (if (source? first-arg)
    {:source first-arg :query second-arg :inputs inputs}
    {:source second-arg :query first-arg :inputs inputs}))

(defn query-result
  "Run a prepared query and return the native result handle. Caller owns it."
  [source ^PreparedQuery prepared & inputs]
  (let [input-edn (inputs-text inputs)]
    (cond
      (instance? Conn source)
      (.query (:native source) (:native prepared) input-edn)

      (instance? DB source)
      (.query (:native source) (:native prepared) input-edn)

      :else
      (throw (ex-info "expected Vev connection or DB" {:source source})))))

(defn- single-entity-rows [result]
  (when-let [ids (.singleEntityColumn result)]
    (mapv (fn [id] [(long id)]) ids)))

(defn- rows-from-result [result]
  (or (single-entity-rows result)
      (mapv clj-value (.rows result))))

(defn- q-from-result [result]
  (if-let [ids (.singleEntityColumn result)]
    (persistent!
      (reduce
        (fn [out id]
          (conj! out [(long id)]))
        (transient #{})
        ids))
    (set (mapv clj-value (.rows result)))))

(defn- entity-column [source ^PreparedQuery prepared inputs]
  (let [input-edn (inputs-text inputs)]
    (cond
      (instance? DB source)
      (.queryEntityColumn (:native source) (:native prepared) input-edn)

      (instance? Conn source)
      (with-open [snapshot (db source)]
        (.queryEntityColumn (:native snapshot) (:native prepared) input-edn))

      :else
      nil)))

(defn- entity-int-pairs [source ^PreparedQuery prepared inputs]
  (let [input-edn (inputs-text inputs)]
    (cond
      (instance? DB source)
      (.queryEntityIntPairs (:native source) (:native prepared) input-edn)

      (instance? Conn source)
      (with-open [snapshot (db source)]
        (.queryEntityIntPairs (:native snapshot) (:native prepared) input-edn))

      :else
      nil)))

(defn- entity-column-rows [ids]
  (mapv (fn [id] [(long id)]) ids))

(defn- entity-column-set [ids]
  (persistent!
    (reduce
      (fn [out id]
        (conj! out [(long id)]))
      (transient #{})
      ids)))

(defn- entity-int-pair-rows [pairs]
  (mapv (fn [pair] [(long (aget ^longs pair 0)) (long (aget ^longs pair 1))]) pairs))

(defn- entity-int-pair-set [pairs]
  (persistent!
    (reduce
      (fn [out pair]
        (conj! out [(long (aget ^longs pair 0)) (long (aget ^longs pair 1))]))
      (transient #{})
      pairs)))

(defn rows
  "Run a query and return rows as a vector of Clojure vectors.

  Accepts both DB-first Vev style and query-first Datomic/DataScript style."
  [query source & inputs]
  (let [{:keys [query source inputs]} (normalize-query-call query source inputs)]
    (let [prepared (if (instance? PreparedQuery query)
                     query
                     (cached-prepare source query))]
      (if-let [ids (entity-column source prepared inputs)]
        (entity-column-rows ids)
        (if-let [pairs (entity-int-pairs source prepared inputs)]
          (entity-int-pair-rows pairs)
          (with-open [result (apply query-result source prepared inputs)]
            (rows-from-result result)))))))

(defn q
  "Run a query and return a set of row vectors."
  [query source & inputs]
  (let [{:keys [query source inputs]} (normalize-query-call query source inputs)]
    (let [prepared (if (instance? PreparedQuery query)
                     query
                     (cached-prepare source query))]
      (if-let [ids (entity-column source prepared inputs)]
        (entity-column-set ids)
        (if-let [pairs (entity-int-pairs source prepared inputs)]
          (entity-int-pair-set pairs)
          (with-open [result (apply query-result source prepared inputs)]
            (q-from-result result)))))))

(defn scalar
  "Run a query expected to return one value."
  [query source & inputs]
  (let [{:keys [query source inputs]} (normalize-query-call query source inputs)]
    (if (instance? PreparedQuery query)
      (with-open [result (apply query-result source query inputs)]
        (clj-value (.scalar result)))
      (with-open [result (apply query-result source (cached-prepare source query) inputs)]
        (clj-value (.scalar result))))))

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
  "Pull one entity or string lookup-ref from a DB or connection."
  [source pattern eid]
  (with-db-source
    source
    (fn [db]
      (clj-value
        (if (and (vector? eid)
                 (= 2 (count eid))
                 (keyword? (first eid))
                 (string? (second eid)))
          (.pullLookupRefString (:native db)
                                (edn-text pattern)
                                (edn-text (first eid))
                                (second eid))
          (.pull (:native db) (edn-text pattern) (long eid)))))))

(defn pull-many
  "Pull several entities, preserving input order."
  [source pattern eids]
  (if (every? integer? eids)
    (with-db-source
      source
      (fn [db]
        (clj-value (.pullMany (:native db)
                              (edn-text pattern)
                              (long-array eids)))))
    (mapv #(pull source pattern %) eids)))

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
