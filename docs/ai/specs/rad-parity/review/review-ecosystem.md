# Ecosystem / cross-cutting review — fulcro-rad-statecharts

Read-only review. Nothing was run. "Verified" means I confirmed it by reading the code. "Suspected" means the mechanism is confirmed but I did not reproduce it.

## Prioritized gaps

**P0**
1. **The published artifact's deps can't load the lib.** `mvn deploy` (Makefile) publishes from `pom.xml`, and pom.xml doesn't match deps.edn:
   - fulcro is 3.9.3 there. The lib requires `fulcro.algorithms.macro-support` and `algorithms.core`, and neither exists in 3.9.3.
   - statecharts is RC13, which isn't bb-compatible. `../fulcro-rad-tui/deps.edn:6` excludes it for exactly this reason.
   - fulcro-rad is 1.6.22 and guardrails is 1.2.16.
   - The promesa 11 pin is missing, and a stray encore dependency is present.
   - Verified.
2. **Custom charts overwrite shared registry ids.**
   - A map-valued `sfo/statechart`/`sro/statechart` gets registered under the shared default id (`:com.fulcrologic.rad.form/form-chart` or `::report/report-chart`) (`form.cljc:385-394`, `report.cljc:683-692`). `report-route-state` always calls the generic `start-report!` (`report.cljc:1128`).
   - A keyword-valued chart gets the *default* chart registered under the user's id. The same pattern is in `server_paginated_report.cljc:336-344` and `incrementally_loaded_report.cljc:241-249`.
   - Statecharts looks up the chart by `::sc/statechart-src` on every event (`../statecharts/.../v20150901_impl.cljc:908`). So one server-paginated or custom report makes other running sessions process events with the wrong chart, and keyword charts never run.
   - Verified by code. Hand this to the form/report reviewers.
3. **README.adoc and the migration guide tell users to route with `com.fulcrologic.rad.routing`.**
   - That is the upstream Dynamic Router namespace: `route-to!` calls `dr/route-to!` (`../fulcro-rad/.../routing.cljc:57`). It has no `edit!`, `create!`, `form-route-state`, `report-route-state` or `force-continue-routing!`.
   - Affected places: `README.adoc:142-170` and `docs/migration-guide.adoc:178-288`.
   - The migration guide's "New Namespaces" table (`:58-100`) lists 11 namespaces that don't exist (`form-chart`, `report-expressions`, `rad.routing`, `sc.session`, …).
   - Verified.
4. **No browser rendering plugin that is published and works with this engine.** Details in §3. Verified.

**P1**

5. **Seven documented options are never read.** `sfo/on-started`, `sfo/on-saved`, `sfo/on-save-failed`, `sfo/statechart-id`, `sro/triggers`, `sro/on-loaded` and `sro/statechart-id` are declared and documented (`README.md:195-213`), but nothing reads them (grep of `src/main` outside the `*_options` files). The report macro rejects `ro/triggers` and points users to `sro/triggers` (`report.cljc:653`), which is dead, so report `:on-change` triggers can't be expressed at all. Verified.
6. **Containers can't be rendered by any plugin.** `container/render-layout` only logs an error (`container.cljc:186-196`). There is no multimethod to hook. The fulcro-rad-tui author documents the workaround (`../fulcro-rad-tui/.../tui/container.cljc:5-18`). Verified.
7. **Default render methods collide.**
   - `fr/render-field :default` and `fr/render-form :default` are each registered in three places: upstream `rad.form` (`form.cljc:299,364`), this lib (`statechart/form.cljc:227,273`), and headless (`headless/form.cljc:162`).
   - Upstream `rad.form` can't be avoided. It is loaded by the server's `form/pathom-plugin`/`form/resolvers`, `middleware.save-middleware`, `autojoin`, fulcro-rad-datomic (`datomic.clj:8`), and both third-party plugins.
   - So load order decides the renderer. If upstream loads last, forms render nil because the map registry is empty. `report.cljc:107` deliberately avoids this; `form.cljc` doesn't.
   - Suspected: the demo's require order happens to be safe.
8. **Entity delete is broken.**
   - `form/delete!` is CLJS-only (`form.cljc:1511-1516`).
   - It transacts `com.fulcrologic.rad.statechart.form/delete-entity`. The lib exports no `resolvers` or `pathom-plugin`, and the demo registers upstream `form/resolvers` (`components/parser.cljc:44`), which only provide `com.fulcrologic.rad.form/delete-entity`.
   - Nothing calls or tests it. Verified by code.
9. **No hooks replacement that follows the no-hooks-for-data rule.** Details in §1. Verified.
10. **The headless plugin bypasses the form statechart.**
    - Every field `onChange` calls `m/set-value!!`/`set-string!!` (`headless/field.cljc:78-241`), not `form/input-changed!`. Headless runs never exercise `:on-change`, `derive-fields` or complete-marking.
    - Missing field renderers: `:pick-many`, `:keyword`, `:uuid`, `:text`, password, multi-line, `:USD`, blob.
    - Controls cover only button, string and boolean. There is no container renderer.
    - Verified.

