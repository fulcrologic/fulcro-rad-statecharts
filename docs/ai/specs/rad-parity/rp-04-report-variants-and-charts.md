# Spec: Report Variants, Chart Registration, and Report Route Hygiene

**Status**: backlog
**Priority**: P0
**Created**: 2026-10-31
**Owner**: AI
**Depends-on**: rp-01
**Skills**: `clojure`, `clojure-repl`, `fulcro-spec-tdd`, `statechart`, `fulcro`, `fulcro-rad`, `fulcro-rad-reports`

## Context

Report variants and custom charts are broken, and routed reports leak data. Sources: gap report §1.1, §1.5, §3 Reports; `review/review-reports.md` P0-1/2/3 and P1-4/5/6; `review/review-routing.md` P1-8 and the "istate vs side-effect" section.

**Chart clobbering.** The same bug is in `start-report!` (`statechart/report.cljc`, about line 672), `start-server-paginated-report!` (`server_paginated_report.cljc`, about line 328) and `start-incrementally-loaded-report!` (`incrementally_loaded_report.cljc`, about line 233):
- A map chart is registered under the variant's shared id (`::report-chart` etc.).
- A keyword chart gets the default chart registered over it.
- `start_report_spec.cljc` (about lines 97–114, "falls through to the default machine key") asserts the clobbering.
- A server-paginated report declared with `sro/statechart spr/server-paginated-report-statechart` and started by `start-report!` overwrites `::report-chart` for every standard report.

**Variants can't be routed or put in a container.**
- `report-route-state` (about line 1107) and the container (`container.cljc` `initialize-params-expr`) always call `report/start-report!`. That installs only the standard aliases: no `:loaded-page`, `:page-cache`, `:total-results` or `:point-in-time`.

**The incremental report renders 0 rows** even when started directly. Its alias map lacks `:filtered-rows`/`:sorted-rows`, which the shared `filter-rows-state`/`sort-rows-state`/`populate-page-state` require. `incrementally_loaded_report_spec` mocks every expression, so nothing catches it.

**`report-route-state` passes `(merge data event-data)` as `:route-params`.** That is the whole routing data model: the `:fulcro/state-map` snapshot, `:_event` and `:route/idents`. It gets stored in the report session's local data inside the Fulcro DB.

**`:ui/busy?` is set on load and never cleared on `:event/failed`.** Affected: report.cljc (about lines 248/577), spr (74/279), ilr (55/179/188).

**Server-paginated `:event/run`** is a plain reload. Upstream `refresh` reset the page to 1, cleared the page cache and total, and set a new point-in-time.

**Server-paginated single-row view is missing.** Upstream `state_machines/server_paginated_report.cljc` had `single-row-selected-ident`, `load-single-row`, `showing-single-row?` and `:event/single-item-loaded`.

**`ro/route` is ignored**, so URLs use the class name.

**`::report/externally-controlled?`** reaches children only on resume, not on start (about lines 694–711). A container child therefore initializes global controls and may load before the container's params apply. The container side is rp-05.

**Report-side `:default` collision** (missed by the review; found while writing this spec):
- Upstream `com.fulcrologic.rad.report` defines `rr/render-row`, `rr/render-report` and `rr/render-controls` `:default` (`../fulcro-rad` `report.cljc:54-59`). They are map-registry based.
- This lib's `render-layout` is `report-impl/render-layout`, which calls `rr/render-report`.
- So if anything loads upstream `rad.report` after a plugin, the plugin's `:default` report methods are replaced and reports render nil. The legacy SUI plugin and fulcro-rad-tui require upstream `rad.report`.
- This is the report analogue of rp-02's form collision.

**Reports as istate targets.** `defsc-report` sets `sfro/statechart` but no `sfro/actors`, so an `istate`'d report starts with a nil `:actor/report`.

## Requirements

1. **Registration.** In all three start fns, a map chart is registered under the report's registry key (the `scr/istate` convention). A keyword chart is used as-is, never re-registered, and throws `ex-info` if it isn't registered.
   - Replace the clobbering assertions in `start_report_spec.cljc`.
   - New test: two report classes with different inline charts running at the same time each process events with their own chart.
2. **Variant-aware start.** `report/start-report!` starts standard, server-paginated and incremental reports with each chart's own aliases and start data. So `report-route-state` and containers work for all three without the caller choosing a start fn.
   - `start-server-paginated-report!`/`start-incrementally-loaded-report!` stay public as thin wrappers.
   - `report.cljc` must not require the variant namespaces (that would be circular).
3. **Incremental aliases.** Add `:filtered-rows`/`:sorted-rows`. Rows render after load, and sort and filter work.
   - Add at least one incremental test that runs the real expressions on a headless app with a silent remote and a delivered load result, and asserts `:ui/current-rows`.
