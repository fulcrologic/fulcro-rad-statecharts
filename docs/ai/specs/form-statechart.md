# Spec: Form Statechart Conversion

**Status**: active
**Priority**: P0
**Created**: 2026-02-20
**Owner**: AI
**Depends-on**: project-setup, session-id-convention, macro-rewrites

## Context

The RAD form system is the largest and most complex UISM in fulcro-rad. `com.fulcrologic.rad.form/form-machine` (~270 lines of UISM definition) manages the full lifecycle of entity editing: loading, creating, editing, saving, undo, route guarding, subform management, and dirty tracking. This spec defines the conversion from UISM to statecharts.

The current form UISM is defined in `src/main/com/fulcrologic/rad/form.cljc` lines 1180-1447. Helper functions that support the machine (start-edit, start-create, leave-form, calc-diff, etc.) are defined alongside it in the same file.

## Requirements

1. Replace `form-machine` (defstatemachine) with a statechart definition using `statechart`, `state`, `transition`, `on-entry`, `script`, etc.
2. Preserve all existing form behaviors: load, create, edit, save, undo, cancel, route guarding, subform add/delete, dirty tracking
3. Maintain the public API surface (`save!`, `cancel!`, `undo-all!`, `add-child!`, `delete-child!`, `edit!`, `create!`, `view!`, `start-form!`, `input-changed!`, `input-blur!`, etc.)
4. Support custom form machines via `fo/machine` option (users can override the chart)
5. All code must be CLJC for headless testing
6. Route integration must use statecharts routing (`rstate`/`istate`) instead of Fulcro dynamic routing

## Current UISM Analysis

### Actors

```clojure
#{:actor/form}   ;; The form component being edited
```

### Aliases

```clojure
{:confirmation-message [:actor/form :ui/confirmation-message]
 :route-denied?        [:actor/form :ui/route-denied?]
 :server-errors        [:actor/form ::form/errors]}
```

### States

| UISM State | Description |
|---|---|
| `:initial` | Entry point. Dispatches to create or edit based on `::create?` event data |
| `:state/loading` | Waiting for server load of existing entity |
| `:state/asking-to-discard-changes` | Confirmation dialog when abandoning dirty form |
| `:state/saving` | Waiting for server save response |
| `:state/editing` | Main interactive editing state (handles most events) |

### Global Events (available in all states)

| Event | Handler |
|---|---|
| `:event/exit` | `uism/exit` - tears down the state machine |
| `:event/reload` | Re-issues load for existing entities (not tempids) |
| `:event/mark-complete` | Marks all form fields as complete for validation |

### State-Specific Events

#### :initial
No named events. The handler function runs immediately:
- Stores startup options (event-data) via `uism/store :options`
- Calls `fo/triggers :started` if defined
- If `::create?` true: calls `start-create` -> transitions to `:state/editing`
- If `::create?` false: calls `start-edit` -> transitions to `:state/loading`

#### :state/loading
| Event | Handler |
|---|---|
| `:event/loaded` | Clears errors, auto-creates to-one refs, handles UI props, adds form config, marks complete, notifies router if pending, transitions to `:state/editing` |
| `:event/failed` | Sets server-errors alias to `[{:message "Load failed."}]` |

#### :state/asking-to-discard-changes
| Event | Handler |
|---|---|
| `:event/ok` | Calls `leave-form` (reverts form, routes away) |
| `:event/cancel` | Returns to `:state/editing` |

#### :state/saving
| Event | Handler |
|---|---|
| `:event/save-failed` | Extracts errors from mutation result, sets server-errors alias, calls `fo/triggers :save-failed`, runs `on-save-failed` txn, transitions to `:state/editing` |
| `:event/saved` | Updates history route (edit action + real ID), calls `fo/triggers :saved`, runs `on-saved` txn, marks form pristine, transitions to `:state/editing` |

