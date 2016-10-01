(ns user
  (:require [system.repl :refer [system set-init! start stop reset]]
            [mambobox-core.main :refer [dev-system]]
            [datomic.api :as d]
            [environ.core :refer [env]]))

(set-init! #'dev-system)

(defn start-system! [] (start))
(defn stop-system! [] (stop))

(defn db [] (:datomic-cmp system))
(defn http-server [] (:http-server-cmp system))

(defn clear-db []
  (d/delete-database (env :datomic-uri))
  (d/create-database (env :datomic-uri))) 
