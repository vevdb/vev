;; Copyright (c) Andreas Flakstad and Vev contributors
;; SPDX-License-Identifier: EPL-2.0

(ns getting-started
  (:require [vev.core :as vev]))

(def conn (vev/create-conn))

(vev/transact! conn
               [[:db/add 100 :db/ident :user/friend]
                [:db/add 100 :db/valueType :db.type/ref]
                {:db/id 1
                 :user/name "Ada"
                 :user/email "ada@example.com"}
                {:db/id 2
                 :user/name "Grace"
                 :user/email "grace@example.com"
                 :user/friend 1}])

(def db (vev/db conn))

(println
 (vev/q '[:find ?name
          :where [?e :user/name ?name]]
        db))

(println
 (vev/pull db
           [:user/name {:user/friend [:user/name]}]
           2))

(println
 (vev/q '[:find ?name
          :in $ ?email
          :where [?e :user/email ?email]
                 [?e :user/name ?name]]
        db
        "ada@example.com"))

(def next-db
  (vev/db-with db [{:db/id 3 :user/name "Barbara"}]))

(println
 (vev/q '[:find ?name
          :where [?e :user/name ?name]]
        next-db))
