(ns mambobox-core.core.users
  (:require [mambobox-core.db.users :as db-users]))

(defn register-device [datomic-cmp device-info]
  (db-users/register-device datomic-cmp device-info))
