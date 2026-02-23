# Spec: Container Namespace Consolidation

**Status**: backlog
**Priority**: P1
**Created**: 2026-02-22
**Owner**: AI
**Depends-on**: (none)
**Phase**: 9 — Namespace Consolidation

## Context

`container.cljc` has satellite files (`container_chart.cljc`, `container_expressions.cljc`) that are small enough to merge in. The chart is ~30 lines and the expressions ~100 lines.

## Requirements

1. Merge `container_chart.cljc` content into `container.cljc`
2. Merge `container_expressions.cljc` content into `container.cljc`
3. Resolve name collision: `broadcast-to-children!` exists in both the public API and expressions. The expression version is an internal statechart helper; rename it or inline it.

## Requires Changes

**Add** (from satellite files):
- `[com.fulcrologic.statecharts.chart :refer [statechart]]`
- `[com.fulcrologic.statecharts.convenience :refer [handle]]`
- `[com.fulcrologic.statecharts.elements :refer [on-entry on-exit script state transition]]`

**Remove**:
- `[com.fulcrologic.rad.statechart.container-chart :as container-chart]`
- `[com.fulcrologic.rad.statechart.container-expressions :as cexpr]`

## Symbol Reference Updates

- `container-chart/container-statechart` → `container-statechart` (local def)
- `cexpr/*` → direct calls (same file)

## Affected Files

- `src/main/com/fulcrologic/rad/statechart/container.cljc` — primary target
- `src/main/com/fulcrologic/rad/statechart/container_chart.cljc` — absorbed, then deleted
- `src/main/com/fulcrologic/rad/statechart/container_expressions.cljc` — absorbed, then deleted

## Verification

1. [ ] All `container-chart` and `container-expressions` content is in `container.cljc`
2. [ ] `container_statechart_spec.cljc` passes after updating requires (`cc/` → `container/`, `cexpr/` → `container/`)
3. [ ] `container_statechart_test.cljc` passes
4. [ ] CLJS compiles with 0 warnings
5. [ ] Satellite files deleted
