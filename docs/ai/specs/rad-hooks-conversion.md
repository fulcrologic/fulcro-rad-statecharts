# Spec: RAD Hooks Conversion

**Status**: backlog
**Priority**: P1
**Created**: 2026-02-20
**Owner**: conductor
**Depends-on**: project-setup, form-statechart, report-statechart

## Context

`com.fulcrologic.rad.rad-hooks` provides `use-form` and `use-report` -- React hooks that allow embedding RAD forms and reports into arbitrary React components without routing. They currently depend on Fulcro UI State Machines (UISMs) for lifecycle management. These must be converted to use statecharts.

The statecharts library already provides `com.fulcrologic.statecharts.integration.fulcro.hooks/use-statechart`, a React hook that manages a statechart session tied to a component's lifecycle. The RAD hooks should become thin wrappers around `use-statechart`, translating RAD-specific options into statechart session configuration.

### Current Implementation Analysis

#### `use-form` (rad_hooks.cljc:16-72)

Current behavior:
1. Generates a container ID via `hooks/use-generated-id`
2. Creates a wrapper component (`rc/nc`) that joins the form data
3. Calls `hooks/use-component` to subscribe to form props
4. On mount: starts the UISM form machine via `uism/begin!` with `:embedded? true`, passing save/cancel mutation callbacks
5. On unmount: removes the UISM via `uism/remove-uism!`
6. Returns `{:form-factory, :form-props, :form-state}`

Key UISM integration points:
- `fo/statechart` -- optional custom form machine override
- `form/form-machine` -- default UISM machine
- `uism/begin!` with actor `:actor/form`
- Event data includes `:on-saved`, `:on-cancel`, `::form/create?`
- State tracked via `::uism/active-state` in container props

#### `use-report` (rad_hooks.cljc:74-90)

Current behavior:
1. Gets the report ident from `comp/get-ident`
2. Calls `hooks/use-component` to subscribe to report props
3. On mount: starts the report via `report/start-report!` with `:embedded? true`
4. On unmount: removes the UISM (unless `:keep-existing?`)
5. Returns `{:report-factory, :report-props, :report-state}`

#### `use-statechart` (statecharts hooks.cljc:10-62)

The statecharts `use-statechart` hook:
1. Takes `this` (component instance) and start args including optional `session-id`
2. Auto-assigns the component as `:actor/component`
3. Registers the statechart from the component's `:statechart` option
4. On mount: calls `scf/start!` if not already running
5. On unmount: sends `:event/unmount` to the chart
6. Returns `{:send!, :config, :local-data, :aliases}`

## Requirements

1. `use-form` must start a form statechart session (not a UISM) on mount and clean it up on unmount
2. `use-report` must start a report statechart session (not a UISM) on mount and clean it up on unmount
3. Both hooks must return enough information for the caller to render and interact with the form/report
4. The returned API should be backward-compatible where possible: `{:form-factory, :form-props, :form-state}` and `{:report-factory, :report-props, :report-state}`
5. Save/cancel completion callbacks must still work (via statechart events rather than UISM mutations)
6. The file must remain `.cljc` -- hooks are React-only at runtime but the namespace must compile on CLJ for headless testing stubs
7. Session ID management must prevent collisions when multiple hook-based forms/reports are on screen simultaneously

## Affected Modules

- `src/main/com/fulcrologic/rad/rad_hooks.cljc` - Primary conversion target
- `src/main/com/fulcrologic/rad/form_statechart.cljc` - Form statechart (must support embedded/hook mode)
- `src/main/com/fulcrologic/rad/report_statechart.cljc` - Report statechart (must support embedded/hook mode)
- `src/test/com/fulcrologic/rad/rad_hooks_test.cljc` - Tests for hook behavior

## Approach

### Option A: Thin Wrappers Around `use-statechart` (Recommended)

Make `use-form` and `use-report` delegate to `use-statechart` from the statecharts library. The RAD hooks add RAD-specific setup:

- Configure actors (`:actor/form` or `:actor/report`)
- Configure aliases for form field access
- Pass RAD-specific start data (create vs edit, save/cancel callbacks)
- Transform the returned `{:send!, :config, :local-data, :aliases}` into the RAD-specific return shape

