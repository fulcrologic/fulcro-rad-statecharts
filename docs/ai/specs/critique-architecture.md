# Architecture Critique: Statechart Design & Fulcro Principles

**Date**: 2026-02-21
**Reviewer**: arch-critic
**Scope**: form_chart, form_expressions, report_chart, report_expressions, container_chart, container_expressions, routing, session, public API (form.cljc, report.cljc)

---

## Executive Summary

The statechart conversion is architecturally sound. The charts model real user-observable process states, Fulcro's normalized client DB remains the source of truth, and the separation between chart definitions and expression functions is clean. The design follows the core principle: "statecharts model process, Fulcro models data."

**Quality**: Good
**Key Strengths**: Clean state separation, good chart/expression factoring, proper use of aliases
**Key Concerns**: Side effects inside `fops/apply-action`, container uses imperative side effects instead of statechart invocations, session ID complexity is borderline

---

## 1. Principle Adherence

### 1.1 "Modelling the data is king" — PASS

The statecharts correctly avoid duplicating Fulcro's data model. Data lives in the normalized client DB accessed via actors and aliases. Session-local data (`ops/assign`) is used only for process-tracking concerns:

- `:options` (form startup configuration)
- `:abandoned?` (process flag for leaving)
- `:last-load-time`, `:raw-items-in-table` (cache staleness tracking)
- `:desired-route` (deferred route for async confirmation)
- `:point-in-time` (server-paginated temporal anchor)
- `:loading-complete?` (incremental load progress flag)

All of these are legitimately process state, not data model concerns.

**Minor concern**: `:options` is stored at chart startup via `ops/assign` and carried for the chart's lifetime. This is correct (the options represent the process configuration, not domain data), but the `store-options` expression conflates two sources — `event-data` and `(:options data)` — with a fallback chain. A comment explaining this would help.

### 1.2 "Statecharts are best for modelling process" — PASS

All states map to real user-observable conditions:

| Chart | States | User-Observable? |
|-------|--------|-------------------|
| Form | initial, creating, loading, load-failed, editing, saving, leaving, exited | Yes — each has distinct UI behavior |
| Report | initializing, loading, processing, ready, sorting, filtering | Yes — loading spinners, busy indicators |
| Container | initializing, ready | Yes — minimal and appropriate |
| Server-paginated | initializing, loading-page, processing-page, ready | Yes — page-by-page loading |
| Incremental | initializing, loading, processing-chunk, finalizing, ready, sorting, filtering | Yes — progressive loading |

No phantom states. No over-engineering. The form chart's `:state/leaving` as a transient state (on-entry + immediate transition) is the right pattern for cleanup-before-exit.

### 1.3 "No side effects in UI code other than signals to statecharts" — MOSTLY PASS

Public API functions (`save!`, `cancel!`, `undo-all!`, `add-child!`, `start-report!`, etc.) correctly delegate to `scf/send!` or `scf/start!`. UI components send events; statecharts handle everything else.

**Exception**: `form/standard-controls` defines `:action` lambdas that call `cancel!`, `undo-all!`, `save!` directly. This is acceptable — they're event dispatchers, not side-effect performers. The side effects happen inside the statechart.

### 1.4 RAD reduces boilerplate via declarative attributes — PASS

Forms, reports, and containers remain declarative. The macro rewrites (`defsc-form`, `defsc-report`, `defsc-container`) generate the statechart plumbing automatically. Users declare `fo/attributes`, `ro/columns`, etc. and get a working statechart for free. Custom charts can be provided via `fo/statechart` / `ro/statechart`.

### 1.5 W3C SCXML semantics — PASS

Charts use proper SCXML patterns:
- Eventless transitions for decision states (`:initial` in form chart)
- Document-order evaluation for conditional transitions (save: valid? first, fallback second)
- `on-entry`/`on-exit` for state lifecycle
- `handle` for internal (targetless) transitions
- No direct state manipulation — all through events and transitions

---

## 2. Expression Function Quality

### 2.1 Overall Pattern

The 4-arg convention `[env data event-name event-data]` is clean and consistent. Destructuring is clear. Functions return operation vectors. This is a good pattern.

### 2.2 Functions That Do Too Much

**`on-loaded-expr` (form_expressions.cljc:131-200)** — IMPORTANT

This is the most complex expression at ~70 lines. It handles:
1. Clearing server errors
2. Auto-creating to-one subform entities
3. Adding form config
4. Marking fields complete
5. Initializing UI props with denormalization, component resolution, and merge

The `ui-props-ops` section (lines 170-194) contains a 25-line closure with nested `reduce`, `keep`, `set/difference`, and conditional component merging. This is too much logic for a single expression function.

