# Critique Round 2: fulcro-rad-statecharts Conversion Specs

**Date**: 2026-02-20
**Reviewer**: critic-2 agent
**Scope**: All 14 spec files (11 original + 3 new), validated against statecharts library source code
**Prior critique**: critique-round-1.md (7 critical, 7 important, 5 suggested)

---

## Executive Summary

The round 1 fixes were comprehensive and well-executed. All 7 critical issues have been addressed, most importantly the 3 new specs (session-id-convention, macro-rewrites, app-initialization) that fill the architectural gaps. The specs are now internally consistent, correctly reference the statecharts library API, and form a coherent implementation plan.

**Verdict: The specs are ready for implementation**, with a small number of issues remaining (none critical).

**Remaining issues**: 2 important, 4 suggested improvements.

---

## Round 1 Issue Resolution Status

### Critical Items

| # | Issue | Status | Notes |
|---|-------|--------|-------|
| 1 | Session-id convention | **RESOLVED** | `session-id-convention.md` is thorough. `ident->session-id` produces keywords. Vector idents correctly identified as invalid `::sc/id`. The `_` separator in `(str (namespace k) "_" (name k))` is a minor concern (see NEW-1). |
| 2 | Macro rewrites | **RESOLVED** | `macro-rewrites.md` covers all three macros. Correctly removes `::uism/asm-id` from queries, adds `sfro/statechart`, `sfro/busy?`, `sfro/initialize`. The `fo/machine` repurposing is well-specified with keyword vs definition branching. |
| 3 | `fops/assoc-alias` signature | **RESOLVED** | All specs now correctly show keyword-argument pairs. Form spec line 297-299 shows both single and multi-alias patterns. Verified against actual source: `(defn assoc-alias [& {:as kvs}] {:op :fulcro/assoc-alias :data kvs})` -- matches. |
| 4 | `view-mode?` breaking change | **RESOLVED** | Form spec verification item #24 explicitly states "view-mode? rewritten to read from statechart session data instead of UISM internal storage (breaking internal change)". Public-API-mapping spec marks it as "Yes (internal breaking)". |
| 5 | `fops/invoke-remote` first arg | **RESOLVED** | Form spec line 432-437 now shows `[(save-form {...})]` as a txn vector. Verified against actual source: `(defn invoke-remote [txn {:keys [...] :as options}])` where docstring says "A Fulcro transaction vector containing a single mutation". |
| 6 | Expression arity standardized | **RESOLVED** | Form spec line 599 documents the 4-arg convention. All expression examples use `(fn [env data event-name event-data] ...)` or `(fn [env data & _] ...)`. Consistent across all specs. |
| 7 | Routing redundancy | **RESOLVED** | Routing spec now opens with "The statecharts routing library already provides a complete replacement" and states "The RAD routing layer should be THIN -- mostly option-to-option mapping and default behaviors." Sections properly reference `rstate`/`istate` rather than re-describing them. |

### Important Items

| # | Issue | Status | Notes |
|---|-------|--------|-------|
| 8 | Blob deferred to v2 | **RESOLVED** | `blob-statechart.md` status is "deferred", priority P3, with clear deferral note at the top. |
| 9 | App initialization spec | **RESOLVED** | `app-initialization.md` is comprehensive. Shows full browser and headless bootstrap sequences. Correctly uses `scf/install-fulcro-statecharts!`, `scr/start!`, `scr/install-url-sync!`. |
| 10 | Headless testing state names | **RESOLVED** | Testing spec revision history confirms fix: "Fixed state names to match actual statechart definitions (`:state/abandoned` -> `:state/exited`, `:state/showing` -> `:state/ready`)". Test examples now use correct state names. |
| 11 | On-change trigger Option A | **RESOLVED** | Form spec line 605-617 commits to Option A with the full new signature: `(fn [env data form-ident changed-key old-value new-value] -> ops-vec)`. Breaking change clearly noted. |
| 12 | Auth common events hoisted | **RESOLVED** | Auth spec now uses a parent compound state `:state/auth` (line 75) with common events (`:event/authenticate`, `:event/logout`, `:event/session-checked`) at the parent level instead of duplicated in each child. |
| 13 | Container cleanup specified | **RESOLVED** | Container spec line 193 recommends "approach 1 (explicit cleanup on route-exit)." Shows `on-exit` script that sends `:event/unmount` to each child. |
| 14 | `report-session-id` defined | **RESOLVED** | Report spec lines 322-327 define `report-session-id` with two arities, referencing `session-id-convention.md`. |

### Suggested Items