**P2**: two control-render contracts and the remaining map registries (§3); UISM still loaded transitively (§4); stale docs and packaging cruft (§5); blob and pathom3 described as "removed" when they actually work (§1).

## 1. Dropped subsystems

| Upstream ns | Usable with statechart forms/reports? | What the user loses / what exists externally |
|---|---|---|
| `authorization` | Client side is a UISM (`defstatemachine auth-machine`, `uism/begin!`, replies via `uism/trigger source-machine-id`; `authorization.cljc:45-253`). It would run, but statechart sessions can't receive its replies. Server-side `redact` is pure and **still active** through upstream `resolvers-common/secure-resolver`, which `resolvers.cljc:45` and datomic use. Verified. | Lose the multi-authority client flow (upstream itself labels it not production-ready). External: only *app-level* login routing charts in `../fulcro-with-statecharts/.../ui/login_dialog.cljc` + `client.cljs:41-60` and in fulcro-rad-statecharts-demo. No library. `src/main/.../rad/CLAUDE.md` wrongly says `secure-resolver` is a pass-through. |
| `blob`, `blob_storage` | **Yes.** Zero UISM. It depends only on `:com.fulcrologic.rad.form/{params,delta,field-style,query-inclusion}` keys, and the engine honors all of them: attribute query-inclusion at `form.cljc:474`, and save via upstream `form/save-form` (`form.cljc:716`). fulcro-with-statecharts uses upstream blob with statechart forms. Suspected-working. | Docs say removed/deferred, which is wrong. The real gaps: no headless renderer for `[:string ::blob/file-upload]`, and `upload-file!` is CLJS-only, so it can't be tested headless. |
| `rad_hooks` | **No.** It calls `uism/begin!` with upstream `form/form-machine` and upstream `report/start-report!` (`rad_hooks.cljc:9-12`). | Lose non-routed embedding (modals, master-detail, quick-create). External replacements are all hooks-based, which violates the user's rule: `../fulcro-with-statecharts/.../semantic_ui/modals.cljc:14-37` (`use-component`/`use-state`), and statecharts `integration/fulcro/hooks.cljc` (`use-statechart`). `docs/ai/specs/rad-hooks-conversion.md` plans to build on `use-statechart`, which would also break the rule. A compliant design is the parent chart invoking the form/report chart (`scr/istate`/`invoke`). |
| `ui_validation` | Thin alias to upstream `rad.form`. | Nothing lost; the engine has `valid?`/`invalid?`. The migration guide's "use Fulcro form-state" advice is inaccurate. |
| `dynamic/generator*` | **No.** It uses `dr/add-route-target!!` plus upstream `form/form`/`report/report`. | Lose model-driven `geno/forms`/`geno/reports`. Runtime constructors exist here (`form.cljc:1763`, `report.cljc:979`), but the routing chart is static, so there is nothing to auto-register routes against. |
| `pathom3`, `resolvers_pathom3` | **Yes.** Server-only, no form or UISM deps. | Nothing to port. "Not ported" in the docs is misleading. |
| `routing*` | No (DR-based). | Replaced by `scr/*`. The `RouteHistory` abstraction has an equivalent in statecharts `url_history` + `simulated_history.cljc`. |

Also a trap: statecharts' `integration/fulcro/rad_integration.cljc` is deprecated, UISM-based, and requires upstream `rad.form`/`rad.report`.

## 2. Delegated upstream impl

- `form.impl`: no UISM, DR or registry references in any delegated function. The `::form/x` keywords use `[com.fulcrologic.rad.form :as-alias form]` (`form.cljc:28`), so option keys match upstream and datomic. Verified.
- `report.impl`: the code itself is UISM-free, but it requires upstream `rad.control`, which requires `ui-state-machines` (`control.cljc:27`). Several of the delegated functions are still map-registry based:
  - `default-render-layout` (`::report/style->layout`, `report/impl.cljc:38-46`); CLAUDE.md wrongly says "logs error"
  - `default-render-row`
  - `control-renderer`
  - `install-formatter!` / `formatted-column-value` (`::report/type->style->formatter`)
  - `install-row-layout!`
  - `loading?`, `form-link` and `genrow` are fine (`::form/id` resolves to upstream).
- Keyword-namespace mismatches: all option keys are shared with upstream (`fo/`, `ro/`, `:com.fulcrologic.rad.control/*`, `:com.fulcrologic.rad.container/*`). The only intentional splits are `sfo/triggers` and `sfo/statechart`, which the macros guard at compile time (`form.cljc:299-302`). Minor: `sfo/triggers` `:on-blur` is read (`form.cljc:1008`) but undocumented.
- fulcro-rad-datomic works on the server through upstream `::form/params`/`::form/delta` (`datomic.clj:54,457`), apart from delete (P1-8).

## 3. Rendering plugins

