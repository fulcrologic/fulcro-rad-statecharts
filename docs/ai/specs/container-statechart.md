# Spec: Container Statechart Conversion

**Status**: active
**Priority**: P1
**Created**: 2026-02-20
**Owner**: AI
**Depends-on**: project-setup, report-statechart, session-id-convention, macro-rewrites

## Context

A RAD container is a coordination component that groups multiple reports under a shared set of controls. The container pulls up non-local controls from its children, unifies controls with matching names, and broadcasts actions (like "run") to all children simultaneously.

The container's UISM is much simpler than the report machine -- it has a single state with two events. The complexity is in how it coordinates child report lifecycle.

## Current Container Machine Analysis

### container-machine (container.cljc)

**Actor**: `:actor/container` (the container component)

**Aliases**:

| Alias | Path | Purpose |
|-------|------|---------|
| `:parameters` | `[:actor/container :ui/parameters]` | Container parameters |

**States**: Only one effective state (`:initial`) with two events:

1. `::uism/started` -- On start:
   - `merge-children` -- Merges initial state of each child report into the container's state subtree
   - `initialize-parameters` -- Sets control values from route params, history params, or defaults
   - `start-children!` -- Calls `report/start-report!` for each child with `::report/externally-controlled? true`

2. `:event/run` -- Broadcasts `:event/run` to all children:
   ```clojure
   (reduce (fn [env [id c]]
             (uism/trigger env (comp/get-ident c {::report/id id}) :event/run))
           env
           (id-child-pairs container-class))
   ```

### Container Options

| Option | Purpose |
|--------|---------|
| `co/children` | Map of `{id ReportClass}` pairs |
| `co/layout` | Grid layout of children `[[ids...] [ids...]]` |
| `co/layout-style` | Rendering hint keyword |
| `co/route` | Route segment string |
| `co/title` | String or fn |

### defsc-container Macro

Generates:
- **Query**: `:ui/parameters`, `{:ui/controls (get-query Control)}`, `[df/marker-table '_]`, plus a join for each child `{child-registry-key (get-query ChildClass)}`
- **Initial state**: Merges child initial states keyed by their registry keys
- **Ident**: `[::id fqkw]`
- **Will-enter**: Uses `container-will-enter` with `dr/route-deferred`

### Key Behaviors

1. **Control unification**: Children's non-local controls are pulled up. If two reports define `::year-filter`, the container presents one control that affects both reports.

2. **External control**: Children are started with `::report/externally-controlled? true`, which means:
   - In `report/initialize-parameters`, global (non-local) controls are NOT initialized by the report itself
   - The container manages global control values
   - Local controls on individual reports are still managed by the report

3. **Child merging**: `merge-children` uses `merge/merge-component` to place each child's initial state at the path `[container-ident child-registry-key]`.

4. **Broadcasting**: The `:event/run` handler iterates over all children and triggers `:event/run` on each child's UISM.

## Proposed Statechart Structure

The container statechart is straightforward -- it's primarily a coordinator, not a complex state machine.

```clojure
(statechart {:id ::container-chart :initial :state/initializing}

  (state {:id :state/initializing}
    (on-entry {}
      (script {:expr (fn [env data]
                       ;; 1. Initialize control parameters from route/history/defaults
                       ;; 2. Merge child initial states into Fulcro state
                       ;; 3. Start child report statecharts
                       (let [container-class (resolve-actor-class data :actor/container)
                             children        (id-child-pairs container-class)]
                         (into
                           ;; Initialize parameters
                           (initialize-container-params env data)
                           ;; Merge children into state
                           [(fops/apply-action merge-children-into-state container-class)])))}))
    ;; After entry actions, start child statecharts (side effect, not ops)
    ;; Then transition to ready
    (transition {:target :state/ready}))

  (state {:id :state/ready}
    ;; Run all children
    (handle :event/run
      (fn [env data]
        ;; Send :event/run to each child's statechart session
        (let [container-class (resolve-actor-class data :actor/container)
              app             (:fulcro/app env)]
          (doseq [[id child-class] (id-child-pairs container-class)]
            (scf/send! app (report-session-id child-class id) :event/run))
          nil)))  ;; no ops needed, side effect only

    ;; Resume: re-initialize params and resume children
    (handle :event/resume
      (fn [env data]
        (let [container-class (resolve-actor-class data :actor/container)
              app             (:fulcro/app env)]
          (doseq [[id child-class] (id-child-pairs container-class)]
            (scf/send! app (report-session-id child-class id) :event/resume))
          (initialize-container-params env data))))))
```

