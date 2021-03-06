(ns mambobox-core.db.datomic-component
  (:require [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [clj-time.core :as time]
            [clj-time.coerce :as timeco]
            [environ.core :refer [env]]
            [io.rkn.conformity :as c]
            [mambobox-core.generic-utils :as gen-utils]
            [mambobox-core.protocols :as mambo-protocols]
            [clojure.string :as str]))

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
    (println "Starting datomic cmp, connecting to " datomic-uri)
    (let [db (d/create-database datomic-uri)
          conn (d/connect datomic-uri)
          schema-norms-map (c/read-resource "schemas/mambobox-schema.edn")
          default-artist-data (c/read-resource "seed-data/artists-list.edn")]
      (c/ensure-conforms conn schema-norms-map [:mambobox/schema :mambobox/db-fns])
      (c/ensure-conforms conn default-artist-data [:mambobox/default-artists])
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
                                 [?album :mb.album/default false]
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
                                  [?artist :mb.artist/default false]
                                  [(missing? $ ?artist :mb.artist/albums)]]
                                db)
                           (map first))]
    (when-not (empty? empty-artists)
      (map (fn [ea-id] [:album/retract ea-id]) empty-artists))))



(defn add-song-transaction [db song-file-id song-info]
  (let [song-artist-name (gen-utils/normalize-entity-name-string (or (:artist song-info) "unknown"))
        song-album-name (gen-utils/normalize-entity-name-string (or (:album song-info) "unknown"))
        song-name (gen-utils/normalize-entity-name-string (or (:title song-info) (str "unknown" (rand-int 10000))))
        song-year (when (:year song-info) (Integer/parseInt (:year song-info)))
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
    (if-let [dest-artist-id (:db/id (d/entity db [:mb.artist/name (gen-utils/normalize-entity-name-string new-artist-name)]))]

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
        [[:artist/add new-artist-id (gen-utils/normalize-entity-name-string new-artist-name)]
         [:album/add new-album-id new-artist-id (:mb.album/name current-song-album)]
         [:db/retract (:db/id current-song-album) :mb.album/songs song-id]
         [:db/add new-album-id :mb.album/songs song-id]]))))

(defn update-song-album-transaction [db song-id new-album-name]
  (let [current-song-album (-> (d/entity db song-id) :mb.album/_songs first)
        current-song-artist (-> current-song-album :mb.artist/_albums first)
        song-new-album (->> (:mb.artist/albums current-song-artist)
                            (filter #(= (:mb.album/name %) (gen-utils/normalize-entity-name-string new-album-name)))
                            first)]
    (conj 
     ;; if dest album already exist
     (if song-new-album
       ;; just add the song to it and remove it from prev album
       [[:db/add (:db/id song-new-album) :mb.album/songs song-id]]

       ;; if not, create the album and add the song to it
       (let [new-album-id (d/tempid :db.part/user)]
         [[:album/add new-album-id (:db/id current-song-artist) (gen-utils/normalize-entity-name-string new-album-name)]
          [:db/add new-album-id :mb.album/songs song-id]]))

     ;; always remove from previous album
     [:db/retract (:db/id current-song-album) :mb.album/songs song-id])))

(defn get-song [db song-id]
  (let [song-album (-> (d/entity db song-id) :mb.album/_songs first)
        song-artist (-> song-album :mb.artist/_albums first)
        song (d/pull db [:db/id
                         :mb.song/name
                         :mb.song/file-id
                         :mb.song/plays-count
                         :mb.song/tags]
                     song-id)]
    (merge song
           {:artist {:db/id (:db/id song-artist)
                     :mb.artist/name (:mb.artist/name song-artist)}
            :album {:db/id (:db/id song-album)
                    :mb.album/name (:mb.album/name song-album)}
            :mb.song/url (str "/public-files/" (:mb.song/file-id song))})))

(defn ensure-artist-albums-clean [datomic-cmp]
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
      ;; keep everything clean
      (ensure-artist-albums-clean datomic-cmp)
      (get-song db-after song-id)))
  
  (update-song-album [datomic-cmp song-id new-album-name user-id]
    (let [{:keys [db-after]} @(transact-reified datomic-cmp
                                                user-id
                                                (update-song-album-transaction (d/db (:conn datomic-cmp))
                                                                               song-id
                                                                               new-album-name))]
      ;; keep everything clean
      ;; if we have this we can't use pre populated artists and albums
      (ensure-artist-albums-clean datomic-cmp)
      (get-song db-after song-id)))
  
  (update-song-name [datomic-cmp song-id new-song-name user-id]
    (let [{:keys [db-after]} @(transact-reified datomic-cmp
                                                user-id
                                                [[:db/add song-id :mb.song/name (gen-utils/normalize-entity-name-string new-song-name)]])]
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
          (map (partial get-song db)))))

  (get-user-uploaded-songs [datomic-cmp user-id]
    (let [db (d/db (:conn datomic-cmp))]
      (->> (d/q '[:find ?sid
                  :in $ ?uid
                  :where
                  [?sid :mb.song/file-id _ ?tx]
                  [?tx :mb.tx/user ?uid]]
            db
            user-id)
           (map first)
           (map (partial get-song db))))))

