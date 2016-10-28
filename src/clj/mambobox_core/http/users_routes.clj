(ns mambobox-core.http.users-routes
  (:require [compojure.api.sweet :refer [context GET POST PUT DELETE]]
            [ring.util.http-response :as response]
            [clojure.spec :as s]
            [schema.core :as schema]
            [mambobox-core.core.users :as core-users]
            [taoensso.timbre :as l]))


(def users-routes
  (context "/user" []
           :tags ["Users"]
           :query-params [device-id :- schema/Str]
           
           (POST "/register-device" req
                 :operationId "registerDevice"
                 :summary "Register a device"
                 :responses {200 {:schema schema/Any :description "Device registered"}
                             409 {:schema schema/Str :description "Device already registered"}}
                 
                 :body-params [locale :- String
                               country :- String]
                 (try
                  (response/ok (core-users/register-device (:datomic-cmp req)
                                                           #:mb.device{:uniq-id device-id
                                                                       :locale locale
                                                                       :country country}))
                  (catch Exception e
                    (case (:type (ex-data e))
                      :duplicate-device-error (response/conflict (.getMessage e))
                      
                      (throw e)))))

           (PUT "/nick" [user-id :as req]
                :operationId "updateUserNick"
                :summary "Update user nick"
                :responses {200 {:schema schema/Any :description "Nick updated"}}
                :body-params [new-nick :- String]
                (response/ok
                 (core-users/update-user-nick (:datomic-cmp req)
                                              user-id
                                              new-nick)))
           
           (PUT "/favourites/:song-id" [user-id :as req]
                :operationId "setSongFavourite"
                :summary "Set a song as favourite for the user"
                :responses {200 {:schema schema/Any :description "Song is favourite"}}
                :path-params [song-id :- schema/Str]
                
                (response/ok (core-users/set-user-favourite-song (:datomic-cmp req)
                                                                 user-id
                                                                 (Long/parseLong song-id))))
           (PUT "/favourites/:song-id" [user-id :as req]
                :operationId "unsetSongFavourite"
                :summary "Unset a song as favourite for the user"
                :responses {200 {:schema schema/Any :description "Song is no more favourite"}}
                :path-params [song-id :- schema/Str]

                (response/ok (core-users/unset-user-favourite-song (:datomic-cmp req)
                                                                 user-id
                                                                 (Long/parseLong song-id))))))
