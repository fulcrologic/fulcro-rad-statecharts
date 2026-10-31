# Spec: Form Lifecycle Parity

**Status**: backlog
**Priority**: P1
**Created**: 2026-10-31
**Owner**: AI
**Depends-on**: rp-03
**Skills**: `clojure`, `clojure-repl`, `fulcro-spec-tdd`, `statechart`, `fulcro`, `fulcro-rad`, `fulcro-uism` (read-only reference for upstream behavior)

## Context

The form chart's lifecycle diverges from upstream `form-machine` in ways users will hit. Sources: gap report §3 Forms; `review/review-forms.md` #4, #5, #7, #11, #13, #15–#17, #19, #22, #23; `review/review-routing.md` P1-4.

Line numbers are approximate as of 2026-10-31; use the function names.

- **`on-change` for add/delete-row.** `add-row-expr`/`delete-row-expr` bind `on-change` and `old-value` but never call them. Upstream calls `apply-on-change` with `(parent-ident relation old new)` (`../fulcro-rad` `form.cljc:1383-1423`).
- **Autocreate order.** `on-loaded-expr` runs `add-form-config*` and `mark-complete-ops` *before* `build-autocreate-ops`. Upstream autocreates first. The new child gets no form config, and the parent is dirty right after load, which blocks routing through `form-busy?`.
- **Cancel ends the session.** `:event/cancel` → `:state/leaving` → final `:state/exited`. Upstream `leave-form` stayed in `:editing`. With `cancel-route :none`, `:embedded?`, a direct `start-form!`, or `:back` without URL sync (`scr/route-back!` returns nil), the form stays on screen with no session behind it.
- **Forced routing leaves dirty data.** Force-continuing away from a dirty form leaves the dirty values in normalized state, so reports show the unsaved edits. Upstream restored pristine.
- **Trigger timing.** `:started` runs in `:initial` (`store-options`) before the create merge or load. `:on-change` receives a `:fulcro/state-map` from before the change. Upstream's env already had the new value.
- **Reload.** `:event/reload` both runs `start-load-expr` in the transition and re-enters `:state/loading`, whose on-entry loads again. Reload isn't blocked for a tempid (upstream `form.cljc:1045-1049`).
- **Picker preload (`load-picker-options-expr`):**
  - Only master-form attributes are covered, so subform pickers are skipped.
  - It requires non-nil form field-options, so `po/*` set only on the attribute is skipped.
  - It passes normalized props, not UI props, to `po/query-parameters`.
- **`form-busy?`** ignores `fo/read-only?`. `fo/silent-abandon?` is dropped by human decision 2 (rp-09).
- **CLJS edits are async.** `input-changed!` uses async `scf/send!`, where upstream used `uism/trigger!!`. Expect controlled-input cursor jumps (Suspected).
- **`cancel-route` shapes.** A vector of strings or `{:route [...]}` is silently ignored.
- **Missing re-exports.** `parent-relation`, client-side `save-as-form`, union helpers, and other engine-neutral upstream publics.
- **Custom charts.** Only transition fragments are exported, and they hard-code `:state/exited`/`:state/loading`. `derive-fields-ops`, `build-autocreate-ops` and `actor-ident` are private.
- **Dead `sfo` keys.** `sfo/on-started`, `on-saved`, `on-save-failed` and `statechart-id` are documented but never read. `:on-blur` in `sfo/triggers` works but is undocumented.

## Requirements

1. **Row triggers.** `:event/add-row` and `:event/delete-row` call `sfo/triggers` `:on-change` with the parent form ident, relation key, old value and new value, as upstream did. Its ops are applied.
2. **Autocreate order.** Autocreate runs before form config and mark-complete. The autocreated child has form config. A freshly loaded form with `autocreate-on-load?` is **not** dirty and doesn't block routing.
3. **Cancel keeps the session alive while the form is on screen.**
   - Cancel restores pristine, runs `:on-cancel`, then routes per `cancel-route`.
   - The session ends only when the route exits (istate), on `abandon-form!`, or on `:event/exit`.
   - With `:none`, `:embedded?`, `start-form!`, or `:back` without history, the form keeps working after cancel: save, reset and edit all still work.
4. **Pristine on forced exit.** When a dirty form's route exits without a save (forced routing or any route change), the entity is restored with `fs/pristine->entity*`. A report showing that entity then shows the persisted values.
5. **Trigger timing.**
   - `:started` runs after the create merge (create), and after the load is issued (edit), matching upstream.
   - `:on-change` receives data whose `:fulcro/state-map` already contains the new value.
