(ns com.fulcrologic.rad.statechart.form-statechart-spec
  "Tier 1: Pure statechart unit tests for the RAD form chart.
   Tests state transitions and expression execution without any Fulcro app.
   All expressions are mocked — this tests the chart structure, not the expression logic."
  (:require
    [com.fulcrologic.rad.statechart.form-chart :as fc]
    [com.fulcrologic.rad.statechart.form-expressions :as fex]
    [com.fulcrologic.statecharts.testing :as t]
    [fulcro-spec.core :refer [=> assertions component specification]]))

;; ===== Helpers =====

(defn form-test-env
  "Creates a testing env for the form chart with optional mock overrides.
   By default, all expressions are mocked to no-op (nil return).
   `create?` controls whether the initial state dispatches to creating or loading.
   `valid?` controls the form-valid? predicate."
  [{:keys [create? valid?]
    :or   {create? true valid? true}
    :as   overrides}]
  (let [base-mocks {fex/store-options                 nil
                    fex/create?                       create?
                    fex/start-create-expr             nil
                    fex/start-load-expr               nil
                    fex/on-loaded-expr                nil
                    fex/on-load-failed-expr           nil
                    fex/attribute-changed-expr        nil
                    fex/blur-expr                     nil
                    fex/mark-all-complete-expr        nil
                    fex/mark-complete-on-invalid-expr nil
                    fex/form-valid?                   valid?
                    fex/prepare-save-expr             nil
                    fex/on-saved-expr                 nil
                    fex/on-save-failed-expr           nil
                    fex/undo-all-expr                 nil
                    fex/prepare-leave-expr            nil
                    fex/leave-form-expr               nil
                    fex/route-denied-expr             nil
                    fex/continue-abandoned-route-expr nil
                    fex/clear-route-denied-expr       nil
                    fex/add-row-expr                  nil
                    fex/delete-row-expr               nil}
        mocks      (merge base-mocks (dissoc overrides :create? :valid?))]
    (t/new-testing-env {:statechart fc/form-chart} mocks)))

(defn terminated?
  "Returns true if the statechart session has terminated (entered a final state).
   When a top-level final state is reached, the configuration is cleared to #{}."
  [env]
  (empty? (t/current-configuration env)))

;; ===== Create Flow =====

(specification "Form chart — create flow"
  (let [env (form-test-env {:create? true})]
    (t/start! env)
    (assertions
      "Runs store-options on entry to :initial"
      (t/ran? env fex/store-options) => true

      "Runs create? condition check"
      (t/ran? env fex/create?) => true

      "Runs start-create-expr on entry to :state/creating"
      (t/ran? env fex/start-create-expr) => true

      "Ends in :state/editing via eventless transition from :state/creating"
      (t/in? env :state/editing) => true)))

;; ===== Edit (Load) Flow =====

(specification "Form chart — edit/load flow"
  (component "Successful load"
    (let [env (form-test-env {:create? false})]
      (t/start! env)
      (assertions
        "Transitions to :state/loading when create? is false"
        (t/in? env :state/loading) => true

        "Runs start-load-expr on entry to loading"
        (t/ran? env fex/start-load-expr) => true)

      (t/run-events! env :event/loaded)
      (assertions
        "Transitions to :state/editing after :event/loaded"
        (t/in? env :state/editing) => true

        "Runs on-loaded-expr"
        (t/ran? env fex/on-loaded-expr) => true)))

  (component "Failed load"
    (let [env (form-test-env {:create? false})]
      (t/start! env)
      (t/run-events! env :event/failed)
      (assertions
        "Transitions to :state/load-failed on :event/failed"
        (t/in? env :state/load-failed) => true

        "Runs on-load-failed-expr"
        (t/ran? env fex/on-load-failed-expr) => true)))

  (component "Reload from loading"
    (let [env (form-test-env {:create? false})]
      (t/start! env)
      (t/run-events! env :event/reload)
      (assertions
        "Stays in :state/loading on :event/reload"
        (t/in? env :state/loading) => true)))

  (component "Exit from loading"
    (let [env (form-test-env {:create? false})]
      (t/start! env)
      (t/run-events! env :event/exit)
      (assertions
        "Terminates the session (reaches final state)"
        (terminated? env) => true)))

  (component "Retry from load-failed"
    (let [env (form-test-env {:create? false})]
      (t/start! env)
      (t/run-events! env :event/failed)
      (t/run-events! env :event/reload)
      (assertions
        "Transitions back to :state/loading on retry"
        (t/in? env :state/loading) => true)))

  (component "Exit from load-failed"
    (let [env (form-test-env {:create? false})]
      (t/start! env)
      (t/run-events! env :event/failed)
      (t/run-events! env :event/exit)
      (assertions
        "Terminates the session"
        (terminated? env) => true))))