### Starting Child Statecharts

The key challenge is starting child report statecharts from within the container's `on-entry`. There are two approaches:

**Option A: Side-effect in expression (recommended)**

The container's initialization expression calls `scf/start!` for each child report as a side effect. This is simple and matches the current behavior where `start-children!` calls `report/start-report!` in a `doseq`.

**Reentrancy warning**: Calling `scf/start!` and `scf/send!` from within expression functions is a side effect that could cause reentrancy issues if the event queue is in `:immediate` mode (synchronous processing). When processing an event triggers `scf/send!`, the send triggers synchronous processing of the new event, which can cause stack depth issues. **Recommendation**: Use `:event-loop? true` (the default for browser apps) which processes events asynchronously via core.async. For tests using `:immediate` mode, be aware that the drain loop has a safety limit of 100 iterations.

```clojure
(fn [env data & _]
  (let [app (:fulcro/app env)
        container-class (scf/resolve-actor-class data :actor/container)]
    (doseq [[id child-class] (id-child-pairs container-class)]
      (report/start-report! app child-class
        {::report/id id
         ::report/externally-controlled? true}))
    ;; Return ops for container's own state
    [(fops/apply-action merge-children-into-state container-class)]))
```

**Option B: Invoke child statecharts**

Use the statechart `invoke` element to spawn child statecharts. This is more "statechart-native" and would automatically manage child lifecycle (cancel on exit). However, it adds complexity:
- Each child needs a unique invoke ID
- The number of children is dynamic (defined in component options)
- Invoke is designed for a fixed number of known children in the chart definition

Recommendation: **Option A**. The container is inherently a dynamic coordinator -- the number and types of children come from component options, not from the chart structure. Using `invoke` for this would require dynamic chart generation.

## How Container Coordinates Child Report Statecharts

### Lifecycle

1. **Container starts** -> Container statechart begins -> on-entry starts all child report statecharts
2. **User changes a global control** -> Control system writes value to `[::control/id k ::control/value]` in Fulcro state -> Control's `:onChange` may trigger `:event/run` on container
3. **Container receives `:event/run`** -> Container sends `:event/run` to each child's statechart session
4. **Container route exit** -> Container statechart reaches final state or is stopped -> Child statecharts should be stopped/cleaned up

### Event Broadcasting

The container needs to send events to all children. In the current UISM, this uses `uism/trigger` within a reduce over the env. In statecharts, since we can't chain event sends through the env, we use direct `scf/send!` calls:

```clojure
(defn broadcast-to-children!
  "Sends an event to all child report statecharts of the container."
  [env data event & [event-data]]
  (let [app             (:fulcro/app env)
        container-class (scf/resolve-actor-class data :actor/container)]
    (doseq [[id child-class] (id-child-pairs container-class)]
      (scf/send! app (report-session-id child-class id) event (or event-data {})))))
```

### Control Value Flow

```
User changes control input
  -> control/set-parameter! writes value to Fulcro state
  -> control's :onChange callback fires (e.g., calls container's run!)
  -> container sends :event/run to all children
  -> each child report reloads with new parameter values
     (reads global control values from Fulcro state during load)
```

Global control values are stored in Fulcro state at `[::control/id key ::control/value]`, which is shared state accessible by all reports. This is independent of both UISM and statecharts -- it's just Fulcro normalized state.

## Child Cleanup

When the container is no longer on screen (route away), child statecharts need cleanup. Options:

