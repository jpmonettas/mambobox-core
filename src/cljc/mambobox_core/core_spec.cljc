(ns mambobox-core.core-spec
  (:require [clojure.spec :as s]))

(s/def :db/id any?)

(s/def :mb.song/name string?)
(s/def :mb.song/file-id string?)
(s/def :mb.song/year integer?)
(s/def :mb.song/url string?)
(s/def :mb.song/plays-count number?)

(s/def :mb.song/artist (s/keys :req [:db/id
                                     :mb.artist/name]))
(s/def :mb.song/album (s/keys :req [:db/id
                                    :mb.album/name]))

(s/def :mb.song/score double?)
(s/def :mb/song (s/keys :req [:db/id
                              :mb.song/name
                              :mb.song/file-id
                              :mb.song/url]
                        :req-un [:mb.song/artist
                                 :mb.song/album]
                        :opt-un [:mb.song/score]))

(s/def :mb.album/name string?)
(s/def :mb.album/songs (s/coll-of :mb/song))


(s/def :mb.artist/name string?)
(s/def :mb.artist/albums (s/coll-of :mb/album))
(s/def :mb/artist (s/keys :req [:mb.artist/id
                                :mb.artist/name]))

 



;;;;;;;;;;;;;
;; Devices ;;
;;;;;;;;;;;;;

(s/def :mb.device/uniq-id string?)
(s/def :mb.device/locale string?)
(s/def :mb.device/country string?)

(s/def :mb/device (s/keys :req [:mb.device/uniq-id
                                :mb.device/locale
                                :mb.device/country]))

;;;;;;;;;;;;;;;;
;; Components ;;
;;;;;;;;;;;;;;;;

(s/def :mb/datomic-cmp map?)
