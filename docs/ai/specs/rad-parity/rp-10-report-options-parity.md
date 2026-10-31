# Spec: Report Options and Hook Parity

**Status**: backlog
**Priority**: P1
**Created**: 2026-10-31
**Owner**: AI
**Depends-on**: rp-04
**Skills**: `clojure`, `clojure-repl`, `fulcro-spec-tdd`, `statechart`, `fulcro-rad`, `fulcro-rad-reports`

## Context

Many report and control options are silently ignored or changed signature. Sources: gap report §3 Reports/controls and "Documented options never read"; `review/review-reports.md` P1-7, 9, 11, 12, 13 and P2.

Line numbers shifted after the `fab72f9` reformat; use the function names.

**Resume.** On `:event/resume` with a still-valid cache, `report.cljc` runs `resume-from-cache-expr` without re-initializing params. New route params and control defaults are ignored until the cache expires. Upstream `handle-resume-report` always ran `initialize-parameters`.

**Hook signatures changed silently.** Upstream `ro/` docstrings, which users still read, describe `uism-env`.
- `ro/report-loaded` and `ro/before-load` are now `(fn [state-map] state-map)` with no report ident, so they can't find their own report.
- `ro/load-cache-expired?` gets `nil` instead of an env.
- A `ro/page-size` fn gets `nil` in `report.cljc` `populate-page-state` and spr `process-loaded-page`, but the statechart env in spr's load.
- `ro/load-options` gets the statechart env.

**Dead options:**
- `ro/post-process`. Upstream: `(fn [uism-env] env)`, run after sort/filter/paginate.
- `ro/columns-EQL`. The generated row query dropped upstream's `eql-overrides` merge.
- `ro/layout-style` and `ro/row-style`. They never reach the `rr/*` multimethods, which dispatch on `rro/style`, `rro/row-style` and so on.

**Variant option keys moved namespace.** The upstream keys are silently ignored:
- `:com.fulcrologic.rad.state-machines.server-paginated-report/point-in-time-view?` (upstream default **true**) and `…/direct-page-access?`;
- `:com.fulcrologic.rad.state-machines.incrementally-loaded-report/chunk-size`.

This lib reads `spr/point-in-time-view?` etc. in its own namespaces instead.

**Missing fns.** `report/reload!` is gone, though the `control_options.cljc` docstring calls it. So is the report-ns `set-parameter!`.

**`sro/triggers`** is documented with an `:on-change (fn [env data report-ident k old new])` contract, and the macro's error for `:com.fulcrologic.rad.report/triggers` points to it, but nothing reads it.
- Note: upstream 1.6.24 has **no** report triggers, so this is a new feature, not parity. Checked with `git show fulcro-rad-1.6.24:src/main/com/fulcrologic/rad/report_options.cljc`.

**`sro/on-loaded` and `sro/statechart-id`** are documented but never read.

**Minor (review P2):**
- `:event/clear-sort` is handled only in the standard chart's `:state/ready`. The incremental chart doesn't handle it at all.
- A sort from the URL replaces the initial-sort map instead of deep-merging into it.
- Server-paginated sort and refresh set point-in-time even when it's disabled.
- With a page number in the URL and `run-on-mount?` false, no initial load runs (upstream ran it).
- Incremental `cache-expired?` uses `boolean?` where the standard chart uses `boolean`.

## Requirements

1. **Resume.** Resume always re-initializes params from route params and control defaults, then either reloads (cache expired) or re-filters from cache. Test: resuming with a new route param uses it without a reload when the cache is valid.
2. **Hook signatures (decision: adapt, never pass nil; document in `sro`).**
   - **State-map hooks.** `ro/report-loaded`, `ro/before-load` and `ro/post-process` are `(fn [state-map report-ident] state-map)`. Call them through `->arity-tolerant`, so existing 1-arity fns keep working.
   - **Env hooks.** `ro/load-cache-expired?` gets `(fn [env cache-looks-stale?])`. `ro/page-size` gets `(fn [env])`. Every call site passes the same thing. Where no statechart env is in scope (pure state-map fns), pass the minimal env needed, at least `:fulcro/app`, and document that.
   - Add a "Hook signatures under the statechart engine" table to the `sro` ns docstring, covering every `ro/` hook that this engine calls differently from upstream.
