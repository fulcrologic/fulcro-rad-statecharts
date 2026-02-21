(ns com.example.components.ring-middleware
  "Ring middleware stack for the demo server."
  (:require
   #?@(:clj [[com.example.components.config :as config]
             [com.example.components.parser :as parser]
             [com.fulcrologic.fulcro.server.api-middleware :as server]
             [hiccup.page :refer [html5]]
             [mount.core :refer [defstate]]
             [ring.middleware.defaults :refer [wrap-defaults]]
             [ring.util.response :as resp]
             [clojure.string :as str]])))

#?(:clj
   (defn- index
     "Generate the HTML page shell for the SPA."
     [csrf-token]
     (html5
      [:html {:lang "en"}
       [:head
        [:title "RAD Demo"]
        [:meta {:charset "utf-8"}]
        [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
        [:link {:rel "shortcut icon" :href "data:image/x-icon;," :type "image/x-icon"}]
        [:script (str "var fulcro_network_csrf_token = '" csrf-token "';")]]
       [:body
        [:div#app]
        [:script {:src "/js/main/main.js"}]]])))

#?(:clj
   (defn- wrap-api
     "Middleware that intercepts API requests at `uri` and routes them to the parser."
     [handler uri]
     (fn [request]
       (if (= uri (:uri request))
         (server/handle-api-request (:transit-params request)
                                    (fn [query]
                                      (parser/parser {:ring/request request} query)))
         (handler request)))))

#?(:clj
   (def ^:private not-found-handler
     (fn [_req]
       {:status 404
        :body   {}})))

#?(:clj
   (defn- wrap-html-routes
     "Serve index.html for all non-API, non-static routes."
     [ring-handler]
     (fn [{:keys [uri anti-forgery-token] :as req}]
       (if (or (str/starts-with? uri "/api")
               (str/starts-with? uri "/js"))
         (ring-handler req)
         (-> (resp/response (index anti-forgery-token))
             (resp/content-type "text/html"))))))

#?(:clj
   (defstate middleware
     :start
     (let [defaults-config (:ring.middleware/defaults-config config/config)]
       (-> not-found-handler
           (wrap-api "/api")
           (server/wrap-transit-params {})
           (server/wrap-transit-response {})
           (wrap-html-routes)
           (wrap-defaults defaults-config)))))
