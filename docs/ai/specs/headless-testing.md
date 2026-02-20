# Spec: Headless Testing Strategy

**Status**: backlog
**Priority**: P1
**Created**: 2026-02-20
**Owner**: conductor
**Depends-on**: project-setup, form-statechart, report-statechart, routing-conversion

## Context

fulcro-rad-statecharts must be testable entirely without a browser. The statecharts library provides a `testing` namespace for pure statechart testing, while Fulcro provides headless rendering (`com.fulcrologic.fulcro.headless`) for integration testing with a real server. The routing system provides `SimulatedURLHistory` for cross-platform URL sync testing.

This spec defines the testing strategy across three tiers:

1. **Unit tests** -- Pure statechart logic via `testing/new-testing-env` (no Fulcro, no server)
2. **Component tests** -- Fulcro headless app with synchronous event loop, no server
3. **Integration tests** -- Full server + headless client via `h/build-test-app`

All tests must be CLJC (or CLJ-only where noted) and run via Kaocha in a REPL.

## Requirements

1. Form statecharts must be testable headlessly: create, edit, save, cancel, validation, dirty state
2. Report statecharts must be testable headlessly: load, sort, filter, paginate
3. Routing must be testable headlessly: navigate, guards, URL sync with `SimulatedURLHistory`
4. Container statecharts must be testable headlessly
5. All test files must be `.cljc` for cross-platform compatibility
6. Tests must work with Kaocha via REPL (no CLI commands)
7. The test suite must demonstrate patterns that downstream applications can follow

## Affected Modules

- `src/test/com/fulcrologic/rad/form_statechart_test.cljc` - Form statechart unit + component tests
- `src/test/com/fulcrologic/rad/report_statechart_test.cljc` - Report statechart unit + component tests
- `src/test/com/fulcrologic/rad/routing_test.cljc` - Routing headless tests
- `src/test/com/fulcrologic/rad/container_test.cljc` - Container headless tests
- `src/test/com/fulcrologic/rad/integration_test.clj` - Full-stack integration examples (CLJ-only)

## Approach

### Tier 1: Pure Statechart Unit Tests

Use `com.fulcrologic.statecharts.testing` to test statechart logic in isolation. No Fulcro app, no DOM, no server. Expressions are mocked or run unmocked against the working-memory data model.

#### Testing Utilities

```clojure
(require '[com.fulcrologic.statecharts.testing :as t])

;; Create a testing environment with optional mocks
(let [env (t/new-testing-env {:statechart my-chart} {some-predicate true})]
  (t/start! env)
  (t/run-events! env :event/trigger)
  (t/in? env :expected-state)       ;; => boolean
  (t/data env)                      ;; => current session data
  (t/ran? env some-fn-ref)          ;; => true if expression was executed
  (t/ran-in-order? env [fn1 fn2])   ;; => true if executed in order
  (t/will-send env :event 5000)     ;; assert delayed event was queued
  (t/sent? env {:event :foo})       ;; check event queue saw a send
  (t/cancelled? env :session :id))  ;; check a send was cancelled
```

#### Jumping to Specific States

```clojure
;; Skip to a deep state to test specific behavior without replaying history
(t/goto-configuration! env
  [[:key value]]     ;; data-model operations to set up context
  #{:target-state})  ;; set of leaf states to activate
```

#### Form Statechart Unit Test Example

```clojure
(ns com.fulcrologic.rad.form-statechart-test
  (:require
    [com.fulcrologic.statecharts.testing :as t]
    [com.fulcrologic.rad.form-statechart :as fsc]
    [fulcro-spec.core :refer [=> assertions specification component]]))

(specification "Form statechart transitions"
  (component "Create flow"
    (let [env (t/new-testing-env {:statechart fsc/form-chart}
               {fsc/valid? true})]
      (t/start! env)
      (assertions
        "Starts in creating state"
        (t/in? env :state/creating) => true)

      ;; :state/creating has an eventless transition to :state/editing
      ;; (fires automatically after on-entry completes, no event needed)
      (assertions
        "Transitions to editing after creation (eventless transition)"
        (t/in? env :state/editing) => true)

      (t/run-events! env :event/save)
      (assertions
        "Transitions to saving when valid"
        (t/in? env :state/saving) => true)))

  (component "Validation failure"
    (let [env (t/new-testing-env {:statechart fsc/form-chart}
               {fsc/valid? false})]
      (t/start! env)
      (t/goto-configuration! env [] #{:state/editing})
      (t/run-events! env :event/save)
      (assertions
        "Stays in editing when validation fails"
        (t/in? env :state/editing) => true)))

  (component "Cancel from clean state"
    (let [env (t/new-testing-env {:statechart fsc/form-chart}
               {fsc/dirty? false})]
      (t/start! env)
      (t/goto-configuration! env [] #{:state/editing})
      (t/run-events! env :event/cancel)
      (assertions
        "Exits immediately when not dirty"
        (t/in? env :state/exited) => true))))
```

