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

(defn build-file-id [filename user-id]
  (str user-id "_" filename))

(s/def ::tempfile #(instance? java.io.File %))
(s/def ::filename string?)
(s/fdef upload-song
        :args (s/cat :datomic-cmp :mb/datomic-cmp
                     :device-id :mb.device/uniq-id
                     :file (s/keys :req-un [::tempfile ::filename])))

;; {artist-name :artist
;;  album-name :album
;;  song-name :title
;;  year :year}

(defn upload-song [datomic-cmp device-uniq-id {:keys [tempfile filename]}]
  (let [user (protos/get-user-by-device-uuid datomic-cmp device-uniq-id)
        song-file-id (build-file-id filename (:db/id user))
        song-dest-file (io/file (format "%s/%s" (env :public-files-folder) song-file-id))
        id3-info (id3/read-tag tempfile)]
    (io/copy tempfile song-dest-file)
    (protos/add-song datomic-cmp song-file-id id3-info (:db/id user))))

#_(id3/read-tag (clojure.java.io/file "/home/jmonetta/music/aquel.mp3"))
#_{:album "Que revienten los artistas",
   :album-artist "La Tabaré",
   :artist "La Tabaré",
   :title "Aquel cuplé",
   :track "1",
   :track-total "13",
   :year "2014"}
