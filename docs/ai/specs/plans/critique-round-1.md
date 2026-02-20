# Critique Round 1: fulcro-rad-statecharts Conversion Specs

**Date**: 2026-02-20
**Reviewer**: critic agent
**Scope**: All 11 spec files, validated against statecharts library source code

---

## Executive Summary

The specs are ambitious and generally well-structured, but contain several critical inaccuracies when compared against the actual statecharts library API, architectural gaps in how the pieces compose, and missing coverage for important edge cases. The most serious issues are:

1. **`fops/assoc-alias` signature is wrong throughout all specs** -- the actual API uses keyword-argument pairs, not positional args
2. **Expression function arity is inconsistent** -- specs mix 2-arg and 4-arg conventions without clarity on which to use
3. **The routing spec underestimates the complexity** of replacing RAD's routing -- `rstate`/`istate` already handle most of what the specs propose to reimplement
4. **No spec covers the `defsc-form` / `defsc-report` macro rewrites** -- these are the most impactful changes for downstream users
5. **The blob spec converts something that has no UISM dependency** -- questionable ROI

---

## Per-Spec Feedback

### 1. project-setup.md

**Quality**: Good. This is the most straightforward spec.

**Issues**:
- **Timbre version conflict is real and under-analyzed.** The spec notes the 4.x -> 6.x jump but doesn't specify what RAD's actual timbre usage looks like. Timbre 6.x changed the logging macro signatures (particularly around `spy` and config). A full audit of RAD's timbre usage is needed before deciding.
- **Missing: guardrails configuration.** The statecharts library uses guardrails with malli (`com.fulcrologic.guardrails.malli.core`), while RAD uses the spec-based guardrails (`com.fulcrologic.guardrails.core`). These are different namespaces. Both should work side-by-side, but this should be verified.
- **Open question about version**: `0.1.0-SNAPSHOT` is correct. Tracking the fork version would cause confusion.

**Recommended changes**: Add a timbre usage audit task. Add guardrails malli/spec coexistence verification.

---

### 2. form-statechart.md

**Quality**: Thorough analysis of the current UISM, good state mapping. Several API inaccuracies.

**Critical Issues**:

1. **`fops/assoc-alias` signature is WRONG.** The spec shows:
   ```clojure
   [(fops/assoc-alias :server-errors [{:message "error"}])]
   ```
   But the actual signature is keyword-argument pairs (variadic `& {:as kvs}`):
   ```clojure
   [(fops/assoc-alias :server-errors [{:message "error"}])]
   ```
   Wait -- this actually happens to work because a single key-value pair works as kwargs. But the spec also shows:
   ```clojure
   [(fops/assoc-alias :server-errors [])]
   ```
   Which is also fine as kwargs. **However**, the conceptual framing is wrong. The spec treats it as `(assoc-alias key value)` -- it's actually `(assoc-alias & {:as kvs})` meaning you can pass multiple at once: `(assoc-alias :server-errors [] :route-denied? false)`. The specs should show the multi-key pattern.

2. **Expression function arity inconsistency.** The spec says expressions are `(fn [env data event-name event-data])` (4-arg). The actual `install-fulcro-statecharts!` docstring says:
   > "The execution model for Fulcro calls expressions with 4 args: env, data, event-name, and event-data."

   This is correct. BUT the routing code uses `(fn [env data & _])` patterns extensively. The spec's `attribute-changed-expr` example shows 4-arg, which is correct. **However**, the spec doesn't mention that the `_event` data is ALSO available in `data` under the `:_event` key. The 4-arg form is a convenience. The spec should note both access patterns.

3. **`start!` session-id handling.** The spec proposes using form-ident as session-id:
   ```clojure
   :session-id form-ident  ;; e.g. [:account/id #uuid "..."]
   ```
   The actual `start!` function accepts `::sc/id` type for session-id. Looking at the guardrails spec: `[:session-id {:optional true} ::sc/id]`. The `::sc/id` spec needs to be checked -- if it requires a keyword, vectors won't work. **This is a critical unknown that could break the entire session-id strategy.**

