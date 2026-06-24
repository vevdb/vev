(ns vev-datascript-bench.core)

(def ^:dynamic *warmup-t*
  (Double/parseDouble (or (System/getenv "VEV_BENCH_WARMUP_MS") "500")))

(def ^:dynamic *bench-t*
  (Double/parseDouble (or (System/getenv "VEV_BENCH_MS") "1000")))

(def ^:dynamic *step*
  (Long/parseLong (or (System/getenv "VEV_BENCH_STEP") "10")))

(def ^:dynamic *repeats*
  (Long/parseLong (or (System/getenv "VEV_BENCH_REPEATS") "5")))

(def ^:dynamic *people-count*
  (Long/parseLong (or (System/getenv "VEV_BENCH_PEOPLE") "20000")))

(def names ["Ivan" "Petr" "Sergei" "Oleg" "Yuri" "Dmitry" "Fedor" "Denis"])
(def last-names ["Ivanov" "Petrov" "Sidorov" "Kovalev" "Kuznetsov" "Voronoi"])
(def sexes [:male :female])

(defn random-man [^java.util.Random rng id]
  {:db/id     id
   :name      (nth names (.nextInt rng (count names)))
   :last-name (nth last-names (.nextInt rng (count last-names)))
   :sex       (nth sexes (.nextInt rng (count sexes)))
   :age       (.nextInt rng 100)
   :salary    (.nextInt rng 100000)})

(defn people
  ([] (people 20000))
  ([n]
   (let [rng (java.util.Random. 42)]
     (shuffle (mapv #(random-man rng %) (range 1 (inc n)))))))

(def people20k (delay (people *people-count*)))

(defn now-ms []
  (/ (System/nanoTime) 1000000.0))

(defn to-fixed [n places]
  (String/format java.util.Locale/US (str "%." places "f") (object-array [(double n)])))

(defn round [n]
  (cond
    (> n 1)     (to-fixed n 1)
    (> n 0.001) (to-fixed n 2)
    :else       n))

(defn percentile [xs n]
  (-> (sort xs)
      (nth (min (dec (count xs))
                (int (* n (count xs)))))))

(defmacro dotime [duration & body]
  `(let [start-t# (now-ms)
         end-t#   (+ ~duration start-t#)]
     (loop [iterations# *step*]
       (dotimes [_# *step*]
         ~@body)
       (let [now# (now-ms)]
         (if (< now# end-t#)
           (recur (+ *step* iterations#))
           (double (/ (- now# start-t#) iterations#)))))))

(defmacro bench [& body]
  `(let [_#       (dotime *warmup-t* ~@body)
         results# (into []
                        (for [_# (range *repeats*)]
                          (dotime *bench-t* ~@body)))]
     (percentile results# 0.5)))