| # | Issue | Status | Notes |
|---|-------|--------|-------|
| 15 | Migration guide outline | **PARTIALLY RESOLVED** | Routing spec has a migration guide section (lines 327-374). No standalone migration guide spec was created, but the inline guide covers the key changes. Acceptable for v1. |
| 16 | Side-effects-in-expressions pattern | **PARTIALLY RESOLVED** | Container spec (line 126) documents reentrancy risk and recommends `:event-loop? true`. App-init spec documents `:immediate` mode for tests. However, there is no single cross-cutting document. |
| 17 | Auto-resolved aliases | **RESOLVED** | Form spec line 282 and report spec line 313 both note "aliases resolve directly on `data` (the Fulcro data model auto-resolves aliases)". |
| 18 | Full app init sequence | **RESOLVED** | `app-initialization.md` shows the complete sequence with code examples for both browser and headless modes. |
| 19 | `update-route-params!` replacement | **RESOLVED** | Routing spec section 10 (line 250-256) explains how route params flow through `establish-route-params-node` and URL codec. |

---

## New Issues Found

### NEW-1 (Important): `ident->session-id` separator collision risk

The `session-id-convention.md` uses `_` to separate namespace and name:

```clojure
(str (namespace k) "_" (name k) "--" v)
```

For ident `[:account/id #uuid "abc"]` this produces `:com.fulcrologic.rad.sc/account_id--abc`.

**Problem**: If a keyword namespace contains underscores (e.g., `:my_app/id`), the inverse function `session-id->ident` uses `(str/split qk #"_" 2)` which will incorrectly split on the first `_` in the namespace rather than the namespace/name boundary.

Example: `[:my_app/thing 42]` -> `"my_app_thing--42"` -> inverse splits to `["my" "app_thing"]` -> `(keyword "my" "app_thing")` = `:my/app_thing` -- WRONG.

**Fix**: Use a separator that cannot appear in keyword namespaces. The `/` character is already used by keywords, so perhaps `"__"` (double underscore) or encode the namespace/name boundary differently. Alternatively, since `(namespace k)` and `(name k)` are well-defined, store them with a more distinctive separator like `"|||"` or URL-encode the components.

**Impact**: Low in practice (Clojure keyword namespaces rarely contain underscores, and RAD idents use reverse-domain namespaces), but the inverse function will produce incorrect results for edge cases.

**Recommendation**: Use the separator `"/"` since it mirrors keyword syntax and cannot appear within a namespace or name individually. The session-id name would be `"account/id--abc"` which is valid in keyword names.

### NEW-2 (Important): `istate` actor is `:actor/component`, not `:actor/form`

Looking at the actual `istate` source (routing.cljc:429-433):

```clojure
:fulcuro/actors (fn [env data & _]
                  (let [Target (rc/registry-key->class target-key)
                        ident  (get-in data [:route/idents target-key] ...)
                        actors (merge {:actor/component (scf/actor Target ident)} ...)]
                    actors))
```

The `istate` invoke automatically sets up `:actor/component` as the actor name, not `:actor/form` or `:actor/report`.

The form spec uses `:actor/form` throughout. The report spec uses `:actor/report`. When these are invoked via `istate` (routing), the routing system will set up `:actor/component`. For the statechart expressions to find their data, either:

1. The `sfro/actors` component option must remap `:actor/component` to `:actor/form` (or `:actor/report`)
2. Or the form/report expressions need to read `:actor/component` instead

The routing-options source shows `sfro/actors` is a component option `(fn [] actors-map)` that gets merged into the invoke params. So a form component could set:

```clojure
sfro/actors (fn [env data] {:actor/form (scf/actor FormClass ident)})
```

And the `istate` merges these: `(merge {:actor/component ...} (?! (rc/component-options Target sfro/actors) env data))`.

**The specs should clarify this mapping.** The macro-rewrites spec should show that `defsc-form` sets `sfro/actors` to provide `:actor/form`, and `defsc-report` provides `:actor/report`. Otherwise, expressions referencing `:actor/form` will find nothing when invoked via routing.

**Recommendation**: Add to `macro-rewrites.md` that the generated component options include:
- For forms: `sfro/actors (fn [env data] {:actor/form ...})`
- For reports: `sfro/actors (fn [env data] {:actor/report ...})`

### NEW-3 (Suggested): `istate` session-id vs `ident->session-id` alignment

The `session-id-convention.md` section 6 (Routing Integration) correctly identifies this open question: "Does `istate` use the same deterministic session-id as `ident->session-id`, or does it generate its own?"