1. **Explicit cleanup**: Container's route-exit sends a stop/cleanup event or calls `scf/stop!` equivalent for each child
2. **GC on final state**: If the container chart reaches a `final` state, and children are invoked via `invoke`, they are automatically cancelled
3. **Lazy cleanup**: Children's statecharts persist but are dormant; `resume` handles re-activation

**Recommendation: Use approach 1 (explicit cleanup on route-exit).** Lazy cleanup (approach 3) causes unbounded memory growth because report statecharts never reach a final state and are never GC'd. The container's statechart should send a cleanup event to each child on exit:

```clojure
;; Container chart with explicit cleanup
(state {:id :state/ready}
  ;; ... events ...
  (on-exit {}
    (script {:expr (fn [env data & _]
                     (let [app (:fulcro/app env)
                           container-class (scf/resolve-actor-class data :actor/container)]
                       (doseq [[id child-class] (id-child-pairs container-class)]
                         (let [session-id (report-session-id child-class)]
                           ;; Send unmount event; child chart handles cleanup and reaches final state
                           (scf/send! app session-id :event/unmount)))
                       nil))})))
```

The report statechart should handle `:event/unmount` by transitioning to a `final` state, triggering automatic GC of the session from Fulcro state.

## Affected Modules

- `com.fulcrologic.rad.container` - Replace `defstatemachine container-machine` with statechart, update `start-container!`, update macro
- `com.fulcrologic.rad.container-options` - No changes needed (options are orthogonal to machine type)
- `com.fulcrologic.rad.report` - `start-report!` must accept statechart-based container coordination (already planned in report spec)

### `defsc-container` Macro Changes

See `macro-rewrites.md` for the full specification. Summary: The container query has no `::uism/asm-id` to remove. The macro adds `sfro/statechart` and `sfro/initialize :once` component options, and removes the `:will-enter` generation.

## Approach

1. Define container statechart in `container.cljc`
2. Update `start-container!` to use `scf/start!`
3. Remove `container-will-enter` (routing handled by `istate`)
4. Update `defsc-container` macro per macro-rewrites.md
5. Port `merge-children`, `initialize-parameters`, `start-children!` as statechart expressions
6. Port broadcast logic for `:event/run`
7. Write tests

## Open Questions

1. **Child session ID scheme**: Each child report needs a deterministic session ID that the container can compute. The current UISM uses `(comp/get-ident child-class {::report/id id})` as the ASM ID. The statechart equivalent should follow the same pattern, e.g., `(keyword (str "report-" (name id)))` or use the ident directly.

2. **Dynamic children**: The container's children are defined in component options, not in the statechart definition. This means the statechart can't statically know how many children it coordinates. Is this acceptable, or should we explore a pattern where the chart is generated from the children config?

3. **Control unification at compile time vs runtime**: Currently, `defsc-container` pulls child controls at compile time (macro expansion). This stays the same -- the control definitions are static. The runtime behavior of reading/writing control values is unchanged.

4. **Should container use `invoke` for children?**: This is the most "correct" statechart pattern for parent-child coordination, but as discussed above, the dynamic nature of container children makes it impractical. Confirming this decision with the team would be valuable.

5. **Route lifecycle**: How does the statecharts routing system notify the container to start? This depends on the routing spec. The container needs a hook equivalent to `will-enter` / `route-deferred`.

## Verification

1. [ ] Container starts and initializes all child reports
2. [ ] Global controls are shared across children
3. [ ] Local controls remain private to their report
4. [ ] `:event/run` on container triggers `:event/run` on all children
5. [ ] Container route params flow to children's parameter initialization
6. [ ] Container renders correctly with UI plugin layout
7. [ ] Multiple instances of the same report class work (different IDs)
8. [ ] Container is headless-testable (CLJC)
9. [ ] `defsc-container` macro generates correct query with statechart session
10. [ ] Route integration works (enter, resume, exit)

## Revision History

- **R1**: Initial spec
- **R2**: Applied critique-round-1 fixes:
  - Addressed reentrancy risk with side effects in expressions (recommend `:event-loop? true`)
  - Referenced macro-rewrites.md for defsc-container changes
  - Specified explicit cleanup on route-exit (not lazy) to prevent unbounded memory growth
