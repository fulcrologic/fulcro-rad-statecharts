# Spec: Routed-Form Options, Chart Registration, and Form Route Hygiene

**Status**: backlog
**Priority**: P0
**Created**: 2026-10-31
**Owner**: AI
**Depends-on**: rp-02
**Skills**: `clojure`, `clojure-repl`, `fulcro-spec-tdd`, `statechart`, `fulcro`, `fulcro-rad`

## Context

Routing a form drops its start options, custom charts collide, and URL behavior regressed. Sources: gap report §1.1, §1.4, §3 Routing; `review/review-routing.md` P0-1, P1-5/6/9, P2-12/13; `review/review-forms.md` #6, #10, #18, #21, #25.

**`edit!`/`create!` ignore `params`.**
- `(scr/route-to! app-ish Form {:id id})` in `statechart/form.cljc` (`edit!`, about line 1907) sends only the id.
- `form-route-state` (about line 1877) passes `scr/istate` `:invoke-params` containing only aliases.
- The form chart's `data-model` seeds `{:options {}}` (`form-chart`, about line 1250), and `store-options` does `(or (:options data) event-data {})`. So options are always `{}`.
- Result: `:on-saved`, `:on-cancel`, `:on-save-failed`, `:embedded?` and `:initial-state` work only through a direct `start-form!`.

**Chart clobbering in `start-form!`** (about line 361).
- A map chart is registered under the shared `:com.fulcrologic.rad.form/form-chart`, which replaces the chart for every running form session. The runtime looks charts up by id on every event.
- A keyword chart gets the default `form-chart` registered over the user's id.
- `scr/istate` registers co-located map charts under the **component registry key** (statecharts `routing.cljc` `istate` `:srcexpr`). That is the convention to match.

**`form-route-state` + keyword `sfo/statechart` throws at chart-build time.** The macro sets only `sfro/statechart-id`, and `istate` throws when it can't derive `:route/reachable` without `sfro/statechart`.

**The `form-route-state` exit-fn cleans up the wrong things.**
- It calls `rc/class->registry-key` on the target, which is nil for keyword/symbol targets.
- istate's own on-exit has already deleted `[:route/idents target]`.
- It deletes `[:route/idents form-ident]`, which is the wrong key.
- So `::form-session-ids` leaks.

**After a create is saved, the URL is pushed, not replaced.**
- `rewrite-created-form-route*` (about line 737) rewrites the root routing params with `apply-action`. The save then takes url-sync's push branch.
- Back lands on the tempid URL, which opens a blank create form.
- `created-form-route-update` probably never fires, because Fulcro's tempid remap rewrites `:original-ident` before `:event/saved` (Suspected).
- Upstream used `history/replace-route!`.

**`fo/route-prefix` is ignored**, so URLs are `/AccountForm?_p=…`. `app-initialization.md:165` required mapping it to `:route/segment`.

## Requirements

1. **Start options reach routed forms.**
   - `edit!`/`create!` `params` reach the form chart's `:options` when the form is entered through `form-route-state`. That covers `:on-saved`, `:on-cancel`, `:on-save-failed`, `:embedded?`, `:initial-state` and arbitrary keys for custom charts.
   - Keys in `params` that are members of the target's `:route/params` set are route params (URL), as upstream's `extra-params` were.
   - Every other key is a start option. Start options never appear in `:routing/parameters`, the URL, or url-sync state.
   - Start options apply to that route entry only. A URL restore or bookmark starts with no options.
2. **Data-model seeding.** The chart's `data-model` no longer shadows invoke-supplied or `start!`-supplied `:options`. Direct `start-form!` behavior is unchanged.
3. **Chart registration in `start-form!`.**
   - A map `sfro/statechart` is registered under the form's registry key, the same id `scr/istate` uses. The default `form-chart` follows this rule too, since `convert-options` stores it as `sfro/statechart`.
   - A keyword `sfro/statechart-id` is used as-is and never re-registered. If it isn't registered, throw `ex-info` naming the keyword and the form.
   - Test: two forms with different inline charts run at the same time, and each processes events with its own chart.
