(ns com.fulcrologic.rad.statechart.session-spec
  (:require
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [com.fulcrologic.rad.ids :refer [new-uuid]]
    [com.fulcrologic.rad.statechart.session :as session]
    [fulcro-spec.core :refer [=> assertions specification]]))

(specification "ident->session-id"
  (assertions
    "produces a keyword"
    (keyword? (session/ident->session-id [:account/id 42])) => true

    "uses the com.fulcrologic.rad.sc namespace"
    (namespace (session/ident->session-id [:account/id 42])) => "com.fulcrologic.rad.sc"

    "encodes the ident key namespace and name separated by underscore"
    (name (session/ident->session-id [:account/id 42])) => "account_id--42"

    "handles UUID ident values (strips dashes for valid keyword names)"
    (session/ident->session-id [:account/id #uuid "ffffffff-ffff-ffff-ffff-000000000001"])
    => :com.fulcrologic.rad.sc/account_id--ffffffffffffffffffff000000000001

    "handles tempid ident values (strips dashes for valid keyword names)"
    (session/ident->session-id [:account/id (tempid/tempid (new-uuid 1))])
    => :com.fulcrologic.rad.sc/account_id--fulcrotempidffffffffffffffffffff000000000001

    "handles integer ident values"
    (session/ident->session-id [:person/id 42])
    => :com.fulcrologic.rad.sc/person_id--42

    "handles string ident values"
    (session/ident->session-id [:thing/name "hello"])
    => :com.fulcrologic.rad.sc/thing_name--hello

    "handles keyword ident values (strips dots for valid keyword names)"
    (session/ident->session-id [:report/id :myapp.ui/AccountList])
    => :com.fulcrologic.rad.sc/report_id--KWmyappuiAccountList

    "handles simple keyword ident values"
    (session/ident->session-id [:container/id :dashboard])
    => :com.fulcrologic.rad.sc/container_id--KWdashboard

    "is deterministic (same ident produces same session-id)"
    (session/ident->session-id [:account/id 42])
    => (session/ident->session-id [:account/id 42])

    "is collision-free (different idents produce different session-ids)"
    (not= (session/ident->session-id [:account/id 42])
      (session/ident->session-id [:person/id 42])) => true
    (not= (session/ident->session-id [:account/id 42])
      (session/ident->session-id [:account/id 43])) => true))
