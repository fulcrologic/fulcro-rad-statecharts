# Cleanup Analysis: fulcro-rad-statecharts as Add-On Library

**Goal**: Convert this project from a *fork/rewrite* of fulcro-rad into an *add-on* library that depends on fulcro-rad rather than replacing it.

## Design Principles

1. **Zero public API changes to fulcro-rad** — existing users see no difference
2. **Minimal code replication** — shared logic extracted to `impl` namespaces, re-exported by both engines
3. **Reuse everything good** — numerics, date/time, attributes, rendering multimethods all come from fulcro-rad dependency
4. **Eliminate cruft** — internal machine helpers are private, not public API
5. **100% backward compatibility** — UISM users change nothing
6. **Minimal confusion** — one require swap to switch engines; compile-time validation catches engine/option mismatches

---

## 1. Forked Files to Delete (Come From Dependency)

These files exist in both projects and are deleted from this project — upstream versions come from the fulcro-rad dependency. Some are byte-for-byte identical; others diverged (whitespace, UISM removal stubs, or upstream evolved past our fork). All are safe to delete because this library does not provide auth, pathom3, or UISM — those come from upstream. Statechart-specific additions (e.g. `install-statecharts!` in `application.cljc`) are extracted to this library's own namespaces before deletion.

| File | Namespace |
|------|-----------|
| `attributes.cljc` | `com.fulcrologic.rad.attributes` |
| `attributes_options.cljc` | `com.fulcrologic.rad.attributes-options` |
| `form_render.cljc` | `com.fulcrologic.rad.form-render` |
| `report_render.cljc` | `com.fulcrologic.rad.report-render` |
| `options_util.cljc` | `com.fulcrologic.rad.options-util` |
| `picker_options.cljc` | `com.fulcrologic.rad.picker-options` |
| `ids.cljc` | `com.fulcrologic.rad.ids` |
| `locale.cljc` | `com.fulcrologic.rad.locale` |
| `errors.cljc` | `com.fulcrologic.rad.errors` |
| `form_render_options.cljc` | `com.fulcrologic.rad.form-render-options` |
| `report_render_options.cljc` | `com.fulcrologic.rad.report-render-options` |
| `application.cljc` | `com.fulcrologic.rad.application` |
| `authorization.cljc` | `com.fulcrologic.rad.authorization` |
| `resolvers.cljc` | `com.fulcrologic.rad.resolvers` |
| `resolvers_common.cljc` | `com.fulcrologic.rad.resolvers-common` |
| `debugging.cljc` | `com.fulcrologic.rad.debugging` |
| `registered_maps.clj` | `com.fulcrologic.rad.registered-maps` |
| `pathom.clj` | `com.fulcrologic.rad.pathom` |
| `pathom_async.clj` | `com.fulcrologic.rad.pathom-async` |
| `pathom_common.clj` | `com.fulcrologic.rad.pathom-common` |
| `middleware/autojoin.cljc` | `com.fulcrologic.rad.middleware.autojoin` |
| `middleware/autojoin_options.cljc` | `com.fulcrologic.rad.middleware.autojoin-options` |
| `middleware/save_middleware.cljc` | `com.fulcrologic.rad.middleware.save-middleware` |
| `type_support/date_time.cljc` | `com.fulcrologic.rad.type-support.date-time` |
| `type_support/decimal.cljc` | `com.fulcrologic.rad.type-support.decimal` |
| `type_support/integer.cljc` | `com.fulcrologic.rad.type-support.integer` |
| `type_support/js_date_formatter.cljs` | `com.fulcrologic.rad.type-support.js-date-formatter` |
| `type_support/js_joda_base.cljs` | `com.fulcrologic.rad.type-support.js-joda-base` |
| `type_support/ten_year_timezone.cljs` | `com.fulcrologic.rad.type-support.ten-year-timezone` |

---

## 2. Files That Were MODIFIED (The Core of the Add-On)

### 2A. Heavy Modifications (statechart machinery replaces UISM)

