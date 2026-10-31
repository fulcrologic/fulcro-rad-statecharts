(ns com.example.components.datomic
  "Datomic connection setup using Mount state."
  (:require
   [com.example.components.config :refer [config]]
   [com.example.model :refer [all-attributes]]
   [mount.core :refer [defstate]]
   #?@(:clj [[com.fulcrologic.rad.database-adapters.datomic-cloud :as datomic]
             [datomic.client.api :as d]])))

#?(:clj
   (defstate ^{:on-reload :noop} datomic-connections
     :start
     (datomic/start-databases all-attributes config)))
