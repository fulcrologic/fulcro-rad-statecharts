# Spec: fix-e2e-test-failures

**Status**: backlog
**Priority**: P1
**Created**: 2026-02-21
**Owner**: AI
**Depends-on**: routing-form-integration

## Context

After routing-form-integration is complete, there will likely be remaining E2E test failures that need individual attention. This spec covers the cleanup pass.

## Requirements

1. Fix remaining form E2E test failures not resolved by routing-form-integration
2. Fix 8 routing E2E test failures on route-guard/dirty-form (HTTP 500 / transit parse errors)
3. Remove silent `(when ...)` guards in form tests that skip assertions — replace with proper assertions that fail visibly
4. Fix invoice form render exception root cause (missing CLJ stub for JS-only rendering dependency)
5. All E2E tests should either pass or have a documented reason for deferral

## Affected Modules

- `src/demo/com/example/headless_form_tests.clj` — Fix silent guards, fix invoice render
- `src/demo/com/example/headless_routing_tests.clj` — Fix route-guard tests
- Possibly `src/main/` files if bugs are found in the statechart logic

## Known Issues

### Form Tests (headless_form_tests.clj)
- Lines 195-219: `(when addr-data ...)` silently skips assertions if address data is nil
- Lines 286-320: `(when inv-id ...)` silently skips assertions if invoice ID is nil
- Lines 300-309: Invoice form render throws on complex subforms (line items + customer picker) — likely missing CLJ stub

### Routing Tests (headless_routing_tests.clj)
- Lines 263, 306, 350: Route-guard/dirty-form tests return HTTP 500 with HTML instead of transit
- The server may be returning error pages when the routing statechart sends unexpected requests

## Verification

1. [ ] No `(when ...)` guards silently skipping assertions in form tests
2. [ ] Invoice form render exception resolved or documented
3. [ ] Route-guard routing tests pass or have documented deferral reason
4. [ ] All passing tests continue to pass
5. [ ] Total E2E test pass rate documented
