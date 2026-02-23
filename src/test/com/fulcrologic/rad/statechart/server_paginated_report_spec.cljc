(ns com.fulcrologic.rad.statechart.server-paginated-report-spec
  "Tier 1: Pure statechart unit tests for the server-paginated report chart.
   Tests state transitions and expression execution without any Fulcro app.
   All expressions are mocked — this tests the chart structure, not the expression logic."
  (:require
    [com.fulcrologic.rad.statechart.report-expressions :as rexpr]
    [com.fulcrologic.rad.statechart.server-paginated-report :as spr]
    [com.fulcrologic.statecharts.testing :as t]
    [fulcro-spec.core :refer [=> assertions component specification]]))

;; ===== Helpers =====

(defn sp-report-test-env
  "Creates a testing env for the server-paginated report chart with optional mock overrides.
   `run-on-mount?` controls `should-run-on-mount?` predicate.
   `page-cached?` controls the `page-cached?` predicate."
  [{:keys [run-on-mount? page-cached?]
    :or   {run-on-mount? true page-cached? false}
    :as   overrides}]
  (let [base-mocks {spr/server-paginated-init-expr   nil
                    rexpr/should-run-on-mount?       run-on-mount?
                    spr/load-server-page-expr        nil
                    spr/process-server-page-expr     nil
                    spr/page-cached?                 page-cached?
                    spr/serve-cached-page-expr       nil
                    spr/set-target-page-expr         nil
                    spr/update-sort-and-refresh-expr nil
                    spr/refresh-expr                 nil
                    spr/resume-server-paginated-expr nil
                    rexpr/select-row-expr            nil
                    rexpr/set-params-expr            nil}
        ;; Anonymous fns in the chart for next-page/prior-page transitions
        ;; need to be handled — they are inline in the chart definition, so
        ;; the testing env will run them unless we mock specific expression vars.
        ;; Since they are anonymous, they'll execute with nil data and return ops.
        ;; For Tier 1, this is acceptable — we only test state transitions.
        mocks      (merge base-mocks (dissoc overrides :run-on-mount? :page-cached?))]
    (t/new-testing-env {:statechart spr/server-paginated-report-statechart} mocks)))

;; ===== Initialization =====

(specification "Server-paginated report chart — initialization"
  (component "Auto-load on mount"
    (let [env (sp-report-test-env {:run-on-mount? true})]
      (t/start! env)
      (assertions
        "Runs server-paginated-init-expr on entry"
        (t/ran? env spr/server-paginated-init-expr) => true

        "Transitions to :state/loading-page when run-on-mount? is true"
        (t/in? env :state/loading-page) => true

        "Runs load-server-page-expr on entry to loading-page"
        (t/ran? env spr/load-server-page-expr) => true)))

  (component "No auto-load"
    (let [env (sp-report-test-env {:run-on-mount? false})]
      (t/start! env)
      (assertions
        "Transitions directly to :state/ready when run-on-mount? is false"
        (t/in? env :state/ready) => true))))

;; ===== Load Flow =====

(specification "Server-paginated report chart — load flow"
  (component "Successful page load"
    (let [env (sp-report-test-env {:run-on-mount? true})]
      (t/start! env)
      (t/run-events! env :event/page-loaded)
      (assertions
        "Transitions through :state/processing-page to :state/ready"
        (t/in? env :state/ready) => true

        "Runs process-server-page-expr"
        (t/ran? env spr/process-server-page-expr) => true)))

  (component "Failed page load"
    (let [env (sp-report-test-env {:run-on-mount? true})]
      (t/start! env)
      (t/run-events! env :event/failed)
      (assertions
        "Transitions to :state/ready on load failure"
        (t/in? env :state/ready) => true))))

;; ===== Ready State — Page Navigation =====

