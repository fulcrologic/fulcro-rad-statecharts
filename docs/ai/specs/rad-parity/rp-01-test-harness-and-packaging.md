# Spec: Test Harness and Packaging

**Status**: backlog
**Priority**: P0
**Created**: 2026-10-31
**Owner**: AI
**Depends-on**: none
**Skills**: `clojure`, `clojure-repl`, `fulcro-spec-tdd`, `fulcro-headless`, `deps-upgrade` (for the version evaluation)

## Context

Every later rad-parity spec verifies itself with the `:unit` and `:demo` kaocha suites. Neither works as a regression net today (review: `review/review-tests.md`, `review/review-demo-failures.md`, gap report §0 and §2.10):

- **Demo E2E: 21 tests, 15 failures, all on the test side.**
  - `src/demo/com/example/headless_form_tests.clj` `form-config` calls `sc.session/ident->session-id`. Since `e364841`, `form-route-state` is an `scr/istate`, so routed forms run under generated string session ids. The helper reads, and sends events to, a session that doesn't exist.
  - `wait-for-form!` (and the report equivalent) renders 30 frames but waits on nothing. Cold-JVM loads lose the race.
  - `headless_report_tests.clj` routes to `AccountInvoices` with `{}`, which has no `:account/id`.
- **Demo tests never run automatically.** `tests.edn` has only `:unit` (`src/test`, `-test$`/`-spec$`), and CI (`.github/workflows/test.yml`) runs `-A:dev:tests:clj-tests` without `:demo`.
- **No REPL-friendly kaocha alias.** `:clj-tests` has `-m kaocha.runner` main-opts. The reviewer had to inject kaocha with `-Sdeps`. No `running-tests.md` exists, though the user's global rules look for one.
- **Racy unit test apps.** `form_statechart_test/test-app`, `report_statechart_test/test-app` and `test_helpers/create-test-app` build `(rad-app/fulcro-rad-app {})` with no remote. Fulcro's fallback remote has a 1-arg `:transmit!`, so each load throws `ArityException` on the tx thread. The resulting `:event/failed` races the test thread. `e3d4e90` fixed the container test the same way this spec fixes the others.
- **`pom.xml` (what `make deploy` publishes) doesn't match `deps.edn`:**
  - pom has fulcro 3.9.3, which lacks namespaces the lib requires (`algorithms.macro-support`, `algorithms.core`).
  - statecharts 1.4.0-RC13, fulcro-rad 1.6.22, guardrails 1.2.16.
  - No promesa 11 pin, and a stray `com.taoensso/encore` 3.159.0.
- **`src/main/deps.cljs` is a leftover from the fork.** It pins `@js-joda/core` 3.2.0, `@js-joda/timezone` 2.8.0 and `big.js` 6.1.1, while `package.json` has 5.7.0 / 2.23.0 / 7.0.1. Nothing in `src/main` requires an npm package directly; upstream fulcro-rad's own `deps.cljs` declares these for the code that uses them.
- **Versions.** fulcro is pinned at 3.10.0-RC2, and 3.10.0/3.10.1 final exist. The demo uses fulcro-rad-datomic 1.5.6, and 1.5.7 exists.

## Requirements

1. **Real form session ids in demo tests.** Make `resolve-form-session-id` in `statechart/form.cljc` public, with a docstring. Keep the name, or rename it to `form-session-id` and leave a private alias. Demo test helpers use it, or `[::form/form-session-ids ident]`. No demo test uses `ident->session-id` for a routed form. Rewrite the stale `headless_form_tests.clj` ns docstring (it describes `rstate` + `start-form!`).
2. **Poll, don't count frames.** Replace fixed-frame waits with an `await!` helper. It calls `h/render-frame!` until a predicate holds or a timeout passes (default 10 s, configurable). On timeout it fails with the session id, the current configuration and the predicate description.
   - Form waits target a given state (default `:state/editing`).
   - Report waits target `:state/ready`, plus a row-count predicate when the test expects rows.
