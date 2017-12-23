(ns data-digestion.philosophy-usa
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.java.jdbc :as sql]))

(def db-spec {:classname "org.sqlite.JDBC" :subprotocol "sqlite" :subname "resources/philosophy_usa/OUT/philosophy-usa.db"})


(defn drop-completion-table! []
  (if (.exists (io/as-file "resources/philosophy_usa/OUT/philosophy-usa.db"))
      (if (> (count (sql/query db-spec ["Select * from sqlite_master where type = \"table\" and name = \"completion\""])) 0)
          (sql/db-do-commands db-spec
            (sql/drop-table-ddl :completion)))))

(defn drop-institution-table! []
  (if (.exists (io/as-file "resources/philosophy_usa/OUT/philosophy-usa.db"))
      (if (> (count (sql/query db-spec ["Select * from sqlite_master where type = \"table\" and name = \"institution\""])) 0)
          (sql/db-do-commands db-spec
            (sql/drop-table-ddl :institution)))))

(defn create-completion-table! []
  (sql/db-do-commands db-spec
    (sql/create-table-ddl
      :completion
      [[:inst "varchar(16)"]
       [:cip "varchar(16)"]
       [:all_cnt :int]])))

(defn create-institution-table! []
  (sql/db-do-commands db-spec
    (sql/create-table-ddl
      :institution
      [[:unitid "varchar(16)"]
       [:instnm "varchar(128)"]
       [:addr "varchar(128)"]
       [:city "varchar(64)"]
       [:stabbr "varchar(4)"]
       [:zip "varchar(16)"]])))
       

(defn load-completion-table! [sq]
  (sql/insert-multi! db-spec :completion sq))

(defn load-institution-table! [sq]
  (sql/insert-multi! db-spec :institution sq))
    
(defn cmpn-row-map [[inst cip _ _ _ all_cnt]] {:inst inst :cip cip :all_cnt (Integer. all_cnt)})
(defn inst-row-map [[unitid instnm addr city stabbr zip]] {:unitid unitid :instnm instnm :addr addr :city city :stabbr stabbr :zip zip})

(defn -main []
  ;;completions
  (let [completion-file (slurp "resources/philosophy_usa/in/2014_2015_Completions_CIP_38_only.csv")
        c-file-lines (clojure.string/split  completion-file #"\r\n")
        c-file-arrays (map #(clojure.string/split  % #",") c-file-lines)
        c-file-maps (map cmpn-row-map (rest c-file-arrays))]
        
    (drop-completion-table!)
    
    (create-completion-table!)
    
    (load-completion-table! c-file-maps)
  
    (println (sql/query db-spec ["Select Count(*) as completion_table_rows from completion"]))
      
    (println (sql/query db-spec ["Select Sum(all_cnt) total_completions from completion"]))
  
  ;;institutions
    (let [institution-file (slurp "resources/philosophy_usa/in/Institutions_Name_and_Addr.csv")
          i-file-lines (clojure.string/split  institution-file #"\r\n")
          i-file-arrays (map #(clojure.string/split  % #",") i-file-lines)
          i-file-maps (map inst-row-map (rest i-file-arrays))])
        
    (drop-institution-table!)
    
    (create-institution-table!)
    
    (load-institution-table! i-file-maps)
    
    
    (println (sql/query db-spec ["Select Count(*) as institution_table_rows from institution"]))))
      
      
