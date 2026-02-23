(ns com.example.components.config
  "Application configuration via Mount."
  (:require
    [com.fulcrologic.fulcro.server.config :as fserver]
    [mount.core :refer [args defstate]]
    [taoensso.timbre :as log]))

(defstate config
  "Load config from EDN file. The `args` map may contain:
   - `:config` — path to the config EDN file (default \"config/dev.edn\")
   - `:overrides` — a map merged on top of loaded config (useful in tests)"
  :start
  (let [{:keys [config overrides]
         :or   {config "config/dev.edn"}} (args)
        loaded (merge (fserver/load-config! {:config-path config}) overrides)]
    (log/info "Loading config" config)
    loaded))
