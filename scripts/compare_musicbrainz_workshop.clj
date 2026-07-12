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

(defn median [values]
  (let [items (vec (sort values))
        n (count items)]
    (if (zero? n)
      0.0
      (if (odd? n)
        (double (nth items (quot n 2)))
        (/ (+ (double (nth items (dec (quot n 2))))
              (double (nth items (quot n 2))))
           2.0)))))

(defn run-q [q db query & args]
  (apply q query db args))

(defn john-lennon-args []
  ["John Lennon"])

(defn sample-rules-john-lennon-args []
  [@workshop/mbrainz-sample-rules "John Lennon"])

(defn sample-rules-always-args []
  [@workshop/mbrainz-sample-rules "always"])

(defn beatles-args []
  [["John Lennon" "Paul McCartney" "George Harrison" "Ringo Starr"]])

(defn sample-rules-george-harrison-args []
  [@workshop/mbrainz-sample-rules ["George Harrison"]])

(defn bill-withers-args []
  ["Bill Withers"])

(defn run-workload-query [q db workload]
  (apply run-q q db (:query workload) ((:args workload))))

(defn run-workload-prepared-query [db prepared workload]
  (let [args ((:args workload))]
    (apply vev/q prepared db args)))

(def workloads
  [{:name "mbrainz-title-by-artist"
    :query workshop/mbrainz-title-by-artist-query
    :args john-lennon-args}
   {:name "mbrainz-title-album-year-by-artist"
    :query workshop/mbrainz-title-album-year-by-artist-query
    :args john-lennon-args}
   {:name "mbrainz-pre-1970-title-album-year"
    :query workshop/mbrainz-pre-1970-title-album-year-query
    :args john-lennon-args}
   {:name "mbrainz-track-release-rule"
    :query workshop/mbrainz-track-release-rule-query
    :args sample-rules-john-lennon-args}
   {:name "mbrainz-track-search-info"
    :query workshop/mbrainz-track-search-info-query
    :args sample-rules-always-args}
   {:name "mbrainz-collab"
    :query workshop/mbrainz-collab-query
    :args #(into [@workshop/mbrainz-sample-rules] (beatles-args))}
   {:name "mbrainz-collab-net-2"
    :query workshop/mbrainz-collab-net-2-query
    :args sample-rules-george-harrison-args}
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
    :query workshop/mbrainz-bill-withers-collaboration-query
    :args bill-withers-args}])

(defn should-run? [selected workload-name]
  (or (= selected "all")
      (= selected workload-name)
      (str/ends-with? workload-name selected)))

(defn selected-workloads [selected]
  (let [selected-items (vec (filter #(should-run? selected (:name %)) workloads))]
    (when (empty? selected-items)
      (throw (ex-info "unknown MusicBrainz workshop workload"
                      {:workload selected
                       :available (mapv :name workloads)})))
    selected-items))

(declare run-workload)

(defn execute-workload [q db workload]
  (if (:query workload)
    (run-workload-query q db workload)
    ((:run workload) q db)))

(defn execute-prepared-vev-workload [db prepared workload]
  (if (:query workload)
    (run-workload-prepared-query db prepared workload)
    ((:run workload) vev/q db)))

(defn run-prepared-vev-workload [db workload warmup-runs measure-runs]
  (if-not (:query workload)
    (run-workload "vev-clojure-prepared" vev/q db workload warmup-runs measure-runs)
    (with-open [prepared (vev/prepare db (:query workload))]
      (dotimes [_ warmup-runs]
        (execute-prepared-vev-workload db prepared workload))
      (let [measurements (doall
                          (repeatedly measure-runs
                                      #(let [[result elapsed] (elapsed-us
                                                               (fn []
                                                                 (execute-prepared-vev-workload db prepared workload)))
                                             rows (result-rows result)]
                                         {:elapsed-us elapsed
                                          :rows (count rows)
                                          :fingerprint (result-fingerprint rows)})))
            first-measurement (first measurements)
            row-count (:rows first-measurement)
            fingerprint (:fingerprint first-measurement)
            inconsistent (seq (remove #(and (= row-count (:rows %))
                                            (= fingerprint (:fingerprint %)))
                                      measurements))
            elapsed-values (map :elapsed-us measurements)
            elapsed (median elapsed-values)]
        (when inconsistent
          (throw (ex-info "workload produced inconsistent repeated results"
                          {:workload (:name workload)
                           :engine "vev-clojure-prepared"
                           :measurements measurements})))
        (println
         (if (= measure-runs 1)
           (fmt "engine=vev-clojure-prepared workload=%s ok=true rows=%d fingerprint=%s elapsed_us=%.0f"
                (:name workload) row-count fingerprint elapsed)
           (fmt "engine=vev-clojure-prepared workload=%s ok=true rows=%d fingerprint=%s elapsed_us=%.0f runs=%d warmup_runs=%d best_us=%.0f worst_us=%.0f"
                (:name workload)
                row-count
                fingerprint
                elapsed
                measure-runs
                warmup-runs
                (apply min elapsed-values)
                (apply max elapsed-values))))
        {:engine "vev-clojure-prepared"
         :workload (:name workload)
         :rows row-count
         :fingerprint fingerprint
         :elapsed-us elapsed}))))

