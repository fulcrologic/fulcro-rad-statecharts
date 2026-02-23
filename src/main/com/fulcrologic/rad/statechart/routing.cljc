(ns com.fulcrologic.rad.statechart.routing
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
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.fulcro.raw.components :as rc]
    [com.fulcrologic.rad.statechart.form :as form]
    [com.fulcrologic.rad.statechart.report :as report]
    [com.fulcrologic.statecharts.data-model.operations :as ops]
    [com.fulcrologic.statecharts.elements :refer [entry-fn exit-fn]]
    [com.fulcrologic.statecharts.integration.fulcro.routing :as scr]
    [taoensso.timbre :as log]))

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

(defn active-leaf-routes
  "Returns the set of active leaf route state IDs from the routing statechart."
  [app-ish]
  (scr/active-leaf-routes app-ish))

(defn report-route-state
  "Creates a routing state for a RAD report. On entry, starts the report via
   `report/start-report!`. Route parameters are merged from statechart session data
   and event data.

   Options are the same as `scr/rstate`, plus:

   * `:route/target` — (required) The report component class or registry key.
   * `:route/params` — (optional) Set of keywords for route parameters.
   * `:report/param-keys` — (optional) Collection of keywords to select from
     merged data/event-data as `:route-params` for the report."
  [{:route/keys  [target]
    :report/keys [param-keys] :as props}]
  (scr/rstate props
    (entry-fn [{:fulcro/keys [app]} data _event-name event-data]
      (log/debug "Starting report via routing")
      (report/start-report! app (comp/registry-key->class target)
        {:route-params (cond-> (merge data event-data)
                         (seq param-keys) (select-keys param-keys))})
      nil)))

(defn form-route-state
  "Creates a routing state for a RAD form. On entry, starts the form's statechart via
   `form/start-form!`. On exit, abandons the form via `form/abandon-form!`.

   The routing event data should include `:id` and optionally `:params`. If `:id` is a tempid,
   the form starts in create mode; otherwise it starts in edit mode.

   Options are the same as `scr/rstate`:

   * `:route/target` — (required) The form component class or registry key.
   * `:route/params` — (optional) Set of keywords for route parameters.

   See `scr/rstate` for full option details."
  [props]
  (scr/rstate props
    (entry-fn [{:fulcro/keys [app]} _data _event-name event-data]
      (log/debug "Starting form via routing" event-data)
      (let [{:keys [id params]} event-data
            form-class (comp/registry-key->class (:route/target props))]
        (form/start-form! app id form-class params))
      nil)
    (exit-fn [{:fulcro/keys [app]} {:route/keys [idents]} & _]
      (when-let [form-ident (get idents (rc/class->registry-key (:route/target props)))]
        (form/abandon-form! app form-ident)
        [(ops/delete [:route/idents form-ident])]))))

(defn edit!
  "Route to a form and start an edit on the given `id`."
  ([app-ish Form id]
   (edit! app-ish Form id {}))
  ([app-ish Form id params]
   (scr/route-to! app-ish Form {:id     id
                                :params params})))

(defn create!
  "Route to a form and start creating a new entity."
  ([app-ish Form]
   (edit! app-ish Form (tempid/tempid) {}))
  ([app-ish Form params]
   (edit! app-ish Form (tempid/tempid) params)))
