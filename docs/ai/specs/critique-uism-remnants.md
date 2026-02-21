# Spec: UISM/DR Remnants Critique
**Status**: backlog
**Priority**: P1
**Created**: 2026-02-21

## Context

This library was ported from UISM/dynamic-routing to statecharts. All active code paths now use statecharts. This is NOT a drop-in replacement for old fulcro-rad — it's a new library. "Backward compatibility" is therefore NOT a valid reason to keep dead UISM code.

The audit found significant UISM remnants concentrated in three main source files (`form.cljc`, `report.cljc`, `application.cljc`) plus stale docstrings, test code, and documentation. Container was already fully cleaned.

## Findings

### MUST REMOVE

1. **`form-machine` defstatemachine** — `form.cljc:1126-1385` — ~260 lines of dead UISM state machine definition. Replaced by `form_chart.cljc`. No code in this library references it. The only reference is the test at `form_spec.cljc:140` which itself should be updated. Keeping this actively misleads users about which code path is live.

2. **UISM helper functions used only by `form-machine`** — These are internal helpers that ONLY serve the dead `form-machine`. They are not called from the statechart code path:
   - `start-edit` — `form.cljc:718-725` — loads form entity via `uism/load`
   - `start-create` — `form.cljc:912-929` — initializes new entity via UISM `apply-action`
   - `leave-form` — `form.cljc:933-958` — UISM form exit handler using `dr/change-route!`
   - `calc-diff` — `form.cljc:961-968` (guardrails `>defn` with `::uism/env` spec)
   - `clear-server-errors` — `form.cljc:972-974` — `uism/assoc-aliased` wrapper
   - `global-events` — `form.cljc:977-985` — UISM event handler map
   - `auto-create-to-one` — `form.cljc:993-1019` — UISM `apply-action` based
   - `apply-derived-calculations` — `form.cljc:1042-1051` — uses `uism/actor-class`, `apply-action`
   - `run-initialize-ui-props` — `form.cljc:1054-1088` — `uism/apply-action` based
   - `handle-save-result` — `form.cljc:1096-1112` — `uism/actor->ident`, `uism/transact`
   - `undo-all` — `form.cljc:1118-1124` — `uism/actor->ident`, `apply-action`
   - `mark-fields-complete*-uism` (target-ready!) — `form.cljc:882-890` — uses `dr/router-for-pending-target`
   - `attr-value` — `form.cljc:711-716` — already commented out with `#_`

3. **`report-machine` defstatemachine** — `report.cljc:415-520` — ~106 lines of dead UISM state machine. Replaced by `report_chart.cljc`. No code in this library references it.

4. **UISM helper functions used only by `report-machine`** — Internal helpers serving only the dead `report-machine`:
   - `report-options` (UISM version) — `report.cljc:160-161` — uses `uism/actor-class`
   - `current-control-value` — `report.cljc:168-178` — uses `uism/actor->ident`, `uism/actor-class`
   - `initialize-parameters` (UISM version) — `report.cljc:180-209` — heavy UISM usage
   - `load-report!` — `report.cljc:215-250` — uses `uism/load`, `uism/activate`
   - `filter-rows` — `report.cljc:255-269` — uses `uism/alias-value`, `uism/assoc-aliased`
   - `sort-rows` — `report.cljc:274-286` — uses `uism/alias-value`, `uism/assoc-aliased`
   - `apply-uism-post-process` — `report.cljc:300-307` — named with "uism" prefix
   - `goto-page*` — `report.cljc:311-337` — uses `uism/alias-value`, `uism/assoc-aliased`
   - `next-page` / `prior-page` — `report.cljc:339-346` — uses `uism/alias-value`
   - `preprocess-raw-result` — `report.cljc:366-374` — uses `uism/alias-value`, `uism/assoc-aliased`
   - `handle-filter-event` — `report.cljc:378-381` — uses `uism/trigger!`, `uism/assoc-aliased`
   - `handle-resume-report` — `report.cljc:386-413` — uses `uism/actor-class`, `uism/retrieve`

5. **`uism` require in `debugging.cljc`** — `debugging.cljc:16` — Required but never used (zero `uism/` calls in file). Dead import.

6. **`dr` require in `report.cljc`** — `report.cljc:26` — Required (`dynamic-routing as dr`) but never used anywhere in the file. Dead import.

7. **`uism` require in `form_spec.cljc`** — `form_spec.cljc:9` — Only used for the test at lines 139-145 which tests the old UISM `start-create` path. This test should be rewritten to test the statechart equivalent, or removed.

8. **UISM test in `form_spec.cljc`** — `form_spec.cljc:135-157` — "Form state initialization" test that manually constructs a UISM env and calls `form/start-create` on the old UISM code path. Should be rewritten against the statechart flow.

### SHOULD REMOVE

9. **Stale UISM docstrings in `form_options.cljc`** — `form_options.cljc:278-292` — The `fo/triggers` docstring documents the old `(fn [uism-env ...] uism-env)` signature for `:on-change`, `:started`, `:saved`, `:save-failed`. The new statechart convention returns an ops vector `(fn [env data form-ident key old new] -> [ops])`. These docstrings will actively mislead users.

10. **Stale UISM docstrings in `report_options.cljc`** — Multiple options documented with `(fn [uism-env] ...)` signatures:
    - `ro/page-size` — `report_options.cljc:336` — documents `(fn [uism-env] page-size)`
    - `ro/post-process` — `report_options.cljc:410` — documents `(fn [uism-env] new-env)`
    - `ro/raw-result-xform` — `report_options.cljc:498` — documents `(fn [uism-env] uism-env)`
    - `ro/load-cache-expired?` — `report_options.cljc:506-516` — documents `(fn [uism-env cache-looks-stale?] boolean?)`
    - `ro/load-options` — `report_options.cljc:538` — documents `(fn [uism-env] map?)`
    - `ro/pre-process` — `report_options.cljc:562` — documents `(fn [env] env')` as "A UISM handler"

