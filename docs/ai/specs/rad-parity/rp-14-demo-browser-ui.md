# Spec: Demo Browser UI (Semantic UI Statechart Plugin)

**Status**: backlog
**Priority**: P1
**Created**: 2026-10-31
**Owner**: AI
**Depends-on**: rp-12a, rp-06
**Skills**: `clojure`, `clojure-repl`, `fulcro`, `fulcro-rad`, `fulcro-headless`, `clj-stubs`, `claude-in-chrome`

## Context

The demo builds and is served by a hiccup page (`src/demo/com/example/components/ring_middleware.cljc`). Its only renderer, though, is the headless plain-HTML plugin (`src/demo/com/example/system.cljc` requires `com.fulcrologic.rad.rendering.headless.plugin`), with no CSS. So it isn't a usable browser demo. Sources: gap report §5 "Runnability"; `review/review-demo.md` P0-2 and "Runnability".

rp-12a put the statechart SUI plugin in `../fulcro-rad-semantic-ui` (`main`, local commits) under `com.fulcrologic.rad.statechart.rendering.semantic-ui.*`, sharing the legacy option namespaces. Its base deps are fulcro-rad 1.6.24. Its dependency on this lib is only an alias, so a `:local/root` consumer doesn't pull a second copy of this lib.

Also from the review, for the client and system setup:
- the original client's `install-formatter! :affirmation` formatter and batched reads were dropped;
- the logging config isn't applied.

This spec also takes over demo-shell file ownership for the specs that follow:
- rp-09 needs the route-denied modal in its own file;
- rp-11 edits `system.cljc`;
- rp-15, rp-16 and rp-17 edit `ui/ui.cljc`.

## Requirements

1. **Dependency.** Add `com.fulcrologic/fulcro-rad-semantic-ui {:local/root "../fulcro-rad-semantic-ui"}` to the `:demo` alias.
   - This repo's `src/main` must remain the only copy of fulcro-rad-statecharts on the classpath. Check with `clojure -Spath -A:dev:demo`.
   - It's an alias change only, so `pom.xml` is unaffected. If `:deps` ever changes, regenerate with `clj -Spom` (root `CLAUDE.md` "Build Hygiene").
2. **Plugin per platform.** In CLJS the client installs the statechart SUI plugin. In CLJ, headless tests keep the headless plugin. Choose with reader conditionals in the client/system bootstrap.
   - The two plugins are never loaded in the same runtime.
   - Restore the `:affirmation` formatter and batched reads from the original client (`../fulcro-rad-demo`).
3. **Page and CSS.** The ring page loads Semantic UI CSS (pinned CDN version) and any CSS the plugin needs. npm deps the plugin needs (e.g. `semantic-ui-react`) go in `package.json` via **pnpm** only, with the lockfile updated.
4. **Navigation like the original demo**, limited to routes that exist: a SUI menu with Account / Inventory / Invoices / Reports entries, a busy indicator, and "New …" actions.
   - Move the route-denied modal out of `Root` into `src/demo/com/example/ui/route_denied.cljc`. Root calls it. rp-09 owns that file afterward.
5. **Browser check in Chrome** (`claude-in-chrome` skill). Start the server with `(go)` from a REPL, build `main`, then walk through every existing screen:
   - Inventory, Invoices, Acct 101 Invoices;
   - New Account, New Item, New Invoice;
   - edit an account from a link, and save.
   - There must be no console errors. Capture screenshots or a short GIF as evidence, linked in the commit message or this spec's notes.
6. **Headless test.** A CLJ test asserts the ring page HTML includes the CSS link, the `app` mount node and `main.js`. It fails before (no CSS).
7. **Logging.** Apply the demo logging config the original demo used (timbre min-level, silencing noisy namespaces).
8. **Housekeeping** (review-demo P2-14):
   - Merge the duplicate `config/` and `resources/config/` into the one the demo actually reads.
   - Fix or delete `Procfile.dev`, which points at make targets that don't exist.
   - Fix the `package.json` `name`/`description`, which were copied from fulcro-rad.

## File Ownership

- `deps.edn`: `:demo` alias only
- `package.json`, `pnpm-lock.yaml` (via pnpm only), `Procfile.dev`, `config/` and `resources/config/` (dedupe)
- `shadow-cljs.edn`
- `src/demo/com/example/client.cljc`, `system.cljc`, `ui/ui.cljc`, `ui/route_denied.cljc` (new; handed to rp-09), `components/ring_middleware.cljc`, `components/config.cljc`
- `src/demo/com/example/headless_page_tests.clj` (new)
- `src/demo/com/example/ui/CLAUDE.md` and `components/CLAUDE.md`: shell and plugin notes only

Read-only: `../fulcro-rad-semantic-ui` (rp-12a/rp-12b). If the plugin needs a fix, report it to the conductor.

## Affected Modules

- Demo client and system bootstrap, Root/nav, ring page, `:demo` alias, npm deps

## Approach

- Use `../fulcro-with-statecharts/src/main/com/example/client.cljs` and `ui.cljc` (read-only) as the donor for the SUI menu and plugin installation.
- The plugin will still be a bridge until rp-12b. Visual glitches caused by plugin debt are recorded, not fixed here.

## Open Questions

- Should the demo get a production `-main` and `prod.edn` (both dropped in the port)? Recommendation: no. It isn't needed for parity verification. Leave it to a later spec if wanted.

## Verification

1. [ ] The req 6 test fails before and passes after. The headless suites still use the headless plugin and stay green.
2. [ ] Classpath check: one fulcro-rad-statecharts (this repo's `src/main`).
3. [ ] Tests run with kaocha in the implementer's own REPL. No CLI test runs.
4. [ ] Full CLJ suite green: `:unit` + `:demo`.
5. [ ] CLJS `main` and `ci-tests` compile with 0 warnings.
6. [ ] Chrome walkthrough done with evidence and no console errors. Known plugin-debt glitches are listed.
7. [ ] `cljfmt check <changed files>` passes from the repo root (root `CLAUDE.md` "Build Hygiene").
8. [ ] Demo `CLAUDE.md` files updated. Server, shadow and REPL stopped. Committed and pushed.
