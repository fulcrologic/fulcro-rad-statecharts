# Critique: API Design and User Experience

**Date**: 2026-02-21
**Scope**: Public API surface of fulcro-rad-statecharts from a user's perspective
**Reviewer**: api-critic agent

## Executive Summary

The library succeeds at its primary goal: users can create RAD forms and reports without understanding statecharts. The `defsc-form` and `defsc-report` macros produce sensible results, the routing layer is clean, and the demo app proves the system works end-to-end. However, the migration story is complicated by duplicate APIs (old stubs coexisting with new implementations), the UISM machinery still visibly leaking through report internals, and several missing features that old RAD users will expect.

**Overall Grade: Good** — Solid foundation, but needs cleanup and documentation polish before release.

---

## 1. Can a User Create a RAD Form Without Understanding Statecharts?

**Answer: Yes, mostly.**

### Walkthrough: Creating a Form

```clojure
(ns myapp.ui.account-form
  (:require
   [myapp.model.account :as account]
   [com.fulcrologic.rad.form :as form]
   [com.fulcrologic.rad.form-options :as fo]))

(form/defsc-form AccountForm [this props]
  {fo/id         account/id
   fo/attributes [account/name account/email account/active?]
   fo/title      "Edit Account"})
```

This is clean and declarative. The user never mentions statecharts. The macro handles:
- Query generation from attributes
- Ident from `fo/id`
- Statechart registration via `sfro/statechart` and `sfro/initialize :always`
- Busy guard via `sfro/busy?` (auto-generated `make-form-busy-fn`)
- Form state tracking via `fs/form-config-join`

**Positive**: The attribute-centric declaration is preserved. A user familiar with old RAD forms will recognize this immediately.

**Issue**: `fo/route-prefix` is still defined in form_options.cljc but the macro no longer generates `:route-segment` or `:will-enter`. Its purpose in the new world is unclear. The demo uses it (`fo/route-prefix "account"`) but it's not consumed by the macro's `convert-options`. This is confusing — either document what it does now, or remove it.

### Triggering Form Actions

```clojure
;; Save, undo, cancel — all work via rendering env
(form/save! {::form/master-form this})
(form/undo-all! {::form/master-form this})
(form/cancel! {::form/master-form this})
```

These are clean and unchanged from old RAD. They internally delegate to `scf/send!` with session IDs, but the user never sees that.

### Starting a Form Programmatically

```clojure
(form/start-form! app entity-id AccountForm {:on-saved some-txn})
```

This is well-documented and intuitive. The params map supports `:on-saved`, `:on-cancel`, `:on-save-failed`, and `:embedded?`.

**Verdict**: Form creation is clean. A RAD user can be productive without any statechart knowledge.

---

## 2. Can a User Create a RAD Report Without Understanding Statecharts?

**Answer: Yes.**

### Walkthrough: Creating a Report

```clojure
(report/defsc-report InventoryReport [this props]
  {ro/columns          [item/name category/label item/price item/in-stock]
   ro/row-pk           item/id
   ro/source-attribute :item/all-items
   ro/run-on-mount?    true
   ro/paginate?        true
   ro/page-size        20})
```

Clean, declarative, no statechart concepts visible. The macro generates the query, ident, initial-state, and wires up `sfro/initialize :once` and `sfro/statechart`.

### Report Actions

```clojure
(report/run-report! this)
(report/sort-rows! this column-attr)
(report/filter-rows! this)
(report/goto-page! this 3)
```

All clean, all delegate to `scf/send!` internally.

**Issue**: `report/start-report!` uses `::machine` as the option key to find a custom statechart:

```clojure
(let [machine-key (or (comp/component-options report-class ::machine) ::report-chart)
```

But the macro generates `sfro/statechart` (or `sfro/statechart-id`) from `ro/statechart`. The `::machine` key is the old deprecated name. This means: if a user sets `ro/statechart` on a report and then calls `start-report!` directly (outside of routing), the custom chart will be **ignored** because `start-report!` reads `::machine`, not `ro/statechart`. This is a bug.

**Verdict**: Report creation is clean. The statechart abstraction is well-hidden.

---

## 3. Is Routing Clean?

**Answer: Yes, with caveats.**

