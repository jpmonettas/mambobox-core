(ns mambobox-core.utils
  (:require [clojure.java.io :as io]
            [com.walmartlabs.datascope :as ds]
            [datomic.api :as d]
            [clojure.string :as str]
            [mambobox-core.protocols :as protos]
            [mambobox-core.generic-utils :as gen-utils])
  (:import datomic.Util))

(defn read-schema [path]
  (first (Util/readAll (io/reader (io/resource path)))))

(defn view-artists [db]
  (->> (d/q '[:find ?artist-id
              :where [?artist-id :mb.artist/name]]
            db)
       (map first)
       (d/pull-many db [:mb.artist/name
                        :db/id
                        {:mb.artist/albums [:mb.album/name
                                            :db/id
                                            {:mb.album/songs [:mb.song/name
                                                              :db/id]}]}])
       (ds/view)))


(comment

  (def all (with-open [r (java.io.PushbackReader. (clojure.java.io/reader "/home/jmonetta/mambolist-final.edn"))]
             (binding [*read-eval* false]
               (read r))))

  (defn clean-album-name [album-name]
    (-> album-name
        (str/replace #"Download" "")
        (str/replace #"&amp;" "&")
        (str/replace #"&amp;" "&")
        (str/replace #"\.rar.*" "")
        (str/replace #"\.zip.*" "")
        (str/replace #"á" "a")
        (str/replace #"é" "e")
        (str/replace #"í" "i")
        (str/replace #"ó" "o")
        (str/replace #"ú" "u")
        (str/replace #".+?[-–—]+" "")
        (.trim)))
  
  (spit "/home/jmonetta/mambolist-final-2.edn"
        (with-out-str 
          (clojure.pprint/pprint
           (map (fn [a]
                  (update a :albums
                          (fn [albums]
                            (->> albums
                                 (map (fn [alb]
                                        (clean-album-name (:album-name alb))))
                                 (remove #(= % ""))
                                 (into #{})))))
                all))))

  (def all-artists
    (with-open [r (java.io.PushbackReader. (clojure.java.io/reader "./doc/artists-list.edn"))]
      (binding [*read-eval* false]
        (read r))))

  (count all-artists)

  (def tx-data (doall
                (map
                 (fn [{:keys [artist-name albums]}]
                   {:db/id (d/tempid :db.part/user)
                    :mb.artist/name (gen-utils/normalize-entity-name-string artist-name)
                    :mb.artist/albums (doall
                                       (map
                                        (fn [album]
                                          {:db/id (d/tempid :db.part/user)
                                           :mb.album/name (gen-utils/normalize-entity-name-string album)})
                                        albums))})
                 all-artists)))
  
  @(mambobox-core.db.datomic-component/transact-reified
    (user/db)
    17592186045437
    tx-data)

 )