;; ===== Save Flow =====

(specification "Form chart — save flow"
  (component "Save when valid"
    (let [env (form-test-env {:create? true :valid? true})]
      (t/start! env)
      (assertions
        "Starts in editing"
        (t/in? env :state/editing) => true)

      (t/run-events! env :event/save)
      (assertions
        "Transitions to :state/saving when form is valid"
        (t/in? env :state/saving) => true

        "Runs prepare-save-expr"
        (t/ran? env fex/prepare-save-expr) => true)))

  (component "Save when invalid"
    (let [env (form-test-env {:create? true :valid? false})]
      (t/start! env)
      (t/run-events! env :event/save)
      (assertions
        "Stays in :state/editing when form is invalid"
        (t/in? env :state/editing) => true

        "Runs mark-complete-on-invalid-expr"
        (t/ran? env fex/mark-complete-on-invalid-expr) => true

        "Does NOT run prepare-save-expr"
        (t/ran? env fex/prepare-save-expr) => false)))

  (component "Save success"
    (let [env (form-test-env {:create? true :valid? true})]
      (t/start! env)
      (t/run-events! env :event/save)
      (t/run-events! env :event/saved)
      (assertions
        "Returns to :state/editing after :event/saved"
        (t/in? env :state/editing) => true

        "Runs on-saved-expr"
        (t/ran? env fex/on-saved-expr) => true)))

  (component "Save failure"
    (let [env (form-test-env {:create? true :valid? true})]
      (t/start! env)
      (t/run-events! env :event/save)
      (t/run-events! env :event/save-failed)
      (assertions
        "Returns to :state/editing after :event/save-failed"
        (t/in? env :state/editing) => true

        "Runs on-save-failed-expr"
        (t/ran? env fex/on-save-failed-expr) => true)))

  (component "Exit from saving"
    (let [env (form-test-env {:create? true :valid? true})]
      (t/start! env)
      (t/run-events! env :event/save)
      (t/run-events! env :event/exit)
      (assertions
        "Terminates the session from saving"
        (terminated? env) => true))))

;; ===== Editing Interactions =====