3. **`ro/post-process`** is called after sort/filter/paginate in all three charts.
4. **`ro/columns-EQL`** overrides are merged into the generated row query, as upstream's `eql-overrides` did.
5. **Upstream variant keys accepted.** The upstream `point-in-time-view?`, `direct-page-access?` and `chunk-size` keys work, and the statechart-ns keys stay as aliases. If both are given, the statechart key wins.
   - `point-in-time-view?` defaults to **true**, as upstream.
   - Point-in-time is set only when it's enabled. This covers sort, filter, refresh and goto.
6. **Missing fns.** `report/reload!` and report-ns `report/set-parameter!` exist, delegate to the control/chart fns, and are documented. The `control_options.cljc` docstring example works.
7. **`sro/triggers :on-change`** is implemented per its docstring. It's called when a control or parameter value of the report changes, through `control/set-parameter!` and `:event/set-ui-parameters`, and its returned ops are applied in the report's session.
   - Upstream had no report triggers (see Context). The conductor may drop this requirement. If it's dropped, remove `sro/triggers` and change the macro error message instead.
8. **`sro/on-loaded` and `sro/statechart-id`** are removed, with a compile-time **warning** in `defsc-report` when present that names the working alternative: `ro/report-loaded`, or a keyword `sro/statechart`. This is the same decision as rp-07 req 13 for `sfo`.
9. **Clear sort.** `:event/clear-sort` is handled in every non-loading state of all three charts.
10. **`ro/layout-style` and `ro/row-style` reach the renderers.** The report's render path supplies them as the `rro/style`/`rro/row-style` dispatch values when the `rro/*` keys aren't set. Test with a defmethod on a non-default style.
11. **URL-derived state:**
    - A sort from the URL deep-merges into `ro/initial-sort-params` (so `:sortable-columns` survives).
    - A page number in the URL triggers the initial load even when `run-on-mount?` is false, as upstream did.
12. **Consistency.** Incremental `cache-expired?` uses `boolean`, like the standard chart.

## File Ownership

- `src/main/com/fulcrologic/rad/statechart/report.cljc`, `server_paginated_report.cljc`, `incrementally_loaded_report.cljc`, `report_options.cljc`, `control.cljc`, `control_options.cljc`
- Report and control test namespaces under `src/test/com/fulcrologic/rad/statechart/`, plus new ones
- `src/main/com/fulcrologic/rad/statechart/CLAUDE.md` (your own sections only)

Read-only: `container.cljc` (rp-05), `form.cljc`, `rendering/headless/**`.

## Affected Modules

- The three report charts and expressions, `defsc-report` (row query, warnings), `report-route-state` initial-load logic, `control/set-parameter`
- The `sro` and `control_options` docstrings

## Approach

- Read the upstream handlers in `../fulcro-rad/src/main/com/fulcrologic/rad/report.cljc` and `state_machines/*.cljc` (read-only) for the exact behavior of resume, post-process, columns-EQL and clear-sort.
- For req 7, the trigger needs the old value. Capture it in `set-parameter` before the write, then send an event to the report session carrying `{k old new}`.

## Open Questions

- Keep `sro/triggers` (a new feature), or drop it (req 7)? Recommendation: keep it. It's small, it's already documented, and the macro already steers users to it. The tradeoff is one more engine-specific option with no upstream counterpart.

## Verification

1. [ ] New or updated tests fail before and pass after for each requirement. Every hook is called with non-nil arguments.
2. [ ] The `sro` docstring hook table covers every hook the engine calls differently.
3. [ ] Tests run with kaocha in the implementer's own REPL. No CLI test runs.
4. [ ] Full CLJ suite green: `:unit` + `:demo`.
5. [ ] CLJS `ci-tests` compiles with 0 warnings.
6. [ ] `statechart/CLAUDE.md` report section updated. REPL killed. Committed and pushed.
7. [ ] `cljfmt check <changed files>` passes from the repo root (fulcro `.cljfmt.edn`; see root `CLAUDE.md` "Build Hygiene").
