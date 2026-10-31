# fulcro-rad-statecharts — port gap report (2026-10-31)

**Verdict: the port is not complete.**

What works today: the core CRUD path in headless/CLJ.
- Standard reports.
- Forms routed via `istate`: edit/save/reset, subforms, pickers, derive-fields on load.

What doesn't:
- Several core features are broken: custom charts, server errors, validation, routed-form options, report variants, containers.
- Whole subsystems are missing: browser renderer, hooks/embedded forms, auth.
- The demo covers roughly a third of `fulcro-rad-demo`.
- The README teaches a namespace that resolves to upstream dynamic routing.
- The published `pom.xml` has stale dependencies.

`docs/ai/specs/TRACKER.md` is stale: it was last updated 2026-02-22, has had `phase9-delete-routing-update-deps` marked "IN PROGRESS" ever since, and claims things that are no longer true.

Labels: **[V]** = verified by tracing code or reproducing in a REPL. **[me]** = I re-checked it myself after the subagent reported it. **[S]** = suspected, meaning the mechanism was seen but not reproduced.

Per-area detail reports (option tables, event mappings, public-fn tables) are in the same directory:
- `review-forms.md`
- `review-reports.md`
- `review-routing.md`
- `review-demo.md`
- `review-ecosystem.md`
- `review-tests.md`
- `review-demo-failures.md`

---

## 0. Test / build status (run 2026-10-31)

| Suite | Result |
|---|---|
| Library unit (`kaocha :unit`, REPL) | **90 tests, 427 assertions, 0 failures** |
| Demo headless E2E (`src/demo/com/example/headless_*_tests.clj`) | **21 tests, 15 failures.** All 15 are on the test side. |
| CLJS `:main` build | **Compiles, 0 warnings** (441 files) |

Why the demo E2E tests fail [V, reproduced in the REPL]:
- **Stale session id.** Commit `e364841` switched forms to `istate` routing, which gives them generated session ids. The test helper (`headless_form_tests.clj:53-61`) still calls `ident->session-id`, so it reads and sends to a session that doesn't exist. 9 `form-in-state?` failures come from this, plus 2 more because save/reset events never reach the form.
- **Timing.** `wait-for-form!` renders 30 frames but doesn't wait on anything, which produces the subform-address failures on a cold JVM.
- **Missing route param.** The `AccountInvoices` test routes with `{}` instead of `{:account/id …}`.
- **Library works when addressed correctly.** Sent to the real session id, edit, save (persisted across clients) and reset all work.

Other test problems:
- **Demo tests never run automatically.** `tests.edn` only scans `src/test` with `-test$`/`-spec$`, and CI omits `:demo`.
- **Racy unit-test apps.** The `test-app` helpers build `fulcro-rad-app {}` with no remote, so Fulcro's fallback remote throws an `ArityException` on a background thread. That is a latent race; `e3d4e90` fixed the same thing for the container test.

---

## 1. P0: library features broken

1. **Custom statecharts overwrite shared chart ids.** [me]
   - A map-valued `sfo/statechart` or `sro/statechart` is registered under the shared default id (`::report-chart`, `::form-chart`), so it replaces the default chart for every running instance.
   - A keyword-valued chart gets the *default* chart registered over the user's id.
   - Locations: `report.cljc:683-692`, `server_paginated_report.cljc:336-344`, `incrementally_loaded_report.cljc:241-249`, `form.cljc:385-394` (keyword case in `start-form!`).
   - `start_report_spec.cljc:97-114` asserts the overwrite as correct.
   - `form-route-state` also throws at chart-build time for a keyword `fo/statechart`, because `istate` can't derive `:route/reachable`.
2. **Server save errors never reach the form.** [me] Filed as **#29**.
3. **Attribute validators are not enforced on save.** No default `fo/validator` is installed, and `->arity-tolerant` was dropped. [me] Filed as **#30**.
4. **Routed forms never receive start options.** [me]
   - `edit!`/`create!` ignore `params` (`form.cljc:1907-1919`).
   - `istate` passes no options.
   - The data-model seeds `{:options {}}` (`form.cljc:1250`).
   - So `:on-saved`, `:on-cancel`, `:on-save-failed`, `:embedded?` and `:initial-state` work only through a direct `start-form!`.
5. **Report variants are unusable in practice.** [me]
   - `report-route-state` and containers always call the standard `start-report!`, which sets only the standard aliases. Server-paginated and incremental reports therefore can't be routed or placed in a container.
   - The incremental report shows zero rows even when started directly: its alias map lacks `:filtered-rows` and `:sorted-rows`, which the shared filter/sort/populate fns require (`incrementally_loaded_report.cljc:255-265`).
   - Its specs mock every expression, so nothing catches this.
