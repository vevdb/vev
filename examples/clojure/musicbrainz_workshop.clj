;; Copyright (c) Andreas Flakstad and Vev contributors
;; SPDX-License-Identifier: EPL-2.0

(ns musicbrainz-workshop
  (:require [vev.core :as d]))

;; Port source of truth:
;; build/upstream/day-of-datomic-conj/src/music_brainz.clj
;; Sections: file top through Pattern inputs.
;; build/upstream/day-of-datomic/tutorial/pull.clj
;; Sections: setup through dynamic pattern input.

(def default-uri "build/musicbrainz/vev-mbrainz-tutorial.sqlite")

(def led-zeppelin [:artist/gid #uuid "678d88b2-87b0-403b-b63d-5da7465aecc3"])
(def mccartney [:artist/gid #uuid "ba550d0e-adac-4864-b88b-407cab5e76af"])
(def dark-side-of-the-moon [:release/gid #uuid "24824319-9bb8-3d1e-a2c5-b8b864dafd1b"])
(def dylan-harrison-sessions [:release/gid #uuid "67bbc160-ac45-4caf-baae-a7e9f5180429"])
(def concert-for-bangla-desh [:release/gid #uuid "f3bdff34-9a85-4adc-a014-922eef9cdaa5"])

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

(def pre-1970-track-titles-broad-query
  '[:find ?title
    :in $ ?artist-name
    :where
    [?a :artist/name ?artist-name]
    [?r :release/artists ?a]
    [?r :release/year ?year]
    [?r :release/name ?name]
    [(< ?year 1970)]
    [?t :track/artists ?a]
    [?t :track/name ?title]])

(def pre-1970-track-titles-with-release-name-query
  '[:find ?title
    :in $ ?artist-name
    :where
    [?a :artist/name ?artist-name]
    [?r :release/artists ?a]
    [?r :release/year ?year]
    [?r :release/name ?name]
    [(< ?year 1970)]
    [?r :release/media ?m]
    [?m :medium/tracks ?t]
    [?t :track/name ?title]])

(def pre-1970-track-titles-query
  '[:find ?title
    :in $ ?artist-name
    :where
    [?a :artist/name ?artist-name]
    [?r :release/artists ?a]
    [?r :release/year ?year]
    [(< ?year 1970)]
    [?r :release/media ?m]
    [?m :medium/tracks ?t]
    [?t :track/name ?title]])

(def track-release-rules
  '[[(track-release ?t ?r)
     [?m :medium/tracks ?t]
     [?r :release/media ?m]]])

(def track-release-rule-query
  '[:find ?title ?album ?year
    :in $ % ?artist-name
    :where
    [?a :artist/name ?artist-name]
    [?t :track/artists ?a]
    [?t :track/name ?title]
    (track-release ?t ?r)
    [?r :release/name ?album]
    [?r :release/year ?year]])

(def track-artist-rules
  '[[(track-artist ?t ?artist-name)
     [?t :track/artists ?a]
     [?a :artist/name ?artist-name]]])

(def track-release-detail-rules
  '[[(track-release ?t ?release-name ?release-year)
     [?m :medium/tracks ?t]
     [?r :release/media ?m]
     [?r :release/name ?release-name]
     [?r :release/year ?release-year]]])

(def track-info-rules
  (concat
   track-artist-rules
   track-release-detail-rules
   '[[(track-info ?t ?artist-name ?release-name ?release-year)
      (track-artist ?t ?artist-name)
      (track-release ?t ?release-name ?release-year)]]))

(def track-rules-exercise-query
  '[:find ?track-name ?artist-name ?release-name ?release-year
    :in $ % ?track-name
    :where
    [?t :track/name ?track-name]
    (track-artist ?t ?artist-name)
    (track-release ?t ?release-name ?release-year)])

(def track-info-rule-query
  '[:find ?track-name ?artist-name ?release-name ?release-year
    :in $ % ?track-name
    :where
    [?t :track/name ?track-name]
    (track-info ?t ?artist-name ?release-name ?release-year)])

