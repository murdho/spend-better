(ns spend-better
  (:require
    [clojure.java.io :as io]
    [clojure.pprint :as pprint]
    [clojure.string :as string]
    [spend-better.config :as config]
    [spend-better.db :as db]
    [spend-better.import :as import]
    [spend-better.transaction :as bank-transaction]
    [spend-better.util :as util])
  (:import (java.text SimpleDateFormat)
           (java.util Date)))

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
         with-category (filter (comp seq :category) categorized)]
     (if (and (= save "save") (seq with-category))
       (do
         (db/update-categories! with-category)
         (pprint/print-table cols with-category)
         (println "-----------------------\n  Saved successfully!\n-----------------------"))
       (pprint/print-table cols categorized)))))

(defn overview
  ([]
   (overview 60))
  ([n-months]
   (let [transactions (db/aggregated-transactions)
         ->category-row (fn [[category txs-cat]]
                          (let [category (if category (name category) "-")
                                m (->> (group-by :month txs-cat)
                                       (map (fn [[month txs-mon]]
                                              (let [month (if month (keyword month) :-)
                                                    total (->> (map :amount txs-mon)
                                                               (reduce +))]
                                                [month total])))
                                       (into {}))]
                            (assoc m :category category)))
         category-rows (->> transactions
                            (group-by :category)
                            (map ->category-row)
                            (sort-by (comp string/lower-case :category)))
         totals (->> category-rows
                     (map #(dissoc % :category))
                     (apply merge-with +))
         months (into #{} (comp (mapcat keys)
                                (remove #{:category :-})) category-rows)
         n-months (if (string? n-months) (Integer/parseInt n-months) n-months)
         cols (concat [:category :-] (->> months sort reverse (take n-months)))]
     (pprint/print-table cols (conj (vec category-rows)
                                    (assoc totals :category "TOTAL"))))))

(defn details
  ([]
   (let [current-month (.format (SimpleDateFormat. "yyyy-MM") (Date.))]
     (details current-month)))
  ([month]
   (let [transactions (->> (db/transactions-for-month month)
                           (map #(update % :description util/truncate 60)))]
     (pprint/print-table [:category :date :other :amount :description :currency :id]
                         transactions))))

(defn -main
  ([]
   (util/exit! "Usage: ..."))
  ([cmd & args]
   (case cmd
     "import" (apply import-statement args)
     "categorize" (apply categorize-transactions args)
     "overview" (apply overview args)
     "details" (apply details args)
     (util/exit! (str "ERR: unknown command: " cmd)))))
