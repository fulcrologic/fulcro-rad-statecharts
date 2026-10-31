# Spec: Statecharts Library Follow-ups

**Status**: backlog
**Priority**: P2
**Created**: 2026-10-31
**Owner**: AI
**Depends-on**: rp-03, rp-11
**Skills**: `clojure`, `clojure-repl`, `fulcro-spec-tdd`, `statechart`, `fulcro`

## Context

A few issues found during the rad-parity review belong in `../statecharts`, not in this lib. Sources: `review/review-routing.md` P2-12, P1-9, P2-16, and "rad_integration.cljc parity"; `review/review-ecosystem.md` §1.

- **`integration/fulcro/rad_integration.cljc`** is deprecated and UISM-based (it requires upstream `rad.form`/`rad.report`), and it still says "Better RAD integration coming soon". Nothing in statecharts points users to this lib, which is effectively its successor.
- **url-sync `prev-url` (Suspected).** `scr/replace-route-params!` doesn't update url-sync's `prev-url`, so the next unchanged-route save can push a duplicate history entry. rp-03 and rp-11 confirm or refute this and record a repro under "Findings" below.
- **istate with a keyword chart.** `scr/istate` throws at chart-build time when the target has only `sfro/statechart-id` and no `:route/reachable` is given. rp-03 works around it with `#{}`; the library should default instead.

## Requirements

1. **Deprecation pointer.** `rad_integration.cljc`'s ns docstring (and any Guide page that mentions RAD) points to `com.fulcrologic/fulcro-rad-statecharts`: `form-route-state`, `report-route-state`, `container-route-state`. Remove "Better RAD integration coming soon".
2. **`prev-url` fix, only if Findings confirm it.**
   - `replace-route-params!` keeps url-sync state consistent, so a later save with an unchanged route doesn't push.
   - Add a statecharts test using `simulated_history` that fails before and passes after.
   - If Findings refute it, close this item with a note.
3. **istate keyword chart.** `istate` doesn't throw when the target has `sfro/statechart-id` but no `sfro/statechart` and no `:route/reachable`. It treats reachable as empty and logs at debug. Docstring updated; test added.
4. **This lib's side.** Once a statecharts release containing reqs 2 and 3 exists, rp-20 bumps the dependency, regenerating `pom.xml` with `clj -Spom`, and removes rp-03's `#{}` workaround if it's redundant. This spec doesn't edit this repo, except its own Findings and status.

## File Ownership

- `../statecharts/src/main/com/fulcrologic/statecharts/integration/fulcro/rad_integration.cljc` (docstring), `integration/fulcro/routing.cljc` (reqs 2–3), the matching tests under `../statecharts/src/test/**`, the related Guide docs. `../statecharts/.cljfmt.edn` is already fixed (local commit `a687336`).
- This file: the Findings section and status

## Affected Modules

- statecharts routing and url-sync, the deprecated RAD integration docs

## Approach

- Work in the statecharts repo's own REPL with its own kaocha config. Re-run this lib's `:unit` + `:demo` against the changed statecharts via a temporary `:local/root` override **in the REPL command line only** (`-Sdeps`), never by editing this repo's `deps.edn`.

## Findings

(Appended by rp-03 and rp-11: repro steps, expected vs. actual, test names.)

## Open Questions

- **Branch and release policy for `../statecharts`.** Should this work go straight to `main` or to a feature branch, and when should an RC (e.g. RC20) be cut so this lib can depend on it? Human call. Until decided: commit on a local branch `rad-parity-followups`, don't push.

## Verification

1. [ ] New statecharts tests (reqs 2–3) fail before and pass after. Or req 2 is closed as refuted, with the evidence in Findings.
2. [ ] statecharts' own suite is green, run with kaocha in the implementer's own REPL in that repo. No CLI test runs.
3. [ ] This lib's `:unit` + `:demo` are green against the modified statecharts (REPL `-Sdeps` override).
4. [ ] `cljfmt check <changed files>` passes from the statecharts repo root.
5. [ ] REPL killed. Committed per the branch policy (local only until decided).
