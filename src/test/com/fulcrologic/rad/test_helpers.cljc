(ns com.fulcrologic.rad.test-helpers
  "Test utilities for creating headless RAD applications with statecharts routing.

   Provides helpers for initializing a fully-functional test app with synchronous
   event processing, optional URL sync via simulated history, and a settle helper
   for ensuring all events have been processed."
  (:require
   [com.fulcrologic.fulcro.application :as app]
   [com.fulcrologic.statecharts.integration.fulcro :as scf]
   [com.fulcrologic.statecharts.integration.fulcro.routing :as scr]
   [com.fulcrologic.statecharts.integration.fulcro.routing.simulated-history :as sim]
   [com.fulcrologic.rad.statechart.application :as rad-app]))

(defn create-test-app
  "Creates a fully initialized headless test app with routing. Returns the app.

   Uses `:immediate` event processing so all statechart transitions are synchronous.
   No URL sync is installed.

   `routing-chart` is the statechart definition for routing.

   `opts` is an optional map:

   * `:root` — Root component class. If provided, `app/set-root!` is called with
     `:initialize-state? true`.
   * `:controls` — UI controls map to install via `install-ui-controls!`.
   * `:extra-env` — Map merged into every expression's `env`."
  [routing-chart & [{:keys [root controls extra-env]}]]
  (let [test-app (rad-app/fulcro-rad-app {})]
    (when root
      (app/set-root! test-app root {:initialize-state? true}))
    (when controls
      (rad-app/install-ui-controls! test-app controls))
    (rad-app/install-statecharts! test-app
                                  {:event-loop? :immediate
                                   :extra-env   (or extra-env {})})
    (rad-app/start-routing! test-app routing-chart)
    test-app))

(defn create-test-app-with-url-sync
  "Like `create-test-app` but also installs URL sync with a simulated history provider.
   Returns `{:keys [app provider]}` so tests can inspect URL state.

   The `provider` supports `sim/history-stack`, `sim/history-cursor`, and
   `sim/history-entries` for inspecting URL history."
  [routing-chart & [opts]]
  (let [test-app (create-test-app routing-chart opts)
        provider (sim/simulated-url-history)]
    (rad-app/install-url-sync! test-app {:provider provider})
    {:app test-app :provider provider}))

(defn settle!
  "Processes pending statechart events until the system stabilizes.

   With `:immediate` event processing (the default for `create-test-app`), events
   are processed synchronously during `send!`, so this is typically a no-op.
   Provided for use with `:event-loop? false` configurations where manual
   processing is needed.

   `app` is the Fulcro application.
   `max-iterations` limits processing loops (default 100) to prevent infinite loops."
  [app & [{:keys [max-iterations] :or {max-iterations 100}}]]
  (dotimes [_ max-iterations]
    (scf/process-events! app)))
