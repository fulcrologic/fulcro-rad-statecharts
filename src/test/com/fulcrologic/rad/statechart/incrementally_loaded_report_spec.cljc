(ns com.fulcrologic.rad.statechart.incrementally-loaded-report-spec
  "Tier 1: Pure statechart unit tests for the incrementally-loaded report chart.
   Tests state transitions and expression execution without any Fulcro app.
   All expressions are mocked — this tests the chart structure, not the expression logic."
  (:require
    [com.fulcrologic.rad.statechart.incrementally-loaded-report :as ilr]
    [com.fulcrologic.rad.statechart.report-expressions :as rexpr]
    [com.fulcrologic.statecharts.testing :as t]
    [fulcro-spec.core :refer [=> assertions component specification]]))

;; ===== Helpers =====

(defn il-report-test-env
  "Creates a testing env for the incrementally-loaded report chart with optional mock overrides.
   `run-on-mount?` controls `should-run-on-mount?` predicate.
   `loading-complete?` controls the `loading-complete?` predicate.
   `cache-expired?` controls the `cache-expired?` predicate."
  [{:keys [run-on-mount? loading-complete? cache-expired?]
    :or   {run-on-mount? true loading-complete? true cache-expired? true}
    :as   overrides}]
  (let [base-mocks {ilr/incremental-init-expr           nil
                    rexpr/should-run-on-mount?          run-on-mount?
                    ilr/start-chunk-load-expr           nil
                    ilr/process-chunk-expr              nil
                    ilr/loading-complete?               loading-complete?
                    ilr/finalize-report-expr            nil
                    ilr/cache-expired?                  cache-expired?
                    ilr/resume-from-cache-expr          nil
                    rexpr/do-sort-and-clear-busy-expr   nil
                    rexpr/do-filter-and-clear-busy-expr nil
                    rexpr/goto-page-expr                nil
                    rexpr/next-page-expr                nil
                    rexpr/prior-page-expr               nil
                    rexpr/select-row-expr               nil
                    rexpr/set-params-expr               nil}
        mocks      (merge base-mocks (dissoc overrides :run-on-mount? :loading-complete? :cache-expired?))]
    (t/new-testing-env {:statechart ilr/incrementally-loaded-report-statechart} mocks)))

;; ===== Initialization =====

(specification "Incrementally-loaded report chart — initialization"
  (component "Auto-load on mount"
    (let [env (il-report-test-env {:run-on-mount? true})]
      (t/start! env)
      (assertions
        "Runs incremental-init-expr on entry"
        (t/ran? env ilr/incremental-init-expr) => true

        "Transitions to :state/loading when run-on-mount? is true"
        (t/in? env :state/loading) => true

        "Runs start-chunk-load-expr on entry to loading"
        (t/ran? env ilr/start-chunk-load-expr) => true)))

  (component "No auto-load"
    (let [env (il-report-test-env {:run-on-mount? false})]
      (t/start! env)
      (assertions
        "Transitions directly to :state/ready when run-on-mount? is false"
        (t/in? env :state/ready) => true))))

;; ===== Load Flow — Single Chunk (Complete) =====

(specification "Incrementally-loaded report chart — single chunk load"
  (component "All data in one chunk (loading-complete? true)"
    (let [env (il-report-test-env {:run-on-mount? true :loading-complete? true})]
      (t/start! env)
      (t/run-events! env :event/page-loaded)
      (assertions
        "Transitions through processing-chunk → finalizing → ready"
        (t/in? env :state/ready) => true

        "Runs process-chunk-expr"
        (t/ran? env ilr/process-chunk-expr) => true

        "Runs finalize-report-expr"
        (t/ran? env ilr/finalize-report-expr) => true))))

;; ===== Load Flow — Multiple Chunks =====

