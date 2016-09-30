(ns mambobox-core.db.music
  (:require [datomic.api :as d]))


(defn add-album [{:keys [album-name artist-name]}]
  [[:album/add (d/tempid :db.part/user) artist-name album-name]])

(defn add-artist [{:keys [artist-name]}]
  [[:artist/add (d/tempid :db.part/user) artist-name]])

(defn add-song [{:keys [song-name duration user-email album-name artist-name]}]
  [[:song/add (d/tempid :db.part/user) song-name duration user-email album-name artist-name]])

(defn add-user [{:keys [email full-name]}]
  [[:user/add (d/tempid :db.part/user) email full-name]])


(defn add-tags [song-id tags]
  [:db/add song-id :song/tags tags])


(comment
@(d/transact (:conn (user/db)) (add-user {:email "jpmonettas@gmail.com"
                                          :full-name "Juan Monetta"}))

@(d/transact (:conn (user/db)) (add-artist {:artist-name "Tito Puente"}))
@(d/transact (:conn (user/db)) (add-artist {:artist-name "Latin Vibe"}))

@(d/transact (:conn (user/db)) (add-album {:album-name "Greatest Hits"
                                           :artist-name "Tito Puente"}))

@(d/transact (:conn (user/db)) (add-song {:song-name "Por tu amor"
                                          :duration 120
                                          :user-email "jpmonettas@gmail.com"
                                          :album-name "Greatest Hits"
                                          :artist-name "Tito Puente"}))

@(d/transact (:conn (user/db)) (add-song {:song-name "Por tu amor"
                                          :duration 120
                                          :user-email "jpmonettas@gmail.com"
                                          :album-name "unknown"
                                          :artist-name "Latin Vibe"}))

;; (d/invoke (d/db (:conn (user/db))) :artist/add (d/db (:conn (user/db))) 1 "Test Artist")

(d/q '[:find ?alb .
       :in $ ?alb-name ?art-name
       :where
       [?ar :artist/name ?art-name]
       [?ar :artist/albums ?alb]
       [?alb :album/name ?alb-name]]
     (d/db (:conn (user/db)))
     "Greatest Hits"
     "Tito Puente")
(-> (d/db (:conn (user/db)))
    (d/entity 17592186045438)
    d/touch)
(user/clear-db)
)