(defn run-workload [engine-name q db workload warmup-runs measure-runs]
  (dotimes [_ warmup-runs]
    (execute-workload q db workload))
  (let [measurements (doall
                      (repeatedly measure-runs
                                  #(let [[result elapsed] (elapsed-us
                                                           (fn []
                                                             (execute-workload q db workload)))
                                         rows (result-rows result)]
                                     {:elapsed-us elapsed
                                      :rows (count rows)
                                      :fingerprint (result-fingerprint rows)})))
        first-measurement (first measurements)
        row-count (:rows first-measurement)
        fingerprint (:fingerprint first-measurement)
        inconsistent (seq (remove #(and (= row-count (:rows %))
                                        (= fingerprint (:fingerprint %)))
                                  measurements))
        elapsed-values (map :elapsed-us measurements)
        elapsed (median elapsed-values)]
    (when inconsistent
      (throw (ex-info "workload produced inconsistent repeated results"
                      {:workload (:name workload)
                       :engine engine-name
                       :measurements measurements})))
    (println
     (if (= measure-runs 1)
       (fmt "engine=%s workload=%s ok=true rows=%d fingerprint=%s elapsed_us=%.0f"
            engine-name (:name workload) row-count fingerprint elapsed)
       (fmt "engine=%s workload=%s ok=true rows=%d fingerprint=%s elapsed_us=%.0f runs=%d warmup_runs=%d best_us=%.0f worst_us=%.0f"
            engine-name
            (:name workload)
            row-count
            fingerprint
            elapsed
            measure-runs
            warmup-runs
            (apply min elapsed-values)
            (apply max elapsed-values))))
    {:engine engine-name
     :workload (:name workload)
     :rows row-count
     :fingerprint fingerprint
     :elapsed-us elapsed}))

(defn stats-line [stats]
  (binding [*print-namespace-maps* false]
    (pr-str stats)))

(defn run-vev-query-stats [db workload]
  (if-not (:query workload)
    (println
     (fmt "engine=vev-clojure-stats workload=%s ok=false reason=no-direct-query-metadata"
          (:name workload)))
    (let [args ((:args workload))
          [result elapsed] (elapsed-us #(vev/query {:query (:query workload)
                                                    :args (into [db] args)
                                                    :query-stats true}))
          rows (result-rows (:ret result))
          row-count (count rows)
          fingerprint (result-fingerprint rows)]
      (println
       (fmt "engine=vev-clojure-stats workload=%s ok=true rows=%d fingerprint=%s elapsed_us=%.0f stats=%s"
            (:name workload)
            row-count
            fingerprint
            elapsed
            (stats-line (:query-stats result)))))))

(defn run-vev [selected query-stats? prepared? warmup-runs measure-runs]
  (with-open [conn (workshop/connect)
              db (workshop/db conn)]
    (let [selected-items (selected-workloads selected)
          results (doall
                   (map #(if prepared?
                           (run-prepared-vev-workload db % warmup-runs measure-runs)
                           (run-workload "vev-clojure" vev/q db % warmup-runs measure-runs))
                        selected-items))]
      (when query-stats?
        (doseq [workload selected-items]
          (run-vev-query-stats db workload)))
      results)))

(defn run-datomic [uri selected warmup-runs measure-runs]
  (let [conn (datomic/connect uri)
        db (datomic/db conn)]
    (doall
     (map #(run-workload "datomic" datomic/q db % warmup-runs measure-runs)
          (selected-workloads selected)))))

(defn result-map [results]
  (into {}
        (map (fn [result]
               [(:workload result) result]))
        results))

(defn compare-results! [selected vev-results datomic-results]
  (let [vev-by-name (result-map vev-results)
        datomic-by-name (result-map datomic-results)
        failures (atom 0)]
    (doseq [workload (map :name (selected-workloads selected))]
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

(defn truthy-arg? [value]
  (#{"1" "true" "yes" "on"} (str/lower-case value)))

(defn int-arg [args name default-value]
  (let [raw (arg-value args name (str default-value))
        value (Integer/parseInt raw)]
    (when (neg? value)
      (throw (ex-info "integer argument must be non-negative"
                      {:arg name :value raw})))
    value))

(defn -main [& raw-args]
  (let [args (vec raw-args)
        engine (arg-value args "--engine" "all")
        selected (arg-value args "--workload" "all")
        datomic-uri (arg-value args "--datomic-uri" default-datomic-uri)
        query-stats? (truthy-arg? (arg-value args "--query-stats" "false"))
        prepared-vev? (truthy-arg? (arg-value args "--prepared-vev" "false"))
        warmup-runs (int-arg args "--warmup-runs" 0)
        measure-runs (int-arg args "--measure-runs" 1)]
    (when (< measure-runs 1)
      (throw (ex-info "measure-runs must be at least 1"
                      {:measure-runs measure-runs})))
    (let [datomic-results (when (or (= engine "all") (= engine "datomic"))
                            (run-datomic datomic-uri selected warmup-runs measure-runs))
          vev-results (when (or (= engine "all") (= engine "vev"))
                        (run-vev selected query-stats? prepared-vev? warmup-runs measure-runs))]
      (when (and vev-results datomic-results)
        (compare-results! selected vev-results datomic-results))
      (shutdown-agents)
      (System/exit 0))))

(apply -main *command-line-args*)