4. **`fops/load` signature mismatch.** The spec shows:
   ```clojure
   [(fops/load form-ident FormClass
      {::sc/ok-event    :event/loaded
       ::sc/error-event :event/failed})]
   ```
   The actual signature is:
   ```clojure
   (defn load [query-root component-or-actor {:keys [] :as options}])
   ```
   The ok/error event keys are correct (`::sc/ok-event`, `::sc/error-event`). The first arg `query-root` can be a keyword or ident, and the second is a component class or actor keyword. The spec's usage looks correct.

5. **`fops/invoke-remote` signature mismatch.** The spec shows:
   ```clojure
   [(fops/invoke-remote `save-form
      {:params ...
       :returning :actor/form
       :ok-event :event/saved
       :error-event :event/save-failed})]
   ```
   The actual signature is:
   ```clojure
   (defn invoke-remote [txn {:keys [target returning ok-event error-event ...]}])
   ```
   The first arg `txn` should be "A Fulcro transaction vector containing a single mutation", but the spec passes a symbol. The actual API expects a txn vector like `[(save-form {...})]` or possibly just the mutation call. **Need to verify whether a bare symbol works or if it needs to be a txn vector.** The spec should show the correct format.

6. **`:state/creating` has an eventless transition to `:state/editing`**, but the on-entry script is synchronous and sets up defaults. The eventless transition fires after on-entry completes. This is correct statechart semantics, but the spec should explicitly note that eventless transitions are evaluated AFTER entry actions complete.

7. **Missing: `view-mode?` function conversion.** The current `view-mode?` reads deeply into UISM internal storage:
   ```clojure
   (-> master-form comp/props ::uism/asm-id (get ...) ::uism/local-storage :options :action)
   ```
   This is completely UISM-specific. The spec lists `view-mode?` as "unchanged" in the query functions section, but it absolutely requires rewriting. It needs to read from statechart session data instead.

8. **Missing: `defsc-form` macro rewrite.** The macro generates `:will-enter`, `:will-leave`, `:allow-route-change?`, query with `[::uism/asm-id '_]`, etc. None of the specs detail exactly what the macro should generate in the new system. This is the single most impactful omission.

9. **The `:state/asking-to-discard-changes` removal is premature.** The spec says it's removed because `route-denied?` alias handles it. But the async confirmation pattern (where a modal asks "discard changes?") currently relies on the UISM being in a distinct state. With statecharts, this could be a child state of `:state/editing`, which would be cleaner than removing it entirely. The spec should address the async confirm UX pattern explicitly.

10. **`on-change` trigger conversion is under-specified.** The spec identifies three options but doesn't commit. The on-change trigger is used by real applications and its conversion needs a definitive answer. Option A (clean break) is correct but needs the full new signature specified.

**Recommended changes**: Fix all API signatures. Address `view-mode?`. Add `defsc-form` macro spec. Specify on-change trigger contract.

---

### 3. report-statechart.md

**Quality**: Good coverage of all three report variants. Good alias mapping.

**Issues**:

1. **`:state/processing` as a transient state with eventless transition is problematic.** The spec proposes:
   ```clojure
   (state {:id :state/processing}
     (on-entry {} (script {:expr process-loaded-data-expr}))
     (transition {:target :state/ready}))
   ```
   An eventless transition fires immediately after on-entry. This means `process-loaded-data-expr` must be synchronous and complete before the transition. For large datasets, filter/sort can be expensive. The two-phase pattern (set busy, yield to render, then process) exists in the current UISM for a reason. The spec acknowledges this for sort/filter but not for the initial load processing.

2. **Data storage strategy is unclear.** The spec stores report data (raw-rows, filtered-rows, sorted-rows, current-rows) via aliases that point into Fulcro state at actor paths. But it also mentions `ops/assign` for page cache in server-paginated reports. **This creates a split brain**: some data in Fulcro state (via aliases), some in statechart session data (via assign). The spec should define a consistent strategy.

3. **`report-session-id` function is referenced but never defined.** Multiple specs reference it. Its implementation determines whether `scf/send!` can find the right session. This needs to be specified: is it `(comp/get-ident report-class {})` (the current UISM pattern), or something else?

4. **Missing: `defsc-report` macro rewrite.** Same issue as `defsc-form` -- the macro generates query, ident, will-enter, initial-state. None of this is specified for the new system.

5. **The incrementally-loaded report spec is thin.** It says "same events as standard report" for `:state/ready` but doesn't enumerate them. This variant needs the full event list.

6. **Missing: `ro/machine` override semantics.** The spec says `ro/machine` becomes a statechart registry key, but doesn't explain how a user registers their custom chart, or how the `defsc-report` macro knows to use it.

**Recommended changes**: Define `report-session-id`. Detail data storage strategy. Add `defsc-report` macro spec. Flesh out incremental report.

---

### 4. container-statechart.md

**Quality**: Adequate for the simpler container system.

**Issues**:

1. **Side effects in expression functions.** The spec proposes calling `scf/start!` and `scf/send!` as side effects within expression functions:
   ```clojure
   (fn [env data]
     (doseq [...] (report/start-report! app child-class ...))
     [...ops...])
   ```
   This is a pattern question: should expression functions have side effects? The statecharts model prefers operations (returned data) over side effects. `scf/send!` from within an expression during event processing could cause reentrancy issues if the event queue is in `:immediate` mode. **The spec should note this risk and recommend `:event-loop? true` or at minimum acknowledge the ordering implications.**

2. **Missing: `defsc-container` macro rewrite.** Same pattern as form/report.

3. **Child cleanup strategy is weak.** "Lazy cleanup" means statechart sessions persist indefinitely. The statecharts library GC's sessions that reach a final state, but report sessions never reach final state. This means memory will grow unboundedly as users navigate. **The spec should specify explicit cleanup on route-exit.**

**Recommended changes**: Address reentrancy risk. Specify cleanup. Add macro spec.

---

### 5. auth-statechart.md

**Quality**: Good analysis, correctly identifies the cross-chart communication challenge.

**Issues**:

1. **Duplicated event handling across states.** The proposed chart has `:event/authenticate`, `:event/logout`, `:event/session-checked` duplicated in `:state/idle`, `:state/gathering-credentials`, and `:state/failed`. This is a classic anti-pattern -- these should be hoisted to a parent compound state:
   ```clojure
   (state {:id :state/auth :initial :state/idle}
     ;; Global events at parent level
     (handle :event/session-checked handle-session-checked!)
     (on :event/logout :state/idle (script {:expr handle-logout!}))
     ;; ... children ...
     (state {:id :state/idle} ...)
     (state {:id :state/gathering-credentials} ...)
     (state {:id :state/failed} ...))
   ```

2. **The `reply-authenticated!` side effect** (sending an event to the source session) is a critical coordination point. The spec mentions `scf/send!` but doesn't detail HOW the source session-id gets to the auth chart. With UISM, it was `source-machine-id`. With statecharts, the source could be a routing session, a form session, etc. The spec should specify the full event data contract for `:event/authenticate`.

3. **Multiple actors not fully addressed.** The current auth machine has actors for each authority provider. The spec's data-model only stores metadata, but the actual UISM uses `uism/swap-actor!` to dynamically change which component serves as `:actor/auth-dialog`. The spec mentions `fops/set-actor` but doesn't show the full actor lifecycle.

4. **"NOT PRODUCTION-READY" presents an opportunity.** The spec asks whether to redesign. **Recommendation**: Do a minimal conversion now (keep same behavior), but flag it as a candidate for v2 redesign. Don't let scope creep here block the main conversion.

**Recommended changes**: Hoist common events. Specify event data contracts. Show actor swap pattern.

---

### 6. routing-conversion.md

**Quality**: Best-written spec. Good comparison tables. But overcomplicates some things.

**Critical Issues**:

1. **The spec RE-IMPLEMENTS what statecharts routing already provides.** Looking at the actual `routing.cljc` source, `rstate` and `istate` already handle:
   - Route initialization (`initialize-route!`)
   - Parent query updates (`update-parent-query!`)
   - Route parameter storage (`establish-route-params-node`)
   - Busy checking (`deep-busy?`)
   - Form dirty checking (`busy-form-handler`)

   The form spec proposes adding `sfro/busy?` and `sfro/statechart` to form components. But `istate` in the routing chart already does this:
   ```clojure
   (istate {:route/target :my.app/AccountForm})
   ```
   And `istate` automatically reads `sfro/statechart` from the component and creates the invocation. **The specs should not re-describe what the routing library already does. They should focus on what RAD needs to ADD to make forms/reports work with the existing routing infrastructure.**

2. **`route-to!` signature change is incomplete.** The actual statecharts `route-to!` signature is:
   ```clojure
   (defn route-to! [app-ish target] ...)
   (defn route-to! [app-ish target data] ...)
   ```
   Where `target` is a component class, registry key, or keyword. RAD's current `route-to!` has:
   ```clojure
   [app options]  ;; where options has :target, :route-params, etc.
   [app-or-component RouteTarget route-params]
   ```
   The mapping from old to new needs a compatibility function that extracts `target` and `data` from the old call patterns. The spec mentions this but doesn't specify the adapter.

3. **`update-route-params!` removal is problematic.** Reports use `update-route-params!` to persist sort/filter/page state in the URL. The spec says this is "removed" and "URL sync is automatic." But URL sync in statecharts routing only syncs the ROUTE (which page you're on), not arbitrary parameters stored in route state. The spec needs to explain how report parameters get into the URL. Looking at the routing code, `establish-route-params-node` stores params from event data into `[:routing/parameters id]`, and the URL codec reads from there. So parameters CAN be in the URL, but they need to flow through the route event, not through a separate update mechanism. This needs explicit specification.

4. **Missing: what happens to `install-routing!`?** Currently apps call `install-routing!` to set up the RAD router. The spec says DELETE but doesn't specify what replaces it in the app setup. The answer is `scr/start!` + `scr/install-url-sync!`, but the spec should show the full app initialization sequence.

5. **Missing: the actual routing chart definition.** Each application needs to define its routing chart with `rstate`/`istate` for each form, report, and container. The spec doesn't show how `defsc-form`'s `fo/route-prefix` maps to the routing chart's `:route/segment`. Is the routing chart auto-generated from form/report definitions, or does the user manually define it?

**Recommended changes**: Reduce redundancy with routing library. Show full app init sequence. Specify route-params-in-URL mechanism. Define whether routing chart is auto-generated or manual.

---

### 7. public-api-mapping.md

**Quality**: Excellent reference document. Most comprehensive spec.

**Issues**:

1. **`view-mode?` is listed as "Unchanged" but IS UISM-DEPENDENT.** As noted in the form spec critique, `view-mode?` reads from `::uism/asm-id` and `::uism/local-storage`. It requires a full rewrite to read from statechart session data. This is a **breaking internal change** even if the public signature stays the same.

2. **`clear-route-denied!` and `continue-abandoned-route!` simplification.** The spec changes these from `[app-ish form-ident]` to `[app-ish]`. This is correct because the routing system has a global session, but it means these functions are no longer form-specific -- they operate on the routing chart. The spec should note this semantic change.

3. **`start-form!` visibility question is critical.** The spec asks whether it should remain public. **It must remain public** for embedded forms (non-routed), which is a common pattern. But for routed forms, it should NOT be called directly -- `istate` handles it. The spec should clearly separate routed vs. embedded usage.

4. **Missing: `trigger!` on forms.** The current 4-arity `trigger!` takes `form-ident` as the session identifier. With statecharts, this could be the form-ident (if used as session-id) or a different session-id. The `send-to-self!` pattern from the routing library is the correct replacement. The spec should recommend `send-to-self!` rather than trying to preserve `trigger!`.

5. **Missing: rendering env changes.** `rendering-env` creates a map used for rendering. If it includes any UISM-derived data, it needs updating. The spec says "unchanged" but should verify.

**Recommended changes**: Fix `view-mode?` classification. Clarify `start-form!` public/private split. Recommend `send-to-self!`.

---

### 8. control-adaptation.md

**Quality**: Good. Correctly identifies the minimal scope of changes.

**Issues**:

1. **Session-id discovery is the real problem.** `run!` currently uses `(comp/get-ident instance)` as both the UISM id and the target. With statecharts, this only works if the session-id convention uses the component ident. The spec acknowledges this dependency but doesn't lock it down. **This convention MUST be specified in a cross-cutting decision, not left as an open question in each spec.**

2. **`scf/send!` first argument.** The spec shows:
   ```clojure
   (scf/send! (comp/any->app instance) session-id :event/run)
   ```
   But `scf/send!` can also accept a component instance directly (it's `app-ish`). The correct call could be:
   ```clojure
   (scf/send! instance session-id :event/run)
   ```
   Actually, looking at the source: `send!` calls `(statechart-env app-ish)` which calls `(impl/statechart-env app-ish)`. The `app-ish` type accepts `::fulcro-appish` which includes components. So `instance` should work directly. Simpler code.

3. **Event namespacing question is over-thought.** `:event/run` should stay as-is. Namespacing it would break the contract between controls and reports/containers for no benefit.

**Recommended changes**: Lock down session-id convention. Simplify `send!` call.

---

### 9. blob-statechart.md

**Quality**: Well-analyzed but **this spec should be deprioritized or removed.**

**Critical concern**: The blob system has ZERO UISM dependency. It uses Fulcro mutations with `action`, `progress-action`, and `result-action`. Converting it to a statechart is pure scope creep. The spec itself asks "Is the statechart conversion of blob worth the effort?" The answer is: **No, not for v1.**

**If kept, issues include**:

1. **SHA256 computation is CLJS-only** (uses `SubtleCrypto` API). The spec doesn't address how to handle this in CLJ for headless testing.
2. **Progress tracking via mutations** uses `net/overall-progress` which is deeply tied to Fulcro's networking layer. Redirecting progress events to a statechart adds complexity without clear benefit.
3. **The statechart adds no new capabilities** -- retry could be added without a statechart.

**Recommendation**: Remove this spec from v1. The blob system works fine without UISM and doesn't need conversion. Add it to a "nice-to-have" list for v2.

---

### 10. headless-testing.md

**Quality**: Good structure. Good test examples. Some API inaccuracies.

**Issues**:

1. **Test state names don't match the proposed statecharts.** The test examples use `:state/abandoned`, `:state/showing` which don't appear in the form or report statechart definitions. The form spec uses `:state/exited` (not `:state/abandoned`) and the report uses `:state/ready` (not `:state/showing`). Tests must use the actual state names.

2. **`t/run-events!` usage is potentially wrong.** The test shows:
   ```clojure
   (t/run-events! env :event/created)
   ```
   But the form statechart doesn't have an `:event/created` event. The `:state/creating` state has an eventless transition to `:state/editing`. The test should not need to send an event -- the eventless transition should fire automatically.

3. **Missing: mock setup for Fulcro operations.** The Tier 2 tests show `scf/start!` but don't explain how to mock the load/save operations. When the form statechart enters `:state/loading`, it fires `fops/load`. In a headless test, this load needs to either be mocked or use a loopback remote. The spec mentions "loopback remotes" but doesn't show how to set them up.

4. **Container test example doesn't match container spec.** The test creates actors `:actor/form` and `:actor/report` on the container, but the container spec uses `:actor/container` as its actor.

5. **Tier 4 integration tests reference `com.fulcrologic.fulcro.headless`** which may or may not exist. The spec should verify this namespace is available in the current Fulcro version.

6. **Missing: how to run tests.** The spec says "run via Kaocha in a REPL" but doesn't show the Kaocha configuration needed. The project may need a `tests.edn` for Kaocha.

**Recommended changes**: Fix state names to match specs. Fix event names. Show mock load/save patterns. Verify headless namespace.

---

### 11. rad-hooks-conversion.md

**Quality**: Thorough analysis. Good comparison between `use-form`/`use-report` and `use-statechart`.

**Issues**:

1. **`use-statechart` integration approach.** The spec correctly identifies that `use-statechart` assumes a co-located chart on the component (via `sfro/statechart` option). For RAD hooks, the chart comes from the form/report module. The proposed "extract `use-statechart-session`" approach would require changes to the **statecharts library itself**, which is a separate project. This should be flagged as a cross-project dependency.

2. **Alternative: just set `sfro/statechart` on the Form/Report component.** If `defsc-form` sets `sfro/statechart` as a component option on the form class (which it should, per the routing integration), then `use-statechart` would work directly. The RAD hook just needs to:
   - Call `use-statechart` with the form instance
   - Map the return value to the legacy `{:form-factory, :form-props, :form-state}` shape

   This is simpler than extracting a new lower-level hook.

3. **`:embedded? true` semantics in statecharts.** The spec asks how this flag translates. The answer: the form statechart should NOT have route-exit behavior when started via hooks. This means the chart needs to either:
   - Check a flag in session data (`:embedded? true`) to skip route-related expressions
   - Be a slightly different chart variant for embedded use

   The spec should specify which approach.

4. **Missing: cleanup semantics.** `use-statechart` sends `:event/unmount` on cleanup, which the chart can handle. But `use-form` currently calls `uism/remove-uism!` which fully destroys the session. The statechart equivalent would be to ensure the chart reaches a final state on unmount, triggering automatic GC. The spec should specify the unmount event handling in the form/report charts.

**Recommended changes**: Simplify to use `sfro/statechart` on component. Specify embedded-mode behavior. Specify cleanup.

---

## Cross-Cutting Concerns

### CC-1: Session ID Convention (CRITICAL)

Multiple specs independently propose session-id strategies without a unified decision:
- **Form**: Use form-ident (vector) as session-id
- **Report**: Use `report-session-id` function (undefined)
- **Container**: Use component ident
- **Auth**: Use a well-known keyword `::auth-machine` -> `session-id`
- **Control**: Derives from `comp/get-ident`
- **Hooks**: Use `hooks/use-generated-id` (random UUID)

**The `::sc/id` type spec in the statecharts library must be checked.** If it only accepts keywords or UUIDs, vector idents won't work. The `start!` function's guardrails spec says `[:session-id {:optional true} ::sc/id]`. We need to verify `::sc/id` allows vectors.

**Recommendation**: Define ONE session-id convention document. Verify vector idents work. If they don't, use `(keyword (str (first ident) "-" (second ident)))` as a deterministic conversion.

### CC-2: `defsc-form` / `defsc-report` / `defsc-container` Macro Rewrites (CRITICAL)

No spec covers what the macros should generate in the new system. These macros are the primary interface for downstream users. They currently generate:
- Query (including `[::uism/asm-id '_]`)
- Initial state
- Ident
- `:will-enter` / `:will-leave` / `:allow-route-change?`
- Route segment
- Component options

In the new system, they need to generate:
- Query (remove UISM, add statechart session reference if needed)
- `sfro/statechart` component option (pointing to the form/report chart)
- `sfro/busy?` component option (for forms: dirty check)
- `sfro/initialize` (`:once` for reports, `:always` for forms)
- Remove all DR lifecycle hooks
- Keep `:route-segment` for documentation/discoverability, but it's configured in the routing chart

**Recommendation**: Add a new spec "macro-rewrites.md" covering all three macro changes.

### CC-3: Expression Function Signatures

The specs inconsistently use 2-arg `(fn [env data] ...)` and 4-arg `(fn [env data event-name event-data] ...)` patterns. The Fulcro integration ALWAYS uses 4-arg (per `install-fulcro-statecharts!` docs). The routing code uses `(fn [env data & _]` for flexibility.

**Recommendation**: Standardize on 4-arg in all specs. Show the `& _` pattern for expressions that don't need event name/data.

### CC-4: Side Effects in Expressions

Multiple specs call `scf/send!`, `scf/start!`, `comp/transact!` as side effects within expression functions. This is necessary in some cases but should be explicitly acknowledged as a pattern with caveats:
- In `:immediate` event-loop mode, `scf/send!` within an expression triggers synchronous processing, which can cause stack depth issues
- `comp/transact!` within expressions bypasses the statechart's operation model

**Recommendation**: Document the "side effects in expressions" pattern with guidelines. Prefer operations over side effects where possible. Use `senv/raise` for internal events instead of `scf/send!`.

### CC-5: Alias Resolution Changes

The specs say aliases are read via `(scf/resolve-aliases data)`. Looking at the actual implementation, `resolve-aliases` is defined in `fulcro_impl.cljc` and is re-exported. The Fulcro data model automatically resolves aliases into the `data` map (per `install-fulcro-statecharts!` docs: "the data model will automatically resolve all aliases into the `data` map in expressions"). This means you can access alias values DIRECTLY on `data` without calling `resolve-aliases`. The specs should note this.

---

## Dependency Graph Issues

### Declared Dependencies

```
project-setup
  <- form-statechart
  <- report-statechart
  <- auth-statechart
  <- routing-conversion
  <- control-adaptation
  <- blob-statechart

report-statechart
  <- container-statechart

form-statechart + report-statechart + routing-conversion
  <- headless-testing

form-statechart + report-statechart
  <- rad-hooks-conversion
```

### Issues

1. **Circular dependency: form <-> routing.** The form spec depends on routing (for `istate` integration), and routing depends on form (for `busy-form-handler`). In practice, the routing library already handles both, so the actual dependency is: routing-conversion must be done BEFORE form conversion, because forms need to know how they're used as route targets.

2. **Missing dependency: all specs -> macro-rewrites.** The macro changes affect how forms/reports/containers are defined. This should be its own spec that depends on form, report, container, and routing specs.

3. **Missing dependency: control -> report + container.** Control's `run!` targets report/container sessions, so it needs their session-id convention finalized first.

4. **Recommended implementation order**:
   1. project-setup
   2. routing-conversion (defines the routing infrastructure all others depend on)
   3. form-statechart (most complex, foundational)
   4. report-statechart (similar pattern to form)
   5. container-statechart (depends on report)
   6. control-adaptation (simple, but needs session-id convention from above)
   7. auth-statechart (independent, can be parallel)
   8. rad-hooks-conversion (depends on form + report)
   9. macro-rewrites (depends on all above)
   10. headless-testing (depends on all above)
   11. public-api-mapping (update after all conversions)

---

## Missing Specs

### MS-1: Macro Rewrites (CRITICAL)

`defsc-form`, `defsc-report`, `defsc-container` macro changes. See CC-2 above.

### MS-2: Session ID Convention (CRITICAL)

A short cross-cutting spec that defines:
- How session IDs are generated for each module
- Whether vector idents are valid session IDs
- The `report-session-id` function implementation
- How hooks generate unique session IDs

### MS-3: App Initialization / Bootstrap

How an application initializes with statecharts routing instead of DR:
- `install-fulcro-statecharts!` call (with `:on-save` for URL sync)
- `scr/start!` with the routing chart
- `scr/install-url-sync!`
- How this replaces `install-routing!` + `install-route-history!`
- What happens with `rad/install!` (the RAD application setup function)

### MS-4: Migration Guide for Downstream Applications

Not a code spec but essential for adoption:
- What downstream apps need to change
- How to define a routing chart from existing DR targets
- How to handle custom UISMs
- What breaks and how to fix it

### MS-5: State Machine Extension Points

The current UISM system allows `assoc-in` on the machine definition (used by incrementally-loaded-report). Statecharts don't support this. The spec needs to document the new extension patterns:
- Shared expression functions
- Chart composition patterns
- When to use `invoke` vs independent charts

---

## Architecture Concerns

### AC-1: Over-engineering the Routing Integration

The specs propose significant custom routing infrastructure on top of what the statecharts routing library already provides. The `rstate`, `istate`, `routes`, `routing-regions`, `busy?`, `busy-form-handler` functions in `routing.cljc` already handle 90% of what the RAD specs describe. The RAD layer should be THIN -- mostly option-to-option mapping and default behaviors.

**Concern**: The form and report specs describe building routing integration from scratch, when they should instead describe how to configure existing `istate` behavior for RAD's specific needs (e.g., how `fo/route-prefix` maps to `:route/segment`).

### AC-2: Data Model Split Between Session and Fulcro State

Reports store cached data (raw-rows, sorted-rows, etc.) in Fulcro state via aliases, but store pagination metadata in session data via `ops/assign`. This creates two sources of truth that must be kept in sync. A cleaner approach would be to store everything in Fulcro state (via aliases) since the report component needs to read it for rendering.

### AC-3: Blob Conversion is Scope Creep

The blob system works. Converting it to statecharts for "consistency" adds risk and work without functional benefit. Remove from v1.

### AC-4: Auth System Redesign Temptation

The auth spec notes the system is "NOT PRODUCTION-READY." Converting it as-is preserves existing (limited) functionality. Redesigning it risks scope explosion. Do the minimal conversion.

---

## Recommended Changes (Prioritized)

### Critical (Must Fix Before Implementation)

1. **Add session-id convention spec** -- verify vector idents work, define convention per module
2. **Add macro-rewrites spec** -- `defsc-form`, `defsc-report`, `defsc-container`
3. **Fix `fops/assoc-alias` usage** -- show keyword-argument pair pattern
4. **Fix `view-mode?`** -- mark as breaking internal change, not "unchanged"
5. **Fix `fops/invoke-remote` first argument** -- verify txn format
6. **Standardize expression arity** -- 4-arg everywhere with `& _` pattern
7. **Reduce routing spec redundancy** -- don't re-describe what `istate`/`rstate` already do

### Important (Should Fix)

8. **Remove blob-statechart from v1** -- zero UISM dependency, pure scope creep
9. **Add app initialization spec** -- replace `install-routing!` bootstrap
10. **Fix headless-testing state names** -- must match actual statechart state IDs
11. **Specify on-change trigger contract** -- commit to Option A with full signature
12. **Hoist auth common events** -- use parent compound state
13. **Address container child cleanup** -- explicit cleanup on route-exit, not lazy
14. **Specify report-session-id** -- must be deterministic from class + context

### Suggested (Nice to Have)

15. **Add migration guide outline** -- what downstream apps change
16. **Document side-effects-in-expressions pattern** -- guidelines and caveats
17. **Show auto-resolved aliases** -- note that aliases appear directly on `data`
18. **Show full app init sequence** -- from zero to working routing
19. **Address `update-route-params!` replacement** -- how report params get into URLs

---

## Metrics Summary

| Metric | Value | Notes |
|--------|-------|-------|
| Specs reviewed | 11 | All complete |
| Critical issues | 7 | Session-id, macros, API signatures |
| Important issues | 7 | Scope, naming, contracts |
| Suggested improvements | 5 | Documentation, patterns |
| Missing specs identified | 5 | Macros, session-id, init, migration, extension |
| API signature errors | 3+ | assoc-alias, invoke-remote, view-mode? |
| State name mismatches | 2+ | Test examples vs chart definitions |
| Unnecessary scope | 1 | Blob conversion |
