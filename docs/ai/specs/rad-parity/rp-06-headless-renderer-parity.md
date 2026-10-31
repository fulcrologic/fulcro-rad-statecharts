# Spec: Headless Renderer Parity

**Status**: backlog
**Priority**: P0
**Created**: 2026-10-31
**Owner**: AI
**Depends-on**: rp-02, rp-05
**Skills**: `clojure`, `clojure-repl`, `fulcro-spec-tdd`, `fulcro-headless`, `fulcro`, `fulcro-rad`, `fulcro-rad-reports`, `date-time`

## Context

The headless plugin (`src/main/com/fulcrologic/rad/rendering/headless/`) is both the CLJ test renderer and, until rp-14, the demo's only renderer. It bypasses the form chart and covers a fraction of RAD's field, control and report options. Sources: gap report §1.7, §4 "Headless plugin coverage"; `review/review-forms.md` #12; `review/review-demo.md` P0-1/4; `review/review-ecosystem.md` P1-10.

- **Fields write state directly.** Every field `onChange` in `field.cljc` calls `m/set-string!`/`m/set-value!` (CLJS) or `m/set-*!!` (CLJ). Nothing calls `form/input-changed!` or `form/input-blur!`.
  - So `sfo/triggers` `:on-change`/`:derive-fields`/`:on-blur`, mark-complete (validation display) and error clearing never run from the UI.
  - In the demo that kills the invoice total, the line-item subtotal, item→price autofill and category→item reload.
- **`:default` collision, plugin side.** `form.cljc:162` registers `fr/render-form :default`. After rp-02 the library no longer depends on it.
- **Missing field renderers:** `:ref :pick-many`, autocomplete, `:keyword`, `:uuid`, `:string` `:multi-line`/`:password`/`:viewable-password`, decimal `:USD`, blob (`:com.fulcrologic.rad.blob/file-upload`), plus picker quick-create (`po/quick-create`).
- **Subforms** read raw `fo/subforms`, so `fo/subform`, fn-valued subform options and registry-key `fo/ui` are missed.
- **Controls:** only `:button`, `:string`, `:boolean`. `:disabled?` works only on buttons.
- **Reports:** `ro/links`, `ro/row-heading`, `ro/rotate?`, `ro/on-select-row`, row-action `:visible?`/`:disabled?`/`:type`, `ro/column-alignment` and `ro/column-class(es)` are ignored.
- **Containers:** no headless renderer (the multimethod comes from rp-05).

## Requirements

1. **Fields go through the chart.**
   - Every editable headless field calls `form/input-changed!` with the coerced value on change, and `form/input-blur!` on blur. Remove the CLJ/CLJS `m/set-*` split so both run the same path.
   - Coercion per type: string; int/long (parse, invalid → nil); double; decimal (`math/numeric`); boolean; enum (keyword); instant (via `dt`, honoring `:date-at-noon`); ref (ident).
   - Test with a fixture form whose `sfo/triggers` has `:on-change` and `:derive-fields`. Driving the rendered input's `:onChange`/`:onBlur` in CLJ runs both, marks the field complete, and shows the validation message for an invalid value.
2. **Plugin side of the collision.**
   - Remove headless `defmethod`s on `:default` of upstream multimethods that rp-02 (forms) and rp-04 (reports) made unnecessary: `fr/render-form`, `fr/render-field`, `rr/render-report`, `rr/render-row`, `rr/render-controls`. Register through the lib-owned dispatch points and specific dispatch values instead.
   - Test both load orders: headless forms and reports render with upstream `rad.form`/`rad.report` loaded last.
   - Update `headless/CLAUDE.md`. Its "No `fr/render-form :default` override" claim is false today.
3. **Field renderers:**
   - `:ref :pick-many` (multi-select over `po/current-form-options`);
   - autocomplete (`:autocomplete` style: text input plus option list, using `ao/autocomplete`/`fo/field-options` per upstream conventions);
   - `:keyword`, `:uuid`;
   - `:string` `:multi-line`/`:password`/`:viewable-password`;
   - `:decimal :USD`;
   - blob `:com.fulcrologic.rad.blob/file-upload`. In CLJ, a test can "complete an upload" by setting the blob fields through `input-changed!`, since upstream `upload-file!` is CLJS-only. Document this.
   - Each gets a rendering test that asserts data-attributes and the change path.
4. **Quick-create.** Pickers (`:pick-one`, `:pick-many`, autocomplete) support `po/quick-create`: typing a new label offers "Create …", which calls the quick-create fn, adds the new entity (tempid) to the options, and selects it, so it saves with the parent form. Editing the new entity in a modal is rp-13, not this spec.
5. **Subforms** resolve their UI through `form/subform-ui`/`fo/subform-options`, covering `fo/subform`, fn-valued options and registry-key `fo/ui`. Tests for each shape.
6. **Controls** (statechart `control/render-control [type style instance key]`):
   - add `:picker`, `:multi-picker`, `:instant` (including `:starting-date`/`:ending-date` styles), `:int`, `:decimal`, `:uuid`;
   - `:disabled?` and `:visible?` honored for every type;
   - value changes go through `control/set-parameter!` and then the control's `:onChange (fn [this v])`, on one CLJ/CLJS path.
   - Tests per type.
7. **Reports:**
   - `ro/links` wraps column values;
   - `ro/row-heading` adds a row heading cell;
   - `ro/rotate?` renders the rotated table (`report/rotate-result` semantics);
   - `ro/on-select-row` is called on row select;
   - row actions honor `:visible?`, `:disabled?` and `:type`;
   - `ro/column-alignment` and `ro/column-class(es)` are emitted as data or class attributes.
   - Tests per option.
8. **Container renderer.** Implement rp-05's container multimethod for headless: render `co/title`, the container controls, and children laid out per `co/layout` (default: one row per child). Test it.

## File Ownership

- `src/main/com/fulcrologic/rad/rendering/headless/**`, including `headless/CLAUDE.md`
- `src/test/com/fulcrologic/rad/rendering/headless_rendering_spec.cljc`, `headless_rendering_test.cljc`, and new headless test namespaces under `src/test/com/fulcrologic/rad/rendering/`

Read-only: everything under `statechart/`. If a library change is needed, stop and report it to the conductor.

## Affected Modules

- `headless/field.cljc`, `form.cljc`, `controls.cljc`, `report.cljc`, `plugin.cljc`, and a new `container.cljc`

## Approach

- Match upstream SUI semantics for option interpretation where this spec is silent (`../fulcro-rad-semantic-ui/src/main/com/fulcrologic/rad/rendering/semantic_ui/**`, read-only). Keep the output plain HTML with `data-rad-*` attributes (see `headless/CLAUDE.md` conventions).
- In CLJ, `scf/send!` under `:event-loop? :immediate` is synchronous, so field tests can assert state right after calling the handler.

## Open Questions

- None.

## Verification

1. [ ] New or updated tests fail before and pass after for every requirement. Before rp-06, field `onChange` doesn't fire `:on-change`; the missing renderers render nothing.
2. [ ] Tests run with kaocha in the implementer's own REPL. No CLI test runs.
3. [ ] Full CLJ suite green, `:unit` + `:demo`, both suite orders in one REPL.
4. [ ] CLJS `ci-tests` and `main` compile with 0 warnings.
5. [ ] `headless/CLAUDE.md` rewritten for the new rules (dispatch, change path, quick-create, blob in CLJ). REPL killed. Committed and pushed.
6. [ ] `cljfmt check <changed files>` passes from the repo root (fulcro `.cljfmt.edn`; see root `CLAUDE.md` "Build Hygiene").
