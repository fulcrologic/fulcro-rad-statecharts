# Spec: Update Demo App for Consolidated Namespaces

**Status**: backlog
**Priority**: P1
**Created**: 2026-02-22
**Owner**: AI
**Depends-on**: phase9-delete-routing-update-deps
**Phase**: 9 — Namespace Consolidation

## Context

The demo app in `src/demo/` uses the library's public API. After namespace consolidation and routing.cljc deletion, demo imports must be updated. The demo should also serve as a working example that validates the entire consolidated API surface.

## Requirements

1. Update `src/demo/com/example/ui/ui.cljc`:
   - Remove any `routing` require (use `form/form-route-state`, `report/report-route-state`, `form/edit!`, `form/create!` directly)
   - Use `scr/route-to!` directly where `rroute/route-to!` was used
2. Update any other demo files that reference deleted namespaces
3. Verify demo compiles (CLJ and CLJS)
4. If possible, start the demo server and confirm basic navigation works

## Verification

1. [ ] No demo file imports deleted satellite namespaces or `statechart.routing`
2. [ ] Demo CLJ compilation succeeds
3. [ ] Demo CLJS compilation succeeds (0 warnings)
4. [ ] Demo server starts and serves pages (manual check)
