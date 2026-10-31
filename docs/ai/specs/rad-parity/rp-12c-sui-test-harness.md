# Spec: SUI plugin test harness (fulcro pattern)

**Status**: active
**Priority**: P1
**Created**: 2026-10-31
**Owner**: AI
**Depends-on**: rp-12a

## Context

`../fulcro-rad-semantic-ui` has no tests. The legacy and statechart semantic-ui namespaces have only been loaded in a JVM REPL; nothing has proven they compile as CLJS or load in a browser. The rp-12a follow-up also bumped the base deps (fulcro-rad 1.6.24, Clojure 1.12.6, CLJS 1.12.145), which changes how the unchanged legacy code builds.

The human's direction: "Use the pattern from ../fulcro for testing."

## Requirements

1. **Copy fulcro's CLJS test setup.** Use `../fulcro`'s files as the template:
   - `package.json`: pnpm `11.21.0` via `packageManager` + `devEngines`; exact versions; devDependencies `karma`, `karma-chrome-launcher`, `karma-cljs-test`, `shadow-cljs`.
   - `pnpm-workspace.yaml`: `saveExact`, `minimumReleaseAge: 10080`, `allowBuilds`, `shamefullyHoist`.
   - `karma.conf.js`: ChromeHeadless, `target/ci.js`, `shadow.test.karma.init`.
   - `shadow-cljs.edn`:
     - a `:test` `:browser-test` build;
     - a `:ci-tests` `:karma` build with `:js-provider :shadow` and `:static-fns false`, output `target/ci.js`, ns-regexp `-(test|spec)$`;
     - `:deps {:aliases [...]}` including `:statecharts`.
   - `Makefile` `tests` target, in this order:
     1. `pnpm install --frozen-lockfile`
     2. `shadow-cljs compile ci-tests`
     3. `karma start --single-run`
     4. the CLJ suite
   - Add `.gitignore` entries for `node_modules`, `target`, `.shadow-cljs` and the test output dir.
2. **npm runtime deps** needed by the plugin in a browser: `react`/`react-dom` at a version within semantic-ui-react 2.1.5's peer range (18.3.1), `semantic-ui-react` 2.1.5, and whatever npm packages fulcro-rad/fulcro need at CLJS compile time. Generate and commit `pnpm-lock.yaml`, and delete the stale `yarn.lock`.
3. **deps.edn test aliases** in the fulcro style:
   - `:test` with `src/test` and fulcro-spec.
   - `:clj-tests` with kaocha.
   - `:dev` with whatever shadow needs.
   - A `tests.edn` kaocha config.
   - Aliases don't affect the pom. If `:deps` changes, regenerate the pom with `clj -Spom`.
4. **Load specs (CLJC, so they run in both CLJ and CLJS):**
   - One spec requires every legacy namespace (`com.fulcrologic.rad.rendering.semantic-ui.*`, `com.fulcrologic.rad.semantic-ui-options`).
   - One spec requires every statechart namespace (`com.fulcrologic.rad.statechart.rendering.semantic-ui.*`).
   - Each asserts that the plugin's public install/entry vars resolve (e.g. the controls map / install fns). Each also calls the plugin's install fn and asserts the expected registrations exist (map entries or multimethod methods).
   - Both engines load in the same build, so this also proves they coexist.
5. **Legacy source stays byte-identical.**
   - If legacy code produces CLJS compiler warnings or browser load errors, REPORT them. Do not fix them.
   - Fix only the new statechart namespaces, the test/build files, and deps.

## Affected Modules
All in `../fulcro-rad-semantic-ui`:
- `package.json`, `pnpm-workspace.yaml`, `pnpm-lock.yaml`, `karma.conf.js`, `shadow-cljs.edn`, `Makefile`, `tests.edn`, `.gitignore`, `deps.edn` (aliases), `yarn.lock` (delete)
- `src/test/**`
- `CLAUDE.md` (how to run the tests)

## Approach
Copy fulcro's files and adapt them to this repo.
- **CLJ:** run the CLJ specs through kaocha in a REPL (the user's rule).
- **CLJS:** follow fulcro's pattern: shadow `compile ci-tests`, then karma `--single-run` with ChromeHeadless.

## Open Questions
- Should GitHub Actions CI be added? `../fulcro` has none; this follows fulcro and adds none.

## Verification
1. [ ] `pnpm install --frozen-lockfile` succeeds from a clean `node_modules`.
2. [ ] `shadow-cljs compile ci-tests` succeeds. List every warning, separating legacy from new namespaces; the new namespaces must have zero warnings.
3. [ ] `karma start --single-run` passes in ChromeHeadless, and its output shows both load specs ran.
4. [ ] CLJ load specs pass via kaocha in a REPL.
5. [ ] Proof that the specs fail when they should: temporarily break a require in a NEW namespace and confirm the build or karma fails, then revert.
6. [ ] `git diff c575afb -- src/main/com/fulcrologic/rad/rendering src/main/com/fulcrologic/rad/semantic_ui_options.cljc` is empty.
7. [ ] `cljfmt check` passes on new and changed `.clj*` files.
8. [ ] Committed locally on `main`, not pushed. All REPLs, shadow servers and karma processes are killed.
