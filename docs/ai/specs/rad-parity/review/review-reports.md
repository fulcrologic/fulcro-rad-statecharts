# Reports / Containers / Controls: parity review (statecharts port vs RAD 1.6.24)

Paths are relative to this repo. `rpt`=`src/main/com/fulcrologic/rad/statechart/report.cljc`, `spr`=`.../server_paginated_report.cljc`, `ilr`=`.../incrementally_loaded_report.cljc`, `ctr`=`.../container.cljc`, `ctl`=`.../control.cljc`. Upstream source is the same as the 1.6.24 tag. Every finding is Verified (traced in code) unless marked Suspected.

## Prioritized gaps

### P0: core feature broken
1. **Custom charts (`sro/statechart`, the replacement for `ro/machine`) are broken in both forms.** `start-report!` (rpt:683-692) registers an inline chart map under the shared key `::report/report-chart`. The statecharts runtime looks the chart up by `statechart-src` on every event (`statecharts/.../v20150901_impl.cljc:899-908`). So the report started last swaps the chart under every running standard report session. A keyword chart is no better: the default `report-statechart` gets registered over the user's pre-registered chart, because `(map? user-chart)` is false. The same pattern is in spr:336-344 and ilr:241-249. `start_report_spec.cljc:97-114` asserts the clobbering behaviour.
2. **Server-paginated and incremental reports can't be routed or put in a container.** `report-route-state` (rpt:1128) and the container (ctr:130) both call `report/start-report!`. That installs only the standard aliases (rpt:698-709), with no `:loaded-page`, `:page-cache` or `:total-results`. The load then gets `:target nil`, `process-*` reads nil results, and the page cache is written to the root key `1`. There is no route-state helper for either variant.
3. **The incremental report always renders zero rows, even when started with `start-incrementally-loaded-report!`.** Its aliases (ilr:255-265) lack `:filtered-rows` and `:sorted-rows`. `filter-rows-state` therefore does `assoc-in sm nil`, `sort-rows-state` reads nil, and `populate-page-state` writes `[]`. Sorting is broken the same way. `incrementally_loaded_report_spec` mocks every expression, so no test catches it.

### P1: options/features users will hit
4. **`:event/run` on a server-paginated report doesn't refresh** (spr:320). Upstream `refresh` reset the page to 1, cleared the page cache and total, and set a new point-in-time. After `control/run!` with new filters, cached pages and the page count are stale.
5. **Server-paginated single-row view is missing.** `single-row-selected-ident`, `load-single-row`, `showing-single-row?` and `:event/single-item-loaded` were not ported.
6. **`:ui/busy?` stays true after `:event/failed`.** It is set on load (rpt:248, spr:74, ilr:55) and never cleared on the failure transitions (rpt:577, spr:279, ilr:179/188). Upstream never set busy on load.
7. **Resume without cache expiry skips re-initializing parameters** (rpt:616-617). Upstream `handle-resume-report` always ran `initialize-parameters`, so new route params and control defaults applied when the user navigated back. Now they are ignored until the cache expires.
8. **URL persistence is partial.**
   - Only the standard chart syncs, from `:state/ready` on-entry (rpt:520-540).
   - It writes only `:ui/parameters`. Global controls (the default kind, stored under `::control/id`) never reach the URL.
   - `set-parameter` no longer updates the URL (ctl:73 TODO).
   - `ro/track-in-url?` is ignored: a report in a modal still rewrites the URL.
   - Container child reports send their child id (the second element of the ident) as the route target, which is wrong.
   - Server-paginated and incremental reports don't sync at all, and there is no container URL sync.
9. **Lifecycle hook signatures changed silently. Users still read the upstream `ro/` docstrings, which document `uism-env`.**
   - `ro/report-loaded` and `ro/before-load` now receive a state-map and must return a state-map (rpt:247,397). `report-loaded` can no longer change state.
   - `ro/load-cache-expired?` receives `nil` instead of the env (rpt:510).
   - A `ro/page-size` fn receives `nil` (rpt:342, spr:91).
   - `ro/load-options` receives the statechart env.
   - `ro/post-process` is dead.
   - `sro/on-loaded` and `sro/triggers` are documented but never read.
10. **Containers aren't functional through routing or plugins.**
    - No route-state helper exists, so nothing calls `start-container!` on route entry.
    - `render-layout` always returns nil and logs an error (ctr:186-196). A `defsc-container` with no body renders nothing, and there is no multimethod a plugin could implement.
    - A custom container chart is ignored: `start-container!` hardcodes `::container-chart` (ctr:206-209), although the macro still stores `sfro/statechart`.
