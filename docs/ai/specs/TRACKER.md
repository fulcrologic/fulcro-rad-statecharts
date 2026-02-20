# Project Tracker

<!-- Last updated: 2026-02-20 | Active: 0 | Blocked: 0 | Backlog: 13 | Done: 0 | Deferred: 1 -->

## Active

(none)

## Blocked

(none)

## Backlog

| Spec | Priority | Created | Depends-on | Summary |
|------|----------|---------|------------|---------|
| [project-setup](project-setup.md) | P1 | 2026-02-20 | none | Deps, artifact, configuration for new project |
| [session-id-convention](session-id-convention.md) | P0 | 2026-02-20 | project-setup | Cross-cutting session ID convention for all modules |
| [routing-conversion](routing-conversion.md) | P0 | 2026-02-20 | project-setup | Routing from DR to statecharts routing |
| [app-initialization](app-initialization.md) | P0 | 2026-02-20 | routing-conversion | Full bootstrap sequence replacing DR routing |
| [form-statechart](form-statechart.md) | P0 | 2026-02-20 | session-id, routing | Form UISM to statechart conversion |
| [report-statechart](report-statechart.md) | P0 | 2026-02-20 | session-id, routing | Report UISM to statechart conversion |
| [container-statechart](container-statechart.md) | P1 | 2026-02-20 | report-statechart | Container UISM to statechart conversion |
| [auth-statechart](auth-statechart.md) | P1 | 2026-02-20 | project-setup | Auth UISM to statechart conversion |
| [control-adaptation](control-adaptation.md) | P1 | 2026-02-20 | session-id, report | Control system adaptation |
| [macro-rewrites](macro-rewrites.md) | P0 | 2026-02-20 | form, report, container, routing | defsc-form/report/container macro changes |
| [rad-hooks-conversion](rad-hooks-conversion.md) | P1 | 2026-02-20 | form, report | RAD hooks (use-form, use-report) conversion |
| [headless-testing](headless-testing.md) | P1 | 2026-02-20 | form, report, routing | Headless testing strategy |
| [public-api-mapping](public-api-mapping.md) | P1 | 2026-02-20 | all above | Public API mapping old to new |

## Deferred

| Spec | Priority | Reason | Summary |
|------|----------|--------|---------|
| [blob-statechart](blob-statechart.md) | P3 | Zero UISM dependency; deferred to v2 | Blob/file-upload statechart conversion |

## Done

(none -- specs complete, ready for implementation after human review)

## Critique History

| Round | Date | Result | Link |
|-------|------|--------|------|
| 1 | 2026-02-20 | 7 critical, 7 important, 5 suggested | [critique-round-1](plans/critique-round-1.md) |
| 2 | 2026-02-20 | 0 critical, 2 important, 4 suggested -- READY | [critique-round-2](plans/critique-round-2.md) |

## Implementation Order (Recommended)

1. project-setup
2. session-id-convention (cross-cutting decision)
3. routing-conversion (infrastructure)
4. app-initialization
5. form-statechart (most complex, foundational)
6. report-statechart (similar pattern to form)
7. container-statechart (depends on report)
8. auth-statechart (independent, can parallel with 5-7)
9. control-adaptation
10. macro-rewrites (depends on form, report, container, routing)
11. rad-hooks-conversion (depends on form, report)
12. headless-testing (depends on all above)
13. public-api-mapping (update after all conversions)

## Open Questions for Human Review

All questions resolved by human review 2026-02-20. See [critique-round-2.md](plans/critique-round-2.md) Section "Consolidated Open Questions for Human Review" for the full list with DECIDED annotations. Only 2 minor items remain open (ident->session-id separator edge case, statecharts release version).
