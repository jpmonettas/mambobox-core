(ns mambobox-core.core-spec
  (:require [clojure.spec :as s]))

(s/def :db/id any?)

(s/def :mb.song/id :db/id)
(s/def :mb.song/name string?)
(s/def :mb.song/file-id string?)
(s/def :mb.song/year integer?)
(s/def :mb.song/url string?)

(s/def :mb/song (s/keys :req [:mb.song/id
                              :mb.song/name
                              :mb.song/duration
                              :mb/artist
                              :mb/album
                              :mb.song/url]))

(s/def :mb.album/id :db/id)
(s/def :mb.album/name string?)
(s/def :mb.album/songs (s/coll-of :mb/song))
(s/def :mb/album (s/keys :req [:mb.album/id
                               :mb.album/name
                               :mb.album/songs]))

(s/def :mb.artist/id :db/id)
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
