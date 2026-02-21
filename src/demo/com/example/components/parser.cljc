(ns com.example.components.parser
  "Pathom parser setup for the demo. Wires together all resolvers, middleware,
   and plugins into a single parser instance."
  (:require
   [com.example.components.config :refer [config]]
   [com.example.model :refer [all-attributes]]
   #?@(:clj [[com.example.components.auto-resolvers :refer [automatic-resolvers]]
             [com.example.components.datomic :refer [datomic-connections]]
             [com.example.components.delete-middleware :as delete]
             [com.example.components.save-middleware :as save]
             [com.example.model.account :as account]
             [com.example.model.invoice :as invoice]
             [com.fulcrologic.rad.attributes :as attr]
             [com.fulcrologic.rad.database-adapters.datomic-cloud :as datomic]
             [com.fulcrologic.rad.form :as form]
             [com.fulcrologic.rad.pathom :as pathom]
             [com.fulcrologic.rad.type-support.date-time :as dt]
             [com.wsscode.pathom.connect :as pc]
             [com.wsscode.pathom.core :as p]
             [mount.core :refer [defstate]]])))

#?(:clj
   (pc/defresolver index-explorer [{::pc/keys [indexes]} _]
     {::pc/input  #{:com.wsscode.pathom.viz.index-explorer/id}
      ::pc/output [:com.wsscode.pathom.viz.index-explorer/index]}
     {:com.wsscode.pathom.viz.index-explorer/index
      (p/transduce-maps
       (remove (comp #{::pc/resolve ::pc/mutate} key))
       indexes)}))

#?(:clj
   (defstate parser
     :start
     (pathom/new-parser config
                        [(attr/pathom-plugin all-attributes)
                         (form/pathom-plugin save/middleware delete/middleware)
                         (datomic/pathom-plugin (fn [_env] {:production (:main datomic-connections)}))
                         {::p/wrap-parser
                          (fn transform-parser-out-plugin-external [parser]
                            (fn transform-parser-out-plugin-internal [env tx]
                              (dt/with-timezone "America/Los_Angeles"
                                (if (and (map? env) (seq tx))
                                  (parser env tx)
                                  {}))))}]
                        [automatic-resolvers
                         form/resolvers
                         account/resolvers
                         invoice/resolvers
                         index-explorer])))
