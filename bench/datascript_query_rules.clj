(require '[datascript.core :as d])

(def schema
  {:follows {:db/valueType :db.type/ref
             :db/cardinality :db.cardinality/many}})

(def rules
  '[[(reachable ?x ?y)
     [?x :follows ?y]]
    [(reachable ?x ?y)
     [?x :follows ?t]
     (reachable ?t ?y)]])

(defn elapsed-us [start]
  (/ (double (- (System/nanoTime) start)) 1000.0))

(defn median [xs]
  (nth (sort xs) (quot (count xs) 2)))

(defn seed-chain [n]
  (vec
    (concat
      (mapcat
        (fn [i]
          [{:db/id i :name (str "node-" i)}
           {:db/id i :follows (inc i)}])
        (range 1 n))
      [{:db/id n :name (str "node-" n)}])))

(defn seed-tree [n width]
  (vec
    (mapcat
      (fn [i]
        (let [children (filter #(<= % n)
                               (map #(+ (* (dec i) width) % 1)
                                    (range 1 (inc width))))]
          (if (seq children)
            [{:db/id i :name (str "node-" i) :follows (vec children)}]
            [{:db/id i :name (str "node-" i)}])))
      (range 1 (inc n)))))

(defn seed-people [n]
  (vec
    (map
      (fn [i]
        {:db/id i
         :email (str "user-" i "@example.com")
         :name (str "User " i)
         :age (mod i 100)})
      (range 1 (inc n)))))

(defn db-with [tx]
  (d/db-with (d/empty-db schema) tx))

(defn time-query [f]
  (dotimes [_ 2]
    (count (f)))
  (let [samples
        (doall
          (for [_ (range 5)]
            (let [start (System/nanoTime)
                  result (f)
                  rows (count result)]
              {:elapsed-us (elapsed-us start)
               :rows rows})))]
    {:elapsed-us (median (map :elapsed-us samples))
     :rows (:rows (first samples))}))

(defn print-result [workload n measured]
  (println
    (format
      "engine=datascript workload=%s n=%d ok=true rows=%d elapsed_us=%.0f steps= clauses= candidates= rule_calls= rule_iterations= max_bindings="
      workload
      n
      (:rows measured)
      (:elapsed-us measured))))

(defn run-chain-from-root [n]
  (let [db (db-with (seed-chain n))
        measured (time-query (fn []
                               (d/q '[:find ?y :in $ % ?x :where (reachable ?x ?y)]
                                    db rules 1)))]
    (print-result "chain-root" n measured)))

(defn run-chain-all [n]
  (let [db (db-with (seed-chain n))
        measured (time-query (fn []
                               (d/q '[:find ?x ?y :in $ % :where (reachable ?x ?y)]
                                    db rules)))]
    (print-result "chain-all" n measured)))

(defn run-tree-from-root [n]
  (let [db (db-with (seed-tree n 3))
        measured (time-query (fn []
                               (d/q '[:find ?y :in $ % ?x :where (reachable ?x ?y)]
                                    db rules 1)))]
    (print-result "tree-root" n measured)))

(defn run-bad-order-join [n]
  (let [db (db-with (seed-people n))
        email (str "user-" (quot n 2) "@example.com")
        measured (time-query (fn []
                               (d/q '[:find ?name ?age
                                      :in $ ?email
                                      :where
                                      [?e :age ?age]
                                      [?e :email ?email]
                                      [?e :name ?name]]
                                    db email)))]
    (print-result "bad-order-join" n measured)))

(doseq [n [3]]
  (run-chain-from-root n))
(doseq [n []]
  (run-chain-all n))
(doseq [n []]
  (run-tree-from-root n))
(doseq [n [1000]]
  (run-bad-order-join n))