**Recommendation**: Extract `build-autocreate-ops` and `build-ui-props-ops` as separate named functions. The expression should compose them, not inline them.

**`attribute-changed-expr` (form_expressions.cljc:237-276)** — ACCEPTABLE

Complex but necessarily so — it handles value normalization for ref/many combinations, error clearing, field completion, on-change triggers, and derive-fields. Each concern is already delegated to a helper. The validation warnings (lines 269-275) are debug-only, which is correct.

**`initialize-params-expr` (report_expressions.cljc:77-111)** — ACCEPTABLE

Single `fops/apply-action` with a multi-step reduce. The complexity is inherent to the control parameter initialization problem. Already well-structured.

### 2.3 Guards and Conditions

Guards are properly pure:
- `create?` — reads session data, returns boolean
- `form-valid?` — reads state map, calls `valid?`, returns boolean
- `should-run-on-mount?` — reads component option, returns boolean
- `cache-expired?` — reads timestamps and table counts, returns boolean
- `loading-complete?` — reads session data flag, returns boolean
- `page-cached?` — reads state map, returns boolean

All are side-effect-free.

---

## 3. Side Effects Analysis — CRITICAL FINDING

### 3.1 Side Effects Inside `fops/apply-action` Closures

Several expression functions embed imperative side effects inside `fops/apply-action` closures that are supposed to be pure state transformations:

**`on-saved-expr` (form_expressions.cljc:364-371)**:
```clojure
(fops/apply-action
  (fn [state-map]
    (let [app (:fulcro/app env)]
      (when app
        (rc/transact! app txn)))  ;; SIDE EFFECT inside apply-action
    state-map))
```

**`on-save-failed-expr` (form_expressions.cljc:395-400)**: Same pattern — `rc/transact!` inside `apply-action`.

**`leave-form-expr` (form_expressions.cljc:433-455)**: Two side effects — `rc/transact!` for on-cancel AND `requiring-resolve` + `route-to!` for routing.

**`continue-abandoned-route-expr` (form_expressions.cljc:491-497)**: `requiring-resolve` + `force-continue-routing!` inside `apply-action`.

**Why this is problematic**:
1. `fops/apply-action` runs inside `swap!` on the Fulcro state atom. Side effects inside `swap!` can execute multiple times if there's contention (CAS retry).
2. It violates the statechart principle that actions return operations, not perform effects.
3. These side effects are invisible to the statechart — they can't be tested, replayed, or intercepted.
4. The `(fn [state-map] ... state-map)` pattern (return unchanged state-map) is a red flag — it means the function exists purely for its side effects.

**Correct approach**: These should use `fops/invoke-remote` for server-bound transactions, or the statechart event queue for routing. For `on-saved`/`on-cancel` transactions, consider `fops/apply-mutations` or a dedicated operation type.

**Severity**: IMPORTANT (not critical only because the current swap! implementation in Fulcro is not retry-based, but this is still architecturally wrong)

### 3.2 Container Side Effects via `doseq`

**`initialize-params-expr` (container_expressions.cljc:70-73)**:
```clojure
(doseq [[id c] children]
  (report/start-report! app c {...}))
```

**`broadcast-to-children!` (container_expressions.cljc:79-82)**:
```clojure
(doseq [[id child-class] (id-child-pairs ContainerClass)]
  (scf/send! app (child-report-session-id child-class id) event ...))
```

These are imperative side effects executed directly in expression functions, not through the statechart operation system. The CLAUDE.md documents this as "Option A (side-effect)" — a conscious choice.

**Why this matters**: Child statechart lifecycle should ideally be managed via the `invoke` element, which is the SCXML-standard way to spawn/manage child sessions. Using `invoke` would give:
- Automatic cleanup when the parent state exits
- Proper error propagation
- Session lifecycle tied to state lifecycle

**Counter-argument**: The current approach works, and `invoke` has complexity around `srcexpr` and actor resolution (as documented in the statecharts skill). This is a pragmatic tradeoff.

**Severity**: SUGGESTED (works correctly, but doesn't use the statechart's own composition mechanism)

---

## 4. Chart Design Analysis

### 4.1 Form Chart — GOOD

The form chart is well-designed:
- `:initial` as a decision state (eventless transitions) is the SCXML way to branch
- `:state/creating` → `:state/editing` via immediate transition (synchronous setup)
- `:state/loading` → `:state/editing` via events (async)
- `:state/saving` returns to `:state/editing` on both success and failure (correct — user stays on form)
- `:state/leaving` → `:state/exited` via immediate transition (cleanup pattern)
- Global `:event/exit` available in multiple states for forced cleanup

