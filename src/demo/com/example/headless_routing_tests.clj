(ns com.example.headless-routing-tests
  "Headless E2E integration tests for routing.
   Tests navigation between forms/reports, route guards, and back navigation.
   Requires a running server (started via mount fixture)."
  (:require
   [clojure.test :refer [use-fixtures]]
   [com.example.client :as client]
   [com.example.components.datomic :refer [datomic-connections]]
   [com.example.components.ring-middleware]
   [com.example.components.server]
   [com.example.model.seed :as seed]
   [com.example.ui.account-forms :refer [AccountForm]]
   [com.example.ui.inventory-report :refer [InventoryReport]]
   [com.example.ui.invoice-forms :refer [InvoiceForm]]
   [com.example.ui.invoice-report :refer [InvoiceReport]]
   [com.example.ui.item-forms :refer [ItemForm]]
   [com.fulcrologic.fulcro.algorithms.form-state :as fs]
   [com.fulcrologic.fulcro.application :as app]
   [com.fulcrologic.fulcro.components :as comp]
   [com.fulcrologic.fulcro.headless :as h]
   [com.fulcrologic.fulcro.headless.hiccup :as hic]
   [com.fulcrologic.rad.ids :refer [new-uuid]]
   [com.fulcrologic.rad.type-support.date-time :as dt]
   [com.fulcrologic.statecharts.integration.fulcro :as scf]
   [com.fulcrologic.statecharts.integration.fulcro.routing :as scr]
   [datomic.client.api :as d]
   [fulcro-spec.core :refer [=> assertions component specification]]
   [mount.core :as mount]))

;; ---------------------------------------------------------------------------
;; Test Server Fixture
;; ---------------------------------------------------------------------------

(def ^:dynamic *test-port* nil)

(defn with-test-system
  "clojure.test fixture: starts mount system on the given port, seeds data, runs tests."
  ([] (with-test-system {}))
  ([{:keys [port] :or {port 9844}}]
   (fn [tests]
     (mount/start-with-args {:config    "config/dev.edn"
                             :overrides {:org.httpkit.server/config {:port port}}})
     (try
       (dt/set-timezone! "America/Los_Angeles")
       (let [connection (:main datomic-connections)]
         (when connection
           (d/transact connection
                       {:tx-data
                        [(seed/new-account (new-uuid 100) "Tony" "tony@example.com" "letmein")
                         (seed/new-account (new-uuid 101) "Sam" "sam@example.com" "letmein")
                         (seed/new-category (new-uuid 1000) "Tools")
                         (seed/new-category (new-uuid 1002) "Toys")
                         (seed/new-item (new-uuid 200) "Widget" 33.99
                                        :item/category "Tools")
                         (seed/new-item (new-uuid 201) "Screwdriver" 4.99
                                        :item/category "Tools")
                         (seed/new-invoice "invoice-1"
                                           (dt/html-datetime-string->inst "2020-01-01T12:00")
                                           "Tony"
                                           [(seed/new-line-item "Widget" 1 33.0M)])]})))
       (binding [*test-port* port]
         (tests))
       (finally
         (mount/stop))))))

(use-fixtures :once (with-test-system {:port 9844}))

;; ---------------------------------------------------------------------------
;; Helpers
;; ---------------------------------------------------------------------------

(defn test-client
  "Create a headless client pointing at the test server."
  [port]
  (client/init port))

(defn routing-config
  "Returns the current statechart configuration for the routing session."
  [app]
  (scf/current-configuration app scr/session-id))

(defn in-route-state?
  "Returns true if the given route state keyword is in the current routing config."
  [app state-kw]
  (contains? (routing-config app) state-kw))

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
;; Tests: Form Navigation (via scr/route-to! API)
;; TODO: sfr/edit! (com.fulcrologic.rad.form/edit!) sends events to the wrong
;; statechart session. It targets the form's own session (derived from the
;; form ident via sc.session/ident->session-id) instead of the routing session
;; (scr/session-id). The routing session is what controls navigation state
;; transitions. Until edit! is fixed to route through the routing statechart,
;; use scr/route-to! directly for form navigation in tests.
;; See also: form.cljc `edit!` and `start-form!` — the fix likely involves
;; having edit! call scr/route-to! instead of directly starting the form session.
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
;; Route guard tests rely on the form UISM marking the form as dirty.
;; After navigating to a form, we need the form data to load from the
;; server. Multiple render-frame! calls are needed to process the async
;; HTTP response and let the form UISM transition to :state/editing.
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

    ;; Wait for any pending HTTP loads to complete before dirtying the form
                 (h/wait-for-idle! app)

    ;; Dirty the form programmatically (seed data + modify field)
                 (dirty-form! app AccountForm ident
                              {:account/id (new-uuid 100) :account/name "Tony" :account/email "tony@example.com"}
                              :account/name "Dirty Data")
                 (h/render-frame! app)

    ;; Try to navigate away via route-to!
                 (scr/route-to! app InventoryReport)
                 (h/render-frame! app)

                 (let [config (routing-config app)
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

    ;; Wait for any pending HTTP loads to complete before dirtying the form
                 (h/wait-for-idle! app)

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

    ;; Wait for any pending HTTP loads to complete before dirtying the form
                 (h/wait-for-idle! app)

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
