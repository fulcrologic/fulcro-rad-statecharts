# Project Tracker

<!-- Last updated: 2026-02-21 | Active: 0 | Blocked: 0 | Backlog: 3 | Done: 17 | Deferred: 3 -->

## Active

(none)

## Blocked

(none)

## Backlog

### Phase 3: Production Readiness (from critique team audit)

| Spec | Priority | Created | Depends-on | Summary |
|------|----------|---------|------------|---------|
| routing-form-integration | P0 | 2026-02-21 | — | Wire routing statechart to use RAD form statechart instead of deprecated `sfr/start-form!` (UISM). The `sfr/` namespace in `../statecharts/` uses `uism/begin!`; routing should call `form/start-form!` (which uses `scf/start!`) instead. This is the #1 gap — forms work through direct API but NOT through routing path. Causes 22/34 form E2E test failures. |
| fix-e2e-test-failures | P1 | 2026-02-21 | routing-form-integration | Fix remaining E2E test failures: (1) form tests — 22/34 fail, likely fixed by routing-form-integration; (2) routing tests — 8/32 fail on route-guard/dirty-form (HTTP 500 / transit parse errors); (3) remove `(when ...)` guards that skip assertions silently; (4) fix invoice form render exception root cause |
| headless-load-callback | P1 | 2026-02-21 | — | Fix `fops/load` ok-action not firing in headless mode. Report E2E tests manually send `:event/loaded` as workaround. Likely an event-loop/callback issue in `:immediate` mode. Library-level fix needed in `../statecharts/` or Fulcro headless. |

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

## Critique History

| Round | Date | Result | Link |
|-------|------|--------|------|
| 1 | 2026-02-20 | 7 critical, 7 important, 5 suggested | [critique-round-1](plans/critique-round-1.md) |
| 2 | 2026-02-20 | 0 critical, 2 important, 4 suggested -- READY | [critique-round-2](plans/critique-round-2.md) |
| 3 | 2026-02-21 | 0 critical (after fix), 5 important, 4 suggested | Phase 2 demo-port critique |
| 4 | 2026-02-21 | 1 critical, 2 important | Full quality audit (5-agent team): compile/test, API, architecture, test validity, ecosystem |

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

## Open Questions for Human Review

All questions resolved by human review 2026-02-20. See [critique-round-2.md](plans/critique-round-2.md) Section "Consolidated Open Questions for Human Review" for the full list with DECIDED annotations. Only 2 minor items remain open (ident->session-id separator edge case, statecharts release version).

## Known Issues (from Critique Rounds 3+4)

### Critical
- **Routing→form integration still uses UISM** (P0 backlog): `sfr/start-form!` in `../statecharts/` deprecated `rad-integration` ns calls `uism/begin!`. The new form statechart (`form_chart.cljc`) works via direct `form/start-form!` → `scf/start!`, but the routing path bypasses it. Causes 22/34 form E2E test failures.

### Important
- **`sfr/edit!` sends to wrong session**: Sends to the form's own session instead of the routing session. Workaround: use `scr/route-to!` directly.
- **`fops/load` ok-action doesn't fire in headless mode** (P1 backlog): Reports require manual `:event/loaded` send. Library-level fix needed.
- **8 routing E2E test failures**: Route-guard/dirty-form tests fail (HTTP 500 / transit parse errors from server returning HTML).
- **Form E2E tests have silent `(when ...)` guards**: 2 tests skip assertions if data is nil, masking failures.
- **`scf/current-configuration` returns nil in headless Root render**: Route-denied modal can't be tested via hiccup.

### Verified Working
- Library: 62 tests, 397 assertions, 0 failures
- Report E2E: 3/3 tests, 21/21 assertions pass
- Demo startup: compiles, seeds Datomic, serves on port 3000
- Statechart conversion: confirmed real by architecture review (all `scf/send!`, no active UISM paths)
- API: coherent and complete for RAD apps; fulcro-rad-datomic fully compatible
- Ecosystem: all `scf/`/`scr/`/`sfr/`/`sfro/` calls verified against `../statecharts/`

## Important Notes

- **fulcro-rad-datomic** is at `../fulcro-rad-datomic/` for reference — do NOT edit it, but maintain compatible names so it can reference this library
- All sibling projects referenced by specs live in the parent directory (`../`). See [WORKFLOW.md](WORKFLOW.md) for the full convention.
- **Classpath warning:** The demo deps.edn MUST use `:override-deps` to exclude old fulcro-rad from transitive dependencies. Old RAD on the classpath will cause conflicts.