**One omission**: No timeout on `:state/loading` or `:state/saving`. A stuck load or save will leave the chart in that state indefinitely. Consider adding `send-after` for a timeout event.

### 4.2 Report Chart — GOOD

Clean design with proper intermediate states:
- `:state/sorting` and `:state/filtering` as observable intermediate states enable UI feedback (`:busy?` flag)
- `:state/processing` as a separate state from `:state/loading` is correct — it separates the async load from the synchronous transform pipeline
- The resume/cache pattern (two transitions on `:event/resume` with `cache-expired?` guard) is textbook statechart

**Observation**: The `:state/sorting` and `:state/filtering` states set busy on entry and clear it on the eventless transition out. Since the eventless transition fires in the same microstep, the busy flag may never actually be visible to the UI. This depends on whether the Fulcro integration batches state updates.

### 4.3 Container Chart — ADEQUATE

The container is simple (2 states, 3 events). This is appropriate — containers are coordinators, not complex processes.

**Issue**: `on-exit` of `:state/ready` sends `:event/unmount` to children, but there's no transition out of `:state/ready`. If the statechart is stopped externally, `on-exit` fires. But the children's report statecharts don't handle `:event/unmount` — they have no such transition. This is a no-op side effect.

### 4.4 Report Variants — GOOD

Both server-paginated and incrementally-loaded variants are well-structured:
- Server-paginated has page caching with `page-cached?` guard — elegant
- Incremental loading uses `loading-complete?` flag via `ops/assign` + eventless transitions — correct pattern for "poll until done"
- Both reuse `report_expressions` functions for shared behavior (init, filter, sort, paginate)

---

## 5. Session Management

### 5.1 `ident->session-id` Pattern

The encoding `[:account/id #uuid "abc"]` → `:com.fulcrologic.rad.sc/account_id--abc` is:
- Deterministic (same ident → same session-id)
- Collision-free (the `--` separator is unambiguous)
- Bidirectional (can parse back to ident)

**Concern**: The stringification of ID values means that different types with the same string representation could collide. For example, the string `"42"` and the integer `42` would both produce `--42`. The `parse-id-value` function handles round-tripping correctly for known types (UUID, int, keyword), but an application using string IDs that look like numbers would get misinterpreted.

**Severity**: LOW — RAD applications almost exclusively use UUID or integer primary keys. String PKs are rare and numeric-string PKs are essentially nonexistent.

### 5.2 Complexity Assessment

The session ID machinery adds ~95 lines of code across `session.cljc` plus ~5 lines at each call site. The alternative (passing session IDs explicitly) would require threading them through the UI, which is worse.

**Verdict**: Worth the complexity. The deterministic derivation from idents is the right choice for RAD, where component identity is already established via idents.

---

## 6. Separation of Chart and Expressions

### 6.1 Current Structure

```
form_chart.cljc         — Chart definition (pure structure, ~120 lines)
form_expressions.cljc   — Expression functions (~580 lines)
form.cljc               — Public API + legacy code (~1400 lines)
```

This is a good separation:
- Charts are declarative and readable (you can see the full state machine in one screen)
- Expressions are testable in isolation
- The public API is a thin event-dispatch layer

### 6.2 Circular Dependency Handling

`form_expressions.cljc` uses `requiring-resolve` to access `form.cljc` functions like `default-state`, `optional-fields`, `valid?`. This avoids the circular dependency (form.cljc requires form_chart.cljc requires form_expressions.cljc).

**Issue**: `requiring-resolve` is a CLJ-only construct. The `#?(:clj ...)` reader conditional is not used here. Since the files are `.cljc`, this will fail in CLJS.

**Wait — checking**: `requiring-resolve` was added in Clojure 1.10 and works at runtime. In CLJS, this function doesn't exist. However, the code is in a `.cljc` file without reader conditionals.

**Upon further review**: `requiring-resolve` is available in ClojureScript via `cljs.core/requiring-resolve` as of recent versions, but its behavior differs. If this is only used in expression functions that run at runtime (not at macro expansion time), and if the statechart integration ensures the namespaces are loaded before expressions fire, this may work.

**Severity**: IMPORTANT — needs verification that `requiring-resolve` works correctly in CLJS for these call sites. If not, the expressions would fail silently or throw at runtime.

---

## 7. Known Issues Assessment

### 7.1 `sfr/edit!` Wrong Session

The routing module's `edit!` function routes to a form component. The form's statechart is started by `form-route-state`'s `entry-fn`, which calls `form/start-form!`. The session ID is derived from the form's ident in `start-form!`.

