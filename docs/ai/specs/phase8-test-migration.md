# Spec: Test Migration

**Status**: backlog
**Priority**: P1
**Created**: 2026-02-22
**Owner**: AI
**Depends-on**: phase8-form-namespace-restructure, phase8-report-namespace-restructure, phase8-supporting-namespace-restructure, phase8-options-namespace-split
**Phase**: 8 — Library Restructuring

## Context

After namespace restructuring, all test files must be updated to use the new `com.fulcrologic.rad.statechart.*` namespaces. Some tests for shared code (attributes, ids, type-support) were deleted in the deps-and-identical-cleanup spec. The remaining tests are all statechart-specific.

## Requirements

1. Update all test file requires from old namespaces to new `statechart.*` namespaces
2. Move test files to match the new source namespace structure
3. Delete test files for code that now comes from the fulcro-rad dependency
4. All tests must pass after migration
5. Test namespaces should mirror source namespaces (e.g., `statechart.form` tested by `statechart.form-test`)

## Test File Moves

| Current Test File | New Test File | Tests For |
|-------------------|---------------|-----------|
| `form_statechart_spec.cljc` | `statechart/form_statechart_spec.cljc` | Form statechart chart behavior |
| `report_statechart_spec.cljc` | `statechart/report_statechart_spec.cljc` | Report statechart chart behavior |
| `container_statechart_spec.cljc` | `statechart/container_statechart_spec.cljc` | Container statechart chart behavior |
| `form_statechart_test.cljc` | `statechart/form_statechart_test.cljc` | Form E2E integration tests |
| `report_statechart_test.cljc` | `statechart/report_statechart_test.cljc` | Report E2E integration tests |
| `container_statechart_test.cljc` | `statechart/container_statechart_test.cljc` | Container E2E integration tests |
| `form_spec.cljc` | `statechart/form_spec.cljc` | Form public API |
| `report_test.cljc` | `statechart/report_test.cljc` | Report public API |
| `server_paginated_report_spec.cljc` | `statechart/server_paginated_report_spec.cljc` | Server-paginated variant |
| `incrementally_loaded_report_spec.cljc` | `statechart/incrementally_loaded_report_spec.cljc` | Incrementally-loaded variant |
| `sc/session_spec.cljc` | `statechart/session_spec.cljc` | Session ID convention |

### Test Files to Delete (shared code, now in fulcro-rad)

- `attributes_spec.cljc`
- `ids_spec.cljc`
- `type_support/date_time_spec.cljc`
- `type_support/decimal_spec.cljc`
- `type_support/js_date_formatter_spec.cljs`

### Test Files to Keep In Place (headless rendering — engine-agnostic)

- `rendering/headless_rendering_spec.cljc` — keep, update requires
- `rendering/headless_rendering_test.cljc` — keep, update requires

### Test Files to Keep In Place (shared utilities)

- `test_helpers.cljc` — keep at `src/test/com/fulcrologic/rad/test_helpers.cljc`, update its requires to use new `statechart.*` namespaces

## Require Updates

In every test file, update:

```clojure
;; OLD
(:require
  [com.fulcrologic.rad.form :as form]
  [com.fulcrologic.rad.form-options :as fo]
  [com.fulcrologic.rad.form-chart :as form-chart]
  [com.fulcrologic.rad.form-expressions :as fex]
  [com.fulcrologic.rad.report :as report]
  [com.fulcrologic.rad.report-options :as ro]
  [com.fulcrologic.rad.routing :as routing]
  [com.fulcrologic.rad.sc.session :as session]
  [com.fulcrologic.rad.control :as control])

;; NEW
(:require
  [com.fulcrologic.rad.statechart.form :as form]
  [com.fulcrologic.rad.form-options :as fo]                      ;; shared, from fulcro-rad
  [com.fulcrologic.rad.statechart.form-options :as sfo]          ;; engine-specific
  [com.fulcrologic.rad.statechart.form-chart :as form-chart]
  [com.fulcrologic.rad.statechart.form-expressions :as fex]
  [com.fulcrologic.rad.statechart.report :as report]
  [com.fulcrologic.rad.report-options :as ro]                    ;; shared, from fulcro-rad
  [com.fulcrologic.rad.statechart.report-options :as sro]        ;; engine-specific
  [com.fulcrologic.rad.statechart.routing :as routing]
  [com.fulcrologic.rad.statechart.session :as session]
  [com.fulcrologic.rad.statechart.control :as control])
```

Also update option key references in test assertions:
- `fo/triggers` → `sfo/triggers` (in statechart-specific test options)
- `fo/machine` → `sfo/statechart`
- `ro/machine` → `sro/statechart`

## Approach

1. Create test directory `src/test/com/fulcrologic/rad/statechart/`
2. Move each test file to new location with updated namespace
3. Update all requires in each test file
4. Update option key references
5. Delete test files for shared code
6. Delete old `src/test/com/fulcrologic/rad/sc/` directory
7. Update test runner configuration if it specifies test paths/patterns
8. Run all tests and fix any failures

## Affected Modules

- All files under `src/test/com/fulcrologic/rad/` — move or delete
- `deps.edn` — update test paths if needed
- Any test runner configuration (kaocha config, etc.)

## Verification

1. [ ] All test files moved to `statechart/` subdirectory
2. [ ] All requires updated to `statechart.*` namespaces
3. [ ] All option key references updated (`fo/triggers` → `sfo/triggers` etc.)
4. [ ] Shared code test files deleted
5. [ ] Test runner finds and runs all tests at new locations
6. [ ] All tests pass (same count as before migration: 83 tests, 473 assertions)
7. [ ] No references to old namespace paths remain in test files
