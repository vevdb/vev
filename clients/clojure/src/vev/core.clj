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
    (.close ^java.lang.AutoCloseable native)
    (.close engine)))

(defrecord DB [^Vev engine native]
  java.lang.AutoCloseable
  (close [_]
    (.close ^java.lang.AutoCloseable native)))

(defrecord PreparedQuery [^Vev engine native]
  java.lang.AutoCloseable
  (close [_]
    (.close ^java.lang.AutoCloseable native)))

(defn open
  "Open an in-memory Vev connection through the native library at lib-path."
  [lib-path]
  (let [engine (Vev. (path lib-path))]
    (try
      (->Conn engine (.openMemory engine))
      (catch Throwable error
        (.close engine)
        (throw error)))))

(defn db
  "Return an immutable DB snapshot from a connection."
  [^Conn conn]
  (->DB (:engine conn) (.db (:native conn))))

(defn transact!
  "Transact Clojure data or EDN text against a connection.

  This currently returns Vev's rendered transaction report string. A later
  wrapper pass should parse this into a Clojure report map once the ABI exposes
  typed transaction reports."
  [^Conn conn tx]
  (.transact (:native conn) (edn-text tx)))

(defn prepare
  "Prepare a query from Clojure data or EDN text."
  [source query]
  (let [engine (:engine source)]
    (->PreparedQuery engine (.prepare engine (edn-text query)))))

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

(defn rows
  "Run a query and return rows as Clojure vectors."
  [source query & inputs]
  (if (instance? PreparedQuery query)
    (with-open [result (apply query-result source query inputs)]
      (mapv clj-value (.rows result)))
    (with-open [prepared (prepare source query)
                result (apply query-result source prepared inputs)]
      (mapv clj-value (.rows result)))))

(def q rows)

(defn scalar
  "Run a query expected to return one value."
  [source query & inputs]
  (if (instance? PreparedQuery query)
    (with-open [result (apply query-result source query inputs)]
      (clj-value (.scalar result)))
    (with-open [prepared (prepare source query)
                result (apply query-result source prepared inputs)]
      (clj-value (.scalar result)))))

(defn q-text
  "Run an EDN string query through the rendered text API."
  ([^Conn conn query]
   (q-text conn query []))
  ([^Conn conn query inputs]
   (.queryText (:native conn) query (pr-str inputs))))
