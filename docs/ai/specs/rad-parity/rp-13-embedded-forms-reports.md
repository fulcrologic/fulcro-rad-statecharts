# Spec: Embedded Forms and Reports (Statechart Replacement for rad-hooks)

**Status**: backlog
**Priority**: P1
**Created**: 2026-10-31
**Owner**: AI
**Depends-on**: rp-03, rp-04, rp-07
**Skills**: `clojure`, `clojure-repl`, `fulcro-spec-tdd`, `statechart`, `fulcro`, `fulcro-rad`, `fulcro-headless`

## Context

Upstream `com.fulcrologic.rad.rad-hooks` (`use-form`, `use-report`) is UISM-based and unusable here. That loses non-routed embedding: modals, master-detail, and quick-create *editing*. Sources: gap report §4 "rad-hooks"; `review/review-ecosystem.md` §1.

Every existing replacement uses React hooks for data, which the human's rule forbids:
- fulcro-with-statecharts `semantic_ui/modals.cljc`;
- statecharts `integration/fulcro/hooks.cljc` `use-statechart`;
- the old plan `docs/ai/specs/rad-hooks-conversion.md`.

The compliant design is a **parent chart that invokes the form or report chart** as a child session, with Fulcro state as the only data store.

Prerequisites this spec builds on, without editing their files:
- rp-03: start options reach the form chart, and charts are registered per component.
- rp-04: aliases are owned by report charts, and charts are istate-safe.
- rp-07: cancel keeps the session alive, ops and fragments are public, and the custom-chart guide exists.

## Requirements

1. **New ns** `com.fulcrologic.rad.statechart.embedded` (CLJC). It provides chart elements for a parent statechart:
   - **Form invocation.** An element, or a fn returning an `invoke`/`state`, that runs the RAD form chart for `Form` and an ident expression (existing id, or tempid for create), with start options from rp-03.
     - The child session is registered so the public form API (`save!`, `cancel!`, `input-changed!`, …) finds it.
     - The parent receives outcome events: saved (with the persisted ident), cancelled, and exited.
   - **Report invocation.** The same for any report variant, with route params replaced by explicit params. Embedded reports never touch the URL (rely on rp-11 `ro/track-in-url?`; until rp-11 lands, document the known issue).
   - **Rendering helpers.** How the parent component's query and body include the embedded form or report, e.g. a join under the parent's ident that the parent chart sets with `fops/apply-action`, plus a render helper.
2. **No React hooks for data.** All lifecycle and data flow is statecharts plus Fulcro state. React hooks are allowed only for transient UI concerns (animations, transient strings), per the user's global rule.
3. **Use-case APIs and tests.** Each has a headless CLJ test with a silent remote and delivered load and save results:
   - **Modal edit.** A parent state machine opens a modal (state), invokes the form, closes on saved or cancelled, and passes the saved ident to the parent.
   - **Master-detail.** A report plus an embedded form. Selecting a row (re)invokes the form for that row. Switching rows while the form is dirty is guarded: the parent chart asks, the same way route denial does, without a blocking dialog. Saving updates the report row.
   - **Quick-create editing.** From a picker, "create new" opens an embedded create form. On save, the new ident is set into the parent form's field and the picker options refresh.
4. **Migration note.** The ns docstring and `statechart/CLAUDE.md` map `use-form`/`use-report` usage onto the new API.
5. **No edits to `form.cljc` or `report.cljc`.** If a change there is unavoidable, stop and report it to the conductor, who schedules it after the then-current owner of that file.

## File Ownership

- `src/main/com/fulcrologic/rad/statechart/embedded.cljc` (new)
- `src/test/com/fulcrologic/rad/statechart/embedded_*_spec.cljc` and `embedded_*_test.cljc` (new)
- `src/main/com/fulcrologic/rad/statechart/CLAUDE.md` (a new "Embedded" section only)

Read-only: `form.cljc`, `report.cljc`, the variant namespaces, `container.cljc`.

## Affected Modules

- New `statechart/embedded.cljc`

## Approach

- Model it on `scr/istate`, which already invokes a component's co-located chart with actors and params, but without routing. Reuse its actor pattern (`sfro/actors`).
- Child session ids: use a deterministic id derived from the parent session and slot (e.g. `[parent-session slot]` encoded). This lets the parent re-invoke and the form API resolve it through `::form-session-ids`.
- Outcome events: use the child chart's `done.invoke` plus data from the final state. If the form chart doesn't reach a final state on save, use the options (`:on-saved` txn → send to parent session). Prefer whichever rp-07 left cleaner, and record the choice.

## Open Questions

- Should the SUI plugin's hook-based picker and modals (rp-12b debt) be replaced with this API as a separate follow-up spec? Recommendation: yes, a new spec after rp-13 and rp-12b. It isn't in this spec's scope.

## Verification

1. [ ] New tests for each use case (req 3). Each fails without the new ns, e.g. the outcome event isn't delivered or the form API can't find the session, and passes after.
2. [ ] `grep` confirms no React hook used for data in the new ns.
3. [ ] Tests run with kaocha in the implementer's own REPL. No CLI test runs.
4. [ ] Full CLJ suite green: `:unit` + `:demo`.
5. [ ] CLJS `ci-tests` compiles with 0 warnings.
6. [ ] Migration note written. REPL killed. Committed and pushed.
7. [ ] `cljfmt check <changed files>` passes from the repo root (fulcro `.cljfmt.edn`; see root `CLAUDE.md` "Build Hygiene").
