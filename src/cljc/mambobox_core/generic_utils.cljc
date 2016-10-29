(ns mambobox-core.generic-utils
  (:require [camel-snake-kebab.core :as csk]
            [clojure.string :as str]))

(defn normalize-entity-name-string [name-str]
  (-> name-str
      csk/->kebab-case
      (str/replace #"-" " ")
      (str/replace #"á" "a")
      (str/replace #"é" "e")
      (str/replace #"í" "i")
      (str/replace #"ó" "o")
      (str/replace #"ú" "u")))

(defn denormalize-entity-name-string [norm-name-str]
  (as-> norm-name-str $
      (str/split $ #" ") 
      (update $ 0 str/capitalize)
      (str/join " " $)))