#### Report Statechart Unit Test Example

```clojure
(specification "Report statechart transitions"
  (component "Load flow"
    (let [env (t/new-testing-env {:statechart rsc/report-chart}
               {rsc/load-report! (fn [e d] nil)})]
      (t/start! env)
      (assertions
        "Starts by loading"
        (t/in? env :state/loading) => true)

      (t/run-events! env :event/loaded)
      (assertions
        "Transitions to ready after load"
        (t/in? env :state/ready) => true)))

  (component "Sort and filter"
    (let [env (t/new-testing-env {:statechart rsc/report-chart}
               {} {:run-unmocked? true})]
      (t/start! env)
      (t/goto-configuration! env [] #{:state/ready})
      (t/run-events! env {:name :event/sort :data {:column :account/name}})
      (assertions
        "Remains in showing after sort"
        (t/in? env :state/ready) => true))))
```

### Mock Load/Save Patterns for Headless Tests

When form or report statecharts trigger `fops/load` or `fops/invoke-remote`, headless tests need to intercept these. Two approaches:

**Approach A: Mock expressions in Tier 1 tests**

```clojure
;; Pass mock functions to new-testing-env
(let [env (t/new-testing-env {:statechart fsc/form-chart}
             {fsc/load-form-expr   (fn [env data & _] nil)   ;; no-op load
              fsc/save-form-expr   (fn [env data & _] nil)})] ;; no-op save
  (t/start! env)
  ;; Test transitions without actual network calls
  ...)
```

**Approach B: Loopback remote in Tier 2 tests**

```clojure
;; Set up a Fulcro app with a loopback remote that returns canned data
(defn test-app-with-loopback []
  (let [a (app/fulcro-app {:remotes {:remote (loopback-remote
                                               {:resolvers [person-resolver]
                                                :mutations [save-form-mutation]})}})]
    (app/set-root! a RootComp {:initialize-state? true})
    (scf/install-fulcro-statecharts! a {:event-loop? false})
    a))
```

This allows `fops/load` and `fops/invoke-remote` to execute against real resolvers/mutations in-process, without network.

### Tier 2: Fulcro Headless Component Tests

Use a real Fulcro app with synchronous event loop (`:event-loop? false`), but no server. Tests verify that statechart + Fulcro state integration works correctly. Use loopback remotes for mock server responses when needed.

#### Test App Setup

```clojure
(defn test-app
  "Creates a headless Fulcro app with synchronous statechart processing."
  []
  (let [a (app/fulcro-app)]
    (app/set-root! a RootComp {:initialize-state? true})
    (scf/install-fulcro-statecharts! a {:event-loop? false})
    a))
```

#### Form Component Test Example

```clojure
(specification "Form lifecycle in Fulcro"
  (component "Edit an existing entity"
    (let [app       (test-app)
          person-id (random-uuid)]
      ;; Pre-populate state
      (swap! (::app/state-atom app) assoc-in [:person/id person-id]
        {:person/id person-id :person/name "Alice"})

      ;; Register and start form statechart
      (scf/register-statechart! app ::person-form person-form-chart)
      (scf/start! app {:machine    ::person-form
                        :session-id ::edit-session
                        :data       {:fulcro/actors {:actor/form (scf/actor PersonForm [:person/id person-id])}}})
      (scf/process-events! app)

      (assertions
        "Form enters editing state"
        (contains? (scf/current-configuration app ::edit-session) :state/editing) => true
        "Actor data is accessible"
        (get-in (app/current-state app) [:person/id person-id :person/name]) => "Alice"))))
```

#### Report Component Test Example

```clojure
(specification "Report lifecycle in Fulcro"
  (component "Load and display rows"
    (let [app (test-app)]
      (scf/register-statechart! app ::account-report report-chart)
      (scf/start! app {:machine    ::account-report
                        :session-id ::report-session
                        :data       {:fulcro/actors {:actor/report (scf/actor AccountReport)}}})
      (scf/process-events! app)

      (assertions
        "Report enters loading state"
        (contains? (scf/current-configuration app ::report-session) :state/loading) => true))))
```

### Tier 3: Routing Tests with SimulatedURLHistory

