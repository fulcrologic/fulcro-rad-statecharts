# Spec: Route Denial via the Routing Parallel Region

**Status**: backlog
**Priority**: P1
**Created**: 2026-10-31
**Owner**: AI
**Depends-on**: rp-07, rp-06, rp-08, rp-14
**Skills**: `clojure`, `clojure-repl`, `fulcro-spec-tdd`, `statechart`, `fulcro`, `fulcro-rad`, `fulcro-headless`

## Context

**Human decision 2:** `fo/confirm` and `fo/silent-abandon?` are dropped. Route denial and confirmation belong in the routing parallel region (scr `:routing-info` pattern). The old keys warn at compile time. Plugins get thin replacement fns for `continue-abandoned-route!` and `clear-route-denied!`.

Current state (gap report §3 Forms; `review/review-forms.md` #8; `review/review-routing.md` P1-4):

**Dead paths in `statechart/form.cljc`.** Nothing sends `:event/route-denied`, because the routing chart raises `:event.routing-info/show` instead. So these are dead:
- `route-denied-expr`, `continue-abandoned-route-expr` and `clear-route-denied-expr`;
- their three `handle`s in `form-chart` `:state/editing` and in `editing-cancel-transitions`;
- the `:route-denied?`/`:confirmation-message` aliases in `start-form!` and in the `form-route-state` invoke params;
- `fo/confirm` handling.

**What scr already provides** (`../statecharts` `integration/fulcro/routing.cljc`):
- `routing-regions` wraps routes in a parallel state with `routing-info-state` (`:routing-info/idle` / `:routing-info/open`).
- `route-denied?`, `force-continue-routing!` and `abandon-route-change!` drive it.

**The demo** renders its own modal inline in `Root` (`src/demo/com/example/ui/ui.cljc`). rp-14 moves it into `src/demo/com/example/ui/route_denied.cljc`, which this spec then owns.

**Plugins.** Upstream plugins call `form/clear-route-denied!` and `form/continue-abandoned-route!`, with arities `[this-form]` and `[app-ish form-ident]` (`../fulcro-rad` `form.cljc:2194-2204`). The legacy SUI form renders its modal inside the form, keyed on `:ui/route-denied?`.

**No default fallback.** Upstream's default was `js/confirm`. Today navigation is silently blocked unless the app renders `scr/route-denied?` UI.

## Requirements

1. **Remove the dead chart paths.**
   - Delete `route-denied-expr`, `continue-abandoned-route-expr`, `clear-route-denied-expr`, their transitions in `form-chart` and `editing-cancel-transitions`, and the `:route-denied?`/`:confirmation-message` aliases.
   - Keep `:server-errors`.
   - Tests that referenced the removed fns are updated or deleted with a note.
2. **Compile-time warnings.** `defsc-form` emits a compile-time **warning**, not an error, when `fo/confirm` or `fo/silent-abandon?` is present. The warning says the key is ignored and points to the route-denial pattern. Test in `options_validation_spec.cljc`.
3. **Replacement fns** in `statechart/form.cljc`:
   - `continue-abandoned-route!` delegates to `scr/force-continue-routing!`.
   - `clear-route-denied!` delegates to `scr/abandon-route-change!`.
   - Both keep upstream's arities (`[this-form]`, `[app-ish form-ident]`; the ident is ignored).
   - Docstrings explain the delegation.
   - Add `form/route-denied?` delegating to `scr/route-denied?`.
4. **Reusable library pattern** in a new ns, `com.fulcrologic.rad.statechart.route-denial`:
   - the query and action fns (`route-denied?`, `continue!`, `cancel!`);
   - a lib-owned render multimethod, e.g. `render-route-denied [app-ish style]`, with a `:default` that renders nothing, so plugins provide the look;
   - a docstring with the full pattern: the routing chart uses `scr/routing-regions`, and the app root renders the denial UI **once** through this ns.
   - **Rule:** plugins never render denial UI inside forms (no double modals).
   - Everything is CLJC and headless-testable.
5. **Headless implementation.** The headless plugin implements the render multimethod (message, Cancel, Continue buttons with `data-rad-*` attributes).
6. **Demo.** `src/demo/com/example/ui/route_denied.cljc` uses the library pattern. Don't edit `ui/ui.cljc`: the routing chart already uses `scr/routing-regions`, and rp-14 already made Root call `route_denied`.
   - New headless demo test ns: dirty a form, attempt a route, assert `:routing-info/open` and the rendered denial UI.
   - Cancel stays on the form with edits intact.
   - Continue completes the route, and the entity is pristine (rp-07 req 4).
7. **Routing chart check.** When the routing chart passed to `rad-app/start-routing!` has no routing-info region (it wasn't built with `scr/routing-regions`), log a one-time warning saying route denial can't be shown.

## File Ownership

- `src/main/com/fulcrologic/rad/statechart/form.cljc`
- `src/main/com/fulcrologic/rad/statechart/route_denial.cljc` (new)
- `src/main/com/fulcrologic/rad/statechart/application.cljc`: only the req 7 warning in `start-routing!`
- `src/main/com/fulcrologic/rad/rendering/headless/**`: only the denial renderer and its registration in `plugin.cljc`
- `src/test/com/fulcrologic/rad/statechart/` form tests, `options_validation_spec.cljc`, a new `route_denial_spec.cljc`, and a new headless rendering test
- `src/demo/com/example/ui/route_denied.cljc`, plus a new `src/demo/com/example/headless_route_denial_tests.clj`
- `src/main/com/fulcrologic/rad/statechart/CLAUDE.md`: replace the "Route Denial (decision …)" section with the implemented pattern

Must not edit `src/demo/com/example/ui/ui.cljc`. rp-15/16/17 own it after rp-14.

## Affected Modules

- `statechart/form.cljc` (removal, warnings, replacement fns), new `statechart/route_denial.cljc`, `statechart/application.cljc` (warning), headless plugin, demo `ui/route_denied.cljc`

## Approach

- Use the scr fns directly; don't wrap the routing chart. `scr/routing-regions` already is the parallel region.
- Use `log/warn` at macro-expansion time for the compile-time warning, the same mechanism as the existing `:will-enter` warning, not `ex-info`.

## Open Questions

- **Default when an app renders no denial UI.** Upstream fell back to `js/confirm`. Recommendation: no blocking default. Blocking dialogs aren't headless-testable, and they bypass plugin styling. Rely on the req 7 warning plus documentation. Tradeoff: an app that forgets the root renderer gets silently blocked navigation (logged at warn) instead of a browser confirm. Human to confirm.

## Verification

1. [ ] New tests fail before and pass after:
   - the dead fns are gone and nothing references them;
   - `fo/confirm` and `fo/silent-abandon?` warn at compile time;
   - the replacement fns, at both arities, drive `:routing-info` correctly;
   - the headless denial renderer works;
   - the demo denial flow passes (Cancel and Continue, pristine after Continue);
   - a routing chart without regions warns.
2. [ ] Tests run with kaocha in the implementer's own REPL. No CLI test runs.
3. [ ] Full CLJ suite green: `:unit` + `:demo`.
4. [ ] CLJS `ci-tests` and `main` compile with 0 warnings.
5. [ ] The pattern is documented in the `route-denial` ns docstring and `statechart/CLAUDE.md`. REPL killed. Committed and pushed.
6. [ ] `cljfmt check <changed files>` passes from the repo root (fulcro `.cljfmt.edn`; see root `CLAUDE.md` "Build Hygiene").
