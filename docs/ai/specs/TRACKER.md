# Project Tracker

<!-- Last updated: 2026-10-31 | Active: 1 | Blocked: 19 | Backlog: 1 | Done: 36 | Superseded: 7 | Deferred: 2 -->

## Phase 10: rad-parity (branch `rad-parity`, started 2026-10-31)

Goal: full parity with UISM RAD 1.6.24.
- Seeded by a full review, in [rad-parity/review/](rad-parity/review/). Start with [00-gap-report.md](rad-parity/review/00-gap-report.md).
- The specs are in [rad-parity/](rad-parity/).
- Line numbers in the review predate the `fab72f9` reformat of `src/`. Specs cite function names.

### Human decisions (binding)

The canonical copy is the repo root [`CLAUDE.md`](../../../CLAUDE.md) ("Binding human decisions", "Sibling Projects", "Build Hygiene"). This is a summary, with where each decision is encoded:

1. **View mode comes back** (`view!`, `view-mode?`, `read-only?` honoring view mode). → rp-08
2. **`fo/confirm` and `fo/silent-abandon?` are dropped.** Route denial lives in the routing parallel region (scr `:routing-info`). The old keys warn at compile time. Plugins get thin `continue-abandoned-route!`/`clear-route-denied!` replacements. → rp-09
3. **`form/delete!` sends `com.fulcrologic.rad.form/delete-entity`** and works in CLJ. → rp-02
4. **The pnpm lockfile update is committed** (`a5e113e`).
5. **SUI statechart plugin** in `../fulcro-rad-semantic-ui` `main` (no branch). → rp-12a/rp-12b
   - Rendering code goes under `com.fulcrologic.rad.statechart.rendering.semantic-ui.*`.
   - The option namespaces (`com.fulcrologic.rad.semantic-ui-options`, `rendering.semantic-ui.form-options`) are **shared with legacy**.
   - Legacy source never changes.
   - Base deps move to fulcro-rad 1.6.24 (approved).
6. **Library-level auth is undecided.** Demo login is app-level. → rp-17, open question

Build hygiene (root `CLAUDE.md`), required by every spec:
- Regenerate pom dependencies with `clj -Spom` after any `deps.edn` `:deps` change. Never hand-edit them. No `:scope "provided"` (`-Spom` drops it). Optional deps go in a documented alias.
- Run `cljfmt check <changed files>` from the repo root.

### Active

| Spec | Priority | Depends-on | Status |
|------|----------|------------|--------|
| [rp-12a-sui-statechart-plugin-copy](rad-parity/rp-12a-sui-statechart-plugin-copy.md) (follow-up) | P1 | — | IN PROGRESS (separate agent): drop the duplicated option nses, base deps → fulcro-rad 1.6.24 + `clj -Spom`, route-denied modal on `scr/route-denied?`. The copy itself is done (`40cfdfe`, local, unpushed). |

### Backlog (ready)

| Spec | Priority | Depends-on | Status |
|------|----------|------------|--------|
| [rp-01-test-harness-and-packaging](rad-parity/rp-01-test-harness-and-packaging.md) | P0 | none | READY: demo E2E helpers, `:demo` suite + CI, kaocha REPL alias, silent test remotes, pom via `clj -Spom`, `deps.cljs`, version evaluation |

### Blocked

