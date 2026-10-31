# Test run report: fulcro-rad-statecharts (2026-10-31)

## Setup
- REPL: my own nREPL on port 7892, stopped after the runs. Command:
  `clojure -J-Djdk.attach.allowAttachSelf -J-XX:-OmitStackTraceInFastThrow -Sdeps '{:deps {nrepl/nrepl {:mvn/version "1.5.1"} lambdaisland/kaocha {:mvn/version "1.91.1392"}}}' -M:dev:tests:demo -m nrepl.cmdline --port 7892`
- Deviation from CI: the `:clj-tests` alias was left out because its main-opts (`-m kaocha.runner`) conflicted with starting nREPL. Kaocha was added via `-Sdeps` at the same version (1.91.1392). `:demo` was on the classpath for every run, so the unit suite also ran with the demo paths. CI does not do this.
- Local toolchain vs CI: Java 25 (CI: 21), Node 23.11.1 (CI: 24), Clojure CLI 1.12.6.1673 (same as CI).
- Single run per suite. No reruns.

## 1. Library unit suite (PASS)
- Command: `(kaocha.repl/run :unit)` (tests.edn, src/test, ns-patterns `-test$` / `-spec$`, skip-meta `:integration`)
- Result: 90 tests, 427 assertions, 0 failures, 0 errors, 0 pending.
- Output also contains ERROR-level log lines from `com.fulcrologic.fulcro.algorithms.tx-processing:152` ("Send threw an exception for tx"), caused by `clojure.lang.ArityException: Wrong number of args (2) passed to: com.fulcrologic.fulcro.raw.application/fulcro-app/fn--35330`. Kaocha reported no failures for these. Not investigated.

## 2. Demo headless E2E suites in src/demo (FAIL: 15 failures, 0 errors)
- Command:
  `(kaocha.repl/run 'com.example.headless-form-tests 'com.example.headless-report-tests 'com.example.headless-routing-tests {:kaocha/tests [{:kaocha.testable/id :demo :kaocha.testable/type :kaocha.type/clojure.test :kaocha/test-paths ["src/demo"] :kaocha/source-paths ["src/main"] :kaocha/ns-patterns ["headless-.*-tests$"]}]})`
- Why an override was needed: the `:unit` suite's patterns (`-test$`, src/test) do not match these namespaces (`-tests`, src/demo).
- Neither the form, report, nor routing namespace carries `^:integration` metadata, so nothing was skipped. The fixtures (`with-test-system`, Datomic Local in-memory, `dev/seed!`) started without error.
- Result: 21 tests, 90 assertions, 15 failures, 0 errors, 75 pass.
- Per namespace: headless-form-tests 8 specs, headless-report-tests 3, headless-routing-tests 10 (21 total, matching the kaocha count). Routing: 0 failures.

Failures (file:line, failed expression, expected vs actual):

Form tests: `/Users/tonykay/fulcrologic/fulcro-rad-statecharts/src/demo/com/example/headless_form_tests.clj`
1. `__Form---account-with-address-subform__` line 203: `(vector? primary) => true`. Expected true, actual false.
2. `__Form---account-with-address-subform__` line 203: `(= :address/id (first primary)) => true`. Expected true, actual false.
3. `__Form---account-with-address-subform__` line 214: `(some? (:address/id addr-data)) => true`. Expected true, actual false.
4. `__Form---cancel-account-edit__` line 163: `(form-in-state? app ident :state/editing) => true`. Expected true, actual false.
5. `__Form---cancel-account-edit__` line 182: `(form-in-state? app ident :state/editing) => true`. Expected true, actual false.
6. `__Form---cancel-account-edit__` line 182: `(:account/name (entity-data app ident)) => "Sam"`. Expected "Sam", actual "Sam Modified".
7. `__Form---modify-account-and-save__` line 117: `(form-in-state? app ident :state/editing) => true`. Expected true, actual false.
8. `__Form---modify-account-and-save__` line 136: `(form-in-state? app ident :state/editing) => true`. Expected true, actual false.
9. `__Form---modify-account-and-save__` line 147: `(:account/name (entity-data app2 ident)) => "Tony Updated"`. Expected "Tony Updated", actual "Tony".
10. `__Form---edit-existing-item__` line 233: `(form-in-state? app ident :state/editing) => true`. Expected true, actual false.
11. `__Form---modify-item-price-and-save__` line 258: `(form-in-state? app ident :state/editing) => true`. Expected true, actual false.
12. `__Form---modify-item-price-and-save__` line 275: `(form-in-state? app ident :state/editing) => true`. Expected true, actual false.
13. `__Form---edit-existing-account__` line 89: `(form-in-state? app ident :state/editing) => true`. Expected true, actual false.
14. `__Form---edit-existing-invoice__` line 306: `(form-in-state? app ident :state/editing) => true`. Expected true, actual false.

