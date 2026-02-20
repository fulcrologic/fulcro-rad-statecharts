# dead-reference-cleanup

**Status:** Backlog | **Priority:** P0 | **Created:** 2026-02-20 | **Depends-on:** cleanup-deletions

## Summary

After deleting modules, remove all requires/references to deleted namespaces from remaining files.

## Critical Files to Refactor

- `form.cljc` — Remove auth references, DR routing calls, blob field handling
- `report.cljc` — Remove DR routing calls, auth references
- `container.cljc` — Remove DR routing references
- `control.cljc` — Remove `rad-routing/update-route-params!` from `set-parameter`, remove `uism/trigger!` from `run!`
- `attributes.cljc` — Remove co-located resolver generation from `defattr`
- `application.cljc`, `common.cljc`, `picker_options.cljc` — Check and clean

## Approach

1. Grep for all deleted namespace references across remaining source files
2. Remove requires, refers, and code paths that depend on deleted modules
3. For `control.cljc`: stub `run!` (will be reconnected during statechart conversion)
4. For `attributes.cljc`: remove resolver co-location support from `defattr` macro

## Acceptance Criteria

- Zero references to deleted namespaces in remaining source
- All remaining files parse without errors
- Functions that depend on deleted code are either removed or stubbed with TODOs