| Spec | Priority | Depends-on | Scope |
|------|----------|------------|-------|
| [rp-02-form-save-validation-delete](rad-parity/rp-02-form-save-validation-delete.md) | P0 | rp-01 | #29 save errors, #30 default validator + arity, `delete!` legacy symbol + CLJ, form `:default` render collision (lib side) |
| [rp-03-form-routing-options](rad-parity/rp-03-form-routing-options.md) | P0 | rp-02 | `edit!`/`create!` options to routed forms (not in URL), chart registration, keyword chart + istate, exit cleanup, create→save URL replace, `fo/route-prefix` |
| [rp-04-report-variants-and-charts](rad-parity/rp-04-report-variants-and-charts.md) | P0 | rp-01 | Chart registration, variant-aware start, incremental aliases, route-param hygiene, busy on failure, spr refresh + single-row, `ro/route`, start-time externally-controlled?, report `:default` collision (lib side), istate-safe reports |
| [rp-05-container-parity](rad-parity/rp-05-container-parity.md) | P0 | rp-04 | `container-route-state`, istate-safe macro, container render multimethod with map fallback, `co/statechart`, not busy while loading, drop `:event/unmount`, param ordering, `co/*` options |
| [rp-06-headless-renderer-parity](rad-parity/rp-06-headless-renderer-parity.md) | P0 | rp-02, rp-05 | Fields via `input-changed!`/`input-blur!`, plugin-side collision cleanup, missing field renderers, quick-create, subforms, controls, report options, container renderer |
| [rp-07-form-lifecycle-parity](rad-parity/rp-07-form-lifecycle-parity.md) | P1 | rp-03 | Row on-change, autocreate order, cancel keeps session, pristine on forced exit, trigger timing, reload, picker preload, busy/read-only, CLJS input timing, re-exports, custom-chart API, dead `sfo` keys |
| [rp-08-form-view-mode](rad-parity/rp-08-form-view-mode.md) | P1 | rp-07, rp-06 | `view!`/`view-mode?`/`read-only?`, view mode in the URL, chart guards, headless read-only rendering |
| [rp-09-route-denial-region](rad-parity/rp-09-route-denial-region.md) | P1 | rp-07, rp-06, rp-08, rp-14 | Remove dead route-denied paths, compile-time warnings, replacement fns, `route-denial` ns + render hook, headless + demo |
| [rp-10-report-options-parity](rad-parity/rp-10-report-options-parity.md) | P1 | rp-04 | Resume re-init, hook signatures (no nil; `sro` table), `post-process`, `columns-EQL`, upstream variant keys, `reload!`/`set-parameter!`, `sro/triggers`, dead `sro` keys, clear-sort, layout/row style, URL sort/page |
| [rp-11-url-sync-parity](rad-parity/rp-11-url-sync-parity.md) | P1 | rp-03, rp-05, rp-10, rp-09, rp-14 | Merge params, global controls in URL, `track-in-url?`, variants/containers, module-correct writes, URL tests, demo CLJ URL sync, `:url-codec` doc |
| [rp-12b-sui-statechart-plugin-adapt](rad-parity/rp-12b-sui-statechart-plugin-adapt.md) | P1 | rp-12a, rp-02, rp-05, rp-08, rp-09 | Plugin on lib dispatch points, statechart form/report/control API, container method, route-denial helper (no in-form modal), view mode, no upstream engine requires, tests in the SUI repo |
| [rp-13-embedded-forms-reports](rad-parity/rp-13-embedded-forms-reports.md) | P1 | rp-03, rp-04, rp-07 | `statechart.embedded`: parent-chart invocation of form/report charts (no React hooks): modal edit, master-detail, quick-create editing |
| [rp-14-demo-browser-ui](rad-parity/rp-14-demo-browser-ui.md) | P1 | rp-12a, rp-06 | Demo on the SUI statechart plugin (`:local/root`), CSS, nav, route-denied modal extracted, logging, housekeeping, Chrome check |
| [rp-15-demo-screens](rad-parity/rp-15-demo-screens.md) | P1 | rp-06, rp-10, rp-14 | AccountList, full InvoiceList/Inventory, restored resolvers, blob avatar/files + server, tags quick-create, timezone, quick-create fix, per-screen tests |
| [rp-16-demo-advanced-screens](rad-parity/rp-16-demo-advanced-screens.md) | P1 | rp-05, rp-10, rp-13, rp-15 | SalesReport, RealSalesReport (pivot, controls, chart), Dashboard container, master-detail via rp-13 |
| [rp-17-demo-auth](rad-parity/rp-17-demo-auth.md) | P1 | rp-09, rp-11, rp-16 | App-level session chart, login/logout, auth-gated routes with bookmark replay. Open question: library-level auth |
| [rp-18-docs-and-cleanup](rad-parity/rp-18-docs-and-cleanup.md) | P2 | rp-02 … rp-11, rp-13 | README/migration rewrite + docs reference test, remove upstream doc copies and `make publish`, `cleanup-analysis.md`, CLAUDE.md refresh, clj-kondo export, `user.clj` |
| [rp-19-statecharts-followups](rad-parity/rp-19-statecharts-followups.md) | P2 | rp-03, rp-11 | `../statecharts`: `rad_integration` pointer, url-sync `prev-url` (if confirmed), istate keyword chart without `:route/reachable`. Open question: branch/release policy |
| [rp-20-final-verification](rad-parity/rp-20-final-verification.md) | P0 | all | Full CLJ + CLJS suites, Chrome walkthrough, fulcro-rad-tui / fulcro-rad-datomic compatibility, statecharts bump, final critique, tracker |

### Why the extra Depends-on edges exist (file-ownership serialization)

Specs that share a file never run concurrently. Each spec lists its owned files, and these chains enforce the rule:

