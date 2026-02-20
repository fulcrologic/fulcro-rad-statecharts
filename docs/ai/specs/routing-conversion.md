# Spec: Routing Conversion - Replace RAD Routing with Statecharts Routing

**Status**: active
**Priority**: P0
**Created**: 2026-02-20
**Owner**: spec-writer-4
**Depends-on**: project-setup, session-id-convention, macro-rewrites, app-initialization

## Context

RAD's routing layer (`com.fulcrologic.rad.routing`, `routing.base`, `routing.history`, `routing.html5_history`) wraps Fulcro Dynamic Router with additional features: a pluggable `RADRouter` protocol, application-level history management, and route parameter tracking via URL query strings.

The statecharts library already provides a complete replacement: `com.fulcrologic.statecharts.integration.fulcro.routing` with `rstate`, `istate`, `routes`, URL sync via `install-url-sync!`, busy checking, and cross-chart routing. **This is a complete replacement, not a wrapper.**

This spec is P0 because routing is fundamental -- forms, reports, containers, and authorization all depend on routing to function.

**Important**: The statecharts routing library (`routing.cljc`) already provides `rstate`, `istate`, `routes`, `routing-regions`, `busy?`, `busy-form-handler`, `initialize-route!`, `update-parent-query!`, `establish-route-params-node`, and `deep-busy?`. The RAD routing layer should be THIN -- mostly option-to-option mapping and default behaviors. This spec focuses on what RAD needs to ADD on top of existing routing infrastructure, not on re-describing what the routing library already does.

## Current RAD Routing Architecture

### Key Components

1. **`routing/base.cljc`** - `RADRouter` protocol with `-route-to!` method
2. **`routing.cljc`** - `RADDynamicRouter` implementation (default), `route-to!`, `back!`, `install-routing!`, `update-route-params!`, `can-change-route?`, `absolute-path`
3. **`routing/history.cljc`** - `RouteHistory` protocol: push/replace/back/undo, listeners, current-route query
4. **`routing/html5_history.cljc`** - `HTML5History` record implementing `RouteHistory`, URL encoding/decoding, popstate handling, `restore-route!`

### How RAD Routing Works Today

```
User action (e.g. form/edit!)
  -> rad-routing/route-to!
    -> RADDynamicRouter/-route-to!
      -> dr/route-to! (Fulcro Dynamic Router)
        -> before-change callback
          -> history/push-route! or history/replace-route!
            -> HTML5History updates browser URL
```

For browser back/forward:
```
Browser popstate event
  -> HTML5History listener
    -> route-predicate (dr/can-change-route?)
      -> dr/change-route! (Fulcro Dynamic Router)
      OR -> history/undo! (if route denied)
```

### Route History Protocol

```clojure
(defprotocol RouteHistory
  (-push-route! [history route params])
  (-replace-route! [history route params])
  (-back! [history])
  (-undo! [history new-route params])
  (-add-route-listener! [history listener-key f])
  (-remove-route-listener! [history listener-key])
  (-current-route [history]))
```

### HTML5History Features

- Hash-based or path-based routing
- Transit+base64 encoding of route params in `_rp_` query parameter
- URL prefix support (context root)
- Custom `route->url` and `url->route` functions
- Default route fallback when no history
- Pop state event handling with UID tracking for direction detection
- `restore-route!` for initial URL restoration

## Statecharts Routing (The Replacement)

The statecharts library at `com.fulcrologic.statecharts.integration.fulcro.routing` provides:

### Core Concepts

| Statecharts Concept | Replaces RAD Concept |
|---|---|
| `rstate` | `dr/defrouter` route target + `form-will-enter` |
| `istate` | Route target with co-located statechart (form/report with own lifecycle) |
| `routes` | Router container with auto-generated transitions |
| `routing-regions` | Parallel state wrapping routes + routing-info region |
| `install-url-sync!` | `install-route-history!` + HTML5History |
| `busy?` guard | `dr/can-change-route?` / `form-will-leave` |
| `route-to!` | `rad-routing/route-to!` |
| `route-back!` / `route-forward!` | `history/back!` |

### How Statecharts Routing Works

```
User action (e.g. route-to!)
  -> scf/send! to routing session with route-to.* event
    -> routes state checks busy? guard
      -> if busy: record-failed-route!, raise :event.routing-info/show
      -> if not busy: transition to target rstate/istate
        -> on-entry: initialize-route!, update-parent-query!
        -> URL sync on-save handler: push/replace URL
```