### The Good

The routing API in the demo is exemplary:

```clojure
;; Define routes declaratively
(def routing-chart
  (statechart {:initial :state/route-root}
    (scr/routing-regions
      (scr/routes {:id :state/root :routing/root `Routes}
        (scr/rstate {:route/target `LandingPage})
        (rroute/report-route-state {:route/target InventoryReport})
        (rroute/form-route-state {:route/target AccountForm
                                  :route/params #{:account/id}})))))

;; Navigate
(scr/route-to! this InventoryReport)
(rroute/create! this AccountForm)
(rroute/edit! this AccountForm entity-id)
```

The `form-route-state` and `report-route-state` helpers are excellent abstractions. They handle on-entry/on-exit lifecycle without the user writing statechart expressions.

### The Caveats

1. **Two routing APIs**: Users must choose between `rroute/route-to!` (RAD compat wrapper) and `scr/route-to!` (direct statecharts routing). The demo itself uses BOTH — `scr/route-to!` for reports and `rroute/create!` for forms. This is confusing. The RAD routing ns should be the single entry point, or the statecharts routing should be the recommended API. Pick one.

2. **`form/view!`, `form/edit!`, `form/create!` are STUBBED** in `form.cljc` (lines 1558-1594) — they just log warnings. But `routing/edit!` and `routing/create!` ARE implemented (lines 119-132). This means there are TWO create functions with the same purpose, one broken and one working, in different namespaces. Users will find the broken one first since it's in the form namespace.

3. **Route denial UX**: The demo shows route denial handling in Root. This is good, but there's no built-in helper component for this. Every user will need to write their own modal. A `(rroute/route-denied-modal app-ish {:on-cancel fn :on-continue fn})` helper or at least a documented pattern would reduce boilerplate.

4. **`back!` is thin**: `routing/back!` delegates to `scr/route-back!`, but there's no `routing/forward!` — only `routing/route-forward!`. The naming inconsistency (`back!` vs `route-forward!`) is a minor paper cut.

---

## 4. Do the Macros Produce Sensible Results?

### defsc-form

**Good**:
- Generates query from attributes including picker caches, form-state config, active-remotes
- Generates ident from `fo/id`
- Auto-creates `sfro/busy?`, `sfro/initialize`, `sfro/statechart`
- Supports hooks mode
- Emits compile-time warning if `:will-enter` is specified

**Issue**: The macro still passes options to `convert-options` at runtime, not compile time. This means errors like missing `fo/id` or `fo/attributes` are runtime errors, not compile-time. The old RAD had the same limitation, but since this is a rewrite, it would be an improvement to catch these at compile time.

### defsc-report

**Good**:
- Generates query, ident, initial-state
- Auto-generates row component if `BodyItem` not specified
- Generates `sfro/initialize :once` and `sfro/statechart`

**Issue**: The macro emits `sfro/statechart` from `ro/statechart`, but `start-report!` reads `::machine` (see issue in section 2).

### defsc-container

**Good**:
- Generates query joining all children
- Generates initial-state that initializes all child report state
- Clean `sfro/initialize :once` integration

**Minor**: The docstring says "If you want this to be a route target, then you must add `:route-segment`" — but `:route-segment` is a Dynamic Router concept that no longer applies. This should be updated.

---

## 5. Is `form-route-state` / `report-route-state` the Right Abstraction?

**Answer: Yes, this is the best part of the new API.**

These functions perfectly encapsulate the "routing lifecycle hooks for RAD components" pattern. They:
- Create an `scr/rstate` with the right on-entry/on-exit
- Start/stop the form's statechart on route entry/exit
- Handle cleanup (abandon form on exit)
- Accept standard `rstate` options (`:route/target`, `:route/params`)

The pattern is also extensible — users can create their own route-state helpers for custom components.

**One suggestion**: Add a `container-route-state` helper for consistency, even if it's trivial:

```clojure
(defn container-route-state [props]
  (scr/rstate props
    (entry-fn [{:fulcro/keys [app]} _data _event-name event-data]
      (container/start-container! app (comp/registry-key->class (:route/target props)) event-data)
      nil)))
```

---

## 6. Minimal Code for a Working RAD App

Based on the demo, the minimal setup is:

```clojure
;; 1. Define attributes (unchanged from old RAD)
;; 2. Define forms/reports with defsc-form/defsc-report (nearly unchanged)
;; 3. Define routing chart (NEW)
;; 4. Bootstrap:

(rad-app/install-statecharts! app {:event-loop? true})
(rad-app/start-routing! app routing-chart)
(rad-app/install-url-sync! app)  ;; CLJS only
(swap! (::app/state-atom app) assoc :ui/ready? true)
```

**Comparison to old RAD**:
```clojure
;; Old RAD bootstrap:
(rad-app/install-ui-controls! app sui/all-controls)
(app/mount! app Root "app")
;; (routing was implicit via Dynamic Router)
```

**Assessment**: The new bootstrap is more explicit (3 calls instead of 1), but each call is well-named and documented. The explicit routing chart declaration is actually an improvement — it makes the route structure visible and auditable.

**Missing**: `install-ui-controls!` still exists in application.cljc but the demo doesn't use it (headless plugin auto-registers via multimethods). This should be documented — when do users need it vs. when do plugins auto-register?

---

## 7. Are the Option Namespaces Well-Organized?

### form_options.cljc

**Good**: Well-documented with docstrings for every option. The `defoption` pattern provides both documentation and a stable keyword reference.

**Issues**:
- `fo/route-prefix` — purpose unclear in new system (see section 1)
- `fo/cancel-route` — docstring references `:back` and route history, but the old routing/history system is removed. Does this still work? The form chart would need to handle it.
- `fo/machine` — marked deprecated, suggests `statechart`. Good.

### report_options.cljc

**Good**: Consistent with form_options.

**Issue**: `ro/route` still exists — same confusion as `fo/route-prefix`.

### General

The split between `form_options`, `form_render_options`, `control_options`, and `report_options` is reasonable. However, users now also need to know about `sfro` options (`sfro/initialize`, `sfro/busy?`, `sfro/statechart`). These are NOT in the RAD options files — they're in the statecharts library. This creates a documentation gap. Users will look in `fo/*` for all form options but won't find the routing-integration ones there.

**Recommendation**: Add `fo/initialize`, `fo/busy?` as aliases that delegate to `sfro/*`, or at minimum add docstring cross-references in form_options.cljc.

---

## 8. Old vs New API Comparison

### What's Better

| Aspect | Old RAD | New RAD | Verdict |
|--------|---------|---------|---------|
| Routing declaration | Implicit via DR route-segment | Explicit statechart | **Better** — visible, auditable |
| Route lifecycle | will-enter/will-leave + allow-route-change? | sfro/initialize + sfro/busy? | **Better** — declarative |
| Form/report startup | UISM begin! inside will-enter | start-form!/start-report! | **Better** — explicit |
| Route denial | DR-specific | Built into routing statechart | **Better** — unified |
| Custom form behavior | Override UISM machine | Override statechart | **Better** — statecharts more expressive |
| Rendering plugin | install-*! functions | defmethod multimethods | **Better** — standard Clojure |
| Testability | CLJS-only in browser | CLJC headless | **Much better** |

### What's Worse

| Aspect | Issue |
|--------|-------|
| Bootstrap complexity | 3 calls instead of implicit setup |
| Namespace knowledge | Must know `rroute`, `scr`, `sfro`, `scf` in addition to `form`, `report` |
| Routing chart boilerplate | Must manually declare every route target |
| Authorization | Completely removed (was in old RAD) |
| Blob/file upload | Completely removed |
| Dynamic generation | `dynamic.generator` removed |
| Pathom 3 | No support (only Pathom 2) |

### What's Missing (API Gaps)

1. **`form/view!`** — Stubbed, not functional. Old RAD had read-only form viewing.
2. **`form/edit!`** and **`form/create!`** in `form.cljc` — Stubbed. Working versions exist in `routing.cljc` but under different signatures.
3. **Authorization** — Entire auth system removed (authorization.cljc, simple-authorization). No replacement.
4. **Blob/file upload** — Removed entirely. No replacement.
5. **Dynamic form/report generation** — `dynamic.generator` removed. No replacement.
6. **Route history tracking in reports** — Multiple TODO comments about re-adding history-based parameter initialization. Currently no-op.
7. **`rad_hooks`** — Hook system removed. No replacement.
8. **`container-route-state`** — Missing (form and report have route-state helpers, container doesn't).

### Confusing Changes

1. **`form/create!` vs `routing/create!`** — Both exist, only the routing version works. Users will find the form version first.
2. **`::machine` vs `ro/statechart`** — Macro writes `sfro/statechart`, but `start-report!` reads `::machine`. Bug.
3. **`fo/route-prefix`** and `ro/route` — Still defined but no longer consumed by macros for route-segment generation. Purpose unclear.
4. **`scr/route-to!` vs `rroute/route-to!`** — Both work, demo uses both. Which should users prefer?
5. **UISM code still in report.cljc** — `report-machine`, `global-events`, all the UISM handler functions are still present. This is confusing for someone reading the source to understand how reports work.

---

## 9. Recommendations

### Critical (Must Fix Before Release)

1. **Fix `start-report!` to read `ro/statechart`** instead of (or in addition to) `::machine`. This is a correctness bug — custom report statecharts set via the macro-supported `ro/statechart` option are silently ignored.

2. **Remove or redirect `form/view!`, `form/edit!`, `form/create!` stubs**. Either:
   - (a) Implement them by delegating to `routing/edit!`, `routing/create!`, or
   - (b) Make them throw with a clear message pointing to `routing/edit!` and `routing/create!`
   Current behavior (logging a warning and doing nothing) is the worst option — silent failure.

3. **Pick one routing API and document it clearly**. Recommendation: `rroute/route-to!` should be the blessed API for RAD users, with `scr/route-to!` documented as the lower-level alternative. Update the demo to be consistent.

### Important (Should Fix)

4. **Clean up `fo/route-prefix` and `ro/route`**. Either:
   - Document their new purpose (URL codec uses them?)
   - Remove them if they're dead options

5. **Add `container-route-state`** helper to `routing.cljc` for consistency.

6. **Add cross-references** in form_options.cljc and report_options.cljc pointing to `sfro/*` options for routing integration.

7. **Document when `install-ui-controls!` is needed** vs. when multimethod registration is sufficient.

8. **Remove UISM report-machine** and associated UISM handler code from `report.cljc`. It's dead code that confuses readers.

### Suggested (Nice to Have)

9. Add a route-denied modal helper or documented pattern.

10. Create a migration guide documenting the old-to-new API mapping, especially for:
    - `will-enter` / `will-leave` → `sfro/initialize` / `sfro/busy?`
    - `form/create!` → `routing/create!`
    - `install-routing!` → `start-routing!`
    - UISM machine overrides → statechart overrides

11. Consider adding `rroute/view!` as a convenience function alongside `rroute/edit!` and `rroute/create!`.

12. Rename `routing/route-forward!` to `routing/forward!` for symmetry with `routing/back!`.

---

## 10. Headless Testing API

The headless plugin (`rad.rendering.headless.plugin`) registers renderers via multimethods, which is clean. The demo shows it working for forms and reports.

**Gap**: There's no documentation on how a user would write their own headless tests for a RAD app. The test infrastructure exists (E2E tests in the test suite prove it), but there's no user-facing guide explaining:
- How to set up a headless test app
- How to simulate routing
- How to verify form state
- How to trigger form save and verify results

This is a significant documentation gap, since headless testing is one of the key advantages of the new system.

---

## Metrics Summary

| Metric | Value | Notes |
|--------|-------|-------|
| Form API compatibility | ~85% | Core form CRUD works; view!, edit!, create! in form ns broken |
| Report API compatibility | ~90% | Works well; `::machine` bug is the main issue |
| Container API compatibility | ~90% | Works; missing route-state helper |
| Routing API compatibility | ~60% | Completely different paradigm (improvement, but breaking) |
| Missing features | 7 | Auth, blob, dynamic gen, hooks, Pathom3, view!, history params |
| Namespace count for basic usage | 6-7 | form, fo, report, ro, routing, scr, sfro |
| Bootstrap steps | 3-4 | install-statecharts!, start-routing!, install-url-sync!, mount |