#### :state/editing
| Event | Handler |
|---|---|
| `:event/attribute-changed` | Clears errors, updates value in state, marks field complete, fires `on-change` trigger, runs `derive-fields` |
| `:event/blur` | No-op (placeholder for future use) |
| `:event/route-denied` | If `:async` confirm: stores desired route, sets `route-denied?` true. If sync confirm fn: prompts user, either leaves or stays |
| `:event/continue-abandoned-route` | Retrieves stored route, pushes/replaces history, retries route via DR, resets form to pristine |
| `:event/clear-route-denied` | Sets `route-denied?` to false |
| `:event/add-row` | Merges new child entity, adds form config, marks fields complete, fires `on-change`, runs derive-fields |
| `:event/delete-row` | Removes child ident from parent relation, fires `on-change`, runs derive-fields |
| `:event/save` | Validates form. If valid: calculates diff, triggers remote save mutation, transitions to `:state/saving`. If invalid: marks all complete, stays in editing |
| `:event/reset` | Calls `undo-all` (clears errors, restores pristine) |
| `:event/cancel` | Calls `leave-form` (reverts form, routes away) |

### Key Helper Functions

| Function | Purpose | Statechart Equivalent |
|---|---|---|
| `start-edit` | Issues `uism/load` for form entity | `fops/load` in on-entry script |
| `start-create` | Generates default state, merges, marks complete | Script in on-entry |
| `leave-form` | Reverts form, determines cancel route, schedules routing | Script + routing event |
| `calc-diff` | Computes `fs/dirty-fields` for save delta | Pure function (unchanged) |
| `clear-server-errors` | `uism/assoc-aliased :server-errors []` | `fops/assoc-alias :server-errors []` |
| `undo-all` | Clears errors + `fs/pristine->entity*` | Script with `fops/apply-action` |
| `auto-create-to-one` | Creates missing to-one refs marked autocreate | Script |
| `apply-derived-calculations` | Runs `:derive-fields` triggers | Script |
| `handle-user-ui-props` | Runs `::initialize-ui-props` after load | Script |
| `route-target-ready` | Notifies router that deferred route is ready | Statecharts routing handles this natively |

## Proposed Statechart Structure

```clojure
(ns com.fulcrologic.rad.form-chart
  (:require
    [com.fulcrologic.statecharts.chart :refer [statechart]]
    [com.fulcrologic.statecharts.elements :refer
     [state transition on-entry on-exit script final data-model]]
    [com.fulcrologic.statecharts.convenience :refer [on handle]]
    [com.fulcrologic.statecharts.data-model.operations :as ops]
    [com.fulcrologic.statecharts.integration.fulcro :as scf]
    [com.fulcrologic.statecharts.integration.fulcro.operations :as fops]
    [com.fulcrologic.rad.form-expressions :as fex]))

(def form-chart
  (statechart {:initial :initial}
    (data-model {:expr (fn [_ _] {:options {}})})

    ;; ===== INITIAL (decision state) =====
    (state {:id :initial}
      (on-entry {}
        (script {:expr fex/store-options}))
      ;; Eventless transitions act as a decision node
      (transition {:cond fex/create? :target :state/creating})
      (transition {:target :state/loading}))

    ;; ===== CREATING =====
    ;; Separated from loading because create is synchronous
    (state {:id :state/creating}
      (on-entry {}
        (script {:expr fex/start-create-expr}))
      ;; Immediately transition to editing after create setup
      (transition {:target :state/editing}))

    ;; ===== LOADING =====
    (state {:id :state/loading}
      (on-entry {}
        (script {:expr fex/start-load-expr}))

      (on :event/loaded :state/editing
        (script {:expr fex/on-loaded-expr}))

      (on :event/failed :state/load-failed
        (script {:expr fex/on-load-failed-expr}))

      ;; Global events available during loading
      (on :event/exit :state/exited)
      (on :event/reload :state/loading
        (script {:expr fex/start-load-expr})))

    ;; ===== LOAD FAILED =====
    ;; Terminal-ish state - can retry or exit
    (state {:id :state/load-failed}
      (on :event/reload :state/loading)
      (on :event/exit :state/exited))

    ;; ===== EDITING (main interactive state) =====
    (state {:id :state/editing}
      ;; --- Global events ---
      (on :event/exit :state/exited)
      (on :event/reload :state/loading
        (script {:expr fex/start-load-expr}))
      (handle :event/mark-complete fex/mark-all-complete-expr)

      ;; --- Field editing ---
      (handle :event/attribute-changed fex/attribute-changed-expr)
      (handle :event/blur fex/blur-expr)

      ;; --- Subform management ---
      (handle :event/add-row fex/add-row-expr)
      (handle :event/delete-row fex/delete-row-expr)

      ;; --- Save flow ---
      (transition {:event :event/save :cond fex/form-valid? :target :state/saving}
        (script {:expr fex/prepare-save-expr}))
      (handle :event/save fex/mark-complete-on-invalid-expr)

      ;; --- Undo ---
      (handle :event/reset fex/undo-all-expr)

      ;; --- Cancel / Route guarding ---
      (on :event/cancel :state/leaving
        (script {:expr fex/prepare-leave-expr}))
      (handle :event/route-denied fex/route-denied-expr)
      (handle :event/continue-abandoned-route fex/continue-abandoned-route-expr)
      (handle :event/clear-route-denied fex/clear-route-denied-expr))

    ;; ===== SAVING =====
    (state {:id :state/saving}
      (on :event/saved :state/editing
        (script {:expr fex/on-saved-expr}))
      (on :event/save-failed :state/editing
        (script {:expr fex/on-save-failed-expr}))

      ;; Global events
      (on :event/exit :state/exited))

    ;; ===== LEAVING =====
    ;; Transient state for form exit cleanup
    (state {:id :state/leaving}
      (on-entry {}
        (script {:expr fex/leave-form-expr}))
      (transition {:target :state/exited}))

    ;; ===== EXITED (final) =====
    (final {:id :state/exited})))
```

