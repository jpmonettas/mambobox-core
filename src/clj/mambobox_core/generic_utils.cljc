(ns mambobox-core.generic-utils
  (:require [camel-snake-kebab.core :as csk]))

(defn normalize-entity-name-string [name-str]
  (csk/->kebab-case name-str))
