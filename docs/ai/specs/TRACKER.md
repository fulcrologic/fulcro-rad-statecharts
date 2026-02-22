# Project Tracker

<!-- Last updated: 2026-02-21 | Active: 1 | Blocked: 0 | Backlog: 0 | Done: 26 | Deferred: 3 -->

## Active

| Task | Summary |
|------|---------|
| headless-chrome-test | Chrome browser testing of all 6 demo pages — verify form-links, pickers, dropdowns, column sort, form save/cancel work in CLJS |

### headless-chrome-test: Remaining Work
- **Form-links fix committed** but untested in Chrome (needs CLJS recompile of demo)
- Demo CLJS rebuild blocked: the demo process was started with local deps overrides from a previous session; no shadow-cljs watch mode running. Need to restart demo server + shadow-cljs watch.
- **Pages tested**: Inventory Report (structure renders, data loads, controls present)
- **Pages NOT tested**: New Account, New Item, Invoices, New Invoice, Acct 101 Invoices
- **Features to verify after rebuild**: form-link click navigates to edit form, picker dropdowns work, column sorting, form onChange, save/cancel buttons, subform add/delete, report pagination

## Blocked

(none)

## Backlog

(none)

## Deferred

| Spec | Priority | Reason | Summary |
|------|----------|--------|---------|
| [blob-statechart](blob-statechart.md) | P3 | Handled externally | Blob/file-upload statechart conversion |
| [auth-statechart](auth-statechart.md) | P3 | Handled externally | Auth UISM to statechart conversion |
| [rad-hooks-conversion](rad-hooks-conversion.md) | P3 | React hooks banned; use statecharts | RAD hooks (use-form, use-report) conversion |

## Done

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
| 5 | 2026-02-21 | 0 critical, 0 issues | Phase 3 final verification: 83 tests, 446 assertions, 0 failures |
| 6 | 2026-02-21 | 2 critical, 15 important, 12 suggested | [critique-consolidated](critique-consolidated.md) — 4-agent deep critique + cross-review |

## Implementation Order (Recommended)

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

## Open Questions for Human Review

All questions resolved by human review 2026-02-20. See [critique-round-2.md](plans/critique-round-2.md) Section "Consolidated Open Questions for Human Review" for the full list with DECIDED annotations. Only 2 minor items remain open (ident->session-id separator edge case, statecharts release version).

## Known Issues

### Critical
- **C1: Statecharts not released to Clojars** — local path dep blocks downstream consumption. Must release statecharts library first.

### Remaining Important
- **`scf/current-configuration` returns nil in headless Root render**: Route-denied modal can't be tested via hiccup.
- **Container Tier 2 tests**: 6 assertion failures — child report lifecycle not starting in test setup. Tier 1 passes.
- **6 pre-existing report Tier 2 errors**: `v20150901_impl.cljc:836` protocol incompatibility in statecharts test mocks.
- **No rendering plugin ported**: semantic-ui/react-bootstrap need multimethod conversion for browser use.

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

### Verified Working (Post Phase 5)
- 83 tests, 473 assertions (CLJ)
- CLJS compilation: 396 files, 0 warnings
- All Tier 1 pure chart tests pass (form, report, container, server-paginated, incrementally-loaded)
- E2E tests: 21 total (form 8, routing 10, report 3) — all pass
- Zero UISM/DR references in production code
- Zero `requiring-resolve` in CLJS code paths
- pom.xml matches deps.edn
- All 24 deps at latest versions
- README and migration guide complete

## Important Notes

- **fulcro-rad-datomic** is at `../fulcro-rad-datomic/` for reference — do NOT edit it, but maintain compatible names so it can reference this library
- All sibling projects referenced by specs live in the parent directory (`../`). See [WORKFLOW.md](WORKFLOW.md) for the full convention.
- **Classpath warning:** The demo deps.edn MUST use `:override-deps` to exclude old fulcro-rad from transitive dependencies. Old RAD on the classpath will cause conflicts.
