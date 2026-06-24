(ns vev-datascript-bench.vev
  (:require [vev.core :as v]
            [vev-datascript-bench.core :as core]))

(def schema-tx
  [{:db/id 100 :db/ident :follows :db/valueType :db.type/ref :db/cardinality :db.cardinality/many}
   {:db/id 101 :db/ident :name :db/valueType :db.type/string}
   {:db/id 102 :db/ident :last-name :db/valueType :db.type/string}
   {:db/id 103 :db/ident :sex :db/valueType :db.type/keyword}
   {:db/id 104 :db/ident :age :db/valueType :db.type/long}
   {:db/id 105 :db/ident :salary :db/valueType :db.type/long}])

(def schema-tx-text
  "[[:db/add 100 :db/ident :follows]
    [:db/add 100 :db/valueType :db.type/ref]
    [:db/add 100 :db/cardinality :db.cardinality/many]
    [:db/add 101 :db/ident :name]
    [:db/add 101 :db/valueType :db.type/string]
    [:db/add 102 :db/ident :last-name]
    [:db/add 102 :db/valueType :db.type/string]
    [:db/add 103 :db/ident :sex]
    [:db/add 103 :db/valueType :db.type/keyword]
    [:db/add 104 :db/ident :age]
    [:db/add 104 :db/valueType :db.type/long]
    [:db/add 105 :db/ident :salary]
    [:db/add 105 :db/valueType :db.type/long]]")

(defn library-path []
  (or (System/getProperty "vev.library")
      (System/getenv "VEV_LIB")
      "build/lib/libvev.dylib"))

(defn- append-value! [^StringBuilder builder value]
  (.append builder (binding [*print-namespace-maps* false] (pr-str value))))

(defn- append-add! [^StringBuilder builder e attr value]
  (.append builder "[:db/add ")
  (.append builder e)
  (.append builder " ")
  (.append builder attr)
  (.append builder " ")
  (append-value! builder value)
  (.append builder "]"))

(defn people-tx-text [people]
  (let [builder (StringBuilder. (* 80 (count people)))]
    (.append builder "[")
    (doseq [p people]
      (let [e (:db/id p)]
        (append-add! builder e :name (:name p))
        (append-add! builder e :last-name (:last-name p))
        (append-add! builder e :sex (:sex p))
        (append-add! builder e :age (:age p))
        (append-add! builder e :salary (:salary p))))
    (.append builder "]")
    (str builder)))

(defn people-db []
  (with-open [initial (v/empty-db (library-path))
              schema-db (v/db-with initial schema-tx-text)]
    (v/db-with schema-db (people-tx-text @core/people20k))))

(def shared-db (delay (people-db)))

(defn isolated-dbs? []
  (= "true" (System/getenv "VEV_BENCH_ISOLATED_DBS")))

(defn bench-db []
  (if (isolated-dbs?)
    (delay (people-db))
    shared-db))

(def db100k-1 (bench-db))
(def db100k-2 (bench-db))
(def db100k-2s (bench-db))
(def db100k-3 (bench-db))
(def db100k-4 (bench-db))
(def db100k-p1 (bench-db))
(def db100k-p2 (bench-db))

(defn q1 []
  (core/bench
    (v/q '[:find ?e
           :where [?e :name "Ivan"]]
         @db100k-1)))

(defn q2 []
  (core/bench
    (v/q '[:find ?e ?a
           :where
           [?e :name "Ivan"]
           [?e :age ?a]]
         @db100k-2)))

(defn q2-switch []
  (core/bench
    (v/q '[:find ?e ?a
           :where
           [?e :age ?a]
           [?e :name "Ivan"]]
         @db100k-2s)))

(defn q3 []
  (core/bench
    (v/q '[:find ?e ?a
           :where
           [?e :name "Ivan"]
           [?e :age ?a]
           [?e :sex :male]]
         @db100k-3)))

(defn q4 []
  (core/bench
    (v/q '[:find ?e ?l ?a
           :where
           [?e :name "Ivan"]
           [?e :last-name ?l]
           [?e :age ?a]
           [?e :sex :male]]
         @db100k-4)))

(defn qpred1 []
  (core/bench
    (v/q '[:find ?e ?s
           :where
           [?e :salary ?s]
           [(> ?s 50000)]]
         @db100k-p1)))

(defn qpred2 []
  (core/bench
    (v/q '[:find ?e ?s
           :in $ ?min-s
           :where
           [?e :salary ?s]
           [(> ?s ?min-s)]]
         @db100k-p2
         50000)))

(def default-benchmarks
  ["q1" "q2" "q2-switch" "q3" "q4" "qpred1" "qpred2"])

(defn -main [& names]
  (let [names (if (seq names) names default-benchmarks)]
    (doseq [n names]
      (if-some [benchmark (ns-resolve 'vev-datascript-bench.vev (symbol n))]
        (let [perf (benchmark)]
          (print (core/round perf) "\t")
          (flush))
        (do
          (print "---" "\t")
          (flush))))
    (println)))
