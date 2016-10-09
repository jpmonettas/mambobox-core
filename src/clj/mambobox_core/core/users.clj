(ns mambobox-core.core.users
  (:require [mambobox-core.protocols :as protos]))

(defn register-device
  ([datomic-cmp device-info]
   (register-device datomic-cmp device-info nil))
  ([datomic-cmp device-info user-id]
   (protos/add-device datomic-cmp device-info user-id)))

(defn update-user-nick [datomic-cmp user-id new-nick]
  (protos/update-user-nick datomic-cmp user-id new-nick))