| Current File | What Changed |
|-------------|--------------|
| **`form.cljc`** | ~40 functions identical, ~20 functions modified (all `uism/trigger!` -> `scf/send!`), added `render-element` defmulti replacing `render-fn`, removed `form-machine`, removed DR routing hooks (`will-enter`, `will-leave`), removed imperative `install-*-renderer!` functions. Added `form-busy?`, `make-form-busy-fn`. |
| **`report.cljc`** | Similar pattern. ~25 identical functions (rendering, querying, formatting), ~15 modified (trigger -> send). Removed `report-machine`, removed DR routing. Added `report-session-id`. |
| **`container.cljc`** | Nearly complete rewrite. UISM container-machine replaced by statechart. DR routing replaced. |
| **`control.cljc`** | `render-control` changed from map-dispatch to defmulti. `run!` changed from uism/trigger to scf/send. |
| **`routing.cljc`** | Complete rewrite. Old: wraps Fulcro Dynamic Router + RADRouter protocol. New: delegates to statecharts routing. Entirely different API surface. |

### 2B. Options Files Modified (callback signature changes)

| Current File | What Changed |
|-------------|--------------|
| **`form_options.cljc`** | Trigger callback signatures changed from UISM env threading to statechart ops vectors. `machine` deprecated, `statechart` option added. Deprecated aliases removed. |
| **`report_options.cljc`** | Same pattern. UISM callback signatures -> statechart patterns. `machine` deprecated, `statechart` added. Deprecated aliases removed. |
| **`container_options.cljc`** | Needs verification - likely similar changes. |
| **`control_options.cljc`** | Needs verification. |

---

## 3. NEW Files (Statecharts-Only, No Original Equivalent)

| File | Namespace | Purpose |
|------|-----------|---------|
| `form_chart.cljc` | `com.fulcrologic.rad.form-chart` | Form statechart definition (replaces `form-machine`) |
| `form_expressions.cljc` | `com.fulcrologic.rad.form-expressions` | Expression functions for form statechart |
| `form_machines.cljc` | `com.fulcrologic.rad.form-machines` | Reusable form statechart fragments |
| `report_chart.cljc` | `com.fulcrologic.rad.report-chart` | Report statechart definition (replaces `report-machine`) |
| `report_expressions.cljc` | `com.fulcrologic.rad.report-expressions` | Expression functions for report statechart |
| `container_chart.cljc` | `com.fulcrologic.rad.container-chart` | Container statechart definition |
| `container_expressions.cljc` | `com.fulcrologic.rad.container-expressions` | Expression functions for container statechart |
| `sc/session.cljc` | `com.fulcrologic.rad.sc.session` | Ident <-> session-id bidirectional conversion |
| `rendering/headless/plugin.cljc` | `com.fulcrologic.rad.rendering.headless.plugin` | Headless rendering (test helper) |
| `server_paginated_report.cljc` | `com.fulcrologic.rad.server-paginated-report` | Server-paginated report variant (statechart) |
| `incrementally_loaded_report.cljc` | `com.fulcrologic.rad.incrementally-loaded-report` | Incremental loading report variant (statechart) |

---

## 4. The UISM-to-Statecharts Conversion Pattern

The conversion follows a consistent mechanical pattern:

### Communication
```clojure
;; UISM:       (uism/trigger! app-ish ident :event/foo data)
;; Statechart: (scf/send! app-ish session-id :event/foo data)
```
Session IDs are derived from idents via `session/ident->session-id`.

### State Machine Start
```clojure
;; UISM:       (uism/begin! app machine-def ident actors event-data)
;; Statechart: (scf/register-statechart! app key chart)
;;             (scf/start! app {:machine key :session-id sid :data {...}})
```

### Handler Functions -> Expression Functions
```clojure
;; UISM handler:   (fn [uism-env] ...) ; returns modified uism-env
;; SC expression:  (fn [env data event-name event-data] ...) ; returns ops vector
```

### Routing
- Old: Fulcro Dynamic Router (`will-enter`, `will-leave`, `route-segment`)
- New: Statecharts routing (`sfro/statechart`, `sfro/initialize`, `sfro/busy?`)

