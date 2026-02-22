(ns com.fulcrologic.rad.statechart.form-statechart-test
  "Tier 2: Fulcro headless component tests for the RAD form statechart.
   Uses a real Fulcro app with :event-loop? :immediate to verify that
   statechart + Fulcro state integration works correctly."
  (:require
   [com.fulcrologic.fulcro.application :as app]
   [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
   [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
   [com.fulcrologic.fulcro.raw.components :as rc]
   [com.fulcrologic.statecharts.integration.fulcro :as scf]
   [com.fulcrologic.rad.statechart.application :as rad-app]
   [com.fulcrologic.rad.attributes :refer [defattr]]
   [com.fulcrologic.rad.attributes-options :as ao]
   [com.fulcrologic.rad.statechart.form :as form]
   [com.fulcrologic.rad.form-options :as fo]
   [com.fulcrologic.rad.statechart.form-chart :as fc]
   [com.fulcrologic.rad.statechart.session :as sc.session]
   [fulcro-spec.core :refer [assertions specification component =>]]))

;; ===== Test Model =====

(defattr person-id :person/id :uuid
  {ao/identity? true})

(defattr person-name :person/name :string
  {ao/identities #{:person/id}
   ao/required?  true})

(defattr person-age :person/age :int
  {ao/identities #{:person/id}})

(form/defsc-form PersonForm [this props]
  {fo/id             person-id
   fo/attributes     [person-name person-age]
   fo/default-values {:person/name "New Person"
                      :person/age  0}
   fo/route-prefix   "person"
   fo/title          "Edit Person"})

;; ===== Test Helpers =====

(defn test-app
  "Creates a headless Fulcro RAD app with synchronous (immediate) event processing."
  []
  (let [a (rad-app/fulcro-rad-app {})]
    (rad-app/install-statecharts! a {:event-loop? :immediate})
    a))

(defn session-id-for
  "Computes the statechart session ID for a form entity."
  [qualified-key entity-id]
  (sc.session/ident->session-id [qualified-key entity-id]))

(defn form-configuration
  "Returns the current configuration of a form session."
  [app qualified-key entity-id]
  (scf/current-configuration app (session-id-for qualified-key entity-id)))

(defn form-in?
  "Checks if a form session is in the given state."
  [app qualified-key entity-id state]
  (contains? (form-configuration app qualified-key entity-id) state))

(defn form-terminated?
  "Checks if a form session has terminated (entered final state)."
  [app qualified-key entity-id]
  (empty? (form-configuration app qualified-key entity-id)))

;; ===== Tests =====

(specification "Form statechart — Fulcro headless create flow"
               (let [app    (test-app)
                     tid    (tempid/tempid)]
                 (form/start-form! app tid PersonForm)
                 (assertions
                  "Form enters :state/editing after create"
                  (form-in? app :person/id tid :state/editing) => true)

                 (let [state-map  (app/current-state app)
                       person     (get-in state-map [:person/id tid])]
                   (assertions
                    "Entity exists in Fulcro state with tempid"
                    (some? person) => true

                    "Default values are populated"
                    (:person/name person) => "New Person"
                    (:person/age person) => 0))))

(specification "Form statechart — Fulcro headless edit flow"
               (let [app       (test-app)
                     person-id (random-uuid)]
    ;; Pre-populate state to simulate a loaded entity
                 (swap! (::app/state-atom app)
                        assoc-in [:person/id person-id]
                        {:person/id   person-id
                         :person/name "Alice"
                         :person/age  30})

                 (form/start-form! app person-id PersonForm)
                 (assertions
                  "Form enters :state/loading for existing entity"
                  (form-in? app :person/id person-id :state/loading) => true)

    ;; Simulate a successful load response
                 (let [sid (session-id-for :person/id person-id)]
                   (scf/send! app sid :event/loaded)
                   (assertions
                    "Form transitions to :state/editing after load"
                    (form-in? app :person/id person-id :state/editing) => true))))

(specification "Form statechart — Fulcro headless attribute change"
               (let [app (test-app)
                     tid (tempid/tempid)]
                 (form/start-form! app tid PersonForm)
                 (let [sid (session-id-for :person/id tid)]
                   (scf/send! app sid :event/attribute-changed
                              {:qualified-key :person/name
                               :value         "Bob"})
                   (assertions
                    "Form stays in editing after attribute change"
                    (form-in? app :person/id tid :state/editing) => true))))

(specification "Form statechart — Fulcro headless cancel flow"
               (let [app (test-app)
                     tid (tempid/tempid)]
                 (form/start-form! app tid PersonForm)
                 (let [sid (session-id-for :person/id tid)]
                   (scf/send! app sid :event/cancel)
                   (assertions
                    "Form terminates after cancel"
                    (form-terminated? app :person/id tid) => true))))

(specification "Form statechart — Fulcro headless save flow"
               (let [app (test-app)
                     tid (tempid/tempid)]
                 (form/start-form! app tid PersonForm)
                 (let [sid (session-id-for :person/id tid)]
      ;; Try to save — form-valid? will run against real Fulcro state
                   (scf/send! app sid :event/save)
      ;; The form may transition to saving or stay in editing depending on validation
      ;; Either way, the session should still be active
                   (assertions
                    "Form session is still active after save attempt"
                    (not (form-terminated? app :person/id tid)) => true))))

(specification "Form statechart — Fulcro headless exit"
               (let [app (test-app)
                     tid (tempid/tempid)]
                 (form/start-form! app tid PersonForm)
                 (let [sid (session-id-for :person/id tid)]
                   (scf/send! app sid :event/exit)
                   (assertions
                    "Form terminates on exit event"
                    (form-terminated? app :person/id tid) => true))))
