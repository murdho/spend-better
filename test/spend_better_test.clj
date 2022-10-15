(ns spend-better-test
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [spend-better]
    [spend-better.config :as config]
    [spend-better.db :as db]
    [spend-better.util :as util]
    [clojure.pprint :as pprint]))

(defn with-test-config [t]
  (with-redefs [config/config-file (delay "config.example.edn")]
    (t)))

(use-fixtures :once with-test-config)

(deftest re-pattern-relaxed'-test
  (is (= "(?i).*abc.*" (str (util/re-pattern-relaxed "abc")))))

(deftest import-file-test
  (is (= [{:id 1,
           :date "2022-07-02",
           :other "Foodmart",
           :amount -12.34M,
           :description "Receipt #6543210987",
           :currency "EUR",
           :category "groceries"}
          {:id 2,
           :date "2022-07-05",
           :other "Bookshop P15E0E7B25",
           :amount -27.15M,
           :description "2022-07-05 13:24\\BKSHP\\JUPITER",
           :currency "EUR",
           :category "books"}
          {:id 3,
           :date "2022-08-01",
           :other "",
           :amount -1.50M,
           :description "Card (..1234) monthly fee 07-2022",
           :currency "EUR",
           :category "fees"}
          {:id 4,
           :date "2022-08-02",
           :other "Indie Platform",
           :amount 350.00M,
           :description "Sales July'22",
           :currency "EUR",
           :category "sales"}]
         (do (spend-better/import-statement "test/testdata/bank1-2022-10-03.csv")
             (db/all-bank-transactions)))))