### Rendering Registration
- Old: Imperative `install-*-renderer!` into runtime atom maps
- New: `defmethod` on multimethods (`render-element`, `render-control`, `fr/render-field`)

---

## 5. Rendering Architecture Clarification

**Already in fulcro-rad (form_render.cljc / report_render.cljc)**:
- `render-form`, `render-header`, `render-fields`, `render-footer`, `render-field` -- all multimethods dispatching on `[key style]` via `render-hierarchy`
- `render-report`, `render-body`, `render-row`, `render-controls` -- same pattern

**What the original RAD form.cljc ALSO had (map-based, NOT multimethod)**:
- `render-fn` -- looks up structural element renderers from a nested map in the Fulcro runtime atom (`::element->style->layout`)
- `install-form-container-renderer!`, `install-form-body-renderer!`, `install-form-ref-renderer!`, `install-field-renderer!` -- imperative functions to populate that map

**What the statecharts version changed**:
- Added `render-element` defmulti (dispatching on `[element layout-style]`) to replace the map-based `render-fn` for *structural* elements (`:form-container`, `:form-body-container`, `:ref-container`)
- Removed `render-fn`, `form-container-renderer`, `form-layout-renderer`, and all `install-*-renderer!` functions
- This brought structural element rendering in line with the field/form rendering that already used multimethods

**Decision**: The `render-element` multimethod unification is a good improvement independent of statecharts. It should likely be backported to fulcro-rad so both engines benefit. However, this is a minor refactor and can be done separately.

---

## 6. Architecture: Zero Change to fulcro-rad Public API + Statecharts Add-On

### Core Idea

**fulcro-rad** gets one internal refactor: extract ~40 pure functions from `form.cljc` and `report.cljc` into `form.impl` / `report.impl`. The existing public vars become pointers to the impl. **Zero public API change** — existing code compiles and runs identically.

**fulcro-rad-statecharts** (this library) is a separate add-on that:
- Depends on fulcro-rad (gets the impl namespaces + all shared code)
- Provides `rad.statechart.form` — re-exports the ~40 shared functions from `form.impl` PLUS statecharts engine functions (`save!`, `start-form!`, etc.)
- Provides `rad.statechart.form-options` — statechart-specific option keys only
- Provides statechart definitions, expressions, routing, session — all purely additive
- The `defsc-form` macro in this library does **compile-time options validation**: catches wrong-engine option keys early

Both libraries coexist on the classpath with no conflict — different namespaces, no shadowing.

### DCE Strategy

- `com.fulcrologic.rad.form` requires UISM, never touches statecharts
- `com.fulcrologic.rad.statechart.form` requires statecharts, never touches UISM
- `com.fulcrologic.rad.form.impl` requires neither (pure shared logic)
- DCE eliminates whichever top-level namespace you don't require

### Namespace Layout

