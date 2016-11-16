(ns user
  (:require [system.repl :refer [system set-init! start stop reset]]
            [mambobox-core.main :refer [dev-system]]
            [datomic.api :as d]
            [environ.core :refer [env]]
            [mambobox-core.generic-utils :as gen-utils]
            [clojure.pprint :as pp]
            [mambobox-core.protocols :as protos]
            [mambobox-core.utils :as utils]
            [com.walmartlabs.datascope :as ds]))

(set-init! #'dev-system)

(defn start-system! [] (start))
(defn stop-system! [] (stop))

(defn db [] (:datomic-cmp system))
(defn http-server [] (:http-server-cmp system))

(defn clear-db []
  (d/delete-database (env :datomic-uri))
  (d/create-database (env :datomic-uri)))

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

(defn q [query]
  (pp/print-table
   (map #(zipmap (range) %)
        (d/q query (d/db (:conn (db)))))))

(defn qhot []
  (->> (protos/hot-songs (db))
       (map (fn [[s score]]
              {:id (:db/id s)
               :name (:mb.song/name s)
               :score score})) 
       (pp/print-table)))

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
  (view-artists (d/db (:conn (db)))))

(defn e [id]
  (pp/pprint (d/touch (d/entity (d/db (:conn (db))) id))))


(comment


  (d/q '[:find ?sid ?score
                  :in $ % ?qstr
                  :where
                  (search ?qstr ?sid ?score)
                  ]
                (d/db (:conn (db)))
                '[[(search ?q ?sid ?score)
                   [(fulltext $ :mb.song/name ?q) [[?sid _ _ ?score]]]]
                  [(search ?q ?sid ?score)
                   [?artist :mb.artist/albums ?album]
                   [?album :mb.album/songs ?sid]
                   [(fulltext $ :mb.artist/name ?q) [[?artist _ _ ?score]]]]
                  [(search ?q ?sid ?score)
                   [?album :mb.album/songs ?sid]
                   [(fulltext $ :mb.album/name ?q) [[?album _ _ ?score]]]]]
                "m*")

  (def dbb (d/create-database "datomic:free://localhost:4334/test"))
  (def conn (d/connect "datomic:free://localhost:4334/test"))
  (d/delete-database "datomic:free://localhost:4334/test")
  (d/transact conn [{:db/id #db/id[:db.part/db]
                     :db/ident :test/name
                     :db/fulltext true
                     :db/unique :db.unique/identity
                     :db/valueType :db.type/string
                     :db/cardinality :db.cardinality/one
                     :db/doc "name"
                     :db.install/_attribute :db.part/db}])

  (d/transact conn [[:db/add (d/tempid :db.part/user) :test/name "intro mayimbe"]
                    [:db/add (d/tempid :db.part/user) :test/name "intro maimbe"]
                    [:db/add (d/tempid :db.part/user) :test/name "inrot mallimbe"]])
  (d/q '[:find ?sn ?score
         :in $ ?q
         :where [(fulltext $ :test/name ?q) [[_ ?sn _ ?score]]]]
       (d/db conn)
       "inro~ mamibe~")

  (d/q '[:find ?sn ?score
         :in $ ?q
         :where [(fulltext $ :mb.song/name ?q) [[_ ?sn _ ?score]]]]
       (d/db (:conn (db)))
       "maimbe~")
  
)
