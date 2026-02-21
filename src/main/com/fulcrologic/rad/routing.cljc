(ns com.fulcrologic.rad.routing
  "RAD routing delegation layer. Routes through the statecharts routing system.

   This namespace provides backward-compatible routing functions for RAD applications
   that delegate to `com.fulcrologic.statecharts.integration.fulcro.routing`. The old
   RAD routing layer (RADRouter protocol, RouteHistory, HTML5History) has been replaced
   entirely by statecharts routing.

   Functions removed (no longer applicable):
   - `install-routing!` — replaced by `scr/start!`
   - `absolute-path` — tied to Dynamic Router
   - `can-change-route?` — replaced by `busy?` guard on `routes`
   - `update-route-params!` — params flow through statechart events"
  (:require
   [com.fulcrologic.statecharts.integration.fulcro.routing :as scr]))

(defn route-to!
  "Route to a target. Accepts both old RAD patterns and new statecharts patterns.

   Old RAD pattern:  `(route-to! app {:target FormClass :route-params {...}})`
   New pattern:      `(route-to! app-ish target)` or `(route-to! app-ish target data)`

   The `target` should be a component class or registry keyword. The `data` map is
   passed as event data to the routing statechart."
  ([app-ish target-or-options]
   (if (map? target-or-options)
     (let [{:keys [target route-params]} target-or-options]
       (scr/route-to! app-ish target (or route-params {})))
     (scr/route-to! app-ish target-or-options)))
  ([app-ish target data]
   (scr/route-to! app-ish target data)))

(defn back!
  "Navigate back through browser history via the installed URL sync provider."
  [app-ish]
  (scr/route-back! app-ish))

(defn route-forward!
  "Navigate forward through browser history via the installed URL sync provider."
  [app-ish]
  (scr/route-forward! app-ish))

(defn force-continue-routing!
  "Force the most-recently-denied route change to proceed, overriding the busy guard."
  [app-ish]
  (scr/force-continue-routing! app-ish))

(defn abandon-route-change!
  "Abandon the most-recently-denied route change and close the routing info."
  [app-ish]
  (scr/abandon-route-change! app-ish))

(defn route-denied?
  "Returns true if the routing statechart is currently showing a route-denied state
   (i.e., a route change was blocked by a busy guard)."
  [app-ish]
  (scr/route-denied? app-ish))