(specification "Form chart — editing interactions"
  (component "Attribute change"
    (let [env (form-test-env {:create? true})]
      (t/start! env)
      (t/run-events! env :event/attribute-changed)
      (assertions
        "Stays in :state/editing"
        (t/in? env :state/editing) => true

        "Runs attribute-changed-expr"
        (t/ran? env fex/attribute-changed-expr) => true)))

  (component "Blur"
    (let [env (form-test-env {:create? true})]
      (t/start! env)
      (t/run-events! env :event/blur)
      (assertions
        "Stays in :state/editing"
        (t/in? env :state/editing) => true

        "Runs blur-expr"
        (t/ran? env fex/blur-expr) => true)))

  (component "Mark complete"
    (let [env (form-test-env {:create? true})]
      (t/start! env)
      (t/run-events! env :event/mark-complete)
      (assertions
        "Stays in :state/editing"
        (t/in? env :state/editing) => true

        "Runs mark-all-complete-expr"
        (t/ran? env fex/mark-all-complete-expr) => true)))

  (component "Undo/reset"
    (let [env (form-test-env {:create? true})]
      (t/start! env)
      (t/run-events! env :event/reset)
      (assertions
        "Stays in :state/editing"
        (t/in? env :state/editing) => true

        "Runs undo-all-expr"
        (t/ran? env fex/undo-all-expr) => true)))

  (component "Add row (subform)"
    (let [env (form-test-env {:create? true})]
      (t/start! env)
      (t/run-events! env :event/add-row)
      (assertions
        "Stays in :state/editing"
        (t/in? env :state/editing) => true

        "Runs add-row-expr"
        (t/ran? env fex/add-row-expr) => true)))

  (component "Delete row (subform)"
    (let [env (form-test-env {:create? true})]
      (t/start! env)
      (t/run-events! env :event/delete-row)
      (assertions
        "Stays in :state/editing"
        (t/in? env :state/editing) => true

        "Runs delete-row-expr"
        (t/ran? env fex/delete-row-expr) => true)))

  (component "Reload from editing"
    (let [env (form-test-env {:create? true})]
      (t/start! env)
      (t/run-events! env :event/reload)
      (assertions
        "Transitions to :state/loading"
        (t/in? env :state/loading) => true)))

  (component "Exit from editing"
    (let [env (form-test-env {:create? true})]
      (t/start! env)
      (t/run-events! env :event/exit)
      (assertions
        "Terminates the session"
        (terminated? env) => true))))

;; ===== Cancel Flow =====

(specification "Form chart — cancel flow"
  (let [env (form-test-env {:create? true})]
    (t/start! env)
    (t/run-events! env :event/cancel)
    (assertions
      "Terminates the session (through :state/leaving to final)"
      (terminated? env) => true

      "Runs prepare-leave-expr"
      (t/ran? env fex/prepare-leave-expr) => true

      "Runs leave-form-expr on entry to :state/leaving"
      (t/ran? env fex/leave-form-expr) => true)))

;; ===== Route Guarding =====

(specification "Form chart — route guarding"
  (component "Route denied"
    (let [env (form-test-env {:create? true})]
      (t/start! env)
      (t/run-events! env :event/route-denied)
      (assertions
        "Stays in :state/editing"
        (t/in? env :state/editing) => true

        "Runs route-denied-expr"
        (t/ran? env fex/route-denied-expr) => true)))

  (component "Continue abandoned route"
    (let [env (form-test-env {:create? true})]
      (t/start! env)
      (t/run-events! env :event/continue-abandoned-route)
      (assertions
        "Stays in :state/editing"
        (t/in? env :state/editing) => true

        "Runs continue-abandoned-route-expr"
        (t/ran? env fex/continue-abandoned-route-expr) => true)))

  (component "Clear route denied"
    (let [env (form-test-env {:create? true})]
      (t/start! env)
      (t/run-events! env :event/clear-route-denied)
      (assertions
        "Stays in :state/editing"
        (t/in? env :state/editing) => true

        "Runs clear-route-denied-expr"
        (t/ran? env fex/clear-route-denied-expr) => true))))

;; ===== goto-configuration! tests =====

(specification "Form chart — goto-configuration! for deep state testing"
  (component "Jump to editing state"
    (let [env (form-test-env {:create? false})]
      (t/start! env)
      (t/goto-configuration! env [] #{:state/editing})
      (assertions
        "Can jump directly to :state/editing"
        (t/in? env :state/editing) => true)

      (t/run-events! env :event/cancel)
      (assertions
        "Can interact normally after goto-configuration!"
        (terminated? env) => true)))

  (component "Jump to saving state"
    (let [env (form-test-env {:create? false})]
      (t/start! env)
      (t/goto-configuration! env [] #{:state/saving})
      (assertions
        "Can jump directly to :state/saving"
        (t/in? env :state/saving) => true)

      (t/run-events! env :event/saved)
      (assertions
        "Can process events from jumped state"
        (t/in? env :state/editing) => true))))
