(ns com.example.headless-routing-tests
  "Headless E2E integration tests for routing.
   Tests navigation between forms/reports, route guards, and back navigation.
   Requires a running server (started via mount fixture)."
  (:require
   [clojure.test :refer [use-fixtures]]
   [com.example.headless-client :refer [await! test-client]]
   [com.example.test-server :refer [with-test-system]]
   [com.example.ui.account-forms :refer [AccountForm]]
   [com.example.ui.inventory-report :refer [InventoryReport]]
   [com.example.ui.invoice-forms :refer [InvoiceForm]]
   [com.example.ui.invoice-report :refer [InvoiceReport]]
   [com.example.ui.item-forms :refer [ItemForm]]
   [com.fulcrologic.fulcro.algorithms.form-state :as fs]
   [com.fulcrologic.fulcro.application :as app]
   [com.fulcrologic.fulcro.headless :as h]
   [com.fulcrologic.fulcro.headless.hiccup :as hic]
   [com.fulcrologic.rad.ids :refer [new-uuid]]
   [com.fulcrologic.rad.statechart.form :as form]
   [com.fulcrologic.statecharts.integration.fulcro :as scf]
   [com.fulcrologic.statecharts.integration.fulcro.routing :as scr]
   [fulcro-spec.core :refer [=> assertions specification]]))

;; ---------------------------------------------------------------------------
;; Fixture — uses shared test_server.clj with unique port 9844
;; ---------------------------------------------------------------------------

(use-fixtures :once (with-test-system {:port 9844}))

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

