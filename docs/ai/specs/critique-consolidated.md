# Consolidated Critique Assessment

**Date**: 2026-02-21
**Reviewer**: cross-critic (cross-review of 4 independent critiques)
**Sources**: critique-uism-remnants, critique-api-design, critique-architecture, critique-production-readiness

---

## Cross-Consistency Check

### Agreements

All four specs agree on:
- The library is architecturally sound and achieves its primary goal
- Dead UISM code must be removed
- The `start-report!` / `::machine` bug is real and must be fixed
- Form/report stubs in `form.cljc` are problematic
- Container statechart has zero test coverage
- No user documentation exists
- Statecharts dependency is a release blocker
- pom.xml is stale

### Contradictions Found

1. **Severity of side-effects in `apply-action`**: Architecture critique rates this IMPORTANT; production-readiness doesn't mention it at all. The architecture critique is correct — this is a real architectural violation. However, it is NOT a release blocker because Fulcro's `swap!` is not retry-based and the pattern works in practice. **Resolution: IMPORTANT, not CRITICAL.**

2. **`requiring-resolve` CLJS compatibility**: Architecture critique flags this as IMPORTANT, claiming it may fail in CLJS. However, `requiring-resolve` has been available in ClojureScript since recent versions, and these expression functions only run at runtime after all namespaces are loaded. The real risk is low. **Resolution: SUGGESTED — verify but don't block on it.**

3. **Missing features framing**: API-design critique lists 7 "missing features" (auth, blob, dynamic gen, hooks, Pathom3, view!, history params). Production-readiness lists rendering plugins as blocking. But the UISM-remnants and architecture critiques correctly note this is NOT a drop-in replacement — auth, blob, hooks, and dynamic gen were intentionally deferred. **Resolution: Only `view!` stub and rendering plugins matter for this assessment. The rest is scope creep (see below).**

4. **`edit!` wrong session**: Production-readiness lists this as IMPORTANT. Architecture critique analyzed it and found the routing path should work correctly via `form-route-state` entry function. TRACKER lists it as a known issue. **Resolution: Keep as IMPORTANT — needs investigation to confirm whether it's real or already fixed.**

---

## Principle Adherence Check

### "Modelling the data is king" — PASS
All four specs confirm: Fulcro's normalized client DB is the source of truth. Statechart session data is limited to process-tracking concerns (options, abandoned flags, cache timestamps). No spec found data duplication.

### "Statecharts model process, not data" — PASS
Form/report/container states map to user-observable lifecycle stages. No phantom states found.

### "No side effects in UI code other than signals to statecharts" — MOSTLY PASS
Public API correctly delegates to `scf/send!` / `scf/start!`. The architecture critique correctly identified side effects inside `fops/apply-action` closures (transact!, route-to!) which violate this principle. These should be fixed but aren't blocking.

### "RAD reduces boilerplate via declarative attributes" — PASS
Macros remain declarative. Users don't need to understand statecharts.

### "This is NOT a drop-in replacement" — PASS
The specs correctly treat this as a new library. No backward-compat hacks recommended.

---

## Scope Creep Check

The following recommendations from the critique specs exceed what this library needs:

| Recommendation | Source | Why It's Scope Creep |
|----------------|--------|---------------------|
| Add authorization system | API-design | Intentionally deferred (see TRACKER) |
| Add blob/file upload | API-design | Intentionally deferred |
| Add Pathom 3 support | API-design | Out of scope for this library |
| Add dynamic form/report generation | API-design | Intentionally deferred |
| Add rad_hooks replacement | API-design | Intentionally deferred (React hooks banned) |
| Route-denied modal helper component | API-design | Nice-to-have, not library responsibility |
| `container-route-state` helper | API-design | Trivial enough for users to write |
| `rroute/view!` convenience function | API-design | No clear use case yet |
| `fo/initialize`, `fo/busy?` aliases | API-design | Creates unnecessary indirection |
| Cache eviction for server-paginated | Production-readiness | Premature optimization |
| Load/save timeout events | Architecture | Feature addition, not bug fix |

**These should all be ignored or deferred.** The library should ship minimal and grow based on real usage.

---

## Consolidated Findings (Deduplicated, Prioritized)

### CRITICAL — Must Fix Before Any Release

