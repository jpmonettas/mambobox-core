(ns mambobox-core.db.datomic-component
  (:require [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [io.rkn.conformity :as c]
            [environ.core :refer [env]]
            [mambobox-core.protocols :as mambo-protocols]
            [mambobox-core.core.music :refer [normalize-entity-name-string]]
            [mambobox-core.http.handler :refer [*request-device-uniq-id*]]))


(defn transact-reified [datomic-cmp tx-data]
  (let [user (mambo-protocols/get-user-by-device-uuid datomic-cmp
                                                      *request-device-uniq-id*)]
    (d/transact (:conn datomic-cmp) (conj tx-data
                                          {:db/id (d/tempid :db.part/tx)
                                           :mb.tx/user (:db/id user)}))))


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

(defn add-song-transaction [db song-file-id id3-info]
  (let [song-artist-name (normalize-entity-name-string (or (:artist id3-info) "unnamed artist"))
        song-album-name (normalize-entity-name-string (or (:album id3-info) "unnamed album"))
        song-name (normalize-entity-name-string (or (:title id3-info) "unnamed song"))
        song-year (:year id3-info)
        artist (d/entity db [:mb.artist/name song-artist-name])
        artist-id (if artist (:db/id artist) (d/tempid :db.part/user))
        album (when artist (first (filter #(= song-album-name (:mb.album/name %))
                                          (:mb.artist/albums artist))))
        album-id (if album (:db/id album) (d/tempid :db.part/user))]
    (cond-> [[:song/add (d/tempid :db.part/user) song-file-id song-name song-year album-id]]
      (not artist) (conj [:artist/add artist-id song-artist-name])
      (not album) (conj [:album/add album-id artist-id song-album-name]))))

(extend-type MamboboxDatomicComponent
  mambo-protocols/MusicPersistence

  (add-song [datomic-cmp song-file-id id3-info]
    (let [{:keys [db-after]} @(transact-reified datomic-cmp
                                                (add-song-transaction (d/db (:conn datomic-cmp))
                                                                      song-file-id
                                                                      id3-info))]
      (d/touch (d/entity db-after [:mb.song/file-id song-file-id]))))

  (update-song [datomic-cmp song-id {:keys [song-name artist-name album-name song-year]}]
    (let [song (d/entity (d/db (:conn datomic-cmp)) song-id)
          tx-data (cond-> []
                    song-name (conj [:db/add song-id :mb.song/name (normalize-entity-name-string song-name)])
                    artist-name (conj [:db/add song-id :mb.artist/name (normalize-entity-name-string artist-name)])
                    album-name (conj [:db/add song-id :mb.album/name (normalize-entity-name-string album-name)])
                    song-year (conj [:db/add song-id :mb.song/year song-year]))
          {:keys [db-after]} @(transact-reified datomic-cmp tx-data)]
      (into {} (d/touch (d/entity db-after song-id)))))
  
  (get-artist-by-name [datomic-cmp name])
  (add-artist [datomic-cmp artist-name])

  (add-album [_ artist-id album-name])
  (get-album-by-name [_ artist-id name])
  
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
    (let [{:keys [db-after]} @(transact-reified datomic-cmp
                                                [[:db/add user-id :mb.user/nick nick]])]
      (into {} (d/touch (d/entity db-after user-id)))))

  (get-user-by-device-uuid [datomic-cmp device-uniq-id]
    (device-user (-> datomic-cmp :conn d/db) device-uniq-id)))

(extend-type MamboboxDatomicComponent
  mambo-protocols/SongTracker

  (track-song-view [datomic-cmp song-id]
    @(transact-reified datomic-cmp
                       [[:song/track-play song-id]])))

(extend-type MamboboxDatomicComponent
  mambo-protocols/SongSearch

  (search-songs-by-str [datomic-cmp str]))
