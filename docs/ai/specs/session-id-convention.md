# Spec: Session ID Convention

**Status**: active
**Priority**: P0
**Created**: 2026-02-20
**Owner**: AI
**Depends-on**: project-setup

## Context

Multiple specs independently propose session-id strategies without a unified decision. The session ID is the key used to identify a running statechart instance and is passed to `scf/start!`, `scf/send!`, and `scf/current-configuration`. Every module (forms, reports, containers, auth, controls, hooks) must agree on how session IDs are generated and resolved, or cross-module communication (e.g., controls sending events to reports) will break.

### `::sc/id` Type Constraint

From `com.fulcrologic.statecharts.specs`:

```clojure
(s/def ::sc/id (s/or :u uuid? :n number? :k keyword? :s string?))
(s/def ::sc/session-id ::sc/id)
```

**Valid session ID types**: UUID, number, keyword, or string.

**Vector idents (e.g., `[:account/id #uuid "..."]`) are NOT valid session IDs.** The spec explicitly enumerates scalar types only. This rules out using Fulcro component idents directly as session IDs.

### `start!` Signature Confirmation

From `com.fulcrologic.statecharts.integration.fulcro`:

```clojure
(>defn start!
  [app-ish {:keys [machine session-id data]
            :or   {session-id (new-uuid) data {}}}]
  [::fulcro-appish [:map
                    [:machine :keyword]
                    [:session-id {:optional true} ::sc/id]
                    [:data {:optional true} map?]] => ...])
```

The guardrails spec enforces `::sc/id` for session-id. If omitted, a random UUID is generated.

## Requirements

1. Define a deterministic conversion from Fulcro idents to valid session IDs
2. Specify the session-id convention for every module: forms, reports, containers, auth, controls, hooks
3. Ensure controls can discover the session-id of the report/container they target
4. Ensure the conversion is reversible where needed (session-id back to ident)
5. All conventions must produce values satisfying `::sc/id` (keyword, UUID, number, or string)

## Convention: Ident-to-Session-ID Conversion

### Core Function: `ident->session-id`

Since vector idents are invalid, we need a deterministic, collision-free conversion:

```clojure
(defn ident->session-id
  "Converts a Fulcro ident to a valid statechart session ID keyword.
   The ident `[:account/id #uuid \"abc\"]` becomes `:com.fulcrologic.rad.sc/account_id--abc`."
  [[k v]]
  (let [ns   "com.fulcrologic.rad.sc"
        name (str (namespace k) "_" (name k) "--" v)]
    (keyword ns name)))