### Design Decisions

1. **`:initial` uses eventless transitions** instead of a handler function. The UISM initial state runs a handler immediately; in statecharts, we use a state with conditional eventless transitions to route to `:state/creating` or `:state/loading`.

2. **`:state/creating` is a separate state** from `:state/loading`. The UISM combines them in `:initial`, but separating them makes the chart clearer and allows the creating state's on-entry to set up defaults before transitioning to editing.

3. **`:state/load-failed` is explicit** rather than remaining in `:state/loading` with an error alias. This makes the failure visible in the statechart configuration.

4. **`:state/leaving` is a transient state** that runs leave-form cleanup and immediately transitions to `:state/exited`. The UISM handles this inline; the statechart makes it explicit.

5. **`:state/asking-to-discard-changes` is removed** as a top-level state. However, the async confirmation pattern (where a modal asks "discard changes?") requires a distinct state for the UI to render the dialog. This can be modeled as a **child state of `:state/editing`** if needed:
   ```clojure
   (state {:id :state/editing :initial :state/edit-normal}
     (state {:id :state/edit-normal}
       ;; ... all editing events ...
       (on :event/route-denied :state/asking-to-discard))
     (state {:id :state/asking-to-discard}
       (on :event/ok :state/leaving)
       (on :event/cancel :state/edit-normal)))
   ```
   For the initial implementation, the simpler alias-flag approach (setting `route-denied?` to true) is sufficient. The child state option is available if async confirmation UX requires it.

6. **Expression functions are in a separate namespace** (`form-expressions`) following the statecharts file organization pattern from the patterns resource. This keeps the chart definition clean and the expressions testable.

## Actor Mapping

| UISM Actor | Statechart Actor | Setup |
|---|---|---|
| `:actor/form` | `:actor/form` | `(scf/actor FormClass form-ident)` passed in `:data` at `start!` |

The actor is set up identically. The `start-form!` function changes from:

```clojure
;; UISM version
(uism/begin! app machine form-ident
  {:actor/form (uism/with-actor-class form-ident form-class)}
  params)

;; Statechart version (see session-id-convention.md for ident->session-id)
(let [session-id (ident->session-id form-ident)]
  (scf/start! app {:machine    (or (comp/component-options form-class ::machine) ::form-chart)
                   :session-id session-id
                   :data       {:fulcro/actors  {:actor/form (scf/actor form-class form-ident)}
                                :fulcro/aliases {:confirmation-message [:actor/form :ui/confirmation-message]
                                                 :route-denied?        [:actor/form :ui/route-denied?]
                                                 :server-errors        [:actor/form ::form/errors]}
                                ::create?       (tempid/tempid? id)
                                :options        params}}))
```

**Session ID strategy**: Use `(ident->session-id form-ident)` to produce a keyword from the form ident. See `session-id-convention.md` for details. This mirrors the UISM approach where `form-ident` is the `asm-id`, and allows `send!` to target the form's session directly.

## Alias Mapping

| UISM Alias | Statechart Alias | Resolves To |
|---|---|---|
| `:confirmation-message` | `:confirmation-message` | `[:actor/form :ui/confirmation-message]` in Fulcro state via actor ident |
| `:route-denied?` | `:route-denied?` | `[:actor/form :ui/route-denied?]` |
| `:server-errors` | `:server-errors` | `[:actor/form ::form/errors]` |

Aliases are defined in the `:fulcro/aliases` key of the start data. They are read directly from `data` (the Fulcro data model automatically resolves all aliases into the `data` map in expressions -- see CC-5 in critique). They can also be read explicitly via `scf/resolve-aliases`. They are written via `fops/assoc-alias` which takes keyword-argument pairs (variadic `& {:as kvs}`).

```clojure
;; UISM: Reading
(uism/alias-value env :server-errors)

;; Statechart: Reading -- aliases resolve directly on `data` map
(let [errors (:server-errors data)] ...)
;; Or explicitly:
(let [{:keys [server-errors]} (scf/resolve-aliases data)] ...)

;; UISM: Writing (single value)
(uism/assoc-aliased env :server-errors [{:message "error"}])

;; Statechart: Writing -- keyword-argument pairs (can set multiple at once)
[(fops/assoc-alias :server-errors [{:message "error"}])]
;; Multiple aliases in one call:
[(fops/assoc-alias :server-errors [] :route-denied? false)]
```

## Event Mapping

Every UISM event maps 1:1 to a statechart event:

| UISM Event | Statechart Event | Triggered By |
|---|---|---|
| `:event/exit` | `:event/exit` | `abandon-form!`, `form-will-leave` |
| `:event/reload` | `:event/reload` | `undo-via-load!` |
| `:event/mark-complete` | `:event/mark-complete` | `mark-all-complete!` |
| `:event/loaded` | `:event/loaded` | Load ok-event callback |
| `:event/failed` | `:event/failed` | Load error-event callback |
| `:event/attribute-changed` | `:event/attribute-changed` | `input-changed!` |
| `:event/blur` | `:event/blur` | `input-blur!` |
| `:event/route-denied` | `:event/route-denied` | `:route-denied` component option |
| `:event/continue-abandoned-route` | `:event/continue-abandoned-route` | `continue-abandoned-route!` |
| `:event/clear-route-denied` | `:event/clear-route-denied` | `clear-route-denied!` |
| `:event/add-row` | `:event/add-row` | `add-child!` |
| `:event/delete-row` | `:event/delete-row` | `delete-child!` |
| `:event/save` | `:event/save` | `save!` |
| `:event/saved` | `:event/saved` | Save mutation ok-event |
| `:event/save-failed` | `:event/save-failed` | Save mutation error-event |
| `:event/reset` | `:event/reset` | `undo-all!` |
| `:event/cancel` | `:event/cancel` | `cancel!` |
| `:event/ok` | (removed) | Was only used in `:state/asking-to-discard-changes` |

## Handler Conversion

Each UISM handler becomes a statechart expression function. Expression functions receive `(fn [env data event-name event-data])` (4-arg Fulcro convention) and return a vector of operations.

### Pattern: UISM `apply-action` -> `fops/apply-action`

