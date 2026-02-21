# Critique: Production Readiness Assessment

**Date**: 2026-02-21
**Scope**: Full library — test coverage, ecosystem gaps, deployment readiness, performance
**Verdict**: NOT READY for public release. Several blocking issues must be resolved first.

---

## Executive Summary

| Area | Status | Verdict |
|------|--------|---------|
| Test Coverage | Good for happy paths, significant gaps in error/edge cases | Important |
| Deployment Blockers | `statecharts` is a local path dep with no released artifact | **CRITICAL** |
| pom.xml Consistency | Fulcro version mismatch (deps.edn 3.9.3 vs pom.xml 3.7.0-RC3) | **CRITICAL** |
| Ecosystem Gaps | Rendering plugins need porting; demo is local-only | Important |
| Performance | No obvious problems; reasonable design | Acceptable |
| 43 Pre-existing Test Failures | Mock protocol incompatibilities in statechart specs | Important |

---

## 1. TEST COVERAGE AUDIT

### 1.1 What IS Tested (83 tests, 446 assertions, 0 failures)

| Test File | Type | Tests | What It Covers |
|-----------|------|-------|---------------|
| `form_statechart_spec.cljc` | Tier 1 (pure chart) | ~20 | All form states, transitions, expression execution flags |
| `report_statechart_spec.cljc` | Tier 1 (pure chart) | ~14 | All report states, pagination, sort/filter, cache resume |
| `form_statechart_test.cljc` | Tier 2 (headless Fulcro) | 6 | Create, edit, attribute-change, cancel, save attempt, exit |
| `report_statechart_test.cljc` | Tier 2 (headless Fulcro) | 7 | Auto-load, no-auto-load, manual run, failed load, pagination, row selection |
| `form_spec.cljc` | Unit | 5 | Query generation, default state, find-fields, optional-fields, form-state init |
| `report_test.cljc` | Unit | 2 | Initial state with fn/map `initialize-ui-props`, `report` helper fn |
| `session_spec.cljc` | Unit | 5 | ident->session-id, session-id->ident, round-trips, auth-session-id |
| `headless_form_tests.clj` | E2E (Datomic) | 8 | Edit existing, modify+save, cancel, subform, item edit, item save, invoice, sequential nav |
| `headless_routing_tests.clj` | E2E (Datomic) | 10 | Initial startup, report click nav, form route-to!, sequential nav, dirty-form route guard, cancel route change, route-denied state |
| `headless_report_tests.clj` | E2E (Datomic) | 3 | Inventory report load+filter, invoice report load, account invoices |

### 1.2 What Is NOT Tested — Coverage Gaps

#### CRITICAL GAPS

1. **Container statechart — ZERO tests**
   - `container_chart.cljc` has no Tier 1 spec (pure chart transitions)
   - `container_expressions.cljc` has no Tier 2 tests (headless Fulcro)
   - No E2E demo test for containers
   - `broadcast-to-children!`, `run-children-expr`, `resume-children-expr`, `unmount-children-expr` are all untested
   - Container is a foundational abstraction (dashboards with multiple reports) — shipping untested is risky

2. **Server-paginated report — ZERO tests**
   - `server_paginated_report.cljc` (259 lines) has no tests at any tier
   - Complex statechart with page caching, point-in-time queries, server-side sort/filter
   - 11 expression functions completely untested
   - `page-cached?` condition predicate untested

3. **Incrementally-loaded report — ZERO tests**
   - `incrementally_loaded_report.cljc` has no tests at any tier
   - Different loading strategy from standard report

#### IMPORTANT GAPS

4. **Error conditions under-tested**
   - Form save failure: Tier 1 tests that the chart transitions, but `on-save-failed-expr` never tested with actual error extraction from mutation results
   - Form load failure: Same — chart transitions tested, but `on-load-failed-expr` expression logic not verified against real errors
   - Network timeouts: Not tested at any level
   - Report load failure: Only Tier 2 verifies the chart stays in ready state; no E2E test with actual server errors

5. **Concurrent scenario testing — ZERO tests**
   - No test opens two forms simultaneously
   - No test verifies form + report coexistence
   - No test verifies container with multiple child reports running concurrently
   - Session ID collision potential never verified under concurrent load