6. **Containers.** [me]
   - **Not routable.** There is no `container-route-state`. `defsc-container` sets no `sfro/actors`, so an `istate` gives `:actor/container` a nil class (`container.cljc:67`).
   - **Not renderable.** `render-layout` logs an error and returns nil, and there is no multimethod for a plugin to implement (`container.cljc:186-196`).
   - **Custom chart ignored.** `container.cljc:206-209`.
7. **The headless renderer bypasses the form chart.** [me]
   - Every field `onChange` calls `m/set-*!`/`!!` directly (`rendering/headless/field.cljc:78-241`), and nothing calls `form/input-changed!` or `input-blur!`.
   - So `:on-change` triggers, derive-fields on edit, mark-complete and validation display never fire from the UI, in the browser or in headless tests.
   - In the demo this kills: invoice total, line-item subtotal, item→price autofill, and category→item reload.
8. **`:default` render methods collide.** [V, reproduced]
   - `fr/render-form` and `fr/render-field` have `:default` methods in upstream `rad.form` (form.cljc:299, 364), in `statechart/form.cljc:227, 273`, and in `headless/form.cljc:162`. The last one loaded wins.
   - Upstream `rad.form` can't be avoided on a full-stack JVM: `form/pathom-plugin`, `form/resolvers`, save-middleware, autojoin and fulcro-rad-datomic all load it.
   - When it loads last, forms render nil. That is the source of the 960 log errors.
   - `report.cljc:107` avoids this problem; `form.cljc` doesn't.

## 2. P0: docs and packaging

9. **README.adoc and `docs/migration-guide.adoc` teach the wrong routing namespace.** [me]
   - They use `com.fulcrologic.rad.routing :as rroute` (`form-route-state`, `edit!`, `create!`, `route-denied?`) and `rad-app/install-statecharts!`.
   - That namespace was deleted from this lib, so the name resolves to upstream's dynamic-routing namespace, which is still on the classpath. `rroute/route-to!` silently drives DR; everything else fails to compile.
   - The migration guide's "New Namespaces" table lists 11 namespaces that don't exist.
   - The guide's `{:route/params #{:account/id}}` example replaces the `#{:id}` default, which breaks URL restore for forms.
10. **`pom.xml` (what `mvn deploy` publishes) doesn't match `deps.edn`.**
    - pom has: fulcro 3.9.3, statecharts RC13, fulcro-rad 1.6.22, guardrails 1.2.16, a stray encore, and no promesa 11 pin. deps.edn has 3.10.0-RC2, RC18, 1.6.24, 1.3.2 and promesa 11. [me]
    - The lib requires fulcro namespaces (`algorithms.macro-support`, `algorithms.core`) that don't exist in 3.9.3. [V by agent]

## 3. P1: features users will hit

### Forms
- **Route-denied / abandon regressed.** [V]
  - Nothing sends `:event/route-denied`, so `fo/confirm`, `fo/silent-abandon?` and `:ui/route-denied?` are dead.
  - `clear-route-denied!` and `continue-abandoned-route!` are missing, though upstream plugins call them.
  - The default now silently blocks navigation unless the app renders `scr/route-denied?` UI. Upstream used `js/confirm`.
  - `form-busy?` ignores `silent-abandon?` and `read-only?` (`form.cljc:412`).
  - Force-continuing away from a dirty form leaves the dirty values in normalized state (no pristine restore), so reports show unsaved edits.
- **Cancel ends the form session.** `:state/leaving` is final. With `cancel-route :none`, `embedded?`, `start-form!`, or `:back` without URL sync, the form stays on screen with no session behind it. [V]
- **The `on-change` trigger is not called for add-row or delete-row** (`form.cljc:1188-1226`). [V]
- **`autocreate-on-load?` runs too late.** It runs after `add-form-config*`/mark-complete (`form.cljc:926-934`), so the new child has no form config, and the parent is dirty right after load, which blocks routing. [V]
- **View mode was removed** (`view!`, `view-mode?`). This was deliberate per `statechart/CLAUDE.md`, but it is a parity gap. [V]
- **Picker preload is incomplete** (`form.cljc:936-953`). [V]
  - Subform pickers are skipped.
  - `po/*` options set only on the attribute are skipped.
  - Normalized props, not UI props, are passed to `po/query-parameters`.
- **Delete has two problems.** [V]
  - `delete!` sends `com.fulcrologic.rad.statechart.form/delete-entity`, while the standard server wiring (upstream `form/resolvers`) registers `com.fulcrologic.rad.form/delete-entity`. Save already uses the upstream symbol. It's a one-line fix.
  - `delete!` is a no-op in CLJ (`form.cljc:1515`).
