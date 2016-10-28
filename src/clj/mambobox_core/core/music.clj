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


(defn upload-song [datomic-cmp {:keys [tempfile filename]} user-id]
  (let [[_ filebase extension] (re-matches #"(.+)\.(.*)" filename)
        song-file-id (str (build-file-id filebase) "." extension)
        song-dest-file (io/file (format "%s/%s" (env :public-files-folder) song-file-id))]
    (io/copy tempfile song-dest-file)
    (protos/add-song datomic-cmp song-file-id (id3/read-tag song-dest-file) user-id)))


(defn update-song-artist [datomic-cmp song-id new-artist-name user-id]
  (protos/update-song-artist datomic-cmp song-id new-artist-name user-id))

(defn update-song-album [datomic-cmp song-id new-album-name user-id]
  (protos/update-song-album datomic-cmp song-id new-album-name user-id))

(defn update-song-name [datomic-cmp song-id new-song-name user-id]
  (protos/update-song-name datomic-cmp song-id new-song-name user-id))

(defn tag-song [datomic-cmp song-id tag user-id]
  (protos/add-song-tag datomic-cmp song-id tag user-id))

(defn untag-song [datomic-cmp song-id tag user-id]
  (protos/remove-song-tag datomic-cmp song-id tag user-id))

(defn get-song-by-id [datomic-cmp song-id]
  (protos/get-song-by-id datomic-cmp song-id))


(defn hot-songs [datomic-cmp])

(defn track-song-play [datomic-cmp song-id user-id]
  (protos/track-song-view datomic-cmp song-id user-id))