```clojure
;; UISM
(uism/apply-action env fs/mark-complete* form-ident)

;; Statechart
[(fops/apply-action fs/mark-complete* form-ident)]
```

### Pattern: UISM `assoc-aliased` -> `fops/assoc-alias`

```clojure
;; UISM
(uism/assoc-aliased env :server-errors [])

;; Statechart
[(fops/assoc-alias :server-errors [])]
```

### Pattern: UISM `store`/`retrieve` -> `ops/assign` / direct data access

```clojure
;; UISM
(uism/store env :options event-data)
(uism/retrieve env :options)

;; Statechart
[(ops/assign :options event-data)]
;; Reading: (:options data)
```

### Pattern: UISM `activate` -> statechart transitions

In UISM, `activate` is called within a handler to change state. In statecharts, state changes are declared as transitions on the chart itself, not in expressions. For cases where the handler conditionally activates different states, we use:

1. **Conditional transitions** with `:cond` predicates on the chart
2. **Multiple transitions** for the same event with different conditions (evaluated in document order)

### Example: `start-load-expr`

```clojure
(defn start-load-expr
  "Expression for loading an existing form entity."
  [env data _event-name _event-data]
  (let [FormClass (scf/resolve-actor-class data :actor/form)
        {:keys [ident]} (get-in data [:fulcro/actors :actor/form])
        form-ident ident]
    [(fops/load form-ident FormClass
       {::sc/ok-event    :event/loaded
        ::sc/error-event :event/failed})]))
```

### Example: `attribute-changed-expr`

```clojure
(defn attribute-changed-expr
  "Expression for handling field value changes."
  [env data _event-name event-data]
  (let [{:keys [form-key form-ident old-value value]
         ::attr/keys [cardinality type qualified-key]} event-data
        ;; Value normalization (same logic as current)
        many? (= :many cardinality)
        ref?  (= :ref type)
        value (cond
                (and ref? many? (nil? value)) []
                (and many? (nil? value)) #{}
                (and ref? many?) (filterv #(not (nil? (second %))) value)
                (and ref? (nil? (second value))) nil
                :else value)
        path  (when (and form-ident qualified-key) (conj form-ident qualified-key))
        ;; Resolve trigger functions
        form-class (some-> form-key rc/registry-key->class)
        on-change  (some-> form-class rc/component-options ::form/triggers :on-change)]
    (cond-> [(fops/assoc-alias :server-errors [])
             (fops/apply-action fs/mark-complete* form-ident qualified-key)]
      (and path (nil? value))
      (conj (fops/apply-action update-in form-ident dissoc qualified-key))

      (and path (some? value))
      (conj (fops/apply-action assoc-in path value))

      ;; on-change and derive-fields require special handling - see open questions
      )))
```

## Remote Operations

### Save (trigger-remote-mutation -> fops/invoke-remote)

```clojure
;; UISM
(uism/trigger-remote-mutation env :actor/form save-mutation
  {::uism/error-event :event/save-failed
   ::uism/ok-event    :event/saved
   ::form/master-pk   master-pk
   ::form/id          (second form-ident)
   ::m/returning      form-class
   ::form/delta       delta})

;; Statechart -- note: first arg is a Fulcro txn vector, not a bare symbol
[(fops/invoke-remote [(save-form {::form/master-pk master-pk
                                  ::form/id        (second form-ident)
                                  ::form/delta     delta})]
   {:returning   :actor/form  ;; resolves actor class
    :ok-event    :event/saved
    :error-event :event/save-failed})]
```

### Load (uism/load -> fops/load)

```clojure
;; UISM
(uism/load env form-ident FormClass
  {::uism/ok-event    :event/loaded
   ::uism/error-event :event/failed})

;; Statechart
[(fops/load form-ident FormClass
   {::sc/ok-event    :event/loaded
    ::sc/error-event :event/failed})]
```

## form-machines.cljc Translation

The current `form_machines.cljc` namespace is empty (just a docstring). It was intended to hold helper functions for custom form machines. In the statechart version, this namespace should provide:

1. **Expression helper functions** that custom charts can compose:
   - `clear-server-errors-ops` - Returns ops to clear errors
   - `undo-all-ops` - Returns ops to revert form
   - `save-ops` - Returns ops to trigger save
   - `standard-load-ops` - Returns ops to load form entity

2. **Reusable chart fragments** that custom charts can include:
   - `global-event-transitions` - Vector of transitions for exit/reload/mark-complete
   - `editing-event-transitions` - Vector of transitions for the editing state

```clojure
(ns com.fulcrologic.rad.form-machines
  "Helper functions and chart fragments for writing custom form statecharts."
  (:require
    [com.fulcrologic.statecharts.elements :refer [transition script]]
    [com.fulcrologic.statecharts.convenience :refer [on handle]]
    [com.fulcrologic.rad.form-expressions :as fex]))

(def global-transitions
  "Reusable transitions for exit, reload, and mark-complete. Include these
   in any state that should support the standard global form events."
  [(on :event/exit :state/exited)
   (on :event/reload :state/loading (script {:expr fex/start-load-expr}))
   (handle :event/mark-complete fex/mark-all-complete-expr)])
```

## Dirty Tracking

Dirty tracking uses Fulcro's `com.fulcrologic.fulcro.algorithms.form-state` (fs) namespace, which is independent of UISM. The statechart integration does **not** change how dirty tracking works:

- `fs/add-form-config*` - Adds form config metadata to state (called on create and load)
- `fs/mark-complete*` - Marks fields as "checked" for validation
- `fs/dirty?` - Checks if form has unsaved changes (used in UI for button states)
- `fs/dirty-fields` - Computes the delta for save
- `fs/pristine->entity*` - Reverts form to last-saved state (undo/cancel)
- `fs/entity->pristine*` - Marks current state as pristine (after save)

All of these operate directly on the Fulcro state map and are called via `fops/apply-action`:

```clojure
;; In expression functions:
[(fops/apply-action fs/add-form-config* FormClass form-ident {:destructive? true})
 (fops/apply-action fs/mark-complete* form-ident)
 (fops/apply-action fs/entity->pristine* form-ident)]
```

The `fs/dirty?` check for UI rendering (button enabled/disabled states) remains a pure function call in the component render, unchanged from current behavior.

## Route Integration

### Current: Fulcro Dynamic Routing

The current form integrates with Fulcro's dynamic routing via:
- `form-will-enter` - Creates `dr/route-deferred` and calls `start-form!`
- `form-will-leave` - Checks if UISM is running, triggers `:event/exit`
- `form-allow-route-change` - Returns true if form is not dirty
- `:route-denied` component option - Triggers `:event/route-denied` on the UISM
- `route-target-ready` - Signals to the router that a deferred route target is ready

### Proposed: Statecharts Routing

With statecharts routing, forms become `rstate` or `istate` elements in the routing chart:

```clojure
;; In the routing chart definition:
(require '[com.fulcrologic.statecharts.integration.fulcro.routing :as scr])

;; A form becomes a route state with a co-located statechart
(scr/rstate {:id ::account-form
             :route/segment ["account" :action :id]
             :route/target AccountForm}
  ;; The form's own statechart runs as an invoked child
  ;; via the routing-options/statechart option on the component
  )
```

The form component uses routing options instead of DR hooks:

```clojure
(defsc-form AccountForm [this props]
  {fo/id             account/id
   fo/attributes     [...]
   fo/route-prefix   "account"
   ;; NEW: statechart routing options
   sfro/statechart   form-chart       ;; co-located chart definition
   sfro/busy?        (fn [env data]   ;; replaces allow-route-change?
                       (let [{:actor/keys [form]} (scf/resolve-actors env :actor/form)]
                         (fs/dirty? form)))
   sfro/initialize   :once})
```

