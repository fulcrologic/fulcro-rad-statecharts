# Spec: Public API Mapping

**Status**: backlog
**Priority**: P1
**Created**: 2026-02-20
**Owner**: spec-writer-4
**Depends-on**: project-setup

## Context

This document maps every public function in RAD's major namespaces from its current UISM-based implementation to its statecharts-based replacement. For each function: current signature, new signature (or "unchanged"), what changes internally, and whether it is a breaking change.

The overarching pattern: functions that use `uism/trigger!` internally will switch to `scf/send!` targeting a statechart session. Functions that use `uism/begin!` will switch to `scf/start!`. Functions that query UISM state will read from statechart session data.

---

## form.cljc

### Core Operations

| Function | Current Signature | New Signature | Internal Change | Breaking? |
|---|---|---|---|---|
| `save!` | `[{::master-form :as renv}]` or `[renv addl-save-params]` | Unchanged | `uism/trigger!` -> `scf/send!` to form session | No |
| `undo-all!` | `[{::master-form :as renv}]` | Unchanged | `uism/trigger!` -> `scf/send!` | No |
| `cancel!` | `[{::master-form :as renv}]` | Unchanged | `uism/trigger!` -> `scf/send!` | No |
| `add-child!` | `[env]` or `[form-instance parent-relation ChildForm]` or `[form-instance parent-relation ChildForm options]` | Unchanged | `uism/trigger!` -> `scf/send!` | No |
| `delete-child!` | `[this-or-renv]` or `[parent-instance relation-key child-ident]` | Unchanged | `uism/trigger!` -> `scf/send!` | No |
| `delete!` | `[this id-key entity-id]` | Unchanged | Mutation-based, no UISM involvement | No |
| `input-changed!` | `[renv k value]` or `[renv k value options]` | Unchanged | `uism/trigger!` -> `scf/send!` | No |
| `input-blur!` | `[renv k value]` | Unchanged | `uism/trigger!` -> `scf/send!` | No |
| `trigger!` | `[renv event]` or `[renv event event-data]` or `[app-ish top-form-ident event event-data]` | `[renv event]` or `[renv event event-data]` or `[app-ish form-session-id event event-data]` | `uism/trigger!!` -> `scf/send!`. The 4-arity form changes from form-ident to session-id. **Recommendation**: Prefer `scr/send-to-self!` from within a routed form component instead of using `trigger!`. `send-to-self!` automatically discovers the co-located chart's session-id. | **Yes** (4-arity) |
| `mark-all-complete!` | `[master-form-instance]` | Unchanged | Direct state mutation, no UISM | No |

### Routing Operations

| Function | Current Signature | New Signature | Internal Change | Breaking? |
|---|---|---|---|---|
| `view!` | `[this form-class entity-id]` + 2 overloads | Unchanged | `rad-routing/route-to!` delegates to statecharts routing | No |
| `edit!` | `[this form-class entity-id]` + 2 overloads | Unchanged | Same delegation | No |
| `create!` | `[app-ish form-class]` + 2 overloads | Unchanged | Same delegation | No |
| `start-form!` | `[app options FormClass]` | Internal changes | **Routed forms**: Called automatically from `istate` on-entry; users should NOT call directly. **Embedded forms** (non-routed, e.g. inline subforms or hook-based): Remains public and callable. | No (signature unchanged) |
| `form-will-enter` | `[app route-params FormClass]` | REMOVED | Replaced by `istate` on-entry + `sfro/busy?` | **Yes** |
| `form-will-leave` | `[FormClass this route-params]` | REMOVED | Replaced by `sfro/busy?` component option | **Yes** |
| `form-allow-route-change` | `[this]` | REMOVED | Replaced by statecharts `busy?` guard | **Yes** |
| `clear-route-denied!` | `[this-form]` or `[app-ish form-ident]` | `[app-ish]` | Uses `scr/abandon-route-change!`. **Semantic change**: no longer form-specific; operates on the global routing chart session. | **Yes** |
| `continue-abandoned-route!` | `[this-form]` or `[app-ish form-ident]` | `[app-ish]` | Uses `scr/force-continue-routing!`. **Semantic change**: no longer form-specific; operates on the global routing chart session. | **Yes** |

### Query Functions

