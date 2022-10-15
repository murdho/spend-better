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

(defn setup
  ([override-dbname]
   (let [sql (slurp "resources/setup.sql")
         conn (cond-> @db
                override-dbname (assoc :dbname override-dbname)
                :finally pg/get-connection)]
     (pg/execute! conn [sql]))))

(defn- normalize-keys [result]
  (cske/transform-keys csk/->kebab-case-keyword result))

(defn- repeat-placeholders [n s]
  (->> (repeat n s)
       (string/join ", ")))

(defn- create-import! [conn {:keys [bank filename]}]
  (let [sql "INSERT INTO imports (bank, filename) VALUES (?, ?) RETURNING id"]
    (->> (pg/execute! conn [sql (name bank) filename])
         first
         normalize-keys)))

(defn- create-bank-transactions! [conn import-id txs]
  (let [sql (str "INSERT INTO transactions (date, other, amount, description, currency, import_id, category)
                  VALUES " (repeat-placeholders (count txs) "(?::DATE, ?, ?, ?, ?, ?, ?)"))
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
  (let [sql "DELETE FROM transactions
             WHERE id IN (SELECT id FROM duplicate_transactions)"]
    (pg/execute! conn [sql])))

(defn insert-bank-statement! [import txs]
  (pg/with-transaction [conn (pg/get-connection @db)]
    (let [{import-id :id} (create-import! conn import)]
      (create-bank-transactions! conn import-id txs)
      (deduplicate-bank-transactions! conn))))

(defn update-categories! [txs]
  (let [sql (str "UPDATE transactions SET category = tx.category FROM (VALUES "
                 (repeat-placeholders (count txs) "(?, ?)")
                 ") AS tx(id, category) WHERE transactions.id = tx.id")
        values (mapcat (juxt :id
                             (comp #(some-> % name) :category)) txs)]
    (pg/execute! (pg/get-connection @db) (cons sql values))))

(defn uncategorized-bank-transactions []
  (let [sql "SELECT id, date::TEXT AS date, other, amount, description, currency
             FROM transactions
             WHERE category IS NULL
             ORDER BY date, amount"]
    (->> (pg/execute! (pg/get-connection @db) [sql])
         normalize-keys)))

(defn all-bank-transactions []
  (let [sql "SELECT id, date::TEXT AS date, other, amount, description, currency, category
             FROM transactions
             ORDER BY date, amount"]
    (->> (pg/execute! (pg/get-connection @db) [sql])
         normalize-keys)))

(defn aggregated-transactions []
  (let [sql "SELECT to_char(date, 'YYYY-MM') AS month, category, sum(amount) AS amount
             FROM transactions
             GROUP BY 1, 2
             ORDER BY 3"
        excluded-categories (set (map name (config/get :excluded-categories)))]
    (->> (pg/execute! (pg/get-connection @db) [sql])
         normalize-keys
         (remove #(excluded-categories (:category %))))))

(defn transactions-for-month [month]
  (let [sql "SELECT id, date::TEXT AS date, other, amount, description, currency, category
             FROM transactions
             WHERE to_char(date, 'YYYY-MM') = ?
             ORDER BY lower(category), date"]
    (->> (pg/execute! (pg/get-connection @db) [sql month])
         normalize-keys)))
