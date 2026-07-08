;; Copyright (c) Andreas Flakstad and Vev contributors
;; SPDX-License-Identifier: EPL-2.0

(ns musicbrainz-workshop
  (:require [vev.core :as d]))

;; Port source of truth:
;; build/upstream/day-of-datomic-conj/src/music_brainz.clj
;; Sections: file top through the pre-1970 release clause-order examples.

(def default-uri "build/musicbrainz/vev-mbrainz-tutorial.sqlite")

(defn connect []
  (d/connect default-uri))

(defn db [conn]
  (d/db conn))

(def john-lennon-release-pull-query
  '[:find [(pull $ ?release [*])]
    :in $ ?artist-name
    :where
    [?release :release/artists ?artist]
    [?artist :artist/name ?artist-name]
    [?release :release/name ?release-name]])

(def tuple-binding-query
  '[:find [(pull $ ?release [*])]
    :in $ [?artist-name ?release-name]
    :where
    [?artist :artist/name ?artist-name]
    [?release :release/artists ?artist]
    [?release :release/name ?release-name]])

(def request-map-query
  '[:find (pull $ ?release
                [:release/name
                 :release/artistCredit
                 :release/year
                 {:release/script [*]}
                 {:release/media [{:medium/tracks [:track/name]}]}])
    :in $ ?artist-name
    :where
    [?artist :artist/name ?artist-name]
    [?release :release/artists ?artist]])

(def collection-binding-query
  '[:find ?release-name
    :in $ [?artist-name ...]
    :where
    [?artist :artist/name ?artist-name]
    [?release :release/artists ?artist]
    [?release :release/name ?release-name]])

(def relation-binding-query
  '[:find ?release
    :in $ [[?artist-name ?release-name]]
    :where
    [?artist :artist/name ?artist-name]
    [?release :release/artists ?artist]
    [?release :release/name ?release-name]])

(def relation-find-missing-db-query
  '[:find ?artist-name ?release-name
    :in ?artist-name
    :where
    [?release :release/name ?release-name]
    [?release :release/artists ?artist]
    [?artist :artist/name ?artist-name]])

(def relation-find-query
  '[:find ?artist-name ?release-name
    :in $ ?artist-name
    :where
    [?release :release/name ?release-name]
    [?release :release/artists ?artist]
    [?artist :artist/name ?artist-name]])

(def collection-find-query
  '[:find [?release-name ...]
    :in $ ?artist-name
    :where
    [?artist :artist/name ?artist-name]
    [?release :release/artists ?artist]
    [?release :release/name ?release-name]])

(def tuple-find-query
  '[:find [?year ?month ?day]
    :in $ ?name
    :where
    [?artist :artist/name ?name]
    [?artist :artist/startDay ?day]
    [?artist :artist/startMonth ?month]
    [?artist :artist/startYear ?year]])

(def scalar-find-query
  '[:find ?year .
    :in $ ?name
    :where
    [?artist :artist/name ?name]
    [?artist :artist/startYear ?year]])

(def return-map-find-query
  '[:find ?artist-name ?release-name
    :keys artist release
    :in $ ?artist-name
    :where
    [?release :release/name ?release-name]
    [?release :release/artists ?artist]
    [?artist :artist/name ?artist-name]])

(defn teste
  [?yeat]
  (< ?yeat 1600))

(def host-predicate-query
  '[:find ?name ?year
    :where
    [?artist :artist/name ?name]
    [?artist :artist/startYear ?year]
    [(user/teste ?year)]])

(def function-expression-query
  '[:find ?track-name ?minutes
    :in $ ?artist-name
    :where
    [?artist :artist/name ?artist-name]
    [?track :track/artists ?artist]
    [?track :track/duration ?millis]
    [(quot ?millis 60000) ?minutes]
    [?track :track/name ?track-name]])

(def artist-type-gender-query
  '[:find ?id ?type ?gender
    :in $ ?name
    :where
    [?e :artist/name ?name]
    [?e :artist/gid ?id]
    [?e :artist/type ?teid]
    [?teid :db/ident ?type]
    [?e :artist/gender ?geid]
    [?geid :db/ident ?gender]])

