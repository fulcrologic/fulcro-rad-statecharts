# Spec: routing-form-integration

**Status**: backlog
**Priority**: P0
**Created**: 2026-02-21
**Owner**: AI
**Depends-on**: none

## Context

The routing statechart currently uses `sfr/form-state` (from `../statecharts/` `rad_integration.cljc`) to launch forms when routing to them. That function calls `sfr/start-form!` which uses the deprecated `uism/begin!`. The new RAD form statechart (`form_chart.cljc`) works via `form/start-form!` → `scf/start!`, but the routing path bypasses it entirely. This causes 22/34 form E2E test failures — forms work via direct API but NOT through routing.

## Requirements

1. Create a `form-route-state` function in `com.fulcrologic.rad.routing` (fulcro-rad-statecharts) that returns a routing state (using `scr/rstate`) with:
   - On-entry: calls `form/start-form!` (from `com.fulcrologic.rad.form`) to start the form's statechart
   - On-exit: calls `form/abandon-form!` to clean up
   - Accepts `:route/target` (form class) and `:route/params` (set of param keywords)
2. The entry function must extract the form ID and params from the routing event data (same shape as `sfr/form-state` currently expects)
3. The demo routing chart (`src/demo/com/example/ui/ui.cljc`) must be updated to use the new `form-route-state` instead of `sfr/form-state`
4. Remove the `sfr` require from the demo (no more `rad_integration` dependency)
5. Must work with `:event-loop? :immediate` (headless/CLJ testing)
6. The form's statechart session-id must be derivable from the form ident (using `sc.session/ident->session-id`)

## Affected Modules

- `src/main/com/fulcrologic/rad/routing.cljc` — Add `form-route-state` function
- `src/demo/com/example/ui/ui.cljc` — Replace `sfr/form-state` with new helper
- `../statecharts/src/main/com/fulcrologic/statecharts/integration/fulcro/rad_integration.cljc` — Reference only (do NOT edit; we're replacing its usage, not modifying it)

## Approach

1. Study `sfr/form-state` in `../statecharts/rad_integration.cljc` (lines 301-320) to understand the on-entry/on-exit shape
2. Study `scr/rstate` in `../statecharts/routing.cljc` to understand how to create routing states with entry/exit hooks
3. Implement `form-route-state` in `com.fulcrologic.rad.routing` that:
   - Uses `scr/rstate` to create the routing state
   - Attaches an entry action that calls `form/start-form!`
   - Attaches an exit action that calls `form/abandon-form!`
4. Update demo `routing-chart` to use the new function
5. Test via existing E2E tests — form tests that previously failed should now pass

## Key Code References

- `sfr/form-state`: `../statecharts/rad_integration.cljc:301-320` — current (deprecated) implementation
- `sfr/start-form!`: `../statecharts/rad_integration.cljc:285-299` — uses `uism/begin!`
- `form/start-form!`: `src/main/com/fulcrologic/rad/form.cljc:365-403` — new implementation using `scf/start!`
- `scr/rstate`: `../statecharts/routing.cljc:337-371` — routing state builder
- `scr/routing-regions`: `../statecharts/routing.cljc` — routing region builder
- Demo routing chart: `src/demo/com/example/ui/ui.cljc:51-73`

## Open Questions

- Should `form-route-state` also handle the report case (a `report-route-state`)? Reports currently use `scr/rstate` directly and work, so probably not needed now.

## Verification

1. [ ] `form-route-state` exists in `com.fulcrologic.rad.routing`
2. [ ] Demo uses `form-route-state` instead of `sfr/form-state`
3. [ ] No `sfr/` or `rad_integration` requires in the demo
4. [ ] Library compiles (CLJ)
5. [ ] Existing unit tests pass (62 tests, 397 assertions)
6. [ ] Form E2E tests pass (majority of the 34 assertions)
7. [ ] Routing E2E tests still pass (non-guard tests)
8. [ ] Report E2E tests still pass (3/3)