(specification "Incrementally-loaded report chart — multiple chunk load"
  (component "Incomplete first chunk (loading-complete? false)"
    (let [env (il-report-test-env {:run-on-mount? true :loading-complete? false})]
      (t/start! env)
      ;; First chunk loaded
      (t/run-events! env :event/page-loaded)
      (assertions
        "Stays in :state/processing-chunk waiting for next chunk"
        (t/in? env :state/processing-chunk) => true

        "Does NOT run finalize-report-expr yet"
        (t/ran? env ilr/finalize-report-expr) => false)))

  (component "Completion after final chunk (via goto-configuration! + event)"
    (let [;; Use loading-complete? = true so eventless transition fires after re-entry
          env (il-report-test-env {:run-on-mount? false :loading-complete? true})]
      (t/start! env)
      ;; Jump to processing-chunk, then send page-loaded to trigger self-transition
      ;; On re-entry, loading-complete? fires the eventless transition to finalizing
      (t/goto-configuration! env [] #{:state/processing-chunk})
      (t/run-events! env :event/page-loaded)
      (assertions
        "Transitions through finalizing to :state/ready when loading is complete"
        (t/in? env :state/ready) => true

        "Runs finalize-report-expr"
        (t/ran? env ilr/finalize-report-expr) => true))))

;; ===== Load Failure =====

(specification "Incrementally-loaded report chart — load failure"
  (component "Failed during initial load"
    (let [env (il-report-test-env {:run-on-mount? true})]
      (t/start! env)
      (t/run-events! env :event/failed)
      (assertions
        "Transitions to :state/ready on failure from loading"
        (t/in? env :state/ready) => true)))

  (component "Failed during chunk processing"
    (let [env (il-report-test-env {:run-on-mount? true :loading-complete? false})]
      (t/start! env)
      (t/run-events! env :event/page-loaded)
      ;; Now in processing-chunk, fail
      (t/run-events! env :event/failed)
      (assertions
        "Transitions to :state/ready on failure from processing-chunk"
        (t/in? env :state/ready) => true))))

;; ===== Ready State — Sort and Filter (Client-Side) =====

(specification "Incrementally-loaded report chart — sort and filter"
  (component "Sort flow (client-side)"
    (let [env (il-report-test-env {:run-on-mount? false})]
      (t/start! env)
      (t/run-events! env :event/sort)
      (assertions
        "Returns to :state/ready after sorting (via observable :state/sorting)"
        (t/in? env :state/ready) => true

        "Runs do-sort-and-clear-busy-expr"
        (t/ran? env rexpr/do-sort-and-clear-busy-expr) => true)))

  (component "Filter flow (client-side)"
    (let [env (il-report-test-env {:run-on-mount? false})]
      (t/start! env)
      (t/run-events! env :event/filter)
      (assertions
        "Returns to :state/ready after filtering (via observable :state/filtering)"
        (t/in? env :state/ready) => true

        "Runs do-filter-and-clear-busy-expr"
        (t/ran? env rexpr/do-filter-and-clear-busy-expr) => true))))

;; ===== Ready State — Pagination =====

(specification "Incrementally-loaded report chart — pagination"
  (component "Goto page"
    (let [env (il-report-test-env {:run-on-mount? false})]
      (t/start! env)
      (t/run-events! env :event/goto-page)
      (assertions
        "Stays in :state/ready"
        (t/in? env :state/ready) => true

        "Runs goto-page-expr"
        (t/ran? env rexpr/goto-page-expr) => true)))

  (component "Next page"
    (let [env (il-report-test-env {:run-on-mount? false})]
      (t/start! env)
      (t/run-events! env :event/next-page)
      (assertions
        "Stays in :state/ready"
        (t/in? env :state/ready) => true

        "Runs next-page-expr"
        (t/ran? env rexpr/next-page-expr) => true)))

  (component "Prior page"
    (let [env (il-report-test-env {:run-on-mount? false})]
      (t/start! env)
      (t/run-events! env :event/prior-page)
      (assertions
        "Stays in :state/ready"
        (t/in? env :state/ready) => true

        "Runs prior-page-expr"
        (t/ran? env rexpr/prior-page-expr) => true))))

;; ===== Ready State — Other Interactions =====

(specification "Incrementally-loaded report chart — ready state interactions"
  (component "Row selection"
    (let [env (il-report-test-env {:run-on-mount? false})]
      (t/start! env)
      (t/run-events! env :event/select-row)
      (assertions
        "Stays in :state/ready"
        (t/in? env :state/ready) => true

        "Runs select-row-expr"
        (t/ran? env rexpr/select-row-expr) => true)))

  (component "Set UI parameters"
    (let [env (il-report-test-env {:run-on-mount? false})]
      (t/start! env)
      (t/run-events! env :event/set-ui-parameters)
      (assertions
        "Stays in :state/ready"
        (t/in? env :state/ready) => true

        "Runs set-params-expr"
        (t/ran? env rexpr/set-params-expr) => true)))

  (component "Manual reload"
    (let [env (il-report-test-env {:run-on-mount? false})]
      (t/start! env)
      (t/run-events! env :event/run)
      (assertions
        "Transitions to :state/loading on :event/run"
        (t/in? env :state/loading) => true))))

;; ===== Resume (Cache Handling) =====

(specification "Incrementally-loaded report chart — resume with cache"
  (component "Resume with expired cache"
    (let [env (il-report-test-env {:run-on-mount? false :cache-expired? true})]
      (t/start! env)
      (t/run-events! env :event/resume)
      (assertions
        "Transitions to :state/loading when cache is expired"
        (t/in? env :state/loading) => true

        "Runs incremental-init-expr for re-initialization"
        (t/ran? env ilr/incremental-init-expr) => true)))

  (component "Resume with valid cache"
    (let [env (il-report-test-env {:run-on-mount? false :cache-expired? false})]
      (t/start! env)
      (t/run-events! env :event/resume)
      (assertions
        "Stays in :state/ready when cache is valid"
        (t/in? env :state/ready) => true

        "Runs resume-from-cache-expr"
        (t/ran? env ilr/resume-from-cache-expr) => true))))

;; ===== goto-configuration! =====

(specification "Incrementally-loaded report chart — goto-configuration! for deep state testing"
  (component "Jump to ready and interact"
    (let [env (il-report-test-env {:run-on-mount? true})]
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
    (let [env (il-report-test-env {:run-on-mount? false :loading-complete? true})]
      (t/start! env)
      (t/goto-configuration! env [] #{:state/loading})
      (assertions
        "Can jump directly to :state/loading"
        (t/in? env :state/loading) => true)

      (t/run-events! env :event/page-loaded)
      (assertions
        "Processes chunks and reaches ready"
        (t/in? env :state/ready) => true))))
