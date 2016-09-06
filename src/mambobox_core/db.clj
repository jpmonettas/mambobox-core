(ns mambobox-core.db
  (:require [datomic.api :as d]
            [clojure.java.io :as io]
            [mambobox-core.utils :as u])
  (:import datomic.Util))

;; [:db/add entity-id attribute value]
;; {:db/id (d/tempid :db.part/user)
;;  :db/ident :song
;;  :db/doc "The song name"
;;  :song/name "Ran kan kan"}
;; [:db/retract entity-id attribute value]
;; [:db.fn/retractEntity entity-id] ;; all attributes retraction
;; {:db/id entity-id
;;  attribute value
;;  attribute value
;;  ... }
;; [data-fn args*]

(def construct-artist
  #db/fn {:lang :clojure
          :params [db id artist-name]
          :code [{:db/id id
                  :artist/name artist-name
                  :artist/albums [{:db/id (d/tempid :db.part/user)
                                   :album/name "unknown"}]}]})
(def construct-album
  #db/fn {:lang :clojure
          :params [db album-id artist-name album-name]
          :code (let [artist-e (d/entity db [:artist/name artist-name])]
                  (if-not artist-e
                    (throw (Exception. (format "There is no artist called %s" artist-name)))
                    (if-not (empty? (->> (:artist/albums artist-e)
                                         (map :album/name)
                                         (filter #(= % album-name))))
                      (throw (Exception. (format "There is already an album called %s for artist %s" album-name artist-name)))
                      [[:db/add album-id :album/name album-name]
                       [:db/add (:db/id artist-e) :artist/albums album-id]])))})

(def construct-song
  #db/fn {:lang :clojure
          :params [db song-id song-name duration user-email album-name artist-name]
          :code (let [album-entity-id (d/q '[:find ?alb .
                                             :in $ ?alb-name ?art-name
                                             :where
                                             [?ar :artist/name ?artist-name]
                                             [?ar :artist/albums ?alb]
                                             [?alb :album/name ?album-name]]
                                           db
                                           album-name
                                           artist-name)]
                  (if-not album-entity-id
                    (throw (Exception. (format "No album found for %s / %s " artist-name album-name)))
                    [{:db/id song-id
                      :song/name song-name
                      :song/duration duration
                      :song/user [:user/email user-email]}
                     [:db/add album-entity-id :album/songs song-id]]))})

(def construct-user
  #db/fn {:lang :clojure
          :params [db id email full-name]
          :code [{:db/id id
                  :user/email email
                  :user/fullname full-name}]})

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
  (def db-uri "datomic:free://localhost:4334/mambodb")
  (def conn (d/connect db-uri))

  (d/delete-database db-uri)
  (d/create-database db-uri)
  @(d/transact conn (u/read-schema "schemas/mambobox-schema.edn"))

  @(d/transact conn [[:db.fn/retractEntity 17592186045424]])
  @(d/transact conn [{:db/id (d/tempid :db.part/user)
                      :db/ident :artist/add
                      :db/fn construct-artist}
                     {:db/id (d/tempid :db.part/user)
                      :db/ident :album/add
                      :db/fn construct-album}
                     {:db/id (d/tempid :db.part/user)
                      :db/ident :song/add
                      :db/fn construct-song}
                     {:db/id (d/tempid :db.part/user)
                      :db/ident :user/add
                      :db/fn construct-user}])
  
  @(d/transact conn (add-user {:email "jpmonettas@gmail.com"
                               :full-name "Juan Monetta"}))
  @(d/transact conn (add-album {:album-name "Greatest Hits"
                                :artist-name "Tito Puente"}))
  @(d/transact conn (add-artist {:artist-name "Tito Puente"}))
  @(d/transact conn (add-song {:song-name "Por tu amor"
                               :duration 120
                               :user-email "jpmonettas@gmail.com"
                               :album-name "Greatest Hits"
                               :artist-name "Tito Puente"}))
  
  (d/entity (d/db conn) [:artist/name "Tito Puente"])
  )

