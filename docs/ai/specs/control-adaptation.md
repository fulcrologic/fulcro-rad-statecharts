# Spec: Control System Adaptation to Statecharts

**Status**: backlog
**Priority**: P1
**Created**: 2026-02-20
**Owner**: conductor
**Depends-on**: project-setup, session-id-convention

## Context

The RAD control system (`com.fulcrologic.rad.control`) provides buttons and inputs for reports and containers. Controls are not backed by model data -- they manage UI parameters like search filters and action buttons.

The control system has a single UISM touchpoint: the `run!` function uses `uism/trigger!` to send an `:event/run` event to the component's state machine (typically a report or container UISM). This is the only UISM coupling in the control namespace.

## Requirements

1. Replace `uism/trigger!` call in `run!` with `scf/send!`
2. Preserve the debounce behavior on `run!` (currently 100ms)
3. The `set-parameter` mutation and `set-parameter!` function do NOT use UISMs and require no changes
4. The `control-options.cljc` namespace is pure option definitions with no UISM dependency -- no changes needed
5. The event name `:event/run` should be preserved or mapped to a statechart-compatible event

## Affected Modules

- `src/main/com/fulcrologic/rad/control.cljc` - Replace `uism/trigger!` in `run!`, update require

## Approach

### Current UISM Usage

The only UISM usage in `control.cljc` is:

```clojure
(require [com.fulcrologic.fulcro.ui-state-machines :as uism])

(def run!
  (debounce
    (fn [instance]
      (uism/trigger! instance (comp/get-ident instance) :event/run))
    100))
```

`uism/trigger!` sends an event to a UISM session identified by the component's ident. In the statecharts world, sessions are identified by a session ID (not an ident directly).

### Conversion Strategy

The report and container statecharts will need to define a session ID convention. The most natural mapping is to use the component's ident as the session ID (or derive a session ID from it), since that's what the current UISM system uses.

The converted `run!` becomes:

```clojure
(require [com.fulcrologic.statecharts.integration.fulcro :as scf])

(def run!
  (debounce
    (fn [instance]
      (let [session-id (ident->session-id (comp/get-ident instance))]
        (scf/send! instance session-id :event/run)))
    100))
```

Key differences:
- `scf/send!` accepts `app-ish` (which includes component instances directly -- no need for `comp/any->app`)
- The session-id is derived from the component ident via `ident->session-id` (see `session-id-convention.md`)
- The event keyword `:event/run` can remain the same -- statecharts use keywords for events

### Session ID Convention

See `session-id-convention.md` for the full session-id strategy. Reports and containers use `ident->session-id` to derive a deterministic keyword session-id from their component ident. The `::sc/id` type only accepts UUID, number, keyword, or string -- NOT vector idents.

```clojure
;; session-id-convention.md defines:
(defn ident->session-id [[table id]]
  (keyword (str (namespace table) "." (name table)) (str id)))

;; Example: [:com.fulcrologic.rad.report/id :account-report]
;; => :com.fulcrologic.rad.report.id/account-report
```

This ensures `run!` can find the right session by deriving the session-id from the component ident, just as it finds the right UISM today.

### Changes Summary

| Location | Current | New |
|----------|---------|-----|
| `control.cljc` require | `[com.fulcrologic.fulcro.ui-state-machines :as uism]` | `[com.fulcrologic.statecharts.integration.fulcro :as scf]` |
| `run!` body | `(uism/trigger! instance (comp/get-ident instance) :event/run)` | `(scf/send! instance (ident->session-id (comp/get-ident instance)) :event/run)` |

No other functions in this namespace need changes.

### No Changes Needed

- `render-control` - Pure UI rendering, no UISM
- `set-parameter` mutation - Direct state manipulation, no UISM
- `set-parameter!` - Calls `comp/transact!`, no UISM
- `control-map->controls` - Data transformation, no UISM
- `current-value` - Reads from app state, no UISM
- `component-controls` - Reads component options, no UISM
- `standard-control-layout` - Layout computation, no UISM
- `control_options.cljc` - Pure option defs, no code

## Open Questions

- Should `run!` accept an explicit session-id parameter for cases where the session ID does not match the component ident? This would add flexibility but change the public API.
- The debounce wrapper captures `instance` but `scf/send!` needs `app`. Using `comp/any->app` inside the debounced function should work, but verify that the app reference remains stable across debounce delays.
- Should the `:event/run` event be namespaced (e.g. `:com.fulcrologic.rad.control/run`) for consistency with statechart event conventions? This affects the report/container statechart definitions.

## Verification

1. [ ] `uism` require removed from `control.cljc`
2. [ ] `scf` require added to `control.cljc`
3. [ ] `run!` uses `scf/send!` instead of `uism/trigger!`
4. [ ] Debounce behavior preserved (100ms)
5. [ ] `set-parameter` and other functions unchanged
6. [ ] Report statechart handles `:event/run` event (verified in report spec)
7. [ ] Container statechart handles `:event/run` event (verified in container spec)
8. [ ] Controls render and function correctly in a running app

## Revision History

- **R1**: Initial spec
- **R2**: Applied critique-round-1 fixes:
  - Added depends-on: session-id-convention
  - Referenced session-id-convention.md for session-id discovery (uses `ident->session-id`, not raw idents)
  - Simplified `scf/send!` call to use component instance directly as `app-ish` (no `comp/any->app` needed)
