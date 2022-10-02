(ns spend-better.import
  (:require
    [clojure.data.csv :as csv]
    [clojure.java.io :as io]
    [clojure.set :as set]
    [clojure.string :as string]
    [spend-better.config :as config]
    [spend-better.util :as util]))

(defn- csv-data->maps [csv-data]
  (map zipmap
       (->> (first csv-data)
            repeat)
       (rest csv-data)))

(defn- normalize-transaction [{:keys [field-mapping]} tx]
  (-> tx
      (set/rename-keys (set/map-invert field-mapping))
      (select-keys (conj (keys field-mapping) :filename))
      (update :amount string/replace \, \.)
      (update :amount parse-double)))

(defn- equals-or-matches? [pattern-or-string s]
  (if (string? pattern-or-string)
    (= pattern-or-string s)
    (re-matches pattern-or-string s)))

(defn- categorize-transaction [categories tx]
  (let [matches? (fn [rule]
                   (cond
                     (map? rule) (every? (fn [[field value]]
                                           (equals-or-matches? value (get tx field))) rule)
                     (:other tx) (re-matches (util/re-pattern-relaxed rule) (:other tx))))]
    (loop [[[category rules] & rest] categories]
      (if category
        (if (some matches? rules)
          (assoc tx :category category)
          (recur rest))
        (assoc tx :category nil)))))

(defn- read-csv [file {:keys [skip-bom]}]
  (with-open [reader (io/reader file)]
    (when skip-bom
      (.skip reader 1))
    (->> reader
         csv/read-csv
         csv-data->maps
         (map #(assoc % :filename (.getName file)))
         doall)))

(defn read-statement-file [file]
  (let [bank (config/filename->bank (.getName file))
        categories (config/get :categories)]
    (->> (read-csv file bank)
         (map (partial normalize-transaction bank))
         (map (partial categorize-transaction categories)))))