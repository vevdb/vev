(ns smoke
  (:require [vev.core :as vev]))

(defn -main [& args]
  (when-not (= 1 (count args))
    (throw (ex-info "usage: smoke <path-to-libvev.dylib>" {})))
  (with-open [conn (vev/create-conn (first args))]
    (let [tx (vev/transact! conn
               [{:db/id 1 :user/name "Ada" :user/email "ada@example.com"}
                {:db/id 2 :user/name "Grace" :user/email "grace@example.com"}])]
      (println "tx:" tx))

    (let [names (with-open [db (vev/db conn)]
                  (vev/q
                    '[:find ?name
                      :in [?email ...]
                      :where [?e :user/email ?email]
                             [?e :user/name ?name]]
                    db
                    ["ada@example.com" "grace@example.com"]))]
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
      (let [rows (with-open [db (vev/db conn)]
                   (vev/q email-query db "grace@example.com"))]
        (println "prepared rows:" rows)
        (when-not (= #{[2 "grace@example.com"]} rows)
          (throw (ex-info "unexpected prepared rows" {:rows rows}))))

      (let [pulled (with-open [db (vev/db conn)]
                     (vev/pull db
                       [:user/name {:user/friend [:user/name]}]
                       1))]
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
        (let [current-rows (with-open [current (vev/db conn)]
                             (count (vev/q all-emails current)))
              snapshot-rows (count (vev/q all-emails snapshot))]
          (println "current-db rows:" current-rows)
          (println "snapshot-db rows:" snapshot-rows)
          (when-not (and (= 3 current-rows) (= 2 snapshot-rows))
            (throw (ex-info "unexpected snapshot row counts"
                            {:current current-rows :snapshot snapshot-rows}))))))))

(apply -main *command-line-args*)
