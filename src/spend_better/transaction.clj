(ns spend-better.transaction
  (:require
    [clojure.set :as set]
    [clojure.string :as string]
    [spend-better.util :as util]))

(defn normalize [{:keys [field-mapping]} tx]
  (-> tx
      (set/rename-keys (set/map-invert field-mapping))
      (select-keys (conj (keys field-mapping) :filename))
      (update :amount string/replace \, \.)
      (update :amount parse-double)))

(defn- equals-or-matches? [pattern-or-string s]
  (if (string? pattern-or-string)
    (= pattern-or-string s)
    (re-matches pattern-or-string s)))

(defn categorize [categories tx]
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