11. **`ro/columns-EQL` is dead in `defsc-report`.** The generated row-query (rpt:798-804) dropped upstream's `eql-overrides` merge.
12. **Variant option keywords changed namespace without warning.** `spr/point-in-time-view?`, `spr/direct-page-access?` and `ilr/chunk-size` now live in the statechart implementation namespaces (spr:26-32, ilr:32-34). Users migrating with the upstream `server-paginated-report-options` / `incrementally-loaded-report-options` keys get them silently ignored.
13. **The `report/reload!` and `report/set-parameter!` aliases are missing.** The `control_options.cljc:37` docstring example still calls `report/reload!`.

### P2: minor or docs
- `:event/clear-sort` is only handled in `:state/ready` (upstream had it global), and the incremental chart doesn't handle it at all.
- The busy flag set in `:state/sorting`/`:state/filtering` is cleared by the eventless transition in the same macrostep, so the UI never sees it. Upstream deferred the work with `trigger!`. (Suspected: depends on render timing.)
- Initial load no longer runs when the URL carries a page number and `run-on-mount?` is false (upstream ran it).
- A sort from the URL replaces the initial-sort map instead of deep-merging into it (rpt:201). A hand-edited URL loses `:sortable-columns`.
- Server-paginated sort and refresh set a point-in-time even when `point-in-time-view?` is false (spr:235,247).
- `::report/externally-controlled?` reaches children only on resume, not on start (rpt:694-711). On start, a child initializes global controls and may run its first load before the container's parameter ops apply (ctr:129-133). (Suspected: ordering.)
- The container sends `:event/unmount`, which no report chart handles, and child sessions are never stopped. The container's `sfro/busy?` now blocks route changes while any child is loading (new behaviour).
- `sro/statechart-id` is dead; `ro/route` and `co/route` are dead (the rstate segment replaces them).
- The incremental `cache-expired?` uses `boolean?` while the standard chart uses `boolean` (ilr:141 vs rpt:511).
- The deprecated aliases `generated-row-class` and `sc-report` were dropped.

## Option coverage

