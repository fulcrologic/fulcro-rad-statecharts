(ns com.example.headless-form-tests
  "Headless E2E integration tests for forms: edit, save, reset, subforms and navigation, against a real server and
   Datomic Local (started by the `with-test-system` fixture).

   Routed forms run under `form/form-route-state`, an `scr/istate` that invokes the form chart as a child session
   with a generated id. Tests must therefore address a form through `form/resolve-form-session-id` (see
   `form-session-id`), never `sc.session/ident->session-id`, which names a session that does not exist for routed
   forms.

   Loads and saves are real HTTP round-trips, so tests wait with `await!` (poll until a predicate holds, fail on
   timeout) rather than rendering a fixed number of frames.

   Assertions are on the normalized state map and statechart configurations, not rendered hiccup."
  (:require
   [clojure.test :refer [use-fixtures]]
   [com.example.components.datomic :refer [datomic-connections]]
   [com.example.headless-client :refer [await! test-client]]
   [com.example.test-server :refer [with-test-system]]
   [com.example.ui.account-forms :refer [AccountForm]]
   [com.example.ui.invoice-forms :refer [InvoiceForm]]
   [com.example.ui.item-forms :refer [ItemForm]]
   [com.fulcrologic.fulcro.algorithms.form-state :as fs]
   [com.fulcrologic.fulcro.application :as app]
   [com.fulcrologic.fulcro.headless :as h]
   [com.fulcrologic.rad.ids :refer [new-uuid]]
   [com.fulcrologic.rad.statechart.form :as form]
   [com.fulcrologic.statecharts.integration.fulcro :as scf]
   [com.fulcrologic.statecharts.integration.fulcro.routing :as scr]
   [datomic.client.api :as d]
   [fulcro-spec.core :refer [=> assertions specification]]))

;; ---------------------------------------------------------------------------
;; Fixture — uses shared test_server.clj on an ephemeral port (`test-client` with no args connects to it)
;; ---------------------------------------------------------------------------

(use-fixtures :once (with-test-system))

;; ---------------------------------------------------------------------------
;; Helpers
;; ---------------------------------------------------------------------------

(defn routing-config
  "Returns the current statechart configuration for the routing session."
  [app]
  (scf/current-configuration app scr/session-id))

(defn in-route-state?
  "Returns true if the given route state keyword is in the current routing config."
  [app state-kw]
  (contains? (routing-config app) state-kw))

(defn form-session-id
  "Returns the statechart session id of the running form for `ident`. Routed forms get generated ids, which the form
   chart records in app state; `form/resolve-form-session-id` reads them."
  [app ident]
  (form/resolve-form-session-id app ident))

(defn form-config
  "Returns the set of active statechart states for the form session."
  [app ident]
  (scf/current-configuration app (form-session-id app ident)))

(defn form-in-state?
  "Returns true if the form's statechart is in the given state."
  [app ident state-kw]
  (contains? (form-config app ident) state-kw))

(defn entity-data
  "Get the normalized entity data from the app state."
  [app ident]
  (get-in (app/current-state app) ident))

