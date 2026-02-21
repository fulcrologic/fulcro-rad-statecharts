# Project Tracker

<!-- Last updated: 2026-02-21 | Active: 0 | Blocked: 0 | Backlog: 1 (demo-port, 11 tasks) | Done: 16 | Deferred: 3 -->

## Active

(none)

## Blocked

(none)

## Backlog

### Phase 2: Demo & Validation

| Spec | Priority | Created | Depends-on | Summary |
|------|----------|---------|------------|---------|
| [demo-port](demo-port.md) | P1 | 2026-02-20 | all Phase 0+1 | Port Datomic demo to src/demo with all-CLJC headless support (11 sub-tasks) |

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

## Critique History

| Round | Date | Result | Link |
|-------|------|--------|------|
| 1 | 2026-02-20 | 7 critical, 7 important, 5 suggested | [critique-round-1](plans/critique-round-1.md) |
| 2 | 2026-02-20 | 0 critical, 2 important, 4 suggested -- READY | [critique-round-2](plans/critique-round-2.md) |

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

### Phase 2: Demo & Validation — IN PROGRESS
17. demo-port (Datomic demo with headless testing) — split into 11 sub-tasks

## Open Questions for Human Review

All questions resolved by human review 2026-02-20. See [critique-round-2.md](plans/critique-round-2.md) Section "Consolidated Open Questions for Human Review" for the full list with DECIDED annotations. Only 2 minor items remain open (ident->session-id separator edge case, statecharts release version).

## Important Notes

- **fulcro-rad-datomic** is at `../fulcro-rad-datomic/` for reference — do NOT edit it, but maintain compatible names so it can reference this library
- All sibling projects referenced by specs live in the parent directory (`../`). See [WORKFLOW.md](WORKFLOW.md) for the full convention.
- **Classpath warning:** The demo deps.edn MUST use `:override-deps` to exclude old fulcro-rad from transitive dependencies. Old RAD on the classpath will cause conflicts.
