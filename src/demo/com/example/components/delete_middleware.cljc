(ns com.example.components.delete-middleware
  "Delete operations middleware for Datomic."
  (:require
   #?@(:clj [[com.fulcrologic.rad.database-adapters.datomic-cloud :as datomic]])))

#?(:clj
   (def middleware (datomic/wrap-datomic-delete)))