```
=== IN FULCRO-RAD (upstream, minimal internal refactor) ===

;; PRIVATE SHARED IMPLEMENTATION (extracted, not new API)
com.fulcrologic.rad.form.impl              ;; ~40 pure functions, server mutations,
                                            ;; defsc-form* helper, convert-options-base,
                                            ;; render-element multimethod, rendering env
                                            ;; NO UISM deps, NO statecharts deps

com.fulcrologic.rad.report.impl            ;; same pattern for reports

;; PUBLIC: UISM VERSION (existing, UNCHANGED public API)
com.fulcrologic.rad.form                   ;; existing vars now point to form.impl
                                            ;; form-machine, start-form!, save!, cancel!, etc.
                                            ;; defsc-form macro (delegates to impl/defsc-form*)
                                            ;; ZERO public API change
com.fulcrologic.rad.form-options           ;; existing option keys, UNCHANGED
com.fulcrologic.rad.report                 ;; same pattern
com.fulcrologic.rad.report-options

;; Everything else in fulcro-rad: UNCHANGED


=== IN FULCRO-RAD-STATECHARTS (this library, add-on) ===

;; PUBLIC: STATECHARTS FORM
com.fulcrologic.rad.statechart.form              ;; re-exports ~40 from form.impl
                                                  ;; PLUS statechart engine functions:
                                                  ;; save!, cancel!, start-form!, etc.
                                                  ;; defsc-form macro with compile-time
                                                  ;; options validation
com.fulcrologic.rad.statechart.form-options      ;; ONLY statechart-specific option keys
                                                  ;; (sfo/triggers, sfo/statechart, etc.)
com.fulcrologic.rad.statechart.form-chart        ;; form statechart definition
com.fulcrologic.rad.statechart.form-expressions  ;; expression functions for form statechart

;; PUBLIC: STATECHARTS REPORT
com.fulcrologic.rad.statechart.report            ;; same pattern as form
com.fulcrologic.rad.statechart.report-options
com.fulcrologic.rad.statechart.report-chart
com.fulcrologic.rad.statechart.report-expressions

;; STATECHARTS ROUTING
com.fulcrologic.rad.statechart.routing           ;; route-to!, edit!, create!, rstate, istate

;; STATECHARTS SUPPORT
com.fulcrologic.rad.statechart.session           ;; ident <-> session-id

;; CONTAINER (deferred)
com.fulcrologic.rad.statechart.container
com.fulcrologic.rad.statechart.container-chart
com.fulcrologic.rad.statechart.container-expressions

;; RENDERING (engine-agnostic, depends on shared form-render/report-render multimethods)
com.fulcrologic.rad.rendering.headless.plugin    ;; headless rendering for testing
```

### How the Macro Sharing Works

```clojure
;; form/impl.cljc - the shared macro helper (a function, not a macro)
#?(:clj
   (defn defsc-form* [env args convert-options-fn]
     ;; ~45 lines of macro emit logic
     ;; Calls convert-options-fn (passed in) for engine-specific routing options
     ))

;; In fulcro-rad form.cljc (UISM) - thin macro wrapper
#?(:clj
   (defmacro defsc-form [& args]
     (impl/defsc-form* &env args convert-options)))  ;; UISM convert-options

;; In this library statechart/form.cljc - thin macro wrapper with validation
#?(:clj
   (defmacro defsc-form [& args]
     (validate-options! args)                         ;; compile-time check for wrong-engine keys
     (impl/defsc-form* &env args convert-options)))   ;; SC convert-options
```

### Compile-Time Options Validation

The `defsc-form` macro in this library inspects the options map at compile time:
- If it sees `fo/triggers` (UISM callback signature), it emits a compile error directing the user to use `sfo/triggers` instead
- If it sees `fo/machine`, it emits a compile error directing to `sfo/statechart`
- Shared keys like `fo/id`, `fo/attributes`, `fo/subforms` pass through — they mean the same thing in both engines

This catches engine mismatches early with clear error messages, not silent runtime misbehavior.

### Re-export Strategy

Manual `def` aliases for the ~40 shared functions:

```clojure
;; In statechart/form.cljc:
(def rendering-env impl/rendering-env)
(def valid? impl/valid?)
(def field-label impl/field-label)
(def master-form impl/master-form)
;; ... etc
```

- Works identically in CLJ and CLJS
- DCE eliminates unused aliases
- One-time cost (these functions are stable)
- Explicit and greppable

### User Experience

**UISM user** (existing, zero changes):
```clojure
(:require [com.fulcrologic.rad.form :as form]
          [com.fulcrologic.rad.form-options :as fo])

(form/defsc-form AccountForm [this props]
  {fo/id           account/id
   fo/attributes   [account/name account/email]
   fo/triggers     {:on-change (fn [uism-env ident k old new] uism-env)}})
```

**Statecharts user** (new):
```clojure
(:require [com.fulcrologic.rad.statechart.form :as form]
          [com.fulcrologic.rad.form-options :as fo]
          [com.fulcrologic.rad.statechart.form-options :as sfo])

(form/defsc-form AccountForm [this props]
  {fo/id           account/id
   fo/attributes   [account/name account/email]
   sfo/triggers    {:on-change (fn [env data ident k old new] [ops ...])}})
```

