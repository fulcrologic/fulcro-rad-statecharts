# Spec: Form Namespace Consolidation

**Status**: backlog
**Priority**: P0
**Created**: 2026-02-22
**Owner**: AI
**Depends-on**: (none)
**Phase**: 9 — Namespace Consolidation

## Context

`form.cljc` has satellite files (`form_chart.cljc`, `form_expressions.cljc`, `form_machines.cljc`) that don't need to be separate. The chart and expressions are tightly coupled to the form public API. Additionally, `routing.cljc` contains `form-route-state`, `edit!`, and `create!` which belong in the form namespace.

## Requirements

1. Merge `form_chart.cljc` content into `form.cljc`
2. Merge `form_expressions.cljc` content into `form.cljc`
3. Merge `form_machines.cljc` content into `form.cljc`
4. Move `form-route-state`, `edit!`, `create!` from `routing.cljc` into `form.cljc`
5. Resolve circular dependency: delete `form-fn-registry` atom, `register-form-fn!`, `resolve-form-fn`, and all `register-form-fn!` calls. Expression functions call form functions directly via `declare` forward refs.
6. All functions that were public in satellite files must remain public in `form.cljc` (they're used by `server_paginated_report.cljc`, `incrementally_loaded_report.cljc`, and test files)

## Name Collisions to Resolve

- `all-keys` — identical in `form.cljc` and `form_expressions.cljc`, keep one
- `update-tree*` — identical, keep the public one from `form.cljc`
- `subform-options` / `subform-ui` — private helpers in `form_expressions.cljc` conflict with public fns in `form.cljc`. Rename expression-private versions (e.g., `expr-subform-options` / `expr-subform-ui`) or inline them

## Section Order in Merged File

1. ns declaration (merged requires from all source files)
2. Forward declarations (`declare` for `valid?`, `default-state`, `mark-fields-complete*`, `optional-fields`, `form-key->attribute`, etc.)
3. Constants, dynamic vars
4. Basic utility functions
5. Rendering section (multimethods, render-field, etc.)
6. Form creation/logic (find-fields, optional-fields, default-state, etc.)
7. Expression helper functions (from `form_expressions.cljc`)
8. Expression functions (from `form_expressions.cljc`)
9. Chart definition `form-chart` (from `form_chart.cljc`)
10. Chart fragments (from `form_machines.cljc`)
11. Public API (start-form!, abandon-form!, defsc-form, etc.)
12. Server-side logic (save-form*, mutations)
13. User interaction helpers (save!, undo-all!, etc.)
14. Routing functions: `form-route-state`, `edit!`, `create!` (from `routing.cljc`)

## Requires Changes

**Add** (from satellite files):
- `[com.fulcrologic.statecharts.data-model.operations :as ops]`
- `[com.fulcrologic.statecharts.integration.fulcro.operations :as fops]`
- `[com.fulcrologic.statecharts.chart :refer [statechart]]`
- `[com.fulcrologic.statecharts.convenience :refer [handle on]]`
- `[com.fulcrologic.statecharts.elements :refer [data-model final on-entry script state transition entry-fn exit-fn]]`

**Remove**:
- `[com.fulcrologic.rad.statechart.form-chart :as form-chart]`
- `[com.fulcrologic.rad.statechart.form-expressions :as fex]`

## Symbol Reference Updates

- `form-chart/form-chart` → `form-chart` (local def)
- `fex/*` → direct calls (same file now)
- Macro backtick: `` `form-chart/form-chart `` → `` `form-chart `` (resolves to `com.fulcrologic.rad.statechart.form/form-chart`)

## Affected Files

- `src/main/com/fulcrologic/rad/statechart/form.cljc` — primary target
- `src/main/com/fulcrologic/rad/statechart/form_chart.cljc` — absorbed, then deleted
- `src/main/com/fulcrologic/rad/statechart/form_expressions.cljc` — absorbed, then deleted
- `src/main/com/fulcrologic/rad/statechart/form_machines.cljc` — absorbed, then deleted

## Verification

1. [ ] All `form-chart`, `form-expressions`, `form-machines` content is in `form.cljc`
2. [ ] `form-route-state`, `edit!`, `create!` are in `form.cljc`
3. [ ] No `requiring-resolve` or `form-fn-registry` remains
4. [ ] `form_statechart_spec.cljc` passes after updating requires (`fc/` → `form/`, `fex/` → `form/`)
5. [ ] `form_spec.cljc` still passes
6. [ ] CLJS compiles with 0 warnings
7. [ ] Satellite files deleted
