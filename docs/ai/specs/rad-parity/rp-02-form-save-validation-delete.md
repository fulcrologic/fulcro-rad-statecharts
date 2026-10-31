# Spec: Form Save Errors, Validation, Delete, and Render-Dispatch Collision

**Status**: backlog
**Priority**: P0
**Created**: 2026-10-31
**Owner**: AI
**Depends-on**: rp-01
**Skills**: `clojure`, `clojure-repl`, `fulcro-spec-tdd`, `statechart`, `fulcro`, `fulcro-rad`, `fulcro-headless`

## Context

Four core form defects, all verified (gap report §1.2, §1.3, §1.8, §3 Forms "Delete"; `review/review-forms.md` #1–#3, #14, #20; `review/review-demo-failures.md` §5).

**GitHub #29: server save errors never reach the form.**
- `on-save-failed-expr` (`statechart/form.cljc`, about line 1089) does `(some-> (scf/mutation-result data) (get save-mutation) ::form/errors)`.
- `scf/mutation-result` is already `:body vals first` (statecharts `integration/fulcro.cljc` `mutation-result`), so the `get` always returns nil.
- As a result, `:server-errors` is never set.

**GitHub #30: attribute validators aren't enforced on save.**
- Upstream `convert-options` installs `::form/validator (attr/make-attribute-validator (form-and-subform-attributes Form) true)` (`../fulcro-rad` `form.cljc:559`). The statechart `convert-options` (about line 434) installs nothing.
- `valid?`/`invalid?` (about line 1580) only check required keys for non-nil, and they call the validator without `->arity-tolerant`.
- So the save guard `form-valid?` lets through `ao/valid?` failures, blank required strings and every subform field.
- The UI disagrees with the save: `invalid-attribute-value?` falls back to `attr/valid-value?`.

**Delete (human decision 3: send the legacy symbol).**
- `delete!` (about line 1511) transacts `com.fulcrologic.rad.statechart.form/delete-entity`, but servers register upstream `form/resolvers`, which provides `com.fulcrologic.rad.form/delete-entity`.
- Its body is `#?(:cljs ...)`, so it's a no-op in CLJ.
- In CLJ, `delete-entity` is bound to the pathom server-mutation map, so a CLJC client mutation can't use that var name today.

**`:default` render-method collision.**
- `fr/render-form :default` and `fr/render-field :default` are defined by:
  - upstream `com.fulcrologic.rad.form` (`form.cljc:299, 364`, map-registry based);
  - `statechart/form.cljc` (about lines 227, 273);
  - `rendering/headless/form.cljc:162`.
- The last one loaded wins. On a full-stack JVM, upstream `rad.form` can't be avoided: it's loaded by `form/pathom-plugin`/`resolvers`, save middleware, autojoin, fulcro-rad-datomic, and the legacy SUI plugin.
- When upstream loads last, forms render nil. That was the 960 "No renderer … :form-container" errors.
- The default form body path is: `render-layout` (= `form-impl/render-layout`) → `fr/render-form` (dispatch `[id-key style]` via `fr/gen-dispatch`) → `:default`.
- Report-side equivalent: rp-04. Plugin-side cleanup: rp-06.

## Requirements

1. **#29.** `on-save-failed-expr` reads `::form/errors` from the unwrapped mutation result. The `:server-errors` alias is set, and `form/server-errors` returns the errors. The `save-failed` trigger and the `:on-save-failed` txn still run. Errors are cleared on the next successful save and on undo/reset. Close #29 from the commit.
2. **#30, default validator.**
   - When `fo/validator` is absent, a default validator equivalent to upstream's `(attr/make-attribute-validator (form-and-subform-attributes Form) true)` is used. A user `fo/validator` wins.
   - `convert-options` receives a registry key, not a class. So build the validator lazily: resolve the class at call time and cache the result per class.
   - The save guard then blocks: an `ao/valid?` failure, a blank required string, and an invalid subform field.
   - After a blocked save, field-level `invalid-attribute-value?` and the save guard agree.
3. **#30, arity.** `valid?`/`invalid?` call the validator through `com.fulcrologic.fulcro.algorithms.lambda/->arity-tolerant`, so a 1-arity `(fn [props])` validator works and a 2-arity `(fn [form field])` one doesn't throw. Close #30 from the commit.
4. **Delete.**
   - `form/delete!` works in CLJ and CLJS. It removes the entity locally (`fns/remove-entity`) and sends the remote mutation as `com.fulcrologic.rad.form/delete-entity`.
   - This must not depend on whether upstream `com.fulcrologic.rad.form` is loaded, and must not conflict with it. Upstream defines a CLJS `defmutation` with that symbol.
   - If this ns keeps `pathom2-server-delete-entity-mutation`, its `::pc/sym` is the legacy symbol.
5. **Render collision, library side.** A form renders through the installed plugin whether upstream `com.fulcrologic.rad.form` is loaded before or after the plugin and this lib.
   - The lib no longer registers `:default` methods on upstream multimethods (`fr/render-form`, `fr/render-field`). This matches the existing `report.cljc` policy comment (about line 107).
   - The mechanism is documented for plugin authors, both in the `statechart/form.cljc` ns docstring and in `statechart/CLAUDE.md`. Document what to register (`form/render-element` and specific `fr/*` dispatch values) and what never to register (`:default` on upstream `fr/*` multimethods).
   - **Mixed builds** (SUI critique I4, 2026-10-31). The lib's current `fr/render-field :default` only logs, and `fr/render-form :default` is lib-owned. When the lib loads AFTER upstream `com.fulcrologic.rad.form`, *legacy* (UISM) forms lose their field rendering: upstream `form/render-field` goes through `fr/render-field`, and the lib's `:default` returns nil. Removing the lib's `:default`s must fix this.
     - Add a test proving that a legacy upstream form renders its fields in both load orders (lib before and after upstream `rad.form`).
     - The SUI statechart plugin's bridge `:default`s currently hide this. They go away in rp-12b req 1.
6. **Suite order.** Running `:unit` after `:demo` in the same REPL passes. The exact failing shape rp-01 found is `:unit`→`:demo`→`:unit`: 16 `headless-rendering-test` failures, because the headless renderer loads first and the demo's upstream `rad.form` loads after it. This removes the hazard rp-01 req 12 documented. Update `docs/ai/running-tests.md` and CI to drop any workaround rp-01 added, including the `{:kaocha.filter/skip [:demo]}` advice for single-ns runs if it's no longer needed.

## File Ownership

- `src/main/com/fulcrologic/rad/statechart/form.cljc`
- `src/main/com/fulcrologic/rad/statechart/form_options.cljc` (docstrings)
- `src/test/com/fulcrologic/rad/statechart/form_spec.cljc`, `form_statechart_spec.cljc`, `form_statechart_test.cljc` (not the rp-01 test-app helper)
- New test namespaces under `src/test/com/fulcrologic/rad/statechart/`, e.g. `form_validation_spec.cljc`, `form_delete_spec.cljc`, `render_dispatch_spec.cljc`
- `src/main/com/fulcrologic/rad/statechart/CLAUDE.md` (your own sections only; re-read right before editing)
- `docs/ai/running-tests.md` and `.github/workflows/test.yml`: only to remove the rp-01 req 12 workaround

Read-only: `rendering/headless/**`, owned by rp-06. The headless `fr/render-form :default` method must become harmless through this spec's library change alone. rp-06 deletes it.

## Affected Modules

- `statechart/form.cljc`: `on-save-failed-expr`, `convert-options`, `valid?`/`invalid?`, `delete!`/`delete-entity`, `render-layout`/`attr->renderer`/`render-field`, and removal of the `:default` defmethods

## Approach

- **#29.** `errors (some-> (scf/mutation-result data) ::form/errors)`, using `(keyword "com.fulcrologic.rad.form" "errors")`. Test it with a real mutation-result event shape, not a mock of the expression.
- **#30.** Add `:com.fulcrologic.rad.form/validator` to `base-options` *before* `options` is merged. The value is a fn that resolves `(rc/registry-key->class registry-key)` on first call and memoizes `(attr/make-attribute-validator (form-and-subform-attributes cls) true)`.
- **Delete.**
  - Rename the server-map var (keep `pathom2-server-delete-entity-mutation` and re-point its symbol).
  - Make `delete-entity` a CLJC `m/defmutation`: `action` (or `ok-action`) removes the entity, and `(remote [env] (m/with-server-side-mutation env 'com.fulcrologic.rad.form/delete-entity))`.
  - In CLJ the client mutation runs through the same path as CLJS.
  - Test with an app whose remote `transmit!` captures the send node, and assert the AST `:dispatch-key`.
- **Render dispatch (recommended mechanism).**
  - The lib's form body computes the `fr/render-form` dispatch value with `(fr/gen-dispatch fro/style false)`.
  - If `(get-method fr/render-form v)` is identical to `(get-method fr/render-form :default)`, there's no specific method, so it calls the lib-owned `render-element env :form-container` (upstream never defines that). Otherwise it calls the specific `fr/render-form` method.
  - Treat `attr->renderer`/`render-field` the same way: fall back to the lib's `default-render-field`.
  - Reject "re-assert `:default` at plugin install time": it still depends on load and reload order.
  - Test by re-adding the upstream `:default` method last (reload `com.fulcrologic.rad.form` in the test, or `.addMethod` the upstream fn), then rendering a headless form and asserting its fields and Save button exist. Then do the same in the reverse order.

## Open Questions

- None. The mechanism choice is the implementer's within the rules of req 5. Record it in `statechart/CLAUDE.md`.

## Verification

1. [ ] New tests fail on the pre-change code and pass after:
   - #29 (server errors populated);
   - #30 (an `ao/valid?` failure, a blank required string and a subform field each block save; a 1-arity validator works);
   - delete (CLJ local removal plus the legacy wire symbol, with and without upstream `rad.form` loaded);
   - render dispatch in both load orders.
2. [ ] #29 and #30 closed by commit reference.
3. [ ] Tests run with kaocha in the implementer's own REPL. No CLI test runs.
4. [ ] Full CLJ suite green, `:unit` + `:demo`, in **both** orders in one REPL (req 6).
5. [ ] CLJS `ci-tests` compiles with 0 warnings (compile only).
6. [ ] Plugin-author rule documented in the `form.cljc` ns docstring and `statechart/CLAUDE.md`. REPL killed. Committed and pushed.
7. [ ] `cljfmt check <changed files>` passes from the repo root (fulcro `.cljfmt.edn`; see root `CLAUDE.md` "Build Hygiene").