6. **Form expression edge cases**
   - `add-row-expr`: tested in E2E only implicitly (subform existence check), never unit-tested
   - `delete-row-expr`: never tested at any tier
   - `derive-fields-ops`: never tested (the derive-fields trigger mechanism)
   - `on-change` trigger in `attribute-changed-expr`: never tested
   - `on-saved-expr` with `on-saved` transaction: never tested
   - `leave-form-expr` with `cancel-route`: never tested
   - `route-denied-expr` sync confirmation path: never tested (only async tested via E2E route guard)

7. **Report expression edge cases**
   - `postprocess-page-state`: literally a no-op (`TODO: Update post-process signature`) — dead code
   - `report-loaded` callback: never invoked anywhere
   - `before-load` option: never tested
   - `raw-result-xform`: never tested
   - Custom `compare-rows` sort: never tested
   - `row-visible?` filter: only trivially tested (no-op filter in E2E)
   - Cache expiration logic in `cache-expired?`: never tested against real timing

8. **Routing edge cases**
   - `form-route-state` exit-fn (`abandon-form!`): tested indirectly, never directly verified
   - `report-route-state` with `param-keys`: never tested
   - `back!` and `route-forward!`: never tested
   - URL sync (simulated history): test helpers exist (`create-test-app-with-url-sync`) but never used

### 1.3 The 43 Pre-existing Test Failures

Per TRACKER.md: "MockEventQueue/MockExecutionModel protocol incompatibility in report/form statechart specs. Not related to Phase 3 — missing protocol method implementations in statecharts test mocks."

**Assessment**: These failures are in the `statecharts` library's test mocks, not in this project's tests. However:
- They indicate the `statecharts` testing API may be unstable
- Users writing their own statechart tests may hit the same mock issues
- This should be resolved in the `statecharts` library before release

### 1.4 User Test Authoring

The headless plugin + `test_helpers.cljc` provides a reasonable foundation for users:
- `create-test-app` / `create-test-app-with-url-sync` are well-documented
- `settle!` helper exists for non-immediate processing
- The E2E demo tests serve as examples

**Gap**: No documentation or guide exists for "how to test your RAD statecharts app." Users would need to reverse-engineer the demo tests.

---

## 2. ECOSYSTEM GAP ANALYSIS

### 2.1 fulcro-rad-datomic

- **Exists** at `../fulcro-rad-datomic/` (confirmed via demo deps.edn `:local/root`)
- **Compatibility**: The demo port uses it successfully with Datomic local
- **Key integration point**: `authorization.cljc` provides a pass-through `redact` stub so fulcro-rad-datomic's resolver generators work without changes
- **Assessment**: Compatible. No changes needed in fulcro-rad-datomic itself.

### 2.2 fulcro-rad-semantic-ui

- **Exists** at `../fulcro-rad-semantic-ui/` (confirmed in directory listing)
- **Status**: NOT ported. The rendering system changed from map-based dispatch to multimethods:
  - `render-element` multimethod replaces `form-container-renderer`/`form-layout-renderer`
  - `fr/render-field` multimethod replaces `install-field-renderer!`
  - `rr/render-report` multimethod replaces `install-layout!`
- **Impact**: Any existing rendering plugin (semantic-ui, react-bootstrap, etc.) needs method definitions for the new multimethods
- **Assessment**: **Blocking for adoption.** Without at least one working rendering plugin, users cannot render forms/reports.

### 2.3 fulcro-rad-react-bootstrap

- **Exists** at `../fulcro-rad-react-bootstrap/`
- **Status**: NOT ported (same multimethod conversion needed as semantic-ui)

### 2.4 fulcro-rad-demo

- **Exists** at `../fulcro-rad-demo/`
- **Status**: A separate demo exists in `src/demo/` within this project. The standalone demo repo is NOT ported.
- **Porting would require**: Updating all rendering plugin references + statechart routing setup

### 2.5 Other Ecosystem Dependencies

| Project | Status | Impact |
|---------|--------|--------|
| `fulcro-rad-sql` | Exists at `../fulcro-rad-sql/` | Likely compatible (same resolver pattern as datomic) |
| `fulcro-rad-kvstore` | Exists at `../fulcro-rad-kvstore/` | Likely compatible |
| `fulcro-rad-template` | Exists at `../fulcro-rad-template/` | Needs full update for statecharts |

### 2.6 Documentation

- **No user documentation exists.** No README update, no migration guide, no API reference.
- The `CLAUDE.md` files scattered through the codebase are for AI agents, not human developers.
- **Needed before release:**
  - Migration guide from old RAD to statecharts RAD
  - Updated README with quick-start
  - Rendering plugin porting guide
  - Testing guide (how to use headless plugin)