(def min-track-duration-query
  '[:find [(min ?dur)]
    :where
    [?e :track/duration ?dur]])

(def sum-medium-track-count-query
  '[:find (sum ?count) .
    :with ?medium
    :where
    [?medium :medium/trackCount ?count]])

(def artist-name-count-query
  '[:find (count ?name) (count-distinct ?name)
    :with ?artist
    :where
    [?artist :artist/name ?name]])

(def track-name-statistics-query
  '[:find ?year (median ?namelen) (avg ?namelen) (stddev ?namelen)
    :with ?track
    :where
    [?track :track/name ?name]
    [(count ?name) ?namelen]
    [?medium :medium/tracks ?track]
    [?release :release/media ?medium]
    [?release :release/year ?year]])

(def custom-mode-aggregate-query
  '[:find (user/mode ?track-count) .
    :with ?media
    :where
    [?media :medium/trackCount ?track-count]])

(def tracks-by-artist-name-query
  '[:find ?title
    :in $ ?artist-name
    :where
    [?a :artist/name ?artist-name]
    [?r :release/artists ?a]
    [?t :track/artists ?a]
    [?t :track/name ?title]])

(def pre-1970-release-names-query
  '[:find ?name
    :in $ ?artist-name
    :where
    [?a :artist/name ?artist-name]
    [?r :release/artists ?a]
    [?r :release/year ?year]
    [(< ?year 1970)]
    [?r :release/name ?name]])

(def pre-1970-release-names-switched-query
  '[:find ?name
    :in $ ?artist-name
    :where
    [?a :artist/name ?artist-name]
    [?r :release/artists ?a]
    [?r :release/year ?year]
    [?r :release/name ?name]
    [(< ?year 1970)]])

(defn opening-release-pulls [db]
  (d/q john-lennon-release-pull-query db "John Lennon"))

(defn tuple-binding-release-pulls [db]
  (d/q tuple-binding-query db ["John Lennon" "Mind Games"]))

(defn request-map-release-pulls [db]
  (d/query {:query request-map-query
            :args [db "Elis Regina"]}))

(defn collection-binding-releases [db]
  (d/q collection-binding-query db ["Paul McCartney" "George Harrison"]))

(defn relation-binding-releases [db]
  (d/q relation-binding-query
       db
       [["John Lennon" "Mind Games"]
        ["Paul McCartney" "Ram"]]))

(defn relation-find-missing-db [db]
  (d/q relation-find-missing-db-query db "elvis presley"))

(defn relation-find-releases [db]
  (d/q relation-find-query db "Elvis Presley"))

(defn collection-find-releases [db]
  (d/q collection-find-query db "John Lennon"))

(defn tuple-find-artist-start [db]
  (d/q tuple-find-query db "John Lennon"))

(defn scalar-find-artist-start [db]
  (d/q scalar-find-query db "John Lennon"))

(defn return-map-releases [db]
  (d/q return-map-find-query db "Paul McCartney"))

(defn return-map-positional-artist [db]
  (let [[artist release] (first (return-map-releases db))]
    artist))

(defn return-map-key-artist [db]
  (let [{:keys [artist release]} (first (return-map-releases db))]
    artist))

(defn host-predicate-artists [db]
  (d/q host-predicate-query db))

(defn function-expression-track-minutes [db]
  (d/q function-expression-query db "John Lennon"))

(defn artist-type-gender [db]
  (d/q artist-type-gender-query db "Janis Joplin"))

(defn min-track-duration [db]
  (d/q min-track-duration-query db))

(defn sum-medium-track-count [db]
  (d/q sum-medium-track-count-query db))

(defn artist-name-counts [db]
  (d/q artist-name-count-query db))

(defn track-name-statistics [db]
  (d/q track-name-statistics-query db))

(defn custom-mode-aggregate [db]
  (d/q custom-mode-aggregate-query db))

(defn tracks-by-artist-name [db]
  (d/query {:query tracks-by-artist-name-query
            :args [db "John Lennon"]
            :io-context :q/tracks-by-artist-name}))

