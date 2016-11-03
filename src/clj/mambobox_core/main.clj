(ns mambobox-core.main
  (:require [com.stuartsierra.component :as component]
            [mambobox-core.http.handler :refer [create-handler]]
            [mambobox-core.http.http-kit :refer [new-web-server]]
            [mambobox-core.db.datomic-component :refer [new-mambobox-datomic-cmp]]
            [system.components.repl-server :refer [new-repl-server]]
            [environ.core :refer [env]]
            [system.repl :refer [system set-init! start stop reset]])
  (:gen-class))

(defn dev-system []
  (component/system-map
   :datomic-cmp (component/using (new-mambobox-datomic-cmp (env :datomic-uri)) [])
   :http-server-cmp (component/using (new-web-server) [:datomic-cmp])))


(defn prod-system []
  (component/system-map
   :datomic-cmp (component/using (new-mambobox-datomic-cmp (env :datomic-uri)) [])
   :http-server-cmp (component/using (new-web-server) [:datomic-cmp])
   :repl-server-cmp (component/using (new-repl-server (Integer. (env :repl-port))) [])))

(defn -main
  [& args]
  (println "Starting prod system")
  (set-init! #'prod-system)
  (start)
  (println "Ready"))