The statecharts routing system is tested headlessly using `SimulatedURLHistory`, which replaces browser history with an atom-backed stack. This pattern is already proven in the statecharts library itself (see `url_sync_headless_spec.cljc`).

#### Routing Test Setup

```clojure
(require '[com.fulcrologic.statecharts.integration.fulcro.routing :as sroute]
         '[com.fulcrologic.statecharts.integration.fulcro.routing.simulated-history :as rsh]
         '[com.fulcrologic.statecharts.integration.fulcro.routing.url-history :as ruh])

(defn test-app []
  (let [a (app/fulcro-app)]
    (app/set-root! a RootComp {:initialize-state? true})
    (scf/install-fulcro-statecharts! a {:event-loop? false})
    a))

(defn route-to-and-process! [app target]
  (sroute/route-to! app target)
  (scf/process-events! app))

(defn settle!
  "Process events until stable (max N rounds)."
  [app]
  (dotimes [_ 10] (scf/process-events! app)))
```

#### Routing Test Example

```clojure
(specification "RAD routing with URL sync"
  (component "Navigation pushes to history"
    (let [app      (test-app)
          provider (rsh/simulated-url-history "/")]
      (sroute/start! app my-routing-chart)
      (scf/process-events! app)
      (let [cleanup (sroute/install-url-sync! app {:provider provider})]
        (sroute/url-sync-on-save sroute/session-id nil app)

        (route-to-and-process! app `PageB)
        (sroute/url-sync-on-save sroute/session-id nil app)

        (assertions
          "URL reflects navigation"
          (ruh/current-href provider) => "/PageB"
          "History stack grew"
          (count (rsh/history-stack provider)) => 2)
        (cleanup))))

  (component "Browser back/forward" :clj-only
    (let [app      (test-app)
          provider (rsh/simulated-url-history "/")]
      (sroute/start! app my-routing-chart)
      (scf/process-events! app)
      (let [cleanup (sroute/install-url-sync! app {:provider provider})]
        (sroute/url-sync-on-save sroute/session-id nil app)

        (route-to-and-process! app `PageB)
        (sroute/url-sync-on-save sroute/session-id nil app)

        ;; Simulate browser back
        (ruh/go-back! provider)
        (scf/process-events! app)

        (assertions
          "Navigated back to initial page"
          (ruh/current-href provider) => "/PageA")
        (cleanup))))

  (component "Route guard (busy check)"
    (let [app      (test-app)
          provider (rsh/simulated-url-history "/")]
      ;; Use a chart with a busy page
      (sroute/start! app busy-routing-chart)
      (scf/process-events! app)
      (let [cleanup (sroute/install-url-sync! app {:provider provider})]
        (sroute/url-sync-on-save sroute/session-id nil app)

        (route-to-and-process! app `BusyPage)
        (sroute/url-sync-on-save sroute/session-id nil app)

        ;; Try to leave -- should be denied
        (ruh/go-back! provider)
        (scf/process-events! app)

        (assertions
          "Route denied when page is busy"
          (sroute/route-denied? app) => true)
        (cleanup)))))
```

#### SimulatedURLHistory Inspection

```clojure
;; Inspect the full history stack
(rsh/history-stack provider)    ;; => ["/PageA" "/PageB" "/PageC"]
(rsh/history-cursor provider)   ;; => 2 (current position)
(rsh/history-entries provider)  ;; => [{:url "/PageA" :index 0} ...]
(ruh/current-href provider)     ;; => "/PageC"
```

### Tier 4: Full-Stack Integration Tests (CLJ-only)

For tests that require a real server (database, resolvers, mutations), use the headless client pattern from `com.fulcrologic.fulcro.headless`.

#### Server Fixture

```clojure
(ns com.fulcrologic.rad.integration-test
  (:require
    [mount.core :as mount]
    [com.fulcrologic.fulcro.headless :as h]
    [com.fulcrologic.fulcro.headless.hiccup :as hic]))

(def ^:dynamic *test-port* nil)

(defn with-test-system
  [{:keys [port] :or {port 3100}}]
  (fn [tests]
    (mount/start-with-args {:config    "config/test.edn"
                            :overrides {:org.httpkit.server/config {:port port}}})
    (try
      (binding [*test-port* port]
        (tests))
      (finally
        (mount/stop)))))
```

#### Integration Test Example

```clojure
(use-fixtures :once (with-test-system {:port 9845}))

