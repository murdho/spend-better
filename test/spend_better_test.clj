(ns spend-better-test
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [spend-better]))

(defn with-test-config [t]
  (with-redefs [spend-better/config-file "config.example.edn"]
    (t)))

(use-fixtures :once with-test-config)

(deftest re-pattern-relaxed'-test
  (is (= "(?i).*abc.*" (str (spend-better/re-pattern-relaxed' "abc")))))

(deftest import-file-test
  (is (= [{:filename "bank1-2022-10-03.csv", :date "2022-07-02", :other "Foodmart", :amount -12.34, :currency "EUR", :description "Receipt #6543210987", :category :groceries}
          {:filename "bank1-2022-10-03.csv", :date "2022-07-05", :other "Bookshop P15E0E7B25", :amount -27.15, :currency "EUR", :description "2022-07-05 13:24\\BKSHP\\JUPITER", :category :books}
          {:filename "bank1-2022-10-03.csv", :date "2022-08-01", :other "", :amount -1.5, :currency "EUR", :description "Card (..1234) monthly fee 07-2022", :category :fees}
          {:filename "bank1-2022-10-03.csv", :date "2022-08-02", :other "Indie Platform", :amount 350.0, :currency "EUR", :description "Sales July'22", :category :sales}]
         (spend-better/import-file "test/testdata/bank1-2022-10-03.csv"))))
