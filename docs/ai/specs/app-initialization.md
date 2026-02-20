# Spec: App Initialization / Bootstrap Sequence

**Status**: backlog
**Priority**: P0
**Created**: 2026-02-20
**Owner**: conductor
**Depends-on**: project-setup, routing-conversion, form-statechart, report-statechart, container-statechart

## Context

Applications built on fulcro-rad currently initialize routing and UI machinery through a combination of `install-routing!`, `install-route-history!`, and `rad/install!`. The statecharts-based system replaces all of these with the statecharts routing library: `scf/install-fulcro-statecharts!`, `scr/start!`, and `scr/install-url-sync!`.

This spec defines the complete bootstrap sequence for a fulcro-rad-statecharts application, covering both browser and headless (testing) modes.

### Namespace Aliases Used in This Spec

```clojure
(require
  '[com.fulcrologic.fulcro.application :as app]
  '[com.fulcrologic.statecharts.integration.fulcro :as scf]
  '[com.fulcrologic.statecharts.integration.fulcro.routing :as scr]
  '[com.fulcrologic.statecharts.integration.fulcro.routing-options :as sfro]
  '[com.fulcrologic.statecharts.integration.fulcro.routing.url-codec-transit :as ruct]
  '[com.fulcrologic.statecharts.chart :refer [statechart]]
  '[com.fulcrologic.statecharts.elements :refer [state final parallel]]
  '[com.fulcrologic.rad.application :as rad-app])
```

## Requirements

1. Define the full app bootstrap sequence that replaces current RAD routing setup
2. Document what replaces `install-routing!` and `install-route-history!`
3. Document what happens to `rad/install!` (the RAD application setup function)
4. Define how the plugin system (rendering controls) initializes
5. Define headless vs browser initialization differences
6. Provide example initialization code for both modes

## What Gets Replaced

| Current RAD Function | Replacement | Notes |
|---|---|---|
| `install-routing!` | `scr/start!` | Registers and starts the routing statechart |
| `install-route-history!` | `scr/install-url-sync!` | Bidirectional URL synchronization |
| DR `will-enter` / `will-leave` | `rstate` / `istate` on-entry/on-exit | Route lifecycle managed by chart elements |
| `route-to!` (DR-based) | `scr/route-to!` | Sends route-to event to routing statechart |
| `uism/begin!` in route entry | `istate` invoke | Statechart auto-invoked on route entry |

### What Stays the Same

- `rad-app/fulcro-rad-app` -- creates the Fulcro application (unchanged)
- `rad-app/install-ui-controls!` -- installs rendering control plugins (unchanged)
- Remote configuration -- HTTP remotes, CSRF tokens, etc. (unchanged)
- `app/mount!` -- mounts the Fulcro app to the DOM (unchanged)

## Bootstrap Sequence

### Step 1: Create the Fulcro App

No changes here. The app is still created with `rad-app/fulcro-rad-app`:

```clojure
(defonce app (rad-app/fulcro-rad-app {}))
```

### Step 2: Install Rendering Controls

No changes. Controls are installed exactly as before:

```clojure
(rad-app/install-ui-controls! app all-controls)
```

This stores the control map in the Fulcro runtime atom under `:com.fulcrologic.rad/controls`. The statecharts system does not interact with controls directly -- they are consumed by form/report renderers at render time.

### Step 3: Install Statecharts on the App

This replaces all UISM infrastructure. Call `scf/install-fulcro-statecharts!` with the app and options:

```clojure
(scf/install-fulcro-statecharts! app
  {:on-save (fn [session-id wmem]
              (scr/url-sync-on-save session-id wmem app))})
```

**Key options:**

- `:on-save` -- **Required for URL sync.** Called every time a statechart reaches a stable state and its working memory is saved. `scr/url-sync-on-save` must be called here, passing `app` as the third argument to enable child chart URL tracking. You may compose additional on-save logic (e.g., session durability):

  ```clojure
  {:on-save (fn [session-id wmem]
              (scr/url-sync-on-save session-id wmem app)
              (my-persistence/save! session-id wmem))}
  ```

