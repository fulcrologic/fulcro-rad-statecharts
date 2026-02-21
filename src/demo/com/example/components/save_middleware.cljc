(ns com.example.components.save-middleware
  "Form save middleware chain. Wraps Datomic save with error handling
   and value rewriting."
  (:require
   #?@(:clj [[com.fulcrologic.rad.database-adapters.datomic-cloud :as datomic]
             [com.fulcrologic.rad.middleware.save-middleware :as r.s.middleware]])))

#?(:clj
   (defn wrap-exceptions-as-form-errors
     "Middleware that catches exceptions during save and converts them to form error maps."
     [handler]
     (fn [pathom-env]
       (try
         (handler pathom-env)
         (catch Throwable t
           {:com.fulcrologic.rad.form/errors
            [{:message (str "Unexpected error saving form: " (ex-message t))}]})))))

#?(:clj
   (def middleware
     (->
      (datomic/wrap-datomic-save)
      (wrap-exceptions-as-form-errors)
      (r.s.middleware/wrap-rewrite-values))))