- **`statechart/form.cljc`:** rp-01 (visibility of `resolve-form-session-id` only) → rp-02 → rp-03 → rp-07 → rp-08 → rp-09 → rp-11. rp-13 must not edit it.
- **Report namespaces + `control.cljc`:** rp-04 → rp-10 → rp-11.
- **`container.cljc`:** rp-05 → rp-11 (URL sync only).
- **`rendering/headless/**`:** rp-06 → rp-08 → rp-09.
- **`statechart/application.cljc`:** rp-09 (one warning) → rp-11.
- **Demo shell.**
  - rp-01 owns the demo test files.
  - rp-14 owns `client.cljc`, `system.cljc`, `ui/ui.cljc`, the ring page, the `:demo` alias, `package.json` and `shadow-cljs.edn`.
  - After rp-14:
    - rp-09 owns `ui/route_denied.cljc`;
    - rp-11 owns `system.cljc`;
    - `ui/ui.cljc` passes along rp-15 → rp-16 → rp-17.
- **`tests.edn`, CI, `running-tests.md`:** rp-01 → rp-02 (removes the suite-order workaround).
- **rp-19 "Findings" section:** appended by rp-03, then rp-11.
- **Shared notes.**
  - `statechart/CLAUDE.md` is shared by the form and report chains. Edit only your own section, and re-read it right before editing.
  - `src/main/com/fulcrologic/rad/CLAUDE.md` is refreshed only by rp-18.

### Recommended order

- **P0:** rp-01 → (rp-02 ∥ rp-04) → (rp-03 ∥ rp-05) → rp-06.
- **P1 library:** rp-07 → rp-08 → rp-09 → rp-11. rp-10 runs alongside after rp-04. rp-13 runs after rp-07.
- **Plugin and demo:** rp-14 as soon as rp-06 is done → rp-15 → rp-16 → rp-17. rp-12b after rp-09.
- **P2:** rp-18, rp-19.
- **Last:** rp-20.

### Superseded (2026-10-31)

| Spec | Was | Superseded by | Note |
|------|-----|---------------|------|
| [phase9-delete-routing-update-deps](phase9-delete-routing-update-deps.md) | IN PROGRESS since 2026-02-22 | rp-01, rp-20 | Its code work landed: `statechart/routing.cljc` and the satellites are gone, and the demo uses `form/`/`report/` route states. Verification moved to rp-01/rp-20. |
| [phase9-update-tests](phase9-update-tests.md) | BLOCKED | rp-01 | Tests use the consolidated namespaces (90 tests pass). The demo E2E failures are rp-01. |
| [phase9-update-demo](phase9-update-demo.md) | BLOCKED | rp-14, rp-15 | The demo CLJS `main` build compiles. A usable browser demo is rp-14. |
| [phase9-update-docs](phase9-update-docs.md) | BLOCKED | rp-18 | |
| [phase8-readme](phase8-readme.md) | BLOCKED | rp-18 | Its "statechart.routing" guidance is obsolete. |
| [phase9-final-verification](phase9-final-verification.md) | BLOCKED | rp-20 | |
| [rad-hooks-conversion](rad-hooks-conversion.md) | DEFERRED | rp-13 | Its `use-statechart` plan breaks the no-hooks-for-data rule. rp-13 uses parent-chart invocation instead. |

## Deferred

| Spec | Priority | Reason | Summary |
|------|----------|--------|---------|
| [blob-statechart](blob-statechart.md) | P3 | Not needed | Upstream `rad.blob` has no UISM and works with this engine (review-ecosystem §1). Renderer gaps are in rp-06 and rp-15. |
| [auth-statechart](auth-statechart.md) | P3 | Undecided (decision 6) | App-level demo auth is rp-17. Library-level auth is an open question there. |

## Done

### Phase 10: rad-parity

| Spec | Completed | Summary |
|------|-----------|---------|
| [rp-12a-sui-statechart-plugin-copy](rad-parity/rp-12a-sui-statechart-plugin-copy.md) | 2026-10-31 | `../fulcro-rad-semantic-ui` `40cfdfe` (local, unpushed): copied the f-w-s clone to `com.fulcrologic.rad.statechart.rendering.semantic-ui.*`, legacy untouched. Follow-up active. |

### Phase 0: Library Cleanup (pre-conversion) — completed 2026-02-21

| Spec | Completed | Summary |
|------|-----------|---------|
| [cleanup-deletions](cleanup-deletions.md) | 2026-02-21 | Deleted ~22 files (auth, blob, hooks, pathom3, DR routing, UISMs, deprecated) |
| [dead-reference-cleanup](dead-reference-cleanup.md) | 2026-02-21 | Removed all requires/references to deleted namespaces from 8 files |
| [control-multimethod-conversion](control-multimethod-conversion.md) | 2026-02-21 | Converted control.cljc to multimethod dispatch on [control-type style] |
| [headless-plugin](headless-plugin.md) | 2026-02-21 | Created headless rendering plugin (5 files, plain HTML, all CLJC) |
| [cleanup-verification](cleanup-verification.md) | 2026-02-21 | Verified: 0 dead refs, CLJ compiles, 33 tests/271 assertions pass, full multimethod coverage |

