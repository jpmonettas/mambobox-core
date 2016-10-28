(ns mambobox-core.db.datomic-component
  (:require [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [environ.core :refer [env]]
            [io.rkn.conformity :as c]
            [mambobox-core.core.music :refer [normalize-entity-name-string]]
            [mambobox-core.protocols :as mambo-protocols]))

(defn transact-reified [datomic-cmp user-id tx-data]
  (try
   (d/transact (:conn datomic-cmp) (conj tx-data
                                         {:db/id (d/tempid :db.part/tx)
                                          :mb.tx/user user-id}))

   ;; Transact exceptions comes wrapped, unwrap an re throw
   (catch java.util.concurrent.ExecutionException juce
       (throw (.getCause juce)))))


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

(defn remove-empty-ablums-transaction [db]
  (let [empty-albums (->> (d/q '[:find ?album
                             :in $
                             :where
                             [?album :mb.album/name _]
                             [(missing? $ ?album :mb.album/songs)]]
                               db)
                          (map first))]
    (when-not (empty? empty-albums)
      (map (fn [ea-id] [:album/retract ea-id]) empty-albums))))

(defn remove-empty-artists-transaction [db]
  (let [empty-artists (->> (d/q '[:find ?artist
                                  :in $
                                  :where
                                  [?artist :mb.artist/name _]
                                  [(missing? $ ?artist :mb.artist/albums)]]
                                db)
                           (map first))]
    (when-not (empty? empty-artists)
      (map (fn [ea-id] [:album/retract ea-id]) empty-artists))))



(defn add-song-transaction [db song-file-id song-info]
  (let [song-artist-name (normalize-entity-name-string (or (:artist song-info) "unknown"))
        song-album-name (normalize-entity-name-string (or (:album song-info) "unknown"))
        song-name (normalize-entity-name-string (or (:title song-info) (str "unknown" (rand-int 10000))))
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
        [[:artist/add new-artist-id (normalize-entity-name-string new-artist-name)]
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
         [[:album/add new-album-id (:db/id current-song-artist) (normalize-entity-name-string new-album-name)]
          [:db/add new-album-id :mb.album/songs song-id]]))

     ;; always remove from previous album
     [:db/retract (:db/id current-song-album) :mb.album/songs song-id])))

(defn get-song [db song-id]
  (let [song-album (-> (d/entity db song-id) :mb.album/_songs first)
        song-artist (-> song-album :mb.artist/_albums first)]
    (-> (d/pull db [:db/id
                    :mb.song/name
                    :mb.song/file-id
                    :mb.song/plays-count
                    :mb.song/tags]
                song-id)
        (assoc :artist {:db/id (:db/id song-artist)
                        :mb.artist/name (:mb.artist/name song-artist)}
               :album {:db/id (:db/id song-album)
                       :mb.album/name (:mb.album/name song-album)}))))

(defn ensure-artist-alums-clean [datomic-cmp]
  (when-let [rm-al-tx (remove-empty-ablums-transaction (d/db (:conn datomic-cmp)))]
    (let [{db-after-albums-clean :db-after} @(d/transact (:conn datomic-cmp) rm-al-tx)]
      (when-let [rm-ar-tx (remove-empty-artists-transaction db-after-albums-clean)]
        (d/transact (:conn datomic-cmp) rm-ar-tx)))))
      

(extend-type MamboboxDatomicComponent
  mambo-protocols/MusicPersistence

  (add-song [datomic-cmp song-file-id id3-info user-id]
    (let [{:keys [db-after]} @(transact-reified datomic-cmp
                                                user-id
                                                (add-song-transaction (d/db (:conn datomic-cmp))
                                                                      song-file-id
                                                                      id3-info))]
      (get-song db-after [:mb.song/file-id song-file-id])))

  (update-song-artist [datomic-cmp song-id new-artist-name user-id]
    (let [{:keys [db-after]} @(transact-reified datomic-cmp
                                                user-id
                                                (update-song-artist-transaction (d/db (:conn datomic-cmp))
                                                                                song-id
                                                                                new-artist-name))]
      (ensure-artist-alums-clean datomic-cmp)
      (get-song db-after song-id)))
  
  (update-song-album [datomic-cmp song-id new-album-name user-id]
    (let [{:keys [db-after]} @(transact-reified datomic-cmp
                                                user-id
                                                (update-song-album-transaction (d/db (:conn datomic-cmp))
                                                                               song-id
                                                                               new-album-name))]
      ;; keep everything clean
      (ensure-artist-alums-clean datomic-cmp)
      (get-song db-after song-id)))
  
  (update-song-name [datomic-cmp song-id new-song-name user-id]
    (let [{:keys [db-after]} @(transact-reified datomic-cmp
                                                user-id
                                                [[:db/add song-id :mb.song/name (normalize-entity-name-string new-song-name)]])]
      (get-song db-after song-id)))
  
  (add-song-tag [datomic-cmp song-id tag user-id]
    (let [{:keys [db-after]} @(transact-reified datomic-cmp
                                                user-id
                                                [[:db/add song-id :mb.song/tags tag]])]
      (get-song db-after song-id)))

  (remove-song-tag [datomic-cmp song-id tag user-id] 
    (let [{:keys [db-after]} @(transact-reified datomic-cmp
                                                user-id
                                                [[:db/retract song-id :mb.song/tags tag]])]
      (get-song db-after song-id)))
  
  (get-song-by-id [datomic-cmp song-id]
    (get-song (d/db (:conn datomic-cmp)) song-id)))


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
    (try 
     (let [conn (:conn datomic-cmp)
           db (d/db conn)
           {:keys [db-after]} @(d/transact conn (register-device-transaction db device-info user-id))]
       (device-user db-after (:mb.device/uniq-id device-info)))
     (catch java.util.concurrent.ExecutionException juce
       (throw (.getCause juce)))))
  
  (update-user-nick [datomic-cmp user-id nick]
    (let [{:keys [db-after]} @(transact-reified datomic-cmp
                                                user-id
                                                [[:db/add user-id :mb.user/nick nick]])]
      (into {} (d/touch (d/entity db-after user-id)))))

  (get-user-by-device-uuid [datomic-cmp device-uniq-id]
    (when device-uniq-id
     (device-user (-> datomic-cmp :conn d/db) device-uniq-id)))

  (set-user-favourite-song [datomic-cmp user-id song-id]
    @(transact-reified datomic-cmp
                       user-id
                       [[:db/add user-id :mb.user/favourite-songs song-id]])
    nil)
  
  (unset-user-favourite-song [datomic-cmp user-id song-id]
    @(transact-reified datomic-cmp
                       user-id
                       [[:db/retract user-id :mb.user/favourite-songs song-id]])
    nil)

  (get-all-user-favourite-songs [datomic-cmp user-id]
    (let [db (d/db (:conn datomic-cmp))]
     (->> (d/entity db user-id)
          :mb.user/favourite-songs
          (map :db/id)
          (map (partial get-song db))))))

(extend-type MamboboxDatomicComponent
  mambo-protocols/SongTracker

  (track-song-view [datomic-cmp song-id user-id] 
    @(transact-reified datomic-cmp
                       user-id
                       [[:song/track-play song-id]])))


(extend-type MamboboxDatomicComponent
  mambo-protocols/SongSearch

  (search-songs-by-str [datomic-cmp str]))