- **`../fulcro-rad-semantic-ui`** (main): targets UISM only. It calls upstream `form/input-changed!`, `report/sort-rows!` and similar, which `uism/trigger!` a machine that doesn't exist. Its `statecharts` branch (Sept 2025) targets "fulcro-rad 2.0.0-SNAPSHOT", an abandoned design. Verified.
- **`../fulcro-rad-react-bootstrap`**: UISM only (fulcro-rad 1.6.18, 9 upstream-form requires). Verified.
- **`../fulcro-with-statecharts/.../semantic_ui/`**: a vendored, unpublished port pinned to lib 0.1.3 and fulcro 3.9.5.
  - It is a *bridge*: it still installs `all-controls` maps and adds `defmethod sc-form/render-element :default` and `fr/render-field :default` that call upstream `form/render-fn` and `form/attr->renderer` (`semantic_ui_controls.cljc:102-117`).
  - It loads the full upstream UISM `rad.form`/`report`/`control`. `entity_picker.cljc:14-29` and `modals.cljc` use hooks for data.
  - The APIs it calls still exist in the current lib. Suspected compatible.
- **`../fulcro-rad-tui`**: targets this lib (0.1.5), but it's a terminal UI, not a browser plugin. Forms use multimethods. Reports, controls and containers still go through the map registry, and it requires upstream `rad.report`/`rad.form` for the `rr/*` `:default` bridges (`tui_controls.cljc:18-25`).
- **`../fulcro-rad-statecharts-demo`** uses upstream SUI 1.4.7 (`client.cljs:11,20`), so field and control interaction is Suspected broken.
- **Headless (this repo)**: plain HTML, gaps listed in P1-10.
- **Two control contracts:**
  - Statechart `control/render-control [type style inst key]` is a multimethod (`statechart/control.cljc:42`); headless uses it.
  - Upstream `control/render-control [owner key]` uses maps; report.impl, TUI and the SUI fork use it.
- **Map-only hooks remaining:**
  - `::report/type->style->formatter`: no multimethod alternative.
  - `::report/row-style->row-layout`, `::report/control-style->control`, `::report/style->layout`: covered if a plugin defines the `rr/*` methods.
  - `::container/style->layout`: nothing reads it, and there's no hook at all.
  - Net: a pure-multimethod plugin can cover forms, reports and controls, but not containers or custom formatters.

## 4. CLJC / headless compliance

- `.clj`-only files in `src/demo`: the four headless test/fixture namespaces plus `database_queries.clj`. All are test- or server-only, which is acceptable. `src/main` is all CLJC.
- CLJS-only behavior: `form/delete!` (P1-8). Demo URL sync is CLJS-only (`system.cljc:32`) even though statecharts ships a CLJC `simulated_history`.
- `js/` refs have stubs: `js/confirm` → `(constantly true)` (`form.cljc:1162`), and `goog.DEBUG` is guarded.
- No `requiring-resolve` and no React hooks in main, demo or test.
- No `uism`/`dr` code references; only CLAUDE.md prose and one negative assertion in a test (`form_spec.cljc:59`).
- UISM is still *loaded* transitively (report.impl → rad.control; rad.application). DR is not.
- `tests.edn` only scans `src/test` for `-test$`/`-spec$`, so the demo E2E namespaces (`*-tests`) never run. Verified.

## 5. Docs and packaging

- `README.md`:
  - Says not to use `rad.form`, `form.impl` or `report.impl`. But the lib delegates to both impl namespaces, and the server needs upstream `form/pathom-plugin`/`resolvers`.
  - Claims `form.impl` "depends on UISM via rad.routing", which is false.
  - The deps snippet is stale (fulcro 3.9.3, statecharts RC1 with a meaningless fulcro-rad exclusion).
  - Documents the dead options from P1-5.
- `README.adoc`: describes the lib as "a fork", requires "Fulcro 3.9.3+", and gives the routing instructions from P0-3.
- Migration guide: uses `fo/statechart`/`fo/on-change` (should be `sfo/*`), and says the namespaces were "removed entirely" when they're still on the classpath.
- `docs/DevelopersGuide.adoc` and `docs/datomic.adoc` are unmodified upstream copies (one commit, "Initial copy"), with no statechart content. `make publish` would rsync this repo's DevelopersGuide over the live upstream RAD book at `RAD.html`.
- `CHANGELOG.md` is the upstream 1.6.0 notes.
- `cleanup-analysis.md` is a stale February 2026 design doc (it lists `statechart.form-chart`, `statechart.routing`, and a `render-element` backport that never happened). Move it to `docs/ai` or delete it.
- Stale memory notes:
  - `src/main/.../rad/CLAUDE.md`: stubs, `routing/edit!`, `run!` no-op, `secure-resolver`
  - `headless/CLAUDE.md`: "no render-form override"
  - `demo/.../ui/CLAUDE.md`: `fo/statechart`
- The clj-kondo export lints only the upstream macros, not `statechart.*/defsc-*`, and sits at the same path as upstream's export.
- `user.clj:10` refreshes `src/example`, which doesn't exist.
- Versions:
  - Fulcro 3.10.0-RC2 is pinned while 3.10.0 and 3.10.1 final exist.
  - statecharts RC18 vs RC19 matters little: RC19 only adds a promesa fail-fast, and the lib already pins promesa 11.
  - fulcro-rad-datomic 1.5.7 is out; the demo uses 1.5.6.
  - `src/main/deps.cljs` npm versions (js-joda 3.2.0) disagree with `package.json` (5.7.0).
