(ns mambobox-core.http.songs-routes
  (:require [compojure.api.sweet :refer [context GET POST PUT DELETE]]
            [ring.util.http-response :as response]
            [ring.swagger.upload :as upload]
            [clojure.spec :as s]
            [schema.core :as schema]
            [mambobox-core.core.music :as core-music]
            [mambobox-core.core.users :as core-user]
            [taoensso.timbre :as l]))

(def songs-routes
  (context "/song" []
           :tags ["Songs"]
           :query-params [device-id :- schema/Str]           
           
           (POST "/upload" [user-id :as req]
                 :operationId "uploadSong"
                 :summary "Upload a song file"
                 :middleware [upload/wrap-multipart-params]
                 :multipart-params [image :- schema/Any]
                 :responses {200 {:schema schema/Any :description "PSD song uploaded"}}
                 (response/ok (core-music/upload-song (:datomic-cmp req) 
                                                      ;; This is called image because it's the
                                                      ;; name the file upload puts to it
                                                      ;; have to change that
                                                      image
                                                      user-id)))
           
           ;; TODO This should be a get when cljs-ajax works 
           (POST "/initial-dump" [user-id :as req]
                :operationId "getInitialDump"
                :summary "Returns the songs initial dump, hot, favourites, etc"
                (let [favourites-songs (core-user/get-all-user-favourite-songs (:datomic-cmp req)
                                                                               user-id)
                      hot-songs (core-music/hot-songs (:datomic-cmp req))
                      user-uploaded-songs (core-user/get-user-uploaded-songs (:datomic-cmp req)
                                                                             user-id)
                      all-songs (-> #{}
                                    (into favourites-songs)
                                    (into hot-songs)
                                    (into user-uploaded-songs))]
                  (response/ok {:favourites-songs-ids (->> favourites-songs
                                                           (map :db/id)
                                                           (into #{}))
                                :hot-songs-ids (->> hot-songs
                                                    (map :db/id)
                                                    (into #{}))
                                :songs all-songs
                                :user-uploaded-songs (->> user-uploaded-songs
                                                          (map :db/id)
                                                          (into #{}))})))

           (PUT "/:song-id/track-play" [user-id :as req]
                :operationId "trackSongPlay"
                :summary "Tracks a song play"
                :path-params [song-id :- schema/Str]
                (core-music/track-song-play (:datomic-cmp req)
                                            (Long/parseLong song-id)
                                            user-id)
                (response/ok))

           (GET "/:song-id" req
                :operationId "getSongById"
                :summary "Returns the song entity"
                :path-params [song-id :- schema/Str]
                (response/ok (core-music/get-song-by-id (:datomic-cmp req)
                                                        (Long/parseLong song-id))))

           (PUT "/:song-id/artist" [user-id :as req]
                :operationId "updateSongArtist"
                :summary "Move song to artist with that name "
                :body-params [new-artist-name :- schema/Str]
                :path-params [song-id :- schema/Str]
                
                (response/ok
                 (core-music/update-song-artist (:datomic-cmp req)
                                                (Long/parseLong song-id)
                                                new-artist-name
                                                user-id)))
           
           (PUT "/:song-id/album" [user-id :as req]
                :operationId "updateSongAlbum"
                :summary "Move song to album with that name in the same artist"
                :body-params [new-album-name :- schema/Str]
                :path-params [song-id :- schema/Str]
                
                (response/ok
                 (core-music/update-song-album (:datomic-cmp req)
                                                (Long/parseLong song-id)
                                                new-album-name
                                                user-id)))
           
           (PUT "/:song-id/name" [user-id :as req]
                :operationId "updateSongName"
                :summary "Update song name"
                :body-params [new-song-name :- schema/Str]
                :path-params [song-id :- schema/Str]
                
                (response/ok
                 (core-music/update-song-name (:datomic-cmp req)
                                              (Long/parseLong song-id)
                                              new-song-name
                                              user-id)))

           (POST "/:song-id/tags/:tag" [user-id tag :as req]
                 :operationId "tagSong"
                 :summary "Tag a song"
                 :path-params [song-id :- schema/Str]
                 
                 (response/ok
                  (core-music/tag-song (:datomic-cmp req)
                                               (Long/parseLong song-id)
                                               tag
                                               user-id)))

           (DELETE "/:song-id/tags/:tag" [user-id tag :as req]
                   :operationId "untagSong"
                   :summary "Untag a song"
                   :path-params [song-id :- schema/Str]
                   
                   (response/ok
                    (core-music/untag-song (:datomic-cmp req)
                                           (Long/parseLong song-id)
                                           tag
                                           user-id)))

           ;; TODO This should be a get when cljs-ajax works 
           (POST "/search" req
                 :operationId "searchSongs"
                 :summary "Search songs"
                 :query-params [q :- schema/Str]

                 (response/ok (core-music/search (:datomic-cmp req)
                                                 q)))

           ;; TODO This should be a get when cljs-ajax works 
           (POST "/artist/search" req
                 :operationId "searchArtists"
                 :summary "Search artist, for use in autocomplete"
                 :query-params [q :- schema/Str]

                 (response/ok (core-music/search-artists (:datomic-cmp req)
                                                        q)))

           ;; TODO This should be a get when cljs-ajax works 
           (POST "/album/search" req
                 :operationId "searchAlbums"
                 :summary "Search albums, for use in autocomplete"
                 :query-params [q :- schema/Str]

                 (response/ok (core-music/search-albums (:datomic-cmp req)
                                                        q)))

           ;; TODO This should be a get when cljs-ajax works 
           (POST "/explore-tag" req
                 :operationId "exploreSongsByTag"
                 :summary "Get songs for a tag"
                 :query-params [tag :- schema/Str
                                page :- schema/Str]

                 (response/ok (core-music/explore-by-tag (:datomic-cmp req)
                                                         tag
                                                         (Integer/parseInt page))))

           ;; TODO This should be a get when cljs-ajax works 
           (POST "/all-artists" req
                 :operationId "getAllArtists"
                 :summary "Get all artists we have so far"

                 (response/ok (core-music/get-all-artists (:datomic-cmp req))))

           ;; TODO This should be a get when cljs-ajax works 
           (POST "/explore-artist" req
                 :operationId "exploreArtistAlbums"
                 :summary "Get albums for an artist"
                 :query-params [artist-id :- schema/Str]

                 (response/ok (core-music/explore-artist (:datomic-cmp req)
                                                         (Long/parseLong artist-id))))

           ;; TODO This should be a get when cljs-ajax works 
           (POST "/explore-album" req
                 :operationId "exploreAlbumSongs"
                 :summary "Get album songs"
                 :query-params [album-id :- schema/Str]

                 (response/ok (core-music/explore-album (:datomic-cmp req)
                                                        (Long/parseLong album-id))))


           ))


