(ns mambobox-core.core.music
  (:require [clojure.spec :as s]
            [claudio.id3 :as id3]
            [clojure.java.io :as io]
            [environ.core :refer [env]]))

(s/def ::tempfile #(instance? java.io.File %))
(s/def ::filename string?)
(s/fdef upload-song
        :args (s/cat :datomic-cmp :mb/datomic-cmp
                     :device-id :mb.device/uniq-id
                     :file (s/keys :req-un [::tempfile ::filename])))

(defn upload-song [datomic-cmp device-id {:keys [tempfile filename]}]
  (io/copy tempfile (io/file (format "%s/%s" (env :public-files-folder) filename)))
  )

#_(id3/read-tag (clojure.java.io/file "/home/jmonetta/music/aquel.mp3"))
#_{:album "Que revienten los artistas",
   :album-artist "La Tabaré",
   :artist "La Tabaré",
   :title "Aquel cuplé",
   :track "1",
   :track-total "13",
   :year "2014"}
