# Running Tests

**Never run the CLJ tests from the CLI (`clojure -M:...:clj-tests`, `make tests`).** That runner is for CI and the Makefile. Agents and humans run kaocha from their own REPL, as below.

## Suites (`tests.edn`)

| Suite   | Paths                          | Namespaces            | What                                                       |
|---------|--------------------------------|-----------------------|------------------------------------------------------------|
| `:unit` | `src/test`                     | `-test$`, `-spec$`    | Library unit tests (headless apps, no server)              |
| `:demo` | `src/demo` (+ `src/main`)      | `headless-.*-tests$`  | Full-stack headless E2E tests against the demo app server  |

## Start a test REPL

From the repo root:

```bash
clojure -M:dev:tests:demo:kaocha:nrepl            # writes .nrepl-port
clojure -M:dev:tests:demo:kaocha:nrepl --port 7911 # or pick a port
```

- `:kaocha` is kaocha without main-opts. It also sets the test JVM opts: guardrails enabled with `guardrails-test.edn` (`:throw? true`), and `-Dcom.fulcrologic.fulcro.inspect=true`.
- `:nrepl` adds nREPL and its main.
- `:demo` must be on the classpath for the `:demo` suite. It's harmless for `:unit`.
- Kill the REPL when you're done.

## Run suites

```clojure
(require 'kaocha.repl)
(kaocha.repl/run :unit)
(kaocha.repl/run :demo)
```

**Order matters until rp-02 lands: run `:unit` before `:demo`. Once `:demo` has run, restart the REPL before you run `:unit` again.**
- The demo loads upstream `com.fulcrologic.rad.form`. Its `fr/render-form :default` method replaces the headless renderer's if the headless renderer was loaded first, i.e. by an earlier `:unit` run.
- After that, `:unit` fails 16 assertions in `headless-rendering-test` (forms render nil).
- Verified 2026-10-31 in fresh JVMs:
  - `:unit` → `:demo` both pass, then `:unit` again fails 16.
  - `:demo` → `:unit` both pass, because when the demo loads first the headless renderer loads after upstream.
- A run that loads both suites at once hits the same failure: the CLI runner with no suite argument, or a namespace focus without the skip below. CI therefore runs each suite in its own JVM.
- See `docs/ai/specs/rad-parity/review/review-demo-failures.md` §5. Don't work around it in tests.

## Run one namespace or one test

When you focus a namespace or var, kaocha can't skip whole suites up front, so it loads every suite, including `:demo` (see the hazard above). Always skip the other suite:

```clojure
;; one unit namespace / one unit test
(kaocha.repl/run 'com.fulcrologic.rad.statechart.session-spec {:kaocha.filter/skip [:demo]})
(kaocha.repl/run 'com.fulcrologic.rad.packaging-spec/__pom-xml-dependencies__ {:kaocha.filter/skip [:demo]})

;; one demo namespace / one demo test
(kaocha.repl/run 'com.example.headless-form-tests {:kaocha.filter/skip [:unit]})
(kaocha.repl/run 'com.example.headless-report-tests/__Account-invoices-report__ {:kaocha.filter/skip [:unit]})
```

fulcro-spec `specification` vars are named `__<description with spaces as dashes>__`.

## Demo E2E fixtures need no external services

- `com.example.test-server/with-test-system` starts the whole demo system with mount, with Datomic Local **in memory** (`:storage-dir :mem` in `config/dev.edn`) and an http-kit server.
- Each namespace uses its own port: routing 9844, form 9846, report 9847.
- It deletes the database before and after each run, then reseeds with `development/seed!`, so every namespace starts from the same fresh data.
- Waits poll with `com.example.headless-client/await!`, which fails after 10 s. Raise it with `(binding [com.example.headless-client/*await-timeout-ms* 30000] ...)` if a machine is slow.

## CLJS

Same pattern as `../fulcro`: a compile, then a single karma run in ChromeHeadless.

```bash
pnpm install --frozen-lockfile
pnpm exec shadow-cljs compile ci-tests     # :ns-regexp "-(test|spec)$", output target/ci.js
pnpm exec karma start --single-run
```

The CLJS build needs Chrome installed locally. On CI, `karma.conf.js` uses a `--no-sandbox` launcher.

## CI and the Makefile

- `.github/workflows/test.yml` and `make tests` run the CLJS steps above.
- Then they run `clojure -M:dev:tests:demo:clj-tests :unit` and `... :demo`, as separate JVMs because of the order hazard.