| Function | Current Signature | New Signature | Internal Change | Breaking? |
|---|---|---|---|---|
| `view-mode?` | `[form-instance]` | Unchanged signature | **Full rewrite**: Currently reads `::uism/asm-id` -> `::uism/local-storage :options :action`. Must read from statechart session data instead. | **Yes** (internal breaking) |
| `valid?` | `[form-rendering-env-or-props]` | Unchanged | Pure function, no UISM dependency | No |
| `invalid?` | `[form-rendering-env-or-props]` | Unchanged | Pure function, no UISM dependency | No |
| `read-only?` | `[form-instance attr]` | Unchanged | Pure function based on component options | No |
| `field-visible?` | `[form-instance attr]` | Unchanged | Pure function | No |
| `server-errors` | `[top-form-instance]` | Unchanged | Reads from component props | No |
| `rendering-env` | `[form-instance]` | Unchanged signature | Constructs env from component props. **Verify**: if it includes any UISM-derived data (e.g., active state from `::uism/asm-id`), those references must be updated to read from statechart session data or component props. | Possibly (internal) |
| `computed-value` | `[env attr]` | Unchanged | Pure computation | No |
| `field-label` | `[form-env attr]` | Unchanged | Pure computation | No |
| `master-form` | `[form-instance]` | Unchanged | Reads from computed data | No |

### Form Machine / Lifecycle

| Function | Current Signature | New Signature | Internal Change | Breaking? |
|---|---|---|---|---|
| `form-machine` (defstatemachine) | UISM definition | Replaced by statechart definition | Complete rewrite from UISM to statechart | **Yes** (if referenced directly) |
| `undo-via-load!` | `[app-ish form-class form-ident]` | Unchanged | Uses `df/load!`, no direct UISM dependency | No |
| `wrap-env` | `[handler]` | Unchanged | Pathom middleware, no UISM dependency | No |
| `pathom-plugin` | `[save-middleware delete-middleware]` | Unchanged | Server-side, no UISM dependency | No |

### Rendering Functions (All Unchanged)

These are pure rendering helpers with no UISM dependency:
`render-field`, `render-input`, `rendering-env`, `render-form-fields`, `render-layout`, `render-subform`, `subform-rendering-env`, `form-container-renderer`, `form-layout-renderer`, `ref-container-renderer`

---

## report.cljc

### Core Operations

| Function | Current Signature | New Signature | Internal Change | Breaking? |
|---|---|---|---|---|
| `run-report!` | `[this]` or `[app-ish class-or-registry-key]` | Unchanged signature | `uism/trigger!` -> `scf/send!` to report session | No |
| `start-report!` | `[app report-class]` or `[app report-class options]` | Internal changes | `uism/begin!` -> `scf/start!`. Called from `istate` on-entry. | Possibly |
| `sort-rows!` | `[this by-attribute]` or `[app class-or-reg-key by-attribute]` | Unchanged | `uism/trigger!` -> `scf/send!` | No |
| `clear-sort!` | `[this]` or `[app class-or-reg-key]` | Unchanged | `uism/trigger!` -> `scf/send!` | No |
| `filter-rows!` | `[this]` or `[app class-or-reg-key]` | Unchanged | `uism/trigger!` -> `scf/send!` | No |
| `goto-page!` | `[this page-number]` or `[app class-or-reg-key page-number]` | Unchanged | `uism/trigger!` -> `scf/send!` | No |
| `next-page!` | `[this]` or `[app class-or-reg-key]` | Unchanged | `uism/trigger!` -> `scf/send!` | No |
| `prior-page!` | `[this]` or `[app class-or-reg-key]` | Unchanged | `uism/trigger!` -> `scf/send!` | No |
| `select-row!` | `[report-instance row-index]` | Unchanged | `uism/trigger!` -> `scf/send!` | No |
| `trigger!` | `[report-instance event event-data]` | Unchanged | `uism/trigger!!` -> `scf/send!` | No |
| `clear-report!` | `[report-instance]` | Unchanged | Mutation-based | No |

### Routing Operations

| Function | Current Signature | New Signature | Internal Change | Breaking? |
|---|---|---|---|---|
| `report-will-enter` | `[app route-params report-class]` | REMOVED | Replaced by `rstate`/`istate` on-entry in routing chart | **Yes** |

### Query Functions (All Unchanged)