- **Trigger timing changed.** `:started` now fires before the load/create merge, and `on-change` sees the pre-change state-map. [V]
- **Reload double-loads, and is not blocked on a tempid.** [V]
- **CLJS edits are async.** `input-changed!` uses async `scf/send!` where upstream used `trigger!!`, so expect controlled-input cursor jumps. [S]
- **`form-route-state` exit-fn cleanup is wrong.** It deletes the wrong key, and `::form-session-ids` probably leaks. [V/S]

### Reports and controls
- **Server-paginated `:event/run` doesn't reset anything**: page cache, total, page and point-in-time are all kept. So results are stale after a filter change. [V]
- **Server-paginated single-row view is missing.** [V]
- **`:ui/busy?` stays true after a failed load**, in all three report charts. [V]
- **Resuming with a still-valid cache skips parameter re-initialization**, so new route params and control defaults are ignored (`report.cljc:616`). [V]
- **Hook signatures changed silently**, while the upstream `ro/` docstrings users read still describe `uism-env`. [V]
  - `report-loaded`/`before-load` now take and return a state-map.
  - `load-cache-expired?` and a `page-size` fn receive nil.
  - `ro/post-process` is never read.
  - `ro/columns-EQL` is ignored by the row query.
- **Variant option keys moved namespace**: `point-in-time-view?`, `direct-page-access?`, `chunk-size`. The upstream keys are silently ignored. [V]
- **`report/reload!` and the report-namespace `set-parameter!` are missing**, though the `control_options` docstring references `reload!`. [V]
- **Report `:on-change` triggers can't be expressed.** The macro rejects `ro/triggers` and points to `sro/triggers`, which nothing reads. [V]

### Routing and URLs
- **`fo/route-prefix`, `ro/route` and `co/route` are ignored**, so URLs look like `/AccountForm?_p=<base64-transit>`. The project's own spec required mapping these to `:route/segment`, and fulcro-with-statecharts sets `:route/segment` by hand on every route. [V]
- **Report URL sync is partial.** [V]
  - Only the standard report syncs, and only `:ui/parameters`.
  - `replace-route-params!` overwrites the whole map, so global control values drop out of the URL at the first `:state/ready`.
  - `set-parameter` doesn't update the URL (TODO at `control.cljc:73`).
  - `ro/track-in-url?` is ignored.
  - Container children sync to the wrong route target.
  - Server-paginated and incremental reports don't sync at all.
- **After a create is saved, the new URL is pushed rather than replaced.** Pressing Back lands on the tempid URL, which opens a blank create form. Fix: use `scr/replace-route-params!`. [V]
- **`report-route-state` passes the whole routing data model as route params**, including the `:fulcro/state-map` snapshot, and that gets stored in the report session's local data in the DB (`report.cljc:1129`). [V]
- **`container-busy?` blocks routing while any child report is loading**, which shows the "unsaved changes" prompt. Upstream never did this. [V]
- **RAD helpers write only to the root routing session.** That is wrong when RAD routes live inside an istate child module. [V]
- **No URL behavior is tested.** `create-test-app-with-url-sync` exists but is unused, and the demo installs URL sync only in CLJS (`system.cljc:32`). `simulated_history` works in CLJ. [V]
- **`install-url-sync!` documents a `:prefix` option that scr ignores** (`application.cljc:65`). [V]
- **No statecharts upgrade work is needed for routing**: RC18→RC19 only changes the promesa shim. [V]

### Documented options that are never read [V]
- **Form:** `sfo/on-started`, `sfo/on-saved`, `sfo/on-save-failed`, `sfo/statechart-id`.
- **Report:** `sro/triggers`, `sro/on-loaded`, `sro/statechart-id`, `ro/route`.
- **Container:** `co/route`, `co/layout`, `co/layout-style`, `co/title`.
- **Undocumented but working:** `:on-blur` in `sfo/triggers`.

## 4. Subsystems / ecosystem

