(ns mambobox-core.db.datomic-component-test
  (:require [clojure.test :refer [deftest is]]
            [datomic.api :as d]
            [io.rkn.conformity :as conformity]
            [com.stuartsierra.component :as component]
            [mambobox-core.db.datomic-component :refer :all]
            [mambobox-core.utils :as utils]))

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
                                                       :mb.song/file-id "l-a1-s1-f1"}]}]}]
        {loaded-db :db-after} (d/with empty-db txdata)
        {db-after-s1 :db-after} (d/with loaded-db (add-song-transaction loaded-db
                                                                        "l-a1-s1-f2"
                                                                        {:artist "LatinVibe"
                                                                         :album "Latin VibeAlbum 1"
                                                                         :year "2010"
                                                                         :title "La llave"}))
        {db-after-s2 :db-after} (d/with db-after-s1 (add-song-transaction db-after-s1
                                                                          "gc-a1-s1-f1"
                                                                          {:artist "El gran combo de puerto rico"
                                                                           :album "GreatestHits"
                                                                           :year "2010"
                                                                           :title "Guaguanco del gran combo"}))
        added-song1-entity (d/entity db-after-s2 [:mb.song/file-id "l-a1-s1-f2"])
        auto-created-artist (d/entity db-after-s2 [:mb.artist/name "el-gran-combo-de-puerto-rico"])
        added-song2-entity (d/entity db-after-s2 [:mb.song/file-id "gc-a1-s1-f1"])]
    
    (is added-song1-entity
        "The new song 1 should exist")
    (is (= (:mb.song/name added-song1-entity) "la-llave")
        "The new song should have a normalized name")
    (is (=  (-> added-song1-entity :mb.album/_songs first :mb.album/name) "latin-vibe-album-1")
        "The new song should be in the correct album")

    (is added-song2-entity
        "The new song 2 should exist")
    (is auto-created-artist
        "There should be an artist for that song 2 added")
    (is (= (->> auto-created-artist :mb.artist/albums (map :mb.album/name) (into #{})) #{"greatest-hits"})
        "There should be 1 albums for that song 2 added")))


(deftest update-song-artist-transaction-test
  (let [empty-db (d/db (:conn (new-test-mambobox-datomic-cmp)))
        latin-vibe-tmp (d/tempid :db.part/user)
        latin-vibe->a1-tmp (d/tempid :db.part/user)
        uk-tmp (d/tempid :db.part/user)
        uk->uk-tmp (d/tempid :db.part/user)
        uk->a1-tmp (d/tempid :db.part/user)
        song1-tmp (d/tempid :db.part/user)
        song2-tmp (d/tempid :db.part/user)
        txdata [{:db/id latin-vibe-tmp
                 :mb.artist/name "latin-vibe"
                 :mb.artist/albums [{:db/id latin-vibe->a1-tmp
                                     :mb.album/name "album-1"}]}
                {:db/id uk-tmp
                 :mb.artist/name "unknown"
                 :mb.artist/albums [{:db/id uk->uk-tmp
                                     :mb.album/name "unknown"
                                     :mb.album/songs [{:db/id song1-tmp
                                                       :mb.song/name "song-1"}]}
                                    {:db/id uk->a1-tmp
                                     :mb.album/name "album-1"
                                     :mb.album/songs [{:db/id song2-tmp
                                                       :mb.song/name "song-2"}]}]}]
        {loaded-db :db-after tempids :tempids} (d/with empty-db txdata)
        r-tmp (fn [tmpid] (d/resolve-tempid loaded-db tempids tmpid))
        
        ;; move song-1 to test->unknown
        {db-after-m1 :db-after} (d/with loaded-db (update-song-artist-transaction loaded-db
                                                                                  (r-tmp song1-tmp)
                                                                                  "test artist"))
        ;; move song-2 to :latin-vibe->album-1
        {db-after-m2 :db-after} (d/with loaded-db (update-song-artist-transaction loaded-db
                                                                                  (r-tmp song2-tmp)
                                                                                 "Latin vibe"))]

    ;; FIRST MOVE !
    (is (d/entity db-after-m1 [:mb.artist/name "test-artist"])
        "there should be a new artist called test-artist")

    (is (-> (d/entity db-after-m1 [:mb.artist/name "test-artist"]) :mb.artist/albums first :mb.album/name (= "unknown"))
        "the new test artist should have an unkown album")

    (is (-> (d/entity db-after-m1 (r-tmp song1-tmp)) :mb.album/_songs first :mb.artist/_albums first :mb.artist/name (= "test-artist"))
        "now the song should be under the test artist")

    (is (-> (d/entity db-after-m1 (r-tmp uk->uk-tmp)) :mb.album/songs empty?)
        "the uk->uk-tmp album should be empty")
    
    ;; SECOND MOVE !

    (is (-> (d/entity db-after-m2 (r-tmp latin-vibe->a1-tmp)) :mb.album/songs first :mb.song/name (= "song-2"))
        "album latin-vibe->a1-tmp should have a song named song-2")))


(deftest update-song-album-transaction-test
  (let [empty-db (d/db (:conn (new-test-mambobox-datomic-cmp)))
        latin-vibe-tmp (d/tempid :db.part/user)
        greatest-hits-tmp (d/tempid :db.part/user)
        por-tu-amor-tmp (d/tempid :db.part/user)
        oldies-tmp (d/tempid :db.part/user)
        txdata [{:db/id latin-vibe-tmp
                 :mb.artist/name "latin-vibe"
                 :mb.artist/albums [{:db/id greatest-hits-tmp
                                     :mb.album/name "greatest-hits"
                                     :mb.album/songs [{:db/id por-tu-amor-tmp
                                                       :mb.song/name "por-tu-amor"}]}
                                    {:db/id oldies-tmp
                                     :mb.album/name "oldies"
                                     :mb.album/songs [{:db/id (d/tempid :db.part/user)
                                                       :mb.song/name "la-llave"}]}]}]
        {loaded-db :db-after tempids :tempids} (d/with empty-db txdata)
        r-tmp (fn [tmpid] (d/resolve-tempid loaded-db tempids tmpid))
        ;; move por-tu-amor to a new-album
        {db-after-m1 :db-after} (d/with loaded-db (update-song-album-transaction loaded-db
                                                                                 (r-tmp por-tu-amor-tmp)
                                                                                 "new-album"))
        ;; move por-tu-amor to oldies
        {db-after-m2 :db-after} (d/with loaded-db (update-song-album-transaction loaded-db
                                                                                 (r-tmp por-tu-amor-tmp)
                                                                                 "oldies"))]

    ;; FIRST MOVE !
    (is (->> (d/entity db-after-m1 (r-tmp latin-vibe-tmp))
             :mb.artist/albums
             (map :mb.album/name)
             (into #{})
             (= #{"greatest-hits" "oldies" "new-album"}))
        "there should be a new album under artist called new-album")

    (is (->> (d/entity db-after-m1 (r-tmp greatest-hits-tmp)) :mb.album/songs empty?)
        "greatest hits should be empty")
    
    ;; SECOND MOVE !

    (is (->> (d/entity db-after-m2 (r-tmp oldies-tmp)) :mb.album/songs count (= 2))
     "oldies album should have two songs")))
