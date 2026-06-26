(ns smoke
  (:require [vev.core :as vev]))

(defn delete-sqlite-files! [path]
  (doseq [suffix ["" "-wal" "-shm"]]
    (java.nio.file.Files/deleteIfExists
      (java.nio.file.Path/of (str path suffix) (make-array String 0)))))

(defn -main [& args]
  (when-not (= 1 (count args))
    (throw (ex-info "usage: smoke <path-to-libvev.dylib>" {})))
  (let [lib-path (first args)]
    (with-open [closed-conn (vev/create-conn lib-path)]
      (vev/transact! closed-conn [{:db/id 10 :user/name "Closed"}])
      (let [closed-db (vev/db closed-conn)]
        (.close closed-conn)
        (when-not (= #{["Closed"]}
                     (vev/q closed-db
                            '[:find ?name :where [?e :user/name ?name]]))
          (throw (ex-info "DB value did not survive connection close" {})))))

  (with-open [conn (vev/create-conn lib-path)]
    (let [tx (vev/transact! conn
               [{:db/id 1 :user/name "Ada" :user/email "ada@example.com"}
                {:db/id 2 :user/name "Grace" :user/email "grace@example.com"}])]
      (println "tx:" tx)
      (when-not (and (:ok tx) (integer? (:tx tx)) (map? (:tempids tx)))
        (throw (ex-info "unexpected transaction report" {:report tx}))))

    (let [db (vev/db conn)
          names (vev/q
                  db
                  '[:find ?name
                    :in [?email ...]
                    :where [?e :user/email ?email]
                           [?e :user/name ?name]]
                  ["ada@example.com" "grace@example.com"])]
      (println "input-collection:" names)
      (when-not (= #{["Ada"] ["Grace"]} names)
        (throw (ex-info "unexpected collection query output" {:rows names}))))

    (vev/transact! conn
      [[:db/add 90 :db/ident :user/email]
       [:db/add 90 :db/unique :db.unique/identity]])

    (vev/transact! conn
      [[:db/add 91 :db/ident :user/status]
       [:db/add 91 :db/unique :db.unique/identity]
       [:db/add 92 :db/ident :user/code]
       [:db/add 92 :db/unique :db.unique/identity]
       [:db/add 1 :user/status :active]
       [:db/add 2 :user/status :inactive]
       [:db/add 1 :user/code 1001]
       [:db/add 2 :user/code 1002]])

    (vev/transact! conn
      [[:db/add 100 :db/ident :user/friend]
       [:db/add 100 :db/valueType :db.type/ref]
       [:db/add 1 :user/friend 2]])

    (with-open [email-query (vev/prepare conn
                             '[:find ?e ?email
                               :in ?needle
                               :where [?e :user/email ?email]
                                      [(= ?email ?needle)]])]
      (let [db (vev/db conn)
            rows (vev/q db email-query "grace@example.com")]
        (println "prepared rows:" rows)
        (when-not (= #{[2 "grace@example.com"]} rows)
          (throw (ex-info "unexpected prepared rows" {:rows rows}))))

      (let [db (vev/db conn)
            pulled (vev/pull db
                     [:user/name {:user/friend [:user/name]}]
                     1)]
        (println "pull:" pulled)
        (when-not (= {:user/name "Ada"
                      :user/friend {:user/name "Grace"}}
                     pulled)
          (throw (ex-info "unexpected pull result" {:pull pulled}))))

      (let [db (vev/db conn)
            lookup-pull (vev/pull db
                          [:user/name]
                          [:user/email "ada@example.com"])
            keyword-lookup-pull (vev/pull db
                                  [:user/name]
                                  [:user/status :active])
            int-lookup-pull (vev/pull db
                              [:user/name]
                              [:user/code 1001])
            many-pull (vev/pull-many db [:user/name] [1 2])]
        (println "lookup pull:" lookup-pull)
        (println "keyword lookup pull:" keyword-lookup-pull)
        (println "int lookup pull:" int-lookup-pull)
        (println "pull many:" many-pull)
        (when-not (= {:user/name "Ada"} lookup-pull)
          (throw (ex-info "unexpected lookup-ref pull" {:pull lookup-pull})))
        (when-not (= {:user/name "Ada"} keyword-lookup-pull)
          (throw (ex-info "unexpected keyword lookup-ref pull" {:pull keyword-lookup-pull})))
        (when-not (= {:user/name "Ada"} int-lookup-pull)
          (throw (ex-info "unexpected int lookup-ref pull" {:pull int-lookup-pull})))
        (when-not (= #{"Ada" "Grace"} (set (map :user/name many-pull)))
          (throw (ex-info "unexpected pull-many" {:pull many-pull}))))

      (let [db (vev/db conn)
            immutable-report (vev/with db [{:db/id 4 :user/name "Barbara"}])
            immutable-next (vev/db-with db [{:db/id 4 :user/name "Barbara"}])]
        (println "with tx:" immutable-report)
        (when-not (and (:ok immutable-report)
                       (= #{} (vev/q db
                                      '[:find ?e
                                        :where [?e :user/name "Barbara"]])))
          (throw (ex-info "immutable with mutated source DB" {:report immutable-report})))
        (when-not (= #{[4]}
                     (vev/q immutable-next
                            '[:find ?e
                              :where [?e :user/name "Barbara"]]))
          (throw (ex-info "db-with did not produce expected DB" {})))
        (with-open [derived-conn (vev/conn-from-db immutable-next)]
          (vev/transact! derived-conn [{:db/id 5 :user/name "Dorothy"}])
          (when-not (and (= #{[4]}
                       (vev/q (vev/db derived-conn)
                              '[:find ?e
                                :where [?e :user/name "Barbara"]]))
                         (= #{[5]}
                            (vev/q (vev/db derived-conn)
                                   '[:find ?e
                                     :where [?e :user/name "Dorothy"]])))
            (throw (ex-info "conn-from-db did not initialize from DB value" {})))))

      (with-open [all-emails (vev/prepare conn
                              '[:find ?e ?email
                                :where [?e :user/email ?email]])
                  snapshot (vev/db conn)]
        (vev/transact! conn
          [{:db/id 3 :user/name "Alan" :user/email "alan@example.com"}])
        (let [current (vev/db conn)
              current-rows (count (vev/q current all-emails))
              snapshot-rows (count (vev/q snapshot all-emails))]
          (println "current-db rows:" current-rows)
          (println "snapshot-db rows:" snapshot-rows)
          (when-not (and (= 3 current-rows) (= 2 snapshot-rows))
            (throw (ex-info "unexpected snapshot row counts"
                            {:current current-rows :snapshot snapshot-rows})))))))

  (let [sqlite-path "tmp.vev.clojure.sqlite"]
    (delete-sqlite-files! sqlite-path)
    (try
      (with-open [durable (vev/connect lib-path sqlite-path)]
        (when (not= {:backend :sqlite :path sqlite-path :basis-t 0} (vev/connection-info durable))
          (throw (ex-info "unexpected durable connection metadata" {})))
        (let [tx (vev/transact! durable
                   [{:db/id 1
                     :user/name "Durable Ada"
                     :user/email "durable-ada@example.com"}])]
          (when-not (:ok tx)
            (throw (ex-info "unexpected SQLite transaction report" {:report tx}))))
        (when (not= 1 (:basis-t (vev/connection-info durable)))
          (throw (ex-info "unexpected durable basis after first tx" {})))
        (with-open [db (vev/db durable)
                    all-emails (vev/prepare durable
                                 '[:find ?e ?email
                                   :where [?e :user/email ?email]])]
          (let [rows (vev/q db all-emails)]
            (println "sqlite-live rows:" rows)
            (when-not (= 1 (count rows))
              (throw (ex-info "unexpected SQLite live row count" {:rows rows}))))))
      (with-open [durable (vev/connect lib-path sqlite-path)
                  db (vev/db durable)
                  all-emails (vev/prepare durable
                               '[:find ?e ?email
                                 :where [?e :user/email ?email]])]
        (let [rows (vev/q db all-emails)]
          (println "sqlite-reopened rows:" rows)
          (when-not (= 1 (count rows))
            (throw (ex-info "unexpected SQLite reopened row count" {:rows rows})))))
      (finally
        (delete-sqlite-files! sqlite-path))))))

(apply -main *command-line-args*)