(def pull-release-name-query
  '[:find (pull ?e [:release/name])
    :in $ ?artist-name
    :where
    [?a :artist/name ?artist-name]
    [?e :release/artists ?a]])

(def pull-release-name-dynamic-pattern-query
  '[:find (pull ?e pattern)
    :in $ ?artist-name pattern
    :where
    [?a :artist/name ?artist-name]
    [?e :release/artists ?a]])

(def pull-release-and-artist-query
  '[:find (pull ?e [:release/name]) (pull ?a [*])
    :in $ ?artist-name pattern
    :where
    [?a :artist/name ?artist-name]
    [?e :release/artists ?a]])

(def pull-release-name-and-artists-query
  '[:find (pull ?e [:release/name :release/artists])
    :in $ ?artist-name pattern
    :where
    [?a :artist/name ?artist-name]
    [?e :release/artists ?a]])

(def duplicate-pull-release-query
  '[:find (pull ?e [:release/name]) (pull ?e [:release/artists])
    :in $ ?artist-name pattern
    :where
    [?a :artist/name ?artist-name]
    [?e :release/artists ?a]])

(def nested-pull-release-artists-query
  '[:find (pull ?e [:release/name
                    {:release/artists [*]}])
    :in $ ?artist-name pattern
    :where
    [?a :artist/name ?artist-name]
    [?e :release/artists ?a]])

(def nested-pull-release-artists-country-query
  '[:find (pull ?e [:release/name
                    {:release/artists [*]}
                    {:release/country [*]}])
    :in $ ?artist-name pattern
    :where
    [?a :artist/name ?artist-name]
    [?e :release/artists ?a]])

(def deep-pull-release-artists-country-query
  '[:find (pull ?e [:release/name
                    {:release/artists [*
                                       {:artist/country [*]}]}
                    {:release/country [*]}])
    :in $ ?artist-name pattern
    :where
    [?a :artist/name ?artist-name]
    [?e :release/artists ?a]])

(def direct-pull-expression-in-query-query
  '[:find [(pull ?e [:release/name]) ...]
    :in $ ?artist
    :where
    [?e :release/artists ?artist]])

(def direct-dynamic-pattern-input-query
  '[:find [(pull ?e pattern) ...]
    :in $ ?artist pattern
    :where
    [?e :release/artists ?artist]])

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

(defn query-stats-result [db query]
  (d/query {:query query
            :args [db "John Lennon"]
            :query-stats true}))

(defn pre-1970-track-title-broad-stats [db]
  (:query-stats (query-stats-result db pre-1970-track-titles-broad-query)))

(defn pre-1970-track-title-with-release-name-stats [db]
  (:query-stats (query-stats-result db pre-1970-track-titles-with-release-name-query)))

(defn pre-1970-track-title-stats [db]
  (:query-stats (query-stats-result db pre-1970-track-titles-query)))

(defn pre-1970-track-title-results [db]
  (:ret (query-stats-result db pre-1970-track-titles-query)))

(defn track-release-rule-results [db]
  (d/query {:query track-release-rule-query
            :args [db track-release-rules "John Lennon"]}))

(defn track-rules-exercise-results [db]
  (d/query {:query track-rules-exercise-query
            :args [db (concat track-artist-rules
                              track-release-detail-rules) "Yer Blues"]}))

(defn track-info-rule-results [db]
  (d/query {:query track-info-rule-query
            :args [db track-info-rules "Yer Blues"]}))

(defn pull-release-names [db]
  (d/query {:query pull-release-name-query
            :args [db "Led Zeppelin"]}))

(defn pull-release-names-dynamic-pattern [db]
  (d/query {:query pull-release-name-dynamic-pattern-query
            :args [db "Led Zeppelin" [:release/name]]}))

(defn pull-release-and-artist [db]
  (d/query {:query pull-release-and-artist-query
            :args [db "Led Zeppelin" [:release/name]]}))

(defn pull-release-name-and-artists [db]
  (d/query {:query pull-release-name-and-artists-query
            :args [db "Led Zeppelin" [:release/name]]}))

(defn duplicate-pull-release [db]
  (d/query {:query duplicate-pull-release-query
            :args [db "Led Zeppelin" [:release/name]]}))

