# Spec: URL Sync Parity

**Status**: backlog
**Priority**: P1
**Created**: 2026-10-31
**Owner**: AI
**Depends-on**: rp-03, rp-05, rp-10, rp-09, rp-14
**Skills**: `clojure`, `clojure-repl`, `fulcro-spec-tdd`, `statechart`, `fulcro`, `fulcro-rad`, `fulcro-rad-reports`

## Context

URL persistence covers only the standard report's `:ui/parameters`, and none of it is tested. Sources: gap report §3 "Routing and URLs"; `review/review-routing.md` P1-7, P1-10, P2-11/12/14; `review/review-reports.md` P1-8.

- **`sync-url-params-expr`** in `report.cljc` runs on `:state/ready` entry. It mirrors only `:ui/parameters`: local controls, sort, page and selected row.
  - `scr/replace-route-params!` replaces the whole params map, so global (non-`:local?`) control values passed through `route-to!` drop out of the URL at the first `:state/ready`.
  - Later global-control changes never reach the URL. Upstream `control/set-parameter` updated it; there's a TODO in `statechart/control.cljc` `set-parameter`.
- **`ro/track-in-url?`** is ignored, so a report in a modal or embedded (rp-13) rewrites the URL.
- **Server-paginated and incremental reports** don't sync at all.
- **Container children** sync with their child id (the second element of the ident) as the route target, which is wrong. Containers themselves don't sync.
- **Root session only.** `rewrite-created-form-route*`, and `replace-route-params!` (which uses `scr/session-id`), write only to the **root** routing session. That's wrong when RAD routes live inside an istate child module.
- **No URL tests.** `test_helpers/create-test-app-with-url-sync` exists but is unused. Statecharts' `simulated_history` works in CLJ.
- **Demo URL sync is CLJS-only:** `#?(:cljs (rad-app/install-url-sync! app))` in `src/demo/com/example/system.cljc`.
- **`:prefix` doc.** `rad-app/install-url-sync!` documents a `:prefix` option that scr ignores. A prefix actually needs `:url-codec (ruct/transit-base64-codec "/app")`.
- **Suspected:** `replace-route-params!` doesn't update url-sync's `prev-url`, so the next unchanged-route save pushes a duplicate entry. This is a statecharts-side issue (rp-19).

## Requirements

1. **Merge, don't overwrite.** Report URL sync merges into the target's existing route params. Global control values given in `route-to!` stay in the URL after the report becomes ready.
2. **Global controls in the URL.**
   - `control/set-parameter!` (global and local) updates the URL for the controlling report or container when it is URL-tracked.
   - Bookmarks restore global control values.
   - Remove the TODO in `control.cljc`.
3. **`ro/track-in-url?`** defaults to true. When false, the report never reads or writes the URL: no initial params from URL, no sync on ready or set-parameter.
4. **Variants and containers:**
   - Server-paginated and incremental reports sync the same keys as standard ones (plus page, for server-paginated).
   - A container syncs its own controls under its own route target.
   - Container children never write the URL under a wrong target. The container owns the URL; children are treated as `track-in-url? false` unless the container delegates to them explicitly.
5. **Module-correct writes.** RAD URL and route-param writes (report sync, created-form URL replace from rp-03, set-parameter) target the routing session that actually contains the route: the root session or the istate child module session.
   - Put the lookup in one helper and use it everywhere.
   - Test with a routing chart whose RAD routes live in a child module.
6. **Tests.** Add URL tests in CLJ using `create-test-app-with-url-sync` and `simulated-history`:
   - form edit/create/save/Back;
   - report sort, page, select, global and local controls;
   - bookmark restore of each;
   - `track-in-url? false`;
   - container controls;
   - server-paginated page.
   - Confirm or refute the `prev-url` duplicate-push suspicion with a test. If it's confirmed, write the repro into rp-19 "Findings" and keep the lib-side assertions.
7. **Demo installs URL sync in CLJ too**, with a simulated history provider for headless runs and browser history in CLJS. Add one demo URL test ns: route, bookmark and restore through the real server.
8. **`install-url-sync!` doc.** The docstring documents `:url-codec` (with the `ruct/transit-base64-codec` prefix example) and drops `:prefix`. If a `:prefix` convenience is kept, implement it by constructing the codec.

## File Ownership

- `src/main/com/fulcrologic/rad/statechart/report.cljc`, `server_paginated_report.cljc`, `incrementally_loaded_report.cljc`, `control.cljc`
- `src/main/com/fulcrologic/rad/statechart/form.cljc`: routing helpers only (`rewrite-created-form-route*`, the URL-replace helper from rp-03)
- `src/main/com/fulcrologic/rad/statechart/container.cljc`: URL sync only
- `src/main/com/fulcrologic/rad/statechart/application.cljc`
- New URL test namespaces under `src/test/com/fulcrologic/rad/statechart/`. New helpers go in your own test ns; `test_helpers.cljc` is read-only.
- `src/demo/com/example/system.cljc`: the URL-sync install only
- `src/demo/com/example/headless_url_tests.clj` (new)
- `docs/ai/specs/rad-parity/rp-19-statecharts-followups.md`: append to "Findings" only
- `src/main/com/fulcrologic/rad/statechart/CLAUDE.md` (your own sections only)

## Affected Modules

- Report URL sync, `control/set-parameter`, form create-URL helper, container sync, `application.cljc`, demo system bootstrap

## Approach

- Write the URL-target helper first: `(routing-session-for app target)` → session id, plus a params path. Then route every write through it.
- Use `(update params merge new)` semantics on the target's `:routing/parameters`. Then call `scr/replace-route-params!` with the merged map. It replaces by design.

## Open Questions

- None.

## Verification

1. [ ] New URL tests (req 6) fail before and pass after. Demo URL test passes.
2. [ ] Tests run with kaocha in the implementer's own REPL. No CLI test runs.
3. [ ] Full CLJ suite green: `:unit` + `:demo`.
4. [ ] CLJS `ci-tests` and `main` compile with 0 warnings.
5. [ ] `prev-url` suspicion resolved (confirmed → rp-19 Findings; refuted → noted in `statechart/CLAUDE.md`).
6. [ ] `statechart/CLAUDE.md` URL section written. REPL killed. Committed and pushed.
7. [ ] `cljfmt check <changed files>` passes from the repo root (fulcro `.cljfmt.edn`; see root `CLAUDE.md` "Build Hygiene").
