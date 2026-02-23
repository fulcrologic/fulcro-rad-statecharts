# Spec: Report Namespace Consolidation

**Status**: backlog
**Priority**: P0
**Created**: 2026-02-22
**Owner**: AI
**Depends-on**: (none)
**Phase**: 9 — Namespace Consolidation

## Context

`report.cljc` has satellite files (`report_chart.cljc`, `report_expressions.cljc`) that don't need to be separate. Additionally, `routing.cljc` contains `report-route-state` which belongs in the report namespace.

No circular dependency issue exists — `report_expressions.cljc` does NOT call back into `report.cljc`.

## Requirements

1. Merge `report_chart.cljc` content into `report.cljc`
2. Merge `report_expressions.cljc` content into `report.cljc`
3. Move `report-route-state` from `routing.cljc` into `report.cljc`
4. All expression functions must remain public (they're used by `server_paginated_report.cljc` and `incrementally_loaded_report.cljc`)

## Section Order in Merged File

1. ns declaration (merged requires)
2. Existing report utility/rendering functions
3. Expression helpers and expression functions (from `report_expressions.cljc`)
4. Chart definition `report-statechart` (from `report_chart.cljc`)
5. Public API (start-report!, run-report!, etc.)
6. `report-route-state` (from `routing.cljc`)
7. Macros (defsc-report, etc.)

## Requires Changes

**Add** (from satellite files):
- `[com.fulcrologic.fulcro.algorithms.normalized-state :as fstate]`
- `[com.fulcrologic.statecharts.data-model.operations :as ops]`
- `[com.fulcrologic.statecharts.integration.fulcro.operations :as fops]`
- `[com.fulcrologic.statecharts.chart :refer [statechart]]`
- `[com.fulcrologic.statecharts.convenience :refer [handle on]]`
- `[com.fulcrologic.statecharts.elements :refer [data-model on-entry script state transition entry-fn]]`

**Remove**:
- `[com.fulcrologic.rad.statechart.report-chart :as report-chart]`

## Symbol Reference Updates

- `report-chart/report-statechart` → `report-statechart` (local def)

## Affected Files

- `src/main/com/fulcrologic/rad/statechart/report.cljc` — primary target
- `src/main/com/fulcrologic/rad/statechart/report_chart.cljc` — absorbed, then deleted
- `src/main/com/fulcrologic/rad/statechart/report_expressions.cljc` — absorbed, then deleted

## Verification

1. [ ] All `report-chart` and `report-expressions` content is in `report.cljc`
2. [ ] `report-route-state` is in `report.cljc`
3. [ ] `report_statechart_spec.cljc` passes after updating requires (`rc/` → `report/`, `rexpr/` → `report/`)
4. [ ] `report_test.cljc` still passes
5. [ ] CLJS compiles with 0 warnings
6. [ ] Satellite files deleted
