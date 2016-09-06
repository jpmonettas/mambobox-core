(ns mambobox-core.core
  (:require [org.httpkit.server :as http-server]
            [compojure.api.sweet :refer [defapi api context GET POST PUT DELETE]]
            [ring.swagger.upload :as upload]
            [mambobox-core.core-spec :refere :all]))

(def out *out*)
(def server
  (http-server/run-server (cc/routes
                           (POST "/uploads" [req]
                                 :middleware [upload/wrap-multipart-params]
                                 {:status 200})
                           (GET "/uploads" [req]
                                   {:status 200
                                    :body "Great"}))
  {:port 1155
   :join false}))
