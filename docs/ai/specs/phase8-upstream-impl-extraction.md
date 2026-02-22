# Spec: Upstream impl Extraction (fulcro-rad PR)

**Status**: backlog
**Priority**: P0
**Created**: 2026-02-22
**Owner**: AI
**Depends-on**: none
**Phase**: 8 — Library Restructuring

## Context

This spec describes work that must happen in the **UPSTREAM fulcro-rad project** (`../fulcro-rad/`), NOT in this library. It is a prerequisite for the restructuring of fulcro-rad-statecharts into an add-on library.

Currently, `form.cljc` and `report.cljc` in fulcro-rad contain both engine-agnostic pure functions (~40 for form, ~25 for report) and UISM-specific engine code. To allow fulcro-rad-statecharts to reuse the pure functions without duplicating them, these functions must be extracted into `form.impl` and `report.impl` namespaces. The existing public vars in `form.cljc` and `report.cljc` become pointers to the impl, preserving **zero public API change**.

This is an internal refactor of fulcro-rad. No user-facing behavior changes.

## Requirements

1. Create `com.fulcrologic.rad.form.impl` namespace containing ~52 pure functions extracted from `form.cljc`
2. Create `com.fulcrologic.rad.report.impl` namespace containing ~25 pure functions extracted from `report.cljc`
3. The impl namespaces must have **no UISM dependencies** and **no statecharts dependencies** — only Fulcro core, RAD attributes/options, and standard Clojure
4. Existing public vars in `form.cljc` become `def` aliases pointing to `form.impl` (e.g., `(def valid? impl/valid?)`)
5. Existing public vars in `report.cljc` become `def` aliases pointing to `report.impl`
6. All existing tests must pass without modification (zero public API change)
7. The `defsc-form*` macro helper function moves to `form.impl`, parameterized by a `convert-options-fn` argument
8. The `defsc-report*` macro helper function moves to `report.impl`, parameterized similarly
9. `convert-options-base` (shared option conversion logic) moves to impl
10. `render-element` multimethod (if present) moves to impl since it's engine-agnostic

## Functions to Extract: form.impl

Based on the classification in cleanup-analysis.md Section 7:

### Constants
- `*default-save-form-mutation*`, `view-action`, `create-action`, `edit-action`, `standard-action-buttons`

### Component Helpers
- `picker-join-key`, `master-form`, `master-form?`, `parent-relation`, `form-key->attribute`, `subform-options`, `subform-ui`, `get-field-options`

### Rendering Env
- `rendering-env`, `render-input`, `field-context`, `with-field-context` (macro), `subform-rendering-env`, `render-subform`

### Field Helpers
- `find-fields`, `optional-fields`, `field-label`, `field-style-config`, `field-autocomplete`, `field-visible?`, `omit-label?`, `computed-value`

### State/Defaults
- `form-pre-merge`, `form-and-subform-attributes`, `default-to-many`, `default-to-one`, `default-state`, `mark-fields-complete*`, `update-tree*`

### Validation
- `valid?`, `invalid?`, `invalid-attribute-value?`, `validation-error-message`

### Server
- `save-form*`, `wrap-env`, `pathom-plugin`, `resolvers`, pathom2 mutation defs, `delete!`

### Misc
- `server-errors`, `sc` (private), `defunion` (macro), `form-body`

### Macro Helper
- `defsc-form*` (parameterized by convert-options-fn), `convert-options-base`

### Rendering (shared multimethod)
- `render-element` (if it dispatches on `[element layout-style]` — engine-agnostic)
- `render-field` delegation, `render-layout` delegation, `render-input`

### Tricky Items (require parameterization)

| Function | Issue | Resolution |
|----------|-------|------------|
| `read-only?` | Calls `view-mode?` (engine-specific) | Each engine defines its own (~5 lines) |
| `standard-controls` | Lambdas call `save!`, `cancel!`, `undo-all!`, `view-mode?` | Each engine builds its own |
| `form-options->form-query` | Original includes `[::uism/asm-id '_]` in query | Takes optional `extra-query-elements` param; UISM passes `[[::uism/asm-id '_]]`, statecharts passes `[]` |

## Functions to Extract: report.impl

Same pattern. Extract all pure/rendering functions that don't reference UISM. Key categories:
- Query/column helpers
- Formatting functions
- Rendering env
- Server-side resolvers
- Macro helper `defsc-report*`

## Approach

1. **Prerequisite**: fulcro-rad must be checked out at `../fulcro-rad/`. Verify with `ls ../fulcro-rad/src/main/com/fulcrologic/rad/form.cljc`.
2. **Read** `../fulcro-rad/src/main/com/fulcrologic/rad/form.cljc` and classify every function/var
3. **Create** `../fulcro-rad/src/main/com/fulcrologic/rad/form/impl.cljc` with pure functions
4. **Update** `form.cljc` to require `form.impl` and create `def` aliases for extracted functions
5. **Verify** all existing fulcro-rad tests pass
6. **Repeat** for `report.cljc` → `report/impl.cljc`
7. **Verify** again
8. **Create PR** against fulcro-rad upstream

## Affected Modules (in ../fulcro-rad/)

- `src/main/com/fulcrologic/rad/form.cljc` — Extract pure functions, replace with def aliases
- `src/main/com/fulcrologic/rad/form/impl.cljc` — NEW: ~52 pure functions
- `src/main/com/fulcrologic/rad/report.cljc` — Extract pure functions, replace with def aliases
- `src/main/com/fulcrologic/rad/report/impl.cljc` — NEW: ~25 pure functions

## Open Questions

1. **Does fulcro-rad have a CI/test suite we can run?** Need to verify zero regression.
2. **Should the impl namespaces be marked as internal/private?** Convention: use `impl` in the name as a signal, but don't add `:no-doc` metadata since downstream (this library) needs to access them.
3. **What about `container.cljc`?** The cleanup analysis defers container work, but if we're extracting impl for form and report, should we do container too? Recommend: defer, since container is less stable.

## Verification

1. [ ] `form.impl` compiles with no UISM/statechart requires
2. [ ] `report.impl` compiles with no UISM/statechart requires
3. [ ] All existing fulcro-rad tests pass unchanged
4. [ ] `(require 'com.fulcrologic.rad.form)` still exposes all public vars
5. [ ] `(require 'com.fulcrologic.rad.report)` still exposes all public vars
6. [ ] `form.impl/defsc-form*` accepts a `convert-options-fn` parameter
7. [ ] `form.impl/form-options->form-query` accepts optional `extra-query-elements`
8. [ ] `read-only?` and `standard-controls` remain in `form.cljc` (not extracted)
9. [ ] PR is clean and reviewable