```clojure
(defn use-form
  [app-ish Form id save-complete-mutation cancel-mutation
   {:keys [save-mutation-params cancel-mutation-params]}]
  (let [app        (rc/any->app app-ish)
        id-key     (-> Form rc/component-options fo/id ao/qualified-key)
        form-ident [id-key id]
        session-id (hooks/use-generated-id)
        ;; Start the form statechart with proper actors
        {:keys [send! config local-data aliases]}
        (use-statechart-for-rad app
          {:statechart  (or (rc/component-options Form :statechart)
                            form/default-form-chart)
           :session-id  session-id
           :data        {:fulcro/actors  {:actor/form (scf/actor Form form-ident)}
                         :on-saved       save-complete-mutation
                         :on-cancel      cancel-mutation
                         :save-params    save-mutation-params
                         :cancel-params  cancel-mutation-params
                         :create?        (tempid/tempid? id)}})]
    {:form-factory (comp/computed-factory Form {:keyfn id-key})
     :form-props   (get-in (app/current-state app) form-ident)
     :form-state   (statechart-state->form-state config)
     :send!        send!}))
```

### Option B: Direct Statechart Management (No use-statechart delegation)

Manage `scf/start!`, `scf/send!`, and cleanup directly in the hooks, similar to how the current code manages UISM lifecycle. This avoids depending on `use-statechart` (which assumes a co-located `:statechart` key on the component) but duplicates lifecycle management.

### Recommendation: Use `sfro/statechart` on the Component (Simplified)

Since `defsc-form` and `defsc-report` macros now set `sfro/statechart` as a component option (see `macro-rewrites.md`), the `use-statechart` hook from the statecharts library works directly -- it reads the chart from the component's `sfro/statechart` option. No adapter or extraction of lower-level hooks is needed.

The RAD hooks become thin wrappers:
1. Call `use-statechart` with the Form/Report instance
2. Map the return value to the legacy `{:form-factory, :form-props, :form-state}` shape
3. Pass RAD-specific data (`:embedded? true`, save/cancel callbacks) via the start data

### Embedded Mode Behavior

When started via hooks (not routing), the form/report statechart needs to know it's embedded. The `:embedded? true` flag is passed in session data:

```clojure
;; In use-form start data:
{:embedded? true
 :on-saved  save-complete-mutation
 :on-cancel cancel-mutation
 ...}
```

The form statechart checks `(:embedded? data)` in expressions to:
- **Skip route-exit behavior**: Embedded forms don't participate in routing guards (`sfro/busy?` is irrelevant)
- **Use callback mutations**: Instead of route navigation on save/cancel, invoke the `:on-saved` / `:on-cancel` mutations from session data
- **No URL sync**: Embedded forms don't affect the URL

### Cleanup Semantics

On unmount, `use-statechart` sends `:event/unmount` to the chart. The form/report statechart must handle this event by transitioning to a `final` state, which triggers automatic GC of the session from Fulcro state. This replaces the current `uism/remove-uism!` call.

```clojure
;; In the form/report statechart:
(on :event/unmount :state/done)

(final {:id :state/done})
```

The `final` state causes the statecharts library to automatically clean up the session data from Fulcro state, preventing memory leaks.

### Session ID Management

Each hook invocation needs a unique session ID:

- **use-form**: Use `hooks/use-generated-id` to create a stable session ID per component instance. This prevents collisions when multiple forms are mounted simultaneously.
- **use-report**: Reports with a singleton ident (e.g., `[::ReportName :singleton]`) can use a deterministic session ID derived from the ident. For multiple instances, use `hooks/use-generated-id`.
- **Reconnection**: If a `session-id` is explicitly provided, the hook should reconnect to an existing session (send no start event if already running). This matches `use-statechart`'s current behavior.

### CLJC Compatibility

The hooks file must compile on CLJ for the following reasons:
- Headless testing may need to reference the namespace
- CLJC form/report modules may reference hook-related vars

Strategy:
- Keep the file as `.cljc`
- Wrap React-specific code in `#?(:cljs ...)` reader conditionals
- Provide CLJ stubs that throw or return nil, clearly documenting they are not callable on CLJ

```clojure
(defn use-form
  "React hook. Use a RAD form backed by a statechart session.
   CLJ: Not callable -- throws. Use statechart testing utilities directly."
  [app-ish Form id save-complete-mutation cancel-mutation & [options]]
  #?(:cljs
     (let [...]  ;; actual hook implementation
       {:form-factory ...
        :form-props   ...
        :form-state   ...})
     :clj
     (throw (ex-info "use-form is a React hook and cannot be called on the JVM. Use statechart testing utilities instead." {}))))
```

