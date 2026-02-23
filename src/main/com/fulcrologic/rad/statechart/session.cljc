(ns com.fulcrologic.rad.statechart.session
  "Deterministic conversion from Fulcro idents to statechart session IDs.

   Statechart session IDs must satisfy `::sc/id` (keyword, UUID, number, or string).
   Fulcro idents are vectors like `[:account/id #uuid \"abc\"]`, which are NOT valid session IDs.
   This namespace provides a one-way mapping from idents to keywords. The encoding is lossy
   (non-alphanumeric chars are stripped from the value) so that tempids and other values with
   special characters produce valid Clojure keywords.

   Routed components use `send-to-self!` and do not need these helpers.
   Embedded (non-routed) components use `ident->session-id` to derive a deterministic session ID."
  (:require
    [clojure.string :as str]
    [com.fulcrologic.guardrails.core :refer [=> >defn]]))

(def ^{:doc "The namespace used for all ident-derived session ID keywords.
             NOTE: This is kept as 'com.fulcrologic.rad.sc' for backward compatibility
             with existing persisted session IDs — do NOT change this value."}
  session-ns "com.fulcrologic.rad.sc")

(>defn ident->session-id
  "Converts a Fulcro ident to a valid statechart session ID keyword.
The ident `[:account/id #uuid \"abc\"]` becomes `:com.fulcrologic.rad.sc/account_id--abc`.

The result is a namespaced keyword that:
- Satisfies `::sc/id`
- Is deterministic (same ident always produces same session-id)
- Is collision-free (the `--` separator is unambiguous)"
  [[k v]]
  [vector? => keyword?]
  (let [v-str (str/replace (cond
                             (keyword? v) (if-let [ns (namespace v)]
                                            (str "KW." ns ".." (name v))
                                            (str "KW." (name v)))
                             :else (str v))
                #"[^a-zA-Z0-9]" "")
        n     (str (namespace k) "_" (name k) "--" v-str)]
    (keyword session-ns n)))
