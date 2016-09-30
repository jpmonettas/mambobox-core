(ns mambobox-core.main
  (:require [com.stuartsierra.component :as component]
            [mambobox-core.http.handler :refer [create-handler]]
            [system.components.http-kit :refer [new-web-server]]
            [mambobox-core.db.datomic-component :refer [new-datomic-db]]
            [system.components.repl-server :refer [new-repl-server]]
            [environ.core :refer [env]]))

(defn dev-system []
  (let [datomic-cmp (new-datomic-db (env :datomic-uri))
        http-server (new-web-server (Integer. (env :http-port)) (create-handler datomic-cmp))]
   (component/system-map
    :datomic-cmp (component/using datomic-cmp [])
    :http-server-cmp (component/using http-server [:datomic-cmp]))))

(defn prod-system []
  (let [datomic-cmp (new-datomic-db (env :datomic-uri))
        http-server (new-web-server (Integer. (env :http-port)) (create-handler datomic-cmp))
        repl-server (new-repl-server (Integer. (env :repl-port)))]
   (component/system-map
    :datomic-cmp (component/using datomic-cmp [])
    :http-server-cmp (component/using http-server [:datomic-cmp])
    :repl-server-cmp (component/using repl-server []))))



