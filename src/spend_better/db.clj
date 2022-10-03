(ns spend-better.db
  (:require
    [clojure.string :as string]
    [pod.babashka.postgresql :as pg]
    [spend-better.config :as config])
  (:import
    (java.util UUID)))

(def ^:private db
  (delay (-> (config/get :database)
             (assoc :dbtype "postgresql"))))

(defn insert-transactions! [txs]
  (let [import-uuid (str (UUID/randomUUID))
        placeholders (->> "(?::date, ?, ?, ?, ?, ?, ?, ?)"
                          (repeat (count txs))
                          (string/join ", "))
        sql (str "INSERT INTO transactions (date, other, amount, description, currency, filename, category, import_uuid) "
                 "VALUES " placeholders)
        values (mapcat (juxt :date
                             :other
                             :amount
                             :description
                             :currency
                             :filename
                             (comp #(some-> % name) :category)
                             (constantly import-uuid))
                       txs)]
    (pg/execute! @db (cons sql values))))
