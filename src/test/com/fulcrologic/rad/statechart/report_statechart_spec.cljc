(ns com.fulcrologic.rad.statechart.report-statechart-spec
  "Tier 1: Pure statechart unit tests for the RAD report chart.
   Tests state transitions and expression execution without any Fulcro app.
   All expressions are mocked — this tests the chart structure, not the expression logic."
  (:require
    [com.fulcrologic.rad.statechart.report-chart :as rc]
    [com.fulcrologic.rad.statechart.report-expressions :as rexpr]
    [com.fulcrologic.statecharts.testing :as t]
    [fulcro-spec.core :refer [=> assertions component specification]]))

;; ===== Helpers =====

(defn report-test-env
  "Creates a testing env for the report chart with optional mock overrides.
   `run-on-mount?` controls `should-run-on-mount?` predicate.
   `cache-expired?` controls the `cache-expired?` predicate."
  [{:keys [run-on-mount? cache-expired?]
    :or   {run-on-mount? true cache-expired? true}
    :as   overrides}]
  (let [;; The anonymous fn in report_chart for sorting/filtering on-entry needs mocking too.
        ;; We'll mock all known expression refs.
        base-mocks {rexpr/initialize-params-expr        nil
                    rexpr/should-run-on-mount?          run-on-mount?
                    rexpr/start-load-expr               nil
                    rexpr/process-loaded-data-expr      nil
                    rexpr/goto-page-expr                nil
                    rexpr/next-page-expr                nil
                    rexpr/prior-page-expr               nil
                    rexpr/select-row-expr               nil
                    rexpr/set-params-expr               nil
                    rexpr/do-sort-and-clear-busy-expr   nil
                    rexpr/do-filter-and-clear-busy-expr nil
                    rexpr/clear-sort-expr               nil
                    rexpr/cache-expired?                cache-expired?
                    rexpr/reinitialize-params-expr      nil
                    rexpr/resume-from-cache-expr        nil}
        mocks      (merge base-mocks (dissoc overrides :run-on-mount? :cache-expired?))]
    (t/new-testing-env {:statechart rc/report-statechart} mocks)))

;; ===== Initialization =====

(specification "Report chart — initialization"
  (component "Auto-load on mount"
    (let [env (report-test-env {:run-on-mount? true})]
      (t/start! env)
      (assertions
        "Runs initialize-params-expr on entry"
        (t/ran? env rexpr/initialize-params-expr) => true

        "Transitions to :state/loading when run-on-mount? is true"
        (t/in? env :state/loading) => true

        "Runs start-load-expr on entry to loading"
        (t/ran? env rexpr/start-load-expr) => true)))

  (component "No auto-load"
    (let [env (report-test-env {:run-on-mount? false})]
      (t/start! env)
      (assertions
        "Transitions directly to :state/ready when run-on-mount? is false"
        (t/in? env :state/ready) => true))))

;; ===== Load Flow =====

(specification "Report chart — load flow"
  (component "Successful load"
    (let [env (report-test-env {:run-on-mount? true})]
      (t/start! env)
      (t/run-events! env :event/loaded)
      (assertions
        "Transitions to :state/processing then :state/ready"
        (t/in? env :state/ready) => true

        "Runs process-loaded-data-expr"
        (t/ran? env rexpr/process-loaded-data-expr) => true)))

  (component "Failed load"
    (let [env (report-test-env {:run-on-mount? true})]
      (t/start! env)
      (t/run-events! env :event/failed)
      (assertions
        "Transitions to :state/ready on load failure"
        (t/in? env :state/ready) => true))))

;; ===== Ready State Interactions =====

