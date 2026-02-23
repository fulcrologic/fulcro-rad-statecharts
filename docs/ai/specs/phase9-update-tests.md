# Spec: Update Test Files for Consolidated Namespaces

**Status**: backlog
**Priority**: P0
**Created**: 2026-02-22
**Owner**: AI
**Depends-on**: phase9-form-consolidation, phase9-report-consolidation, phase9-container-consolidation
**Phase**: 9 — Namespace Consolidation

## Context

After merging satellite files into their parent namespaces, test files may still reference deleted namespace aliases or use stale patterns. All test requires and assertions must be verified against the consolidated namespace structure.

## Requirements

1. Verify all test files compile and run:
   - `form_statechart_spec.cljc` — uses `form/` prefix for chart/expression functions
   - `form_spec.cljc` — uses `form/` prefix
   - `report_statechart_spec.cljc` — uses `report/` prefix for chart/expression functions
   - `report_test.cljc` — uses `report/` prefix
   - `container_statechart_spec.cljc` — uses `container/` prefix for chart/expression functions
   - `container_statechart_test.cljc` — uses `container/` prefix
   - `server_paginated_report_spec.cljc` — uses `report/` for expression functions
   - `incrementally_loaded_report_spec.cljc` — uses `report/` for expression functions
   - `options_validation_spec.cljc`
   - `session_spec.cljc`
   - `headless_rendering_spec.cljc`
   - `headless_rendering_test.cljc`
   - `headless_routing_tests.clj`
2. No test file imports deleted satellite namespaces
3. Run full test suite — all 77+ tests must pass with 0 failures
4. If any test references `routing/form-route-state`, `routing/edit!`, `routing/create!`, or `routing/report-route-state`, update to use `form/` or `report/` prefix

## Verification

1. [ ] `grep -r 'form-chart\|form-expressions\|form-machines\|report-chart\|report-expressions\|container-chart\|container-expressions' src/test/` finds 0 namespace references
2. [ ] Full test suite: 77+ tests, 0 failures
3. [ ] No test uses deprecated routing wrappers