The potential issue: if routing passes a form component class but the ident isn't established until `start-form!` computes it from the `id` parameter, there's a window where the session doesn't exist yet. This shouldn't cause a "wrong session" — it would cause a "no session" error.

**Assessment**: The `edit!` function in `routing.cljc` correctly passes `{:id id :params params}` as event data. The `form-route-state` entry function extracts `id` from this event data and calls `start-form!` with the correct class and ID. The session ID is derived deterministically. This should work correctly.

If the "wrong session" issue persists, it's likely a timing issue where the routing event data doesn't propagate correctly to the entry function, or where the form ident and session ID are computed from different sources.

### 7.2 `scf/current-configuration` nil in Headless

This is documented as a race condition in the `headless-load-callback` spec. The `(constantly snapshot)` pattern was the root cause, and it was fixed. If the issue persists after the fix, it would indicate a deeper problem with working memory synchronization in the headless (synchronous) execution model.

---

## 8. Anti-Patterns Found

| Pattern | Location | Impact | Recommendation |
|---------|----------|--------|----------------|
| Side effects in `apply-action` | form_expressions: on-saved, on-save-failed, leave-form, continue-abandoned-route | Architectural violation, potential double-execution | Create dedicated operation types or use event queue |
| Imperative child management | container_expressions: initialize-params, broadcast-to-children | Bypasses statechart composition | Consider `invoke` for child lifecycle (future work) |
| `(fn [sm] ... sm)` identity pattern | form_expressions: ~4 locations | Code smell indicating side-effect-only "action" | Extract side effects into proper operations |
| Undeclared CLJS compat for `requiring-resolve` | form_expressions:92 | May fail in CLJS runtime | Add reader conditional or restructure deps |
| `postprocess-page-state` is a no-op | report_expressions:264-275 | Dead code, TODO comment | Either implement or remove |

---

## 9. Recommendations

### Critical

(None — the architecture is fundamentally sound)

### Important

1. **Extract side effects from `apply-action` closures** — The `rc/transact!` and `route-to!` calls inside `(fn [state-map] ... state-map)` closures should be moved to a proper statechart action pattern. Options:
   - Use `fops/invoke-remote` for server-bound transactions
   - Use `scf/send!` to the routing statechart instead of `route-to!`
   - Create a `fops/schedule-transaction` operation that queues the txn for after the state transition

2. **Verify `requiring-resolve` in CLJS** — Test that `form_expressions.cljc` works correctly when compiled to ClojureScript. If `requiring-resolve` doesn't work in CLJS, restructure to avoid the circular dependency (e.g., pass the needed functions via `:extra-env` at chart startup).

3. **Decompose `on-loaded-expr`** — Extract `build-autocreate-ops` and `build-ui-props-ops` into named helper functions. The current 70-line expression function is too dense.

4. **Remove or implement `postprocess-page-state`** — Currently a no-op with a TODO comment. Either implement the post-process hook or remove the dead code.

### Suggested

1. **Add load/save timeouts** — Form chart's `:state/loading` and `:state/saving` have no timeout. Consider `send-after` for a configurable timeout event (e.g., `:event/timeout` after 30s).

2. **Document the `busy?` flag visibility** — Clarify whether the busy flag set in `:state/sorting`/`:state/filtering` is actually visible to the UI given that eventless transitions fire in the same microstep.

3. **Consider `invoke` for container children** — Using statechart invocations instead of imperative `doseq` + `start-report!` would align with SCXML composition patterns and provide automatic cleanup.

4. **Add `:event/unmount` handling to report chart** — The container sends `:event/unmount` to children on exit, but the report statechart doesn't handle it. Either add a transition or remove the dead event.

5. **Session ID collision documentation** — Document the type ambiguity edge case (string "42" vs integer 42) in the session module, even though it's extremely unlikely in practice.

---

## 10. Metrics Summary

| Metric | Value | Assessment |
|--------|-------|------------|
| States per chart (form) | 8 | Appropriate — maps to real lifecycle |
| States per chart (report) | 5 | Lean and correct |
| States per chart (container) | 2 | Minimal — appropriate for coordinator |
| Expression functions (form) | 18 | Reasonable for form complexity |
| Expression functions (report) | 15 | Well-decomposed |
| Side effects in expressions | 6 locations | Too many — should be 0 |
| Pure guards | 6 | All correct |
| Session data keys | ~7 per chart | Appropriate process tracking |
| Lines of chart definition | ~120 (form), ~85 (report), ~30 (container) | Readable in one screen |
| Alias definitions | ~3 (form), ~12 (report) | Appropriate data binding |
