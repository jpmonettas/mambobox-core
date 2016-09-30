(ns mambobox-core.core.music
  (:require [clojure.spec :as s]))

#_(s/fdef upload-song
        :args (s/cat :datomic-cmp
                     :device-id
                     :file))
(defn upload-song [datomic-cmp device-id file ])