| # | Finding | Sources | Rationale |
|---|---------|---------|-----------|
| C1 | **Statecharts library is a local path dependency** — no released Clojars artifact exists. No downstream project can consume this library. | Production-readiness | Complete deployment blocker |
| C2 | **pom.xml version mismatches** — Fulcro 3.7.0-RC3 (should be 3.9.3), Clojure 1.10.3 (should be 1.11.4), statecharts 0.1.0-SNAPSHOT (should match actual). Consumers will get wrong transitive deps. | Production-readiness | Deployment blocker |

### IMPORTANT — Should Fix Before Release

#### Dead Code

| # | Finding | Sources | Rationale |
|---|---------|---------|-----------|
| I1 | **~650 lines of dead UISM code** — `form-machine` (~260 lines), `report-machine` (~106 lines), ~14 UISM-only helper functions, 5 dead requires, 1 dead test. All must be removed. | UISM-remnants, API-design | Actively misleads users; inflates codebase by ~25% |
| I2 | **Stale UISM docstrings** — `form_options.cljc` and `report_options.cljc` document `(fn [uism-env ...])` signatures for options that now take different args. | UISM-remnants | Will cause user errors |

#### Bugs

| # | Finding | Sources | Rationale |
|---|---------|---------|-----------|
| I3 | **`start-report!` reads `::machine` instead of `ro/statechart`** — custom report statecharts set via macro-supported `ro/statechart` are silently ignored. | API-design | Correctness bug — silent failure |
| I4 | **`form/view!`, `form/edit!`, `form/create!` are silent stubs** — log warnings and do nothing. Working implementations exist in `routing.cljc` under different names. Users find the broken versions first. | API-design, UISM-remnants | Confusing; should either redirect or throw |
| I5 | **`sfr/edit!` wrong session target** — documented in TRACKER as sending to form session instead of routing session. | Production-readiness, Architecture | May cause routing failures |

#### Architecture

| # | Finding | Sources | Rationale |
|---|---------|---------|-----------|
| I6 | **Side effects inside `fops/apply-action`** — `rc/transact!` and `route-to!` inside `(fn [state-map] ... state-map)` closures in form_expressions.cljc (~4 locations). Violates statechart action principles. | Architecture | Architectural violation; not blocking because Fulcro's swap! doesn't retry, but should be fixed |
| I7 | **`on-loaded-expr` is 70 lines** — handles 5 distinct concerns in one function. Should be decomposed into `build-autocreate-ops` and `build-ui-props-ops`. | Architecture | Maintainability |

#### Testing

| # | Finding | Sources | Rationale |
|---|---------|---------|-----------|
| I8 | **Container statechart — ZERO tests** at any tier. `broadcast-to-children!`, `run-children-expr`, `resume-children-expr` all untested. | Production-readiness | Shipping untested foundational abstraction |
| I9 | **Server-paginated report — ZERO tests**. 259 lines, 11 expression functions, page caching logic, all untested. | Production-readiness | Complex code path completely unverified |
| I10 | **Incrementally-loaded report — ZERO tests** at any tier. | Production-readiness | Different loading strategy, unverified |
| I11 | **43 pre-existing test failures** in statecharts mock protocols. Affects user test authoring confidence. | Production-readiness | Should be fixed in statecharts library before release |

#### Ecosystem

| # | Finding | Sources | Rationale |
|---|---------|---------|-----------|
| I12 | **No rendering plugin ported** — semantic-ui and react-bootstrap both need multimethod conversions. Library is unusable for browser apps without one. | Production-readiness | Adoption blocker (not release blocker if headless-only is initial target) |
| I13 | **No user documentation** — no README update, no migration guide, no API reference, no testing guide. | Production-readiness, API-design | Users cannot adopt without docs |

#### API Clarity

| # | Finding | Sources | Rationale |
|---|---------|---------|-----------|
| I14 | **Two routing APIs without guidance** — `rroute/route-to!` and `scr/route-to!` both work, demo uses both. No documented recommendation. | API-design | User confusion |
| I15 | **`fo/route-prefix` and `ro/route` purpose unclear** — still defined in options but not consumed by macros for route-segment generation. | API-design | Dead or undocumented options confuse users |

### SUGGESTED — Nice to Have

