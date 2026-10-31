# Spec: Test harness follow-ups (from the rp-01 critique)

**Status**: backlog
**Priority**: P2
**Created**: 2026-10-31
**Owner**: AI
**Depends-on**: rp-02, rp-04 (they currently edit the demo test files)

## Context

The rp-01 critique (2026-10-31) gave PASS-WITH-FIXES. Its Important items are handled elsewhere:
- **I1** (statecharts arity bug): rp-19a.
- **I2** (unit spec for `resolve-form-session-id`): added to rp-02.
- **I3** (UI-driven form E2E tests): added to rp-06.

This spec collects the Suggestions.

## Requirements

1. **Log checks.** `docs/ai/running-tests.md` says that any "no exceptions in the log" check must run with `{:capture-output? false}`, because kaocha capture hides other threads' logs on passing tests.
2. **`packaging_spec`.** Also compares deps.edn `:exclusions` and `:optional` against the pom's `<exclusions>` and `<optional>`. A mutation test proves a hand-added `<optional>` fails it.
3. **Order-hazard wording** in `running-tests.md`. State the real invariant: the headless renderer must load after upstream `rad.form`. If rp-02 has removed the hazard, delete the section instead.
4. **Exact demo assertions.** Turn these into exact values, now that the seed is fresh per fixture:
   - invoice report rows `=> 5`;
   - address subform ident and street;
   - item price `33.99M`;
   - invoice line-item count.
   Also:
   - rename the misnamed "modify item price and save" spec;
   - make the inventory "filtering" test actually filter, or rename it.
5. **`await!` messages.** A throwing `:session-id` fn must not replace the timeout exception. Omit the session clause when there's no sid.
6. **CI `:unit` classpath.** Decide whether `:unit` should run with `-M:tests:clj-tests` (without `:dev:demo`), and do it or document why not. It's currently run with `:dev:demo`, which shifts data.json, tools.reader and slf4j versions.
7. **Log noise.**
   - Set the timbre min-level to `:warn` in test setup, both CLJ and karma.
   - Investigate the `Apply action failed: mark_complete (nil nil)` and `Unable to record attribute change. Path cannot be calculated.` messages in `form-statechart-test`. Fix the test, or the bug if it's real.
8. **Leftovers.**
   - Dedupe the `:kaocha`/`:clj-tests` `:jvm-opts`.
   - Drop the unused default port 3100.
   - CI also compiles the CLJS `:main` build.
   - Set the rp-01 spec Status to done.
9. **Fixture safety.** `with-test-system` fails fast if `(mount/running-states)` is non-empty, so it can never delete a live dev DB.

## Verification

1. [ ] Each item done, or explicitly rejected with a reason in this spec.
2. [ ] `:unit` and `:demo` are green via kaocha in a REPL, uncaptured, with no unexpected ERROR lines. CLJS `ci-tests` + karma are green.
3. [ ] `cljfmt check` passes on changed files. pom via `clj -Spom` if `:deps` changed. Pushed with `git push origin rad-parity`.
