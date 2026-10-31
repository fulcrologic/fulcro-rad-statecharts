# Spec: Container Parity (Routing, Rendering, Custom Charts)

**Status**: backlog
**Priority**: P0
**Created**: 2026-10-31
**Owner**: AI
**Depends-on**: rp-04
**Skills**: `clojure`, `clojure-repl`, `fulcro-spec-tdd`, `statechart`, `fulcro`, `fulcro-rad`, `fulcro-rad-reports`

## Context

Containers can't be routed, can't be rendered by a plugin, and ignore custom charts. Sources: gap report §1.6, §3; `review/review-reports.md` P1-10 and P2; `review/review-routing.md` P0-2, P1-4; `review/review-ecosystem.md` §3.

- **No `container-route-state`.** `defsc-container` sets `sfro/statechart` but no `sfro/actors`, while the chart resolves `:actor/container` (`container.cljc` `container-class`). An `istate`'d container gets a nil class. Today the only option is a hand-written `rstate` with an `entry-fn` that calls `start-container!`.
- **Not renderable.** `render-layout` (`container.cljc`, about line 186) logs an error and returns nil, and there is no multimethod. Upstream looked up `[:com.fulcrologic.rad/controls ::container/style->layout layout-style]` in the runtime atom. fulcro-rad-tui registers its container layout there (`../fulcro-rad-tui/.../tui/container.cljc` docstring and `tui_controls.cljc`), so that map path must keep working.
- **Custom chart ignored.** `start-container!` hard-codes `::container-chart`, even though the macro stores `sfro/statechart`/`sfro/statechart-id` from `:com.fulcrologic.rad.statechart.container/statechart`. That key has no option var.
- **`container-busy?`** returns true while any child report is loading. Because the macro sets it as `sfro/busy?`, routing away from a loading container shows the "unsaved changes" prompt. Upstream never blocked.
- **`:event/unmount`** is broadcast on exit (`unmount-children-expr`). No report chart handles it, and upstream never sent it.
- **Ordering.** `initialize-params-expr` starts the children (`report/start-report!` side effect) before its control param ops apply.
- **Options with no effect:** `co/route`, `co/title`, `co/layout`, `co/layout-style`.

## Requirements

1. **`container-route-state`.** Add it to `container.cljc`: an `scr/rstate` whose `entry-fn` calls `start-container!` with this target's route params only, using the same hygiene as rp-04 req 4.
   - Default `:route/params`: the keys of the container's `::control/controls`.
   - Default `:route/segment` from `co/route`; an explicit value wins.
   - Test routing to, away from and back to a container (resume).
2. **istate safety.** `defsc-container` generates `sfro/actors` (`:actor/container`), and the chart owns its aliases, so `scr/istate` on a container works. `container-route-state` is the documented path. Test both.
3. **Render multimethod.**
   - `render-layout` dispatches through a new lib-owned multimethod, e.g. `render-container-layout`, using `fr/render-hierarchy`. The dispatch value comes from `co/layout-style`, default `:default`.
   - Its `:default` method falls back to the upstream map registry `[:com.fulcrologic.rad/controls :com.fulcrologic.rad.container/style->layout style]`.
   - It logs an error and returns nil only when neither exists.
   - The multimethod contract (args, what `co/layout`/`co/title` mean) goes in the docstring.
   - Tests: a `defmethod` renders; a map-registered layout renders (fulcro-rad-tui compatibility); neither → nil.
4. **Custom charts.** Add `co/statechart` to `container_options.cljc`, with the keyword value `:com.fulcrologic.rad.statechart.container/statechart` the macro already reads.
   - `start-container!` uses `sfro/statechart`/`sfro/statechart-id` with rp-04's registration rule: a map goes under the registry key; a keyword is used as-is and must be registered.
   - Test: a custom container chart processes `:event/run`.
5. **Not busy while loading.** A container never blocks routing because a child is loading. Remove `sfro/busy?` from the macro output, or make it return false.
   - Keep `container-busy?` as a public query fn. Rename it to `children-loading?` and keep the old name as a deprecated alias.
   - Test: route away while a child is loading. The route succeeds, and `:routing-info/open` is never entered.
6. **Remove the `:event/unmount` broadcast.** Child report sessions persist for resume, like routed reports. Decision: remove rather than handle it, because handling would mean report-chart edits outside this spec, and upstream never had it.
7. **Ordering.** The container applies its control param ops (route params and defaults into the `::control/id` table) **before** starting children. Children are started with `::report/externally-controlled? true`, which rp-04 req 9 stores at start.
   - Test: a child's first load sees the container's default control value.
8. **Option semantics.** The `co/route`, `co/title`, `co/layout` and `co/layout-style` docstrings describe the statechart behavior (no "dynamic routing" text). The render contract exposes `co/title` and `co/layout` to renderers. The headless implementation is rp-06.
9. **Variant children.** A container with a server-paginated child shows that child's rows (relies on rp-04 req 2). Test it.

## File Ownership

- `src/main/com/fulcrologic/rad/statechart/container.cljc`, `container_options.cljc`
- `src/test/com/fulcrologic/rad/statechart/container_statechart_spec.cljc`, `container_statechart_test.cljc`, and new container test namespaces
- `src/main/com/fulcrologic/rad/statechart/CLAUDE.md` (your own sections only)

Read-only: the report namespaces (rp-04/rp-10) and `rendering/headless/**` (rp-06). If a report-side change is needed, stop and report it to the conductor.

## Affected Modules

- `container.cljc`: macro, `start-container!`, `initialize-params-expr`, chart exit, `render-layout`, `container-busy?`, new `container-route-state`

## Approach

- Mirror `report-route-state` (post-rp-04) for `container-route-state`.
- Copy the runtime-atom lookup from upstream `../fulcro-rad/src/main/com/fulcrologic/rad/container.cljc` `render-layout` into the multimethod's `:default`, with `->arity-tolerant` as upstream report layouts do.
- The lib may define `:default` on its **own** new multimethod. Upstream never defines it.

## Open Questions

- None.

## Verification

1. [ ] New tests fail before and pass after for reqs 1–7 and 9. Docstring changes (req 8) reviewed.
2. [ ] A map-registered container layout (fulcro-rad-tui style) renders through the auto-generated body.
3. [ ] Tests run with kaocha in the implementer's own REPL. No CLI test runs.
4. [ ] Full CLJ suite green: `:unit` + `:demo`.
5. [ ] CLJS `ci-tests` compiles with 0 warnings.
6. [ ] `statechart/CLAUDE.md` container section updated. Leave the stale `:event/unmount` note in `src/main/com/fulcrologic/rad/CLAUDE.md` for rp-18. REPL killed. Committed and pushed.
7. [ ] `cljfmt check <changed files>` passes from the repo root (fulcro `.cljfmt.edn`; see root `CLAUDE.md` "Build Hygiene").
