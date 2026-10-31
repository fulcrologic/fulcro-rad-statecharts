# Spec: rad-parity Final Verification

**Status**: backlog
**Priority**: P0
**Created**: 2026-10-31
**Owner**: AI (verification) + critique agent
**Depends-on**: rp-01, rp-02, rp-03, rp-04, rp-05, rp-06, rp-07, rp-08, rp-09, rp-10, rp-11, rp-12a, rp-12b, rp-13, rp-14, rp-15, rp-16, rp-17, rp-18, rp-19
**Skills**: `clojure`, `clojure-repl`, `fulcro-spec-tdd`, `fulcro-headless`, `claude-in-chrome`, `deps-upgrade`

## Context

This spec closes the rad-parity effort. It re-checks every claim the 2026-10-31 review disproved (`review/00-gap-report.md`) and gates the release.

The previous tracker said things like "E2E all pass" and "pom.xml matches deps.edn" without re-running anything. This spec re-runs everything and records the evidence.

## Requirements

1. **CLJ.** `:unit` and `:demo` are green in one REPL, in both suite orders. Record test and assertion counts.
2. **CLJS.**
   - `main` and `ci-tests` compile with 0 warnings.
   - Run the CLJS test build (`pnpm exec karma start --single-run`, as CI does) and record pass counts.
   - The GitHub Actions run on `rad-parity` is green.
3. **Chrome walkthrough** of every demo screen (`claude-in-chrome` skill), with evidence (screenshots or GIF) and no console errors:
   - landing;
   - AccountList (filter, show-inactive, enable/disable);
   - Account form (addresses, avatar/files, tags quick-create, timezone);
   - Inventory (category filter and link);
   - Item form;
   - InvoiceList (row actions, delete);
   - Invoice form (line items, derived totals, customer quick-create);
   - AccountInvoices;
   - SalesReport;
   - RealSalesReport (pivot, chart);
   - Dashboard;
   - master-detail;
   - login, logout and bookmark replay;
   - route-denial dialog;
   - view-mode form;
   - URL bookmark and restore for a report and a form.
4. **Downstream compatibility:**
   - **fulcro-rad-tui.** Its test suite passes against this lib's `rad-parity` (REPL `-Sdeps` `:local/root` override). Its map-registered container layout renders through the auto-generated container body (rp-05).
   - **fulcro-rad-datomic.** Its save and delete middleware work through the demo (save, delete with the legacy symbol). Its own tests pass with this lib on the classpath, if it has a suite that loads this lib.
5. **Packaging.**
   - `packaging_spec` is green.
   - `pom.xml` dependencies were regenerated with `clj -Spom` after the last `:deps` change.
   - If rp-19 produced a statecharts release, bump the dependency here (`deps.edn`, then `clj -Spom`), drop redundant workarounds, and re-run 1–2.
6. **Final critique.** Spawn a `critique` agent (opus) over the whole rad-parity diff (`git diff main...rad-parity`) and the specs. Every critique finding becomes a new spec or fix task. Each defect fix gets a regression test that fails before the fix.
7. **Tracker.**
   - Every rad-parity spec is `done`, with evidence links.
   - The "Known Issues" and "Verified Working" sections are rewritten from this run's evidence only.
8. **Formatting.** `cljfmt check` passes on all files changed in the effort (`git diff --name-only main...rad-parity -- '*.clj*'`), run from the repo root.

## File Ownership

- `docs/ai/specs/TRACKER.md`, rad-parity spec statuses, this spec's evidence section
- `deps.edn`/`pom.xml`: only the req 5 statecharts bump (`pom.xml` via `clj -Spom`)
- No source edits. Defects found here become new specs.

## Affected Modules

- None directly.

## Approach

- Run the steps in order: 1 → 2 → 4 → 3 → 6 → 7. Fix nothing inline; file follow-ups.

## Open Questions

- **Release.** Should rad-parity be released as 0.2.0 (breaking: removed `sfo`/`sro` keys, signature changes) or 0.1.x? Recommendation: 0.2.0.

## Verification

1. [ ] Reqs 1–8 done, with evidence recorded below. Counts, run ids and screenshot links included.
2. [ ] Every critique finding is closed or has a spec.
3. [ ] REPLs, servers and shadow processes stopped. Tracker committed and pushed.

## Evidence

(filled in by the implementer)
