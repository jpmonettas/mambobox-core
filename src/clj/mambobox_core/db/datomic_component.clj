(ns mambobox-core.db.datomic-component
  (:require [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [io.rkn.conformity :as c]
            [environ.core :refer [env]]))

(defrecord Datomic [conn]
  component/Lifecycle
  (start [component]
    (let [db (d/create-database (env :datomic-uri))
          conn (d/connect (env :datomic-uri))
          schema-norms-map (c/read-resource "schemas/mambobox-schema.edn")]
      (c/ensure-conforms conn schema-norms-map [:mambobox/schema :mambobox/db-fns])
      (assoc component :conn conn)))
  (stop [component]
    (when conn (d/release conn))
    (assoc component :conn nil)))

(defn new-datomic-db []
  (map->Datomic {}))