(specification "Report chart — ready state interactions"
  (component "Manual reload"
    (let [env (report-test-env {:run-on-mount? false})]
      (t/start! env)
      (t/run-events! env :event/run)
      (assertions
        "Transitions to :state/loading on :event/run"
        (t/in? env :state/loading) => true)))

  (component "Pagination — goto-page"
    (let [env (report-test-env {:run-on-mount? false})]
      (t/start! env)
      (t/run-events! env :event/goto-page)
      (assertions
        "Stays in :state/ready"
        (t/in? env :state/ready) => true

        "Runs goto-page-expr"
        (t/ran? env rexpr/goto-page-expr) => true)))

  (component "Pagination — next-page"
    (let [env (report-test-env {:run-on-mount? false})]
      (t/start! env)
      (t/run-events! env :event/next-page)
      (assertions
        "Stays in :state/ready"
        (t/in? env :state/ready) => true

        "Runs next-page-expr"
        (t/ran? env rexpr/next-page-expr) => true)))

  (component "Pagination — prior-page"
    (let [env (report-test-env {:run-on-mount? false})]
      (t/start! env)
      (t/run-events! env :event/prior-page)
      (assertions
        "Stays in :state/ready"
        (t/in? env :state/ready) => true

        "Runs prior-page-expr"
        (t/ran? env rexpr/prior-page-expr) => true)))

  (component "Row selection"
    (let [env (report-test-env {:run-on-mount? false})]
      (t/start! env)
      (t/run-events! env :event/select-row)
      (assertions
        "Stays in :state/ready"
        (t/in? env :state/ready) => true

        "Runs select-row-expr"
        (t/ran? env rexpr/select-row-expr) => true)))

  (component "Set UI parameters"
    (let [env (report-test-env {:run-on-mount? false})]
      (t/start! env)
      (t/run-events! env :event/set-ui-parameters)
      (assertions
        "Stays in :state/ready"
        (t/in? env :state/ready) => true

        "Runs set-params-expr"
        (t/ran? env rexpr/set-params-expr) => true)))

  (component "Clear sort"
    (let [env (report-test-env {:run-on-mount? false})]
      (t/start! env)
      (t/run-events! env :event/clear-sort)
      (assertions
        "Stays in :state/ready"
        (t/in? env :state/ready) => true

        "Runs clear-sort-expr"
        (t/ran? env rexpr/clear-sort-expr) => true))))

;; ===== Sort and Filter (Observable Intermediate States) =====

(specification "Report chart — sort and filter"
  (component "Sort flow"
    (let [env (report-test-env {:run-on-mount? false})]
      (t/start! env)
      (t/run-events! env :event/sort)
      (assertions
        "Returns to :state/ready after sorting (via eventless transition)"
        (t/in? env :state/ready) => true

        "Runs do-sort-and-clear-busy-expr"
        (t/ran? env rexpr/do-sort-and-clear-busy-expr) => true)))

  (component "Filter flow"
    (let [env (report-test-env {:run-on-mount? false})]
      (t/start! env)
      (t/run-events! env :event/filter)
      (assertions
        "Returns to :state/ready after filtering (via eventless transition)"
        (t/in? env :state/ready) => true

        "Runs do-filter-and-clear-busy-expr"
        (t/ran? env rexpr/do-filter-and-clear-busy-expr) => true))))

;; ===== Resume (Cache Handling) =====

(specification "Report chart — resume with cache"
  (component "Resume with expired cache"
    (let [env (report-test-env {:run-on-mount? false :cache-expired? true})]
      (t/start! env)
      (t/run-events! env :event/resume)
      (assertions
        "Transitions to :state/loading when cache is expired"
        (t/in? env :state/loading) => true

        "Runs reinitialize-params-expr"
        (t/ran? env rexpr/reinitialize-params-expr) => true)))

  (component "Resume with valid cache"
    (let [env (report-test-env {:run-on-mount? false :cache-expired? false})]
      (t/start! env)
      (t/run-events! env :event/resume)
      (assertions
        "Stays in :state/ready when cache is valid"
        (t/in? env :state/ready) => true

        "Runs resume-from-cache-expr"
        (t/ran? env rexpr/resume-from-cache-expr) => true))))

;; ===== goto-configuration! =====

(specification "Report chart — goto-configuration! for deep state testing"
  (component "Jump to ready and interact"
    (let [env (report-test-env {:run-on-mount? true})]
      (t/start! env)
      (t/goto-configuration! env [] #{:state/ready})
      (assertions
        "Can jump directly to :state/ready"
        (t/in? env :state/ready) => true)

      (t/run-events! env :event/run)
      (assertions
        "Can trigger reload from jumped state"
        (t/in? env :state/loading) => true)))

  (component "Jump to loading and complete"
    (let [env (report-test-env {:run-on-mount? false})]
      (t/start! env)
      (t/goto-configuration! env [] #{:state/loading})
      (assertions
        "Can jump directly to :state/loading"
        (t/in? env :state/loading) => true)

      (t/run-events! env :event/loaded)
      (assertions
        "Processes and reaches ready"
        (t/in? env :state/ready) => true))))