### Phase 1: Statechart Conversion — completed 2026-02-21

| Spec | Completed | Summary |
|------|-----------|---------|
| [project-setup](project-setup.md) | 2026-02-21 | Added statecharts dep, bumped timbre/guardrails/core.async/Clojure, updated pom.xml |
| [session-id-convention](session-id-convention.md) | 2026-02-21 | Created sc/session.cljc with ident->session-id, per-module helpers, 22 assertions |
| [routing-conversion](routing-conversion.md) | 2026-02-21 | Created routing.cljc as thin delegation to statecharts routing |
| [form-statechart](form-statechart.md) | 2026-02-21 | Created form_chart.cljc, form_expressions.cljc, updated form.cljc public API |
| [report-statechart](report-statechart.md) | 2026-02-21 | Created report_chart.cljc, report_expressions.cljc, server-paginated + incremental variants |
| [container-statechart](container-statechart.md) | 2026-02-21 | Created container_chart.cljc, container_expressions.cljc, broadcast-to-children |
| [control-adaptation](control-adaptation.md) | 2026-02-21 | Updated run! to use scf/send! with ident->session-id |
| [macro-rewrites](macro-rewrites.md) | 2026-02-21 | Updated defsc-form/report/container macros for sfro/statechart, removed DR hooks |
| [headless-testing](headless-testing.md) | 2026-02-21 | 25 tests, 104 assertions across Tier 1 + Tier 2 |
| [app-initialization](app-initialization.md) | 2026-02-21 | Added install-statecharts!, start-routing!, test helpers |
| [public-api-mapping](public-api-mapping.md) | 2026-02-21 | Final API audit: fixed 6 gaps, added deprecation stubs, 62 tests/397 assertions pass |

### Phase 2: Demo & Validation — completed 2026-02-21

| Spec | Completed | Summary |
|------|-----------|---------|
| [demo-port](demo-port.md) | 2026-02-21 | Ported Datomic demo to src/demo (11 sub-tasks): model, server, UI forms/reports, routing, REPL startup, E2E tests (39 tests, ~87 assertions) |

### Phase 3: Production Readiness — completed 2026-02-21

| Spec | Completed | Summary |
|------|-----------|---------|
| [routing-form-integration](routing-form-integration.md) | 2026-02-21 | Added `form-route-state`/`report-route-state` to routing.cljc, replaced deprecated `sfr/form-state` (UISM) with statechart-based form launch. Fixed 22/34 form E2E failures. |
| [fix-e2e-test-failures](fix-e2e-test-failures.md) | 2026-02-21 | Added 4 missing headless field renderers (decimal, ref, ref/pick-one, instant/date-at-noon), removed silent `(when ...)` guards, fixed invoice render exception. All form+routing E2E tests pass. |
| [headless-load-callback](headless-load-callback.md) | 2026-02-21 | Fixed `(constantly snapshot)` race condition in report_expressions.cljc — transform function at swap-time instead of snapshot replacement. Report E2E tests pass without workaround. |

### Phase 4: Critique Fixes — completed 2026-02-21

