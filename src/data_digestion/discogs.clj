(ns data-digestion.discogs
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.java.jdbc :as sql]))

(def db-spec {:classname "org.sqlite.JDBC" :subprotocol "sqlite" :subname "resources/discogs/OUT/discogs.db"})

(defn drop-table! [tbname]
  (if (.exists (io/as-file (:subname db-spec)))
      (if (> (count (sql/query db-spec [(str "Select * from sqlite_master where type = \"table\" and name = \"" tbname "\"")])) 0)
          (sql/db-do-commands db-spec
            (sql/drop-table-ddl (keyword tbname))))))

(defn create-release-table! []
  (sql/db-do-commands db-spec
    (sql/create-table-ddl
      :release
      [[:title "varchar(256)"]
       [:artist "varchar(64)"]
       [:label "varchar(64)"]
       [:year :int]
       [:dateadded :datetime]]))) 

(defn load-release-table! [mp]
  (sql/insert-multi! db-spec :release mp))
    

(defn export-discog-tsv [file sq]
  (with-open [writer (io/writer file)]
    (.write writer (str (clojure.string/join "\t" ["Title" "Artist" "Label" "Year" "Date Added"]) "\n"))
    (doseq [i sq]
      (.write writer (str (clojure.string/join "\t" [(:title i) (:artist i) (:label i) (:year i) (:dateadded i)]) "\n")))))


(defn basic-release-info [rl]
  {:title  (clojure.string/replace (get-in rl ["basic_information" "title"]) #"(\t|\")" "")
   :artist (get-in rl ["basic_information" "artists" 0 "name"])
   :label (get-in rl ["basic_information" "labels"  0 "name"])          
   :year   (get-in rl ["basic_information" "year"])
   :dateadded (get rl "date_added")})
   

(defn -main []
  (let  [discog-json (slurp "https://api.discogs.com/users/ericcervin/collection/folders/0/releases")
         discog-map  (json/read-str discog-json)
         page-count  (get-in discog-map ["pagination" "pages"])
         page-seq (for [i (range 1 (inc page-count))]
                       (slurp (str "https://api.discogs.com/users/ericcervin/collection/folders/0/releases?per_page=50&page=" i)))
         basic-info (flatten (map #(map basic-release-info (get (json/read-str %) "releases")) page-seq))]
    
    ;;write tsv report
    (export-discog-tsv "resources/discogs/OUT/discogs_list_all.tsv" basic-info)
    
    ;;drop release table
    (drop-table! "release")
    
    ;;create release table in sqlite
    (create-release-table!)
    
    ;;insert rows in sqlite
    (load-release-table! basic-info)
    
    ;;print total rows in table
    (println (sql/query db-spec ["Select Count(*) from release"]))))
    
    ;;(sql/query db-spec ["Select substr(dateadded,0,5) as Year, substr(dateadded,6,2) as Month, Count(*) from release group by substr(dateadded,0,5), substr(dateadded,6,2)"])
    