- `:on-delete` -- Optional `(fn [session-id])` called when a session reaches a final state and is GC'd.

- `:event-loop?` -- Controls event processing mode:
  - `true` (default) -- Starts a core.async event loop. Use in browser.
  - `false` -- Manual processing via `scf/process-events!`. Use in headless tests.
  - `:immediate` -- Synchronous processing during `send!`. Use in CLJ tests that need immediate state transitions without manual polling.

- `:extra-env` -- A map merged into every expression's `env` argument. Use for injecting services:

  ```clojure
  {:extra-env {:my-service (create-service)}}
  ```

- `:async?` -- If `true`, uses promesa-based async processor. Enables `afop/await-load` and `afop/await-mutation`. Defaults to `false`.

**What this does internally:**

1. Creates the statechart infrastructure (data model, event queue, execution model, registry, working memory store, processor)
2. Installs all of it on the Fulcro app's runtime atom under `::sc/env`
3. Registers and starts the internal application master chart
4. The 4-arg expression calling convention is enabled (`(fn [env data event-name event-data]`)

### Step 4: Register Form/Report/Container Statecharts

Individual form and report statecharts are typically co-located on their component classes via the `sfro/statechart` component option. They are auto-registered during routing when `istate` invokes them (the `srcexpr` in `istate` calls `scf/register-statechart!` lazily).

For statecharts that need explicit registration (e.g., a custom auth chart):

```clojure
(scf/register-statechart! app ::auth-chart auth-statechart)
```

### Step 5: Define the Routing Chart

The routing chart is the central piece that replaces DR's routing tree. It defines the application's route structure using `scr/routes`, `scr/rstate`, and `scr/istate`.

```clojure
(def routing-chart
  (statechart {:initial :state/route-root}
    (scr/routing-regions
      (scr/routes {:id :region/main :routing/root :my.app/Root}
        ;; Simple route (no co-located statechart)
        (scr/rstate {:route/target :my.app/Dashboard})

        ;; Route with co-located statechart (form/report)
        (scr/istate {:route/target :my.app/AccountForm
                     :route/segment "account"
                     :route/params #{:id}})

        ;; Route with nested routes
        (scr/rstate {:route/target :my.app/AdminPanel :parallel? true}
          (scr/routes {:id :region/admin :routing/root :my.app/AdminPanel}
            (scr/istate {:route/target :my.app/UserList})
            (scr/istate {:route/target :my.app/AuditReport})))))))
```

**Key routing elements:**

- `scr/routing-regions` -- Wraps routes in a parallel state with routing info management (route-denied modal support). Returns a `:state/route-root` containing `:state/top-parallel`.

- `scr/routes` -- A state representing a routing region. Options:
  - `:id` -- State ID (e.g., `:region/main`)
  - `:routing/root` -- The component that serves as the root of this routing region. Must have a constant ident (or be the app root).

- `scr/rstate` -- A simple routing state. The `:route/target` is a component registry key. The state ID is derived from the target (do NOT pass `:id`). Handles component initialization and parent query updates on entry.

- `scr/istate` -- A routing state that invokes a co-located statechart on the target component. The component must have `sfro/statechart` or `sfro/statechart-id` set. Handles everything `rstate` does, plus starts the child statechart session.

**How `fo/route-prefix` maps to the routing chart:**

The RAD form option `fo/route-prefix` (e.g., `"account"`) maps to `:route/segment` on `rstate`/`istate`. If not specified, defaults to the simple name of the target component's registry key.

**How route parameters work:**

Route parameters are declared via `:route/params` (a set of keywords). When a route-to event carries data matching those keywords, the values are stored in the data model at `[:routing/parameters <state-id>]`. The URL codec encodes these parameters into the URL.

### Step 6: Start the Routing Chart

```clojure
(scr/start! app routing-chart)
```

This:
1. Validates the route configuration (checks for duplicate leaf names, segment collisions, reachable target collisions)
2. Registers the chart under `scr/session-id` (which is `::scr/session`)
3. Starts a session with that same well-known session ID

**Optional second argument for validation mode:**

```clojure
(scr/start! app routing-chart {:routing/checks :strict})
```