Same `form/save!`, `form/cancel!`, `form/rendering-env`, etc. — complete API from one namespace either way. Shared option keys use `fo/`, statechart-specific keys use `sfo/`.

### Migration

**Existing UISM users**: Zero changes. `com.fulcrologic.rad.form` is unchanged.

**Statecharts users**: Change requires from `com.fulcrologic.rad.form` to `com.fulcrologic.rad.statechart.form`, add `com.fulcrologic.rad.statechart.form-options` require, and use `sfo/` for engine-specific options.

---

## 7. The impl Boundary (What Goes Where)

Based on detailed classification of every function in the original `form.cljc`:

### form.impl (~52 functions) - Pure, no engine deps

All functions that use only Fulcro core, RAD attributes/options, and standard Clojure:

- **Constants**: `*default-save-form-mutation*`, `view-action`, `create-action`, `edit-action`, `standard-action-buttons`
- **Component helpers**: `picker-join-key`, `master-form`, `master-form?`, `parent-relation`, `form-key->attribute`, `subform-options`, `subform-ui`, `get-field-options`
- **Rendering env**: `rendering-env`, `render-input`, `field-context`, `with-field-context` (macro), `subform-rendering-env`, `render-subform`
- **Field helpers**: `find-fields`, `optional-fields`, `field-label`, `field-style-config`, `field-autocomplete`, `field-visible?`, `omit-label?`, `computed-value`
- **State/defaults**: `form-pre-merge`, `form-and-subform-attributes`, `default-to-many`, `default-to-one`, `default-state`, `mark-fields-complete*`, `update-tree*`
- **Validation**: `valid?`, `invalid?`, `invalid-attribute-value?`, `validation-error-message`
- **Server**: `save-form*`, `wrap-env`, `pathom-plugin`, `resolvers`, pathom2 mutation defs, `delete!`
- **Misc**: `server-errors`, `sc` (private), `defunion` (macro), `form-body`
- **Macro helper**: `defsc-form*` (parameterized by convert-options-fn), `convert-options-base`

### Tricky Items (transitive engine deps)

| Function | Issue | Resolution |
|----------|-------|------------|
| `read-only?` | Calls `view-mode?` (engine-specific) | Each engine defines its own (~5 lines, pragmatic duplication) |
| `standard-controls` | Lambdas call `save!`, `cancel!`, `undo-all!`, `view-mode?` | Must stay in public ns (each engine builds its own) |
| `form-options->form-query` | Original includes `[::uism/asm-id '_]` in query | Takes optional `extra-query-elements` param; UISM passes `[[::uism/asm-id '_]]`, statecharts passes `[]` |

### Engine-specific functions - Stay in public ns (pruned)

Each engine provides its own implementation. **Public API only** — internal machine helpers are private:

| Category | Functions |
|----------|-----------|
| **Lifecycle** | `start-form!`, `abandon-form!` |
| **Actions** | `save!`, `cancel!`, `undo-all!`, `delete!`, `add-child!`, `delete-child!` |
| **Field events** | `input-changed!`, `input-blur!`, `mark-all-complete!` |
| **State queries** | `view-mode?`, `read-only?` |
| **Routing** | `view!`, `edit!`, `create!` (UISM) or via `statechart.routing` (statecharts) |
| **Route conflict** | `clear-route-denied!`, `continue-abandoned-route!` |
| **Controls** | `standard-controls` |
| **Macro** | `defsc-form`, `convert-options` |
| **Statecharts-only** | `form-busy?`, `make-form-busy-fn` |

Internal helpers (`start-edit`, `start-create`, `leave-form`, `calc-diff`, `auto-create-to-one`, `apply-derived-calculations`, etc.) are **private** in their respective engine namespace.

### Rendering functions - Backward compatible

