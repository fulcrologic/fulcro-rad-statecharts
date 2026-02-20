# Spec: Authorization Statechart Conversion

**Status**: backlog
**Priority**: P1
**Created**: 2026-02-20
**Owner**: spec-writer-4
**Depends-on**: project-setup

## Context

RAD's authorization system (`com.fulcrologic.rad.authorization`) uses a Fulcro UISM (`auth-machine`) to manage authentication state per provider/authority. The original code is explicitly labeled "NOT PRODUCTION-READY" and was designed as a starting point. This conversion replaces the UISM with a statechart while preserving the existing public API signatures.

The auth system supports:
- Multiple named authorities/providers (e.g. `:local`, `:oauth`)
- Session checking on startup (each provider's `::check-session` mutation)
- Credential gathering flow (username/password via `::authentication-providers`)
- Cross-machine communication: sends `:event/authenticated` or `:event/authentication-failed` back to the requesting machine
- Logout per-provider

## Current UISM Architecture

### States

| UISM State | Description |
|---|---|
| `:initial` | Runs `::check-session` mutations on all actors, stores config, transitions to `:state/idle` |
| `:state/idle` | Waiting. Handles all global events. |
| `:state/gathering-credentials` | Auth dialog is shown to the user. Handles all global events. |
| `:state/failed` | Auth failed. Handles all global events (can retry). |

### Global Events (available in all states)

| Event | Handler |
|---|---|
| `:event/authenticate` | If already authenticated for provider, replies immediately. Otherwise stores provider and transitions to gathering-credentials. |
| `:event/logged-in` | Adds provider to authenticated set, replies `:event/authenticated` to source machine, goes idle. |
| `:event/failed` | Removes provider from authenticated set, replies `:event/authentication-failed`, goes to failed. |
| `:event/logout` | Runs `::logout` mutation on all actors, removes provider from set, updates state map, goes idle. |
| `:event/session-checked` | Checks session status in state-map, adds/removes from authenticated set. Runs `after-session-check` if configured. |

### UISM Storage

- `:authenticated` - Set of currently authenticated provider keywords
- `:provider` - The provider currently being authenticated
- `:source-machine-id` - The UISM that requested auth (for reply)
- `:config` - Startup options passed to `start!`

### Aliases

- `:username` -> `[:actor/auth-dialog :ui/username]`
- `:password` -> `[:actor/auth-dialog :ui/password]`
- `:status` -> `[:actor/session]`

## Proposed Statechart

**Design note**: Keep this as a minimal conversion from UISM, not a redesign. The auth system is labeled "NOT PRODUCTION-READY" and a redesign is deferred to v2.

```clojure
(statechart {:initial :state/initializing}
  (data-model {:expr (fn [_ _] {:authenticated #{}
                                  :provider nil
                                  :source-session-id nil
                                  :config {}})})

  (state {:id :state/initializing}
    (on-entry {}
      (script {:expr (fn [env data & _]
                       ;; Run ::check-session mutations on all authority actors
                       ;; Store config from event data
                       [(ops/assign :config (-> data :_event :data))
                        (ops/assign :authenticated #{})])}))
    (on :event/initialized :state/idle))

  ;; Parent compound state hoists common events to avoid duplication
  (state {:id :state/auth :initial :state/idle}
    ;; --- Common events available in all child states ---
    (transition {:event :event/authenticate
                 :cond  (fn [_ data & _]
                          (contains? (:authenticated data)
                            (-> data :_event :data :provider)))
                 :target :state/idle}
      (script {:expr reply-authenticated!}))
    (transition {:event :event/authenticate
                 :target :state/gathering-credentials}
      (script {:expr store-provider-and-source!}))
    (on :event/logout :state/idle
      (script {:expr handle-logout!}))
    (handle :event/session-checked handle-session-checked!)

    ;; --- Child states ---
    (state {:id :state/idle})

    (state {:id :state/gathering-credentials}
      (on-entry {}
        (script {:expr setup-auth-dialog!}))
      (on :event/logged-in :state/idle
        (script {:expr handle-logged-in!}))
      (on :event/failed :state/failed
        (script {:expr handle-failed!})))

    (state {:id :state/failed}
      (on :event/logged-in :state/idle
        (script {:expr handle-logged-in!})))))
```

### Key Differences from UISM

1. **No cross-machine trigger**: UISM `uism/trigger` sent events to other UISMs by their asm-id. With statecharts, the auth chart must use `scf/send!` with the `source-session-id` stored in session data. The session-id replaces the asm-id concept.

2. **Actor swapping**: The UISM dynamically swaps the `:actor/auth-dialog` ident based on the current provider. With statecharts, use `fops/set-actor` to achieve the same actor ident reassignment:
   ```clojure
   (defn setup-auth-dialog!
     "Swaps the auth dialog actor to the component for the current provider."
     [env data & _]
     (let [provider      (:provider data)
           config        (:config data)
           providers-map (::authentication-providers config)
           DialogClass   (get providers-map provider)
           dialog-ident  (comp/get-ident DialogClass {})]
       [(fops/set-actor data :actor/auth-dialog {:class DialogClass :ident dialog-ident})]))
   ```

3. **State-map side effects**: The UISM runs `comp/transact!` as side effects (e.g., `::check-session`, `::logout` mutations). In the statechart, these become `fops/invoke-remote` operations or direct `comp/transact!` via the app in env.

4. **Data storage**: UISM uses `uism/store` / `uism/retrieve`. Statecharts use `ops/assign` / direct data access.

### Event Data Contract: `:event/authenticate`

The `:event/authenticate` event must include enough data for the auth chart to:
- Know which provider to authenticate with
- Know where to send the reply event

```clojure
;; Event data contract for :event/authenticate
{:provider          :local          ;; keyword identifying the authority provider
 :source-session-id session-id}    ;; session-id of the requesting chart (form, routing, etc.)

;; Example: form requesting auth
(scf/send! app auth-session-id :event/authenticate
  {:provider          :local
   :source-session-id (form-session-id this)})
```

The `store-provider-and-source!` expression stores both:
```clojure
(defn store-provider-and-source!
  [env data _event-name event-data]
  [(ops/assign :provider (:provider event-data))
   (ops/assign :source-session-id (:source-session-id event-data))])
```

The `reply-authenticated!` expression sends the reply:
```clojure
(defn reply-authenticated!
  [env data & _]
  (let [app        (:fulcro/app env)
        source-sid (:source-session-id data)]
    (when source-sid
      (scf/send! app source-sid :event/authenticated {:provider (:provider data)}))
    nil))
```

## Public API Mapping

| Current Function | Change | Notes |
|---|---|---|
| `start! [app authority-ui-roots options]` | Internal rewrite | Uses `scf/start!` instead of `uism/begin!`. Same signature. |
| `authenticate! [app-ish provider source-machine-id]` | Signature change | `source-machine-id` becomes `source-session-id`. Uses `scf/send!` instead of `uism/trigger!`. |
| `authenticate [any-sm-env provider source-machine-id]` | Signature change | Same rename. Uses statechart event queue instead of `uism/trigger`. |
| `logged-in! [app-ish provider]` | Internal rewrite | Uses `scf/send!`. Same signature. |
| `failed! [app-ish provider]` | Internal rewrite | Uses `scf/send!`. Same signature. |
| `logout! [app-ish provider]` | Internal rewrite | Uses `scf/send!`. Same signature. |
| `verified-authorities [app-ish]` | Internal rewrite | Reads from statechart session data instead of UISM storage. Same signature. |
| `defauthenticator` macro | Rewrite | Needs to work with statecharts session queries instead of UISM asm-id. |
| `machine-id` constant | Becomes `session-id` | The well-known session ID for the auth statechart. |

## Session Checking

On startup, the current code iterates over all actors, finds those with `::check-session` component options, and runs those mutations via `comp/transact!`. The statechart version should:

1. In the `:state/initializing` on-entry, iterate the configured authority UI roots
2. Run each `::check-session` mutation via `comp/transact!` (using `(:fulcro/app env)`)
3. Each mutation's result should trigger `:event/session-checked` back to the auth statechart
4. Transition to `:state/idle` after initial check (can be immediate; session-checked events arrive asynchronously)

## Multi-Authority Support

The auth system supports multiple providers. Key considerations:

- The `:authenticated` set in session data tracks which providers are authenticated
- Only one provider can be in the "gathering credentials" flow at a time (single `:provider` slot)
- The `defauthenticator` macro generates a component that selects the correct provider UI based on the current provider being authenticated
- Provider-specific UI components are registered via `::authentication-providers` map on the controller component

## Files to Modify

| File | Action |
|---|---|
| `authorization.cljc` | Rewrite: Replace UISM with statechart |
| `authorization/simple_authorization.cljc` | Review: Currently a stub, may need updates if it references UISM |

## Testing Strategy

1. Use `com.fulcrologic.statecharts.testing` to verify state transitions
2. Test: idle -> authenticate (not yet authed) -> gathering-credentials -> logged-in -> idle
3. Test: idle -> authenticate (already authed) -> stays idle, replies immediately
4. Test: gathering-credentials -> failed -> retry -> logged-in -> idle
5. Test: logout removes provider from authenticated set
6. Test: session-checked adds/removes provider based on status
7. Test: reply events are sent to the correct source session

## Open Questions

1. **Cross-chart communication**: When a form or routing chart requests auth, how do they provide their session-id for the reply? The UISM used `source-machine-id` which was another UISM's asm-id. With statecharts, the source needs to provide its statechart session-id.

2. **Auth dialog actor swapping**: Is `fops/set-actor` sufficient for dynamic actor reassignment, or do we need a different pattern for swapping which component serves as the auth dialog?

3. **Session data vs state-map storage**: The current code stores auth status in both UISM storage (`uism/store :authenticated`) AND the normalized state map (`[::authorization provider]`). The statechart version should pick one source of truth -- session data is preferred, but UI components may need to read from the state map.

4. **Deprecation path**: **Decision: Minimal conversion now.** Since the original code is explicitly "NOT PRODUCTION-READY", do a minimal conversion (keep same behavior) and flag it as a candidate for v2 redesign. Don't let scope creep here block the main conversion.

## Revision History

- **R1**: Initial spec
- **R2**: Applied critique-round-1 fixes:
  - Hoisted common events (`:event/authenticate`, `:event/logout`, `:event/session-checked`) to parent compound state `:state/auth`
  - Specified event data contract for `:event/authenticate` (includes `:source-session-id`)
  - Showed actor swap pattern with `fops/set-actor` for auth dialog
  - Added note: keep minimal conversion, don't redesign for v1
