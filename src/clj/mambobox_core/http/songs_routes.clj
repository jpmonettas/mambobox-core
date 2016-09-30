(ns mambobox-core.http.songs-routes
  (:require [compojure.api.sweet :refer [context GET POST PUT DELETE]]
            [ring.util.http-response :as response]
            [ring.swagger.upload :as upload]
            [clojure.spec :as s]
            [schema.core :as schema]))

(def songs-routes
  (context "/song" []
           (POST "/uploads" [req]
                 :summary "Upload a song file"
                 :middleware [upload/wrap-multipart-params]
                 :multipart-params [file :- upload/TempFileUpload]
                 :responses {200 {:schema schema/Any :description "PSD song uploaded"}}
                 {:status 200})
           (GET "/" req
                :summary "Dummy"
                (let []
                 (response/ok "Great songs await")))

           ))