### Return Value Mapping

The hooks must map statechart configuration to the legacy return shape:

| Legacy key       | Source                                        |
|------------------|-----------------------------------------------|
| `:form-factory`  | `(comp/computed-factory Form {:keyfn id-key})`|
| `:form-props`    | Resolved actor data from Fulcro state         |
| `:form-state`    | Mapped from active statechart configuration   |
| `:report-factory`| `(comp/computed-factory Report {:keyfn pk})`  |
| `:report-props`  | `hooks/use-component` for report subscription |
| `:report-state`  | Mapped from active statechart configuration   |

The `:form-state` / `:report-state` values were previously UISM active-state keywords. We need a mapping from statechart states to equivalent keywords so downstream code that switches on form/report state continues to work. For example:

```clojure
(defn statechart-state->form-state
  "Maps statechart configuration to legacy form state keywords for backward compatibility."
  [config]
  (cond
    (contains? config :state/editing) :state/editing
    (contains? config :state/saving)  :state/saving
    (contains? config :state/loading) :state/loading
    :else                             :state/initial))
```

### Completion Callbacks

Currently, save/cancel invoke Fulcro mutations directly. In the statechart model:

1. The form statechart emits `:event/saved` or `:event/cancelled` when done
2. The hook sets up a statechart expression that invokes the save/cancel mutation when entering the corresponding final/completion state
3. This is configured via the `:data` map passed to `scf/start!`

The statechart's on-entry for the "saved" state would look at session data for the callback mutation and invoke it:

```clojure
(on-entry {}
  (script {:expr (fn [env data]
                   (when-let [on-saved (:on-saved data)]
                     [(fops/invoke-remote on-saved
                        {:params (merge (:save-params data)
                                   {:ident (:form-ident data)})})]))}))
```

## Open Questions

- Should `use-statechart` in the statecharts library be refactored to expose a lower-level `use-statechart-session` hook, or should the RAD hooks manage the lifecycle independently?
- The current `use-form` supports a custom `:fo/statechart` override on the Form component. Should this be preserved as a `:statechart` override, or should all forms use a single canonical form statechart?
- Should the `:form-state` / `:report-state` return values be the raw statechart configuration set, or the legacy keyword mapping? Raw is more powerful but breaks backward compatibility.
- How should `use-form`'s `:embedded? true` flag translate to the statechart model? The form statechart likely needs to know it was started by a hook (not routing) to skip route-exit behavior.
- The current `use-report` calls `report/start-report!` which does UISM + data loading. In the statechart model, should data loading be triggered by the report statechart's initial state entry, or should the hook explicitly load?
- Should we add a `send!` function to the return map so callers can send arbitrary events to the form/report statechart? This would be a new capability not available in the UISM model.

## Verification

1. [ ] `use-form` starts a statechart session (not a UISM) on mount
2. [ ] `use-form` cleans up the session on unmount
3. [ ] `use-form` returns `{:form-factory, :form-props, :form-state}` with correct values
4. [ ] `use-form` correctly handles create (tempid) vs edit (real id)
5. [ ] `use-form` save/cancel callbacks are invoked via statechart completion states
6. [ ] `use-report` starts a statechart session on mount
7. [ ] `use-report` returns `{:report-factory, :report-props, :report-state}`
8. [ ] `use-report` respects `:keep-existing?` on unmount
9. [ ] Multiple simultaneous hook-based forms do not collide (unique session IDs)
10. [ ] File compiles on both CLJ and CLJS
11. [ ] CLJ stubs throw with clear error messages
12. [ ] Backward-compatible state keywords map correctly from statechart configuration

## Revision History

- **R1**: Initial spec
- **R2**: Applied critique-round-1 fixes:
  - Simplified to use `sfro/statechart` on component (no need to extract `use-statechart-session` hook)
  - Specified embedded-mode behavior: `:embedded? true` in session data, skip route-exit, use callback mutations
  - Specified cleanup semantics: `:event/unmount` -> chart reaches `final` state -> automatic GC
  - Removed cross-project dependency on extracting lower-level hook from statecharts library
