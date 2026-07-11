;; Copyright (c) Andreas Flakstad and Vev contributors
;; SPDX-License-Identifier: EPL-2.0

(ns compare-musicbrainz-workshop
  (:require [clojure.string :as str]
            [datomic.api :as datomic]
            [musicbrainz-workshop :as workshop]
            [vev.core :as vev])
  (:import [java.util Locale]))

;; Source of truth:
;; build/upstream/mbrainz-sample/examples/clj/datomic/samples/mbrainz.clj
;; Section: data queries.

(def default-datomic-uri "datomic:dev://localhost:4334/mbrainz-1968-1973")

(def uint64-modulus 18446744073709551616N)
(def fingerprint-seed 0N)

(defn fmt [template & args]
  (String/format Locale/US template (to-array args)))

(defn fingerprint-text [seed text]
  (reduce
   (fn [hash codepoint]
     (mod (+ (* hash 131) codepoint) uint64-modulus))
   seed
   (seq (.toArray (.codePoints text)))))

(defn canonical-text [value]
  (cond
    (map? value)
    (str "{"
         (str/join " "
                   (map (fn [[k v]]
                          (str (canonical-text k) " " (canonical-text v)))
                        (sort-by (comp canonical-text key) value)))
         "}")

    (set? value)
    (str "#{" (str/join " " (sort (map canonical-text value))) "}")

    (sequential? value)
    (str "[" (str/join " " (map canonical-text value)) "]")

    (instance? java.util.UUID value)
    (str value)

    :else
    (binding [*print-namespace-maps* false]
      (pr-str value))))

(defn row-key [row]
  (let [values (if (sequential? row) row [row])]
    (str/join "|" (map canonical-text values))))

(defn result-rows [result]
  (cond
    (set? result) result
    (nil? result) []
    (instance? java.util.Collection result) result
    (sequential? result) result
    :else [result]))

(defn result-fingerprint [rows]
  (let [hash (reduce
              (fn [hash key]
                (fingerprint-text (fingerprint-text hash key) "\n"))
              fingerprint-seed
              (sort (map row-key rows)))
        hex (.toString (biginteger hash) 16)]
    (str (apply str (repeat (max 0 (- 16 (count hex))) "0")) hex)))

(defn elapsed-us [f]
  (let [start (System/nanoTime)
        result (f)
        elapsed (/ (double (- (System/nanoTime) start)) 1000.0)]
    [result elapsed]))

(defn run-q [q db query & args]
  (apply q query db args))

(def workloads
  [{:name "mbrainz-title-by-artist"
    :run (fn [q db]
           (run-q q db workshop/mbrainz-title-by-artist-query "John Lennon"))}
   {:name "mbrainz-title-album-year-by-artist"
    :run (fn [q db]
           (run-q q db workshop/mbrainz-title-album-year-by-artist-query "John Lennon"))}
   {:name "mbrainz-pre-1970-title-album-year"
    :run (fn [q db]
           (run-q q db workshop/mbrainz-pre-1970-title-album-year-query "John Lennon"))}
   {:name "mbrainz-track-release-rule"
    :run (fn [q db]
           (run-q q db
                  workshop/mbrainz-track-release-rule-query
                  @workshop/mbrainz-sample-rules
                  "John Lennon"))}
   {:name "mbrainz-track-search-info"
    :run (fn [q db]
           (run-q q db
                  workshop/mbrainz-track-search-info-query
                  @workshop/mbrainz-sample-rules
                  "always"))}
   {:name "mbrainz-collab"
    :run (fn [q db]
           (run-q q db
                  workshop/mbrainz-collab-query
                  @workshop/mbrainz-sample-rules
                  ["John Lennon" "Paul McCartney" "George Harrison" "Ringo Starr"]))}
   {:name "mbrainz-collab-net-2"
    :run (fn [q db]
           (run-q q db
                  workshop/mbrainz-collab-net-2-query
                  @workshop/mbrainz-sample-rules
                  ["George Harrison"]))}
   {:name "mbrainz-collab-nested"
    :run (fn [q db]
           (run-q q db
                  workshop/mbrainz-collab-nested-query
                  @workshop/mbrainz-sample-rules
                  (run-q q db
                         workshop/mbrainz-collab-nested-query
                         @workshop/mbrainz-sample-rules
                         [["Diana Ross"]])))}
   {:name "mbrainz-bill-withers-collaborations"
    :run (fn [q db]
           (run-q q db
                  workshop/mbrainz-bill-withers-collaboration-query
                  "Bill Withers"))}])

