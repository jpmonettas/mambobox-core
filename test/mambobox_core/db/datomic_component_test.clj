(ns mambobox-core.db.datomic-component-test
  (:require [clojure.test :refer [deftest is]]
            [datomic.api :as d]
            [io.rkn.conformity :as conformity]
            [com.stuartsierra.component :as component]
            [mambobox-core.db.datomic-component :refer :all]))

(defn new-test-mambobox-datomic-cmp []
  (component/start (new-mambobox-datomic-cmp (str "datomic:mem://" (d/squuid)))))

(deftest add-song-transaction-test
  (let [empty-db (d/db (:conn (new-test-mambobox-datomic-cmp)))
        txdata [{:db/id (d/tempid :db.part/user)
                 :mb.artist/name "latin-vibe"
                 :mb.artist/albums [{:db/id (d/tempid :db.part/user)
                                     :mb.album/name "latin-vibe-album-1"
                                     :mb.album/songs [{:db/id (d/tempid :db.part/user)
                                                       :mb.song/name "l-a1-s1"
                                                       :mb.song/file-id "l-a1-s1-f1"}]}]}
                {:db/id (d/tempid :db.part/user)
                 :mb.user/email "jp@mail.com"}]
        {loaded-db :db-after} (d/with empty-db txdata)
        user-id (:db/id (d/entity loaded-db [:mb.user/email "jp@mail.com"]))
        {db-after :db-after} (d/with loaded-db (add-song-transaction loaded-db
                                                                     "l-a1-s1-f2"
                                                                     {:artist "LatinVibe"
                                                                      :album "Latin VibeAlbum 1"
                                                                      :year 2010
                                                                      :title "La llave"}
                                                                     user-id))
        added-song-entity (d/entity db-after [:mb.song/file-id "l-a1-s1-f2"])]
    
    (is added-song-entity "The new song should exist")
    (is (= (:mb.song/name added-song-entity) "la-llave") "The new song should have a normalized name")
    (is (=  (-> added-song-entity :mb.album/_songs first :mb.album/name)
            "latin-vibe-album-1") "The new song should be in the correct album")))

