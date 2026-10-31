# Spec: Semantic UI Statechart Plugin — Adapt to the Engine

**Status**: backlog
**Priority**: P1
**Created**: 2026-10-31
**Owner**: AI
**Depends-on**: rp-12a, rp-02, rp-05, rp-08, rp-09
**Skills**: `clojure`, `clojure-repl`, `fulcro-spec-tdd`, `fulcro`, `fulcro-rad`, `fulcro-rad-reports`, `fulcro-headless`

## Context

rp-12a copied the fulcro-with-statecharts SUI clone into `../fulcro-rad-semantic-ui` under `com.fulcrologic.rad.statechart.rendering.semantic-ui.*`. The rendering code is new; the option namespaces are shared with legacy.

The copy is still a **bridge**. Per its "Known debt" section in `../fulcro-rad-semantic-ui/src/main/com/fulcrologic/rad/statechart/rendering/semantic_ui/CLAUDE.md`, as of `40cfdfe`:

**Map-based installation.**
- `semantic_ui_controls/all-controls` is installed with `install-ui-controls!`.
- The statechart `control/render-control [type style instance key]` multimethod has no methods.

**`:default` bridges** in `semantic_ui_controls.cljc`:
- `defmethod sc-form/render-element :default` delegates to upstream `form/render-fn`.
- `defmethod fr/render-field :default` delegates to upstream `form/attr->renderer`.
- Both collide with the lib and other plugins.

**Upstream UISM-era calls:**
- `report.cljc`: upstream `rad.control/run!` for row-action `reload?`, upstream `form/edit!` for form links, `report/control-renderer`, and `report/render-*`.
- `form.cljc` and `container.cljc`: upstream `rad.control/render-control`.
- `entity_picker.cljc`: requires `ui-state-machines`, apparently unused.

**React hooks for data:** `entity_picker.cljc` (`use-state`/`use-gc`/`use-lifecycle`/`use-generated-id`), `modals.cljc` (`use-statechart-form`) and `autocomplete.cljc` (`use-component`/`use-gc`).

**Unchanged from the copy:**
- Container rendering is ported but has no lib hook. rp-05 adds one.
- View mode isn't adapted. rp-08 adds it.

**Route denial.** The rp-12a follow-up already drives the modal from `scr/route-denied?`, with buttons calling `scr/abandon-route-change!`/`scr/force-continue-routing!`. What remains is switching to the rp-09 helpers and deciding where the modal lives.

## Requirements

1. **Render dispatch rules** (rp-02 forms, rp-04 reports):
   - Remove every `defmethod` on `:default` of upstream multimethods (`fr/render-form`, `fr/render-field`, `rr/render-report`/`render-row`/`render-controls`) and the `sc-form/render-element :default` bridge.
   - Register through the lib-owned dispatch points and specific dispatch values, with hierarchy `derive!` for style defaults.
   - Forms and reports render with upstream `rad.form`/`rad.report` loaded before or after the plugin.
2. **Statechart form API.** Fields call statechart `form/input-changed!`/`input-blur!`. Actions call statechart `save!`, `undo-all!`, `cancel!`, `delete!` (legacy symbol, rp-02), `add-child!`, `delete-child!`, `edit!` and `view!` from `com.fulcrologic.rad.statechart.form`.
3. **Controls.** Every SUI control (button, string, boolean, picker, multi-picker, instant/date-range inputs, …) is a `defmethod` on statechart `control/render-control`. `install-ui-controls!` is used only for remaining map-only hooks, e.g. `::report/type->style->formatter`.
4. **Reports** use statechart `report/*` fns (`run-report!`, `sort-rows!`, `goto-page!`, `select-row!`, `filter-rows!`, `form-link`) and statechart `form/edit!` for form links.
5. **Container.** Implement rp-05's container render multimethod with the SUI layout from the copied `container.cljc`.
6. **Route denial.**
   - Use rp-09's library pattern: implement `route-denial/render-route-denied` with the SUI modal, and make `form/clear-route-denied!`/`form/continue-abandoned-route!` the thin replacement fns.
   - **Decision to implement:** remove the modal from the form body. rp-09's rule is that the app root renders denial UI exactly once, which avoids double modals. The plugin's modal survives only as the rp-09 render method.
7. **View mode** (rp-08). Fields render read-only and Save/Undo/add/delete are hidden or disabled in view mode, through `form/read-only?`/`form/view-mode?`.
8. **No upstream engine requires.** The new namespaces don't require `com.fulcrologic.rad.form`, `com.fulcrologic.rad.report`, `com.fulcrologic.rad.control` or `com.fulcrologic.fulcro.ui-state-machines`. Legacy option namespaces and engine-neutral upstream namespaces are fine: attributes, options-util, form-render, report-render, picker-options, type-support, blob. Any unavoidable exception is listed with its reason in the new `CLAUDE.md`.
9. **React hooks.** Hooks in the entity picker, modals and autocomplete may stay for now. List them as debt in the new `CLAUDE.md`, pointing to rp-13 for the statechart replacement. Add no new hooks.
10. **Tests in the SUI repo.** Add a test alias (fulcro-spec plus kaocha, on top of `:statecharts`) and CLJ tests that:
    - assert a field `onChange` reaches `form/input-changed!` (real form session in a headless app);
    - assert every SUI control type has a `control/render-control` method;
    - assert the container method is registered and renders;
    - assert forms render in both upstream load orders;
    - statically assert (via `clojure.tools.namespace.parse` over the new files) that none of the req 8 banned namespaces is required.