- **UISM (`rad.form`)**: Keeps `render-fn`, `form-container-renderer`, `form-layout-renderer`, `install-field-renderer!`, `install-form-container-renderer!`, `install-form-body-renderer!`, `install-form-ref-renderer!` exactly as they are today.
- **Statecharts (`rad.statechart.form`)**: Uses `render-element` multimethod instead.
- **Shared**: `render-field` (delegates to `fr/render-field`), `render-layout` (delegates to `fr/render-form`), `render-input`. These go in `form.impl`.

---

## 8. The Engine Seams (Reference)

Every difference between UISM and statecharts boils down to these operations:

| Operation | UISM | Statecharts |
|-----------|------|-------------|
| Session identity | ident itself | `session/ident->session-id` |
| Start machine | `uism/begin!` | `scf/register-statechart!` + `scf/start!` |
| Send event | `uism/trigger!` | `scf/send!` |
| Send event (sync) | `uism/trigger!!` | `scf/send!` |
| Read local data | `get-in state [::uism/asm-id id ...]` | `get-in state [::sc/local-data sid ...]` |
| Check running? | Check `::uism/active-state` | `scf/current-configuration` |
| Query additions | `[::uism/asm-id '_]` | nothing |
| Route lifecycle | DR `will-enter`/`will-leave` | `sfro/statechart`, `sfro/busy?` |

---

## 9. Resolved Decisions

1. **Two separate libraries**: fulcro-rad stays as-is (with impl extraction). fulcro-rad-statecharts is a separate add-on library.

2. **Zero public API change to fulcro-rad**: The impl extraction is an internal refactor. Existing public vars become pointers to impl. No user-visible change.

3. **Option keys**: Shared keys (`fo/id`, `fo/attributes`, `fo/subforms`, etc.) use `fo/` directly — no duplication. `sfo/` only defines keys with new or different semantics for statecharts (`sfo/triggers`, `sfo/statechart`).

4. **Three requires for statecharts users**: `rad.statechart.form`, `rad.form-options`, and `rad.statechart.form-options`. Honest, zero duplication, complete.

5. **Compile-time validation**: The `defsc-form` macro in this library catches wrong-engine option keys at compile time.

6. **`read-only?`**: Each engine defines its own. Small function, pragmatic duplication.

7. **`form-options->form-query`**: Takes optional `extra-query-elements`. UISM passes UISM-specific query additions, statecharts passes nothing.

8. **Rendering**: Backward compatible. UISM keeps `install-*-renderer!` + `render-fn`. Statecharts uses `render-element` multimethod. No cross-pollination.

9. **rstate vs istate**: Handled in the routing namespace, NOT in defsc-form/defsc-report. `defsc-form` itself doesn't change for this.

10. **Container scope**: Deferred. Focus on form + report first.

11. **Headless rendering**: Lives in `rendering.headless.*`. Depends only on `form_render.cljc` / `report_render.cljc` multimethods (shared). Engine-agnostic.

---

## 10. Dependency Graph

```
=== fulcro-rad (upstream) ===

form.impl       -> Fulcro core, RAD attrs/options, form-options (NO engine deps)
form            -> form.impl + UISM + dynamic-routing (public vars point to impl)
form-options    -> only defines keywords (unchanged)

report.impl     -> same pattern
report          -> report.impl + UISM + dynamic-routing
report-options  -> unchanged


=== fulcro-rad-statecharts (this library) ===

statechart.form              -> form.impl + statecharts
statechart.form-options      -> only defines new keywords (no deps beyond clojure.core)
statechart.form-expressions  -> form.impl + statecharts (NOT statechart.form, avoids circular dep)
statechart.form-chart        -> statechart.form-expressions
statechart.routing           -> statecharts
statechart.session           -> statecharts

statechart.report            -> report.impl + statecharts (same pattern)
statechart.report-options    -> only defines new keywords
statechart.report-expressions -> report.impl + statecharts
statechart.report-chart      -> statechart.report-expressions
```

---

## 11. Remaining Open Questions

1. **Container scope**: Deferred. Focus on form + report first.

2. **Are we ready to finalize this analysis and move toward writing specs?**
