(ns mambobox-core.http.http-kit
  (:require [com.stuartsierra.component :as component]
            [mambobox-core.http.handler :refer [create-handler]]
            [environ.core :refer [env]]
            [org.httpkit.server :as http-server]))

(defrecord HttpKitComponent [server datomic-cmp]

  component/Lifecycle

  (start [this]
    (assoc this :server (http-server/run-server
                         (create-handler datomic-cmp)
                         {:port (Integer. (env :http-port))
                          :join? false
                          :max-body (* 25 1024 1024)})))
  (stop [this]
    (server)
    (dissoc this :server)))

(defn new-web-server []
  (map->HttpKitComponent {}))