(defn run-workload [engine-name q db workload]
  (let [[result elapsed] (elapsed-us #((:run workload) q db))
        rows (result-rows result)
        row-count (count rows)
        fingerprint (result-fingerprint rows)]
    (println
     (fmt "engine=%s workload=%s ok=true rows=%d fingerprint=%s elapsed_us=%.0f"
          engine-name (:name workload) row-count fingerprint elapsed))
    {:engine engine-name
     :workload (:name workload)
     :rows row-count
     :fingerprint fingerprint
     :elapsed-us elapsed}))

(defn run-vev []
  (with-open [conn (workshop/connect)
              db (workshop/db conn)]
    (doall
     (map #(run-workload "vev-clojure" vev/q db %) workloads))))

(defn run-datomic [uri]
  (let [conn (datomic/connect uri)
        db (datomic/db conn)]
    (doall
     (map #(run-workload "datomic" datomic/q db %) workloads))))

(defn result-map [results]
  (into {}
        (map (fn [result]
               [(:workload result) result]))
        results))

(defn compare-results! [vev-results datomic-results]
  (let [vev-by-name (result-map vev-results)
        datomic-by-name (result-map datomic-results)
        failures (atom 0)]
    (doseq [workload (map :name workloads)]
      (let [vev-result (get vev-by-name workload)
            datomic-result (get datomic-by-name workload)]
        (cond
          (nil? vev-result)
          (do
            (swap! failures inc)
            (println (fmt "MISSING workload=%s engine=vev-clojure" workload)))

          (nil? datomic-result)
          (do
            (swap! failures inc)
            (println (fmt "MISSING workload=%s engine=datomic" workload)))

          (and (= (:rows vev-result) (:rows datomic-result))
               (= (:fingerprint vev-result) (:fingerprint datomic-result)))
          (let [ratio (if (pos? (:elapsed-us datomic-result))
                        (/ (:elapsed-us vev-result) (:elapsed-us datomic-result))
                        Double/POSITIVE_INFINITY)]
            (println
             (fmt "MATCH workload=%s rows=%d fingerprint=%s vev_us=%.0f datomic_us=%.0f ratio=%.3f"
                  workload
                  (:rows vev-result)
                  (:fingerprint vev-result)
                  (:elapsed-us vev-result)
                  (:elapsed-us datomic-result)
                  ratio)))

          :else
          (do
            (swap! failures inc)
            (println
             (fmt "MISMATCH workload=%s vev_rows=%d vev_fingerprint=%s datomic_rows=%d datomic_fingerprint=%s"
                  workload
                  (:rows vev-result)
                  (:fingerprint vev-result)
                  (:rows datomic-result)
                  (:fingerprint datomic-result)))))))
    (when (pos? @failures)
      (throw (ex-info "MusicBrainz workshop comparison failed"
                      {:failures @failures})))))

(defn arg-value [args name default-value]
  (let [idx (.indexOf args name)]
    (if (and (>= idx 0) (< (inc idx) (count args)))
      (nth args (inc idx))
      default-value)))

(defn -main [& raw-args]
  (let [args (vec raw-args)
        engine (arg-value args "--engine" "all")
        datomic-uri (arg-value args "--datomic-uri" default-datomic-uri)
        datomic-results (when (or (= engine "all") (= engine "datomic"))
                          (run-datomic datomic-uri))
        vev-results (when (or (= engine "all") (= engine "vev"))
                      (run-vev))]
    (when (and vev-results datomic-results)
      (compare-results! vev-results datomic-results))
    (shutdown-agents)
    (System/exit 0)))

(apply -main *command-line-args*)
