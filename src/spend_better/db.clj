(ns spend-better.db
  (:require [spend-better.config :as config]))

(def db {:dbtype   "postgresql"
         :host     "localhost"
         :dbname   "spend_better_dev"
         :user     "postgres"
         :password ""
         :port     5432})

(def ^:private db
  (delay (-> (config/get :database)
             (assoc :dbtype "postgresql"))))

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
                             (comp #(some-> % ) :category)) txs)]
    (pg/execute! db (cons sql values))))
