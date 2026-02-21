(ns com.example.system
  "System statechart and bootstrap for the demo application.
   Installs headless renderers, statecharts, and starts routing."
  (:require
   [com.example.ui.ui :refer [routing-chart]]
   [com.fulcrologic.rad.application :as rad-app]
   [com.fulcrologic.rad.rendering.headless.plugin]
   [com.fulcrologic.rad.type-support.date-time :as dt]
   [taoensso.timbre :as log]))

(defn setup-RAD
  "Installs headless renderers. No `install-ui-controls!` needed — the headless
   plugin registers via multimethods when required."
  [app]
  ;; Requiring the headless plugin namespace is sufficient — its defmethod forms
  ;; register the renderers globally. Nothing else to do here.
  (log/info "RAD headless renderers installed"))

(defn start!
  "Starts the demo system. Installs statecharts, starts routing, and marks the
   app as ready.

   Options:
   * `:event-loop?` — `true` (browser default), `:immediate` (CLJ tests). Default `true`."
  ([app] (start! app {}))
  ([app {:keys [event-loop?] :or {event-loop? true}}]
   (dt/set-timezone! "America/Los_Angeles")
   (setup-RAD app)
   (rad-app/install-statecharts! app {:event-loop? event-loop?})
   (let [result (rad-app/start-routing! app routing-chart)]
     #?(:clj (when result (deref result)))
     #?(:cljs (rad-app/install-url-sync! app))
     result)))