| # | Finding | Sources | Rationale |
|---|---------|---------|-----------|
| S1 | **`postprocess-page-state` is a no-op** — TODO comment, dead code. Either implement or remove. | Architecture, Production-readiness | Dead code |
| S2 | **`report-loaded` callback never invoked** — declared in options but unreferenced. | Production-readiness | Dead code |
| S3 | **Container `on-exit` sends `:event/unmount` but reports don't handle it** — dead event. | Architecture | No-op side effect |
| S4 | **Stale comments** referencing UISM in `headless_routing_tests.clj`, `report_expressions.cljc`, `form_expressions.cljc`. | UISM-remnants | Misleading but not harmful |
| S5 | **`defsc-container` docstring references `:route-segment`** — Dynamic Router concept that no longer applies. | API-design | Stale documentation |
| S6 | **Naming inconsistency**: `routing/back!` vs `routing/route-forward!` | API-design | Minor paper cut |
| S7 | **`form_machines.cljc` naming confusion** — despite being statechart-based, name echoes old UISM form-machines. Consider renaming. | UISM-remnants | Confusion risk |
| S8 | **Verify `requiring-resolve` in CLJS** — used in form_expressions.cljc to break circular deps. May need reader conditionals. | Architecture | Low risk but should verify |
| S9 | **`::uism/asm-id` in `default-network-blacklist`** — dead entry since forms/reports no longer include it in queries. | UISM-remnants | Cosmetic dead code |
| S10 | **No concurrent scenario tests** — no tests with two forms open, or form+report coexistence. | Production-readiness | Edge case coverage |
| S11 | **Busy flag visibility in sorting/filtering** — eventless transitions may fire in same microstep, making busy flag never visible to UI. | Architecture | Should be documented |
| S12 | **SCM tag in pom.xml** is a hardcoded commit hash — should be dynamic for releases. | Production-readiness | Release process concern |

---

## Missed Concerns (Not Covered by Any Spec)

1. **Guardrails specs on new functions**: The expression functions in `form_expressions.cljc` and `report_expressions.cljc` lack Guardrails `>defn` type annotations. Old UISM code had them (e.g., `calc-diff` with `::uism/env` spec). The new statechart expressions should have equivalent specs for development-time validation.

2. **ClojureScript compilation verification**: No spec mentions whether the full library compiles cleanly under ClojureScript (not just CLJ). The `.cljc` files could have CLJ-only constructs that slip through. The demo runs in CLJS, so this is likely fine, but explicit CLJS compile verification should be part of the release checklist.

3. **Thread safety of session management**: `ident->session-id` is pure and safe, but `scf/start!` and `scf/send!` mutate statechart working memory. If multiple browser events fire concurrently (e.g., two report loads triggered simultaneously), is the statechart event processing serialized? This is likely handled by the statecharts library's event queue, but should be verified.

---

## Release Readiness Summary

| Gate | Status | Blocking? |
|------|--------|-----------|
| Statecharts released to Clojars | Not done | **YES** |
| pom.xml accurate | Not done | **YES** |
| Dead UISM code removed | Not done | No (but strongly recommended) |
| `start-report!` bug fixed | Not done | No (workaround: don't use custom charts via `ro/statechart`) |
| Form stubs resolved | Not done | No (workaround: use `routing/create!` etc.) |
| Container tests | Not done | No (but risky) |
| Rendering plugin ported | Not done | Depends on target audience |
| Documentation | Not done | Depends on target audience |

**Minimum for internal/alpha release**: C1 + C2 only.
**Minimum for public beta**: C1 + C2 + I1-I5 (dead code, bugs, stubs).
**Minimum for stable release**: All CRITICAL + all IMPORTANT.

---

## Action Plan (Recommended Order)

### Phase 4a: Release Blockers
1. Release statecharts library to Clojars (C1)
2. Regenerate pom.xml from deps.edn (C2)

### Phase 4b: Code Cleanup
3. Delete dead UISM code — form-machine, report-machine, helpers, requires (I1)
4. Fix `start-report!` to read `ro/statechart` (I3)
5. Remove or redirect form stubs to routing.cljc (I4)
6. Update stale docstrings in form_options/report_options (I2)
7. Investigate and fix `edit!` session targeting (I5)

### Phase 4c: Testing
8. Add container statechart tests — Tier 1 + Tier 2 (I8)
9. Add server-paginated report tests (I9)
10. Add incrementally-loaded report tests (I10)

### Phase 4d: Architecture
11. Extract side effects from `apply-action` closures (I6)
12. Decompose `on-loaded-expr` (I7)

### Phase 4e: Ecosystem (for public release)
13. Port one rendering plugin (I12)
14. Write migration guide + README (I13)
15. Document routing API recommendation (I14)
16. Clarify or remove `fo/route-prefix` / `ro/route` (I15)
