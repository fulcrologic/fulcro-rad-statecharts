(ns com.example.headless-report-tests
  "Headless E2E integration tests for reports: inventory (load, filter), invoices (load) and per-account invoices
   (routed with an `:account/id` parameter), against a real server and a freshly seeded Datomic Local database.

   Loads are real HTTP round-trips, so tests wait with `await!` (poll until the report chart is in `:state/ready` with
   the expected rows, fail on timeout) rather than rendering a fixed number of frames. Assertions are on the
   normalized state map and statechart configurations."
  (:require
   [clojure.test :refer [use-fixtures]]
   [com.example.headless-client :refer [await! test-client]]
   [com.example.test-server :refer [with-test-system]]
   [com.example.ui.inventory-report :refer [InventoryReport]]
   [com.example.ui.invoice-forms :refer [AccountInvoices]]
   [com.example.ui.invoice-report :refer [InvoiceReport]]
   [com.fulcrologic.fulcro.application :as app]
   [com.fulcrologic.fulcro.components :as comp]
   [com.fulcrologic.fulcro.headless :as h]
   [com.fulcrologic.rad.ids :refer [new-uuid]]
   [com.fulcrologic.rad.statechart.report :as report]
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

(defn current-row-count
  "Returns the number of current rows of `report-class` in the app state."
  [app report-class]
  (count (:ui/current-rows (get-in (app/current-state app) (comp/get-ident report-class {})))))

(defn await-report!
  "Waits until the statechart of `report-class` is in `:state/ready` and the report has at least `min-rows` current
   rows (default 0). Returns the app."
  ([app report-class] (await-report! app report-class 0))
  ([app report-class min-rows]
   (let [sid (report/report-session-id report-class)]
     (await! app {:description (str (comp/component-name report-class) " in :state/ready with >= " min-rows " rows")
                  :session-id  sid}
       #(and (contains? (scf/current-configuration % sid) :state/ready)
          (<= min-rows (current-row-count % report-class)))))))

(defn route-to-report!
  "Routes to `report-class` with `route-params` and waits (see `await-report!`) for its load to finish with at least
   `min-rows` rows. Returns the app."
  ([app report-class route-params] (route-to-report! app report-class route-params 0))
  ([app report-class route-params min-rows]
   (scr/route-to! app report-class route-params)
   (await-report! app report-class min-rows)))

;; ---------------------------------------------------------------------------
;; Inventory Report
;; ---------------------------------------------------------------------------

(specification "Inventory report"
  (let [app     (test-client 9847)
        rpt-key :com.example.ui.inventory-report/InventoryReport
        sid     (report/report-session-id InventoryReport)]
    (h/render-frame! app)

    (component "loading"
      (route-to-report! app InventoryReport {} 1)

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
      (await-report! app InventoryReport)

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
        sid     (report/report-session-id InvoiceReport)]
    (h/render-frame! app)

    (component "loading"
      (route-to-report! app InvoiceReport {} 1)

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
        sid     (report/report-session-id AccountInvoices)]
    (h/render-frame! app)

    (component "loading account-specific invoices"
      ;; Sam (101) has exactly one seeded invoice: invoice-3, 2 Wrench + 2 Hammer at 12.50 = 50.00
      (route-to-report! app AccountInvoices {:account/id (new-uuid 101)} 1)

      (let [state (app/current-state app)
            rd    (report-data app :com.fulcrologic.rad.report/id rpt-key)
            rows  (:ui/current-rows rd)]
        (assertions
          "report statechart is in ready state"
          (scf/current-configuration app sid) => #{:state/ready}

          "busy flag is cleared"
          (:ui/busy? rd) => false

          "loads exactly the account's one seeded invoice"
          (count rows) => 1

          "the row is that account's invoice (total 50.00)"
          (mapv #(compare 50.00M (get-in state (conj % :invoice/total))) rows) => [0])))))