(specification "Form save round-trip"
  (let [app (client/init *test-port*)]
    (h/render-frame! app)

    ;; Navigate to create form
    (h/click-on-text! app "New Account")
    (h/render-frame! app)

    ;; Fill in fields
    (h/type-into-labeled! app "Name" "Test User")
    (h/type-into-labeled! app "Email" "test@example.com")
    (h/render-frame! app)

    ;; Save
    (h/click-on-text! app "Save")
    (h/render-frame! app)

    (assertions
      "Shows success state after save"
      (some? (hic/find-nth-by-text (h/hiccup-frame app) "Saved" 0)) => true)))
```

### Container Tests

Containers compose forms and reports. Test them by verifying the statechart correctly coordinates child sessions.

```clojure
(specification "Container statechart"
  (component "Manages child form and report sessions"
    (let [app (test-app)]
      (scf/register-statechart! app ::container container-chart)
      (scf/start! app {:machine    ::container
                        :session-id ::container-session
                        :data       {:fulcro/actors
                                     {:actor/container (scf/actor PersonContainer)}}})
      (settle! app)

      (assertions
        "Container enters its initial state"
        (contains? (scf/current-configuration app ::container-session) :state/ready) => true)

      ;; Send event to open form
      (scf/send! app ::container-session :event/edit {:person/id (random-uuid)})
      (settle! app)

      (assertions
        "Container transitions to editing state"
        (contains? (scf/current-configuration app ::container-session) :state/editing) => true))))
```

### Testing Conventions

1. **File naming**: `*_test.cljc` for component/routing tests, `*_spec.cljc` for pure statechart specs
2. **Namespace pattern**: `com.fulcrologic.rad.<module>-test` or `com.fulcrologic.rad.<module>-spec`
3. **Use fulcro-spec**: `specification`, `component`, `assertions`, `=>` for all assertions
4. **CLJ-only sections**: Wrap browser-back/forward tests in `#?(:clj ...)` -- these require synchronous event processing
5. **No `Thread/sleep`**: All tests should use synchronous event processing (`event-loop? false` + `process-events!`)
6. **settle! helper**: Use `(dotimes [_ 10] (scf/process-events! app))` for cross-chart invocations that need multiple rounds

### Test Directory Structure

```
src/test/com/fulcrologic/rad/
  form_statechart_spec.cljc      ;; Tier 1: pure statechart
  form_statechart_test.cljc      ;; Tier 2: Fulcro headless component
  report_statechart_spec.cljc    ;; Tier 1: pure statechart
  report_statechart_test.cljc    ;; Tier 2: Fulcro headless component
  container_test.cljc            ;; Tier 2: container coordination
  routing_test.cljc              ;; Tier 3: routing with SimulatedURLHistory
  integration_test.clj           ;; Tier 4: full-stack (CLJ-only)
```

## Open Questions

- Should we provide a `com.fulcrologic.rad.testing` namespace with RAD-specific test helpers (e.g., `test-form-app`, `test-report-app`) that set up the boilerplate?
- How should mock remote responses be structured for form save/load tests? Loopback remotes or mock execution model?
- For integration tests, what server framework should the example assume? Mount is shown here but the pattern should be framework-agnostic.
- Should there be a shared `test-fixtures` namespace for common setup patterns (test-app, settle!, route-to-and-process!)?
- The existing `form_spec.cljc` and `report_test.cljc` test UISM-based code. Should these be preserved as regression tests during the migration, or replaced entirely?
- How do we test form `derive-fields` and `on-change` triggers in the statechart model? These currently depend on UISM env threading.

## Verification

1. [ ] Pure statechart tests pass for form chart (create, edit, save, cancel, validation, dirty)
2. [ ] Pure statechart tests pass for report chart (load, sort, filter, paginate)
3. [ ] Fulcro headless form tests pass (actor resolution, state transitions, save round-trip)
4. [ ] Fulcro headless report tests pass (load, row rendering, controls)
5. [ ] Routing tests pass with SimulatedURLHistory (navigate, back/forward, guards, URL sync)
6. [ ] Container tests pass (child session coordination)
7. [ ] All tests are CLJC (except integration tests which are CLJ-only)
8. [ ] All tests run via Kaocha in a REPL
9. [ ] No tests use Thread/sleep or browser-specific APIs
10. [ ] Test patterns are documented well enough for downstream projects to follow

## Revision History

- **R1**: Initial spec
- **R2**: Applied critique-round-1 fixes:
  - Fixed state names to match actual statechart definitions (`:state/abandoned` -> `:state/exited`, `:state/showing` -> `:state/ready`)
  - Fixed form test: removed non-existent `:event/created` (`:state/creating` uses eventless transition to `:state/editing`)
  - Added mock load/save patterns section (Tier 1 mocked expressions, Tier 2 loopback remotes)
  - Fixed container test actor names (`:actor/container`, not `:actor/form`/`:actor/report`)
