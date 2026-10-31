# Spec: Documentation and Repository Cleanup

**Status**: backlog
**Priority**: P2
**Created**: 2026-10-31
**Owner**: AI
**Depends-on**: rp-02, rp-03, rp-04, rp-05, rp-06, rp-07, rp-08, rp-09, rp-10, rp-11, rp-13
**Skills**: `clojure`, `clojure-repl`, `fulcro-spec-tdd`, `fulcro-rad`, `statechart`; `doc-writer` agent recommended for the prose

## Context

The user-facing docs teach an API that doesn't exist, and several repo files are unmodified upstream copies. Sources: gap report §2.9, §4 "Other docs"; `review/review-ecosystem.md` §5; `review/review-routing.md` P0-3, P2-16.

**README.adoc and `docs/migration-guide.adoc`:**
- They require `com.fulcrologic.rad.routing :as rroute` (`form-route-state`, `edit!`, `create!`, `route-denied?`). This lib deleted that namespace, so the name now resolves to upstream's Dynamic-Router namespace. `rroute/route-to!` silently drives DR, and the rest doesn't compile.
- They call `rad-app/install-statecharts!` with `rad-app` aliased to **upstream** `com.fulcrologic.rad.application`, which has no such fn. The fn exists in `com.fulcrologic.rad.statechart.application`; that's the alias to use.
- The migration guide's "New Namespaces" table lists 11 namespaces that don't exist.
- Its `{:route/params #{:account/id}}` example drops `:id`, which breaks form URL restore.
- It uses `fo/statechart`/`fo/on-change` (should be `sfo/*`).
- It says the upstream namespaces were "removed entirely"; they're still on the classpath.

**README.md:**
- The deps snippet is stale: fulcro 3.9.3, statecharts RC1.
- It falsely says `form.impl` "depends on UISM via rad.routing".
- It documents dead options.
- It says not to use `form.impl`/`report.impl`, which the lib delegates to.
- It says nothing about servers still needing upstream `form/pathom-plugin`/`resolvers`.

**Upstream copies:**
- `docs/DevelopersGuide.adoc` and `docs/datomic.adoc` are unmodified upstream copies.
- `CHANGELOG.md` holds the upstream 1.6.0 notes.
- `make publish` would rsync this repo's guide over the live RAD book at `RAD.html`.

**Stale files and notes:**
- `cleanup-analysis.md` (root) is a stale February design doc.
- Stale CLAUDE.md content: `src/main/com/fulcrologic/rad/CLAUDE.md` (it carries a "Partly stale" banner pointing here), `rendering/headless/CLAUDE.md`, `statechart/CLAUDE.md` decision stubs.
- **clj-kondo:** the export lints only upstream macros, and it lives at upstream's path, `src/clj-kondo/clj-kondo.exports/com.fulcrologic/fulcro-rad/config.edn`.
- `src/dev/user.clj` refreshes `src/example`, which doesn't exist.

**False "removed" claims.** blob and pathom3 are called removed or deferred, but both work (blob has no UISM; pathom3 is server-only).

**Repo housekeeping:** the duplicate config dirs, `Procfile.dev` and `package.json` name are rp-14's, since that spec owns those files.

## Requirements

1. **README.adoc, README.md and `docs/migration-guide.adoc` are rewritten against the real API after rp-02..rp-11 and rp-13:**
   - namespaces `statechart.form/report/container/control/application/session/route-denial/embedded`, plus `sfo`/`sro`/`co`;
   - routing via `scr` plus `form-route-state`/`report-route-state`/`container-route-state`;
   - `edit!`/`create!`/`view!` with the route-param/option split;
   - app setup via `com.fulcrologic.rad.statechart.application` (`install-statecharts!`, `start-routing!`, `install-url-sync!` with `:url-codec`);
   - the route-denial pattern, view mode, embedded forms and reports, container rendering, report hook signatures (from rp-10's `sro` table), and plugin-author rules (no `:default` on upstream multimethods);
   - what's safe to use from upstream, including the server side (`form/pathom-plugin`, `form/resolvers`, `save-form*`), blob and pathom3, plus the note that UISM is still on the classpath transitively;
   - a current deps snippet.
   - Remove every `com.fulcrologic.rad.routing`/`rroute` reference and every nonexistent namespace. Fix the `:route/params` example to include `:id`.
2. **Docs reference test.** Add a fulcro-spec test (CLJ) that extracts the `(:require …)`/`require` forms and `alias/sym` references from code blocks in README.adoc, README.md and `docs/migration-guide.adoc`. It asserts every namespace loads and every referenced var resolves. It fails on today's docs.
3. **Upstream copies (decision: remove, not replace).**
   - Delete `docs/DevelopersGuide.adoc` and `docs/datomic.adoc`. The README links to the upstream RAD book.
   - Replace `CHANGELOG.md` with this lib's changelog, starting with the rad-parity changes.
   - Remove the `book`/`publish` Makefile targets so nothing can overwrite `RAD.html`.
4. **Delete `cleanup-analysis.md`.** Git history keeps it.
5. **CLAUDE.md refresh** under `src/main` and the repo root: `src/main/com/fulcrologic/rad/CLAUDE.md` (remove the banner once accurate), `statechart/CLAUDE.md`, `rendering/headless/CLAUDE.md`. Demo CLAUDE.md files belong to the demo specs.
6. **clj-kondo export.**
   - Move it to `src/clj-kondo/clj-kondo.exports/com.fulcrologic/fulcro-rad-statecharts/config.edn`.
   - Cover `com.fulcrologic.rad.statechart.form/defsc-form`, `report/defsc-report`, `container/defsc-container`, and any other public macros (lint-as or hooks).
   - Verify by linting a fixture that uses all three macros with zero unresolved-symbol errors.
7. **`src/dev/user.clj`** refreshes the real source dirs (`src/main src/dev src/test src/demo`).

## File Ownership

- `README.adoc`, `README.md`, `docs/**` (except `docs/ai/specs/**`, which belongs to the conductor and specs), `CHANGELOG.md`, `cleanup-analysis.md` (delete), `Makefile` (`book`/`publish` targets)
- `src/clj-kondo/**`, `src/dev/user.clj`
- `src/main/com/fulcrologic/rad/CLAUDE.md`, `statechart/CLAUDE.md`, `rendering/headless/CLAUDE.md`, root `CLAUDE.md` (factual updates only; the decision text stays canonical)
- `src/test/com/fulcrologic/rad/docs_spec.clj` (new)

Read-only: all code under `src/main` and `src/demo`.

## Affected Modules

- Documentation, kondo export, dev tooling, repo housekeeping

## Approach

- Generate the API facts from code: `ns-publics`, docstrings, option namespaces. Use the demo as the example source, so examples are known to work.
- Write the docs reference test first (red), then rewrite until green.

## Open Questions

- None.

## Verification

1. [ ] `docs_spec` fails on the old docs and passes on the new ones.
2. [ ] clj-kondo fixture lint: 0 errors for the statechart macros.
3. [ ] `make -n publish` reports no such target.
4. [ ] Tests run with kaocha in the implementer's own REPL. No CLI test runs.
5. [ ] Full CLJ suite green: `:unit` + `:demo`.
6. [ ] `cljfmt check <changed files>` passes from the repo root (root `CLAUDE.md` "Build Hygiene").
7. [ ] REPL killed. Committed and pushed.
