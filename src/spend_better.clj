(ns spend-better
  (:require
    [clojure.data.csv :as csv]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.set :as set]
    [clojure.string :as string]))

(defn re-pattern-relaxed' [s]
  (re-pattern (str "(?i).*" s ".*")))

(def re-pattern-relaxed (memoize re-pattern-relaxed'))

(def ^:private config (->> "config.edn"
                           slurp
                           (edn/read-string {:readers {'regex re-pattern
                                                       'rule-regex re-pattern-relaxed}})))

(defn csv-data->maps [csv-data]
  (map zipmap
       (->> (first csv-data)
            repeat)
       (rest csv-data)))

(defn filename->bank-config [filename]
  (let [[name config] (first (filter (fn [[_ config]] (re-matches (:filename config) filename))
                                     (:banks config)))]
    (when config
      (assoc config :name name))))

(defn read-statement-csv [{:keys [skip-bom]} file]
  (with-open [reader (io/reader file)]
    (when skip-bom
      (.skip reader 1))
    (->> reader
         csv/read-csv
         csv-data->maps
         (map #(assoc % :filename (.getName file)))
         doall)))

(defn normalize-transaction [{:keys [field-mapping]} tx]
  (-> tx
      (set/rename-keys (set/map-invert field-mapping))
      (select-keys (conj (keys field-mapping) :filename))
      (update :amount string/replace \, \.)
      (update :amount parse-double)))

(defn equals? [pattern-or-string s]
  (if (string? pattern-or-string)
    (= pattern-or-string s)
    (re-matches pattern-or-string s)))

(defn categorize-transaction [tx]
  (let [categories (:categories config)
        matches? (fn [rule]
                   (cond
                     (map? rule) (every? (fn [[field value]]
                                           (equals? value (get tx field))) rule)
                     :else (re-matches (re-pattern-relaxed rule) (:other tx))))]
    (loop [[[category rules] & rest] categories]
      (if category
        (if (some matches? rules)
          (assoc tx :category category)
          (recur rest))
        (assoc tx :category nil)))))

(defn import-file [filepath]
  (let [file (io/file filepath)
        bank-config (filename->bank-config (.getName file))
        raw-transactions (read-statement-csv bank-config file)
        normalized-txs (map (partial normalize-transaction bank-config) raw-transactions)
        categorized (map categorize-transaction normalized-txs)]
    (run! #(prn %) categorized)))