Report tests: `/Users/tonykay/fulcrologic/fulcro-rad-statecharts/src/demo/com/example/headless_report_tests.clj`
15. `__Account-invoices-report__` line 173 (context "loading account-specific invoices"): `(pos? (count (:ui/current-rows rd))) => true`. Expected true, actual false.

Other output from this run (not investigated):
- 960 ERROR log lines from `com.fulcrologic.rad.form:187`: "No renderer was installed for layout style :default for UI element :form-container".
- 35 `:event/save-failed` events in the statechart log.
- Full kaocha output (3.1 MB, ANSI-stripped copy): `/private/tmp/claude-501/-Users-tonykay-fulcrologic-fulcro-rad-statecharts/0047c70e-74e5-4189-bb45-78679f107710/scratchpad/demo-tests-clean.txt`

## 3. CLJS :main build (COMPILES, 0 warnings, 0 errors)
- `pnpm install --frozen-lockfile`: succeeded (145 packages). Creates `node_modules/` (gitignored).
- `pnpm exec shadow-cljs compile main`: exit 0. Log: `/private/tmp/claude-501/-Users-tonykay-fulcrologic-fulcro-rad-statecharts/0047c70e-74e5-4189-bb45-78679f107710/scratchpad/shadow-main-compile.log`
- Result: `[:main] Build completed. (441 files, 440 compiled, 0 warnings, 18.50s)`
- The 4 `WARNING:` lines are JDK `sun.misc.Unsafe` deprecation notices from the closure-compiler jar. They are not shadow compiler warnings.
- Not run: the `ci-tests` build and `karma start --single-run` (CI's cljs job and the Makefile). Not requested.

## 4. Side effect that needs a decision
- `pnpm install` rewrote `/Users/tonykay/fulcrologic/fulcro-rad-statecharts/pnpm-lock.yaml`. It is an uncommitted, 199-line pure insertion: a pnpm 11 `packageManagerDependencies` header block at the top of the file. No dependency entries changed.
- I tried to restore it with `git checkout -- pnpm-lock.yaml`. The auto-mode classifier denied that command as irreversible local destruction. I did not try another way to overwrite the file.
- To revert: run `git checkout -- pnpm-lock.yaml` yourself, or keep the file if the pnpm 11 header is wanted.

## 5. Cleanup and what was not done
- My nREPL (port 7892) and its JVM were stopped. The `.nrepl-port` file it wrote was removed. The `ps` check afterward found no processes from my runs.
- Other processes on this machine belong to other worktrees or REPLs and were not touched: a shadow-cljs watch under `/Users/tonykay/fulcrologic/.worktrees/simplymeet-referrer-unban-approved-only`, and several JVMs with `-Dtest`. One of those JVMs (PID 43881, `-Dtest`) appeared during the run. I did not start it.
- No source or test files were edited. No commits or pushes.
- Skipped: the `ci-tests` shadow build and karma (not requested); the CLI runner (`clojure -A:dev:tests:clj-tests`), per the rules.