(defn await-form!
  "Waits until the form for `ident` is in `state-kw` (default `:state/editing`). Returns the app."
  ([app ident] (await-form! app ident :state/editing))
  ([app ident state-kw]
   (await! app {:description (str "form " (pr-str ident) " in " state-kw)
                :session-id  #(form-session-id % ident)}
     #(form-in-state? % ident state-kw))))

(defn route-to-form!
  "Routes to `form-class` to edit the entity at `ident`, then waits until its form chart reaches `:state/editing`
   (the load has been merged). Returns the app."
  [app form-class ident]
  (scr/route-to! app form-class {:id (second ident)})
  (await-form! app ident))

(defn pristine-value
  "Returns the form-state pristine (last loaded or saved) value of `k` for the form entity at `ident`."
  [app ident k]
  (get-in (app/current-state app) [::fs/forms-by-ident (fs/form-id ident) ::fs/pristine-state k]))

(defn await-saved!
  "Waits until the save round-trip for the form at `ident` has finished: the form chart is back in `:state/editing`
   and the form was marked pristine with `k` = `v`. Returns the app."
  [app ident k v]
  (await! app {:description (str "form " (pr-str ident) " saved with " k " = " (pr-str v))
               :session-id  #(form-session-id % ident)}
    #(and (form-in-state? % ident :state/editing) (= v (pristine-value % ident k)))))

;; ---------------------------------------------------------------------------
;; Tests: Account Form — Edit Existing
;; ---------------------------------------------------------------------------

(specification "Form — edit existing account"
  (let [app   (test-client)
        ident [:account/id (new-uuid 100)]]
    (h/render-frame! app)

    ;; Route to account form for editing
    (route-to-form! app AccountForm ident)

    (assertions
      "routes to AccountForm state"
      (in-route-state? app :com.example.ui.account-forms/AccountForm) => true

      "form statechart enters editing state"
      (form-in-state? app ident :state/editing) => true

      "loads the account name from the server"
      (:account/name (entity-data app ident)) => "Tony"

      "loads the account email from the server"
      (:account/email (entity-data app ident)) => "tony@example.com"

      "loads the active flag"
      (:account/active? (entity-data app ident)) => true)))

;; ---------------------------------------------------------------------------
;; Tests: Account Form — Modify and Save
;; ---------------------------------------------------------------------------

(specification "Form — modify account and save"
  ;; Saves Sally (102), an account no other test in this namespace reads, so test order doesn't matter.
  (let [app   (test-client)
        ident [:account/id (new-uuid 102)]]
    (h/render-frame! app)

    ;; Route to account form
    (route-to-form! app AccountForm ident)

    (assertions
      "form is in editing state before modification"
      (form-in-state? app ident :state/editing) => true)

    ;; Modify the name field directly in app state
    (swap! (::app/state-atom app) assoc-in
      (conj ident :account/name) "Sally Updated")
    (h/render-frame! app)

    (assertions
      "name is updated in local state"
      (:account/name (entity-data app ident)) => "Sally Updated")

    ;; Trigger save via the form statechart
    (scf/send! app (form-session-id app ident) :event/save {})
    (await-saved! app ident :account/name "Sally Updated")

    (assertions
      "form returns to editing state after save"
      (form-in-state? app ident :state/editing) => true

      "saved name persists in state"
      (:account/name (entity-data app ident)) => "Sally Updated")

    ;; Verify persistence: create a fresh client and re-load
    (let [app2 (test-client)
          _    (h/render-frame! app2)
          _    (route-to-form! app2 AccountForm ident)]
      (assertions
        "saved data persists across client sessions"
        (:account/name (entity-data app2 ident)) => "Sally Updated"))))

;; ---------------------------------------------------------------------------
;; Tests: Account Form — Cancel (undo changes)
;; ---------------------------------------------------------------------------

(specification "Form — cancel account edit"
  (let [app   (test-client)
        ident [:account/id (new-uuid 101)]]
    (h/render-frame! app)

    ;; Route to Sam's account form
    (route-to-form! app AccountForm ident)

    (assertions
      "form loads Sam's data"
      (:account/name (entity-data app ident)) => "Sam"
      "form is in editing state"
      (form-in-state? app ident :state/editing) => true)

    ;; Modify the name
    (swap! (::app/state-atom app) assoc-in
      (conj ident :account/name) "Sam Modified")
    (h/render-frame! app)

    (assertions
      "name is modified locally"
      (:account/name (entity-data app ident)) => "Sam Modified")

    ;; Trigger reset (undo all changes without leaving form)
    (scf/send! app (form-session-id app ident) :event/reset {})
    (await-form! app ident)

    (assertions
      "after reset, form is still in editing state"
      (form-in-state? app ident :state/editing) => true

      "name reverts to original value"
      (:account/name (entity-data app ident)) => "Sam")))

;; ---------------------------------------------------------------------------
;; Tests: Account Form — Address Subform
;; ---------------------------------------------------------------------------

(specification "Form — account with address subform"
  (let [app   (test-client)
        ident [:account/id (new-uuid 100)]]
    (h/render-frame! app)

    ;; Route to Tony's account (which has seeded addresses via autocreate)
    (route-to-form! app AccountForm ident)

    (let [account (entity-data app ident)
          primary (:account/primary-address account)]
      (assertions
        "account has a primary address reference"
        (vector? primary) => true

        "primary address has an address/id key"
        (= :address/id (first primary)) => true))

    ;; Check that primary address data was loaded/created
    (let [account    (entity-data app ident)
          addr-ident (:account/primary-address account)
          addr-data  (entity-data app addr-ident)]
      (assertions
        "primary address data was loaded (not nil)"
        (some? addr-data) => true

        "primary address entity exists in state"
        (some? (:address/id addr-data)) => true))))

;; ---------------------------------------------------------------------------
;; Tests: Item Form — Edit Existing
;; ---------------------------------------------------------------------------

(specification "Form — edit existing item"
  (let [app   (test-client)
        ident [:item/id (new-uuid 200)]]
    (h/render-frame! app)

    ;; Route to Widget item form
    (route-to-form! app ItemForm ident)

    (assertions
      "routes to ItemForm state"
      (in-route-state? app :com.example.ui.item-forms/ItemForm) => true

      "form statechart enters editing state"
      (form-in-state? app ident :state/editing) => true

      "loads the item name"
      (:item/name (entity-data app ident)) => "Widget"

      "loads the item price"
      (some? (:item/price (entity-data app ident))) => true)))

;; ---------------------------------------------------------------------------
;; Tests: Item Form — Modify and Save
;; ---------------------------------------------------------------------------

(specification "Form — modify item price and save"
  (let [app   (test-client)
        ident [:item/id (new-uuid 201)]]
    (h/render-frame! app)

    ;; Route to Screwdriver item form
    (route-to-form! app ItemForm ident)

    (assertions
      "form loads Screwdriver data"
      (:item/name (entity-data app ident)) => "Screwdriver"
      "form is in editing state"
      (form-in-state? app ident :state/editing) => true)

    ;; Modify the name
    (swap! (::app/state-atom app) assoc-in
      (conj ident :item/name) "Screwdriver Pro")
    (h/render-frame! app)

    ;; Save
    (scf/send! app (form-session-id app ident) :event/save {})
    (await-saved! app ident :item/name "Screwdriver Pro")

    (assertions
      "form returns to editing after save"
      (form-in-state? app ident :state/editing) => true

      "saved name persists"
      (:item/name (entity-data app ident)) => "Screwdriver Pro")))

;; ---------------------------------------------------------------------------
;; Tests: Invoice Form — Edit Existing
;; ---------------------------------------------------------------------------

(specification "Form — edit existing invoice"
  (let [app        (test-client)
        connection (:main datomic-connections)
        ;; Query Datomic for the seeded invoice ID (random UUID)
        inv-ids    (d/q '[:find ?id
                          :where [?e :invoice/id ?id]]
                     (d/db connection))
        inv-id     (ffirst inv-ids)]
    (h/render-frame! app)

    (assertions
      "datomic connection is available"
      (some? connection) => true

      "seeded invoice exists in database"
      (some? inv-id) => true)

    (let [ident [:invoice/id inv-id]]
      (route-to-form! app InvoiceForm ident)

      (assertions
        "routes to InvoiceForm state"
        (in-route-state? app :com.example.ui.invoice-forms/InvoiceForm) => true

        "form enters editing state"
        (form-in-state? app ident :state/editing) => true

        "invoice has line items"
        (let [invoice (entity-data app ident)]
          (vector? (:invoice/line-items invoice))) => true))))

;; ---------------------------------------------------------------------------
;; Tests: Sequential Form Navigation
;; ---------------------------------------------------------------------------

(specification "Form — sequential navigation between forms"
  (let [app (test-client)]
    (h/render-frame! app)

    (assertions
      "starts at landing page"
      (in-route-state? app :com.example.ui.ui/LandingPage) => true)

    ;; Navigate to account form
    (route-to-form! app AccountForm [:account/id (new-uuid 100)])

    (assertions
      "first navigation goes to AccountForm"
      (in-route-state? app :com.example.ui.account-forms/AccountForm) => true)

    ;; Navigate to item form
    (route-to-form! app ItemForm [:item/id (new-uuid 200)])

    (assertions
      "second navigation goes to ItemForm"
      (in-route-state? app :com.example.ui.item-forms/ItemForm) => true

      "AccountForm is no longer active"
      (in-route-state? app :com.example.ui.account-forms/AccountForm) => false)

    ;; Navigate back to account form
    (route-to-form! app AccountForm [:account/id (new-uuid 101)])

    (assertions
      "third navigation goes back to AccountForm"
      (in-route-state? app :com.example.ui.account-forms/AccountForm) => true)))
