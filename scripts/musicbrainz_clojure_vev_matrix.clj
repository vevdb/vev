;; Copyright (c) Andreas Flakstad and Vev contributors
;; SPDX-License-Identifier: EPL-2.0

(ns musicbrainz-clojure-vev-matrix
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [vev.core :as vev]))

(def default-lib "build/lib/libvev.dylib")
(def default-schema "build/musicbrainz/vev-mbrainz-subset-500-schema.edn")
(def default-values "build/musicbrainz/vev-mbrainz-subset-500-values.edn")
(def default-values-prefix "build/musicbrainz/vev-mbrainz-subset-full-chunked")

(def musicbrainz-rules
  '[[(track-release ?t ?r)
     [?m :medium/tracks ?t]
     [?r :release/media ?m]]
    [(track-info ?t ?track-name ?artist-name ?album ?year)
     [?t :track/name ?track-name]
     [?t :track/artists ?a]
     [?a :artist/name ?artist-name]
     (track-release ?t ?r)
     [?r :release/name ?album]
     [?r :release/year ?year]]
    [(short-track ?a ?t ?len ?max)
     [?t :track/artists ?a]
     [?t :track/duration ?len]
     [(< ?len ?max)]]])

(def workloads
  [{:name "musicbrainz-smoke-country-names"
    :kind :query
    :query '[:find ?country-name
             :where
             [?country :country/name ?country-name]]
    :args []}
   {:name "musicbrainz-real-release-first"
    :kind :query
    :query '[:find ?title ?album ?year
             :in $ ?artist-name
             :where
             [?a :artist/name ?artist-name]
             [?r :release/artists ?a]
             [?r :release/year ?year]
             [(< ?year 1970)]
             [?r :release/name ?album]
             [?r :release/media ?m]
             [?m :medium/tracks ?t]
             [?t :track/name ?title]]
    :args ["The Beatles"]}
   {:name "musicbrainz-real-track-first"
    :kind :query
    :query '[:find ?title ?album ?year
             :in $ ?artist-name
             :where
             [?a :artist/name ?artist-name]
             [?t :track/artists ?a]
             [?t :track/name ?title]
             [?r :release/artists ?a]
             [?r :release/year ?year]
             [(< ?year 1970)]
             [?r :release/name ?album]
             [?r :release/media ?m]
             [?m :medium/tracks ?t]]
    :args ["The Beatles"]}
   {:name "musicbrainz-real-beatles-releases"
    :kind :query
    :query '[:find ?release-name
             :where
             [?artist :artist/name "The Beatles"]
             [?release :release/artists ?artist]
             [?release :release/name ?release-name]]
    :args []}
   {:name "musicbrainz-real-beatles-duration-sum"
    :kind :query
    :query '[:find ?year (sum ?millis)
             :with ?track
             :where
             [?artist :artist/name "The Beatles"]
             [?release :release/artists ?artist]
             [?release :release/year ?year]
             [?release :release/media ?medium]
             [?medium :medium/tracks ?track]
             [?track :track/duration ?millis]]
    :args []}
   {:name "musicbrainz-real-missing-start-year"
    :kind :query
    :query '[:find ?artist-name
             :where
             [?artist :artist/name ?artist-name]
             [(missing? $ ?artist :artist/startYear)]]
    :args []}
   {:name "musicbrainz-real-top-duration"
    :kind :query
    :query '[:find (min 2 ?millis) (max 2 ?millis)
             :where
             [?track :track/duration ?millis]]
    :args []}
   {:name "musicbrainz-real-rule-track-info"
    :kind :query
    :query '[:find ?track-name ?album ?year
             :in $ % ?artist-name
             :where
             (track-info ?track ?track-name ?artist-name ?album ?year)
             [(< ?year 1970)]]
    :args [musicbrainz-rules "The Beatles"]}
   {:name "musicbrainz-real-pull-release"
    :kind :query
    :preserve-rows true
    :query '[:find (pull ?release [:release/name :release/year])
             :in $ [?release-name ...]
             :where
             [?release :release/name ?release-name]]
    :args [["Abbey Road" "In a Silent Way"]]}
   {:name "musicbrainz-real-direct-pull-artist"
    :kind :pull
    :pattern '[:artist/gid :artist/name :artist/startYear]
    :entity [:artist/gid #uuid "b10bbbfc-cf9e-42e0-be17-e2c3e1d2600d"]}
   {:name "musicbrainz-real-direct-pull-artist-releases"
    :kind :pull
    :pattern '[:artist/name {:release/_artists [:release/name :release/year]}]
    :entity [:artist/gid #uuid "b10bbbfc-cf9e-42e0-be17-e2c3e1d2600d"]}
   {:name "musicbrainz-real-direct-pull-many-artists"
    :kind :pull-many
    :pattern '[:artist/gid :artist/name :artist/startYear]
    :entities [[:artist/gid #uuid "b10bbbfc-cf9e-42e0-be17-e2c3e1d2600d"]
               [:artist/gid #uuid "561d854a-6a28-4aa7-8c99-323e6ce46c2a"]]}])

(def uint64-modulus 18446744073709551616N)

(defn fingerprint-text [seed text]
  (reduce
   (fn [hash byte-value]
     (mod (+ (* hash 131) byte-value)
          uint64-modulus))
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

    (sequential? value)
    (str "[" (str/join " " (map canonical-text value)) "]")

    (set? value)
    (str "#{" (str/join " " (sort (map canonical-text value))) "}")

    (instance? java.util.UUID value)
    (str value)

    (or (instance? Double value)
        (instance? Float value))
    (str "[:vev/float \"" value "\"]")

    :else
    (pr-str value)))

(defn result-rows [result]
  (cond
    (set? result) result
    (sequential? result) result
    :else [result]))

(defn result-row-count [rows]
  (count rows))

(defn result-row-key [row]
  (let [values (if (sequential? row) row [row])]
    (str/join "|" (map canonical-text values))))

(defn hex64 [value]
  (let [hex (.toString (biginteger value) 16)
        padded (str "0000000000000000" hex)]
    (subs padded (- (count padded) 16))))

(defn result-fingerprint [rows]
  (hex64
   (reduce
    (fn [hash key]
      (fingerprint-text (fingerprint-text hash key) "\n"))
    0N
    (sort (map result-row-key rows)))))

(defn elapsed-us [f]
  (let [start (System/nanoTime)
        result (f)
        stop (System/nanoTime)]
    [result (/ (- stop start) 1000.0)]))

(defn timing [samples]
  (let [xs (vec (sort samples))
        n (count xs)
        idx (fn [p]
              (min (dec n) (long (Math/floor (* p (dec n))))))]
    {:min (first xs)
     :median (nth xs (idx 0.5))
     :p90 (nth xs (idx 0.9))
     :max (last xs)}))

(defn parse-int-arg [args name default-value]
  (let [idx (.indexOf args name)]
    (if (and (>= idx 0) (< (inc idx) (count args)))
      (Long/parseLong (nth args (inc idx)))
      default-value)))

(defn parse-arg [args name default-value]
  (let [idx (.indexOf args name)]
    (if (and (>= idx 0) (< (inc idx) (count args)))
      (nth args (inc idx))
      default-value)))

(defn should-run? [selected name]
  (or (= selected "all")
      (= selected name)
      (= selected (str/replace name #"^musicbrainz-real-" ""))
      (= selected (str/replace name #"^musicbrainz-smoke-" ""))))

(defn load-tx! [conn path label]
  (let [[report elapsed] (elapsed-us #(vev/transact-text! conn (slurp path)))]
    (when (str/includes? report ":ok false")
      (throw (ex-info "Vev tx failed" {:path path :report report})))
    (println
     (format "engine=clojure-vev workload=musicbrainz-real-load ok=true stage=%s tx_us=%.0f"
             label elapsed))))

(defn load-real! [conn schema values values-prefix values-chunks]
  (let [[_ elapsed]
        (elapsed-us
         #(do
            (load-tx! conn schema "schema")
            (if (seq values)
              (load-tx! conn values "values")
              (doseq [index (range values-chunks)]
                (load-tx! conn
                          (format "%s-values-%04d.edn" values-prefix index)
                          (format "values-%04d" index))))))]
    (println
     (format "engine=clojure-vev workload=musicbrainz-real-load ok=true total_us=%.0f"
             elapsed))))

(defn workload-result [db workload]
  (case (:kind workload)
    :query (if (:preserve-rows workload)
             (apply vev/rows (:query workload) db (:args workload))
             (apply vev/q (:query workload) db (:args workload)))
    :pull [(vev/pull db (:pattern workload) (:entity workload))]
    :pull-many (vev/pull-many db (:pattern workload) (:entities workload))))

(defn run-workload [db warmups samples workload]
  (dotimes [_ warmups]
    (workload-result db workload))
  (let [sample-us (doall
                   (for [_ (range samples)]
                     (second (elapsed-us #(workload-result db workload)))))
        result (workload-result db workload)
        rows (result-rows result)
        t (timing sample-us)]
    (println
     (format
      "engine=clojure-vev workload=%s ok=true rows=%d fingerprint=%s min_us=%.0f median_us=%.0f p90_us=%.0f max_us=%.0f"
      (:name workload)
      (result-row-count rows)
      (result-fingerprint rows)
      (:min t)
      (:median t)
      (:p90 t)
      (:max t)))))

(defn -main [& raw-args]
  (let [args (vec raw-args)
        lib (parse-arg args "--lib" default-lib)
        uri (parse-arg args "--uri" "")
        schema (parse-arg args "--schema" default-schema)
        values (parse-arg args "--values" default-values)
        values-prefix (parse-arg args "--values-prefix" default-values-prefix)
        values-chunks (parse-int-arg args "--values-chunks" 8)
        selected (parse-arg args "--workload" "country-names")
        warmups (parse-int-arg args "--warmups" 5)
        samples (parse-int-arg args "--samples" 10)]
    (if (seq uri)
      (let [[conn open-us] (elapsed-us #(vev/connect lib uri))]
        (try
          (println
           (format "engine=clojure-vev workload=musicbrainz-real-open ok=true uri=%s open_us=%.0f info=%s"
                   uri
                   open-us
                   (pr-str (vev/connection-info conn))))
          (with-open [db (vev/db conn)]
            (doseq [workload workloads
                    :when (should-run? selected (:name workload))]
              (run-workload db warmups samples workload)))
          (finally
            (.close conn))))
      (with-open [conn (vev/create-conn lib)]
        (load-real! conn schema values values-prefix values-chunks)
        (with-open [db (vev/db conn)]
          (doseq [workload workloads
                  :when (should-run? selected (:name workload))]
            (run-workload db warmups samples workload)))))))

(apply -main *command-line-args*)
