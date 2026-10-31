# Spec: Semantic UI Statechart Plugin — Copy

**Status**: done (the follow-up below is active under a separate agent)
**Priority**: P1
**Created**: 2026-10-31
**Completed**: 2026-10-31 (`../fulcro-rad-semantic-ui` commit `40cfdfe` on `main`, local, not pushed)
**Owner**: AI
**Depends-on**: none
**Skills**: `clojure`, `clojure-repl`, `fulcro-rad`

## Context

No browser rendering plugin works with this engine (gap report §4):
- `fulcro-rad-semantic-ui` `main` is UISM-only.
- Its `statecharts` branch targets an abandoned RAD 2.0 design.
- fulcro-with-statecharts has an unpublished SUI clone, built against lib 0.1.3.

**Human decision 5 (as amended 2026-10-31):**
- The statechart SUI plugin lives in `../fulcro-rad-semantic-ui` on its `main` branch (no branch).
- Its **rendering code** goes under new namespaces `com.fulcrologic.rad.statechart.rendering.semantic-ui.*`. The structure is parallel to legacy, and duplicated code is fine.
- The **option namespaces are shared with legacy**: `com.fulcrologic.rad.semantic-ui-options` and `com.fulcrologic.rad.rendering.semantic-ui.form-options`. There is no `com.fulcrologic.rad.statechart.semantic-ui-options`. Option keys are identical across engines.
- Legacy `com.fulcrologic.rad.rendering.semantic-ui.*` **source** must not change at all.
- The SUI base deps move to fulcro-rad 1.6.24 (which brings fulcro 3.9.5). This was approved: it fixes legacy `controls.action-button`/`semantic-ui-controls` failing to load in CLJ (`algorithms.lambda` first shipped in fulcro 3.9.0).
- The donor is `../fulcro-with-statecharts/src/main/com/fulcrologic/rad/rendering/semantic_ui/` (commit `392956c`). That repo is an app, not a dependency.

The canonical record of these decisions is the root `CLAUDE.md` of both repos.

## Requirements

1. Copy the donor clone into `../fulcro-rad-semantic-ui/src/main/com/fulcrologic/rad/statechart/rendering/semantic_ui/**`. Rename every ns to `com.fulcrologic.rad.statechart.rendering.semantic-ui.*`, along with all internal requires, `:require-macros` and fully qualified symbol references.
2. **User-facing option keyword values are identical to legacy**, so option maps are portable.
   - The new rendering code requires the legacy option namespaces (`com.fulcrologic.rad.semantic-ui-options`, `com.fulcrologic.rad.rendering.semantic-ui.form-options`) directly.
   - No `::kw` on a user-facing option in new code.
   - Internal keywords (idents, registry keys) follow the new namespaces, so they don't collide with legacy when both are loaded.
3. **Legacy source is byte-identical.** `git diff 40cfdfe~1 -- src/main/com/fulcrologic/rad/rendering src/main/com/fulcrologic/rad/semantic_ui_options.cljc` shows no changes to legacy namespaces.
4. **Dependency.** `com.fulcrologic/fulcro-rad-statecharts` is available only through the documented `:statecharts` alias (`:local/root "../fulcro-rad-statecharts"`, with overrides). It stays **out of the pom**, so legacy users get nothing new.
   - `40cfdfe` added it to `pom.xml` as `provided`. Under the root `CLAUDE.md` "Build Hygiene" rule, that entry goes away when the pom's dependencies are regenerated with `clj -Spom`. `:scope "provided"` in `deps.edn` is noise, because `-Spom` drops it.
   - Base deps are fulcro-rad 1.6.24.
   - Pom dependencies are always regenerated with `clj -Spom`, never hand-edited.
5. Every new ns loads in CLJ under `-M:statecharts`. Legacy namespaces load in a REPL without the alias, now that the base deps are bumped.
6. A `CLAUDE.md` in the new directory records origin, keyword policy, dependency notes and the known debt for rp-12b.
7. Commit locally on `main`. **No push.**

## Follow-up (in progress, separate agent)

- Remove the duplicated `com.fulcrologic.rad.statechart.semantic-ui-options` and the statechart `form_options` copy, and point the new code at the legacy option namespaces (req 2 as amended).
- Bump base deps to fulcro-rad 1.6.24, and regenerate the pom's dependencies with `clj -Spom`. This drops the hand-added `provided` fulcro-rad-statecharts entry.
- Fix the carried-over route-denied modal bug. The modal is now driven by `scr/route-denied?`, and its buttons call `scr/abandon-route-change!`/`scr/force-continue-routing!`.
- Update both `CLAUDE.md` files to match.

## File Ownership

- `../fulcro-rad-semantic-ui/src/main/com/fulcrologic/rad/statechart/**` (new)
- `../fulcro-rad-semantic-ui/deps.edn` (`:statecharts` alias, base dep versions), `pom.xml` (via `clj -Spom` only), `CLAUDE.md`, `README.adoc` (statechart section only)

## Affected Modules

- New statechart rendering namespaces in `../fulcro-rad-semantic-ui`

## Approach

- Mechanical copy plus rename. Verify keyword values in a REPL: every legacy option var evaluates `=` to what the new code uses.

## Open Questions

- When does `../fulcro-rad-semantic-ui` `main` get pushed or released? Human call. rp-14 consumes it through `:local/root` until then.

## Verification

1. [x] Every new ns `require`s cleanly in CLJ under `-M:statecharts`. Done in `40cfdfe`; the follow-up re-checks after the option-ns removal.
2. [x] Legacy files unchanged (git diff check, req 3).
3. [ ] (Follow-up) Add a fulcro-spec load test in the SUI repo (a test alias using `:statecharts`) that requires every new ns and asserts the option keyword values used by the new code `=` the legacy vars. Run it with kaocha in a REPL. It fails if a ns doesn't load or a keyword drifts.
4. [ ] (Follow-up) Legacy namespaces load in CLJ without the alias after the base-dep bump.
5. [ ] (Follow-up) `cljfmt check <changed files>` passes on the **new** files only. Legacy files are never reformatted.
6. [ ] (Follow-up) `CLAUDE.md` files updated. Local commit. No push.
