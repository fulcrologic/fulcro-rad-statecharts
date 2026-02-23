(ns com.example.components.auto-resolvers
  "Auto-generated Pathom resolvers from RAD attributes."
  (:require
    [com.example.model :refer [all-attributes]]
    [mount.core :refer [defstate]]
    #?@(:clj [[com.fulcrologic.rad.database-adapters.datomic-cloud :as datomic]
              [com.fulcrologic.rad.resolvers :as res]])))

#?(:clj
   (defstate automatic-resolvers
     :start
     (vec
       (concat
         (res/generate-resolvers all-attributes)
         (datomic/generate-resolvers all-attributes :production)))))