- `:warn` (default) -- Logs warnings for configuration issues
- `:strict` -- Throws on configuration issues

### Step 7: Install URL Sync (Browser Only)

```clojure
(scr/install-url-sync! app)
```

Call AFTER `scr/start!` completes. This sets up bidirectional synchronization between the routing statechart state and the browser URL.

**Options:**

```clojure
(scr/install-url-sync! app
  {:prefix "/"
   :url-codec (ruct/transit-base64-codec)
   :on-route-denied (fn [url] (log/warn "Route denied:" url))})
```

- `:provider` -- A `URLHistoryProvider`. Defaults to `(bh/browser-url-history)` on CLJS. Required on CLJ (must provide a simulated history).
- `:url-codec` -- A `URLCodec` for encoding/decoding URLs. Defaults to `(ruct/transit-base64-codec)`.
- `:prefix` -- URL path prefix (default `"/"`).
- `:on-route-denied` -- `(fn [url])` called when back/forward navigation is denied by the busy guard.

**URL sync mechanics:**

- **State-to-URL:** When the routing statechart (or any child chart) saves working memory, the `url-sync-on-save` handler (installed in step 3) computes the URL from the current configuration and pushes/replaces it in the browser history.
- **URL-to-state:** When the user clicks back/forward, the popstate handler decodes the URL and sends the appropriate `route-to` event to the routing statechart.

Returns a cleanup function that removes all listeners.

### Step 8: Mount the App

No changes:

```clojure
(app/mount! app Root "app")
```

## Complete Browser Initialization Example

```clojure
(ns my.app.client
  (:require
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.statecharts.integration.fulcro :as scf]
    [com.fulcrologic.statecharts.integration.fulcro.routing :as scr]
    [com.fulcrologic.statecharts.chart :refer [statechart]]
    [com.fulcrologic.rad.application :as rad-app]
    [my.app.ui.root :refer [Root]]
    [my.app.ui.controls :refer [all-controls]]
    ;; Require component namespaces so they register
    [my.app.ui.account-form]
    [my.app.ui.dashboard]
    [my.app.ui.user-list]))

(defonce app (rad-app/fulcro-rad-app {}))

(def routing-chart
  (statechart {:initial :state/route-root}
    (scr/routing-regions
      (scr/routes {:id :region/main :routing/root :my.app.ui.root/Root}
        (scr/rstate {:route/target :my.app.ui.dashboard/Dashboard})
        (scr/istate {:route/target :my.app.ui.account-form/AccountForm
                     :route/segment "account"
                     :route/params #{:id}})
        (scr/istate {:route/target :my.app.ui.user-list/UserList
                     :route/segment "users"})))))

(defn ^:export init []
  ;; 1. Install rendering controls
  (rad-app/install-ui-controls! app all-controls)

  ;; 2. Install statecharts infrastructure
  (scf/install-fulcro-statecharts! app
    {:on-save (fn [session-id wmem]
                (scr/url-sync-on-save session-id wmem app))})

  ;; 3. Start the routing chart
  (scr/start! app routing-chart)

  ;; 4. Install URL synchronization
  (scr/install-url-sync! app)

  ;; 5. Mount the app
  (app/mount! app Root "app"))
```

## Complete Headless (Test) Initialization Example

```clojure
(ns my.app.test-helpers
  (:require
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.statecharts.integration.fulcro :as scf]
    [com.fulcrologic.statecharts.integration.fulcro.routing :as scr]
    [com.fulcrologic.statecharts.integration.fulcro.routing.simulated-history :as sim]
    [com.fulcrologic.statecharts.chart :refer [statechart]]
    [com.fulcrologic.rad.application :as rad-app]))

(defn create-test-app
  "Creates a fully initialized test app with routing. Returns the app.
   The app uses :immediate event processing so all transitions are synchronous."
  [routing-chart & [{:keys [controls]}]]
  (let [test-app (rad-app/fulcro-rad-app {})]
    ;; Set the root so Fulcro state is initialized
    (app/set-root! test-app Root {:initialize-state? true})

    ;; Install controls if provided
    (when controls
      (rad-app/install-ui-controls! test-app controls))

    ;; Install statecharts with :immediate event processing (no async, no polling)
    (scf/install-fulcro-statecharts! test-app
      {:event-loop? :immediate
       :on-save     (fn [session-id wmem]
                      (scr/url-sync-on-save session-id wmem test-app))})

    ;; Start routing
    (scr/start! test-app routing-chart)
    test-app))

(defn create-test-app-with-url-sync
  "Like create-test-app but also installs URL sync with a simulated history provider.
   Returns {:keys [app provider]} so tests can inspect URL state."
  [routing-chart & [opts]]
  (let [test-app (create-test-app routing-chart opts)
        provider (sim/simulated-url-history)]
    (scr/install-url-sync! test-app {:provider provider})
    {:app test-app :provider provider}))
```

