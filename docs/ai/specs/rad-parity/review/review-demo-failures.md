# Demo E2E failure diagnosis (fulcro-rad-statecharts)

**Bottom line:** I found no library regression and no upstream regression behind the 15 failures. 14 come from stale or timing-fragile test helpers. The 15th is a test that routes without the parameter the report needs. With the right session id, edit, save and reset all work in the REPL, and the save persists across clients. Separately, there is one real but dormant library bug: the renderer can be replaced depending on load order. It caused the 960 log errors but none of the failures.

Method: my own nREPL (`-M:dev:tests:demo` + kaocha 1.91.1392), now stopped. A kaocha run of `headless-form-tests` reproduced the same 14 failures (seed 1872295122, 23 assertions passed). After that I drove the app by hand against the live fixture. No files were edited.

---

## 1. `form-in-state? … :state/editing` false (:89, :117, :136, :163, :182, :233, :258, :275, :306). Class (c), Verified

- **Root cause:** the helper looks in the wrong session. Since `e364841` (2026-03-01, "switch to istate routing"), `form/form-route-state` (`form.cljc:1877`) runs the form as an `scr/istate` invocation. Its session id is a **string** such as `":com.example.ui.account-forms/AccountForm.<uuid>"`. The helper at `headless_form_tests.clj:53-61` still uses `sc.session/ident->session-id`, which belongs to the old `start-form!` path. The tests were last touched 2026-02-23, before that switch.
- **Evidence (REPL), after routing to account 100:**
  - The session list holds only `master-statechart`, `routing/session` and `":com.example.ui.account-forms/AccountForm.c9a7…"`.
  - The session `:com.fulcrologic.rad.sc/account_id--ffff…0100` has configuration `#{}`.
  - Looking the id up in `[:com.fulcrologic.rad.statechart.form/form-session-ids ident]` gives `#{:state/editing :state/active}` (5 of 5 runs).
  - In the kaocha log, every form ran `on_loaded_expr` and entered `:state/editing`.
- **Second problem in the same helper (timing):** `wait-for-form!` renders 30 frames but does not wait for anything. In one REPL run the form was still `#{:state/active :state/loading}` right after it returned.
- **Fix:** read the session id from `[::sc/local-data scr/session-id :invocation/id <registry-key>]`, or from `::form/form-session-ids`. Making the private `resolve-form-session-id` public would be cleaner. Then poll until `:state/editing` instead of rendering a fixed number of frames.

## 2. Saves don't persist (:147); cancel leaves "Sam Modified" (:182). Class (c), Verified

- **Root cause:** same as cluster 1. `:event/save` and `:event/reset` go to the non-existent ident-derived session. `scf/send!` returns `true` and nothing happens.
- **No save was ever attempted.** The earlier log never runs `prepare-save-expr`, `on-saved-expr` or `undo-all-expr`. All 35 lines that mention `:event/save-failed` are DEBUG dumps of chart or form options inside `run-expression!` results, not real events.
- **Evidence (REPL):**
  - Sending `:event/save` to the real session goes `#{:state/saving}` → `#{:state/editing}`, with no `::form/errors`. A fresh client then loads `"Tony Updated"`, so the server save path works.
  - `:event/reset`: sent to the ident-derived id the name stays `"Sam Modified"`; sent to the real id it becomes `"Sam"`.
- **Fix:** the session-id fix from cluster 1.

## 3. Address subform (:203, :214): primary address not an ident. Class (c), timing, Verified

- **Root cause:** the assertion runs before the load response has been merged. In both full runs this was the first test executed, right after server start on a cold JVM, so 30 frames did not cover the HTTP round-trip.
- **Evidence:**
  - Checking state right after `route-to!` gives exactly the failure pattern: account keys are only `(:account/id)`, `primary` is nil, `(some? addr-data)` is true (because `get-in` with a nil path returns the whole state), and `:address/id` is nil.
  - On a warm JVM, primary is `[:address/id #uuid "…0300"]` in 5 of 5 runs.
  - Kaocha running that test alone (fresh fixture) passed twice, 4 of 4 assertions.
- **Fix:** the same "wait until `:state/editing`" helper.

## 4. Account-invoices report (`headless_report_tests.clj:173`): empty rows. Class (c), Verified