For browser back/forward:
```
Browser popstate event
  -> URLHistoryProvider listener
    -> install-url-sync! popstate handler
      -> resolve-route-and-navigate!
        -> route-to! (sends event to statechart)
          -> busy? guard check
            -> accept: URL stays, settled-index updates
            -> deny: undo via go-back!/go-forward!
```

### Key API from Statecharts Routing

```clojure
;; Setup
(start! app statechart)                    ;; Register and start routing session
(install-url-sync! app {:provider ...})    ;; Bidirectional URL sync

;; Navigation
(route-to! app-ish target data)            ;; Route to a target
(route-back! app-ish)                      ;; Browser back
(route-forward! app-ish)                   ;; Browser forward
(force-continue-routing! app-ish)          ;; Override busy guard
(abandon-route-change! app-ish)            ;; Cancel pending route change

;; Query
(active-leaf-routes app-ish)               ;; Current leaf routes (cross-chart)
(route-denied? app-ish)                    ;; Is routing blocked?
(route-current-url app-ish)                ;; Current URL from provider

;; Rendering
(ui-current-subroute this factory-fn)      ;; Render current child route
(ui-parallel-route this key factory-fn)    ;; Render parallel route child

;; Component self-routing
(send-to-self! this event data)            ;; Send event to co-located chart
(current-invocation-configuration this)    ;; Query co-located chart state
```

## Conversion Plan

### 1. Replace `routing/base.cljc` - DELETE

The `RADRouter` protocol is no longer needed. Statecharts routing is the only routing system.

### 2. Replace `routing/history.cljc` - DELETE

The `RouteHistory` protocol is replaced by `URLHistoryProvider` from `com.fulcrologic.statecharts.integration.fulcro.routing.url_history`.

### 3. Replace `routing/html5_history.cljc` - DELETE

`HTML5History` is replaced by `BrowserURLHistory` from `com.fulcrologic.statecharts.integration.fulcro.routing.browser_history`. Key differences:

| HTML5History | BrowserURLHistory |
|---|---|
| Transit+base64 in `_rp_` query param | Configurable URLCodec (default: transit+base64) |
| Manual UID tracking for direction | Index-based direction detection |
| `route->url` / `url->route` customizable | URLCodec protocol for encoding/decoding |
| `restore-route!` for initial URL | `install-url-sync!` does initial URL restoration |
| `apply-route!` manual routing | Automatic via `resolve-route-and-navigate!` |

### 4. Rewrite `routing.cljc` - MAJOR REWRITE

The main routing namespace becomes a thin delegation layer to `com.fulcrologic.statecharts.integration.fulcro.routing`:

```clojure
(ns com.fulcrologic.rad.routing
  (:require
    [com.fulcrologic.statecharts.integration.fulcro.routing :as scr]))

;; Delegate to statecharts routing
(def route-to! scr/route-to!)
(def back! scr/route-back!)

;; Remove these (no longer applicable):
;; - install-routing! (no pluggable router protocol)
;; - absolute-path (tied to Dynamic Router)
;; - can-change-route? (replaced by busy? guard)
;; - update-route-params! (params flow through statechart events)

;; New additions from statecharts routing:
(def route-forward! scr/route-forward!)
(def force-continue-routing! scr/force-continue-routing!)
(def abandon-route-change! scr/abandon-route-change!)
(def route-denied? scr/route-denied?)
```

### 5. Form Routing Integration

Currently forms use:
- `form-will-enter` - Creates a `dr/route-deferred`, calls `start-form!`, then `dr/target-ready`
- `form-will-leave` - Checks dirty state, returns boolean or `:deferred`
- `form-allow-route-change` - Delegates to `dr/can-change-route?`

With statecharts:
- Forms become `istate` targets with co-located statecharts
- `form-will-enter` logic moves to `istate`'s `on-entry` (initialize route, set actor)
- `form-will-leave` / dirty checking becomes `sfro/busy?` on the form component
- The form's own statechart (currently form-machine UISM) becomes a co-located statechart referenced via `sfro/statechart`

```clojure
;; Example form route definition in the app's routing chart:
(istate {:route/target :my.app/AccountForm
         :route/segment "account"
         :route/params #{:id :action}})

;; The form component declares its co-located chart:
(defsc AccountForm [this props]
  {sfro/statechart account-form-chart   ;; the converted form-machine
   sfro/busy? (fn [env data]
                (let [{:actor/keys [component]} (scf/resolve-actors env :actor/component)]
                  (fs/dirty? component)))
   ...})
```

### 6. Report Routing Integration

Currently reports use:
- `report-will-enter` - Creates `dr/route-deferred`, calls `start-report!`, then `dr/target-ready`