**Key differences from browser mode:**

| Aspect | Browser | Headless |
|---|---|---|
| `:event-loop?` | `true` (core.async loop) | `:immediate` (synchronous) |
| URL sync provider | Browser history (auto) | Simulated history (manual) |
| `app/mount!` | Called with DOM element | Not called; use `app/set-root!` |
| Component registration | Requires from ns | Same -- must require component ns |

## What Happens to `rad/install!`

The current RAD application module does NOT have a single `install!` function. The setup is composed from individual calls:

1. `rad-app/fulcro-rad-app` -- Creates the app (unchanged)
2. `rad-app/install-ui-controls!` -- Installs controls (unchanged)
3. Routing setup -- **Replaced** by steps 3-7 above

The `default-network-blacklist` in `rad-app` currently includes `::uism/asm-id`. In the statecharts system, this entry becomes irrelevant (no `asm-id` in queries), but it is harmless to leave in place. The blacklist may need `::sc/session-id` added if statechart session data appears in queries, but since statechart working memory is stored at `[::sc/session-id session-id]` (a table, not a component prop), it will not appear in normal component queries.

## Plugin System (Rendering Controls) Initialization

Rendering controls are initialized identically to current RAD. The `install-ui-controls!` function stores the control map in the app runtime atom. Form and report renderers access controls at render time via `(-> app ::app/runtime-atom deref :com.fulcrologic.rad/controls)`.

The statecharts system does not change how controls are stored, looked up, or used. Controls are a rendering concern orthogonal to state management.

## Route Denied / Dirty Form Handling

The statecharts routing system has built-in support for route denial when forms are dirty:

1. Each form component can set `sfro/busy?` as a component option, or the system auto-detects dirty forms via `fs/dirty?`
2. When a route-to event arrives and `busy?` returns true, the routing chart stores the failed route event and raises `:event.routing-info/show`
3. The UI can show a confirmation dialog when `(scr/route-denied? app)` returns true
4. Call `(scr/force-continue-routing! app)` to force the route change
5. Call `(scr/abandon-route-change! app)` to cancel and stay on the current route

This replaces the UISM-based `route-denied?`, `continue-abandoned-route!`, and `clear-route-denied!` functions.

## Open Questions

- Should `fulcro-rad-app` be updated to optionally call `install-fulcro-statecharts!` automatically, or should it remain a separate step? Recommendation: keep separate for clarity and testability.
- **DECIDED: Routing chart is user-defined.** The user manually defines their routing chart using `rstate`/`istate`. RAD does NOT auto-generate the routing chart from form/report definitions. Auto-generation may be considered for a future version, but for v1 the chart is explicitly authored by the application developer.

## Verification

1. [ ] Browser app initializes with statecharts routing instead of DR
2. [ ] URL sync works bidirectionally (state changes update URL, URL changes trigger routing)
3. [ ] Route denial works for dirty forms
4. [ ] Headless test app initializes without browser dependencies
5. [ ] Headless tests can route programmatically and verify state
6. [ ] `install-ui-controls!` works unchanged
7. [ ] `fulcro-rad-app` works unchanged
8. [ ] Form/report co-located statecharts are auto-registered on first route entry
9. [ ] Nested routing regions work (parallel routes)
10. [ ] Route parameters are stored and accessible via `[:routing/parameters <state-id>]`