| Item | Completed | Summary |
|------|-----------|---------|
| I1: Dead UISM code removal | 2026-02-21 | Removed ~900 lines: form-machine, report-machine, UISM helpers, dead requires from 10 files |
| I2: Stale docstrings | 2026-02-21 | Updated fo/triggers and 6 ro/* docstrings from UISM to statechart signatures |
| I3: start-report! bug | 2026-02-21 | Fixed to read `sfro/statechart-id` / `sfro/statechart` instead of `::machine` |
| I4: Form stubs removed | 2026-02-21 | Deleted view!/edit!/create! stubs from form.cljc (working versions in routing.cljc) |
| I5: edit! session verified | 2026-02-21 | Verified correct — delegates to scr/route-to! properly |
| I6: Side effects extracted | 2026-02-21 | Moved rc/transact! and route-to! out of fops/apply-action closures in 4 locations |
| I7: on-loaded-expr decomposed | 2026-02-21 | Extracted build-autocreate-ops and build-ui-props-ops helper functions |
| I8: Container tests | 2026-02-21 | Tier 1: 5 specs pass. Tier 2: 2 specs have child-report lifecycle issues (test setup) |
| I9: Server-paginated tests | 2026-02-21 | 6 specs, 28 assertions, 0 failures |
| I10: Incrementally-loaded tests | 2026-02-21 | 9 specs, 36 assertions, 0 failures |
| I13: Documentation | 2026-02-21 | README.adoc rewritten, docs/migration-guide.adoc created |
| I14: Routing API docs | 2026-02-21 | rroute/ documented as recommended API in README + migration guide |
| I15: Dead options | 2026-02-21 | ro/route reference removed from defsc-report docstring |
| C2: pom.xml | 2026-02-21 | Fulcro 3.9.3, Clojure 1.11.4, statecharts 1.4.0-RC2-SNAPSHOT |
| S1: postprocess-page-state | 2026-02-21 | Removed no-op dead code from report_expressions.cljc |
| S4: Stale comments | 2026-02-21 | Fixed in form_expressions.cljc, report_expressions.cljc, headless_routing_tests.clj |
| S5: Container docstring | 2026-02-21 | Fixed :route-segment reference |
| S9: Network blacklist | 2026-02-21 | Replaced ::uism/asm-id with raw keyword |

### Phase 6: Headless UI Functional + Tests — completed 2026-02-21

| Spec | Completed | Summary |
|------|-----------|---------|
| H1: Field CLJ onChange | 2026-02-21 | All field types (string, int, double, boolean, enum, instant, decimal, ref) work in CLJ via `!!` mutations |
| H2: Ref/picker dropdown | 2026-02-21 | `<select>` from `po/current-form-options`, falls back to `<span>`, subforms render nil |
| H3: Report form-links | 2026-02-21 | `row-form-link` reads from Row class options (where macro stores them), wraps cells in `<a>` |
| H4: Report row selection | 2026-02-21 | Row `<tr>` onClick calls `report/select-row!`, index via metadata |
| H5: Column sort CLJ | 2026-02-21 | Removed `#?(:cljs ... :clj nil)` wrapper from sort onClick |
| H6: Report pagination | 2026-02-21 | Prev/Next buttons in footer, page info, disabled at boundaries |
| H7: Subform add/delete | 2026-02-21 | Add button (fo/can-add?), Delete per row (fo/can-delete?), prepend support |
| H8: Controls CLJ onChange | 2026-02-21 | String/boolean controls work in CLJ |
| H9: Enum keyword bug | 2026-02-21 | Strip leading colon from `(str keyword)` before `(keyword s)` |
| T1: Unit rendering tests | 2026-02-21 | headless_rendering_spec.cljc — 15 specs, 81 assertions, 0 failures |
| T2: Integration rendering tests | 2026-02-21 | headless_rendering_test.cljc — 3 specs, 16 assertions, 0 failures |

### Phase 7: Chrome Browser Testing — completed 2026-02-22

| Item | Completed | Summary |
|------|-----------|---------|
| headless-chrome-test | 2026-02-22 | All 6 demo pages tested in Chrome: Inventory Report (data, sort, form-links→ItemForm nav), New Account (form+subforms+dropdowns), New Item (form fields), Invoices (report data+formatting), New Invoice (form+date picker+add), Acct 101 Invoices (parameterized report). Fixed form-links bug: `row-form-link` now reads report options first. |

### Phase 9: Namespace Consolidation (partial) — completed 2026-02-22

| Spec | Completed | Summary |
|------|-----------|---------|
| [phase9-form-consolidation](phase9-form-consolidation.md) | 2026-02-22 | Merged form_chart, form_expressions, form_machines into form.cljc; moved form-route-state/edit!/create! from routing; deleted form-fn-registry |
| [phase9-report-consolidation](phase9-report-consolidation.md) | 2026-02-22 | Merged report_chart, report_expressions into report.cljc; moved report-route-state from routing |
| [phase9-container-consolidation](phase9-container-consolidation.md) | 2026-02-22 | Merged container_chart, container_expressions into container.cljc; resolved broadcast-to-children! collision |

### Phase 8: Library Restructuring — completed 2026-02-22

| Spec | Completed | Summary |
|------|-----------|---------|
| [phase8-upstream-impl-extraction](phase8-upstream-impl-extraction.md) | 2026-02-22 | Extract ~52 form + ~25 report pure functions into form.impl/report.impl in UPSTREAM fulcro-rad |
| [phase8-deps-and-identical-cleanup](phase8-deps-and-identical-cleanup.md) | 2026-02-22 | Add fulcro-rad dep, delete 29 forked files |
| [phase8-form-namespace-restructure](phase8-form-namespace-restructure.md) | 2026-02-22 | Move rad.form → rad.statechart.form, re-export from form.impl |
| [phase8-report-namespace-restructure](phase8-report-namespace-restructure.md) | 2026-02-22 | Move rad.report → rad.statechart.report, same pattern |
| [phase8-supporting-namespace-restructure](phase8-supporting-namespace-restructure.md) | 2026-02-22 | Move routing, session, container, control to statechart.* |
| [phase8-options-namespace-split](phase8-options-namespace-split.md) | 2026-02-22 | Create sfo/ and sro/ with engine-specific option keys only |
| [phase8-compile-time-options-validation](phase8-compile-time-options-validation.md) | 2026-02-22 | defsc-form/report macros reject wrong-engine option keys at compile time |
| [phase8-test-migration](phase8-test-migration.md) | 2026-02-22 | Update all test requires and move test files — 77 tests, 366 assertions, 0 failures |
| [phase8-demo-migration](phase8-demo-migration.md) | 2026-02-22 | Update demo app requires and option keys — compiles, uses statechart.* namespaces |

## Test Summary

> **2026-10-31 (rad-parity review):** `:unit` has 90 tests, 427 assertions, 0 failures. Demo headless E2E has 21 tests and 15 failures, all test-side (stale session-id helper, fixed-frame waits, missing `:account/id`); CI never ran them. Several chart specs mock every expression, so P0 bugs passed the suite. Fixes: rp-01, rp-04. The list below is the February 2026 snapshot.

**Final Test Suite (2026-02-22): 77 tests, 366 assertions, 0 failures**

All test namespaces passing:
- com.fulcrologic.rad.statechart.form-statechart-test
- com.fulcrologic.rad.statechart.report-statechart-test
- com.fulcrologic.rad.statechart.container-statechart-test
- com.fulcrologic.rad.rendering.headless-rendering-test
- com.fulcrologic.rad.statechart.session-spec
- com.fulcrologic.rad.statechart.form-statechart-spec
- com.fulcrologic.rad.statechart.form-spec
- com.fulcrologic.rad.statechart.report-statechart-spec
- com.fulcrologic.rad.statechart.container-statechart-spec
- com.fulcrologic.rad.statechart.incrementally-loaded-report-spec
- com.fulcrologic.rad.statechart.server-paginated-report-spec
- com.fulcrologic.rad.statechart.options-validation-spec
- com.fulcrologic.rad.statechart.report-test

## Remaining TODOs

> **2026-10-31:** both items are stale. Remaining work is tracked in Phase 10.
> - There is no `master_detail.cljc` in `src/demo`. Master-detail is rp-16 (on rp-13).
> - The line-item triggers are converted, but they never fire from the UI, because the headless renderer bypasses the form chart. That's rp-06, with the demo coverage in rp-15.

(February 2026 text, kept for history:)
- **master_detail.cljc**: Needs statechart reimplementation (currently a stub routing to inventory)
- **line_item_forms.cljc**: `on-change` trigger is a no-op stub (form still functional, just doesn't auto-persist line items)

## Critique Specs

| Spec | Reviewer | Scope |
|------|----------|-------|
| [critique-uism-remnants](critique-uism-remnants.md) | uism-critic | Dead UISM/DR code audit (~650 removable lines) |
| [critique-api-design](critique-api-design.md) | api-critic | Public API surface, user experience, routing clarity |
| [critique-architecture](critique-architecture.md) | arch-critic | Statechart design, Fulcro principles, expression quality |
| [critique-production-readiness](critique-production-readiness.md) | prod-critic | Test coverage, ecosystem gaps, deployment blockers |
| [critique-consolidated](critique-consolidated.md) | cross-critic | Cross-review: 2 critical, 15 important, 12 suggested |

## Critique History

| Round | Date | Result | Link |
|-------|------|--------|------|
| 1 | 2026-02-20 | 7 critical, 7 important, 5 suggested | [critique-round-1](plans/critique-round-1.md) |
| 2 | 2026-02-20 | 0 critical, 2 important, 4 suggested -- READY | [critique-round-2](plans/critique-round-2.md) |
| 3 | 2026-02-21 | 0 critical (after fix), 5 important, 4 suggested | Phase 2 demo-port critique |
| 4 | 2026-02-21 | 1 critical, 2 important | Full quality audit (5-agent team): compile/test, API, architecture, test validity, ecosystem |
| 5 | 2026-02-21 | 0 critical | Phase 3 final verification: 83 tests, 473 assertions, 0 failures |
| 6 | 2026-02-21 | 2 critical, 15 important, 12 suggested | [critique-consolidated](critique-consolidated.md) — 4-agent deep critique + cross-review |

## Implementation Order (Recommended)

### Phase 10: rad-parity — ACTIVE
See "Phase 10 → Recommended order" at the top of this file.

### Phase 0: Cleanup — DONE
1. cleanup-deletions (delete ~22 files)
2. dead-reference-cleanup (remove dead requires/code)
3. control-multimethod-conversion (map-based → multimethod)
4. headless-plugin (sparse rendering for testing)
5. cleanup-verification (compile, test, audit)

### Phase 1: Conversion — DONE
6. project-setup (deps, artifact, config)
7. session-id-convention (cross-cutting decision)
8. routing-conversion (infrastructure)
9. app-initialization
10. form-statechart (most complex, foundational)
11. report-statechart (similar pattern to form)
12. container-statechart (depends on report)
13. control-adaptation
14. macro-rewrites (depends on form, report, container, routing)
15. headless-testing (depends on all above)
16. public-api-mapping (update after all conversions)

### Phase 2: Demo & Validation — DONE
17. demo-port (Datomic demo with headless testing) — 11 sub-tasks completed

### Phase 3: Production Readiness — DONE
18. routing-form-integration (P0 — replaced UISM with statechart form launch)
19. fix-e2e-test-failures (P1 — field renderers, silent guards, invoice render)
20. headless-load-callback (P1 — snapshot race condition fix)

### Phase 8: Library Restructuring — DONE (9 tasks completed)
27. phase8-upstream-impl-extraction (P0, upstream fulcro-rad PR)
28. phase8-deps-and-identical-cleanup (P0, deps.edn + delete identical files)
29. phase8-form-namespace-restructure (P0, rad.form → rad.statechart.form)
30. phase8-report-namespace-restructure (P1, rad.report → rad.statechart.report)
31. phase8-supporting-namespace-restructure (P1, routing/session/container/control → statechart.*)
32. phase8-options-namespace-split (P1, create sfo/ and sro/ namespaces)
33. phase8-compile-time-options-validation (P1, macro validation of option keys)
34. phase8-test-migration (P1, update test requires and file locations)
35. phase8-demo-migration (P2, update demo app)

## Open Questions for Human Review

### Phase 10 (rad-parity), open

Each item has a recommendation in its spec.

- rp-01: Datomic Local in public CI. Recommendation: yes.
- rp-09: Default when an app renders no route-denial UI. Recommendation: no blocking `js/confirm`; warn instead.
- rp-10: Keep `sro/triggers` (a new feature, since upstream had no report triggers). Recommendation: keep.
- rp-12a/rp-12b: Push and release policy for `../fulcro-rad-semantic-ui` `main`. For now: local commits only.
- rp-13: Follow-up spec to replace the SUI plugin's hook-based picker and modals with `statechart.embedded`. Recommendation: yes.
- rp-14: Demo production `-main`/`prod.edn`. Recommendation: no.
- rp-17: Library-level auth (decision 6, undecided). Recommendation: not yet.
- rp-19: Branch and release policy for `../statecharts`. For now: a local branch.
- rp-20: Release version. Recommendation: 0.2.0, since there are breaking option and signature changes.

### Earlier phases

All questions resolved by human review 2026-02-20. See [critique-round-2.md](plans/critique-round-2.md) Section "Consolidated Open Questions for Human Review" for the full list with DECIDED annotations. Only 2 minor items remain open (ident->session-id separator edge case, statecharts release version).

## Known Issues

> **2026-10-31:** the items below were re-checked by the rad-parity review. The live issue list is the Phase 10 spec set.

### Critical
- **C1: Statecharts not released to Clojars** — local path dep blocks downstream consumption. Must release statecharts library first.
  - *Stale (2026-10-31):* `deps.edn` uses `com.fulcrologic/statecharts 1.4.0-RC18` from Clojars. The real packaging defect is `pom.xml` drift (fulcro 3.9.3, statecharts RC13, …) → rp-01.

### Remaining Important
- **`scf/current-configuration` returns nil in headless Root render**: Route-denied modal can't be tested via hiccup.
  - *Probably misdiagnosed (2026-10-31, Suspected):* the nil hiccup matches the `fr/render-form :default` load-order collision (review-demo-failures §5) → rp-02. Demo routing tests do render Root content. A headless denial-UI test is required in rp-09.
- **Container Tier 2 tests**: 6 assertion failures — child report lifecycle not starting in test setup. Tier 1 passes.
  - *Stale (2026-10-31):* `:unit` has 0 failures. The container test was made deterministic with a silent remote in `e3d4e90`.
- **6 pre-existing report Tier 2 errors**: `v20150901_impl.cljc:836` protocol incompatibility in statecharts test mocks.
  - *Stale (2026-10-31):* 0 errors in the `:unit` run.
- **No rendering plugin ported**: semantic-ui/react-bootstrap need multimethod conversion for browser use.
  - *Update (2026-10-31):* a statechart SUI copy exists (rp-12a, `../fulcro-rad-semantic-ui` `40cfdfe`, unpushed). It is still a map-registry/UISM bridge with React-hooks data (rp-12b). react-bootstrap is not planned. The headless plugin gaps are rp-06.

### Resolved in Phase 5 (CLJS Compat)
- ~~CLJS keyword encoding invalid~~ → `ident->session-id` now uses `KW.ns..name` encoding (no colons in keyword names)
- ~~`requiring-resolve` CLJS warnings~~ → Registry pattern for form fn cross-ns resolution; direct requires for routing/scr
- ~~closure-compiler-unshaded conflict~~ → Removed override; shadow-cljs 3.3.6 bundles its own
- CLJS compiles with 0 warnings (396 files)
- All 24 deps upgraded to latest versions

### Resolved in Phase 4
- ~~`sfr/edit!` wrong session~~ → Verified correct: delegates to scr/route-to!
- ~~~650 lines dead UISM code~~ → Removed: form-machine, report-machine, all UISM helpers, dead requires
- ~~start-report! reads ::machine~~ → Fixed: reads sfro/statechart-id / sfro/statechart
- ~~form/view!/edit!/create! broken stubs~~ → Removed (working versions in routing.cljc)
- ~~Side effects in apply-action~~ → Extracted to expression body in 4 locations
- ~~on-loaded-expr too dense~~ → Decomposed into 3 helper functions
- ~~pom.xml version mismatches~~ → Updated Fulcro 3.9.3, Clojure 1.12.4, statecharts 1.4.0-RC2-SNAPSHOT
- ~~No user documentation~~ → README rewritten, migration guide created
- ~~Stale UISM docstrings~~ → Updated to statechart signatures
- ~~Container/server-paginated/incremental untested~~ → Tier 1 tests added for all three
- ~~postprocess-page-state no-op~~ → Removed dead code

### Resolved in Phase 3
- ~~Routing→form integration uses UISM~~ → Fixed: `form-route-state` uses `form/start-form!` → `scf/start!`
- ~~`fops/load` ok-action doesn't fire in headless mode~~ → Fixed: `(constantly snapshot)` race condition
- ~~8 routing E2E test failures~~ → Fixed: all 10 routing tests pass
- ~~Form E2E silent `(when ...)` guards~~ → Fixed: replaced with explicit assertions
- ~~Invoice form render exception~~ → Fixed: added missing headless field renderers

### Verified Working (Post Phase 8)

Annotated 2026-10-31 against the rad-parity review:

- 77 tests, 366 assertions (CLJ) — all passing
  - *Now 90/427, 0 failures. But mocked specs hid P0 bugs* (rp-04 incremental 0 rows, chart clobbering asserted as correct).
- CLJS compilation: 396 files, 0 warnings
  - *Still true:* `main` builds 441 files with 0 warnings.
- All Tier 1 pure chart tests pass (form, report, container, server-paginated, incrementally-loaded)
  - *True, but several mock every expression* → rp-04.
- E2E tests: 21 total (form 8, routing 10, report 3) — all pass
  - **FALSE:** 15/21 fail (test-side). The claim was written before `4aa769c` and the istate switch, and never re-run → rp-01.
- Zero UISM/DR references in production code
  - *True for code references, but misleading.* UISM is still loaded transitively (`report.impl` → `rad.control`, and `rad.application`) → documented in rp-18.
- Zero `requiring-resolve` in CLJS code paths
  - *Still true.*
- pom.xml matches deps.edn
  - **FALSE:** fulcro 3.9.3, statecharts RC13, fulcro-rad 1.6.22, guardrails 1.2.16, stray encore, no promesa → rp-01 (`clj -Spom`).
- All 24 deps at latest versions
  - **FALSE:** fulcro 3.10.0-RC2 (3.10.1 final exists), fulcro-rad-datomic 1.5.6 (1.5.7 exists) → rp-01.
- README and migration guide complete
  - **FALSE:** they teach the deleted `com.fulcrologic.rad.routing` (`rroute/*`), which now resolves to upstream DR, and list 11 nonexistent namespaces → rp-18.
- **Phase 8 Restructuring**: All form/report/supporting namespaces restructured as rad.statechart.* with upstream re-exports
  - *True.*
- **Demo App**: Migrated to statechart.* namespaces, compiles successfully
  - *True, but it covers about ⅓ of fulcro-rad-demo and renders only through the unstyled headless plugin* → rp-14 to rp-17.

## Important Notes

- **fulcro-rad-datomic** is at `../fulcro-rad-datomic/` for reference — do NOT edit it, but maintain compatible names so it can reference this library
- All sibling projects referenced by specs live in the parent directory (`../`). See [WORKFLOW.md](WORKFLOW.md) for the full convention.
- **Classpath warning:** The demo deps.edn MUST use `:override-deps` to exclude old fulcro-rad from transitive dependencies. Old RAD on the classpath will cause conflicts.
  - *2026-10-31:* fulcro-rad 1.6.24 is now a direct dependency (this lib uses its `form.impl`/`report.impl`), so no override is needed for it. The rule that still applies: exactly one fulcro-rad-statecharts (this repo's `src/main`) on the demo classpath (rp-14).
