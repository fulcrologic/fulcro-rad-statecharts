(ns com.fulcrologic.rad.statechart.report-statechart-test
  "Tier 2: Fulcro headless component tests for the RAD report statechart.
   Uses a real Fulcro app with :event-loop? :immediate to verify that
   statechart + Fulcro state integration works correctly."
  (:require
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.rad.attributes :refer [defattr]]
    [com.fulcrologic.rad.attributes-options :as ao]
    [com.fulcrologic.rad.report-options :as ro]
    [com.fulcrologic.rad.statechart.application :as rad-app]
    [com.fulcrologic.rad.statechart.report :as report]
    [com.fulcrologic.rad.statechart.session :as sc.session]
    [com.fulcrologic.statecharts.integration.fulcro :as scf]
    [fulcro-spec.core :refer [=> assertions specification]]))

;; ===== Test Model =====

(defattr item-id :item/id :uuid
  {ao/identity? true})

(defattr item-name :item/name :string
  {ao/identities #{:item/id}})

(defattr item-price :item/price :int
  {ao/identities #{:item/id}})

;; Use the report helper function (not defsc-report macro) to avoid CLJ macro expansion bug
(def ItemReport
  (report/report ::ItemReport
    {ro/columns          [item-name item-price]
     ro/source-attribute :item/all-items
     ro/row-pk           item-id
     ro/run-on-mount?    true
     ro/route            "items"
     ro/title            "Items"}))

(def LazyReport
  (report/report ::LazyReport
    {ro/columns          [item-name item-price]
     ro/source-attribute :item/all-items
     ro/row-pk           item-id
     ro/run-on-mount?    false
     ro/route            "lazy-items"
     ro/title            "Lazy Items"}))

;; ===== Test Helpers =====

(defn test-app
  "Creates a headless Fulcro RAD app with synchronous (immediate) event processing."
  []
  (let [a (rad-app/fulcro-rad-app {})]
    (rad-app/install-statecharts! a {:event-loop? :immediate})
    a))

(defn report-sid
  "Returns the statechart session ID for a report class."
  [report-class]
  (sc.session/ident->session-id (comp/ident report-class {})))

(defn report-configuration
  "Returns the current configuration of a report session."
  [app report-class]
  (scf/current-configuration app (report-sid report-class)))

(defn report-in?
  "Checks if a report session is in the given state."
  [app report-class state]
  (contains? (report-configuration app report-class) state))

;; ===== Tests =====

(specification "Report statechart — Fulcro headless auto-load on mount"
  (let [app (test-app)]
    (report/start-report! app ItemReport)
    (assertions
      "Report with run-on-mount? true enters :state/loading"
      (report-in? app ItemReport :state/loading) => true)

    ;; Simulate successful load
    (let [sid (report-sid ItemReport)]
      (scf/send! app sid :event/loaded)
      (assertions
        "Report transitions to :state/ready after load"
        (report-in? app ItemReport :state/ready) => true))))

(specification "Report statechart — Fulcro headless no auto-load"
  (let [app (test-app)]
    (report/start-report! app LazyReport)
    (assertions
      "Report with run-on-mount? false enters :state/ready directly"
      (report-in? app LazyReport :state/ready) => true)))

(specification "Report statechart — Fulcro headless manual run"
  (let [app (test-app)]
    (report/start-report! app LazyReport)
    (let [sid (report-sid LazyReport)]
      (scf/send! app sid :event/run)
      (assertions
        "Report transitions to :state/loading on :event/run"
        (report-in? app LazyReport :state/loading) => true)

      (scf/send! app sid :event/loaded)
      (assertions
        "Report transitions to :state/ready after load completes"
        (report-in? app LazyReport :state/ready) => true))))

(specification "Report statechart — Fulcro headless failed load"
  (let [app (test-app)]
    (report/start-report! app ItemReport)
    (let [sid (report-sid ItemReport)]
      (scf/send! app sid :event/failed)
      (assertions
        "Report transitions to :state/ready even on failure"
        (report-in? app ItemReport :state/ready) => true))))

(specification "Report statechart — Fulcro headless pagination events"
  (let [app (test-app)]
    (report/start-report! app LazyReport)
    (let [sid (report-sid LazyReport)]
      (scf/send! app sid :event/goto-page {:page 2})
      (assertions
        "Stays in :state/ready after goto-page"
        (report-in? app LazyReport :state/ready) => true)

      (scf/send! app sid :event/next-page)
      (assertions
        "Stays in :state/ready after next-page"
        (report-in? app LazyReport :state/ready) => true)

      (scf/send! app sid :event/prior-page)
      (assertions
        "Stays in :state/ready after prior-page"
        (report-in? app LazyReport :state/ready) => true))))

(specification "Report statechart — Fulcro headless row selection"
  (let [app (test-app)]
    (report/start-report! app LazyReport)
    (let [sid (report-sid LazyReport)]
      (scf/send! app sid :event/select-row {:row 0})
      (assertions
        "Stays in :state/ready after row selection"
        (report-in? app LazyReport :state/ready) => true))))