(defn await-form-editing!
  "Waits until the routed form for `ident` has loaded and its chart is in `:state/editing`, so a later load response
   cannot overwrite test edits. Routed forms run under generated session ids, found via
   `form/resolve-form-session-id`. Returns the app."
  [app ident]
  (await! app {:description (str "form " (pr-str ident) " in :state/editing")
               :session-id  #(form/resolve-form-session-id % ident)}
    #(contains? (scf/current-configuration % (form/resolve-form-session-id % ident)) :state/editing)))

(defn dirty-form!
  "Programmatically marks a form entity as dirty by:
   1. Merging seed data into the entity (so the form has something to be dirty against)
   2. Adding form-state config (pristine snapshot)
   3. Modifying a field so it differs from pristine
   This bypasses the need for the HTTP load + hiccup-based type-into-labeled!."
  [app FormClass ident seed-data field dirty-value]
  (let [state-atom (::app/state-atom app)]
    (swap! state-atom
      (fn [state-map]
        (-> state-map
          ;; Ensure entity has data
          (update-in ident merge seed-data)
          ;; Add form-state config (takes a pristine snapshot)
          (fs/add-form-config* FormClass ident)
          ;; Now dirty a field
          (assoc-in (conj ident field) dirty-value))))))

;; ---------------------------------------------------------------------------
;; Tests: Initial Startup
;; ---------------------------------------------------------------------------

(specification "Routing — initial app startup"
  (let [app (test-client 9844)]
    (h/render-frame! app)

    (assertions
      "renders the app root with the app title"
      (some? (hic/find-nth-by-text (h/hiccup-frame app) "RAD Demo" 0)) => true

      "shows the welcome landing page text"
      (some? (hic/find-nth-by-text (h/hiccup-frame app) "Welcome" 0)) => true

      "the routing statechart is running"
      (some? (routing-config app)) => true

      "the landing page state is active"
      (in-route-state? app :com.example.ui.ui/LandingPage) => true)))

;; ---------------------------------------------------------------------------
;; Tests: Report Navigation (via click-on-text!)
;; ---------------------------------------------------------------------------

(specification "Routing — navigate to Inventory report via click"
  (let [app (test-client 9844)]
    (h/render-frame! app)

    (h/click-on-text! app "Inventory")
    (h/render-frame! app)

    (assertions
      "landing page is no longer visible"
      (nil? (hic/find-nth-by-text (h/hiccup-frame app) "Welcome" 0)) => true

      "the inventory report title is rendered"
      (some? (hic/find-nth-by-text (h/hiccup-frame app) "Inventory Report" 0)) => true

      "statechart is in InventoryReport state"
      (in-route-state? app :com.example.ui.inventory-report/InventoryReport) => true)))

(specification "Routing — navigate to Invoices report via click"
  (let [app (test-client 9844)]
    (h/render-frame! app)

    (h/click-on-text! app "Invoices")
    (h/render-frame! app)

    (assertions
      "landing page is no longer visible"
      (nil? (hic/find-nth-by-text (h/hiccup-frame app) "Welcome" 0)) => true

      "statechart is in InvoiceReport state"
      (in-route-state? app :com.example.ui.invoice-report/InvoiceReport) => true)))

;; ---------------------------------------------------------------------------
;; Tests: Form Navigation (via scr/route-to! API; form/edit! is a thin wrapper over it)
;; ---------------------------------------------------------------------------

(specification "Routing — navigate to Account form via route-to!"
  (let [app (test-client 9844)]
    (h/render-frame! app)

    (scr/route-to! app AccountForm {:id (new-uuid 100)})
    (h/render-frame! app)

    (assertions
      "statechart is in AccountForm state"
      (in-route-state? app :com.example.ui.account-forms/AccountForm) => true

      "landing page state is no longer active"
      (in-route-state? app :com.example.ui.ui/LandingPage) => false)))

(specification "Routing — navigate to Item form via route-to!"
  (let [app (test-client 9844)]
    (h/render-frame! app)

    (scr/route-to! app ItemForm {:id (new-uuid 200)})
    (h/render-frame! app)

    (assertions
      "statechart is in ItemForm state"
      (in-route-state? app :com.example.ui.item-forms/ItemForm) => true

      "landing page state is no longer active"
      (in-route-state? app :com.example.ui.ui/LandingPage) => false)))

(specification "Routing — navigate to Invoice form via route-to!"
  (let [app (test-client 9844)]
    (h/render-frame! app)

    (scr/route-to! app InvoiceForm {:id (new-uuid 1)})
    (h/render-frame! app)

    (assertions
      "statechart is in InvoiceForm state"
      (in-route-state? app :com.example.ui.invoice-forms/InvoiceForm) => true

      "landing page state is no longer active"
      (in-route-state? app :com.example.ui.ui/LandingPage) => false)))

;; ---------------------------------------------------------------------------
;; Tests: Sequential Navigation
;; ---------------------------------------------------------------------------

(specification "Routing — sequential navigation between pages"
  (let [app (test-client 9844)]
    (h/render-frame! app)

    (assertions
      "starts at landing page"
      (in-route-state? app :com.example.ui.ui/LandingPage) => true)

    ;; Navigate to Inventory report
    (h/click-on-text! app "Inventory")
    (h/render-frame! app)

    (assertions
      "first navigation goes to Inventory"
      (in-route-state? app :com.example.ui.inventory-report/InventoryReport) => true
      "inventory report title renders"
      (some? (hic/find-nth-by-text (h/hiccup-frame app) "Inventory Report" 0)) => true)

    ;; Navigate to Invoices report
    (h/click-on-text! app "Invoices")
    (h/render-frame! app)

    (assertions
      "second navigation goes to Invoices"
      (in-route-state? app :com.example.ui.invoice-report/InvoiceReport) => true
      "inventory report is no longer rendered"
      (nil? (hic/find-nth-by-text (h/hiccup-frame app) "Inventory Report" 0)) => true)

    ;; Navigate to Account form via API
    (scr/route-to! app AccountForm {:id (new-uuid 100)})
    (h/render-frame! app)

    (assertions
      "third navigation goes to Account form"
      (in-route-state? app :com.example.ui.account-forms/AccountForm) => true)))

;; ---------------------------------------------------------------------------
;; Tests: Route Guard (dirty form)
;; Route guard tests rely on the form statechart seeing the form as dirty. The form's
;; load is a real HTTP round-trip, so each test waits for :state/editing
;; (await-form-editing!) before dirtying the form; a late load would overwrite it.
;; ---------------------------------------------------------------------------

(specification "Routing — route guard on dirty form"
  (let [app   (test-client 9844)
        ident [:account/id (new-uuid 100)]]
    (h/render-frame! app)

    ;; Navigate to account form
    (scr/route-to! app AccountForm {:id (new-uuid 100)})
    (h/render-frame! app)

    (assertions
      "account form state is active"
      (in-route-state? app :com.example.ui.account-forms/AccountForm) => true)

    ;; Wait for the form's load to finish before dirtying it
    (await-form-editing! app ident)

    ;; Dirty the form programmatically (seed data + modify field)
    (dirty-form! app AccountForm ident
      {:account/id (new-uuid 100) :account/name "Tony" :account/email "tony@example.com"}
      :account/name "Dirty Data")
    (h/render-frame! app)

    ;; Try to navigate away via route-to!
    (scr/route-to! app InventoryReport)
    (h/render-frame! app)

    (let [config  (routing-config app)
          denied? (contains? config :routing-info/open)]
      (assertions
        "route guard activates — routing-info/open state entered"
        denied? => true

        "did NOT navigate to inventory (still on account form)"
        (contains? config :com.example.ui.account-forms/AccountForm) => true))

    ;; Force continue
    (scr/force-continue-routing! app)
    (h/render-frame! app)

    (assertions
      "after forcing continue, we navigate to inventory"
      (in-route-state? app :com.example.ui.inventory-report/InventoryReport) => true)))

(specification "Routing — cancel route change on dirty form"
  (let [app   (test-client 9844)
        ident [:account/id (new-uuid 100)]]
    (h/render-frame! app)

    ;; Navigate to account form
    (scr/route-to! app AccountForm {:id (new-uuid 100)})
    (h/render-frame! app)

    ;; Wait for the form's load to finish before dirtying it
    (await-form-editing! app ident)

    ;; Dirty the form programmatically
    (dirty-form! app AccountForm ident
      {:account/id (new-uuid 100) :account/name "Tony" :account/email "tony@example.com"}
      :account/name "Dirty Data")
    (h/render-frame! app)

    ;; Try to navigate away
    (scr/route-to! app InventoryReport)
    (h/render-frame! app)

    (assertions
      "route guard is active"
      (in-route-state? app :routing-info/open) => true)

    ;; Cancel (abandon the route change)
    (scr/abandon-route-change! app)
    (h/render-frame! app)

    (assertions
      "after cancelling, we stay on the account form"
      (in-route-state? app :com.example.ui.account-forms/AccountForm) => true

      "routing-info returns to idle"
      (in-route-state? app :routing-info/idle) => true

      "inventory report is NOT active"
      (in-route-state? app :com.example.ui.inventory-report/InventoryReport) => false)))

;; ---------------------------------------------------------------------------
;; Tests: Route-denied modal in rendered hiccup
;; ---------------------------------------------------------------------------

(specification "Routing — route-denied state contains expected info"
  (let [app   (test-client 9844)
        ident [:account/id (new-uuid 100)]]
    (h/render-frame! app)

    ;; Navigate to account form
    (scr/route-to! app AccountForm {:id (new-uuid 100)})
    (h/render-frame! app)

    ;; Wait for the form's load to finish before dirtying it
    (await-form-editing! app ident)

    ;; Dirty the form programmatically
    (dirty-form! app AccountForm ident
      {:account/id (new-uuid 100) :account/name "Tony" :account/email "tony@example.com"}
      :account/name "Dirty Data")
    (h/render-frame! app)

    ;; Trigger route guard
    (scr/route-to! app InventoryReport)
    (h/render-frame! app)

    (let [config (routing-config app)]
      (assertions
        "route guard fires — routing-info/open is in the configuration"
        (contains? config :routing-info/open) => true

        "the original route target (AccountForm) is still active"
        (contains? config :com.example.ui.account-forms/AccountForm) => true

        "route-denied? returns true for the app"
        (scr/route-denied? app) => true))))
