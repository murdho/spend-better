(ns spend-better.db
  {:clj-kondo/config
   '{:lint-as {pod.babashka.postgresql/with-transaction next.jdbc/with-transaction}}}
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

(defn- create-import! [conn filename]
  (let [sql "INSERT INTO imports (filename) VALUES (?) RETURNING id"]
    (first (pg/execute! conn [sql filename]))))

(defn- create-bank-transactions! [conn import-id txs]
  (let [placeholders (->> "(?::date, ?, ?, ?, ?, ?, ?)"
                          (repeat (count txs))
                          (string/join ", "))
        sql (str "INSERT INTO transactions (date, other, amount, description, currency, import_id, category) "
                 "VALUES " placeholders)
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

(defn insert-bank-statement! [filename txs]
  (pg/with-transaction [conn (pg/get-connection @db)]
     (let [{import-id :imports/id} (create-import! conn filename)]
       (create-bank-transactions! conn import-id txs)
       (deduplicate-bank-transactions! conn))))
