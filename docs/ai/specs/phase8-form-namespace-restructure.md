# Spec: Form Namespace Restructure

**Status**: backlog
**Priority**: P0
**Created**: 2026-02-22
**Owner**: AI
**Depends-on**: phase8-upstream-impl-extraction, phase8-deps-and-identical-cleanup
**Phase**: 8 — Library Restructuring

## Context

Currently the statechart-specific form code lives at `com.fulcrologic.rad.form`, which conflicts with the upstream fulcro-rad namespace of the same name. This spec moves all statechart form code to `com.fulcrologic.rad.statechart.form` (note: full word `statechart`, not abbreviated).

After this restructure:
- `com.fulcrologic.rad.form` — provided by fulcro-rad (UISM engine), not in this project
- `com.fulcrologic.rad.statechart.form` — provided by this project (statecharts engine)
- `com.fulcrologic.rad.form.impl` — provided by fulcro-rad (shared pure functions)

Users switch engines by changing one require: `rad.form` → `rad.statechart.form`.

## Requirements

1. Create `com.fulcrologic.rad.statechart.form` namespace that:
   - Re-exports ~40 shared functions from `com.fulcrologic.rad.form.impl` via `def` aliases
   - Contains all statechart-specific engine functions: `save!`, `cancel!`, `undo-all!`, `start-form!`, `add-child!`, `delete-child!`, `input-changed!`, `input-blur!`, `mark-all-complete!`, `view-mode?`, `read-only?`, `form-busy?`, `make-form-busy-fn`, `abandon-form!`, `clear-route-denied!`, `continue-abandoned-route!`
   - Contains the `defsc-form` macro (delegates to `impl/defsc-form*` with statechart-specific `convert-options`)
   - Contains `standard-controls` (engine-specific because it references `save!`, `cancel!`, etc.)

2. Move `com.fulcrologic.rad.form-chart` → `com.fulcrologic.rad.statechart.form-chart`

3. Move `com.fulcrologic.rad.form-expressions` → `com.fulcrologic.rad.statechart.form-expressions`

4. Move `com.fulcrologic.rad.form-machines` → `com.fulcrologic.rad.statechart.form-machines`

5. Delete the old `com.fulcrologic.rad.form` from this project (it now comes from fulcro-rad dependency)

6. All internal references between form namespaces must be updated

## File Moves

| Current Path | New Path |
|-------------|----------|
| `src/main/.../rad/form.cljc` | `src/main/.../rad/statechart/form.cljc` |
| `src/main/.../rad/form_chart.cljc` | `src/main/.../rad/statechart/form_chart.cljc` |
| `src/main/.../rad/form_expressions.cljc` | `src/main/.../rad/statechart/form_expressions.cljc` |
| `src/main/.../rad/form_machines.cljc` | `src/main/.../rad/statechart/form_machines.cljc` |

Note: The `_` in filenames corresponds to `-` in namespace names. So `statechart/form_chart.cljc` → `com.fulcrologic.rad.statechart.form-chart`.

## Re-export Pattern

```clojure
(ns com.fulcrologic.rad.statechart.form
  "Statecharts-based RAD form engine. Provides the same public API as
   com.fulcrologic.rad.form but uses statecharts instead of UISM.

   Shared functions are re-exported from com.fulcrologic.rad.form.impl.
   Engine-specific functions (save!, cancel!, start-form!, etc.) are
   defined directly in this namespace."
  (:require
    [com.fulcrologic.rad.form.impl :as impl]
    [com.fulcrologic.statecharts.integration.fulcro :as scf]
    [com.fulcrologic.rad.statechart.session :as session]
    ;; ... other requires
    ))

;; === Re-exports from form.impl (shared, engine-agnostic) ===
(def rendering-env impl/rendering-env)
(def valid? impl/valid?)
(def invalid? impl/invalid?)
(def field-label impl/field-label)
(def master-form impl/master-form)
(def master-form? impl/master-form?)
;; ... ~40 total def aliases

;; === Engine-specific functions ===
(defn save! [form-instance]
  ;; statecharts-specific implementation
  ...)

(defn cancel! [form-instance]
  ...)

;; === Macro ===
#?(:clj
   (defmacro defsc-form [& args]
     (impl/defsc-form* &env args convert-options)))
```

## Namespace Dependency Chain

```
statechart.form → form.impl (re-exports)
               → statechart.form-expressions (for start-form! internals)
               → statechart.session (ident->session-id)
               → scf (statecharts integration)

statechart.form-chart → statechart.form-expressions

statechart.form-expressions → form.impl (shared helpers)
                            → scf, fops, ops (statechart operations)
                            → NOT statechart.form (avoids circular dep)

statechart.form-machines → statechart.form-expressions (chart fragments)
```

## Approach

1. Create directory `src/main/com/fulcrologic/rad/statechart/`
2. Copy `form.cljc` → `statechart/form.cljc`, update namespace to `com.fulcrologic.rad.statechart.form`
3. Refactor: Remove functions that belong in `form.impl`, replace with `def` aliases
4. Move `form_chart.cljc` → `statechart/form_chart.cljc`, update namespace
5. Move `form_expressions.cljc` → `statechart/form_expressions.cljc`, update namespace
6. Move `form_machines.cljc` → `statechart/form_machines.cljc`, update namespace
7. Update all internal cross-references
8. Delete old files at `rad/form.cljc`, `rad/form_chart.cljc`, `rad/form_expressions.cljc`, `rad/form_machines.cljc`
9. Update all files that require the old namespaces (grep for `:as form]` etc.)
10. Verify compilation

**Note**: Verification covers source compilation only (`clj -e "(require ...)"` for source namespaces). Test compilation will be broken until the test-migration spec is complete.

## Affected Modules

- `src/main/com/fulcrologic/rad/statechart/form.cljc` — NEW (moved + restructured)
- `src/main/com/fulcrologic/rad/statechart/form_chart.cljc` — NEW (moved)
- `src/main/com/fulcrologic/rad/statechart/form_expressions.cljc` — NEW (moved)
- `src/main/com/fulcrologic/rad/statechart/form_machines.cljc` — NEW (moved)
- `src/main/com/fulcrologic/rad/form.cljc` — DELETE (comes from fulcro-rad dep)
- `src/main/com/fulcrologic/rad/form_chart.cljc` — DELETE (moved)
- `src/main/com/fulcrologic/rad/form_expressions.cljc` — DELETE (moved)
- `src/main/com/fulcrologic/rad/form_machines.cljc` — DELETE (moved)
- All files requiring `rad.form` (updated to `rad.statechart.form` or `rad.form.impl`)

## Verification

1. [ ] `com.fulcrologic.rad.statechart.form` namespace compiles
2. [ ] All re-exported functions are accessible via `(require '[com.fulcrologic.rad.statechart.form :as form])`
3. [ ] `form/save!`, `form/cancel!`, `form/valid?`, `form/rendering-env` etc. all resolve
4. [ ] `form/defsc-form` macro works and produces correct component
5. [ ] No circular dependencies between statechart.form and statechart.form-expressions
6. [ ] Old `com.fulcrologic.rad.form` is NOT present in this project's source tree
7. [ ] All tests that used `rad.form` are updated (see test-migration spec)
8. [ ] Statechart form chart still functions correctly after namespace move
