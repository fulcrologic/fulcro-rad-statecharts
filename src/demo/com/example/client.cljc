(ns com.example.client
  "Client entry point for the demo application.
   CLJ path: headless test app with http-kit driver.
   CLJS path: browser app with relative /api endpoint."
  (:require
    #?@(:clj
        [[com.fulcrologic.fulcro.headless :as h]
         [com.fulcrologic.fulcro.inspect.repl-tool :as repl-tool]
         [com.fulcrologic.fulcro.networking.http-kit-driver :as hd]]
        :cljs
        [[com.fulcrologic.fulcro.algorithms.timbre-support
          :refer [console-appender prefix-output-fn]]])
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.fulcro.networking.http-remote :as net]
    [com.fulcrologic.rad.application :as rad-app]
    [com.example.system :as sys]
    [com.example.ui.ui :refer [Root]]
    [taoensso.timbre :as log]))

(defonce SPA (atom nil))

(defn secured-request-middleware [{:keys [csrf-token]}]
  (-> (net/wrap-fulcro-request)
    (cond->
      csrf-token (net/wrap-csrf-token csrf-token))))

(defn init
  "Initialize the demo application.
   In CLJ: creates a headless test app connecting to localhost:`port`.
   In CLJS: creates a browser app connecting to relative /api."
  ([] (init 3000))
  ([port]
   (let [token #?(:clj "SERVER-SIDE-APP"
                  :cljs (when (exists? js/fulcro_network_csrf_token)
                          js/fulcro_network_csrf_token))
         options       {:remotes
                        {:remote
                         (net/fulcro-http-remote
                           {:url #?(:clj  (str "http://localhost:" port "/api")
                                    :cljs "/api")
                            #?@(:clj [:http-driver (hd/make-http-kit-driver)])
                            :request-middleware (secured-request-middleware {:csrf-token token})})}}
         spa #?(:cljs (rad-app/fulcro-rad-app options)
                :clj   (h/build-test-app (merge options {:root-class Root})))]
     (reset! SPA spa)
     #?(:clj  (repl-tool/install! spa)
        :cljs (log/merge-config! {:output-fn prefix-output-fn
                                  :appenders {:console (console-appender)}}))
     (app/mount! spa Root #?(:clj :app :cljs "app"))
     #?(:cljs (sys/start! spa)
        :clj  (sys/start! spa {:event-loop? :immediate}))
     spa)))

(defn refresh
  "Hot code reload support. Re-mounts the app."
  []
  (when-let [spa @SPA]
    (log/info "Refreshing app")
    (comp/refresh-dynamic-queries! spa)
    #?(:cljs (app/mount! spa Root "app"))))