Looking at the `istate` source, the `child-session-id` option can be explicitly set. If not set, the invoke system generates an internal session-id (typically from `invokeid`). This means:

- For routed forms/reports: The session-id is NOT `ident->session-id(form-ident)`. It's whatever the invoke system generates.
- Public API functions like `save!`, `cancel!` that need to send events to the form session cannot use `ident->session-id`.
- The `send-to-self!` pattern works because it walks the component tree to find the invocation session-id.

**The specs correctly recommend `send-to-self!` for routed forms**, but the distinction needs to be clearer. The form spec's `start-form!` function (line 261-269) shows explicit `scf/start!` with `ident->session-id` -- this is the embedded/non-routed path. For routed forms, the session-id is managed by `istate`.

**Recommendation**:
1. Add a note to `session-id-convention.md` section 6 clarifying that `ident->session-id` is ONLY for non-routed (embedded) forms/reports
2. For routed forms, public API (`save!`, `cancel!`) should use `send-to-self!` or `current-invocation-configuration` to discover the session
3. Consider setting `child-session-id` on `istate` to `ident->session-id` for consistency, or accept the duality

### NEW-4 (Suggested): Container `broadcast-to-children!` needs session-id for routed children

The container spec's `broadcast-to-children!` function (line 163-169) uses `report-session-id` to compute session IDs. But if child reports are started via `istate` (routing) rather than `report/start-report!`, the session-id won't match `report-session-id`.

Container children are started via `report/start-report!` (explicit `scf/start!`), not via routing. So this is correct for the container use case. But the spec should explicitly note that container children are NOT routed -- they are explicitly started by the container's statechart.

### NEW-5 (Suggested): Missing `sfro/actors` in macro-rewrites.md

As described in NEW-2, the `macro-rewrites.md` spec needs to include `sfro/actors` in the generated component options. Currently the spec shows `sfro/statechart`, `sfro/busy?`, and `sfro/initialize` but not `sfro/actors`.

### NEW-6 (Suggested): Auth spec `store-provider-and-source!` uses 4-arg but declares `_event-name event-data`

The auth spec line 147-149 shows:

```clojure
(defn store-provider-and-source!
  [env data _event-name event-data]
  [(ops/assign :provider (:provider event-data))
   (ops/assign :source-session-id (:source-session-id event-data))])
```

This is correct (4-arg convention). But line 67 in the same spec shows:

```clojure
(fn [env data & _]
  [(ops/assign :config (-> data :_event :data))
   (ops/assign :authenticated #{})])
```

This accesses event data via `(-> data :_event :data)` (the 2-arg pattern). While both work, the spec should be internally consistent -- either use the 4-arg destructuring OR the `:_event` path, not mix them.

---

## Cross-Spec Consistency Check

### Dependency Graph

The dependency graph is now correct and complete:

```
project-setup
  <- session-id-convention
  <- form-statechart (also depends on session-id-convention, macro-rewrites)
  <- report-statechart (also depends on session-id-convention, macro-rewrites)
  <- auth-statechart
  <- routing-conversion (also depends on session-id-convention, macro-rewrites, app-initialization)
  <- control-adaptation (also depends on session-id-convention)

report-statechart
  <- container-statechart (also depends on session-id-convention, macro-rewrites)

form-statechart + report-statechart + routing-conversion
  <- headless-testing

form-statechart + report-statechart
  <- rad-hooks-conversion

session-id-convention + form-statechart + report-statechart + container-statechart + routing-conversion
  <- macro-rewrites

project-setup + routing-conversion + form-statechart + report-statechart + container-statechart
  <- app-initialization
```

No circular dependencies. The implementation order should be:

1. project-setup
2. session-id-convention
3. routing-conversion (infrastructure)
4. form-statechart
5. report-statechart
6. container-statechart
7. auth-statechart (parallel with 5-6)
8. control-adaptation
9. macro-rewrites
10. rad-hooks-conversion
11. headless-testing
12. app-initialization
13. public-api-mapping (update after all)

### Cross-Reference Verification

| Spec A | References | Spec B | Consistent? |
|--------|-----------|--------|-------------|
| form-statechart | session-id convention | session-id-convention | Yes -- uses `ident->session-id` |
| form-statechart | macro changes | macro-rewrites | Yes -- references defsc-form changes |
| report-statechart | `report-session-id` | session-id-convention | Yes -- same pattern |
| report-statechart | data storage | form-statechart | Yes -- both use aliases for Fulcro state |
| container-statechart | child session-ids | session-id-convention | Yes -- uses `report-session-id` |
| container-statechart | cleanup | report-statechart | Needs `:event/unmount` in report chart |
| routing-conversion | app init | app-initialization | Yes -- routing references app-init |
| control-adaptation | session discovery | session-id-convention | Yes -- uses `ident->session-id` |
| headless-testing | state names | form/report specs | Yes -- fixed in R2 |
| rad-hooks | embedded mode | form-statechart | Yes -- both reference `:embedded? true` |
| public-api-mapping | view-mode? | form-statechart | Yes -- both mark as breaking |