- **Root cause:** the test calls `(wait-for-report! app AccountInvoices {})` with no `:account/id`. The server logs `WARN database-queries:41 get-customer-invoices called without :account/id` and returns `[]`.
- **Evidence (REPL):** routing with `{}` gives `:state/ready` and 0 rows. Routing with `{:account/id (new-uuid 101)}` gives 2 rows, with `:account/id` present in `:ui/parameters`.
- **History (Suspected it never passed as written):** the test has routed with `{}` since `9688775` (Feb 20). Before the guard added in `4aa769c` (Feb 22), a nil id made `d/q` throw (`Unable to find data source: $__in__2`, reproduced). The tracker's "21 E2E pass" line was written 2026-02-21 10:12, before both `4aa769c` and the istate switch, and was not re-checked.
- **Fix:** pass `{:account/id (new-uuid 101)}`.

## 5. 960× `ERROR com.fulcrologic.rad.form:187 No renderer … :form-container`. Class (a), load-order bug, Verified; did not cause any failures

- **Root cause:** three namespaces each define `(defmethod fr/render-form :default …)`, and the last one loaded wins:
  - upstream `com.fulcrologic.rad.form:364`, the map-based `render-fn` that logs this error;
  - `statechart/form.cljc:273`;
  - `rendering/headless/form.cljc:162`.
- **When it fires:** the library unit suite never loads upstream `rad.form` (only `:as-alias` from `report.impl`). The earlier REPL ran the unit suite first, so the headless method was installed first. The demo then loaded upstream `rad.form` later via `com.example.model.account` (needed server-side for `form/save-form*` and `form/pathom-plugin`), and upstream replaced the headless method. A demo-only JVM loads upstream first, so headless wins: my run had 0 such errors and the same 14 failures.
- **Evidence (REPL):** reloading upstream `rad.form` switches the `:default` method to `com.fulcrologic.rad.form$eval…`. A created AccountForm then renders no "Save" button (it did before), and 14 of these errors are logged.
- **Why it matters:** any CLJ full-stack process, or any test that checks hiccup, can silently render nil forms. This may be what the test-file note "Hiccup rendering returns nil for form routes" is describing (Suspected).
- **Fix:** register the headless and statechart renderers on a dispatch value upstream doesn't claim (a dedicated style or hierarchy parent instead of `:default`), or re-assert the method when the plugin is installed. Don't depend on load order.

## 6. Unit suite `ArityException … raw.application/fulcro-app/fn` (tx-processing:152). Classes (c) and (d), Verified

- **Root cause:** the test apps are built with `(rad-app/fulcro-rad-app {})`, so they get Fulcro's built-in fallback remote. That fallback's `:transmit!` takes one argument (`(fn [{::txn/keys [result-handler]}] …)`, `raw/application.cljc:308` in fulcro 3.9.3, the same in 3.10.0-RC2). `txn/net-send!` calls it with two, `(transmit! remote send-node)`. This is old latent upstream behaviour, not caused by the version bump.
- **Which tests trigger it:** I captured 5 hits during `(kaocha.repl/run :unit)`, which gave 90 tests, 427 assertions, 0 failures. They come from the `test-app` helpers in:
  - `src/test/com/fulcrologic/rad/statechart/form_statechart_test.cljc:39-42`: the edit flow (:96) loads `[:person/id …]` and the save flow (:136) runs `save-form` with a tempid;
  - `report_statechart_test.cljc:48-51`: the auto-load test (:74) loads `:item/all-items`.
  - `test_helpers/create-test-app` has the same pattern.
- **Why tests still pass:** the exception is caught, the handler reports a 500, and the error event is delivered from Fulcro's tx thread. That races the test thread, which is the same flakiness `e3d4e90` fixed for the container test.
- **Fix:** give these test apps a silent remote, `{:remotes {:remote {:transmit! (fn [_ _] nil)}}}`, as `e3d4e90` did. Optionally fix the fallback remote's arity in Fulcro itself.

## Also stale
- The docstring at the top of `headless_form_tests.clj` and the `src/main/com/fulcrologic/rad/CLAUDE.md` "Routing" section still describe the `rstate` + `start-form!` design.
