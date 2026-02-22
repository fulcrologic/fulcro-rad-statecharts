# Phase 8 Spec Critique

**Date**: 2026-02-22
**Reviewer**: Critique Agent
**Verdict**: PASS WITH REVISIONS — specs are structurally sound and complete; several issues need fixing before implementation.

---

## Summary

9 specs were generated from `cleanup-analysis.md` Section 6. All major work items are covered, namespace naming is consistent (`statechart` not `sc`), file paths reference real files, and the three-require pattern is correctly documented. The dependency chain is logically correct but has fragile intermediate states that should be documented.

---

## Spec Coverage Check

Every work item from cleanup-analysis.md Section 6 has a corresponding spec:

| Work Item | Spec | Status |
|-----------|------|--------|
| Extract form.impl / report.impl to fulcro-rad | phase8-upstream-impl-extraction | OK |
| Add fulcro-rad dep, delete identical files | phase8-deps-and-identical-cleanup | OK |
| Move form.cljc → statechart/form.cljc | phase8-form-namespace-restructure | OK |
| Move report.cljc → statechart/report.cljc | phase8-report-namespace-restructure | OK |
| Move routing, session, container, control | phase8-supporting-namespace-restructure | OK |
| Split form-options / report-options | phase8-options-namespace-split | OK |
| Compile-time macro validation | phase8-compile-time-options-validation | OK |
| Migrate tests | phase8-test-migration | OK |
| Migrate demo app | phase8-demo-migration | OK |
| Headless rendering (engine-agnostic) | Implicit — stays in place, requires updated | **GAP** (see Issue 1) |

---

## Issues

### Issue 1 (SIGNIFICANT): Headless rendering files not in any Affected Modules

The 5 headless rendering files require namespaces being moved:

```
rendering/headless/report.cljc requires:
  - com.fulcrologic.rad.control → statechart.control
  - com.fulcrologic.rad.form (as-alias) → statechart.form
  - com.fulcrologic.rad.report → statechart.report
  - com.fulcrologic.rad.routing → statechart.routing

rendering/headless/form.cljc requires:
  - com.fulcrologic.rad.form → statechart.form

rendering/headless/controls.cljc, plugin.cljc — likely similar
```

None of the restructure specs list these files in their "Affected Modules" sections. They would be caught by the "grep for old namespace references" approach step, but should be explicitly called out since they're a key part of this project.

**Fix**: Add the `rendering/headless/*.cljc` files to the "Affected Modules" section of `phase8-supporting-namespace-restructure.md` (since that spec handles routing, control, and session — the last batch of renames).

---

### Issue 2 (SIGNIFICANT): test_helpers.cljc omitted from test-migration

The file `src/test/com/fulcrologic/rad/test_helpers.cljc` exists in the test directory but is not mentioned in `phase8-test-migration.md`. It likely contains shared test utilities used by statechart tests and may require namespace updates.

**Fix**: Add `test_helpers.cljc` to the test-migration spec — either in "Test Files to Move" (to `statechart/test_helpers.cljc`) or in a "Test Files to Keep In Place" section with a note to update its requires.

---

### Issue 3 (MODERATE): Intermediate compilation fragility not documented

The dependency chain is:
```
upstream-impl-extraction
  → deps-and-identical-cleanup (adds fulcro-rad dep, deletes identical files)
    → form-namespace-restructure (moves form.cljc → statechart/form.cljc)
      → report-namespace-restructure
        → supporting-namespace-restructure
          → options-namespace-split
            → compile-time-options-validation
              → test-migration
                → demo-migration
```

After `deps-and-identical-cleanup`, our `form.cljc` (statechart version) **shadows** fulcro-rad's `form.cljc` (UISM version) on the classpath. This is correct behavior (`:paths` takes precedence over dependency sources), and the code compiles fine because our version is self-contained. After `form-namespace-restructure` deletes our `form.cljc`, fulcro-rad's version becomes visible.

However:
- **Tests will not compile** between `deps-and-identical-cleanup` and `test-migration` (several specs apart). Tests still reference old namespaces and old option keys.
- Each restructure spec says "Verify compilation" but doesn't clarify that this means **source compilation only**, not tests.

**Fix**: Add a note to `phase8-deps-and-identical-cleanup.md` and each restructure spec stating: "Verification covers source compilation only (`clj -e \"(require ...)\"` for source namespaces). Test compilation will be broken until the test-migration spec is complete."

---

### Issue 4 (MODERATE): Report restructure unnecessarily serialized after form

