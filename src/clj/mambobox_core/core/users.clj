(ns mambobox-core.core.users
  (:require [mambobox-core.protocols :as protos]))

(defn register-device
  ([datomic-cmp device-info]
   (register-device datomic-cmp device-info nil))
  ([datomic-cmp device-info user-id]
   (protos/add-device datomic-cmp device-info user-id)))

(defn update-user-nick [datomic-cmp user-id new-nick]
  (protos/update-user-nick datomic-cmp
                           user-id
                           new-nick))

(defn set-user-favourite-song [datomic-cmp user-id song-id]
  (protos/set-user-favourite-song datomic-cmp user-id song-id))

(defn unset-user-favourite-song [datomic-cmp user-id song-id]
  (protos/unset-user-favourite-song datomic-cmp user-id song-id))

(defn get-all-user-favourite-songs [datomic-cmp user-id]
  (protos/get-all-user-favourite-songs datomic-cmp user-id))
