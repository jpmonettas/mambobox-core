(ns mambobox-core.db.datomic-component
  (:require [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [io.rkn.conformity :as c]
            [environ.core :refer [env]]
            [mambobox-core.protocols :as mambo-protocols]
            [mambobox-core.core.music :refer [normalize-entity-name-string]]))

(defrecord MamboboxDatomicComponent [conn datomic-uri]

  component/Lifecycle
  
  (start [component]
    (let [db (d/create-database datomic-uri)
          conn (d/connect datomic-uri)
          schema-norms-map (c/read-resource "schemas/mambobox-schema.edn")]
      (c/ensure-conforms conn schema-norms-map [:mambobox/schema :mambobox/db-fns])
      (assoc component :conn conn)))
  (stop [component]
    (when conn (d/release conn))
    (assoc component :conn nil)))

(defn new-mambobox-datomic-cmp [datomic-uri]
  (map->MamboboxDatomicComponent {:datomic-uri datomic-uri}))

(defn add-song-transaction [db song-file-id id3-info user-id]
  (let [song-artist-name (normalize-entity-name-string (:artist id3-info))
        song-album-name (normalize-entity-name-string (:album id3-info))
        song-name (normalize-entity-name-string (:title id3-info))
        song-year (:year id3-info)
        artist (d/entity db [:mb.artist/name song-artist-name])
        artist-id (if artist (:db/id artist) (d/tempid :db.part/user))
        album (when artist (first (filter #(= song-album-name (:mb.album/name %))
                                          (:mb.artist/albums artist))))
        album-id (if album (:db/id album) (d/tempid :db.part/user))]
    (cond-> [[:song/add (d/tempid :db.part/user) song-file-id song-name song-year user-id album-id]]
      (not artist) (conj [:artist/add artist-id :mb.artist/name song-artist-name])
      (not album) (conj [:album/add album-id :mb.album/name song-album-name]))))

(extend-type MamboboxDatomicComponent
  mambo-protocols/MusicPersistence

  (get-artist-by-name [datomic-cmp name])
  (add-artist [datomic-cmp artist-name]
    @(d/transact (:conn datomic-cmp)
                 [[:artist/add (d/tempid :db.part/user) artist-name]]))

  (add-album [_ artist-id album-name])
  (get-album-by-name [_ artist-id name])

  
  (add-song [datomic-cmp song-file-id id3-info user-id]
    (let [{:keys [db-after]} @(d/transact (:conn datomic-cmp)
                                          (add-song-transaction (d/db (:conn datomic-cmp))
                                                                song-file-id
                                                                id3-info
                                                                user-id))]
      (d/touch (d/entity [:mb.song/file-id song-file-id]))))
  
  (update-song-album [_ song-id album-id])
  (update-song-artist [_ song-id artist-id])
  (add-song-tag [_ song-id tag user-id])
  (get-song [_ song-id]))


(defn device-user [db device-uniq-id]
  (let [user-id (d/q '[:find ?u .
                       :in $ ?d-uid
                       :where
                       [?d :mb.device/uniq-id ?d-uid]
                       [?u :mb.user/devices ?d]]
                     db
                     device-uniq-id)]
    (when user-id
     (d/touch (d/entity db user-id)))))

(defn register-device-transaction [db #:mb.device{:keys [uniq-id country locale]} user-id]
  (if user-id
    [[:device/add (d/tempid :db.part/user) uniq-id locale country user-id]]
    (let [tmp-user-id (d/tempid :db.part/user)]
      [[:device/add (d/tempid :db.part/user) uniq-id locale country tmp-user-id]
       [:db/add tmp-user-id :mb.user/nick (str "anonymous" (rand-int 1000))]])))

(extend-type MamboboxDatomicComponent
  mambo-protocols/UserPersistence

  (add-device [datomic-cmp device-info user-id]
    (let [conn (:conn datomic-cmp)
          db (d/db conn)
          {:keys [db-after]} @(d/transact conn (register-device-transaction db device-info user-id))]
      (device-user db-after (:mb.device/uniq-id device-info))))
  
  (update-user-nick [datomic-cmp user-id nick]
    (let [{:keys [db-after]} @(d/transact (:conn datomic-cmp)
                                          [[:db/add user-id :mb.user/nick nick]])]
      (into {} (d/touch (d/entity db-after user-id)))))

  (get-user-by-device-uuid [datomic-cmp device-uniq-id]
    (device-user (-> datomic-cmp :conn d/db) device-uniq-id)))

(extend-type MamboboxDatomicComponent
  mambo-protocols/SongTracker

  (track-song-view [datomic-cmp song-id user-id]))

(extend-type MamboboxDatomicComponent
  mambo-protocols/SongSearch

  (search-songs-by-str [datomic-cmp str]))
