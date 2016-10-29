(ns user
  (:require [system.repl :refer [system set-init! start stop reset]]
            [mambobox-core.main :refer [dev-system]]
            [datomic.api :as d]
            [environ.core :refer [env]]
            [mambobox-core.generic-utils :as gen-utils]
            [clojure.pprint :as pp]
            [mambobox-core.protocols :as protos]
            [mambobox-core.utils :as utils]))

(set-init! #'dev-system)

(defn start-system! [] (start))
(defn stop-system! [] (stop))

(defn db [] (:datomic-cmp system))
(defn http-server [] (:http-server-cmp system))

(defn clear-db []
  (d/delete-database (env :datomic-uri))
  (d/create-database (env :datomic-uri)))

(defn load-initial-artists-albums []
  (let [all-artists (with-open [r (java.io.PushbackReader. (clojure.java.io/reader "./doc/artists-list.edn"))]
          (binding [*read-eval* false]
            (read r)))
        tx-data (doall
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
                  all-artists))]
    @(mambobox-core.db.datomic-component/transact-reified (user/db) 17592186045437 tx-data)))

(defn q [query]
  (pp/print-table
   (map #(zipmap (range) %)
        (d/q query (d/db (:conn (db)))))))

(defn qdevices []
  (q '[:find ?d ?dui ?un ?u
       :where
       [?d :mb.device/uniq-id ?dui]
       [?u :mb.user/devices ?d]
       [?u :mb.user/nick ?un]]))

(defn qsongs []
  (q '[:find ?s ?sname ?artistn ?albumn ?sfid ?count ?u ?unick
       :where
       [?s :mb.song/name ?sname ?tx]
       [?s :mb.song/file-id ?sfid]
       [?artist :mb.artist/albums ?album]
       [?album :mb.album/songs ?s]
       [?artist :mb.artist/name ?artistn]
       [?album :mb.album/name ?albumn]
       [?s :mb.song/plays-count ?count]
       [?tx :mb.tx/user ?u]
       [?u :mb.user/nick ?unick]]))

(defn view-artist []
  (utils/view-artists (d/db (:conn (db)))))

(defn e [id]
  (pp/pprint (d/touch (d/entity (d/db (:conn (db))) id))))

(comment
  
  )
