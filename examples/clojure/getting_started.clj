;; Copyright (c) Andreas Flakstad and Vev contributors
;; SPDX-License-Identifier: EPL-2.0

(ns getting-started
  (:require [vev.core :as vev]))

(defn delete-sqlite-files! [path]
  (doseq [suffix ["" "-wal" "-shm"]]
    (java.nio.file.Files/deleteIfExists
     (java.nio.file.Path/of (str path suffix) (make-array String 0)))))

(defn -main [& args]
  (let [lib-path (or (first args) (System/getenv "VEV_LIB") "build/lib/libvev.dylib")]
    (let [conn (vev/create-conn lib-path)]
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

      (let [db (vev/db conn)
            query (vev/prepare conn
                               '[:find ?name
                                 :in ?email
                                 :where [?e :user/email ?email]
                                        [?e :user/name ?name]])
            next-db (vev/db-with db [{:db/id 3 :user/name "Barbara"}])]
        (println
         (vev/q db
                '[:find ?name
                  :where [?e :user/name ?name]]))

        (println
         (vev/pull db
                   [:user/name {:user/friend [:user/name]}]
                   2))

        (println (vev/q db query "ada@example.com"))

        (println
         (vev/q next-db
                '[:find ?name
                  :where [?e :user/name ?name]]))))

    (let [sqlite-path "build/examples/clojure/getting-started.vev.sqlite"]
      (java.nio.file.Files/createDirectories
       (java.nio.file.Path/of "build/examples/clojure" (make-array String 0))
       (make-array java.nio.file.attribute.FileAttribute 0))
      (delete-sqlite-files! sqlite-path)
      (let [durable (vev/connect lib-path sqlite-path)
            _ (vev/transact! durable [{:db/id 1 :user/name "Durable Ada"}])
            db (vev/db durable)]
        (println
         (vev/q db
                '[:find ?name
                  :where [?e :user/name ?name]]))))))

(apply -main *command-line-args*)