With statecharts:
- Reports become either `rstate` (simple) or `istate` (with co-located report statechart) targets
- `report-will-enter` logic moves to `rstate`/`istate` on-entry
- Reports that use `ro/run-on-mount?` will have their chart auto-started via `istate`

### 7. Container Routing Integration

Currently containers use:
- `container-will-enter` - Creates `dr/route-deferred`, calls `start-container!`, then `dr/target-ready`

With statecharts:
- Containers become `istate` targets
- Child report coordination handled by container's co-located statechart

### 8. Route Parameter Flow

Current: Route params arrive via `dr/route-to!` and are stored in dynamic router component props. Forms read `:action` and `:id` from route params. Reports read filter params from route params.

Statecharts: Route params flow through event data when `route-to!` is called. `rstate`/`istate` use `establish-route-params-node` to store params at `[:routing/parameters state-id]` in session data. Forms/reports access params from their statechart's data model.

### 9. Busy Guard Integration

Current: Forms check `fs/dirty?` in `form-will-leave`. Dynamic Router's `can-change-route?` walks the router tree.

Statecharts: The `busy?` function on `routes` is the sole guard. It uses `deep-busy?` to walk the entire invocation tree, checking `sfro/busy?` on each active route's component. For forms, the default `busy-form-handler` checks `fs/dirty?` automatically. Custom busy logic can be provided via `sfro/busy?` component option.

### 10. History Integration for `update-route-params!`

Current: `update-route-params!` reads current route from history, applies an update function, and replaces the route.

Statecharts: Route params are part of statechart session data. `establish-route-params-node` stores params from event data into `[:routing/parameters <state-id>]`, and the URL codec reads from there. So parameters CAN be in the URL, but they flow through the route event data, not through a separate update mechanism.

**How report parameters get into URLs**: When a report stores sort/filter/page state, these values are passed as event data to `route-to!`. The routing chart's `establish-route-params-node` on the report's `istate` stores them at `[:routing/parameters <state-id>]`. The URL codec encodes this into the URL. When navigating back, the URL codec decodes the params and passes them as event data, which the report's statechart uses to restore state.

This function is removed; callers should use `scf/send!` to update their own statechart data (which triggers URL sync automatically), or store transient params in Fulcro state directly.

### 11. Adapter for Old `route-to!` Call Patterns

The RAD `route-to!` currently has two arities:
- `[app options-map]` where options has `:target`, `:route-params`, etc.
- `[app-or-component RouteTarget route-params]`

The statecharts `scr/route-to!` takes `[app-ish target]` or `[app-ish target data]`.

RAD's `routing.cljc` should provide a compatibility adapter:

```clojure
(defn route-to!
  "Route to a target. Accepts both old RAD patterns and new statecharts patterns."
  ([app-ish target-or-options]
   (if (map? target-or-options)
     ;; Old RAD pattern: (route-to! app {:target FormClass :route-params {...}})
     (let [{:keys [target route-params]} target-or-options]
       (scr/route-to! app-ish target (or route-params {})))
     ;; New pattern: (route-to! app-ish target)
     (scr/route-to! app-ish target-or-options)))
  ([app-ish target data]
   (scr/route-to! app-ish target data)))
```

### 12. App Initialization Sequence

See `app-initialization.md` for the complete bootstrap sequence that replaces `install-routing!` + `install-route-history!`. Summary:

1. `(rad-app/install-ui-controls! app all-controls)` -- unchanged
2. `(scf/install-fulcro-statecharts! app {:on-save ...})` -- replaces all UISM infrastructure
3. `(scr/start! app routing-chart)` -- replaces `install-routing!`
4. `(scr/install-url-sync! app)` -- replaces `install-route-history!`
5. `(app/mount! app Root "app")` -- unchanged

### 13. Routing Chart is User-Defined

The routing chart is NOT auto-generated from form/report definitions. Users must manually define their routing chart using `rstate`/`istate`. RAD provides helpers (`busy-form-handler`, etc.) but the chart structure is application-specific.

```clojure
;; User defines their routing chart
(def my-routing-chart
  (statechart {:initial :state/route-root}
    (scr/routing-regions
      (scr/routes {:id :region/main :routing/root :my.app/Root}
        (scr/rstate {:route/target :my.app/Dashboard})
        (scr/istate {:route/target :my.app/AccountForm
                     :route/segment "account"
                     :route/params #{:id :action}})
        (scr/istate {:route/target :my.app/AccountReport
                     :route/segment "accounts"})))))
```

