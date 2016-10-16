(ns mambobox-core.core.music
  (:require [clojure.spec :as s]
            [claudio.id3 :as id3]
            [clojure.java.io :as io]
            [environ.core :refer [env]]
            [camel-snake-kebab.core :as csk]
            [clojure.string :as str]
            [mambobox-core.protocols :as protos]))


(defn normalize-entity-name-string [name-str]
  (csk/->kebab-case name-str))

(defn build-file-id [filename]
  (str (normalize-entity-name-string filename) "_" (rand-int 100)))

(s/def ::tempfile #(instance? java.io.File %))
(s/def ::filename string?)

(s/fdef upload-song
        :args (s/cat :datomic-cmp :mb/datomic-cmp
                     :file (s/keys :req-un [::tempfile ::filename])))


(defn upload-song [datomic-cmp {:keys [tempfile filename]}]
  (let [
        [_ filebase extension] (re-matches #"(.+)\.(.*)" filename)
        song-file-id (str (build-file-id filebase) "." extension)
        song-dest-file (io/file (format "%s/%s" (env :public-files-folder) song-file-id))]
    (io/copy tempfile song-dest-file)
    (protos/add-song datomic-cmp song-file-id (id3/read-tag song-dest-file))))


(defn update-song-artist [datomic-cmp song-id new-artist-name]
  (protos/update-song-artist datomic-cmp song-id new-artist-name))

(defn update-song-album [datomic-cmp song-id new-album-name]
  (protos/update-song-album datomic-cmp song-id new-album-name))

(defn update-song-name [datomic-cmp song-id new-song-name]
  (protos/update-song-name datomic-cmp song-id new-song-name))

(defn user-favourites-songs [datomic-cmp device-id]
  (let [user (protos/get-user-by-device-uuid datomic-cmp device-id)]
    )
  )

(defn hot-songs [datomic-cmp]
  
  )

(defn track-song-play [datomic-cmp song-id]
  (protos/track-song-view datomic-cmp song-id))
