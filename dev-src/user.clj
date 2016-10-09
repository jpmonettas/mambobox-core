(ns user
  (:require [system.repl :refer [system set-init! start stop reset]]
            [mambobox-core.main :refer [dev-system]]
            [datomic.api :as d]
            [environ.core :refer [env]]
            [clojure.pprint :as pp]
            [mambobox-core.protocols :as protos]))

(set-init! #'dev-system)

(defn start-system! [] (start))
(defn stop-system! [] (stop))

(defn db [] (:datomic-cmp system))
(defn http-server [] (:http-server-cmp system))

(defn clear-db []
  (d/delete-database (env :datomic-uri))
  (d/create-database (env :datomic-uri))) 

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
  (q '[:find ?s ?sname ?count ?u ?unick
       :where
       [?s :mb.song/name ?sname ?tx]
       [?s :mb.song/plays-count ?count]
       [?tx :mb.tx/user ?u]
       [?u :mb.user/nick ?unick]]))



(defn e [id]
  (pp/pprint (d/touch (d/entity (d/db (:conn (db))) id))))
(comment
 @(d/transact (:conn (db))
              [{:db/id #db/id[:db.part/user]
                :db/ident :song/track-play
                :db/fn (d/function '{:lang :clojure
                                     :params [db song-id ]
                                     :code (let [song (d/entity db song-id)]
                                             [[:db/add song-id :mb.song/plays-count (inc (:mb.song/plays-count song))]])})}])
 )
