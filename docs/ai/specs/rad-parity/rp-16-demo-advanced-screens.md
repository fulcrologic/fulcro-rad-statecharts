# Spec: Demo Advanced Screens (Sales, Dashboard, Master-Detail)

**Status**: backlog
**Priority**: P1
**Created**: 2026-10-31
**Owner**: AI
**Depends-on**: rp-05, rp-10, rp-13, rp-15
**Skills**: `clojure`, `clojure-repl`, `fulcro-spec-tdd`, `fulcro-headless`, `fulcro-rad-reports`, `clj-stubs`, `pathom`, `date-time`, `dataviz` (chart styling only)

## Context

The original demo's advanced screens haven't been ported. Sources: gap report §5; `review/review-demo.md` P1-7/8/9.

**SalesReport** (original `../fulcro-rad-demo/src/shared/com/example/ui/sales_report.cljc`): mock 1000-row data (`sales.cljc`), denormalized, paginated, with a picker filter.

**RealSalesReport:**
- invoice-statistics attributes and resolvers;
- `ro/raw-result-xform` with `report/rotate-result`;
- pivot (`ro/rotate?`);
- instant (`:starting-date`/`:ending-date`) and group-by controls;
- victory charts.

The statistics attributes and resolver are in `../fulcro-with-statecharts` (`model_rad/invoice.cljc`, `model/invoice.cljc`). The UI text is in `../fulcro-rad-statecharts-demo` (namespace-swap only, won't compile as is).

**Dashboard container** (original `dashboard.cljc`): needs rp-05 (routing and rendering).

**Master-detail** (original `master_detail.cljc`, a UISM report machine): no statechart port exists anywhere. This spec rebuilds it on rp-13's embedded API.

## Requirements

1. **SalesReport**: mock data resolver, denormalized rows, pagination, a `:picker` filter control, a route and nav entry.
2. **RealSalesReport**:
   - statistics attributes and resolvers;
   - `raw-result-xform` + `rotate-result`;
   - `ro/rotate?` pivot;
   - instant and group-by controls;
   - a victory chart.
   - victory is an npm dep via **pnpm**. CLJ stubs follow the `clj-stubs` skill, so the namespace loads in CLJ and headless tests render a placeholder that carries the chart data.
3. **Dashboard**: a `defsc-container` with at least two report children sharing a control. It's routed with `container-route-state` and rendered through the container multimethod (headless in tests, SUI in the browser).
4. **Master-detail**: an item report beside an embedded `ItemForm`, built on rp-13. Select a row → the form loads → edit → save → the report row updates. Switching rows while dirty is guarded per rp-13.
5. **Headless tests.** One new demo test ns per screen:
   - Sales: pagination and the picker filter changing rows.
   - RealSales: date-range and group-by params reach the resolver; the pivoted rows are as expected.
   - Dashboard: both children reach ready; a shared control change broadcasts and reloads both.
   - Master-detail: the full select/edit/save flow, plus the dirty-switch guard.

## File Ownership

- `src/demo/com/example/ui/sales_report.cljc`, `dashboard.cljc`, `master_detail.cljc` (new), `ui/ui.cljc` (routes and nav)
- `src/demo/com/example/model/**` (sales, statistics), `model.cljc`, `components/parser.cljc` (resolver registration), seed and `development.cljc` additions
- `package.json`, `pnpm-lock.yaml` (via pnpm only). If any `deps.edn` `:deps` change, regenerate with `clj -Spom`.
- New `src/demo/com/example/headless_*_tests.clj` for these screens
- `src/demo/com/example/ui/CLAUDE.md` (your own section only)

Must not edit `system.cljc`, `ui/route_denied.cljc` or `src/main`. If a library change is needed, report it to the conductor.

## Affected Modules

- New demo screens, model and resolvers, routes, npm deps

## Approach

- Port from the originals plus f-w-s statistics. Replace UISM hooks with statechart equivalents: rp-10 hook signatures, `sro/*`.
- Keep chart data computation in pure fns, and test those fns directly as well.

## Open Questions

- None.

## Verification

1. [ ] New tests (req 5) fail before (the screens don't exist) and pass after.
2. [ ] Tests run with kaocha in the implementer's own REPL. No CLI test runs.
3. [ ] Full CLJ suite green: `:unit` + `:demo`.
4. [ ] CLJS `main` compiles with 0 warnings. CLJ loads every new ns (stubs work).
5. [ ] Chrome smoke of each screen. Plugin-debt glitches are listed.
6. [ ] `cljfmt check <changed files>` passes from the repo root (root `CLAUDE.md` "Build Hygiene").
7. [ ] Demo `CLAUDE.md` updated. REPL and server stopped. Committed and pushed.
