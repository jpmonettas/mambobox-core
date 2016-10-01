(ns mambobox-core.http.songs-routes
  (:require [compojure.api.sweet :refer [context GET POST PUT DELETE]]
            [ring.util.http-response :as response]
            [ring.swagger.upload :as upload]
            [clojure.spec :as s]
            [schema.core :as schema]
            [mambobox-core.core.music :as core-music]
            [taoensso.timbre :as l]))

(def songs-routes
  (context "/song" []
           :tags ["Songs"]
           (POST "/upload" [device-uniq-id image :as req]
                 :summary "Upload a song file"
                 :middleware [upload/wrap-multipart-params]
                 :responses {200 {:schema schema/Any :description "PSD song uploaded"}}
                 (core-music/upload-song (:datomic-cmp req)
                                         device-uniq-id
                                         image)
                 (response/ok))
           
           (GET "/" req
                :summary "Dummy"
                (let []
                 (response/ok "Great songs await")))))