(defn nested-pull-release-artists [db]
  (d/query {:query nested-pull-release-artists-query
            :args [db "Led Zeppelin" [:release/name]]}))

(defn nested-pull-release-artists-country [db]
  (d/query {:query nested-pull-release-artists-country-query
            :args [db "Led Zeppelin" [:release/name]]}))

(defn deep-pull-release-artists-country [db]
  (d/query {:query deep-pull-release-artists-country-query
            :args [db "Led Zeppelin" [:release/name]]}))

(defn direct-pull-expression-in-query [db]
  (d/q direct-pull-expression-in-query-query db led-zeppelin))

(defn direct-dynamic-pattern-input [db]
  (d/q direct-dynamic-pattern-input-query db led-zeppelin [:release/name]))

(defn dylan-harrison-cd [db]
  (d/q '[:find ?medium .
         :in $ ?release
         :where
         [?release :release/media ?medium]]
       db
       dylan-harrison-sessions))

(defn ghost-riders [db]
  (d/q '[:find ?track .
         :in $ ?release ?trackno
         :where
         [?release :release/media ?medium]
         [?medium :medium/tracks ?track]
         [?track :track/position ?trackno]]
       db
       dylan-harrison-sessions
       11))

(defn pull-artist-name-start [db]
  (d/pull db [:artist/name :artist/startYear] led-zeppelin))

(defn pull-artist-country [db]
  (d/pull db [:artist/country] led-zeppelin))

(defn pull-artists-by-country [db]
  (d/pull db [:artist/_country] :country/GB))

(defn pull-release-media-default [db]
  (d/pull db [:release/media] dark-side-of-the-moon))

(defn pull-release-by-media [db]
  (d/pull db [:release/_media] (dylan-harrison-cd db)))

(defn pull-track-artists [db]
  (d/pull db [:track/name {:track/artists [:db/id :artist/name]}] (ghost-riders db)))

(defn pull-release-media-tracks [db]
  (d/pull db
          [{:release/media
            [{:medium/tracks
              [:track/name {:track/artists [:artist/name]}]}]}]
          concert-for-bangla-desh))