11. **From the rp-12a/12c critique (2026-10-31):**
    - **Ban list (req 8).** Also ban `com.fulcrologic.rad.container`, `com.fulcrologic.fulcro.routing.dynamic-routing` and `com.fulcrologic.rad.routing*`.
      - `container.cljc` requires upstream `rad.container` only for `id-child-pairs`. Use the lib's `statechart.container/id-child-pairs`.
      - Drop the unused upstream `rad.report` require in `controls/control.cljc`.
      - Replace the upstream form helpers: `form/field-label`, `omit-label?`, `field-style-config`, `render-fn`, `form-layout-renderer`, `standard-action-buttons` and `ref-container-renderer`.
    - **Mixed build.** After the plugin's bridge `:default`s are removed (req 1), legacy SUI forms and reports must still render, in a build containing legacy SUI + this lib + statechart SUI, in both load orders. This depends on rp-02 req 5 (mixed builds).
    - **Fail-when-broken proofs.** Every test must be shown to fail with its relevant bridge or dispatch method removed. The plugin loads upstream `rad.form`, whose `:default`s do the same map lookup, so the current render-field assertion is NOT discriminating (critique I3).
    - **Route-denial verification (req 6).**
      - The denial UI is visible in a real browser (karma or the rp-14 Chrome check) and disappears after Cancel. CLJ checks can't see the CLJS `shouldComponentUpdate` problem: the lib form query has no `[::sc/session-id '_]` link (critique I1).
      - After Continue, the entity is pristine. Needs rp-07 req 4 (critique I2).
    - **Test infra.** A guardrails test config with JVM opts for the CLJ suite, since the headless tests will exercise the lib's `>defn`s (CLJS already uses `:throw? true`). The `:legacy` kaocha id was added by the critique fix-up, so keep it working.
    - **Docs.** The tested lib version and its minimum are stated. The debt list is complete.
    - **dom-server nil.** Rendering a master form in CLJ dom-server throws an NPE when `:async-abandon-modal` is nil or `(constantly nil)`, because dom-server can't render a nil child inside `StandardFormContainer`'s fragment. Found in the `f95b5c9` fix-up. Removing the in-form modal (req 6) must leave the fragment free of nil children, with a CLJ render test.
    - **Legacy README snippet.** The legacy example threads `install-ui-controls!` with `->`, but the fn returns the runtime map, not the app. It's docs only: fix it, or ask the human, since it describes legacy.

## File Ownership

- `../fulcro-rad-semantic-ui/src/main/com/fulcrologic/rad/statechart/**` (new namespaces only)
- `../fulcro-rad-semantic-ui/src/test/**` (new) and a test alias in `../fulcro-rad-semantic-ui/deps.edn` (alias additions only; regenerate the pom with `clj -Spom` only if `:deps` changes, which it shouldn't)
- The new-directory `CLAUDE.md`

Legacy namespaces and option namespaces are read-only. Never reformat legacy files. This repo is read-only; if a lib change is needed, stop and report it to the conductor.

## Affected Modules

- Statechart SUI namespaces: `semantic_ui_controls`, `form`, `field`, `report`, `container`, `controls/*`, `entity_picker`, `modals`, `autocomplete`

## Approach

- Start from the `Known debt` list and work through it item by item, writing the corresponding test first.
- Use the headless plugin (post-rp-06) as the reference for the lib-facing contracts: dispatch points, control multimethod, container multimethod, route denial.

## Open Questions

- Commit or push policy for `../fulcro-rad-semantic-ui` `main`: same as rp-12a (local commits, no push) until the human says otherwise.

## Verification

1. [ ] New SUI tests (req 10) fail on the rp-12a state and pass after.
2. [ ] Tests run with kaocha in the implementer's own REPL in the SUI repo. No CLI test runs.
3. [ ] This repo's full CLJ suite (`:unit` + `:demo`) is still green. Re-run it if any shared expectation changed.
4. [ ] CLJS: after rp-14 exists, this repo's `main` build (with the plugin via `:local/root`) compiles with 0 warnings.
5. [ ] Legacy files byte-identical (`git diff` on legacy paths is empty).
6. [ ] `cljfmt check <changed files>` passes from the SUI repo root, on new files only (root `CLAUDE.md` "Build Hygiene").
7. [ ] New-directory `CLAUDE.md` updated (debt list current). REPL killed. Local commit, no push.
