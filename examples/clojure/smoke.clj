(ns smoke
  (:require [vev.core :as vev]))

(defn -main [& args]
  (when-not (= 1 (count args))
    (throw (ex-info "usage: smoke <path-to-libvev.dylib>" {})))
  (with-open [conn (vev/open (first args))]
    (let [tx (vev/transact! conn
               [{:db/id 1 :user/name "Ada" :user/email "ada@example.com"}
                {:db/id 2 :user/name "Grace" :user/email "grace@example.com"}])]
      (println "tx:" tx))

    (let [names (vev/q conn
                  '[:find ?name
                    :in [?email ...]
                    :where [?e :user/email ?email]
                           [?e :user/name ?name]]
                  ["ada@example.com" "grace@example.com"])]
      (println "input-collection:" names)
      (when-not (= [["Ada"] ["Grace"]] names)
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
      (let [rows (vev/q conn email-query "grace@example.com")]
        (println "prepared rows:" rows)
        (when-not (= [[2 "grace@example.com"]] rows)
          (throw (ex-info "unexpected prepared rows" {:rows rows}))))

      (let [pulled (vev/scalar conn
                     '[:find (pull ?e [:user/name {:user/friend [:user/name]}])
                       :where [?e :user/name "Ada"]])]
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
        (let [current-rows (count (vev/q conn all-emails))
              snapshot-rows (count (vev/q snapshot all-emails))]
          (println "current-db rows:" current-rows)
          (println "snapshot-db rows:" snapshot-rows)
          (when-not (and (= 3 current-rows) (= 2 snapshot-rows))
            (throw (ex-info "unexpected snapshot row counts"
                            {:current current-rows :snapshot snapshot-rows}))))))))

(apply -main *command-line-args*)
