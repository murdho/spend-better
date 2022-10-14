(ns spend-better.import
  (:require
    [clojure.data.csv :as csv]
    [clojure.java.io :as io]
    [spend-better.config :as config]
    [spend-better.transaction :as bank-transaction]))

(defn- csv-data->maps [csv-data]
  (map zipmap
       (->> (first csv-data)
            repeat)
       (rest csv-data)))

(defn- read-csv [file {:keys [skip-bom separator]
                       :or {separator \,}}]
  (with-open [reader (io/reader file)]
    (when skip-bom
      (.skip reader 1))
    (-> reader
        (csv/read-csv :separator separator)
        csv-data->maps
        doall)))

(defn read-statement-file [bank-config file]
  (let [categories (config/get :categories)]
    (->> (read-csv file bank-config)
         (map (partial bank-transaction/normalize bank-config))
         (map (partial bank-transaction/categorize categories)))))
