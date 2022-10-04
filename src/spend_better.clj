(ns spend-better
  (:require
    [clojure.java.io :as io]
    [clojure.pprint :as pprint]
    [spend-better.config :as config]
    [spend-better.db :as db]
    [spend-better.import :as import]
    [spend-better.transaction :as bank-transaction]
    [spend-better.util :as util]
    [clojure.string :as string]))

(defn import-statement [filepath]
  (let [file (io/file filepath)
        filename (.getName file)
        bank-config (config/filename->bank filename)
        import {:bank (:name bank-config)
                :filename filename}
        txs (import/read-statement-file bank-config file)]
    (db/insert-bank-statement! import txs)))

(defn categorize-transactions
  ([]
   (categorize-transactions false))
  ([save]
   (let [categories (config/get :categories)
         cols [:date :other :amount :description :currency :category]
         uncategorized (db/uncategorized-bank-transactions)
         categorized (->> uncategorized
                          (map (partial bank-transaction/categorize categories))
                          (map #(update % :description util/truncate 60))
                          (map #(update % :category (fnil name ""))))
         with-category (filter :category categorized)]
     (pprint/print-table cols categorized)
     (when (and (= save "save") (seq with-category))
       (db/update-categories! with-category)
       (println "-----------------------\n  Saved successfully!\n-----------------------")))))
