(ns spend-better
  (:require
    [clojure.java.io :as io]
    [spend-better.import :as import]))

(defn import-file [filepath]
  (let [file (io/file filepath)
        txs (import/read-statement-file file)]
    (run! #(prn %) txs)))
