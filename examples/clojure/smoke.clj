(ns smoke
  (:import [java.nio.file Path]
           [vev Vev Vev$Entity Vev$MapValue]))

(defn map-get [^Vev$MapValue value key]
  (.get value key))

(defn -main [& args]
  (when-not (= 1 (count args))
    (throw (ex-info "usage: smoke <path-to-libvev.dylib>" {})))
  (let [vev (Vev. (Path/of (first args) (make-array String 0)))]
    (try
      (with-open [conn (.openMemory vev)]
        (let [tx (.transact conn
                   "[{:db/id 1 :user/name \"Ada\" :user/email \"ada@example.com\"}
                     {:db/id 2 :user/name \"Grace\" :user/email \"grace@example.com\"}]")]
          (println "tx:" tx))

        (let [collection (.queryText conn
                           "[:find ?name
                             :in [?email ...]
                             :where [?e :user/email ?email]
                                    [?e :user/name ?name]]"
                           "[[\"ada@example.com\" \"grace@example.com\"]]")]
          (println "input-collection:" collection)
          (when-not (and (.contains collection "\"Ada\"")
                         (.contains collection "\"Grace\""))
            (throw (ex-info "unexpected collection query output" {}))))

        (.transact conn
          "[[:db/add 100 :db/ident :user/friend]
            [:db/add 100 :db/valueType :db.type/ref]
            [:db/add 1 :user/friend 2]]")

        (with-open [email-query (.prepare vev
                                 "[:find ?e ?email
                                   :in ?needle
                                   :where [?e :user/email ?email]
                                          [(= ?email ?needle)]]")
                    stmt (.statement email-query)]
          (let [rows (with-open [result (.query conn (.bindString stmt "grace@example.com"))]
                       (.rows result))]
            (println "statement rows:" rows)
            (when-not (= [[(Vev$Entity. 2) "grace@example.com"]] (mapv vec rows))
              (throw (ex-info "unexpected statement rows" {:rows rows}))))

          (with-open [collection-query (.prepare vev
                                        "[:find ?name
                                          :in [?email ...]
                                          :where [?e :user/email ?email]
                                                 [?e :user/name ?name]]")
                      collection-stmt (.statement collection-query)]
            (let [rows (with-open [result (.query conn (.bindStringCollection
                                                         collection-stmt
                                                         (into-array String ["ada@example.com" "grace@example.com"])))]
                         (.rows result))
                  names (sort (map first rows))]
              (println "statement collection names:" names)
              (when-not (= ["Ada" "Grace"] (vec names))
                (throw (ex-info "unexpected collection statement rows" {:rows rows})))))

          (with-open [pull-query (.prepare vev
                                  "[:find (pull ?e [:user/name {:user/friend [:user/name]}])
                                    :where [?e :user/name \"Ada\"]]")]
            (let [pulled (with-open [result (.query conn pull-query "[]")]
                           (.scalar result))
                  friend (map-get pulled ":user/friend")]
              (println "pull:" pulled)
              (when-not (and (= "Ada" (map-get pulled ":user/name"))
                             (= "Grace" (map-get friend ":user/name")))
                (throw (ex-info "unexpected pull result" {:pull pulled})))))

          (with-open [all-emails (.prepare vev "[:find ?e ?email :where [?e :user/email ?email]]")
                      snapshot (.db conn)]
            (.transact conn "[{:db/id 3 :user/name \"Alan\" :user/email \"alan@example.com\"}]")
            (let [current-rows (with-open [result (.query conn all-emails "[]")]
                                 (.rowCount result))
                  snapshot-rows (with-open [result (.query snapshot all-emails "[]")]
                                  (.rowCount result))]
              (println "current-db rows:" current-rows)
              (println "snapshot-db rows:" snapshot-rows)
              (when-not (and (= 3 current-rows) (= 2 snapshot-rows))
                (throw (ex-info "unexpected snapshot row counts"
                                {:current current-rows :snapshot snapshot-rows})))))))
      (finally
        (.close vev)))))

(apply -main *command-line-args*)
