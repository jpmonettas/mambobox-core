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
           (POST "/register-device" req
                 :summary "Register a device"
                 :responses {200 {:schema schema/Any :description "Device registered"}}
                 :body-params [uniq-id :- String
                               locale :- String
                               country :- String]
                 (response/ok (core-users/register-device (:datomic-cmp req)
                                                          #:mb.device{:uniq-id uniq-id
                                                                      :locale locale
                                                                      :country country})))

           (PUT "/nick" req
                 :summary "Register a device"
                 :responses {200 {:schema schema/Any :description "Nick updated"}}
                 :body-params [device-uniq-id :- String
                               new-nick :- String]
                 (response/ok
                  (core-users/update-user-nick (:datomic-cmp req)
                                               device-uniq-id
                                               new-nick)))))
