(ns spend-better.util)

(defn- re-pattern-relaxed' [s]
  (re-pattern (str "(?i).*" s ".*")))

(def re-pattern-relaxed
  (memoize re-pattern-relaxed'))

(defn exit! [message]
  (.println *err* message)
  (System/exit 1))