| Function | Signature | Notes |
|---|---|---|
| `report-ident` | `[report-class-or-registry-key]` | Pure ident computation |
| `current-rows` | `[report-instance]` | Reads from component props |
| `loading?` | `[report-instance]` | Reads from component props |
| `current-page` | `[report-instance]` or `[state-map key]` | Reads from props/state |
| `page-count` | `[report-instance]` or `[state-map key]` | Reads from props/state |
| `currently-selected-row` | `[report-instance]` or `[state-map key]` | Reads from props/state |

### Rendering Functions (All Unchanged)

`render-layout`, `render-row`, `render-controls`, `control-renderer`, `column-heading-descriptors`, `form-link`, `link`, `formatted-column-value`, `current-rows`, `genrow`

---

## container.cljc

| Function | Current Signature | New Signature | Internal Change | Breaking? |
|---|---|---|---|---|
| `start-container!` | `[app container-class options]` | Internal changes | `uism/begin!` -> `scf/start!` | Possibly |
| `container-will-enter` | `[app route-params container-class]` | REMOVED | Replaced by `istate` on-entry | **Yes** |
| `render-layout` | `[container-instance]` | Unchanged | Pure rendering | No |
| `id-child-pairs` | `[container]` | Unchanged | Reads component options | No |
| `child-classes` | `[container]` | Unchanged | Reads component options | No |
| `container-options` | `[uism-env & k-or-ks]` | `[env & k-or-ks]` | Reads from statechart env instead of UISM env | **Yes** (env type change) |

---

## control.cljc

| Function | Current Signature | New Signature | Internal Change | Breaking? |
|---|---|---|---|---|
| `run!` | `[instance]` | Unchanged | `uism/trigger!` -> `scf/send!` to instance's session | No |
| `set-parameter!` | `[instance parameter-name new-value]` | Unchanged | Mutation internally; URL tracking changes from `rad-routing/update-route-params!` to statechart data update | No |
| `current-value` | `[instance control-key]` | Unchanged | Reads from component props or state map | No |
| `render-control` | `[owner control-key]` or `[owner control-key control]` | Unchanged | Pure rendering | No |
| `component-controls` | `[class-or-instance]` or `[class-or-instance recursive?]` | Unchanged | Reads component options | No |

### `set-parameter` mutation

The `set-parameter` mutation currently calls `rad-routing/update-route-params!` to persist control values in the URL. This changes:

- **Before**: `rad-routing/update-route-params!` reads from `history/current-route`, applies update, calls `history/replace-route!`
- **After**: The mutation updates Fulcro state directly. URL sync is handled by the statecharts routing layer automatically when the control's statechart session data changes (if the control value is part of route params).

---

## routing.cljc

| Function | Current Signature | New Signature | Internal Change | Breaking? |
|---|---|---|---|---|
| `route-to!` | `[app options]` or `[app-or-component RouteTarget route-params]` | `[app-ish target]` or `[app-ish target data]` | Delegates to `scr/route-to!` instead of `RADRouter/-route-to!` | **Yes** (arity/params) |
| `back!` | `[app-or-component]` | Unchanged | Delegates to `scr/route-back!` instead of `history/back!` | No |
| `install-routing!` | `[app routing]` | REMOVED | No pluggable router protocol | **Yes** |
| `absolute-path` | `[app-ish RouteTarget route-params]` | REMOVED | Tied to Dynamic Router | **Yes** |
| `can-change-route?` | `[app-or-component new-route]` | REMOVED | Replaced by `scr/route-denied?` | **Yes** |
| `update-route-params!` | `[app-or-component f & args]` | REMOVED | No RAD-level history; URL sync is automatic | **Yes** |

### New Functions (from statecharts routing, re-exported)

| Function | Signature | Description |
|---|---|---|
| `route-forward!` | `[app-ish]` | Navigate forward in browser history |
| `force-continue-routing!` | `[app-ish]` | Override busy guard for denied route |
| `abandon-route-change!` | `[app-ish]` | Cancel pending route change |
| `route-denied?` | `[app-ish]` | Is routing currently blocked? |
| `active-leaf-routes` | `[app-ish]` | Get set of active leaf route state IDs |

---

## authorization.cljc

