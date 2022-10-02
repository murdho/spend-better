(ns spend-better.config
  (:refer-clojure :exclude [get])
  (:require
    [clojure.edn :as edn]
    [spend-better.util :as util]))

(def ^:private config-file "config.edn")

(def ^:private config
  (delay (->> config-file
              slurp
              (edn/read-string {:readers {'regex re-pattern
                                          'rule-regex util/re-pattern-relaxed}}))))

(defn filename->bank [filename]
  (let [[name config] (first
                        (filter (fn [[_ config]] (re-matches (:filename config) filename))
                                (:banks @config)))]
    (if-not config
      (throw (ex-info (str "bank config not found for filename " filename) {}))
      (assoc config :name name))))

(defn get [& ks]
  (let [not-found (gensym)
        value (get-in @config ks not-found)]
    (if (= value not-found)
      (throw (ex-info (str "config property not found at path " ks) {}))
      value)))
