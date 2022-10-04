(ns spend-better
  (:require
    [clojure.java.io :as io]
    [spend-better.config :as config]
    [spend-better.db :as db]
    [spend-better.import :as import]
    [spend-better.transaction :as bank-transaction]))

(defn import-file [filepath]
  (let [file (io/file filepath)
        filename (.getName file)
        bank-config (config/filename->bank filename)
        import {:bank (:name bank-config)
                :filename filename}
        txs (import/read-statement-file bank-config file)]
    (db/insert-bank-statement! import txs)))

(defn categorize-transactions []
  (let [uncategorized-txs (db/uncategorized-bank-transactions)
        categories (config/get :categories)]
    (println uncategorized-txs)
    (println (map (partial bank-transaction/categorize categories) uncategorized-txs))))