Key differences:
1. **No `will-enter`/`will-leave`**: The routing statechart handles entry/exit lifecycle
2. **No `route-deferred`**: Loading happens in the form's own statechart, routing shows the form immediately (or the form shows a loading indicator)
3. **`busy?` replaces `allow-route-change?`**: The routing system checks `busy?` before allowing navigation away
4. **Route denied flow**: The routing system's built-in busy-checking handles the "unsaved changes" guard. The form chart can still set `route-denied?` for async confirmation UI

### convert-options Changes

See `macro-rewrites.md` for the full specification of `defsc-form` macro changes. Summary:

The `convert-options` function (lines 528-578) must be updated to:
1. Remove `:will-enter`, `:will-leave`, `:allow-route-change?`, `:route-denied` generation
2. Add `sfro/statechart`, `sfro/busy?`, and `sfro/initialize :always` to component options
3. Remove `[::uism/asm-id '_]` from the query (no longer needed)
4. Keep `:route-segment` generation for discoverability
5. Support `fo/machine` as either a statechart definition or a pre-registered chart ID keyword
6. Emit a compile-time warning if `:will-enter` is overridden in user options

### Public API Changes for Routing

| Current | New |
|---|---|
| `form-will-enter` | Removed. Routing chart handles entry |
| `form-will-leave` | Removed. Routing chart handles exit |
| `form-allow-route-change` | Replaced by `sfro/busy?` |
| `start-form!` | Still exists for non-routed / embedded forms. For routed forms, the routing system starts the chart |
| `edit!` / `create!` / `view!` | Change to use `scr/route-to!` instead of `rad-routing/route-to!` |

## Expression Functions Namespace

All expression functions live in `com.fulcrologic.rad.form-expressions`:

```clojure
(ns com.fulcrologic.rad.form-expressions
  "Statechart expression functions for the RAD form chart. These are the
   executable content that runs inside form statechart states and transitions."
  (:require
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.algorithms.normalized-state :as fns]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [com.fulcrologic.fulcro.raw.components :as rc]
    [com.fulcrologic.statecharts :as sc]
    [com.fulcrologic.statecharts.data-model.operations :as ops]
    [com.fulcrologic.statecharts.integration.fulcro :as scf]
    [com.fulcrologic.statecharts.integration.fulcro.operations :as fops]
    [com.fulcrologic.rad.form :as form]
    [taoensso.timbre :as log]))
```

Each expression is a `(fn [env data event-name event-data] ops-or-nil)`. The Fulcro integration ALWAYS calls expressions with 4 args (per `install-fulcro-statecharts!` docs). The last two args (event-name and event-data) are also available in `data` under the `:_event` key as a convenience. Use `(fn [env data & _])` when the event name/data are not needed.

**Convention**: All expressions in this spec use the 4-arg form. When event-name and event-data are unused, they are bound as `_event-name _event-data` or elided with `& _`.

## Open Questions

1. **on-change trigger**: **Decision: Option A (clean break).** The new on-change signature is:
   ```clojure
   (fn [env data form-ident changed-key old-value new-value]
     ;; env - statechart env (contains :fulcro/app, etc.)
     ;; data - statechart session data (aliases auto-resolved)
     ;; form-ident - the ident of the form being edited
     ;; changed-key - the qualified keyword of the changed field
     ;; old-value - previous value
     ;; new-value - new value
     ;; Returns: vector of operations (fops/apply-action, fops/assoc-alias, ops/assign, etc.) or nil
     [(fops/apply-action assoc-in (conj form-ident :derived/field) (compute-derived new-value))])
   ```
   This is a **breaking change** from the UISM signature `(fn [uism-env form-ident k old new] -> uism-env)`. The key difference: returns ops vector instead of threaded env.

2. **started/saved/save-failed triggers**: **Decision: Change to statechart expression signature.** New signature: `(fn [env data event-name event-data] ops-vec)` matching the standard 4-arg expression convention. This is a **breaking change**.

3. **Session ID as form-ident**: **Resolved.** Vector idents are NOT valid `::sc/id` values (spec only allows uuid, number, keyword, string). See `session-id-convention.md` for the deterministic `ident->session-id` conversion that produces a namespaced keyword from a Fulcro ident. Use `(form-session-id form-instance)` or `(ident->session-id form-ident)` throughout.

