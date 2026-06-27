(ns musicbrainz-query-matrix
  (:require [clojure.string :as str]
            [datomic.api :as d]))

(def default-uri "datomic:dev://localhost:4334/mbrainz-1968-1973")

(def queries
  [{:name "musicbrainz-real-release-first"
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
    :query '[:find ?release-name
             :where
             [?artist :artist/name "The Beatles"]
             [?release :release/artists ?artist]
             [?release :release/name ?release-name]]
    :args []}
   {:name "musicbrainz-real-beatles-track-count"
    :query '[:find (count ?track)
             :where
             [?artist :artist/name "The Beatles"]
             [?track :track/artists ?artist]
             [?track :track/name]]
    :args []}
   {:name "musicbrainz-real-beatles-min-max-duration"
    :query '[:find (min ?dur) (max ?dur)
             :where
             [?artist :artist/name "The Beatles"]
             [?track :track/artists ?artist]
             [?track :track/duration ?dur]]
    :args []}
   {:name "musicbrainz-real-lookup-country"
    :query '[:find ?name
             :where
             [?country :country/name "United Kingdom"]
             [?country :country/name ?name]]
    :args []}
   {:name "musicbrainz-real-selected-artists-releases"
    :query '[:find ?artist-name ?release-name
             :in $ [?artist-name ...]
             :where
             [?artist :artist/name ?artist-name]
             [?release :release/artists ?artist]
             [?release :release/name ?release-name]]
    :args [["The Beatles" "Miles Davis"]]}
   {:name "musicbrainz-real-not-beatles-male"
    :query '[:find ?artist-name
             :where
             [?artist :artist/name "The Beatles"]
             (not [?artist :artist/gender :artist.gender/male])
             [?artist :artist/name ?artist-name]]
    :args []}
   {:name "musicbrainz-real-or-two-artists"
    :query '[:find ?artist-name
             :where
             (or [?artist :artist/name "The Beatles"]
                 [?artist :artist/name "Miles Davis"])
             [?artist :artist/name ?artist-name]]
    :args []}])

(def uint64-modulus 18446744073709551616N)
(def fingerprint-seed 0N)

(defn fingerprint-text [seed text]
  (reduce
   (fn [hash byte-value]
     (mod (+ (* hash 131) byte-value)
          uint64-modulus))
   seed
   (seq (.toArray (.codePoints text)))))

(defn result-row-key [row]
  (let [values (if (sequential? row) row [row])]
    (str/join "|" (map pr-str values))))

(defn result-fingerprint [rows]
  (let [hash (reduce
              (fn [hash key]
                (fingerprint-text (fingerprint-text hash key) "\n"))
              fingerprint-seed
              (sort (map result-row-key rows)))
        hex (.toString (biginteger hash) 16)]
    (str (apply str (repeat (max 0 (- 16 (count hex))) "0"))
         hex)))

(defn elapsed-us [f]
  (let [start (System/nanoTime)
        result (f)
        elapsed (/ (double (- (System/nanoTime) start)) 1000.0)]
    [result elapsed]))

(defn timing [samples]
  (let [xs (vec (sort samples))
        n (count xs)
        idx (fn [p]
              (min (dec n) (long (Math/floor (* p (dec n))))))]
    {:min (first xs)
     :median (nth xs (idx 0.5))
     :p90 (nth xs (idx 0.9))
     :max (last xs)}))

(defn print-result-rows [workload rows]
  (doseq [key (sort (map result-row-key rows))]
    (println (format "row engine=datomic workload=%s key=%s" workload key))))

(defn run-query [db warmups samples print-rows? {:keys [name query args]}]
  (dotimes [_ warmups]
    (apply d/q query db args))
  (let [sample-us (doall
                   (for [_ (range samples)]
                     (second (elapsed-us #(apply d/q query db args)))))
        result (apply d/q query db args)
        t (timing sample-us)]
    (println
     (format
      "engine=datomic workload=%s ok=true rows=%d fingerprint=%s min_us=%.0f median_us=%.0f p90_us=%.0f max_us=%.0f"
      name
      (count result)
      (result-fingerprint result)
      (:min t)
      (:median t)
      (:p90 t)
      (:max t)))
    (when print-rows?
      (print-result-rows name result))))

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
      (= selected (str/replace name #"^musicbrainz-real-" ""))))

(defn -main [& raw-args]
  (let [args (vec raw-args)
        uri (parse-arg args "--uri" default-uri)
        selected (parse-arg args "--workload" "all")
        warmups (parse-int-arg args "--warmups" 5)
        samples (parse-int-arg args "--samples" 10)
        print-rows? (= (parse-arg args "--print-rows" "false") "true")
        conn (d/connect uri)
        db (d/db conn)]
    (doseq [query queries
            :when (should-run? selected (:name query))]
      (run-query db warmups samples print-rows? query))
    (shutdown-agents)
    (System/exit 0)))

(apply -main *command-line-args*)
