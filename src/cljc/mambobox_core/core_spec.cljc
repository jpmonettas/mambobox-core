(ns mambobox-core.core-spec
  (:require [clojure.spec :as s]))


(s/def :mb.song/id any?)
(s/def :mb.song/name string?)
(s/def :mb.song/duration int?)
(s/def :mb.song/url string?)

(s/def :mb.album/name string?)

(s/def :mb.artist/name string?)

(s/def :mb/song (s/keys :req [:mb.song/name
                              :mb.song/duration
                              :mb.artist/name
                              :mb.album/name
                              :mb.song/url])) 



