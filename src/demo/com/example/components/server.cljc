(ns com.example.components.server
  "HTTP server using http-kit, managed by Mount."
  (:require
    [com.example.components.config :refer [config]]
    [com.example.components.ring-middleware :refer [middleware]]
    [mount.core :refer [defstate]]
    #?@(:clj [[org.httpkit.server :refer [run-server]]
              [taoensso.timbre :as log]])))

#?(:clj
   (defstate http-server
     :start
     (let [cfg     (get config :org.httpkit.server/config)
           stop-fn (run-server middleware cfg)]
       (log/info "Starting webserver with config" cfg)
       {:stop stop-fn})
     :stop
     (let [{:keys [stop]} http-server]
       (when stop
         (stop)))))