| Function | Current Signature | New Signature | Internal Change | Breaking? |
|---|---|---|---|---|
| `start!` | `[app authority-ui-roots]` or `[app authority-ui-roots options]` | Unchanged | `uism/begin!` -> `scf/start!` | No |
| `authenticate!` | `[app-ish provider source-machine-id]` | `[app-ish provider source-session-id]` | `uism/trigger!` -> `scf/send!` | **Yes** (param rename) |
| `authenticate` | `[any-sm-env provider source-machine-id]` | `[env provider source-session-id]` | `uism/trigger` -> statechart event queue send | **Yes** (param rename + env type) |
| `logged-in!` | `[app-ish provider]` | Unchanged | `uism/trigger!` -> `scf/send!` | No |
| `failed!` | `[app-ish provider]` | Unchanged | `uism/trigger!` -> `scf/send!` | No |
| `logout!` | `[app-ish provider]` | Unchanged | `uism/trigger!` -> `scf/send!` | No |
| `verified-authorities` | `[app-ish]` | Unchanged | Reads statechart session data instead of UISM storage | No |
| `machine-id` | `::auth-machine` | Renamed to `session-id` | Constant rename | **Yes** |
| `defauthenticator` macro | `[sym authority-map]` | Rewrite needed | Query changes from UISM asm-id to statechart session queries | **Yes** |
| `redact` | `[env query-result]` | Unchanged | Pure function, no UISM dependency | No |
| `readable?` | `[env a]` | Unchanged | Pure function | No |

---

## Summary of Breaking Changes

### Removed Functions (7)

1. `form/form-will-enter`
2. `form/form-will-leave`
3. `form/form-allow-route-change`
4. `report/report-will-enter`
5. `container/container-will-enter`
6. `routing/install-routing!`
7. `routing/update-route-params!`

### Changed Signatures (6)

1. `form/trigger!` (4-arity: form-ident -> session-id)
2. `form/clear-route-denied!` (simplified to `[app-ish]`)
3. `form/continue-abandoned-route!` (simplified to `[app-ish]`)
4. `routing/route-to!` (new arity pattern)
5. `auth/authenticate!` (source-machine-id -> source-session-id)
6. `auth/authenticate` (env type + param rename)

### Removed Concepts (3)

1. `routing/absolute-path` (Dynamic Router concept)
2. `routing/can-change-route?` (replaced by `route-denied?`)
3. `auth/machine-id` (renamed to `session-id`)

### New Functions (5+)

1. `routing/route-forward!`
2. `routing/force-continue-routing!`
3. `routing/abandon-route-change!`
4. `routing/route-denied?`
5. `routing/active-leaf-routes`

## Open Questions

1. **`route-to!` signature compatibility**: The current `route-to!` has two arities: `[app options-map]` and `[app RouteTarget route-params]`. The statecharts `route-to!` takes `[app-ish target]` or `[app-ish target data]`. Should we maintain a compatibility wrapper that detects the old `[app options-map]` pattern (has `:target` key)?

2. **`start-form!` / `start-report!` / `start-container!` visibility**: These are currently public and called by `*-will-enter`. With statecharts, they are called from `istate` on-entry. Should they remain public (for non-routing usage like embedding a report inline) or become internal?

3. **`form/trigger!` session-id discovery**: The 4-arity `trigger!` currently takes a form ident (which is also the UISM asm-id). With statecharts, the session-id may differ from the form ident. How do callers discover the session-id? Options: (a) convention that session-id equals form ident, (b) store session-id on the component, (c) use `send-to-self!` pattern.

4. **Control URL tracking**: `set-parameter` mutation currently writes to URL via `update-route-params!`. The new pattern needs to be defined: should control values be part of statechart session data (and therefore auto-synced to URL), or should they remain in Fulcro state with a separate URL sync mechanism?

5. **`container-options` env parameter**: Currently takes a UISM env. The statechart env has a different shape. Should we provide an adapter, or update all callers?

## Revision History

- **R1**: Initial spec
- **R2**: Applied critique-round-1 fixes:
  - Fixed `view-mode?` from "Unchanged" to "Breaking internal change" (reads from UISM internals, requires full rewrite)
  - Clarified `clear-route-denied!` / `continue-abandoned-route!` semantic change (no longer form-specific, operates on global routing session)
  - Separated `start-form!` into routed vs. embedded usage (public for embedded, automatic for routed)
  - Added recommendation for `send-to-self!` as preferred replacement for `trigger!`
  - Added verification note for `rendering-env` regarding UISM-derived data