| Option | Status | Evidence | Note |
|---|---|---|---|
| row-pk, columns, source-attribute, BodyItem, query-inclusions, row-query-inclusion, denormalize?, initialize-ui-props | Works | rpt:745-822, 979-1040 | |
| column-EQL (attribute) | Works | rpt:803 | |
| columns-EQL | Dead-key | rpt:798-804 | |
| run-on-mount? | Works | rpt:226 | ignores URL page |
| controls / control-layout, `:local?`, `:retain?`, `:default-value` | Works | rpt:210-221, ctl:139 | |
| initial-sort-params, compare-rows | Works | rpt:173, 317 | |
| row-visible?, skip-filtering? | Works | rpt:297-315 | |
| paginate?, page-size | Partial | rpt:342 | fn arg is nil |
| raw-result-xform | Works | rpt:258 | |
| load-cache-seconds | Works | rpt:492 | |
| load-cache-expired? | API-changed | rpt:510 | gets nil env |
| report-loaded, before-load | API-changed | rpt:247,397 | state-map fns |
| load-options | API-changed | rpt:242 | statechart env |
| post-process | Dead-key | (no reads) | |
| track-in-url? | Dead-key | (no reads) | |
| machine | API-changed → Broken | rpt:652-655, 683-692 | `sro/statechart` (P0 #1) |
| route | Dead-key | rpt:753 | rstate `:route/segment` |
| column-formatters/-formatter, field-formatter(s), column-styles | Works | report-impl `formatted-column-value` | |
| column-headings/-heading, column-infos/-info | Works | report-impl `column-heading-descriptors` | |
| form-links | Works | headless report.cljc:64-79 | |
| title | Works (headless) | headless report.cljc:123 | |
| links, row-heading, rotate?, on-select-row, column-alignment, column-class(es) | Renderer-only | headless report.cljc | the headless plugin ignores all of these except form-links |
| row-actions | Partial (headless) | headless report.cljc:101-111 | `:visible?` and `:type` ignored |
| layout-style, row-style | Dead-key (effectively) | report-impl map dispatch | `rr/*` dispatches on `rro/style`, not on these |
| spr point-in-time-view?, direct-page-access? | API-changed | spr:26-32 | new keyword namespace |
| ilr chunk-size | API-changed | ilr:32-34 | new keyword namespace |
| co/children | Works | ctr:96-133 | |
| co/layout, co/layout-style, co/title | Dead-key | ctr:186-196 | no renderer path |
| co/route | Dead-key | ctr:245 | only validated |
| sro/triggers, sro/on-loaded, sro/statechart-id | Dead-key | grep: no reads | |

## Event mapping (upstream UISM → statechart)

| Upstream | Statechart | Diff |
|---|---|---|
| report `:initial` | `:state/initializing`, eventless to loading or ready | no URL-page auto-run |
| `:event/loaded` | loading → processing → ready | hook signatures; no post-process |
| `:event/failed` | → ready | busy stuck; no error log |
| goto/next/prior-page | ready external self-transition | URL synced on entry |
| `:event/sort` → `:event/do-sort` | `:state/sorting` (eventless) | `do-sort` event gone |
| `:event/filter` → `:event/do-filter` | `:state/filtering` | same |
| select-row, set-ui-parameters, run | ready / loading | parity |
| `:event/resume` | `cache-expired?` ? reinit + load : refilter | refilter branch skips reinit (P1 #7) |
| clear-sort (global) | ready only | |
| server-paginated `:event/run` = refresh | plain reload | P1 #4 |
| server-paginated `:event/single-item-loaded` | Missing | P1 #5 |
| server-paginated sort, filter, goto (cached or load), resume | parity | PIT always set |
| incremental page-loaded / loaded | processing-chunk → finalizing | finalize broken (P0 #3) |
| container `::uism/started` | `:state/initializing` on-entry | children start before param ops |
| container `:event/run` | `handle` broadcast | parity |
| (new) container `:event/resume`, unmount | broadcast | unmount unhandled |

## Public functions

- **Same:** `run-report!`, `start-report!`, `sort-rows!`, `clear-sort!`, `filter-rows!`, `goto-page!`, `next-page!`, `prior-page!`, `select-row!`, `current-page`, `page-count`, `currently-selected-row`, `current-rows`, `loading?`, `form-link`, `link`, `formatted-column-value`, `built-in-formatter`, `install-formatter!`, `install-row-layout!`, `rotate-result`, `genrow`, `report`, `clear-report!`, `trigger!`, `column-classes`, and the `render-*` helpers.
- **Missing:** `reload!`, `set-parameter!` (report-ns alias), `install-layout!`, `route-params-path`, `showing-single-row?`.
- **API-changed:** `report-options` became `report-options*`. The machine helpers (`filter-rows`, `sort-rows`, `populate-current-page`, `report-cache-expired?`, ...) became `*-state`/`*-expr` fns. `render-control` is now a 4-arg multimethod. `report-will-enter` and `container-will-enter` throw.
- **Controls:**
  - `run!` works.
  - `set-parameter!` works but does no URL tracking.
  - `current-value`, `component-controls` and `standard-control-layout` work.
  - Callbacks: `:action (fn [this])` works. `:onChange (fn [this v])` works for `:string`/`:boolean`. `:visible?` works. `:disabled?` only works for buttons.

## Rendering dispatch

- **Multimethods:** `rr/render-report`, `rr/render-body`, `rr/render-row`, `rr/render-controls`, `rr/render-header` and `rr/render-footer` cover reports. `control/render-control`, dispatching on `[type style]`, covers controls. Nothing registers `:default` methods (rpt:107-111), so a plugin is required.
- **Still map-based (runtime-atom):**
  - `::type->style->formatter`: works, used by `formatted-column-value`.
  - `::row-style->row-layout`: reached only if a plugin calls `default-render-row`.
  - `::control-style->control`: read by `control-renderer`, but there is no installer and nothing uses it.
  - `::style->layout`: read by `default-render-layout`, but `install-layout!` was removed.
- **Upstream plugins are incompatible:** the semantic-ui plugin's control map is no longer consulted.
- **A plugin can render everything except containers,** which have no hook at all. The headless plugin only implements `:button`, `:string` and `:boolean` controls, with no instant, number, picker or multi-picker.

## CLJS-only behaviour

None found on the statechart path. `debounce` runs immediately in CLJ (by design). URL sync is testable in CLJ through `simulated-url-history`, but `test_helpers/create-test-app-with-url-sync` is unused, so report URL sync has no tests. The report and container integration tests check only the active state: none checks rows, busy, URL or cache.
