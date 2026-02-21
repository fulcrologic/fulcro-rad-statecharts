(ns com.fulcrologic.rad.authorization
  "Stub authorization namespace. The original RAD authorization/redaction system
   has been removed. This provides pass-through implementations so that database
   adapters (e.g. fulcro-rad-datomic) that reference `auth/redact` continue to work.")

(defn redact
  "Pass-through stub. Returns `query-result` unchanged.
   Original signature: `[env query-result]`."
  [_env query-result]
  query-result)