---

## 3. DEPLOYMENT READINESS

### 3.1 CRITICAL: Statecharts Dependency is Local-Only

```clojure
;; deps.edn line 9
com.fulcrologic/statecharts {:local/root "../statecharts"}
```

The `statecharts` library is referenced as a local path, not a released Maven/Clojars artifact. The `pom.xml` lists it as:

```xml
<dependency>
    <groupId>com.fulcrologic</groupId>
    <artifactId>statecharts</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

The actual statecharts pom.xml shows version `1.4.0-RC2-SNAPSHOT`. **This is a release blocker:**
- No downstream project can consume fulcro-rad-statecharts via Clojars/Maven
- The pom.xml version (`0.1.0-SNAPSHOT`) doesn't match the actual statecharts version (`1.4.0-RC2-SNAPSHOT`)
- The statecharts library itself must be released to Clojars first

### 3.2 CRITICAL: pom.xml Version Mismatches

| Dependency | deps.edn | pom.xml | Match? |
|------------|----------|---------|--------|
| Fulcro | 3.9.3 | 3.7.0-RC3 | **NO** |
| Clojure | 1.11.4 | 1.10.3 | **NO** |
| ClojureScript | 1.10.914 | 1.10.914 | Yes |
| Statecharts | local | 0.1.0-SNAPSHOT | **NO** (actual is 1.4.0-RC2-SNAPSHOT) |
| Timbre | 6.8.0 | 6.8.0 | Yes |
| Guardrails | 1.2.16 | 1.2.16 | Yes |

The pom.xml appears to be the pre-conversion pom.xml with outdated versions. **This must be regenerated from deps.edn before release.**

### 3.3 Artifact Information

- GroupId: `com.fulcrologic` (correct)
- ArtifactId: `fulcro-rad-statecharts` (new artifact name, not `fulcro-rad`)
- Version: `0.1.0-SNAPSHOT` (appropriate for first release)
- Distribution: Clojars (correct)
- SCM tag: hardcoded commit hash `a321576cebecc9230088b2c75fc0d45dfae9fb8d` (should be dynamic)

### 3.4 Demo Alias Uses Local Paths

```clojure
:demo {:extra-deps {com.fulcrologic/fulcro-rad-datomic {:local/root "../fulcro-rad-datomic"}
                    ...}
       :override-deps {com.fulcrologic/fulcro-rad {:local/root "."}}}
