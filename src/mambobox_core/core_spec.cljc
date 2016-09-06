(ns mambobox-core.core-spec
  (:require [clojure.spec :as s]))


(s/def :mb.song/name string?)
(s/def :mb.song/url string?)

(s/def :mb/song (s/keys :req [:mb.song/name
                              :mb.song/url]))



