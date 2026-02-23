(ns com.fulcrologic.rad.statechart.session-spec
  (:require
    [com.fulcrologic.rad.statechart.session :as session]
    [fulcro-spec.core :refer [=> assertions component specification]]))

(specification "ident->session-id"
  (assertions
    "produces a keyword"
    (keyword? (session/ident->session-id [:account/id 42])) => true

    "uses the com.fulcrologic.rad.sc namespace"
    (namespace (session/ident->session-id [:account/id 42])) => "com.fulcrologic.rad.sc"

    "encodes the ident key namespace and name separated by underscore"
    (name (session/ident->session-id [:account/id 42])) => "account_id--42"

    "handles UUID ident values"
    (session/ident->session-id [:account/id #uuid "ffffffff-ffff-ffff-ffff-000000000001"])
    => :com.fulcrologic.rad.sc/account_id--ffffffff-ffff-ffff-ffff-000000000001

    "handles integer ident values"
    (session/ident->session-id [:person/id 42])
    => :com.fulcrologic.rad.sc/person_id--42

    "handles string ident values"
    (session/ident->session-id [:thing/name "hello"])
    => :com.fulcrologic.rad.sc/thing_name--hello

    "handles keyword ident values"
    (session/ident->session-id [:report/id :myapp.ui/AccountList])
    => :com.fulcrologic.rad.sc/report_id--KW.myapp.ui..AccountList

    "is deterministic (same ident produces same session-id)"
    (session/ident->session-id [:account/id 42])
    => (session/ident->session-id [:account/id 42])

    "is collision-free (different idents produce different session-ids)"
    (not= (session/ident->session-id [:account/id 42])
      (session/ident->session-id [:person/id 42])) => true
    (not= (session/ident->session-id [:account/id 42])
      (session/ident->session-id [:account/id 43])) => true))

(specification "session-id->ident"
  (assertions
    "returns nil for keywords without the session namespace"
    (session/session-id->ident :other.ns/foo) => nil
    (session/session-id->ident :unqualified) => nil

    "returns nil for auth-session-id (not an ident-derived session-id)"
    (session/session-id->ident session/auth-session-id) => nil

    "returns nil for malformed session-id names"
    (session/session-id->ident :com.fulcrologic.rad.sc/nodelimiter) => nil)

  (component "round-trip with UUID ident values"
    (let [ident [:account/id #uuid "ffffffff-ffff-ffff-ffff-000000000001"]
          sid   (session/ident->session-id ident)]
      (assertions
        "recovers the original ident"
        (session/session-id->ident sid) => ident)))

  (component "round-trip with integer ident values"
    (let [ident [:person/id 42]
          sid   (session/ident->session-id ident)]
      (assertions
        "recovers the original ident"
        (session/session-id->ident sid) => ident)))

  (component "round-trip with string ident values"
    (let [ident [:thing/name "hello-world"]
          sid   (session/ident->session-id ident)]
      (assertions
        "recovers the original ident"
        (session/session-id->ident sid) => ident)))

  (component "round-trip with keyword ident values"
    (let [ident [:report/id :myapp.ui/AccountList]
          sid   (session/ident->session-id ident)]
      (assertions
        "recovers the original ident"
        (session/session-id->ident sid) => ident)))

  (component "round-trip with simple keyword ident values"
    (let [ident [:container/id :dashboard]
          sid   (session/ident->session-id ident)]
      (assertions
        "recovers the original ident"
        (session/session-id->ident sid) => ident))))

(specification "form-session-id (2-arity)"
  (assertions
    "derives session-id from the ident, ignoring the class"
    (session/form-session-id :ignored [:account/id 42])
    => (session/ident->session-id [:account/id 42])))

(specification "auth-session-id"
  (assertions
    "is a keyword (satisfies ::sc/id)"
    (keyword? session/auth-session-id) => true

    "is namespaced under the session namespace"
    (namespace session/auth-session-id) => "com.fulcrologic.rad.statechart.session"))
