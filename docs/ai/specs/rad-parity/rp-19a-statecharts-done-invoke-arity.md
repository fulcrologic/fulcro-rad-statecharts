# Spec: statecharts `application-chart` done.invoke arity bug

**Status**: active
**Priority**: P0
**Created**: 2026-10-31
**Owner**: AI
**Depends-on**: none

## Context

The rp-01 critique found this (verified by the conductor).

- `../statecharts/src/main/com/fulcrologic/statecharts/integration/fulcro.cljc` (~line 300) always builds the execution model with `{:explode-event? true}`, so expression fns are called with **4 args**.
- `fulcro_impl.cljc` `application-chart`'s `:done.invoke.*` script is `(fn [_env {:keys [_event]}] ...)`, which only accepts **2**.
- **In CLJ:** every child session that reaches a final state (e.g. a `start-form!` form exiting) throws `ArityException: Wrong number of args (4)`. The cleanup `op/delete`s for `[::sc/session-id source]` and `[::sc/local-data source]` never run, so finished sessions leak in the Fulcro state map. That is exactly the headless/CLJ case.
- **In CLJS:** the extra args are silently ignored.
- In fulcro-rad-statecharts this shows as 6 `ArityException`s in an uncaptured `:unit` run. kaocha's output capture hid them.

## Requirements

1. Fix the `:done.invoke.*` script to accept the exploded arity, e.g. `(fn [_env {:keys [_event]} & _] ...)`.
2. Audit every expression fn in `../statecharts/src/main/com/fulcrologic/statecharts/integration/**` (routing, ui-routes, operations, async-operations, fulcro, fulcro-impl, rad-integration) for fixed arities that would break under `:explode-event? true`. Fix them the same way. List every one you find.
3. Add a CLJ test that fails before the fix. It starts a child session under the Fulcro integration (or the master application chart), drives it to a final state, and asserts:
   - no exception is thrown or logged;
   - the child's `::sc/session-id` and `::sc/local-data` entries are removed from the state map.
4. Statecharts' own suite stays green.

## Rules

- Work in `../statecharts` on **main**, the same as the earlier `.cljfmt.edn` fix (`a687336`). Commit locally and do NOT push. A release (RC20) is the human's call.
- Run CLJ tests via kaocha in your own REPL (see that repo's README/Makefile/`tests.edn` for aliases). Never use a CLI test runner.
- Run `cljfmt check` on changed files. `.cljfmt.edn` in that repo is already fixed.
- If `deps.edn` `:deps` change, regenerate the pom with `clj -Spom`. They shouldn't need to change.

## Verification

1. [ ] The new test fails before the fix (shows the ArityException or the leaked keys) and passes after.
2. [ ] The audit list is in the commit message and in a `CLAUDE.md` note in `integration/` (create it if missing): always use `& _` or 4-arity for expression fns.
3. [ ] The full statecharts CLJ suite is green.
4. [ ] `cljfmt check` passes. The REPL is killed. The commit is local only.
5. [ ] In fulcro-rad-statecharts, an uncaptured `:unit` run against `:local/root ../statecharts` has no `fulcro-impl` ArityException. Run it once from a REPL started with `-Sdeps` that overrides statecharts; don't edit deps.edn.