| Area | Status |
|---|---|
| **Browser rendering plugin** | **None works with this engine.** [V]<br>• `fulcro-rad-semantic-ui` main is UISM-only, and its `statecharts` branch targets an abandoned RAD 2.0 design.<br>• `fulcro-rad-react-bootstrap` is UISM-only.<br>• fulcro-with-statecharts has an unpublished SUI *bridge* pinned to lib 0.1.3. It still loads upstream UISM form/report/control and uses React hooks for data in the picker and modals.<br>• fulcro-rad-tui targets this lib but is terminal-only. |
| **Rendering contracts** | • Controls have two incompatible contracts: the statechart multimethod `[type style inst key]` and upstream's map-based `[owner key]`.<br>• Containers and `::type->style->formatter` have no multimethod hook.<br>• `ro/layout-style` and `ro/row-style` do nothing, because the multimethods dispatch on `rro/style`. |
| **Headless plugin coverage** | • Controls: only `:button`, `:string`, `:boolean`.<br>• Fields missing: `:pick-many`, autocomplete, `:keyword`, `:uuid`, text/password/multi-line, `:USD`, blob.<br>• No quick-create.<br>• Ignores `ro/links`, `row-heading`, `rotate?`, `on-select-row` and row-action `:visible?`. |
| **rad-hooks** (`use-form`, `use-report`) | **Unusable** (it is UISM). You lose embedded and modal forms, master-detail and quick-create editing. Every existing replacement uses React hooks for data, which breaks your rule. The compliant design is a parent chart invoking the form/report chart. |
| **Auth** | The client side is a UISM and can't talk to statechart sessions. Server-side redaction still works. Only app-level login charts exist (in fulcro-with-statecharts), not a library. |
| **Blob** | **Works.** It has no UISM, and the engine honors the keys it uses. The docs wrongly say it was removed. The gaps are the headless renderer and the CLJS-only `upload-file!`. |
| **dynamic generator** | Unusable: it depends on dynamic routing, and the routing chart is static. |
| **pathom3** | Works (server-only). The docs are misleading about this. |
| **Transitive UISM** | UISM is still loaded, via `report.impl` → `rad.control` and via `rad.application`. |
| **Other docs** | `DevelopersGuide.adoc`, `datomic.adoc` and `CHANGELOG.md` are unmodified upstream copies, and `make publish` would rsync this repo's guide over the live RAD book. Stale: `cleanup-analysis.md`, several CLAUDE.md files, and the clj-kondo export, which lints only the upstream macros. |

## 5. Demo vs `../fulcro-rad-demo`

**Ported:**
- Account form, minus avatar/files blobs, tags, and timezone.
- Address, item and invoice forms, including line items.
- InventoryReport and InvoiceReport. The demo notes call these "new", but they come from the originals in `item_forms`/`invoice_forms`.
- AccountInvoices.

**Missing:**
- Auth/login.
- AccountList report: filter, show-inactive toggle, enable/disable row actions.
- Master-detail. It has no statechart port anywhere; fulcro-with-statecharts's version is still UISM.
- SalesReport and RealSalesReport: pivot/rotate, instant and picker controls, charts.
- Dashboard container (blocked by P0-6).
- Blob avatar/files with the server-side blob store; tags pick-many with quick-create; timezone autocomplete.
- Dropped resolvers: item→category and line-item→category. Existing line items load with no category.
- Removed report pieces: row actions, New buttons, the category filter.

**Runnability:**
- It builds and is served by a hiccup page.
- The UI is unstyled plain HTML from the headless plugin, with triggers broken (P0-7). It isn't a usable browser demo.
- The quick-create path writes `:time-zone/zone-id`, which isn't in the model. [S]

**Donors:**
- **fulcro-with-statecharts** is the best source: SUI bridge, login chart, full AccountList/InvoiceList/Inventory, timezone, and the blob server side.
- **fulcro-rad-statecharts-demo** is an abandoned three-commit namespace swap. It still uses dynamic routing and deleted namespaces, so it is only useful as source text for the sales and dashboard UIs.

## 6. Recommended order

1. **Fix the library P0s (§1).** Most are small and local:
   - chart-id registration;
   - #29 and #30;
   - option pass-through for routed forms;
   - variant-aware report start, plus the incremental aliases;
   - `container-route-state` plus a container render multimethod;
   - make the headless renderer go through `input-changed!`;
   - the `:default` method collision.
2. **Fix the demo E2E helpers**: real session id, polling, the account-id param. Then add a `:demo` suite to `tests.edn` and CI so regressions surface. Use `simulated_history` to add URL tests.
3. **Fix the docs and `pom.xml`** (§2) before any release.
4. **Make the rendering-plugin decision.** Promote the fulcro-with-statecharts SUI bridge into a real multimethod plugin with no upstream UISM or React-hooks data. This blocks a usable browser demo.
5. **Build a statechart replacement for rad-hooks** (an invoked child form/report chart). This unblocks master-detail, modals and quick-create editing.
6. **Port the remaining demo screens** (§5), plus auth.

**Open decisions:**
- Do view mode and `fo/confirm`/`silent-abandon?` come back, as the parity goal implies?
- Does the browser plugin live in this repo or in fulcro-rad-semantic-ui?
