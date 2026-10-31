# Demo E2E Test Harness

How to run the tests: `docs/ai/running-tests.md` (the `:demo` kaocha suite, from a REPL only).

## Session ids

- Routed forms run under `form/form-route-state`, an `scr/istate` with a **generated string session id**.
- Address them with `form/resolve-form-session-id` (it reads `[::form/form-session-ids ident]`). In tests, use the `form-session-id` helper.
- **Never** use `sc.session/ident->session-id` for a routed form. It names a session that doesn't exist: `scf/send!` returns true and nothing happens, and `current-configuration` is `#{}`.
- Reports still use `report/report-session-id`, which is constant per report class.

## Waiting

- Never wait by rendering a fixed number of frames, and never with `h/wait-for-idle!` (it gives up after ~100 ms). A cold JVM's first load takes longer than either.
- `headless-client/await!` renders frames until a predicate holds. On timeout (default 10 s, `*await-timeout-ms*`) it throws with the session id, that session's configuration, and what was awaited.
- Forms: `route-to-form!` / `await-form!` wait for `:state/editing`.
- Saves: `await-saved!` waits for editing **and** pristine = the new value. "Back in editing" alone is already true before the save starts.
- Reports: `route-to-report!` / `await-report!` wait for `:state/ready` plus a minimum row count.

## Data

- `test-server/with-test-system` deletes the Datomic Local `:mem` database before and after each run.
  - `start-databases` reuses an existing database, and `:mem` storage lives as long as the JVM.
  - Without the delete, every fixture seeded on top of the last run: invoices have random ids, so they duplicated, and saves leaked between runs.
- Kaocha randomizes test order within a namespace. A test that saves must edit an entity no other test in that namespace reads. The form save test edits Sally (102) for this reason.
- Seed facts the tests rely on: Tony 100 (with primary address 300), Sam 101 (exactly one invoice, total 50.00), Sally 102, Barbara 103, items 200 to 206.
- `AccountInvoices` needs `{:account/id ...}` route params. Without them the server logs a warning and returns `[]`.

## Renderer collision (until rp-02)

- Loading the demo loads upstream `com.fulcrologic.rad.form`, which re-registers `fr/render-form :default`.
- If the headless renderer was loaded earlier (by a `:unit` run), upstream wins. A later `:unit` run in that JVM then fails 16 headless-rendering assertions.
- `:demo` first, then `:unit`, passes. Details in `docs/ai/running-tests.md`.
- These demo tests assert on state and configuration, not form hiccup.
