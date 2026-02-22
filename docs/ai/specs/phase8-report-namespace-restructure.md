# Spec: Report Namespace Restructure

**Status**: backlog
**Priority**: P1
**Created**: 2026-02-22
**Owner**: AI
**Depends-on**: phase8-upstream-impl-extraction, phase8-deps-and-identical-cleanup
**Phase**: 8 — Library Restructuring

## Context

Same pattern as the form namespace restructure. Move all statechart-specific report code from `com.fulcrologic.rad.report` to `com.fulcrologic.rad.statechart.report`. This spec follows the identical approach documented in `phase8-form-namespace-restructure.md` — refer to that spec for the detailed re-export pattern and approach.

## Requirements

1. Create `com.fulcrologic.rad.statechart.report` namespace that:
   - Re-exports ~25 shared functions from `com.fulcrologic.rad.report.impl` via `def` aliases
   - Contains all statechart-specific engine functions: `run-report!`, `start-report!`, `set-parameter!`, `select-row!`, `filter-rows`, `sort-rows!`, `next-page!`, `prior-page!`, `goto-page!`, `report-session-id`
   - Contains the `defsc-report` macro (delegates to `impl/defsc-report*` with statechart-specific `convert-options`)

2. Move `com.fulcrologic.rad.report-chart` → `com.fulcrologic.rad.statechart.report-chart`
3. Move `com.fulcrologic.rad.report-expressions` → `com.fulcrologic.rad.statechart.report-expressions`
4. Move `com.fulcrologic.rad.server-paginated-report` → `com.fulcrologic.rad.statechart.server-paginated-report`
5. Move `com.fulcrologic.rad.incrementally-loaded-report` → `com.fulcrologic.rad.statechart.incrementally-loaded-report`
6. Delete old report files from this project (they come from fulcro-rad dependency)

## File Moves

| Current Path | New Path |
|-------------|----------|
| `src/main/.../rad/report.cljc` | `src/main/.../rad/statechart/report.cljc` |
| `src/main/.../rad/report_chart.cljc` | `src/main/.../rad/statechart/report_chart.cljc` |
| `src/main/.../rad/report_expressions.cljc` | `src/main/.../rad/statechart/report_expressions.cljc` |
| `src/main/.../rad/server_paginated_report.cljc` | `src/main/.../rad/statechart/server_paginated_report.cljc` |
| `src/main/.../rad/incrementally_loaded_report.cljc` | `src/main/.../rad/statechart/incrementally_loaded_report.cljc` |

## Affected Modules

- `src/main/com/fulcrologic/rad/statechart/report.cljc` — NEW (moved + restructured)
- `src/main/com/fulcrologic/rad/statechart/report_chart.cljc` — NEW (moved)
- `src/main/com/fulcrologic/rad/statechart/report_expressions.cljc` — NEW (moved)
- `src/main/com/fulcrologic/rad/statechart/server_paginated_report.cljc` — NEW (moved)
- `src/main/com/fulcrologic/rad/statechart/incrementally_loaded_report.cljc` — NEW (moved)
- Old files at `rad/report.cljc`, `rad/report_chart.cljc`, `rad/report_expressions.cljc` — DELETE
- Old files at `rad/server_paginated_report.cljc`, `rad/incrementally_loaded_report.cljc` — DELETE
- All files requiring the old report namespaces (updated to `rad.statechart.report`)

**Note**: Verification covers source compilation only (`clj -e "(require ...)"` for source namespaces). Test compilation will be broken until the test-migration spec is complete.

## Verification

1. [ ] `com.fulcrologic.rad.statechart.report` namespace compiles
2. [ ] All re-exported functions accessible via `(require '[com.fulcrologic.rad.statechart.report :as report])`
3. [ ] `defsc-report` macro works and produces correct component
4. [ ] Server-paginated and incrementally-loaded variants work at new namespaces
5. [ ] No circular dependencies
6. [ ] Old report namespaces removed from this project's source tree
