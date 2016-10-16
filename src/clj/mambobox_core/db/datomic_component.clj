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

(defn add-song-transaction [db song-file-id song-info]
  (let [song-artist-name (normalize-entity-name-string (or (:artist song-info) "unknown"))
        song-album-name (normalize-entity-name-string (or (:album song-info) "unknown"))
        song-name (normalize-entity-name-string (or (:title song-info) "unknown"))
        song-year (:year song-info)
        artist (d/entity db [:mb.artist/name song-artist-name])
        artist-id (if artist (:db/id artist) (d/tempid :db.part/user))
        album (when artist (first (filter #(= song-album-name (:mb.album/name %))
                                          (:mb.artist/albums artist))))
        album-id (if album (:db/id album) (d/tempid :db.part/user))]
    (cond-> [[:song/add (d/tempid :db.part/user) song-file-id song-name song-year album-id]]
      (not artist) (conj [:artist/add artist-id song-artist-name])
      (not album) (conj [:album/add album-id artist-id song-album-name]))))

(defn update-song-artist-transaction [db song-id new-artist-name]
  (let [song (d/entity db song-id)
        current-song-album (first (:mb.album/_songs song))]
    
    ;; let's see if there is already an artist with that name
    (if-let [dest-artist-id (:db/id (d/entity db [:mb.artist/name (normalize-entity-name-string new-artist-name)]))]

      ;; if the artist exist, does it has an album with that name?
      (if-let [dest-album-id (ffirst (d/q '[:find ?dest-album-id
                                           :in $ ?song-id ?dest-artist-id
                                           :where
                                           [?dest-artist-id :mb.artist/albums ?dest-album-id]
                                           [?dest-album-id :mb.album/name ?dest-album-name]
                                           [?song-album :mb.album/songs ?song-id]
                                           [?song-album :mb.album/name ?song-album-name]
                                           [(= ?song-album-name ?dest-album-name)]]
                                         db song-id dest-artist-id))]
        
        ;; the artist already has an album with the name so just move the song there
        [[:db/retract (:db/id current-song-album) :mb.album/songs song-id]
         [:db/add dest-album-id :mb.album/songs song-id]]

        (let [new-album-id (d/tempid :db.part/user)]
          [[:album/add  new-album-id dest-artist-id (:mb.album/name current-song-album)]
           [:db/retract (:db/id current-song-album) :mb.album/songs song-id]
           [:db/add new-album-id :mb.album/songs song-id]]))
      
      ;; if not, we need to create an artist, album and move the song
      (let [new-artist-id (d/tempid :db.part/user)
            new-album-id (d/tempid :db.part/user)]
        [[:artist/add new-artist-id new-artist-name]
         [:album/add new-album-id new-artist-id (:mb.album/name current-song-album)]
         [:db/retract (:db/id current-song-album) :mb.album/songs song-id]
         [:db/add new-album-id :mb.album/songs song-id]]))))

(defn update-song-album-transaction [db song-id new-album-name]
  (let [current-song-album (-> (d/entity db song-id) :mb.album/_songs first)
        current-song-artist (-> current-song-album :mb.artist/_albums first)
        song-new-album (->> (:mb.artist/albums current-song-artist)
                            (filter #(= (:mb.album/name %) (normalize-entity-name-string new-album-name)))
                            first)]
    (conj 
     ;; if dest album already exist
     (if song-new-album
       ;; just add the song to it and remove it from prev album
       [[:db/add (:db/id song-new-album) :mb.album/songs song-id]]

       ;; if not, create the album and add the song to it
       (let [new-album-id (d/tempid :db.part/user)]
         [[:album/add new-album-id (:db/id current-song-artist) new-album-name]
          [:db/add new-album-id :mb.album/songs song-id]]))

     ;; always remove from previous album
     [:db/retract (:db/id current-song-album) :mb.album/songs song-id])))


(extend-type MamboboxDatomicComponent
  mambo-protocols/MusicPersistence

  (add-song [datomic-cmp song-file-id id3-info]
    (let [{:keys [db-after]} @(transact-reified datomic-cmp
                                                (add-song-transaction (d/db (:conn datomic-cmp))
                                                                      song-file-id
                                                                      id3-info))]
      (d/touch (d/entity db-after [:mb.song/file-id song-file-id]))))

  (update-song-artist [datomic-cmp song-id new-artist-name]
    (let [{:keys [db-after]} @(transact-reified datomic-cmp
                                                (update-song-artist-transaction (d/db (:conn datomic-cmp))
                                                                                song-id
                                                                                new-artist-name))]
      (d/touch (d/entity db-after song-id))))
  
  (update-song-album [datomic-cmp song-id new-album-name]
    (let [{:keys [db-after]} @(transact-reified datomic-cmp
                                                (update-song-album-transaction (d/db (:conn datomic-cmp))
                                                                               song-id
                                                                               new-album-name))]
      (d/touch (d/entity db-after song-id))))
  
  (update-song-name [datomic-cmp song-id new-song-name]
    (let [{:keys [db-after]} @(transact-reified datomic-cmp
                                                [[:db/add song-id :mb.song/name new-song-name]])]
      (d/touch (d/entity db-after song-id))))
  
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
