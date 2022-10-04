(ns spend-better.db
  {:clj-kondo/config
   '{:lint-as {pod.babashka.postgresql/with-transaction next.jdbc/with-transaction}}}
  (:require
    [camel-snake-kebab.core :as csk]
    [camel-snake-kebab.extras :as cske]
    [clojure.string :as string]
    [pod.babashka.postgresql :as pg]
    [spend-better.config :as config]))

(def ^:private db
  (delay (-> (config/get :database)
             (assoc :dbtype "postgresql"))))

(defn setup []
  (let [sql (slurp "resources/setup.sql")]
    (pg/execute! @db [sql])))

(defn- normalize-keys [result]
  (cske/transform-keys csk/->kebab-case-keyword result))

(defn- create-import! [conn {:keys [bank filename]}]
  (let [sql "INSERT INTO imports (bank, filename) VALUES (?, ?) RETURNING id"]
    (->> (pg/execute! conn [sql (name bank) filename])
         first
         normalize-keys)))

(defn- create-bank-transactions! [conn import-id txs]
  (let [placeholders (->> "(?::date, ?, ?, ?, ?, ?, ?)"
                          (repeat (count txs))
                          (string/join ", "))
        sql (str "INSERT INTO transactions (date, other, amount, description, currency, import_id, category)
                  VALUES " placeholders)
        values (mapcat (juxt :date
                             :other
                             :amount
                             :description
                             :currency
                             (constantly import-id)
                             (comp #(some-> % name) :category))
                       txs)]
    (pg/execute! conn (cons sql values))))

(defn- deduplicate-bank-transactions! [conn]
  (let [sql "DELETE FROM transactions WHERE id IN (SELECT id FROM duplicate_transactions)"]
    (pg/execute! conn [sql])))

(defn insert-bank-statement! [import txs]
  (pg/with-transaction [conn (pg/get-connection @db)]
     (let [{import-id :id} (create-import! conn import)]
       (create-bank-transactions! conn import-id txs)
       (deduplicate-bank-transactions! conn))))

(defn uncategorized-bank-transactions []
  (let [sql "SELECT date::TEXT AS date, other, amount, description, currency FROM transactions
             WHERE category IS NULL"]
    (->> (pg/execute! (pg/get-connection @db) [sql])
         normalize-keys)))