6. **Reload.** One load per `:event/reload`. Reload on a tempid form is ignored with a warning. Reload during `:state/saving` is ignored.
7. **Picker preload.**
   - Subform pickers preload.
   - `po/*` set only on the attribute is honored.
   - `po/query-parameters` receives UI props (`fns/ui->props`).
8. **`form-busy?`** returns false for read-only forms (`fo/read-only?` true or fn). View mode joins this in rp-08.
9. **CLJS input timing.** Investigate whether async `input-changed!` causes cursor jumps in a controlled input.
   - If it's real, the visible field value must update in the same render as the keystroke. For example, apply the value synchronously, then send the chart event for triggers and derive.
   - Add a CLJ test that state holds the new value immediately after `input-changed!` returns, with `:event-loop? true`.
   - Record the finding in `statechart/CLAUDE.md` either way.
10. **`cancel-route` shapes.** Unsupported legacy shapes (vector of strings, `{:route [...]}`) log a one-time warning that names the supported shapes.
11. **Re-exports.**
    - Diff `(ns-publics 'com.fulcrologic.rad.form)` against the statechart form ns.
    - Re-export every engine-neutral fn that `form-impl` or a pure upstream fn backs, including `parent-relation` and the union helpers.
    - List the deliberate omissions in the ns docstring: UISM-specific fns, and server-side `pathom-plugin`/`resolvers`/`save-form*`, which servers keep requiring from upstream.
    - A test asserts the re-exported set.
12. **Custom-chart extension.**
    - Make `derive-fields-ops`, `build-autocreate-ops` and `actor-ident` public with docstrings.
    - Parameterize the fragments' hard-coded targets (fns taking target state ids), keeping the existing defs as defaults.
    - Add state-level fragments for loading, saving and creating, or document how to compose them.
    - Document custom-chart authoring in the ns docstring and `statechart/CLAUDE.md`.
    - A test builds a custom chart from the fragments and runs edit → change → save.
13. **Dead `sfo` keys.**
    - Remove `sfo/on-started`, `on-saved`, `on-save-failed` and `statechart-id` from `form_options.cljc`.
    - `defsc-form` emits a compile-time **warning** if any of them is present, naming the replacement: `sfo/triggers` `:started`/`:saved`/`:save-failed`, or a keyword `sfo/statechart`.
    - Document `:on-blur` in the `sfo/triggers` docstring.

## File Ownership

- `src/main/com/fulcrologic/rad/statechart/form.cljc`, `form_options.cljc`
- Form test namespaces under `src/test/com/fulcrologic/rad/statechart/` (incl. `options_validation_spec.cljc` for req 13) and new form test namespaces
- `src/main/com/fulcrologic/rad/statechart/CLAUDE.md` (your own sections only)

## Affected Modules

- `statechart/form.cljc`:
  - expression fns: `add-row-expr`, `delete-row-expr`, `on-loaded-expr`, `store-options`, `attribute-changed-expr`, `leave-form-expr`, `load-picker-options-expr`, `form-busy?`, `input-changed!`;
  - `form-chart`, the fragments, `form-route-state` exit, re-exports, and the macro warnings.

## Approach

- Read each upstream handler in `../fulcro-rad/src/main/com/fulcrologic/rad/form.cljc` (read-only) before changing its statechart equivalent. Match observable behavior, not structure.
- For req 3, consider keeping cancel inside `:state/editing` as a handled event, with `leave-form-expr` as its script, and reserving `:state/exited` for route exit and `:event/exit`.
- For req 4, the exit-fn in `form-route-state` (rp-03 already touched it) is the natural hook. Check `fs/dirty?` on the actor's entity and apply `pristine->entity*`.

## Open Questions

- None.

## Verification

1. [ ] New tests fail before and pass after for each of reqs 1–13. For req 9, a test if the finding is real, otherwise a written finding.
2. [ ] Tests run with kaocha in the implementer's own REPL. No CLI test runs.
3. [ ] Full CLJ suite green: `:unit` + `:demo`.
4. [ ] CLJS `ci-tests` compiles with 0 warnings.
5. [ ] Custom-chart guide and re-export omissions documented. `statechart/CLAUDE.md` updated. REPL killed. Committed and pushed.
6. [ ] `cljfmt check <changed files>` passes from the repo root (fulcro `.cljfmt.edn`; see root `CLAUDE.md` "Build Hygiene").