```

This is acceptable for the demo alias (not shipped), but the `:override-deps` suggests that the old `fulcro-rad` artifact would conflict on the classpath. This needs documentation.

### 3.5 Clojure Version Compatibility

The CLAUDE.md notes: "malli (from statecharts dep chain) uses `random-uuid` (Clojure 1.11+). The routing ns cannot load in a 1.10.3 REPL."

- deps.edn declares Clojure 1.11.4 — this is fine
- pom.xml declares 1.10.3 — **must be updated to 1.11+**

---

## 4. PERFORMANCE ASSESSMENT

### 4.1 Statechart Overhead vs UISM

- **Session startup**: Each form/report creates a statechart session via `scf/start!`. This is comparable to `uism/begin!` — both create atoms/state.
- **Event processing**: `scf/send!` processes events through the statechart engine. This involves:
  - Finding enabled transitions
  - Evaluating conditions (simple predicates)
  - Running expression functions
  - Applying operations to Fulcro state
- **Assessment**: The overhead is proportional to statechart complexity. Form/report charts have ~8 states with simple transitions — this is trivial.

### 4.2 Session ID Encoding/Decoding

`ident->session-id` uses string concatenation + keyword creation:
```clojure
(keyword session-ns (str (namespace k) "_" (name k) "--" v))
```

`session-id->ident` uses string splitting + type parsing:
```clojure
(str/split n #"--" 2) ;; then parse-id-value
```

**Assessment**: Both are O(1) string operations. No performance concern unless called in a tight loop (they aren't — called once per form/report lifecycle).

### 4.3 N+1 Concerns

- **Form loading**: One `fops/load` per form entity. Subform data is loaded via Fulcro's join resolution — no N+1.
- **Report loading**: One `fops/load` per report. Rows are batch-loaded via the source-attribute resolver.
- **Container**: Starts N child report statecharts (one per child). Each issues its own load. This IS N loads, but they're intentional (each report is independent).
- **State map operations**: Several expressions use `fops/apply-action` which does `swap!` on the state atom. Multiple `apply-action` ops in a single expression result in multiple swaps. This is a minor inefficiency — could batch them — but not a blocking issue.

### 4.4 Server-Paginated Report

The page cache in `server_paginated_report.cljc` stores pages as vectors in Fulcro state. For reports with many pages, this could accumulate memory. No cache eviction exists.

**Assessment**: Minor concern. Most reports won't exceed a handful of cached pages.

---

## 5. PRIORITIZED RECOMMENDATIONS

### CRITICAL (Must Fix Before Release)

1. **Release the `statecharts` library to Clojars** — Without this, no downstream project can depend on fulcro-rad-statecharts. The local-path dependency is a complete deployment blocker.

2. **Regenerate pom.xml from deps.edn** — The Fulcro version mismatch (3.7.0-RC3 vs 3.9.3) and Clojure version (1.10.3 vs 1.11.4) will cause dependency resolution failures for consumers.

3. **Update statecharts version in pom.xml** — Change from `0.1.0-SNAPSHOT` to whatever version the statecharts library is released at.

### IMPORTANT (Should Fix Before Release)

4. **Add container statechart tests** — At minimum, a Tier 1 spec testing state transitions and a Tier 2 test verifying child report coordination.

5. **Add server-paginated report tests** — This is a complex statechart variant with page caching that ships completely untested.

6. **Port at least one rendering plugin** — fulcro-rad-semantic-ui or fulcro-rad-react-bootstrap. Without a rendering plugin, the library is unusable for browser apps. The headless plugin only works for tests.

7. **Write a migration guide** — Document the changes from `fulcro-rad` to `fulcro-rad-statecharts` for existing users. Cover: removed namespaces, new `defsc-form`/`defsc-report` options, routing setup, rendering plugin conversion.

8. **Fix `sfr/edit!` session targeting** — Known issue: sends to form session instead of routing session. Documented in TRACKER.md but unfixed.

9. **Resolve the 43 pre-existing test failures** — Even though they're in the statecharts mocks, they affect user confidence and user test authoring.

### SUGGESTED (Nice to Have)

10. **Add concurrent scenario tests** — Multiple forms open, form + report navigation.

11. **Test error paths end-to-end** — Save failure with actual server error, load failure with network error.

12. **Remove dead code**: `postprocess-page-state` is a no-op with a TODO. `report-loaded` callback is declared in options but never invoked.

13. **Add URL sync tests** — `create-test-app-with-url-sync` exists but is never used in any test.

14. **Document the headless testing pattern** — How users should structure their own tests using the test_helpers and headless plugin.

---

## 6. METRICS SUMMARY

| Metric | Value | Target | Status |
|--------|-------|--------|--------|
| Unit test count | 83 | — | Good |
| Assertion count | 446 | — | Good |
| Test failures | 0 | 0 | Pass |
| Pre-existing failures | 43 | 0 | Fail |
| Form chart coverage | ~90% states/transitions | 100% | Good |
| Report chart coverage | ~90% states/transitions | 100% | Good |
| Container chart coverage | 0% | 100% | **Fail** |
| Server-paginated coverage | 0% | 100% | **Fail** |
| Incrementally-loaded coverage | 0% | 100% | **Fail** |
| Expression unit tests | ~40% | >80% | Needs Work |
| E2E integration | Good (3 test files) | — | Good |
| Rendering plugins ported | 0 / 2+ | >=1 | **Fail** |
| Documentation | 0 pages | Migration guide + README | **Fail** |
| Deployment readiness | Blocked | Releasable | **Fail** |

---

## 7. KNOWN ISSUES INVENTORY

| Issue | Severity | Location | Status |
|-------|----------|----------|--------|
| Statecharts dep is local path | Critical | deps.edn:9 | Open |
| pom.xml version mismatches | Critical | pom.xml | Open |
| Container chart untested | Important | container_chart.cljc | Open |
| Server-paginated untested | Important | server_paginated_report.cljc | Open |
| `edit!` wrong session target | Important | routing.cljc / form.cljc | Open (documented) |
| `postprocess-page-state` no-op | Suggested | report_expressions.cljc:264-275 | Open |
| `report-loaded` never called | Suggested | report_expressions.cljc:284 | Open |
| No rendering plugin | Important | — | Open |
| No user documentation | Important | — | Open |
| 43 mock test failures | Important | statecharts test mocks | Open (external) |
| `scf/current-configuration` nil in headless Root | Important | Known issue per TRACKER | Open |