(specification "Server-paginated report chart — page navigation"
  (component "goto-page with cached page"
    (let [env (sp-report-test-env {:run-on-mount? false :page-cached? true})]
      (t/start! env)
      (t/run-events! env :event/goto-page)
      (assertions
        "Stays in :state/ready when page is cached"
        (t/in? env :state/ready) => true

        "Runs serve-cached-page-expr for cached pages"
        (t/ran? env spr/serve-cached-page-expr) => true)))

  (component "goto-page with uncached page"
    (let [env (sp-report-test-env {:run-on-mount? false :page-cached? false})]
      (t/start! env)
      (t/run-events! env :event/goto-page)
      (assertions
        "Transitions to :state/loading-page for uncached pages"
        (t/in? env :state/loading-page) => true

        "Runs set-target-page-expr"
        (t/ran? env spr/set-target-page-expr) => true)))

  (component "next-page"
    (let [env (sp-report-test-env {:run-on-mount? false})]
      (t/start! env)
      (t/run-events! env :event/next-page)
      (assertions
        "Stays in :state/ready (self-transition)"
        (t/in? env :state/ready) => true)))

  (component "prior-page"
    (let [env (sp-report-test-env {:run-on-mount? false})]
      (t/start! env)
      (t/run-events! env :event/prior-page)
      (assertions
        "Stays in :state/ready (self-transition)"
        (t/in? env :state/ready) => true))))

;; ===== Ready State — Sort and Filter =====

(specification "Server-paginated report chart — sort and filter"
  (component "Sort triggers server reload"
    (let [env (sp-report-test-env {:run-on-mount? false})]
      (t/start! env)
      (t/run-events! env :event/sort)
      (assertions
        "Transitions to :state/loading-page on sort (server re-sorts)"
        (t/in? env :state/loading-page) => true

        "Runs update-sort-and-refresh-expr"
        (t/ran? env spr/update-sort-and-refresh-expr) => true)))

  (component "Filter triggers server reload"
    (let [env (sp-report-test-env {:run-on-mount? false})]
      (t/start! env)
      (t/run-events! env :event/filter)
      (assertions
        "Transitions to :state/loading-page on filter (server re-filters)"
        (t/in? env :state/loading-page) => true

        "Runs refresh-expr"
        (t/ran? env spr/refresh-expr) => true))))

;; ===== Ready State — Other Interactions =====

(specification "Server-paginated report chart — ready state interactions"
  (component "Row selection"
    (let [env (sp-report-test-env {:run-on-mount? false})]
      (t/start! env)
      (t/run-events! env :event/select-row)
      (assertions
        "Stays in :state/ready"
        (t/in? env :state/ready) => true

        "Runs select-row-expr"
        (t/ran? env rexpr/select-row-expr) => true)))

  (component "Set UI parameters"
    (let [env (sp-report-test-env {:run-on-mount? false})]
      (t/start! env)
      (t/run-events! env :event/set-ui-parameters)
      (assertions
        "Stays in :state/ready"
        (t/in? env :state/ready) => true

        "Runs set-params-expr"
        (t/ran? env rexpr/set-params-expr) => true)))

  (component "Manual reload"
    (let [env (sp-report-test-env {:run-on-mount? false})]
      (t/start! env)
      (t/run-events! env :event/run)
      (assertions
        "Transitions to :state/loading-page on :event/run"
        (t/in? env :state/loading-page) => true)))

  (component "Resume"
    (let [env (sp-report-test-env {:run-on-mount? false})]
      (t/start! env)
      (t/run-events! env :event/resume)
      (assertions
        "Transitions to :state/loading-page on resume"
        (t/in? env :state/loading-page) => true

        "Runs resume-server-paginated-expr"
        (t/ran? env spr/resume-server-paginated-expr) => true))))

;; ===== goto-configuration! =====

(specification "Server-paginated report chart — goto-configuration! for deep state testing"
  (component "Jump to ready and interact"
    (let [env (sp-report-test-env {:run-on-mount? true})]
      (t/start! env)
      (t/goto-configuration! env [] #{:state/ready})
      (assertions
        "Can jump directly to :state/ready"
        (t/in? env :state/ready) => true)

      (t/run-events! env :event/run)
      (assertions
        "Can trigger reload from jumped state"
        (t/in? env :state/loading-page) => true)))

  (component "Jump to loading-page and complete"
    (let [env (sp-report-test-env {:run-on-mount? false})]
      (t/start! env)
      (t/goto-configuration! env [] #{:state/loading-page})
      (assertions
        "Can jump directly to :state/loading-page"
        (t/in? env :state/loading-page) => true)

      (t/run-events! env :event/page-loaded)
      (assertions
        "Processes and reaches ready"
        (t/in? env :state/ready) => true))))