`phase8-report-namespace-restructure.md` has `depends-on: phase8-form-namespace-restructure`. But form and report restructuring are independent operations — they touch different files and different namespaces. The only shared dependency is that both need `upstream-impl-extraction` and `deps-and-identical-cleanup` done first.

**Fix**: Change the report spec's `depends-on` to `phase8-upstream-impl-extraction, phase8-deps-and-identical-cleanup` (same as form). This allows form and report restructure to run in parallel, saving time.

Similarly, `phase8-supporting-namespace-restructure.md` depends on `phase8-form-namespace-restructure` but could depend on just `phase8-deps-and-identical-cleanup`. The supporting namespaces (routing, session, container, control) are independent of the form/report restructure — they just need the dep in place. Cross-reference updates between them can happen in any order since they're all done before test-migration.

---

### Issue 5 (MINOR): Upstream spec should note fulcro-rad checkout prerequisite

`phase8-upstream-impl-extraction.md` references `../fulcro-rad/` for the upstream work. The directory exists (verified), but the spec should explicitly state this prerequisite in its Context or Approach section.

**Fix**: Add to the Approach section: "Prerequisite: fulcro-rad must be checked out at `../fulcro-rad/`. Verify with `ls ../fulcro-rad/src/main/com/fulcrologic/rad/form.cljc`."

---

### Issue 6 (MINOR): Options-namespace-split should clarify which keys currently exist

The spec lists statechart-specific keys (`sfo/triggers`, `sfo/statechart`, `sfo/on-started`, etc.) but doesn't verify which of these actually exist in the current `form_options.cljc`. Some keys (like `sfo/on-started`, `sfo/on-saved`, `sfo/on-save-failed`) may be new inventions not present in the current code.

**Fix**: Add an Approach step: "Audit current `form_options.cljc` and `report_options.cljc` to inventory every key. Cross-reference with cleanup-analysis Section 2B. Keys not present in current code should be marked as NEW in the spec table."

(Note: The spec does have "Audit current files" as step 1 in Approach, but doesn't distinguish between keys that currently exist vs. keys being newly introduced.)

---

## Positive Findings

### Namespace naming: PASS
All 9 specs consistently use `statechart` (full word). No instances of the old `sc` abbreviation except when referring to the existing `rad.sc.session` path (which is being moved to `rad.statechart.session`).

### Three-require pattern: PASS
The pattern is correctly shown in:
- `phase8-options-namespace-split.md` (Context section)
- `phase8-test-migration.md` (Require Updates section)
- `phase8-demo-migration.md` (Require Update Pattern section)

All three consistently show:
```clojure
[com.fulcrologic.rad.statechart.form :as form]         ;; engine
[com.fulcrologic.rad.form-options :as fo]               ;; shared keys
[com.fulcrologic.rad.statechart.form-options :as sfo]   ;; engine-specific keys
```

### File path verification: PASS
All file paths in "Affected Modules" sections were cross-referenced against the actual repository:
- All source files listed exist at the specified paths
- All test files listed exist at the specified paths
- All demo files listed exist (with minor path variations in the demo tree)
- The `pom.xml` exists at project root (referenced by deps-and-identical-cleanup)

### Dependency chain: PASS (with Issue 3 caveat)
The deps-on chain is logically correct. The classpath shadowing during the transitional period (our form.cljc shadows fulcro-rad's form.cljc) is the expected behavior in Clojure's classpath resolution and doesn't cause issues.

### Format consistency: PASS
All 9 specs follow the same structure: Title, Metadata (Status/Priority/Created/Owner/Depends-on/Phase), Context, Requirements, domain-specific sections, Approach, Affected Modules, Verification checklist. This matches the quality bar of existing specs.

### Upstream PR scope: PASS
The upstream-impl-extraction spec is properly scoped — it describes only the internal refactor to fulcro-rad (extract impl, create def aliases, zero public API change). It does not attempt to modify fulcro-rad's public interface.

---

## Recommended Revision Priority

1. **Issue 1** — Add headless files to Affected Modules (quick fix, prevents missed files)
2. **Issue 2** — Add test_helpers.cljc to test-migration (quick fix)
3. **Issue 4** — Parallelize form/report/supporting restructures (speeds up execution)
4. **Issue 3** — Document compilation expectations (prevents confusion during implementation)
5. **Issue 5** — Note fulcro-rad checkout prerequisite (quick fix)
6. **Issue 6** — Clarify new vs existing option keys (informational)