(defn pull-release-wildcard [db]
  (d/pull db '[*] concert-for-bangla-desh))

(defn pull-track-wildcard-artists [db]
  (d/pull db '[* {:track/artists [:artist/name]}] (ghost-riders db)))

(defn pull-mccartney-default-end-year [db]
  (d/pull db '[:artist/name (:artist/endYear :default 0)] mccartney))

(defn pull-mccartney-default-end-year-string [db]
  (d/pull db '[:artist/name (:artist/endYear :default "N/A")] mccartney))

(defn pull-mccartney-absent-attribute [db]
  (d/pull db '[:artist/name :died-in-1966?] mccartney))

(defn pull-led-zeppelin-track-limit [db]
  (d/pull db '[(:track/_artists :limit 10)] led-zeppelin))

(defn pull-led-zeppelin-track-limit-names [db]
  (d/pull db '[{(:track/_artists :limit 10) [:track/name]}] led-zeppelin))

(defn pull-led-zeppelin-track-limit-names-as [db]
  (d/pull db '[{(:track/_artists :limit 10 :as "Tracks") [:track/name]}] led-zeppelin))

(defn pull-led-zeppelin-track-no-limit [db]
  (d/pull db '[(:track/_artists :limit nil)] led-zeppelin))

(defn pull-led-zeppelin-empty-results [db]
  (d/pull db '[:penguins] led-zeppelin))

(defn pull-track-artists-empty-results [db]
  (d/pull db '[{:track/artists [:penguins]}] (ghost-riders db)))

(defn validate-opening-bindings! []
  (with-open [conn (connect)
              db (db conn)]
    (let [opening (opening-release-pulls db)
          tuple (tuple-binding-release-pulls db)
          request (request-map-release-pulls db)
          collection (collection-binding-releases db)
          relation (relation-binding-releases db)]
      (assert (= 21 (count opening)))
      (assert (= 4 (count tuple)))
      (assert (= 10 (count request)))
      (assert (= 14 (count collection)))
      (assert (= 5 (count relation)))
      {:opening-release-pulls (count opening)
       :tuple-binding-release-pulls (count tuple)
       :request-map-release-pulls (count request)
       :collection-binding-releases (count collection)
       :relation-binding-releases (count relation)})))

(defn validate-find-specifications! []
  (with-open [conn (connect)
              db (db conn)]
    (let [missing-db-error (try
                             (relation-find-missing-db db)
                             nil
                             (catch Throwable error
                               (.getMessage error)))
          relation (relation-find-releases db)
          collection (collection-find-releases db)
          tuple (tuple-find-artist-start db)
          scalar (scalar-find-artist-start db)
          return-map (return-map-releases db)
          positional-artist (return-map-positional-artist db)
          key-artist (return-map-key-artist db)
          host-predicate (host-predicate-artists db)
          function-expression (function-expression-track-minutes db)]
      (assert (some? missing-db-error))
      (assert (.contains ^String missing-db-error ":in does not include $"))
      (assert (= 29 (count relation)))
      (assert (= 12 (count collection)))
      (assert (= [1940 10 9] tuple))
      (assert (= 1940 scalar))
      (assert (= 3 (count return-map)))
      (assert (= "Paul McCartney" positional-artist))
      (assert (= "Paul McCartney" key-artist))
      (assert (empty? host-predicate))
      (assert (= 71 (count function-expression)))
      {:missing-db-status :error
       :missing-db-error missing-db-error
       :relation-find-releases (count relation)
       :collection-find-releases (count collection)
       :tuple-find-artist-start tuple
       :scalar-find-artist-start scalar
       :return-map-releases (count return-map)
       :return-map-positional-artist positional-artist
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

(defn validate-pre-1970-track-stats! []
  (with-open [conn (connect)
              db (db conn)]
    (let [broad (query-stats-result db pre-1970-track-titles-broad-query)
          with-release-name (query-stats-result db pre-1970-track-titles-with-release-name-query)
          final (query-stats-result db pre-1970-track-titles-query)
          broad-stats (:query-stats broad)
          with-release-name-stats (:query-stats with-release-name)
          final-stats (:query-stats final)]
      (assert (= 70 (count (:ret broad))))
      (assert (= 17 (count (:ret with-release-name))))
      (assert (= 17 (count (:ret final))))
      (assert (= 70 (:output-rows broad-stats)))
      (assert (= 17 (:output-rows with-release-name-stats)))
      (assert (= 17 (:output-rows final-stats)))
      {:pre-1970-track-titles-broad (count (:ret broad))
       :pre-1970-track-titles-broad-stats broad-stats
       :pre-1970-track-titles-with-release-name (count (:ret with-release-name))
       :pre-1970-track-titles-with-release-name-stats with-release-name-stats
       :pre-1970-track-titles (count (:ret final))
       :pre-1970-track-titles-stats final-stats})))

(defn validate-rules-intro! []
  (with-open [conn (connect)
              db (db conn)]
    (let [release-rows (track-release-rule-results db)
          exercise-rows (track-rules-exercise-results db)
          info-rows (track-info-rule-results db)]
      (assert (= 93 (count release-rows)))
      (assert (= 3 (count exercise-rows)))
      (assert (= 3 (count info-rows)))
      {:track-release-rule-query (count release-rows)
       :track-rules-exercise (count exercise-rows)
       :track-info-rules-composition (count info-rows)
       :rules-seq-input-status :supported-by-wrapper})))

(defn validate-pattern-inputs! []
  (with-open [conn (connect)
              db (db conn)]
    (let [literal (pull-release-names db)
          dynamic (pull-release-names-dynamic-pattern db)
          release-and-artist (pull-release-and-artist db)
          name-and-artists (pull-release-name-and-artists db)
          duplicate-error (try
                            (duplicate-pull-release db)
                            nil
                            (catch Throwable error
                              (.getMessage error)))
          nested (nested-pull-release-artists db)
          nested-country (nested-pull-release-artists-country db)
          deep (deep-pull-release-artists-country db)]
      (assert (= 8 (count literal)))
      (assert (= literal dynamic))
      (assert (= 8 (count release-and-artist)))
      (assert (= 8 (count name-and-artists)))
      (assert (some? duplicate-error))
      (assert (.contains ^String duplicate-error "duplicate pull"))
      (assert (= 8 (count nested)))
      (assert (= 14 (count nested-country)))
      (assert (= 14 (count deep)))
      {:pull-release-name (count literal)
       :dynamic-pull-pattern (count dynamic)
       :pull-release-and-artist (count release-and-artist)
       :pull-release-name-and-artists (count name-and-artists)
       :duplicate-pull-same-var-status :error
       :duplicate-pull-same-var-error duplicate-error
       :nested-pull-release-artists (count nested)
       :nested-pull-release-artists-country (count nested-country)
       :deep-pull-release-artists-country (count deep)})))

(defn validate-pull-intro! []
  (with-open [conn (connect)
              db (db conn)]
    (let [artist (pull-artist-name-start db)
          country (pull-artist-country db)
          reverse-country (pull-artists-by-country db)
          release-media (pull-release-media-default db)
          reverse-media (pull-release-by-media db)
          track-artists (pull-track-artists db)
          reverse-country-count (count (:artist/_country reverse-country))]
      (assert (= "Led Zeppelin" (:artist/name artist)))
      (assert (= 1968 (:artist/startYear artist)))
      (assert (= 1000180 (get-in country [:artist/country :db/id])))
      (assert (= 482 reverse-country-count))
      (assert (= 1 (count (:release/media release-media))))
      (assert (= 1 (count (:release/_media reverse-media))))
      (assert (= "Ghost Riders in the Sky" (:track/name track-artists)))
      (assert (= #{"George Harrison" "Bob Dylan"}
                 (set (map :artist/name (:track/artists track-artists)))))
      {:pull-attribute-name artist
       :pull-artist-country country
       :pull-reverse-country-count reverse-country-count
       :pull-release-media-count (count (:release/media release-media))
       :pull-reverse-component-count (count (:release/_media reverse-media))
       :pull-map-spec-artists (count (:track/artists track-artists))})))

(defn validate-pull-nested-map! []
  (with-open [conn (connect)
              db (db conn)]
    (let [pulled (pull-release-media-tracks db)
          media (:release/media pulled)
          track-counts (mapv #(count (:medium/tracks %)) media)
          first-track (first (:medium/tracks (first media)))]
      (assert (= 6 (count media)))
      (assert (= [2 2 4 2 5 4] track-counts))
      (assert (= "Jumpin' Jack Flash / Youngblood" (:track/name first-track)))
      (assert (= ["Leon Russell"] (mapv :artist/name (:track/artists first-track))))
      {:pull-nested-map-media-count (count media)
       :pull-nested-map-track-counts track-counts
       :pull-nested-map-first-track (:track/name first-track)})))

(defn validate-pull-wildcard! []
  (with-open [conn (connect)
              db (db conn)]
    (let [pulled (pull-release-wildcard db)]
      (assert (= 13 (count pulled)))
      (assert (= "The Concert for Bangla Desh" (:release/name pulled)))
      (assert (= 1971 (:release/year pulled)))
      (assert (= #uuid "f3bdff34-9a85-4adc-a014-922eef9cdaa5" (:release/gid pulled)))
      {:pull-wildcard-attribute-count (count pulled)
       :pull-wildcard-release-name (:release/name pulled)
       :pull-wildcard-release-year (:release/year pulled)})))

(defn validate-pull-wildcard-map! []
  (with-open [conn (connect)
              db (db conn)]
    (let [pulled (pull-track-wildcard-artists db)
          artist-names (mapv :artist/name (:track/artists pulled))]
      (assert (= 6 (count pulled)))
      (assert (= "Ghost Riders in the Sky" (:track/name pulled)))
      (assert (= 11 (:track/position pulled)))
      (assert (= 218506 (:track/duration pulled)))
      (assert (= ["George Harrison" "Bob Dylan"] artist-names))
      {:pull-wildcard-map-attribute-count (count pulled)
       :pull-wildcard-map-track-name (:track/name pulled)
       :pull-wildcard-map-artists artist-names})))

(defn validate-pull-default-option! []
  (with-open [conn (connect)
              db (db conn)]
    (let [pulled (pull-mccartney-default-end-year db)]
      (assert (= "Paul McCartney" (:artist/name pulled)))
      (assert (= 0 (:artist/endYear pulled)))
      {:pull-default-option-name (:artist/name pulled)
       :pull-default-option-end-year (:artist/endYear pulled)})))

(defn validate-pull-default-option-string! []
  (with-open [conn (connect)
              db (db conn)]
    (let [pulled (pull-mccartney-default-end-year-string db)]
      (assert (= "Paul McCartney" (:artist/name pulled)))
      (assert (= "N/A" (:artist/endYear pulled)))
      {:pull-default-option-string-name (:artist/name pulled)
       :pull-default-option-string-end-year (:artist/endYear pulled)})))

(defn validate-pull-absent-attribute! []
  (with-open [conn (connect)
              db (db conn)]
    (let [pulled (pull-mccartney-absent-attribute db)]
      (assert (= "Paul McCartney" (:artist/name pulled)))
      (assert (not (contains? pulled :died-in-1966?)))
      {:pull-absent-attribute-name (:artist/name pulled)
       :pull-absent-attribute-present? (contains? pulled :died-in-1966?)})))

(defn validate-pull-explicit-limit! []
  (with-open [conn (connect)
              db (db conn)]
    (let [pulled (pull-led-zeppelin-track-limit db)
          tracks (:track/_artists pulled)]
      (assert (= 10 (count tracks)))
      (assert (every? #(contains? % :db/id) tracks))
      {:pull-explicit-limit-count (count tracks)})))

(defn validate-pull-limit-subspec! []
  (with-open [conn (connect)
              db (db conn)]
    (let [pulled (pull-led-zeppelin-track-limit-names db)
          tracks (:track/_artists pulled)
          names (mapv :track/name tracks)]
      (assert (= 10 (count tracks)))
      (assert (= "Whole Lotta Love" (first names)))
      (assert (some #{"Stairway to Heaven"} names))
      (assert (every? #(contains? % :track/name) tracks))
      {:pull-limit-subspec-count (count tracks)
       :pull-limit-subspec-first (first names)})))

(defn validate-pull-limit-subspec-as! []
  (with-open [conn (connect)
              db (db conn)]
    (let [pulled (pull-led-zeppelin-track-limit-names-as db)
          tracks (get pulled "Tracks")
          names (mapv :track/name tracks)]
      (assert (= 10 (count tracks)))
      (assert (not (contains? pulled :track/_artists)))
      (assert (= "Whole Lotta Love" (first names)))
      (assert (some #{"Stairway to Heaven"} names))
      (assert (every? #(contains? % :track/name) tracks))
      {:pull-limit-subspec-as-count (count tracks)
       :pull-limit-subspec-as-first (first names)})))

(defn validate-pull-no-limit! []
  (with-open [conn (connect)
              db (db conn)]
    (let [pulled (pull-led-zeppelin-track-no-limit db)
          tracks (:track/_artists pulled)]
      (assert (= 128 (count tracks)))
      (assert (every? #(contains? % :db/id) tracks))
      {:pull-no-limit-count (count tracks)})))

(defn validate-pull-empty-results! []
  (with-open [conn (connect)
              db (db conn)]
    (let [pulled (pull-led-zeppelin-empty-results db)]
      (assert (map? pulled))
      (assert (empty? pulled))
      {:pull-empty-results-count (count pulled)})))

(defn validate-pull-empty-results-in-collection! []
  (with-open [conn (connect)
              db (db conn)]
    (let [pulled (pull-track-artists-empty-results db)
          artists (:track/artists pulled)]
      (assert (= 2 (count artists)))
      (assert (every? map? artists))
      (assert (every? empty? artists))
      {:pull-empty-results-in-collection-count (count artists)})))

(defn validate-pull-expression-in-query! []
  (with-open [conn (connect)
              db (db conn)]
    (let [pulled (direct-pull-expression-in-query db)
          names (set (map :release/name pulled))]
      (assert (= 8 (count pulled)))
      (assert (contains? names "Led Zeppelin"))
      (assert (every? #(= #{:release/name} (set (keys %))) pulled))
      {:pull-expression-in-query-count (count pulled)
       :pull-expression-in-query-has-led-zeppelin (contains? names "Led Zeppelin")})))

(defn validate-dynamic-pattern-input! []
  (with-open [conn (connect)
              db (db conn)]
    (let [pulled (direct-dynamic-pattern-input db)
          names (set (map :release/name pulled))]
      (assert (= 8 (count pulled)))
      (assert (contains? names "Led Zeppelin"))
      (assert (every? #(= #{:release/name} (set (keys %))) pulled))
      {:dynamic-pattern-input-count (count pulled)
       :dynamic-pattern-input-has-led-zeppelin (contains? names "Led Zeppelin")})))

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

  ;; Bind track names from releases before 1970. The first form intentionally
  ;; joins all artist tracks and only uses releases for the year predicate.
  (pre-1970-track-title-broad-stats db)

  ;; Try again by following release -> media -> tracks while still binding the
  ;; release name.
  (pre-1970-track-title-with-release-name-stats db)

  ;; Final production-shaped query from the upstream section.
  (pre-1970-track-title-results db)

  ;; Rules:
  ;; The upstream intro rule, first exercise, and rules-composition section are
  ;; ported above. Vev accepts the Datomic-style `%` rules input, including
  ;; `(concat track-artist-rules track-release-detail-rules)`, and validates
  ;; composed rules through `track-info-rules`.
  track-release-rules
  (track-release-rule-results db)
  (track-rules-exercise-results db)
  (track-info-rule-results db)

  ;; Pattern inputs:
  ;; Upstream demonstrates pull expressions in `:find`, dynamic pull patterns
  ;; as `:in` parameters, legal multiple-pull forms, an invalid duplicate pull
  ;; on the same var, and nested ref navigation.
  (pull-release-names db)
  (pull-release-names-dynamic-pattern db)
  (pull-release-and-artist db)
  (pull-release-name-and-artists db)
  ;; Datomic rejects this duplicate pull on `?e`; Vev should also reject it.
  (duplicate-pull-release db)
  (nested-pull-release-artists db)
  (nested-pull-release-artists-country db)
  (deep-pull-release-artists-country db)

  ;; Direct pull tutorial:
  ;; Source of truth is `build/upstream/day-of-datomic/tutorial/pull.clj`,
  ;; from "attribute name" through "dynamic pattern input".
  (pull-artist-name-start db)
  (pull-artist-country db)
  ;; Upstream expects this reverse lookup to return artists. Vev currently
  ;; returns an empty collection for this direct pull, while the equivalent
  ;; query over `:artist/country :country/GB` has rows. Keep it visible.
  (pull-artists-by-country db)
  (pull-release-media-default db)
  (pull-release-by-media db)
  (pull-track-artists db)

  ;; Nested map specifications:
  (pull-release-media-tracks db)

  ;; Wildcard specification:
  (pull-release-wildcard db)

  ;; Wildcard + map specification:
  (pull-track-wildcard-artists db)

  ;; Default option:
  (pull-mccartney-default-end-year db)

  ;; Default option with different type:
  (pull-mccartney-default-end-year-string db)

  ;; Absent attributes are omitted from results:
  (pull-mccartney-absent-attribute db)

  ;; Explicit limit:
  (pull-led-zeppelin-track-limit db)

  ;; Limit + subspec:
  (pull-led-zeppelin-track-limit-names db)

  ;; Limit + subspec + :as option:
  (pull-led-zeppelin-track-limit-names-as db)

  ;; No limit:
  (pull-led-zeppelin-track-no-limit db)

  ;; Empty results:
  (pull-led-zeppelin-empty-results db)

  ;; Empty results in a collection:
  (pull-track-artists-empty-results db)

  ;; Pull expression in query:
  (direct-pull-expression-in-query db)

  ;; Dynamic pattern input:
  (direct-dynamic-pattern-input db)

  (.close db)
  (.close conn))