4. **Embedded forms**: Forms with `:embedded? true` skip routing. How do embedded forms start their statechart?
   - The `start-form!` function should work for this case, starting the chart directly

5. **Custom form machines**: Users who have overridden `fo/machine` with a custom UISM will need migration guidance. Their custom UISMs won't work with the new system.

6. **Query changes**: The current query includes `[::uism/asm-id '_]` for the UISM. The statechart equivalent query inclusion (if any) needs to be determined. The statechart session is at `[::sc/session-id session-id]` but may not need to be in the component query if we don't render based on chart state.

7. **Transition ordering for save validation**: The `:event/save` handler currently validates, then either saves or marks-complete-and-stays. In the statechart, we have two transitions: one with `:cond form-valid?` targeting `:state/saving`, and a second (unconditional) handling the invalid case. This relies on document-order evaluation of transitions.

## Dependencies

- **project-setup** (P1) - Statecharts dependency must be configured
- **routing-statechart** spec - Route integration details depend on the routing spec
- **public-api-mapping** spec - API surface decisions affect this spec

## Affected Modules

- `src/main/com/fulcrologic/rad/form.cljc` - Major rewrite: remove UISM, add statechart setup
- `src/main/com/fulcrologic/rad/form_machines.cljc` - Populate with statechart helpers
- `src/main/com/fulcrologic/rad/form_expressions.cljc` - NEW: expression functions
- `src/main/com/fulcrologic/rad/form_chart.cljc` - NEW: chart definition (or inline in form.cljc)
- `src/main/com/fulcrologic/rad/form_options.cljc` - Add statechart-related options, deprecate UISM ones

## Verification

1. [ ] All UISM states have equivalent statechart states
2. [ ] All UISM events have equivalent statechart events
3. [ ] All aliases map correctly to statechart aliases
4. [ ] Actor resolution works for form class and ident
5. [ ] Load flow: start -> loading -> loaded -> editing
6. [ ] Create flow: start -> creating -> editing
7. [ ] Save flow: editing -> saving -> (saved -> editing | save-failed -> editing)
8. [ ] Cancel flow: editing -> leaving -> exited
9. [ ] Undo resets form to pristine state
10. [ ] Dirty tracking works unchanged via fs/*
11. [ ] Subform add-child creates and merges default state
12. [ ] Subform delete-child removes child from parent relation
13. [ ] Route denied (sync) prompts user and either leaves or stays
14. [ ] Route denied (async) sets flag for UI rendering
15. [ ] Continue abandoned route re-routes after form reset
16. [ ] Custom form charts via `fo/machine` work
17. [ ] Public API functions (save!, cancel!, undo-all!, etc.) work unchanged
18. [ ] on-change trigger fires on field changes
19. [ ] derive-fields trigger runs after changes
20. [ ] Validation prevents save when form is invalid
21. [ ] Server errors display after save failure
22. [ ] Form works in embedded (non-routed) mode
23. [ ] All code is CLJC (headless testable)
24. [ ] `view-mode?` rewritten to read from statechart session data instead of UISM internal storage (breaking internal change)

## Revision History

- **R1**: Initial spec
- **R2**: Applied critique-round-1 fixes:
  - Fixed `fops/assoc-alias` to show keyword-argument pairs pattern
  - Standardized expression arity to 4-arg `(fn [env data event-name event-data] ...)` with `& _` convention
  - Fixed `fops/invoke-remote` first arg to be a txn vector `[(mutation-call {...})]`
  - Marked `view-mode?` as requiring full rewrite (not unchanged)
  - Added note about `:state/asking-to-discard-changes` as child state option
  - Committed to on-change trigger Option A with full signature
  - Referenced session-id-convention.md for session ID strategy
  - Referenced macro-rewrites.md for defsc-form changes
  - Noted that aliases resolve directly on `data` map (CC-5)