How `fo/route-prefix` maps to the routing chart: the RAD form option `fo/route-prefix` (e.g., `"account"`) corresponds to `:route/segment` on `istate`. The route segment is specified in the routing chart, not on the component.

## Files Changed

| File | Action | Notes |
|---|---|---|
| `routing/base.cljc` | DELETE | `RADRouter` protocol no longer needed |
| `routing/history.cljc` | DELETE | Replaced by statecharts `url_history.cljc` |
| `routing/html5_history.cljc` | DELETE | Replaced by statecharts `browser_history.cljc` |
| `routing.cljc` | REWRITE | Thin delegation to statecharts routing |
| `form.cljc` | MODIFY | Remove `form-will-enter`, `form-will-leave`, `form-allow-route-change`; add `sfro/busy?` |
| `report.cljc` | MODIFY | Remove `report-will-enter`; forms/reports become `rstate`/`istate` targets |
| `container.cljc` | MODIFY | Remove `container-will-enter`; containers become `istate` targets |

## Migration Guide for Downstream Users

### Before (RAD Dynamic Router)

```clojure
;; App setup
(history/install-route-history! app (html5/html5-history))

;; Routing
(rad-routing/route-to! this AccountForm {:id account-id})
(rad-routing/back! this)

;; Form definition
(defsc-form AccountForm [this props]
  {fo/route-prefix "account"
   fo/id account/id
   ...})

;; In app root
(dr/defrouter MainRouter [this props]
  {:router-targets [AccountForm AccountReport Dashboard]})
```

### After (Statecharts Routing)

```clojure
;; App setup
(scr/start! app my-routing-chart)
(scr/install-url-sync! app)

;; Routing (same API!)
(rad-routing/route-to! this :my.app/AccountForm {:id account-id})
(rad-routing/back! this)

;; Form definition (chart co-located on component)
(defsc AccountForm [this props]
  {sfro/statechart  account-form-chart
   sfro/busy?       (busy-form-handler AccountForm)
   fo/id            account/id
   ...})

;; In app, define routing chart
(def app-routing-chart
  (statechart {:initial :state/route-root}
    (routing-regions
      (routes {:id :region/main :routing/root :my.app/MainLayout}
        (rstate {:route/target :my.app/Dashboard})
        (istate {:route/target :my.app/AccountForm
                 :route/params #{:id :action}})
        (istate {:route/target :my.app/AccountReport})))))
```

## Testing Strategy

1. Verify `route-to!` sends correct events to routing statechart session
2. Verify `back!` delegates to `route-back!`
3. Verify form busy guard blocks routing when form is dirty
4. Verify URL sync pushes/replaces on programmatic navigation
5. Verify URL sync handles browser back/forward with busy guard denial
6. Verify route params flow from event data to form/report initialization
7. Verify cross-chart routing via `istate` `:route/reachable`

## Open Questions

1. **DECIDED: Macros generate `sfro/statechart` and `sfro/busy?`.** The `defsc-form` / `defsc-report` macros no longer generate `form-will-enter` / `report-will-enter` / `:route-segment`. They instead generate `sfro/statechart`, `sfro/busy?`, and `sfro/initialize` component options. See `macro-rewrites.md`.

2. **DECIDED: Route segments live only on `istate` in the routing chart.** The `:route/segment` is specified in the user-defined routing chart, not on the component. `fo/route-prefix` is no longer used for route-segment generation in macros.

3. **`update-route-params!` replacement**: Several RAD controls use `update-route-params!` to persist control values in the URL. What is the idiomatic statecharts replacement? Should controls store values in their parent chart's data model, with URL sync reflecting it?

4. **Backward compatibility period**: Should `routing.cljc` include deprecation wrappers for removed functions (`install-routing!`, `absolute-path`, `can-change-route?`) that log warnings, or just remove them?

5. **DECIDED: No `:route-segment` in macros.** `defsc-form` / `defsc-report` no longer generate `:route-segment`. Route segments live in the routing chart definition on `istate`.

## Revision History

- **R1**: Initial spec
- **R2**: Applied critique-round-1 fixes:
  - Added depends-on: session-id-convention, macro-rewrites, app-initialization
  - Reduced redundancy with existing statecharts routing library (noted that `rstate`/`istate` already handle most concerns)
  - Added adapter for old `route-to!` call patterns (section 11)
  - Explained how report parameters get into URLs via `establish-route-params-node` (section 10)
  - Referenced app-initialization.md for bootstrap sequence (section 12)
  - Clarified that routing chart is user-defined, not auto-generated (section 13)
