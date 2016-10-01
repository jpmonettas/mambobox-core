(ns mambobox-core.db.users
  (:require [datomic.api :as d]
            [clojure.string :as str]))

(defn device-user [db device-uniq-id]
  (let [user-id (d/q '[:find ?u .
                       :in $ ?d-uid
                       :where
                       [?d :mb.device/uniq-id ?d-uid]
                       [?u :mb.user/devices ?d]]
                     db
                     device-uniq-id)]
    (when user-id
     (d/touch (d/entity db user-id)))))

(defn- register-device-transaction [db #:mb.device{:keys [uniq-id country locale]}]
  (if-let [user (device-user db uniq-id)]
    [[:device/add (d/tempid :db.part/user) uniq-id locale country (:db/id user)]]
    (let [tmp-user-id (d/tempid :db.part/user)]
      [[:device/add (d/tempid :db.part/user) uniq-id locale country tmp-user-id]
       [:db/add tmp-user-id :mb.user/nick (str "anonymous" (rand-int 1000))]])))




(defn register-device [datomic-cmp device-info]
  (let [conn (:conn datomic-cmp)
        db (d/db conn)
        {:keys [db-after]} @(d/transact conn (register-device-transaction db device-info))]
    (device-user db-after (:mb.device/uniq-id device-info))))