(defn pre-1970-release-name-stats [db]
  (-> (d/query {:query pre-1970-release-names-query
                :args [db "John Lennon"]
                :query-stats true})
      :query-stats))

(defn pre-1970-release-name-results [db]
  (:ret (d/query {:query pre-1970-release-names-query
                  :args [db "John Lennon"]
                  :query-stats true})))

(defn pre-1970-release-name-switched-stats [db]
  (-> (d/query {:query pre-1970-release-names-switched-query
                :args [db "John Lennon"]
                :query-stats true})
      :query-stats))

(defn pre-1970-release-name-switched-results [db]
  (:ret (d/query {:query pre-1970-release-names-switched-query
                  :args [db "John Lennon"]
                  :query-stats true})))

(defn validate-opening-bindings! []
  (with-open [conn (connect)
              db (db conn)]
    (let [opening (opening-release-pulls db)
          tuple (tuple-binding-release-pulls db)
          request (request-map-release-pulls db)
          collection (collection-binding-releases db)
          relation-error (try
                           (relation-binding-releases db)
                           nil
                           (catch Throwable error
                             (.getMessage error)))]
      (assert (= 21 (count opening)))
      (assert (= 4 (count tuple)))
      (assert (= 10 (count request)))
      (assert (= 14 (count collection)))
      (assert (some? relation-error))
      {:opening-release-pulls (count opening)
       :tuple-binding-release-pulls (count tuple)
       :request-map-release-pulls (count request)
       :collection-binding-releases (count collection)
       :relation-binding-status :pending
       :relation-binding-error relation-error})))

(defn validate-find-specifications! []
  (with-open [conn (connect)
              db (db conn)]
    (let [missing-db (relation-find-missing-db db)
          relation (relation-find-releases db)
          collection (collection-find-releases db)
          tuple (tuple-find-artist-start db)
          scalar (scalar-find-artist-start db)
          return-map (return-map-releases db)
          positional-error (try
                             (return-map-positional-artist db)
                             nil
                             (catch Throwable error
                               (.getMessage error)))
          key-artist (return-map-key-artist db)
          host-predicate (host-predicate-artists db)
          function-expression (function-expression-track-minutes db)]
      (assert (empty? missing-db))
      (assert (= 29 (count relation)))
      (assert (= 12 (count collection)))
      (assert (= [1940 10 9] tuple))
      (assert (= 1940 scalar))
      (assert (= 3 (count return-map)))
      (assert (some? positional-error))
      (assert (= "Paul McCartney" key-artist))
      (assert (empty? host-predicate))
      (assert (= 71 (count function-expression)))
      {:missing-db-status :pending-error-shape
       :missing-db-count (count missing-db)
       :relation-find-releases (count relation)
       :collection-find-releases (count collection)
       :tuple-find-artist-start tuple
       :scalar-find-artist-start scalar
       :return-map-releases (count return-map)
       :return-map-positional-status :pending-indexed-map-row
       :return-map-positional-error positional-error
       :return-map-key-artist key-artist
       :host-predicate-status :pending-host-function
       :host-predicate-count (count host-predicate)
       :function-expression-track-minutes (count function-expression)})))

