# Project Tracker

<!-- Last updated: 2026-02-21 | Active: 0 | Blocked: 0 | Backlog: 12 | Done: 5 | Deferred: 3 -->

## Active

(none)

## Blocked

(none)

## Backlog

### Phase 1: Statechart Conversion

| Spec | Priority | Created | Depends-on | Summary |
|------|----------|---------|------------|---------|
| [project-setup](project-setup.md) | P1 | 2026-02-20 | cleanup-verification | Deps, artifact, configuration for new project |
| [session-id-convention](session-id-convention.md) | P0 | 2026-02-20 | project-setup | Cross-cutting session ID convention for all modules |
| [routing-conversion](routing-conversion.md) | P0 | 2026-02-20 | project-setup | Routing from DR to statecharts routing |
| [app-initialization](app-initialization.md) | P0 | 2026-02-20 | routing-conversion | Full bootstrap sequence replacing DR routing |
| [form-statechart](form-statechart.md) | P0 | 2026-02-20 | session-id, routing | Form UISM to statechart conversion |
| [report-statechart](report-statechart.md) | P0 | 2026-02-20 | session-id, routing | Report UISM to statechart conversion |
| [container-statechart](container-statechart.md) | P1 | 2026-02-20 | report-statechart | Container UISM to statechart conversion |
| [control-adaptation](control-adaptation.md) | P1 | 2026-02-20 | session-id, report | Control system adaptation |
| [macro-rewrites](macro-rewrites.md) | P0 | 2026-02-20 | form, report, container, routing | defsc-form/report/container macro changes |
| [headless-testing](headless-testing.md) | P1 | 2026-02-20 | form, report, routing | Headless testing strategy |
| [public-api-mapping](public-api-mapping.md) | P1 | 2026-02-20 | all above | Public API mapping old to new |

### Phase 2: Demo & Validation

| Spec | Priority | Created | Depends-on | Summary |
|------|----------|---------|------------|---------|
| [demo-port](demo-port.md) | P1 | 2026-02-20 | headless-plugin, macro-rewrites | Port Datomic demo to src/demo with all-CLJC headless support |

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

## Critique History

| Round | Date | Result | Link |
|-------|------|--------|------|
| 1 | 2026-02-20 | 7 critical, 7 important, 5 suggested | [critique-round-1](plans/critique-round-1.md) |
| 2 | 2026-02-20 | 0 critical, 2 important, 4 suggested -- READY | [critique-round-2](plans/critique-round-2.md) |

## Implementation Order (Recommended)

### Phase 0: Cleanup
1. cleanup-deletions (delete ~22 files)
2. dead-reference-cleanup (remove dead requires/code)
3. control-multimethod-conversion (map-based → multimethod)
4. headless-plugin (sparse rendering for testing)
5. cleanup-verification (compile, test, audit)

### Phase 1: Conversion
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

### Phase 2: Demo & Validation
17. demo-port (Datomic demo with headless testing)

## Open Questions for Human Review

All questions resolved by human review 2026-02-20. See [critique-round-2.md](plans/critique-round-2.md) Section "Consolidated Open Questions for Human Review" for the full list with DECIDED annotations. Only 2 minor items remain open (ident->session-id separator edge case, statecharts release version).

## Important Notes

- **fulcro-rad-datomic** is at `../fulcro-rad-datomic/` for reference — do NOT edit it, but maintain compatible names so it can reference this library
- All sibling projects referenced by specs live in the parent directory (`../`). See [WORKFLOW.md](WORKFLOW.md) for the full convention.
- **Classpath warning:** The demo deps.edn MUST use `:override-deps` to exclude old fulcro-rad from transitive dependencies. Old RAD on the classpath will cause conflicts.