4. **Keyword chart + `form-route-state`.** When the target has `sfro/statechart-id` and no `sfro/statechart`, and the caller gave no `:route/reachable`, `form-route-state` passes `:route/reachable #{}`. Chart construction doesn't throw, and routing to that form works.
5. **Exit cleanup.** On route exit, the `::form-session-ids` entry for the exited form is removed for class, keyword and symbol targets, and nothing else is deleted. Read the ident before istate's on-exit deletes it, or derive it from the child session.
6. **Create then save replaces the URL.**
   - After the first successful save of a created form, the URL is updated with `scr/replace-route-params!`. There is no new history entry.
   - Back from the edit URL goes to the page before the create, not to the tempid URL.
   - Fix or remove `created-form-route-update` according to what actually fires.
   - Test with `test-helpers/create-test-app-with-url-sync` and `simulated-history` (`sim/history-stack`, `sim/history-cursor`).
   - If the only remaining failure is statecharts url-sync not updating `prev-url` on `replace-route-params!` (review-routing P2-12), don't fix statecharts here. Keep the lib-side assertions. Write the repro and expected behavior into rp-19's "Findings" section, and leave the residual assertion out with a comment pointing to rp-19.
7. **`fo/route-prefix` → `:route/segment`.** `form-route-state` defaults `:route/segment` to the form's `fo/route-prefix`, and an explicit `:route/segment` wins. A URL test asserts the segment.

## File Ownership

- `src/main/com/fulcrologic/rad/statechart/form.cljc`
- Form test namespaces under `src/test/com/fulcrologic/rad/statechart/`, plus a new `form_routing_spec.cljc`
- `src/main/com/fulcrologic/rad/statechart/CLAUDE.md` (your own sections only)
- `docs/ai/specs/rad-parity/rp-19-statecharts-followups.md`: append to "Findings" only

Read-only: `src/test/com/fulcrologic/rad/test_helpers.cljc` (owned by rp-01). Put new helpers in your own test ns.

## Affected Modules

- `statechart/form.cljc`: `edit!`, `create!`, `form-route-state`, `start-form!`, `form-chart` data-model, `store-options`, `rewrite-created-form-route*`/`created-form-route-update`/`on-saved-expr` URL handling

## Approach

**Options.**
- istate `:invoke-params` is merged into the invoke `:params`, and fn values are evaluated as expressions `(fn [env data & _])`, the same way istate's own `:fulcro/actors` is.
- `edit!` writes the non-route options to a pending slot in Fulcro state keyed by the form's registry key, e.g. `[::pending-start-options rk]`, and then calls `scr/route-to!` with the route params.
- `form-route-state` adds `:options (fn [_ data & _] (get-in (:fulcro/state-map data) [::pending-start-options rk]))`.
- `store-options` stores the options and clears the slot.
- Verify how invoke params, `scf/start!` `:data` and the chart `data-model` combine before relying on this, and record the finding in `statechart/CLAUDE.md`.

**Prior art.** Unmerged `origin/fix/issues-12-13-url-update-side-effects` (April 2026) has a URL-update-after-create attempt. The human judged it STALE: issues #12/#13 were already fixed on main via #23, and the branch is 25 commits behind. Use it as reference only. Read it before implementing req 6. Don't merge it blindly; verify it against current code, including rp-02's changes.

**Registration.** Use `(rc/class->registry-key FormClass)` as the id for map charts.

**URL.** Replace `apply-action` on the root routing params with `scr/replace-route-params!` for the route target, using the persisted id. Root-session vs. nested-module correctness is rp-11; keep the target lookup in one helper so rp-11 can change it in one place.

## Open Questions

- None.

## Verification

1. [ ] New tests fail before and pass after:
   - routed `create!` with `:initial-state` and `:on-saved` (both take effect);
   - `edit!` with `:embedded? true` (no route change on cancel);
   - options are absent from the URL and `:routing/parameters`;
   - two concurrent inline charts;
   - keyword chart routed;
   - `::form-session-ids` cleaned for class, keyword and symbol targets;
   - create → save → Back history behavior;
   - route-prefix segment.
2. [ ] Tests run with kaocha in the implementer's own REPL. No CLI test runs.
3. [ ] Full CLJ suite green: `:unit` + `:demo`.
4. [ ] CLJS `ci-tests` compiles with 0 warnings.
5. [ ] Any statecharts-side residue is written into rp-19 Findings. `statechart/CLAUDE.md` updated. REPL killed. Committed and pushed.
6. [ ] `cljfmt check <changed files>` passes from the repo root (fulcro `.cljfmt.edn`; see root `CLAUDE.md` "Build Hygiene").
