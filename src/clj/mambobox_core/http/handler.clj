(ns mambobox-core.http.handler
  (:require [compojure.core :refer [routes]]
            [mambobox-core.http.songs-routes :refer [songs-routes]]
            [mambobox-core.http.users-routes :refer [users-routes]]
            [compojure.api.swagger :refer [swagger-routes]]
            [ring.util.http-response :as response]
            [compojure.api.sweet :refer [api defapi GET context]]
            [compojure.route :refer [files resources not-found]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [environ.core :refer [env]]
            [camel-snake-kebab.core :refer [->kebab-case ->camelCase]]
            [taoensso.timbre :as l]
            [mambobox-core.http.commons :refer [*request-device-uniq-id*]]))



(defn generic-exception-handler [^Exception e data req]
  (response/internal-server-error {:message (.getMessage e)}))

(defn wrap-components
  "Inject all components in the request, so we can do it without global defs"
  [handler datomic-cmp]
  (fn [request]
    (let [injected-request (-> request
                               (assoc :datomic-cmp datomic-cmp))]
      (handler injected-request))))

(defn wrap-device-id-mandatory [handler]
  (fn [request]
    (if (.startsWith (:uri request) "/swagger-ui")
      (handler request)
      (if-not (-> request :params :device-id)
        (response/bad-request "You can't make a request without :device-id parameter")
        (binding [*request-device-uniq-id* (-> request :params :device-id)]
          (handler request))))))

(defn key-json->clj [x]
  (keyword (->kebab-case x)))

(defn key-clj->json [x]
  (->camelCase (if (keyword? x) (name x) x)))

(def api-routes
  (api
   {:exceptions {:handlers {:compojure.api.exception/default generic-exception-handler}}
    :format {:formats [:json :edn]
             :params-opts {:json {:key-fn key-json->clj}}
             :response-opts {:json {:key-fn key-clj->json}}}
    :swagger {:ui "/swagger-ui"
              :spec "/swagger-ui/swagger.json"
              :data {:info {:title "Mambobox api"}}}}

   #'songs-routes
   #'users-routes))

(defn create-handler [datomic-cmp]
  (routes
   (resources "/resources/")
   (files "/public-files/" {:root (env :public-files-folder)})

   (-> api-routes
       (wrap-cors :access-control-allow-origin [#".*"]
                    :access-control-allow-methods [:post :get :put :delete])
       (wrap-components datomic-cmp)
       (wrap-device-id-mandatory)
       (wrap-keyword-params)
       (wrap-params))

   (not-found "Not Found")))



