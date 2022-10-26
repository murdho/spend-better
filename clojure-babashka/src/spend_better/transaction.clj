(ns spend-better.transaction
  (:require
    [clojure.set :as set]
    [clojure.string :as string]
    [spend-better.util :as util])
  (:import
    (java.util.regex Pattern)))

(defn- check-date-format
  "Returns date string value if it matches given format, otherwise returns nil.
  Necessary for discarding invalid values (e.g. Reserved) in date field that
  some bank statements have."
  [s date-format]
  (when (re-matches date-format s)
    s))

(defn normalize [{:keys [field-mapping date-format]} tx]
  (-> tx
      (set/rename-keys (set/map-invert field-mapping))
      (select-keys (conj (keys field-mapping) :filename))
      (update :amount string/replace \, \.)
      (update :amount parse-double)
      (update :date check-date-format date-format)))

(defn- equals-or-matches? [pattern-or-value v]
  (if (instance? Pattern pattern-or-value)
    (re-matches pattern-or-value v)
    (= 0 (compare pattern-or-value v))))

(defn categorize [categories tx]
  (let [matches? (fn [rule]
                   (cond
                     (map? rule) (every? (fn [[field value]]
                                           (equals-or-matches? value (or (get tx field) ""))) rule)
                     (:other tx) (re-matches (util/re-pattern-relaxed rule) (:other tx))))]
    (loop [[[category rules] & rest] categories]
      (if category
        (if (some matches? rules)
          (assoc tx :category category)
          (recur rest))
        (assoc tx :category nil)))))
