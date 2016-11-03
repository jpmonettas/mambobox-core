(ns mambobox-core.utils
  (:require [clojure.java.io :as io]
            [datomic.api :as d]
            [clojure.string :as str]
            [mambobox-core.protocols :as protos]
            [mambobox-core.generic-utils :as gen-utils]
            [system.repl :refer [system]])
  (:import datomic.Util))

(defn read-schema [path]
  (first (Util/readAll (io/reader (io/resource path)))))


;; (comment

;;   (defn load-initial-artists-albums [user-id]
;;   (let [all-artists (with-open [r (java.io.PushbackReader. (clojure.java.io/reader "./doc/artists-list.edn"))]
;;           (binding [*read-eval* false]
;;             (read r)))
;;         tx-data (doall
;;                  (map
;;                   (fn [{:keys [artist-name albums]}]
;;                     {:db/id (d/tempid :db.part/user)
;;                      :mb.artist/name (gen-utils/normalize-entity-name-string artist-name)
;;                      :mb.artist/default true
;;                      :mb.artist/albums (doall
;;                                         (map
;;                                          (fn [album]
;;                                            {:db/id (d/tempid :db.part/user)
;;                                             :mb.album/name (gen-utils/normalize-entity-name-string album)
;;                                             :mb.album/default true})
;;                                          albums))})
;;                   all-artists))]
;;     (spit "./resources/artists-lst.edn" (with-out-str (clojure.pprint/pprint tx-data)))
;;     ;;@(mambobox-core.db.datomic-component/transact-reified (:datomic-cmp system) user-id tx-data)
;;     ))

;;   (def all (with-open [r (java.io.PushbackReader. (clojure.java.io/reader "/home/jmonetta/mambolist-final.edn"))]
;;              (binding [*read-eval* false]
;;                (read r))))

;;   (defn clean-album-name [album-name]
;;     (-> album-name
;;         (str/replace #"Download" "")
;;         (str/replace #"&amp;" "&")
;;         (str/replace #"&amp;" "&")
;;         (str/replace #"\.rar.*" "")
;;         (str/replace #"\.zip.*" "")
;;         (str/replace #"á" "a")
;;         (str/replace #"é" "e")
;;         (str/replace #"í" "i")
;;         (str/replace #"ó" "o")
;;         (str/replace #"ú" "u")
;;         (str/replace #".+?[-–—]+" "")
;;         (.trim)))
  
;;   (spit "/home/jmonetta/mambolist-final-2.edn"
;;         (with-out-str 
;;           (clojure.pprint/pprint
;;            (map (fn [a]
;;                   (update a :albums
;;                           (fn [albums]
;;                             (->> albums
;;                                  (map (fn [alb]
;;                                         (clean-album-name (:album-name alb))))
;;                                  (remove #(= % ""))
;;                                  (into #{})))))
;;                 all))))

  

;;  )
