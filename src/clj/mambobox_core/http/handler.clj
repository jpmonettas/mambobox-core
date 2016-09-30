(ns mambobox-core.http.handler
  (:require [compojure.core :refer [routes]]
            [mambobox-core.http.songs-routes :refer [songs-routes]]
            [compojure.api.swagger :refer [swagger-routes]]
            [ring.util.http-response :as response]
            [compojure.api.sweet :refer [api defapi GET context]]
            [compojure.route :refer [files resources not-found]]
            [ring.middleware.cors :refer [wrap-cors]]
            [environ.core :refer [env]]))

(defn generic-exception-handler [^Exception e data req]
  (response/internal-server-error {:message (.getMessage e)}))

(defn wrap-components
  "Inject all components in the request, so we can do it without global defs"
  [handler datomic-cmp]
  (fn [request]
    (let [injected-request (-> request
                               (assoc :datomic-cmp datomic-cmp))]
      (handler injected-request))))


(def api-routes
  (api
   {:exceptions {:handlers {:compojure.api.exception/default generic-exception-handler}}
    :swagger
    {:ui "/"
     :spec "/swagger.json"
     :data {:info {:title "Mambobox api"}}}}

   #'songs-routes))

(defn create-handler [datomic-cmp]
  (routes
   (resources "/resources/")
   (files "/public-files/" {:root (env :public-files-folder)})

   (-> api-routes
       (wrap-cors :access-control-allow-origin [#".*"]
                    :access-control-allow-methods [:post :get :put :delete])
       (wrap-components datomic-cmp))

   (not-found "Not Found")))



