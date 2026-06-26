(ns vev.core
  (:require [clojure.edn :as edn])
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

(defn- source? [value]
  (or (instance? Conn value)
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

      :else
      nil)))

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

(defn- entity-int-pair-rows [columns]
  (let [^objects columns columns
        ^longs entities (aget columns 0)
        ^longs values (aget columns 1)
        n (alength entities)]
    (mapv (fn [index] [(long (aget entities index)) (long (aget values index))])
          (range n))))

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
    (mapv (fn [index] [(long (aget entities index))
                       (aget strings index)
                       (long (aget values index))])
          (range n))))

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

(defn- optimized-query-output [source prepared inputs entity-fn pair-fn triple-fn]
  (if-let [ids (entity-column source prepared inputs)]
    (entity-fn ids)
    (if-let [columns (entity-int-pair-columns source prepared inputs)]
      (pair-fn columns)
      (when-let [columns (entity-string-int-triples source prepared inputs)]
        (triple-fn columns)))))

(defn- prepared-query-output [source prepared inputs result-fn entity-fn pair-fn triple-fn]
  (or (optimized-query-output source prepared inputs entity-fn pair-fn triple-fn)
      (with-open [result (apply query-result source prepared inputs)]
        (result-fn result))))

(defn profile
  "Run a prepared query against a DB and return Vev's native query stats."
  [^PreparedQuery prepared ^DB db & inputs]
  (edn/read-string (.profileEdn (:native db) (:native prepared) (inputs-text inputs))))

(defn- query-output [source prepared rules inputs result-fn entity-fn pair-fn triple-fn]
  (if rules
    (with-open [result (apply query-result-with-rules source prepared rules inputs)]
      (result-fn result))
    (prepared-query-output source prepared inputs result-fn entity-fn pair-fn triple-fn)))

(defn rows
  "Run a query and return rows as a vector of Clojure vectors.

  Accepts both DB-first Vev style and query-first Datomic/DataScript style."
  [query source & inputs]
  (let [{:keys [query source inputs]} (normalize-query-call query source inputs)]
    (if (instance? PreparedQuery query)
      (prepared-query-output source query inputs
                             rows-from-result
                             entity-column-rows
                             entity-int-pair-rows
                             entity-string-int-triple-rows)
      (let [{rules :rules inputs :inputs} (split-rules-input query (vec inputs))]
        (with-open [prepared (prepare source query)]
          (query-output source prepared rules inputs
                        rows-from-result
                        entity-column-rows
                        entity-int-pair-rows
                        entity-string-int-triple-rows))))))

(defn q
  "Run a query and return a set of row vectors."
  [query source & inputs]
  (let [{:keys [query source inputs]} (normalize-query-call query source inputs)]
    (if (instance? PreparedQuery query)
      (prepared-query-output source query inputs
                             q-from-result
                             entity-column-set
                             entity-int-pair-set
                             entity-string-int-triple-set)
      (let [{rules :rules inputs :inputs} (split-rules-input query (vec inputs))]
        (with-open [prepared (prepare source query)]
          (query-output source prepared rules inputs
                        q-from-result
                        entity-column-set
                        entity-int-pair-set
                        entity-string-int-triple-set))))))

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
          (let [pattern-text (edn-text pattern)
                attr-text (edn-text (first eid))
                value (second eid)]
            (cond
              (string? value)
              (.pullLookupRefString (:native db) pattern-text attr-text value)

              (keyword? value)
              (.pullLookupRefKeyword (:native db) pattern-text attr-text (str value))

              (integer? value)
              (.pullLookupRefInt (:native db) pattern-text attr-text (long value))

              (instance? Vev$Entity value)
              (.pullLookupRefEntity (:native db) pattern-text attr-text (.id ^Vev$Entity value))

              :else
              (throw (ex-info "unsupported lookup-ref pull value"
                              {:value value
                               :supported #{:string :keyword :integer :entity}}))))
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
