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
           :query-params [device-id :- schema/Str]
           
           (POST "/upload" req
                 :summary "Upload a song file"
                 :middleware [upload/wrap-multipart-params]
                 :multipart-params [image :- upload/TempFileUpload]
                 :responses {200 {:schema schema/Any :description "PSD song uploaded"}}
                 
                 (response/ok (core-music/upload-song (:datomic-cmp req) 
                                                      ;; This is called image because it's the
                                                      ;; name the file upload puts to it
                                                      ;; have to change that
                                                      image)))
           
           (GET "/initial-dump" req
                :summary "Returns the songs initial dump, hot, favourites, etc"
                :body-params [device-id :- schema/Str]
                (response/ok {:favourites (core-music/user-favourites-songs (:datomic-cmp req)
                                                                            device-id)
                              :hot (core-music/hot-songs (:datomic-cmp req))}))

           (PUT "/:song-id/track-play" [song-id :as req]
                :summary "Returns the songs initial dump, hot, favourites, etc"
                (core-music/track-song-play (:datomic-cmp req)
                                            (Long/parseLong song-id))
                (response/ok))

           (PUT "/:song-id/artist" [song-id :as req]
                :summary "Move song to artist with that name "
                :body-params [new-artist-name :- schema/Str]
                (response/ok
                 (core-music/update-song-artist (:datomic-cmp req)
                                                (Long/parseLong song-id)
                                                new-artist-name)))
           (PUT "/:song-id/album" [song-id :as req]
                :summary "Move song to album with that name in the same artist"
                :body-params [new-album-name :- schema/Str]
                (response/ok
                 (core-music/update-song-album (:datomic-cmp req)
                                                (Long/parseLong song-id)
                                                new-album-name)))
           (PUT "/:song-id/name" [song-id :as req]
                :summary "Update song name"
                :body-params [new-song-name :- schema/Str]
                (response/ok
                 (core-music/update-song-name (:datomic-cmp req)
                                                (Long/parseLong song-id)
                                                new-song-name)))))


