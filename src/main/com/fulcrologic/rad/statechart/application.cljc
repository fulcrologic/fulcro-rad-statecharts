(ns com.fulcrologic.rad.statechart.application
  "Statechart-specific application setup functions for Fulcro RAD.

   Contains the three functions that extend `com.fulcrologic.rad.application` with
   statechart infrastructure:

   * `install-statecharts!` — installs the statechart engine with URL sync
   * `start-routing!` — registers and starts a routing statechart
   * `install-url-sync!` — installs bidirectional URL synchronization"
  (:require
   [com.fulcrologic.statecharts.integration.fulcro :as scf]
   [com.fulcrologic.statecharts.integration.fulcro.routing :as scr]))

(defn install-statecharts!
  "Installs statechart infrastructure on the Fulcro `app` with RAD defaults.

   Wraps `scf/install-fulcro-statecharts!` with URL sync support enabled via
   `scr/url-sync-on-save`. Additional `options` are passed through and may include:

   * `:on-save` — Additional `(fn [session-id wmem])` called after URL sync on-save.
   * `:on-delete` — `(fn [session-id])` called when a session reaches a final state.
   * `:event-loop?` — `true` (default, browser), `false` (manual), or `:immediate` (CLJ tests).
   * `:extra-env` — Map merged into every expression's `env`.
   * `:async?` — If true, use promesa-based async processor."
  [app & [{:keys [on-save] :as options}]]
  (let [combined-on-save (if on-save
                           (fn [session-id wmem]
                             (scr/url-sync-on-save session-id wmem app)
                             (on-save session-id wmem))
                           (fn [session-id wmem]
                             (scr/url-sync-on-save session-id wmem app)))]
    (scf/install-fulcro-statecharts! app
                                     (assoc (or options {}) :on-save combined-on-save))))

(defn start-routing!
  "Registers and starts the routing `statechart`. Wraps `scr/start!`.

   `options` is an optional map that may include:

   * `:routing/checks` — `:warn` (default) or `:strict`."
  [app statechart & [options]]
  (scr/start! app statechart (or options {})))

(defn install-url-sync!
  "Installs bidirectional URL synchronization. Call after `start-routing!`.
   Wraps `scr/install-url-sync!`. Returns a cleanup function.

   `options` is an optional map that may include:

   * `:provider` — A `URLHistoryProvider`. Defaults to browser history on CLJS.
   * `:url-codec` — A `URLCodec` for encoding/decoding URLs.
   * `:prefix` — URL path prefix (default \"/\").
   * `:on-route-denied` — `(fn [url])` called when navigation is denied."
  [app & [options]]
  (scr/install-url-sync! app (or options {})))
