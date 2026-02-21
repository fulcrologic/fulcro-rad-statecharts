# Spec: headless-load-callback

**Status**: done
**Priority**: P1
**Created**: 2026-02-21
**Owner**: AI
**Depends-on**: none

## Context

`fops/load` ok-action callback doesn't fire in headless mode. Report E2E tests manually send `:event/loaded` as a workaround. The data arrives in Fulcro state via auto-merge, but the statechart stays in `:state/loading` because the ok-action callback (which sends the statechart event) never fires.

## Requirements

1. Identify the root cause: why does the ok-action callback not fire in headless/`:immediate` mode?
2. Fix at the library level (either in `../statecharts/` or in fulcro-rad-statecharts)
3. Remove the manual `:event/loaded` workaround from report E2E tests
4. Reports should transition from `:state/loading` to `:state/ready` automatically after load completes

## Key Code References

- `fops/load` definition: `../statecharts/operations.cljc:42-61` — returns `{:op :fulcro/load ...}` data map
- `run-fulcro-data-op! :fulcro/load`: `../statecharts/fulcro_impl.cljc:325-353` — creates ok-action closure that calls `sp/send!`
- Workaround: `src/demo/com/example/headless_report_tests.clj:60-75` — `wait-for-report!` manually sends `:event/loaded`
- Headless setup: `src/main/com/fulcrologic/rad/application.cljc:118-137` — `install-statecharts!` with `:event-loop? :immediate`

## Root Cause (Identified)

The ok-action callback chain works correctly — `sp/send!` fires, `drain-events!` runs, and `process-events!` transitions the statechart to `#{:state/ready}`. The actual bug was in `report_expressions.cljc`'s `process-loaded-data-expr`:

`(fops/apply-action (constantly updated))` captured a snapshot of the entire Fulcro state-map during expression evaluation (inside `process-event!`, before `save-working-memory!`). When the deferred `transact!!` executed `do-apply-action`, it replaced the entire state including statechart working memory, overwriting `#{:state/ready}` back to `#{:state/loading}`.

**Fix**: Changed to a transform function that applies changes at swap-time:
```clojure
(fops/apply-action (fn [current] (-> current (preprocess-raw-result data) (filter-rows-state data) (sort-rows-state data) (populate-page-state data))))
```

## Affected Modules

- `../statecharts/src/main/com/fulcrologic/statecharts/integration/fulcro_impl.cljc` — Likely fix location
- `src/demo/com/example/headless_report_tests.clj` — Remove workaround after fix

## Verification

1. [x] Root cause identified and documented
2. [x] Fix applied at library level (report_expressions.cljc, not statecharts lib)
3. [x] Report E2E tests pass WITHOUT manual `:event/loaded` send (3 tests, 21 assertions, 0 failures)
4. [x] Form loads via routing also work — no `(constantly snapshot)` pattern in form code
5. [x] No regressions in unit tests (14 tests, 51 assertions, 0 failures)