3. **`AccountInvoices`** routes with `{:account/id (new-uuid 101)}` and asserts the exact number of seeded invoices for that account.
4. **`:demo` kaocha suite** in `tests.edn`: `:test-paths ["src/demo"]`, `:ns-patterns ["headless-.*-tests$"]`, `:source-paths ["src/main" "src/demo"]`. `:unit` is unchanged.
5. **REPL tooling:**
   - Add a kaocha alias without main-opts, e.g. `:kaocha {:extra-deps {lambdaisland/kaocha ...}}`, so a REPL can start with `-M:dev:tests:demo:kaocha` plus nREPL.
   - Keep `:clj-tests` for CI.
   - Write `docs/ai/running-tests.md` covering:
     - the REPL start command;
     - `(require 'kaocha.repl)`, `(kaocha.repl/run :unit)` and `(kaocha.repl/run :demo)`;
     - running one namespace or one test;
     - that demo fixtures use Datomic Local in memory and a per-namespace port, so no external services are needed;
     - "never run tests from the CLI".
6. **CI and Makefile:**
   - The clj job in `.github/workflows/test.yml` runs both suites with the `:demo` alias on the classpath.
   - The `tests` target in `Makefile` does the same.
   - CI may use the CLI runner. Agents never do.
7. **Silent remote** (`{:remotes {:remote {:transmit! (fn [_ _] nil)}}}`) for every test-app constructor under `src/test`: `form_statechart_test/test-app`, `report_statechart_test/test-app`, `test_helpers/create-test-app`, and any other `fulcro-rad-app {}`. Tests that need a load result deliver it explicitly, as `container_statechart_test` does. A `:unit` run logs no `ArityException … fulcro-app/fn` lines.
8. **`pom.xml` matches the runtime `:deps` of `deps.edn` exactly:**
   - Regenerate the pom's dependencies with `clj -Spom` from the repo root. Never hand-edit them (root `CLAUDE.md` "Build Hygiene").
   - Don't use `:scope "provided"`: `clj -Spom` silently drops it. A dependency only some users need goes in a documented `deps.edn` alias and stays out of the pom (root `CLAUDE.md` "Build Hygiene").
   - Expected result: the same artifact set and versions; promesa `11.0.678` present; encore gone (it isn't in `:deps`; first check that nothing in `src/main` requires `taoensso.encore` directly); no dev, test or demo deps.
   - Hand-check the non-dependency pom sections (scm, licenses, gpg plugin, version `0.1.6-SNAPSHOT`) after regeneration.
9. **Packaging regression test.** Add a CLJ fulcro-spec test, `src/test/com/fulcrologic/rad/packaging_spec.clj`, that parses `pom.xml` and `deps.edn` and asserts the dependency sets and versions are equal. It fails on today's files.
10. **Delete `src/main/deps.cljs`** (decision: the lib requires no npm package directly). If the implementer finds a direct npm require in `src/main`, keep the file instead, set it to the `package.json` versions, and record why in `src/main/com/fulcrologic/rad/CLAUDE.md`.
11. **Version evaluation.** Use the `deps-upgrade` skill on a clean tree:
    - Try fulcro → latest 3.10.x final in `deps.edn`, then regenerate `pom.xml` with `clj -Spom`. Try fulcro-rad-datomic → 1.5.7 in `:demo` (alias only, so no pom change).
    - Keep each bump only if `:unit` + `:demo` stay green and the CLJS `ci-tests` and `main` builds compile with 0 warnings.
    - statecharts RC18→RC19 is optional (the routing review found only a promesa shim change).
    - Record the outcome, kept or reverted and why, in this spec's Verification notes.
12. **Suite-order hazard (until rp-02 lands).** Loading the demo loads upstream `com.fulcrologic.rad.form`, which re-registers `fr/render-form :default` and can make the headless renderer render nil forms (review-demo-failures §5).
    - If `:unit` fails only when run after `:demo` in one JVM, CI runs the two suites as separate JVM invocations, and `running-tests.md` says "run `:unit` before `:demo`, or restart" until rp-02 is done.
    - Do not work around the collision in tests.

## File Ownership

Only these files may be edited:

- `tests.edn`, `deps.edn` (aliases, versions), `pom.xml`, `src/main/deps.cljs` (delete), `Makefile` (the `tests` target only), `.github/workflows/test.yml`
- `docs/ai/running-tests.md` (new)
- `src/demo/com/example/headless_form_tests.clj`, `headless_report_tests.clj`, `headless_routing_tests.clj`, `headless_client.clj`, `test_server.clj`
- `src/test/com/fulcrologic/rad/test_helpers.cljc`
- The `test-app` fns in `src/test/com/fulcrologic/rad/statechart/form_statechart_test.cljc` and `report_statechart_test.cljc`, plus any other test-app constructor under `src/test`
- `src/test/com/fulcrologic/rad/packaging_spec.clj` (new)
- `src/main/com/fulcrologic/rad/statechart/form.cljc`: only the visibility and docstring of `resolve-form-session-id`
- `src/demo/com/example/CLAUDE.md` (new; test-harness notes)

## Affected Modules

- `src/demo/com/example/headless_*_tests.clj`: session-id lookup, polling helpers, AccountInvoices param
- `tests.edn`, `deps.edn`, `.github/workflows/test.yml`, `Makefile`: `:demo` suite, kaocha REPL alias, CI
- `pom.xml`, `src/main/deps.cljs`: packaging
- `src/test/.../test_helpers.cljc` and test-app fns: silent remote

## Approach

1. Fix the session-id helper first, then re-run `:demo`. The review reproduced that edit, save and reset work when addressed to the real session. Then add polling. Then fix the AccountInvoices param.
2. Add the `:demo` suite and the kaocha alias. Write `running-tests.md` from the commands that actually worked.
3. Write `packaging_spec` (red), run `clj -Spom` (green), then delete `deps.cljs`.
4. Do the version evaluation last, one bump at a time, on a clean tree.
5. Push and confirm the GitHub Actions run on `rad-parity` is green (`gh run list --branch rad-parity`, `gh run view`).

## Open Questions

- (Resolved 2026-10-31) PR #26: FOLD IT IN. `com.wsscode/pathom3` in `:dev` is unused: nothing in src/ or the demo uses Pathom 3, and it only dragged in promesa 8. Remove it from `deps.edn`, but keep the top-level promesa 11 pin, because statecharts requires promesa 11+ for consumers. The conductor closes PR #26 when rad-parity merges.
- Is Datomic Local (`com.datomic/local` from Maven Central) acceptable in public CI? Recommendation: yes. It's in-memory, needs no credentials, and is what the fixtures already use.

## Verification

1. [x] `:demo` suite: 21/21 tests pass in the implementer's REPL. Before the fix: the 14 form failures (session id plus timing) and the AccountInvoices failure. After the fix: none.
2. [x] `packaging_spec` fails against the old `pom.xml` and passes after `clj -Spom`. No hand edits to pom dependencies.
3. [x] A `:unit` run shows no `ArityException … fulcro-app/fn` log lines.
4. [x] Tests run with kaocha in the implementer's own REPL per `docs/ai/running-tests.md`. No CLI test runs.
5. [x] Full CLJ suite green: `(kaocha.repl/run :unit)` and `(kaocha.repl/run :demo)`, 0 failures/errors. Run both orders and record the result (req 12).
6. [x] CLJS `ci-tests` and `main` builds compile with 0 warnings (compile only).
7. [x] GitHub Actions run on `rad-parity` is green with the `:demo` suite included.
8. [x] Version bumps: outcome recorded (kept/reverted, why).
9. [x] `src/demo/com/example/CLAUDE.md` records the session-id and polling helpers. REPL killed. Committed and pushed.
10. [x] `cljfmt check <changed files>` passes from the repo root (fulcro `.cljfmt.edn`; see root `CLAUDE.md` "Build Hygiene").

### Verification notes (implementer, 2026-10-31)

- **Final results (fulcro 3.10.1, statecharts RC19, demo fulcro-rad-datomic 1.5.7), in my own REPL** (`clojure -M:dev:tests:demo:kaocha:nrepl`; guardrails enabled with `guardrails-test.edn`):
  - `:unit`: 91 tests, 431 assertions, 0 failures/errors.
  - `:demo`: 21 tests, 91 assertions, 0 failures/errors.
  - 0 `ArityException`, `Send threw` and `No renderer` log lines.
- **Item 1, before/after.** With the fix in place but the helper temporarily redefined back to `ident->session-id`, all 8 form specs error. The `await!` timeout reports session `:com.fulcrologic.rad.sc/account_id--…` in `#{}`. With the fix: 0 failures.
- **AccountInvoices has exactly 1 row (Sam's invoice-3, total 50.00), not the review's 2.** The review's REPL had seeded twice. `start-databases` reuses the Datomic Local `:mem` DB, so data accumulated across fixtures (random-id invoices duplicated, saves leaked). `with-test-system` now deletes the DB before and after each run.
  - The routing tests moved onto that shared fixture. Their private fixture had anti-forgery on, so their form loads never succeeded.
  - The form save test now edits Sally (102), because saves really persist now and kaocha randomizes order.
- **Item 2.** The red run listed fulcro 3.9.3, statecharts RC13, fulcro-rad 1.6.22, guardrails 1.2.16, stray encore and no promesa. Green after `clj -Spom`. Only `<dependencies>` changed; scm, licenses, gpg plugin and `0.1.6-SNAPSHOT` are untouched.
- **Item 5, order (req 12), fresh JVMs:**
  - `:unit` → `:demo`: both green.
  - `:demo` → `:unit`: both green.
  - `:unit` → `:demo` → `:unit`: the second `:unit` fails 16 assertions, all in `headless-rendering-test`.
  - The hazard is "headless renderer loaded, then demo loads upstream `rad.form`". A kaocha run that loads both suites at once hits it too: the CLI runner with no suite argument, or a namespace focus without `{:kaocha.filter/skip [:demo]}`.
  - CI and `make tests` run `:unit` and `:demo` in separate JVMs. `running-tests.md` documents the rule.
- **Item 6.** Clean compiles (`.shadow-cljs/builds` removed) after each bump: `ci-tests` 428 files and `main` 441 files, both with 0 warnings.
  - `ci-tests` now uses fulcro's `:ns-regexp "-(test|spec)$"` (also the `:test` build), so the CLJC `*-test` namespaces run in CLJS too.
  - Karma (ChromeHeadless, `--single-run`): 74/74 SUCCESS locally and on CI. With `-spec$` only it was 52/52.
- **Item 7.** GitHub Actions on `rad-parity`: green, starting with run 41223499397.
  - clj: `:unit` 91/431, `:demo` 21/91, each in its own JVM with `-M:dev:tests:demo:clj-tests <suite>`.
  - cljs: karma 74/74.
- **Item 8, version bumps** (each kept only after a fresh-REPL `:unit` + `:demo` run, clean CLJS compiles and karma):
  - fulcro 3.10.0-RC2 → 3.10.1: KEPT. pom regenerated.
  - fulcro-rad-datomic 1.5.6 → 1.5.7 (`:demo` only): KEPT. pom unchanged.
  - statecharts RC18 → RC19: KEPT. RC19 only adds a fail-fast check for an old promesa. pom regenerated.
- **PR #26 folded in.** Removed `com.wsscode/pathom3` from `:dev`; the promesa 11 pin stays.
  - fulcro-rad-datomic's `lazy-invoke` macro `requiring-resolve`s pathom3 at compile time when it is present. When it is absent, that path compiles to a throw. It is only reached via `make-pathom3-resolver`, which the demo (Pathom 2) never calls.
  - Suites green without it.
- **Item 10.** `cljfmt check` passes on every changed `.clj`/`.cljc`/`.edn` file.

