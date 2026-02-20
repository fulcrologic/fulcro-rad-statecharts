(ns com.fulcrologic.rad.resolvers-common)

;; TODO: Authorization/redaction will be re-added during statechart conversion
(defn secure-resolver
  "Wraps a resolver function. Previously applied authorization redaction; now a pass-through
   pending statechart conversion."
  [resolver]
  (fn [env input]
    (resolver env input)))