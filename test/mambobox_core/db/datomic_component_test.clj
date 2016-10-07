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
        {db-after-s1 :db-after} (d/with loaded-db (add-song-transaction loaded-db
                                                                     "l-a1-s1-f2"
                                                                     {:artist "LatinVibe"
                                                                      :album "Latin VibeAlbum 1"
                                                                      :year 2010
                                                                      :title "La llave"}
                                                                     user-id))
        {db-after-s2 :db-after} (d/with db-after-s1 (add-song-transaction db-after-s1
                                                                          "gc-a1-s1-f1"
                                                                          {:artist "El gran combo de puerto rico"
                                                                           :album "GreatestHits"
                                                                           :year 2010
                                                                           :title "Guaguanco del gran combo"}
                                                                          user-id))
        added-song1-entity (d/entity db-after-s2 [:mb.song/file-id "l-a1-s1-f2"])
        auto-created-artist (d/entity db-after-s2 [:mb.artist/name "el-gran-combo-de-puerto-rico"])
        added-song2-entity (d/entity db-after-s2 [:mb.song/file-id "gc-a1-s1-f1"])]
    
    (is added-song1-entity "The new song 1 should exist")
    (is (= (:mb.song/name added-song1-entity) "la-llave") "The new song should have a normalized name")
    (is (=  (-> added-song1-entity :mb.album/_songs first :mb.album/name)
            "latin-vibe-album-1") "The new song should be in the correct album")

    (is added-song2-entity "The new song 2 should exist")
    (is auto-created-artist "There should be an artist for that song 2 added")
    (is (= (->> auto-created-artist :mb.artist/albums (map :mb.album/name) (into #{}))
           #{"unknown" "greatest-hits"}) "There should be 2 albums for that song 2 added")
    ))

