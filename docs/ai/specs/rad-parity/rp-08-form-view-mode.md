# Spec: Form View Mode

**Status**: backlog
**Priority**: P1
**Created**: 2026-10-31
**Owner**: AI
**Depends-on**: rp-07, rp-06
**Skills**: `clojure`, `clojure-repl`, `fulcro-spec-tdd`, `statechart`, `fulcro-rad`, `fulcro-headless`

## Context

**Human decision 1: view mode comes back.**

Upstream (`../fulcro-rad` `form.cljc`):
- `view!` routes with `{:action "view" :id id}`.
- `view-mode?` reads the form machine's `:options :action`.
- `read-only?` returns true for every field when the master form is in view mode.

This lib removed all three; `statechart/CLAUDE.md` explains why: the old code was dead, because nothing set `:action`. Now it must be real behavior that something sets and that survives a URL restore. Sources: `review/review-forms.md` #9; `review/review-routing.md` (feature mapping, `view!`).

## Requirements

1. **`form/view!`** — `[app-ish Form id]` and `[app-ish Form id params]`. It routes to a routed form (`form-route-state`) in view mode, with the same params and options split as `edit!` (rp-03).
2. **View mode is part of the route.** A bookmarked or URL-restored view-mode form opens in view mode, and an edit URL opens in edit mode.
   - Default `:route/params` for `form-route-state` include the mode key. Choose the key and value and document them.
   - Existing edit/create URLs keep working.
3. **`form/view-mode?`** takes a form instance (master or subform), or app-ish plus form ident. It returns true when the master form's session is in view mode. The answer is computed from session data that the chart actually sets at start, not from dead constants.
4. **`form/read-only?`** returns true for every field when the master form is in view mode, in addition to the existing `fo/read-only?`/`fo/read-only-fields`/attribute rules.
5. **The chart enforces view mode.** In view mode:
   - `:event/save`, `:event/attribute-changed`, `:event/add-row` and `:event/delete-row` are ignored (debug log);
   - `form-busy?` is false;
   - loading, reload and cancel work.
   - `start-form!` accepts the mode in its params, so a non-routed form can be viewed.
6. **Renderers show view mode.** The headless plugin renders fields read-only and hides or disables Save/Undo/add/delete controls in view mode, through `form/read-only?` and `form/view-mode?`. Keep the headless change minimal.

## File Ownership

- `src/main/com/fulcrologic/rad/statechart/form.cljc`
- Form test namespaces, plus a new `form_view_mode_spec.cljc`
- `src/main/com/fulcrologic/rad/rendering/headless/form.cljc` and `field.cljc`: only the view-mode read-only and control-visibility changes
- `src/test/com/fulcrologic/rad/rendering/` (new view-mode rendering test)
- `src/main/com/fulcrologic/rad/statechart/CLAUDE.md`: replace the "View Mode (decision …: RESTORE)" section with the implemented design

## Affected Modules

- `statechart/form.cljc`: `view!`, `view-mode?`, `read-only?`, `form-route-state` default params, chart guards, `form-busy?`, `start-form!`
- Headless form and field renderers

## Approach

- Carry the mode in the route params, e.g. `:action :view`, which is serializable, and copy it into the session at start, e.g. `[:options :action]` or a dedicated `::form/mode`.
- Guard the editing transitions with a `view-mode?` condition rather than a separate state, so the chart's shape and fragments don't change.

## Open Questions

- None.

## Verification

1. [ ] New tests fail before and pass after:
   - `view!` routes;
   - URL restore of a view URL opens in view mode (`create-test-app-with-url-sync`);
   - `view-mode?` is true in the master and in subforms;
   - `read-only?` is true for all fields;
   - save and change are ignored;
   - `form-busy?` is false;
   - the headless render shows no enabled Save and has read-only inputs.
2. [ ] Tests run with kaocha in the implementer's own REPL. No CLI test runs.
3. [ ] Full CLJ suite green: `:unit` + `:demo`.
4. [ ] CLJS `ci-tests` compiles with 0 warnings.
5. [ ] `statechart/CLAUDE.md` view-mode section rewritten. REPL killed. Committed and pushed.
6. [ ] `cljfmt check <changed files>` passes from the repo root (fulcro `.cljfmt.edn`; see root `CLAUDE.md` "Build Hygiene").
