(ns mambobox-core.utils
  (:require [clojure.java.io :as io])
  (:import datomic.Util))

(defn read-schema [path]
  (first (Util/readAll (io/reader (io/resource path)))))
