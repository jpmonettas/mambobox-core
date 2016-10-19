(ns mambobox-core.utils
  (:require [clojure.java.io :as io]
            [com.walmartlabs.datascope :as ds]
            [datomic.api :as d])
  (:import datomic.Util))

(defn read-schema [path]
  (first (Util/readAll (io/reader (io/resource path)))))

(defn view-artists [db]
  (->> (d/q '[:find ?artist-id
              :where [?artist-id :mb.artist/name]]
            db)
       (map first)
       (d/pull-many db [:mb.artist/name
                        :db/id
                        {:mb.artist/albums [:mb.album/name
                                            :db/id
                                            {:mb.album/songs [:mb.song/name
                                                              :db/id]}]}])
       (ds/view)))