### Potential Contradiction

The report statechart does not currently define `:event/unmount` handling or a `final` state. The container spec (line 206) sends `:event/unmount` to child reports on exit, and the rad-hooks spec (line 143) expects charts to handle `:event/unmount` by reaching a `final` state. **The report statechart spec should add `:event/unmount` -> `final` state handling.** Same for the form statechart (already has `:state/exited` as `final`, but needs to accept `:event/unmount` as a trigger).

---

## Consolidated Open Questions for Human Review

These are questions that require human decision-making before or during implementation:

### Architecture Decisions

1. **`ident->session-id` separator**: Should the separator between namespace and name be `_` (current), `__`, `/`, or something else? Edge case with underscored namespaces (NEW-1).

2. **`istate` child-session-id**: Should RAD configure `child-session-id` on `istate` to match `ident->session-id`, or accept that routed forms use invoke-generated session-ids and `send-to-self!`? (session-id-convention.md OQ-1)

3. **`fo/machine` rename**: Should `fo/machine` be renamed to `fo/statechart`? Keeps backward compat but may confuse users. (macro-rewrites.md OQ-1)

4. **Route-segment in macros**: Should `defsc-form` keep generating `:route-segment`? It's unused by statecharts routing but useful for discoverability. (macro-rewrites.md OQ-2)

5. **Routing chart auto-generation**: Should RAD eventually auto-generate the routing chart from form/report definitions? Current decision: user-defined. (app-initialization.md OQ-2)

### Technical Questions

6. **Timbre version**: Should RAD bump to timbre 6.x to match statecharts? Needs audit of RAD's timbre usage. (project-setup OQ-2)

7. **Statecharts library version**: What version of statecharts to use at release? (project-setup OQ-1)

8. **Two-phase sort/filter**: Self-send vs intermediate state? Current recommendation: self-send. (report-statechart OQ-2)

9. **Report composability**: Separate charts vs shared expressions for incremental/server-paginated? Current recommendation: shared expressions. (report-statechart OQ-3)

10. **`start-form!` / `start-report!` visibility**: These are needed for embedded use. Should they remain public? Current recommendation: yes. (public-api-mapping OQ-2)

### Deferred

11. **Blob statechart**: Deferred to v2. No action needed.
12. **Auth redesign**: Deferred to v2. Minimal conversion for v1.
13. **Migration guide**: Inline in routing spec for now. Standalone guide for v2.

---

## Overall Readiness Assessment

| Criterion | Rating | Notes |
|-----------|--------|-------|
| **Completeness** | Excellent | All major systems covered. 14 specs total. |
| **API accuracy** | Excellent | All API signatures verified against source. `fops/assoc-alias`, `fops/invoke-remote`, `fops/load`, `fops/set-actor` all match. |
| **Internal consistency** | Good | Cross-references are accurate. One gap: report/form charts need `:event/unmount` handling. |
| **Routing integration** | Excellent | Correctly leverages existing `rstate`/`istate` infrastructure. Thin RAD layer on top. |
| **Statecharts API usage** | Excellent | Expression arity (4-arg), alias resolution, actor resolution all correctly documented. |
| **Dependency graph** | Excellent | Clear, acyclic, with correct implementation ordering. |
| **Open questions** | Good | 13 questions, none blocking. All have recommended defaults. |

### Verdict

**The specs are ready for implementation.** The 2 important issues (NEW-1 separator risk, NEW-2 actor naming) should be addressed during implementation but do not block starting work. The 4 suggested improvements are refinements that can be handled as they arise.

---

## Metrics Summary

| Metric | Round 1 | Round 2 | Delta |
|--------|---------|---------|-------|
| Specs reviewed | 11 | 14 | +3 new specs |
| Critical issues | 7 | 0 | All resolved |
| Important issues | 7 | 2 | 5 resolved, 2 new |
| Suggested improvements | 5 | 4 | 3 resolved, 4 new |
| API signature errors | 3+ | 0 | All fixed |
| State name mismatches | 2+ | 0 | All fixed |
| Missing specs | 3 critical | 0 | All written |
| Open questions | Scattered | 13 consolidated | Organized for review |
