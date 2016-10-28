(ns mambobox-core.generic-utils
  (:require [camel-snake-kebab.core :as csk]
            [clojure.string :as str]))

(defn normalize-entity-name-string [name-str]
  (csk/->kebab-case name-str))

(defn denormalize-entity-name-string [norm-name-str]
  (as-> norm-name-str $
      (str/split $ #"-") 
      (update $ 0 str/capitalize)
      (str/join " " $)))