(extend-type MamboboxDatomicComponent
  mambo-protocols/SongTracker

  (track-song-view [datomic-cmp song-id user-id] 
    @(transact-reified datomic-cmp
                       user-id
                       [[:song/track-play song-id]])))

(def gravity 1.8)
(defn score-fn [sdate s-plays-count]
  (let [days-since-creation (time/in-days (time/interval (timeco/from-date sdate)
                                                         (time/now)))]
    (/ (inc s-plays-count)
       (Math/pow (inc days-since-creation) gravity))))

(defn fuzzy-query-terms [q]
  (->> (str/split q #" ")
       (map #(str % "~"))
       (reduce str)))

(extend-type MamboboxDatomicComponent
  mambo-protocols/SongSearch

  (search-songs-by-str [datomic-cmp qstr]
    (let [db (d/db (:conn datomic-cmp))]
      (->> (d/q '[:find ?sid ?score
                  :in $ % ?qstr
                  :where
                  (search ?qstr ?sid ?score)
                  ]
                db
                '[[(search ?q ?sid ?score)
                   [(fulltext $ :mb.song/name ?q) [[?sid _ _ ?score]]]]
                  [(search ?q ?sid ?score)
                   [?artist :mb.artist/albums ?album]
                   [?album :mb.album/songs ?sid]
                   [(fulltext $ :mb.artist/name ?q) [[?artist _ _ ?score]]]]
                  [(search ?q ?sid ?score)
                   [?album :mb.album/songs ?sid]
                   [(fulltext $ :mb.album/name ?q) [[?album _ _ ?score]]]]]
                (fuzzy-query-terms qstr))
           (map (fn [[sid score]]
                  (let [song (get-song db sid)]
                    {:db/id (:db/id song)
                     :mb.song/name (:mb.song/name song)
                     :mb.artist/name (-> song :artist :mb.artist/name)
                     :mb.album/name (-> song :album :mb.album/name)})))
           (take 10))))

  
  (search-albums [datomic-cmp q]
    (->> (d/q '[:find ?aname ?score
                :in $ ?q
                :where
                [(fulltext $ :mb.album/name ?q) [[_ ?aname _ ?score]]]]
              (d/db (:conn datomic-cmp))
              (fuzzy-query-terms q))
         (sort-by second >)
         (map first)
         (take 5)))

  (search-artists [datomic-cmp q]
    (->> (d/q '[:find ?aname ?score
                :in $ ?q
                :where
                [(fulltext $ :mb.artist/name ?q) [[_ ?aname _ ?score]]]]
              (d/db (:conn datomic-cmp))
              (fuzzy-query-terms q))
         (sort-by second >)
         (map first)
         (take 5)))
  

  (hot-songs [datomic-cmp]
    (let [db (d/db (:conn datomic-cmp))]
     (->> (d/q '[:find ?sid ?score
                 :where
                 [?sid :mb.song/file-id _ ?tx]
                 [?sid :mb.song/plays-count ?s-plays-count]
                 [?tx :db/txInstant ?tx-time]
                 [(mambobox-core.db.datomic-component/score-fn ?tx-time ?s-plays-count) ?score]]
               db)
          (sort-by second >)
          (take 20)
          (map (fn [[id score]]
                 [(get-song db id) score])))))

  (explore-by-tag [datomic-cmp tag page]
    (let [db (d/db (:conn datomic-cmp))]
      (->> (d/q '[:find ?sid
                  :in $ ?tag
                  :where
                  [?sid :mb.song/tags ?tag]]
                db
                tag)
           (map first)
           (map (partial get-song db)))))
  
  (get-all-artists [datomic-cmp]
    (let [db (d/db (:conn datomic-cmp))
          artists-ids (map first (d/q '[:find ?aid
                                        :where
                                        [?aid :mb.artist/albums ?albid]
                                        [?albid :mb.album/songs]] db))]
      (->> (d/pull-many db [:db/id :mb.artist/name] artists-ids)
           (into #{}))))
  
  (explore-artist [datomic-cmp artist-id]
    (let [db (d/db (:conn datomic-cmp))
          albums-ids (->> (d/entity db artist-id) :mb.artist/albums (map :db/id))]
      (d/pull-many db [:db/id :mb.album/name] albums-ids)))
  
  (explore-album [datomic-cmp album-id]
    (let [db (d/db (:conn datomic-cmp))
          songs-ids (->> (d/entity db album-id) :mb.album/songs (map :db/id))]
      (map (partial get-song db) songs-ids))))
