(ns smoke
  (:require [vev.core :as vev]))

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
      (println "tx:" tx))

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
                            {:current current-rows :snapshot snapshot-rows})))))))))

(apply -main *command-line-args*)
