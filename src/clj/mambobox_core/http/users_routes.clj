(ns mambobox-core.http.users-routes
  (:require [compojure.api.sweet :refer [context GET POST PUT DELETE]]
            [ring.util.http-response :as response]
            [clojure.spec :as s]
            [schema.core :as schema]
            [mambobox-core.core.users :as core-users]
            [taoensso.timbre :as l]
            [mambobox-core.http.commons :refer [*request-device-uniq-id*]]))


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
                                                           #:mb.device{:uniq-id *request-device-uniq-id*
                                                                       :locale locale
                                                                       :country country}))
                  (catch Exception e
                    (case (:type (ex-data e))
                      :duplicate-device-error (response/conflict (.getMessage e))
                      
                      (throw e)))))

           (PUT "/nick" req
                :operationId "updateUserNick"
                :summary "Update user nick"
                :responses {200 {:schema schema/Any :description "Nick updated"}}
                :body-params [new-nick :- String
                              user-id :- Number]
                (response/ok
                 (core-users/update-user-nick (:datomic-cmp req)
                                              user-id
                                              new-nick)))))
