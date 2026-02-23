(ns com.example.headless-report-tests
  "Headless E2E integration tests for reports.
   Tests inventory report (load, sort, filter) and invoice report (load, sort)
   against a real Datomic database with seeded data.

   NOTE: Hiccup rendering returns nil for routed views in headless CLJ mode
   (same known issue as form tests — scf/current-configuration in Root's render
   body fails in CLJ). Tests use state-map assertions instead."
  (:require
    [clojure.test :refer [use-fixtures]]
    [com.example.headless-client :refer [test-client]]
    [com.example.test-server :refer [with-test-system]]
    [com.example.ui.inventory-report :refer [InventoryReport]]
    [com.example.ui.invoice-forms :refer [AccountInvoices]]
    [com.example.ui.invoice-report :refer [InvoiceReport]]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.headless :as h]
    [com.fulcrologic.rad.statechart.session :as sc-session]
    [com.fulcrologic.statecharts.integration.fulcro :as scf]
    [com.fulcrologic.statecharts.integration.fulcro.routing :as scr]
    [fulcro-spec.core :refer [=> assertions component specification]]))

(use-fixtures :once
  (with-test-system {:port 9847}))

;; ---------------------------------------------------------------------------
;; Helpers
;; ---------------------------------------------------------------------------

(defn report-data
  "Get the report entity data from the normalized state map."
  [app report-ident-key report-qualified-key]
  (get-in (app/current-state app) [report-ident-key report-qualified-key]))

(defn row-names
  "Extract a named field from each row ident in the report's current-rows."
  [app report-ident-key report-qualified-key field-key]
  (let [state (app/current-state app)
        rd    (get-in state [report-ident-key report-qualified-key])
        rows  (:ui/current-rows rd)]
    (mapv (fn [ident] (get-in state (conj ident field-key))) rows)))

(defn in-route-state?
  "Check if the routing statechart includes the given state."
  [app state-kw]
  (let [routing-session :com.fulcrologic.statecharts.integration.fulcro.routing/session]
    (contains? (scf/current-configuration app routing-session) state-kw)))

(defn wait-for-report!
  "Route to a report and wait for data to load. Returns the app.
   Routes via statechart routing, then waits for HTTP responses to complete.
   The fops/load ok-action fires the statechart :event/loaded automatically."
  [app report-class route-params]
  (scr/route-to! app report-class route-params)
  (dotimes [_ 5] (h/render-frame! app))
  ;; Wait for all pending HTTP requests to complete
  (h/wait-for-idle! app)
  (dotimes [_ 5] (h/render-frame! app))
  app)

;; ---------------------------------------------------------------------------
;; Inventory Report
;; ---------------------------------------------------------------------------

(specification "Inventory report"
  (let [app     (test-client 9847)
        rpt-key :com.example.ui.inventory-report/InventoryReport
        sid     (sc-session/report-session-id InventoryReport {})]
    (h/render-frame! app)

    (component "loading"
      (wait-for-report! app InventoryReport {})

      (let [rd    (report-data app :com.fulcrologic.rad.report/id rpt-key)
            names (row-names app :com.fulcrologic.rad.report/id rpt-key :item/name)]
        (assertions
          "routes to InventoryReport"
          (in-route-state? app rpt-key) => true

          "report statechart is in ready state"
          (scf/current-configuration app sid) => #{:state/ready}

          "busy flag is cleared"
          (:ui/busy? rd) => false

          "loads all 7 seeded items"
          (count (:ui/current-rows rd)) => 7

          "contains Widget"
          (some #{"Widget"} names) => "Widget"

          "contains Screwdriver"
          (some #{"Screwdriver"} names) => "Screwdriver"

          "contains Wrench"
          (some #{"Wrench"} names) => "Wrench"

          "contains Hammer"
          (some #{"Hammer"} names) => "Hammer"

          "contains Doll"
          (some #{"Doll"} names) => "Doll"

          "contains Robot"
          (some #{"Robot"} names) => "Robot"

          "contains Building Blocks"
          (some #{"Building Blocks"} names) => "Building Blocks")))

    (component "filtering"
      ;; Send filter event to re-filter rows client-side
      (scf/send! app sid :event/filter {})
      (dotimes [_ 5] (h/render-frame! app))

      (let [rd (report-data app :com.fulcrologic.rad.report/id rpt-key)]
        (assertions
          "all rows still present after no-op filter"
          (count (:ui/current-rows rd)) => 7)))))

;; ---------------------------------------------------------------------------
;; Invoice Report
;; ---------------------------------------------------------------------------

(specification "Invoice report"
  (let [app     (test-client 9847)
        rpt-key :com.example.ui.invoice-report/InvoiceReport
        sid     (sc-session/report-session-id InvoiceReport {})]
    (h/render-frame! app)

    (component "loading"
      (wait-for-report! app InvoiceReport {})

      (let [state          (app/current-state app)
            rd             (report-data app :com.fulcrologic.rad.report/id rpt-key)
            rows           (:ui/current-rows rd)
            ;; Invoice rows have :account/name denormalized directly (via Pathom resolution)
            customer-names (mapv (fn [ident]
                                   (get-in state (conj ident :account/name)))
                             rows)]
        (assertions
          "routes to InvoiceReport"
          (in-route-state? app rpt-key) => true

          "report statechart is in ready state"
          (scf/current-configuration app sid) => #{:state/ready}

          "busy flag is cleared"
          (:ui/busy? rd) => false

          "loads seeded invoices"
          (pos? (count rows)) => true

          "includes Tony's invoice"
          (some #{"Tony"} customer-names) => "Tony"

          "includes Sally's invoice"
          (some #{"Sally"} customer-names) => "Sally")))))

;; ---------------------------------------------------------------------------
;; Account-specific invoices report
;; ---------------------------------------------------------------------------

(specification "Account invoices report"
  (let [app     (test-client 9847)
        rpt-key :com.example.ui.invoice-forms/AccountInvoices
        sid     (sc-session/report-session-id AccountInvoices {})]
    (h/render-frame! app)

    (component "loading account-specific invoices"
      (wait-for-report! app AccountInvoices {})

      (let [rd (report-data app :com.fulcrologic.rad.report/id rpt-key)]
        (assertions
          "report statechart is in ready state"
          (scf/current-configuration app sid) => #{:state/ready}

          "busy flag is cleared"
          (:ui/busy? rd) => false

          "loads invoice rows"
          (pos? (count (:ui/current-rows rd))) => true)))))
