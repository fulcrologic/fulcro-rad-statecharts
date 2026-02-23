(ns com.fulcrologic.rad.statechart.container-statechart-test
  "Tier 2: Fulcro headless component tests for the RAD container statechart.
   Uses a real Fulcro app with :event-loop? :immediate to verify that
   statechart + Fulcro state integration works correctly."
  (:require
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.rad.attributes :refer [defattr]]
    [com.fulcrologic.rad.attributes-options :as ao]
    [com.fulcrologic.rad.report-options :as ro]
    [com.fulcrologic.rad.statechart.application :as rad-app]
    [com.fulcrologic.rad.statechart.container :as container]
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

;; Two child reports for the container — using the function form to avoid CLJ macro issues
(def ReportA
  (report/report ::ReportA
    {ro/columns          [item-name item-price]
     ro/source-attribute :item/report-a-items
     ro/row-pk           item-id
     ro/run-on-mount?    false
     ro/route            "report-a"
     ro/title            "Report A"}))

(def ReportB
  (report/report ::ReportB
    {ro/columns          [item-name item-price]
     ro/source-attribute :item/report-b-items
     ro/row-pk           item-id
     ro/run-on-mount?    false
     ro/route            "report-b"
     ro/title            "Report B"}))

;; Container component — built programmatically (not via macro)
(def TestContainer
  (comp/sc ::TestContainer
    {:query               (fn [_] [:ui/parameters
                                   {:ui/controls (comp/get-query com.fulcrologic.rad.statechart.control/Control)}
                                   {::ReportA (comp/get-query ReportA)}
                                   {::ReportB (comp/get-query ReportB)}])
     :ident               (fn [_ _] [::container/id ::TestContainer])
     :initial-state       (fn [_] {:ui/parameters {}
                                   :ui/controls   []
                                   ::ReportA      (comp/get-initial-state ReportA {::report/id ::ReportA})
                                   ::ReportB      (comp/get-initial-state ReportB {::report/id ::ReportB})})
     ::container/children {::ReportA ReportA
                           ::ReportB ReportB}}
    (fn [this] nil)))

;; ===== Test Helpers =====

(defn test-app
  "Creates a headless Fulcro RAD app with synchronous (immediate) event processing."
  []
  (let [a (rad-app/fulcro-rad-app {})]
    (rad-app/install-statecharts! a {:event-loop? :immediate})
    a))

(defn container-sid
  "Returns the statechart session ID for the test container."
  []
  (sc.session/ident->session-id (comp/get-ident TestContainer {})))

(defn container-configuration
  "Returns the current configuration of the container session."
  [app]
  (scf/current-configuration app (container-sid)))

(defn container-in?
  "Checks if the container session is in the given state."
  [app state]
  (contains? (container-configuration app) state))

(defn child-report-sid
  "Returns the session ID for a child report given its class and container-assigned id."
  [child-class id]
  (container/child-report-session-id child-class id))

(defn child-report-in?
  "Checks if a child report session is in the given state."
  [app child-class id state]
  (contains? (scf/current-configuration app (child-report-sid child-class id)) state))

;; ===== Tests =====

(specification "Container statechart — Fulcro headless start"
  (let [app (test-app)]
    (container/start-container! app TestContainer {})
    (assertions
      "Container enters :state/ready after start"
      (container-in? app :state/ready) => true

      "Child report A is started (has a running session)"
      (some? (seq (scf/current-configuration app (child-report-sid ReportA ::ReportA)))) => true

      "Child report B is started (has a running session)"
      (some? (seq (scf/current-configuration app (child-report-sid ReportB ::ReportB)))) => true

      "Child report A enters :state/ready (run-on-mount? false)"
      (child-report-in? app ReportA ::ReportA :state/ready) => true

      "Child report B enters :state/ready (run-on-mount? false)"
      (child-report-in? app ReportB ::ReportB :state/ready) => true)))

(specification "Container statechart — Fulcro headless run triggers children"
  (let [app (test-app)]
    (container/start-container! app TestContainer {})
    ;; Send :event/run to the container, which broadcasts to children
    (scf/send! app (container-sid) :event/run)
    (assertions
      "Container stays in :state/ready after run"
      (container-in? app :state/ready) => true

      "Child report A transitions to :state/loading on run"
      (child-report-in? app ReportA ::ReportA :state/loading) => true

      "Child report B transitions to :state/loading on run"
      (child-report-in? app ReportB ::ReportB :state/loading) => true)))

(specification "Container statechart — Fulcro headless resume"
  (let [app (test-app)]
    (container/start-container! app TestContainer {})
    ;; Resume should broadcast :event/resume to children
    (scf/send! app (container-sid) :event/resume {:route-params {}})
    (assertions
      "Container stays in :state/ready after resume"
      (container-in? app :state/ready) => true)))

;; NOTE: The container sends :event/unmount to children on exit from :state/ready,
;; but the standard report statechart does NOT handle :event/unmount — it simply
;; ignores the event. This is a known limitation (S3). The broadcast happens but
;; has no effect on standard reports.
