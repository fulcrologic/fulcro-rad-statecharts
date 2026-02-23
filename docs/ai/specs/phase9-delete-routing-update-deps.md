# Spec: Delete Routing Namespace and Update Dependents

**Status**: backlog
**Priority**: P0
**Created**: 2026-02-22
**Owner**: AI
**Depends-on**: phase9-form-consolidation, phase9-report-consolidation
**Phase**: 9 — Namespace Consolidation

## Context

`routing.cljc` is a thin wrapper around `com.fulcrologic.statecharts.integration.fulcro.routing` (scr). After form and report consolidation absorb `form-route-state`/`edit!`/`create!` and `report-route-state`, the remaining functions (`route-to!`, `back!`, `route-forward!`, `force-continue-routing!`, `abandon-route-change!`, `route-denied?`, `active-leaf-routes`) are pure one-liner delegations. Users should use `scr/` directly instead.

This spec also updates all files that depend on the deleted satellite namespaces.

## Requirements

1. Delete `routing.cljc`
2. Update `server_paginated_report.cljc`: remove `report-expressions` require, change `rexpr/` → `report/`
3. Update `incrementally_loaded_report.cljc`: remove `report-expressions` require, change `rexpr/` → `report/`
4. Update `rendering/headless/report.cljc`: remove `routing` require, change `routing/edit!` → `form/edit!`
5. Update all test files that reference deleted namespaces:
   - `form_statechart_spec.cljc`: `fc/` → `form/`, `fex/` → `form/`
   - `report_statechart_spec.cljc`: `rc/` → `report/`, `rexpr/` → `report/`
   - `container_statechart_spec.cljc`: `cc/` → `container/`, `cexpr/` → `container/`
   - `container_statechart_test.cljc`: `cexpr/` → `container/`
   - `server_paginated_report_spec.cljc`: `rexpr/` → `report/`
   - `incrementally_loaded_report_spec.cljc`: `rexpr/` → `report/`
6. Update demo project (`src/demo/com/example/ui/ui.cljc`):
   - Remove `routing` require
   - Use `form/form-route-state`, `report/report-route-state`, `form/create!`, `form/edit!`
   - Use `scr/route-to!` directly where `rroute/route-to!` was used

## Affected Files

- `src/main/com/fulcrologic/rad/statechart/routing.cljc` — deleted
- `src/main/com/fulcrologic/rad/statechart/server_paginated_report.cljc` — require update
- `src/main/com/fulcrologic/rad/statechart/incrementally_loaded_report.cljc` — require update
- `src/main/com/fulcrologic/rad/rendering/headless/report.cljc` — require update
- `src/test/com/fulcrologic/rad/statechart/form_statechart_spec.cljc` — require update
- `src/test/com/fulcrologic/rad/statechart/report_statechart_spec.cljc` — require update
- `src/test/com/fulcrologic/rad/statechart/container_statechart_spec.cljc` — require update
- `src/test/com/fulcrologic/rad/statechart/container_statechart_test.cljc` — require update
- `src/test/com/fulcrologic/rad/statechart/server_paginated_report_spec.cljc` — require update
- `src/test/com/fulcrologic/rad/statechart/incrementally_loaded_report_spec.cljc` — require update
- `src/demo/com/example/ui/ui.cljc` — require update

## Verification

1. [ ] `routing.cljc` is deleted
2. [ ] All 8 deleted satellite files are gone (form_chart, form_expressions, form_machines, report_chart, report_expressions, container_chart, container_expressions, routing)
3. [ ] No remaining requires reference deleted namespaces (grep for `form-chart`, `form-expressions`, `form-machines`, `report-chart`, `report-expressions`, `container-chart`, `container-expressions`, `statechart.routing`)
4. [ ] Full test suite passes (77+ tests)
5. [ ] CLJS compiles with 0 warnings
6. [ ] Demo project compiles