(defn validate-basic-aggregations! []
  (with-open [conn (connect)
              db (db conn)]
    (let [janis (artist-type-gender db)
          min-duration (min-track-duration db)
          sum-track-count (sum-medium-track-count db)
          name-counts (artist-name-counts db)]
      (assert (= 1 (count janis)))
      (assert (= 1 (count min-duration)))
      (assert (= #{[3000]} min-duration))
      (assert (= 99847 sum-track-count))
      (assert (= #{[4601 4588]} name-counts))
      {:artist-type-gender janis
       :min-track-duration min-duration
       :sum-medium-track-count sum-track-count
       :artist-name-counts name-counts
       :track-name-statistics-status :pending-performance
       :custom-mode-aggregate-status :pending-host-aggregate})))

(defn validate-deeper-query-intro! []
  (with-open [conn (connect)
              db (db conn)]
    (let [tracks (tracks-by-artist-name db)]
      (assert (= 70 (count tracks)))
      {:tracks-by-artist-name (count tracks)})))

(defn validate-pre-1970-release-stats! []
  (with-open [conn (connect)
              db (db conn)]
    (let [release-result (d/query {:query pre-1970-release-names-query
                                   :args [db "John Lennon"]
                                   :query-stats true})
          switched-result (d/query {:query pre-1970-release-names-switched-query
                                    :args [db "John Lennon"]
                                    :query-stats true})
          release-names (:ret release-result)
          release-stats (:query-stats release-result)
          switched-names (:ret switched-result)
          switched-stats (:query-stats switched-result)]
      (assert (= 3 (count release-names)))
      (assert (= release-names switched-names))
      (assert (= 3 (:output-rows release-stats)))
      (assert (= 3 (:output-rows switched-stats)))
      {:pre-1970-release-names (count release-names)
       :pre-1970-release-stats release-stats
       :pre-1970-release-switched-names (count switched-names)
       :pre-1970-release-switched-stats switched-stats})))

(comment
  (def conn (connect))
  (def db (db conn))

  ;; Upstream opening query-stats prompt:
  ;; John Lennon has only a small number of releases in this sample, so this
  ;; query should be easy to reason about before profiling/reordering.
  (opening-release-pulls db)

  ;; Tuple binding:
  ;; "What releases are associated with the artist named John Lennon and named
  ;; Mind Games?"
  (tuple-binding-release-pulls db)

  ;; Request-map query, corresponding to upstream `d/query`.
  (request-map-release-pulls db)

  ;; Collection binding:
  ;; "What releases are associated with either Paul McCartney or George
  ;; Harrison?"
  (collection-binding-releases db)

  ;; Relation binding:
  ;; This is the next wrapper gap. The upstream Datomic form is ported above,
  ;; but the current Clojure durable Vev path rejects vector-of-tuples relation
  ;; input until Java/Clojure expose the typed relation binding helpers that
  ;; already exist lower in the native ABI/Python wrapper.
  (relation-binding-releases db)

  ;; Find specifications:
  ;; The upstream section first demonstrates a relation find missing `$` in
  ;; `:in`; Datomic raises a helpful error. Vev currently returns no rows for
  ;; that shape, so validation keeps it visible as pending error-shape parity.
  (relation-find-missing-db db)

  ;; Relation find spec:
  (relation-find-releases db)

  ;; Collection find spec:
  (collection-find-releases db)

  ;; Single tuple find spec:
  (tuple-find-artist-start db)

  ;; Scalar find spec:
  (scalar-find-artist-start db)

  ;; Return maps and Clojure destructuring:
  (return-map-releases db)
  (return-map-positional-artist db)
  (return-map-key-artist db)

  ;; Predicate expressions:
  ;; Upstream uses `(user/teste ?year)`. Vev parses the shape but does not call
  ;; arbitrary host Clojure predicates through the native query engine yet.
  (host-predicate-artists db)

  ;; Function expressions:
  (function-expression-track-minutes db)

  ;; Built-in expression functions:
  (artist-type-gender db)

  ;; Aggregations:
  (min-track-duration db)
  (sum-medium-track-count db)
  (artist-name-counts db)

  ;; Statistics:
  ;; The upstream query groups by release year and computes median, avg, and
  ;; stddev over track-title lengths. It is ported as
  ;; `track-name-statistics-query`, but is not in the smoke path yet because it
  ;; currently runs too slowly against the persistent tutorial store.
  (track-name-statistics db)

  ;; Custom Aggregates:
  ;; The upstream `(user/mode ?track-count)` example requires host aggregate
  ;; execution from the native query engine, so it remains pending.
  (custom-mode-aggregate db)

  ;; Let's go deeper:
  ;; Upstream starts with this `d/query` request map and `:io-context`.
  ;; Vev accepts the Datomic-style request map and ignores the Datomic-specific
  ;; stats context key for now.
  (tracks-by-artist-name db)

  ;; Limit releases before 1970 and inspect query stats:
  (pre-1970-release-name-stats db)

  ;; Same query with the release-name clause before the predicate:
  (pre-1970-release-name-switched-stats db)

  (.close db)
  (.close conn))