4. **Route-param hygiene.** `report-route-state` passes only this target's route params, i.e. `[:routing/parameters target]` merged with route-event data restricted to the route's `:route/params`, and `:report/param-keys` if given.
   - A test asserts the report session's local data has no `:fulcro/state-map`, `:_event` or `:route/idents`.
5. **Busy on failure.** `:ui/busy?` is false after `:event/failed` in all three charts. One test per chart.
6. **Server-paginated refresh.** `:event/run` resets current page to 1, clears page cache and total, and takes a new point-in-time.
   - Test: change a filter param, run, and assert the stale cached page isn't shown and the page count is recomputed.
   - Gate point-in-time handling exactly as sort/filter do today. rp-10 makes it honor `point-in-time-view?`.
7. **Server-paginated single-row view.** Port upstream `single-row-selected-ident`, `load-single-row`, `showing-single-row?` and the `:event/single-item-loaded` handling, with tests.
8. **`ro/route` → `:route/segment`.** `report-route-state` defaults `:route/segment` from `ro/route`, and an explicit `:route/segment` wins.
9. **Start options in session data.** `start-report!` stores `::report/externally-controlled?` (and the other start options) in session data at **start**, not only on resume. Test: a report started externally controlled doesn't initialize global controls.
10. **Report-side render collision, library side.** Reports render through the installed plugin whether or not upstream `com.fulcrologic.rad.report` loads after it. Use the same mechanism rp-02 chose for forms: no reliance on the `:default` method of upstream `rr/*` multimethods.
    - If the mechanism needs a lib-owned dispatch point, add it here and document it for plugins in the `report.cljc` ns docstring.
    - rp-06 does the headless side.
    - Test both load orders.
11. **istate safety.** `defsc-report` generates `sfro/actors` (`:actor/report`), and the chart owns its aliases (req 2), so `scr/istate` on a report works.
    - `report-route-state` (rstate + side-effect start) stays the documented route path, because it keeps upstream caching and `:event/resume`.
    - Test both paths.

## File Ownership

- `src/main/com/fulcrologic/rad/statechart/report.cljc`, `server_paginated_report.cljc`, `incrementally_loaded_report.cljc`, `report_options.cljc`, `control.cljc`, `control_options.cljc`
- Report test namespaces under `src/test/com/fulcrologic/rad/statechart/`: `report_*`, `start_report_spec.cljc`, `server_paginated_report_spec.cljc`, `incrementally_loaded_report_spec.cljc`, and new ones
- `src/main/com/fulcrologic/rad/statechart/CLAUDE.md` (your own sections only)

Read-only: `container.cljc` (rp-05) and `rendering/headless/**` (rp-06).

## Affected Modules

- The three report namespaces (start fns, aliases, failure transitions, spr refresh and single-row)
- `report-route-state`, the `defsc-report` macro, and `render-layout`

## Approach

**Aliases belong to the chart, not the starter.** Two options:
- Put each chart's alias map and variant start data into the chart's `data-model` expression, so any starter (`start-report!`, istate, container) gets them.
- Or keep a small registry keyed by chart id that each variant ns fills at load time, which `start-report!` consults.

Prefer the data-model if `scf/start!` `:data` and `data-model` merge the way you need. Verify that first and record it.

**Prior art.** Unmerged `origin/move-reports-to-istates` (March 2026) converted `report-route-state` to istate. The human judged it STALE (16 commits behind). Use it as reference only; never merge it. Read it for req 11. This spec deliberately keeps rstate as the documented route path, for caching and resume parity.

**Registration helper.** Share one private helper across the three start fns: `(chart-id-for app report-class)` registers a map under the registry key, validates a keyword, and returns the id.

**Single-row view.** Port it from upstream `../fulcro-rad/src/main/com/fulcrologic/rad/state_machines/server_paginated_report.cljc`, converting UISM handlers to expression fns that return ops, with the same patterns as the existing spr expressions.

## Open Questions

- None.

## Verification

1. [ ] New or updated tests fail before and pass after for every numbered requirement. The clobbering assertions in `start_report_spec` are replaced, not deleted.
2. [ ] A routed server-paginated report and a routed incremental report show rows.
3. [ ] Tests run with kaocha in the implementer's own REPL. No CLI test runs.
4. [ ] Full CLJ suite green, `:unit` + `:demo`. Report render works in both load orders.
5. [ ] CLJS `ci-tests` compiles with 0 warnings.
6. [ ] `statechart/CLAUDE.md` records the alias ownership and registration rules. REPL killed. Committed and pushed.
7. [ ] `cljfmt check <changed files>` passes from the repo root (fulcro `.cljfmt.edn`; see root `CLAUDE.md` "Build Hygiene").