```

This produces a namespaced keyword that:
- Satisfies `::sc/id` (it is a keyword)
- Is deterministic (same ident always produces same session-id)
- Is collision-free (the separator `--` is unambiguous)
- Uses a dedicated namespace to avoid conflicts with application keywords

### Inverse Function: `session-id->ident`

```clojure
(defn session-id->ident
  "Converts a session ID keyword back to a Fulcro ident. Returns nil if the keyword
   is not a converted ident."
  [session-id]
  (when (and (keyword? session-id)
             (= "com.fulcrologic.rad.sc" (namespace session-id)))
    (let [n     (name session-id)
          [qk v] (str/split n #"--" 2)
          [ns nm] (str/split qk #"_" 2)]
      (when (and ns nm v)
        [(keyword ns nm) (ids/id-string->id v)]))))
```

## Per-Module Conventions

### 1. Forms

**Session ID**: `(ident->session-id form-ident)` where `form-ident` is `[id-key entity-id]`.

```clojure
;; Starting a form
(let [form-ident [qualified-key coerced-id]
      session-id (ident->session-id form-ident)]
  (scf/start! app {:machine    ::form-chart
                   :session-id session-id
                   :data       {:fulcro/actors {:actor/form (scf/actor FormClass form-ident)}
                                ...}}))
```

**Rationale**: Forms are identified by their entity ident. The session-id must be derivable from the ident so that `save!`, `cancel!`, etc. can compute it from `(comp/get-ident this)`.

**Helper**:

```clojure
(defn form-session-id
  "Returns the statechart session ID for a form instance or class+ident."
  ([form-instance]
   (ident->session-id (comp/get-ident form-instance)))
  ([form-class ident]
   (ident->session-id ident)))
```

### 2. Reports

**Session ID**: `(ident->session-id report-ident)` where `report-ident` is `[::report/id fqkw]`.

Reports use a singleton ident pattern: `[::report/id :myapp.ui/AccountList]`. The session-id is derived the same way as forms.

```clojure
(defn report-session-id
  "Returns the statechart session ID for a report instance or class."
  ([report-instance]
   (ident->session-id (comp/get-ident report-instance)))
  ([report-class]
   (ident->session-id (comp/get-ident report-class {}))))
```

**Usage in controls**: Controls call `(comp/get-ident instance)` on their parent report/container to derive the session-id. This is the same pattern as the current UISM code.

```clojure
;; In control/run!
(defn run! [instance]
  (let [ident      (comp/get-ident instance)
        session-id (ident->session-id ident)]
    (scf/send! instance session-id :event/run)))
```

### 3. Containers

**Session ID**: `(ident->session-id container-ident)` where `container-ident` is `[::container/id fqkw]`.

Same pattern as reports. Containers are singletons with a class-derived ident.

```clojure
(defn container-session-id
  "Returns the statechart session ID for a container instance or class."
  ([container-instance]
   (ident->session-id (comp/get-ident container-instance)))
  ([container-class]
   (ident->session-id (comp/get-ident container-class {}))))
```

### 4. Authorization

**Session ID**: Well-known keyword `::auth-session`.

The auth system is a singleton. There is exactly one auth statechart per application. Using a well-known keyword is simpler and more discoverable than computing from an ident.

```clojure
(def auth-session-id ::auth-session)

;; Starting auth
(scf/start! app {:machine    ::auth-chart
                 :session-id auth-session-id
                 :data       {...}})

;; Sending events from any context
(scf/send! app ::auth-session :event/authenticate {:source-session source-id})
```

### 5. Hooks (Embedded Forms/Reports)

**Session ID**: Random UUID generated per mount via `hooks/use-gc-id`.

Hooks create ephemeral, non-routed instances. They have no stable ident, so a random UUID is appropriate. The `use-gc-id` hook from the statecharts library already provides a UUID that is stable across re-renders and auto-cleaned on unmount.

```clojure
;; Inside use-form / use-report hook
(let [session-id (hooks/use-gc-id)]
  (hooks/use-effect
    (fn []
      (scf/start! app {:machine    chart-key
                       :session-id session-id
                       :data       {:fulcro/actors {:actor/form (scf/actor FormClass form-ident)}}}))
    [session-id])
  ...)
```

### 6. Routing Integration (`istate`)

**Session ID**: Managed by `istate`'s `invoke` element.

When a component is used as a route target via `istate`, the statecharts invocation system manages the child session automatically. RAD does not need to manage this directly -- `istate` handles the lifecycle.

**DECIDED: `ident->session-id` is only for embedded/non-routed use.** Routed forms use `send-to-self!` -- no session-id discovery needed. The `send-to-self!` pattern from the routing library is the correct and only way to send events from within a routed component:

```clojure
;; Inside a routed form, the component can use:
(scr/send-to-self! this :event/save)
```

The public API functions (`save!`, `cancel!`, etc.) when called from within a routed form component use `send-to-self!` internally. This avoids any need to align `istate`'s invoke-generated session-id with `ident->session-id`.

For non-routed (embedded) forms/reports (e.g., modals, inline subforms, hook-based), `ident->session-id` is used because these are started explicitly via `start-form!` / `start-report!` with a known session-id.

This simplifies the session-id convention considerably:
- **Routed**: `send-to-self!` (session-id discovery is automatic)
- **Embedded/non-routed**: `ident->session-id` (session-id is deterministic from the ident)

## Summary Table

| Module      | Context | Session ID Source                         | Type    | Stable? | Example                                         |
|-------------|---------|-------------------------------------------|---------|---------|--------------------------------------------------|
| Form        | Routed  | Managed by `istate` invoke; use `send-to-self!` | UUID | Per-route | Auto-generated by invocation system |
| Form        | Embedded | `(ident->session-id form-ident)`         | Keyword | Yes     | `:com.fulcrologic.rad.sc/account_id--abc-123`    |
| Report      | Routed  | Managed by `istate` invoke; use `send-to-self!` | UUID | Per-route | Auto-generated by invocation system |
| Report      | Embedded | `(ident->session-id report-ident)`       | Keyword | Yes     | `:com.fulcrologic.rad.sc/report_id--myapp...`    |
| Container   | Routed  | Managed by `istate` invoke; use `send-to-self!` | UUID | Per-route | Auto-generated by invocation system |
| Container   | Embedded | `(ident->session-id container-ident)`    | Keyword | Yes     | `:com.fulcrologic.rad.sc/container_id--myapp...` |
| Auth        | Global  | `::auth-session`                          | Keyword | Yes     | `:com.fulcrologic.rad.sc/auth-session`           |
| Hooks       | Embedded | `(hooks/use-gc-id)`                      | UUID    | Per-mount | `#uuid "..."` (random)                         |

## Affected Modules

- `src/main/com/fulcrologic/rad/sc/session.cljc` (NEW) - `ident->session-id`, `session-id->ident`, per-module helpers
- `src/main/com/fulcrologic/rad/form.cljc` - Use `form-session-id` in `start-form!`, `save!`, `cancel!`, etc.
- `src/main/com/fulcrologic/rad/report.cljc` - Use `report-session-id` in `start-report!`, `run-report!`, etc.
- `src/main/com/fulcrologic/rad/container.cljc` - Use `container-session-id` in `start-container!`
- `src/main/com/fulcrologic/rad/control.cljc` - Use `ident->session-id` in `run!`
- `src/main/com/fulcrologic/rad/rad_hooks.cljc` - Use `hooks/use-gc-id` for embedded instances

## Open Questions

1. **DECIDED: Routed forms use `send-to-self!`, not `ident->session-id`.** `ident->session-id` is only for embedded/non-routed use. Routed forms use `send-to-self!` which automatically discovers the co-located chart's session-id. No need to align `istate`'s child-session-id with `ident->session-id`.
2. **Should the namespace be shortened?** `com.fulcrologic.rad.sc` is verbose in the keyword. An alternative is `:rad.sc/account_id--abc` but this uses a non-reversed namespace.

## Verification

1. [ ] `ident->session-id` produces values satisfying `::sc/id` (keyword)
2. [ ] `session-id->ident` correctly round-trips for all ident value types (UUID, int, string, keyword)
3. [ ] `form-session-id` matches what `start-form!` uses
4. [ ] `report-session-id` matches what `start-report!` uses
5. [ ] Controls can discover and send events to report/container sessions
6. [ ] Hook-based session IDs are unique per mount and cleaned up on unmount
7. [ ] Auth uses a well-known keyword accessible from any context
