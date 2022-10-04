(ns spend-better.db
  (:require
    [clojure.string :as string]
    [pod.babashka.postgresql :as pg]
    [spend-better.config :as config]))

(def ^:private db
  (delay (-> (config/get :database)
             (assoc :dbtype "postgresql"))))

(defn setup []
  (let [sql (slurp "resources/setup.sql")]
    (pg/execute! @db [sql])))

(defn insert-transactions! [txs]
  (let [placeholders (->> "(?::date, ?, ?, ?, ?, ?, ?)"
                          (repeat (count txs))
                          (string/join ", "))
        sql (str "INSERT INTO transactions (date, other, amount, description, currency, filename, category) "
                 "VALUES " placeholders)
        values (mapcat (juxt :date
                             :other
                             :amount
                             :description
                             :currency
                             :filename
                             (comp #(some-> % name) :category))
                       txs)]
    (pg/execute! @db (cons sql values))))