11. **`::uism/asm-id` in `default-network-blacklist`** — `application.cljc:28` — The blacklist filters `::uism/asm-id` from network queries. Since forms/reports no longer include `::uism/asm-id` in their queries (already removed during macro-rewrites), this entry is dead. However, removing the `uism` require from `application.cljc` requires replacing the keyword with its fully-qualified string form or removing the entry entirely.

12. **Stale UISM comments in `headless_routing_tests.clj`** — `headless_routing_tests.clj:257-261` — Comments reference "form UISM" but the form now uses statecharts.

13. **`dynamic-routing-options` parameter name in stubbed functions** — `form.cljc:1564,1573,1593` — The `view!`, `edit!`, `create!` stubs still accept a `dynamic-routing-options` parameter. These stubs should either be removed (since `routing.cljc` now has `edit!` and `create!`) or updated.

14. **Stale comment in `report_expressions.cljc`** — `report_expressions.cljc:270-273` — `TODO` comment about `post-process` receiving a `uism-env`. The statechart version now passes `state-map` and `data`.

15. **Stale comment in `form_expressions.cljc`** — `form_expressions.cljc:65` — "options may already be in data if passed at start!, or in event-data for UISM compat"

### KEEP

16. **`uism` require in `form.cljc`** — `form.cljc:16` — KEEP ONLY IF `form-machine` is kept (see MUST REMOVE #1). If `form-machine` is removed, this require becomes dead and should also be removed. If there's a phased removal, this is acceptable as a transient dependency.

17. **`uism` require in `report.cljc`** — `report.cljc:27` — Same as above — KEEP ONLY IF `report-machine` is kept.

18. **`dr` require in `form.cljc`** — `form.cljc:34` — Used by `leave-form` (line 946, 952), `start-create` (line 918), `form-machine` `:state/loading` handler (line 1159), and `form-machine` `:event/continue-abandoned-route` (line 1281). ALL usages are inside UISM-only code paths. If MUST REMOVE items are deleted, this require becomes dead too.

### QUESTIONABLE

19. **`form_machines.cljc`** — This file is NOT dead UISM code. Despite its name (which echoes the old `form-machines` from Phase 0), it's a new statechart-based file providing reusable chart fragments for custom form statecharts. The name is confusing given the UISM cleanup context. **Decision needed**: Should it be renamed to something like `form_chart_fragments.cljc` to avoid confusion with the deleted UISM form-machines?

20. **`form/view!`, `form/edit!`, `form/create!` stubs** — `form.cljc:1558-1594` — These are log-warning stubs. `routing.cljc` now provides working `edit!` and `create!` (no `view!`). **Decision needed**: Should the stubs be removed entirely (breaking old import sites) or updated to delegate to `routing.cljc`? Since this is a new library, removing seems right.

21. **CLAUDE.md notes about UISM** — `src/main/com/fulcrologic/rad/CLAUDE.md:101-110,150,274-278` — These document the conversion history and rationale for keeping UISM. After cleanup, these notes should be updated to reflect the new state (UISM removed, not retained).

## Requirements

1. Delete `form-machine` defstatemachine and all UISM-only helper functions from `form.cljc` (~lines 711-1385, carefully preserving any non-UISM functions in that range)
2. Delete `report-machine` defstatemachine and all UISM-only helper functions from `report.cljc` (~lines 155-520)
3. Remove `uism` require from `form.cljc`, `report.cljc`, `debugging.cljc`, `application.cljc`
4. Remove `dr` (dynamic-routing) require from `form.cljc` and `report.cljc`
5. Replace `::uism/asm-id` in `application.cljc:28` with the raw keyword `:com.fulcrologic.fulcro.ui-state-machines/asm-id` (still useful to blacklist for any mixed-usage scenarios) OR remove the entry entirely
6. Remove `uism` require and rewrite UISM-based test in `form_spec.cljc:135-157`
7. Update `form_options.cljc` docstrings to document the statechart callback signatures
8. Update `report_options.cljc` docstrings to document statechart-compatible signatures
9. Remove or delegate `form/view!`, `form/edit!`, `form/create!` stubs
10. Fix stale UISM comments in `headless_routing_tests.clj`, `report_expressions.cljc`, `form_expressions.cljc`
11. Update `src/main/com/fulcrologic/rad/CLAUDE.md` to reflect UISM removal

## Impact Summary

| Category | Lines of code | Files affected |
|----------|--------------|----------------|
| Dead UISM state machines | ~366 lines | form.cljc, report.cljc |
| Dead UISM helpers | ~200 lines | form.cljc, report.cljc |
| Stale requires | 5 imports | form.cljc, report.cljc, debugging.cljc, application.cljc, form_spec.cljc |
| Stale docstrings | ~30 lines | form_options.cljc, report_options.cljc |
| Stale comments | ~10 lines | headless_routing_tests.clj, report_expressions.cljc, form_expressions.cljc |
| Stale stubs | ~40 lines | form.cljc (view!/edit!/create!) |
| **Total removable** | **~650 lines** | **8 files** |

## Verification

- [ ] `grep -ri 'uism\|ui-state-machine\|defstatemachine' src/` returns zero results in main source (excluding CLAUDE.md)
- [ ] `grep -ri 'dynamic-routing\|dr/' src/main/` returns zero results
- [ ] All existing tests pass after removal
- [ ] Demo app compiles and functions correctly
- [ ] `form_spec.cljc` test rewritten to use statechart flow
- [ ] No remaining `(fn [uism-env` in any docstring
