# RAD Source Notes

## Design Decision: impl namespace delegation

`statechart/report.cljc` delegates 19 pure rendering/utility functions to `com.fulcrologic.rad.report.impl` via
`(def fn-name report-impl/fn-name)`. This accepts the transitive UISM dependency from `report.impl` but eliminates
~300 lines of duplicated code and fixes a `::form/id` namespace resolution bug (the local `form` alias resolved to
`com.fulcrologic.rad.statechart.form/id` instead of `com.fulcrologic.rad.form/id`, causing `form-link`, `link`, and
`genrow` to silently fail to find form IDs).

`statechart/form.cljc` still does NOT require `form.impl` — evaluate separately if the same delegation pattern
makes sense there.

## Dead Reference Cleanup (Phase 0)

The following namespaces were deleted and all references removed from remaining source:

- `authorization`, `authorization.simple-authorization` - auth/redaction framework
- `blob`, `blob-storage` - file upload/storage
- `routing`, `routing.base`, `routing.history`, `routing.html5-history` - RAD routing layer
- `rad-hooks` - hook system
- `ui-validation` - UI-level validation
- `form-machines` - form state machine defs
- `state-machines.*` - report state machine variants
- `dynamic.generator`, `dynamic.generator-options` - dynamic form/report generation
- `pathom3`, `resolvers-pathom3` - Pathom 3 resolver generation

## Routing (Statecharts)

`routing.cljc` has been deleted. Routing functions are now in their respective modules or used directly from
`com.fulcrologic.statecharts.integration.fulcro.routing` (`scr/`):

- `scr/route-to!`, `scr/route-back!`, `scr/route-forward!` — use directly from statecharts library
- `scr/force-continue-routing!`, `scr/abandon-route-change!` — use directly from statecharts library
- `form/form-route-state` — in `form.cljc`, creates `scr/rstate` with on-entry calling `form/start-form!` and on-exit
  calling `form/abandon-form!`
- `report/report-route-state` — in `report.cljc`, creates `scr/rstate` with on-entry calling `report/start-report!`
- `form/create!` / `form/edit!` — in `form.cljc`, convenience functions that route to a form with a tempid (create)
  or existing id (edit)
- These route-state functions use `entry-fn`/`exit-fn` macros from `statecharts.elements` as additional children of
  `scr/rstate` (SCXML allows multiple on-entry/on-exit blocks per state).

## Stubbed Functions (pending statechart conversion)

These functions were stripped of deleted-namespace dependencies and stubbed:

- `form/view!`, `form/edit!`, `form/create!` - log warning, no-op (were `rad-routing/route-to!`)
- `control/run!` - no-op (was `uism/trigger!` for `:event/run`)
- `control/set-parameter` mutation - removed route-param tracking
- `form/leave-form` - removed `rad-routing/route-to!` and `history/back!` paths
- `form` `:event/saved` - removed history replace-route
- `form` `:event/continue-abandoned-route` - removed history push/replace
- `report/initialize-parameters` - removed history-based param init
- `report/page-number-changed` - no-op (was route-param update)
- `report` `:initial` handler - removed history-based page detection
- `report` `:event/do-sort`, `:event/select-row` - removed route-param tracking
- `container/initialize-parameters` - removed history-based param init
- `resolvers-common/secure-resolver` - pass-through (was auth/redact wrapper)

## Multimethod Rendering Conversion

Map-based dispatch keys removed from source code:

- `::type->style->control` - was in control.cljc and form.cljc
- `::element->style->layout` - was in form.cljc
- `::style->layout` - was in report.cljc and container.cljc

### control.cljc

- `render-control` converted from function to defmulti dispatching on `[control-type style]`
- Uses `fr/render-hierarchy` from form_render.cljc (shared with form and report renderers)
- New signature: `[control-type style instance control-key]` (was `[owner control-key]` / `[owner control-key control]`)
- `:default` method logs a warning for missing renderers

### form.cljc

- `render-fn` replaced by `render-element` defmulti dispatching on `[element style]`
- `form-container-renderer`, `form-layout-renderer` removed (use `render-element` with `:form-container` /
  `:form-body-container`)
- `attr->renderer` simplified to delegate to `fr/render-field` multimethod
- `default-render-field` simplified to log error (plugins register via `defmethod fr/render-field`)
- `install-field-renderer!`, `install-form-container-renderer!`, `install-form-body-renderer!`,
  `install-form-ref-renderer!` removed
- Plugins should now use `defmethod` on `fr/render-field`, `fr/render-form`, and `form/render-element`

### report.cljc

- `default-render-layout` simplified to log error (plugins register via `defmethod rr/render-report`)
- `install-layout!` removed
- Plugins should use `defmethod rr/render-report` instead

### container.cljc

- `render-layout` simplified to log error (container rendering needs multimethod registration)

### Remaining map-based dispatch (not yet converted)

- `::row-style->row-layout` in report.cljc
- `::control-style->control` in report.cljc
- `::type->style->formatter` in report.cljc

## Form Statechart Conversion

### Architecture

- `form.cljc` — Contains statechart definition, all expression functions, reusable chart fragments, helper ops, and
  public API. Previously split across `form_chart.cljc`, `form_expressions.cljc`, and `form_machines.cljc` — all
  consolidated into `form.cljc` during Phase 9.

### Key Design Decisions
- **Session ID**: Uses `sc.session/ident->session-id` and `sc.session/form-session-id` to convert form idents to
  statechart session IDs
- **view-mode?**: Now reads from `[::sc/local-data session-id :options :action]` instead of UISM internal storage
- **form-allow-route-change**: Reads `:abandoned?` from `[::sc/local-data session-id :abandoned?]`
- **Chart registration**: `start-form!` registers `::form-chart` on first use if no custom chart specified
- **on-change trigger**: New breaking signature returns ops vector: `(fn [env data form-ident key old new] -> [ops])`
- **UISM fully removed**: All `uism` requires, `form-machine` defstatemachine, and UISM helper functions have been
  deleted. This is a new library — backward compat with old UISM code is not a goal.
- **invoke-remote for save**: Uses `fops/invoke-remote` with `[(list save-mutation params)]` vector pattern
- **Query change**: `[::uism/asm-id '_]` removed from generated form query
- **fo/\* option access**: NEVER use `::fo/keys` destructuring in expressions — `::fo/` resolves to `form-options` ns,
  but keys live in `form` ns. Use `(get opts fo/var-name)` instead.
- **form-ns keywords in expressions**: Use `(keyword "com.fulcrologic.rad.form" "name")` since `::form/name` would
  resolve to wrong ns
- **CLJ compat**: Use `rc/component-type` not `rc/react-type` (CLJS-only)

### UISM Removal Complete

- All `uism` requires removed from `form.cljc`, `report.cljc`, `debugging.cljc`, `application.cljc`
- `form-machine` defstatemachine and all UISM helper functions deleted from `form.cljc`
- `report-machine` defstatemachine and all UISM helper functions deleted from `report.cljc`
- `form/view!`, `form/edit!`, `form/create!` stubs removed — use `routing/edit!` and `routing/create!` instead
- `::uism/asm-id` in `application.cljc` blacklist replaced with raw qualified keyword
- UISM test in `form_spec.cljc` removed (tested deleted `form/start-create`)
- `dynamic-routing` requires removed from `form.cljc` and `report.cljc`

## Container Statechart Conversion

### Architecture

- `container.cljc` — Contains statechart definition, expression functions, and public API. Previously split across
  `container_chart.cljc` and `container_expressions.cljc` — consolidated into `container.cljc` during Phase 9.

### Key Design Decisions

- **Option A (side-effect)**: Children are started via `report/start-report!` side-effect in on-entry, not via
  statechart `invoke`
- **Session ID**: Uses `sc.session/ident->session-id` for the container, and
  `sc.session/ident->session-id (comp/get-ident child-class {::report/id id})` for children
- **Broadcast**: `broadcast-to-children!` uses `scf/send!` to each child's session
- **Cleanup**: on-exit of `:state/ready` sends `:event/unmount` to all children
- **UISM removed**: `container-machine`, `merge-children`, `start-children!`, `initialize-parameters` (UISM versions)
  all removed
- **New public fn**: `broadcast-to-children!` added to public API for external use
- **Macro updated**: `defsc-container` macro updated with sfro options (macro-rewrites spec)
- **No circular dep**: Container expression functions require `report.cljc` directly (report doesn't require container)

## Report Statechart Conversion

### Architecture

- `report.cljc` — Contains statechart definition, all shared expression functions, and public API. Previously split
  across `report_chart.cljc` and `report_expressions.cljc` — consolidated into `report.cljc` during Phase 9.
- `server_paginated_report.cljc` — Server-paginated report statechart (self-contained)
- `incrementally_loaded_report.cljc` — Incrementally-loaded report statechart (self-contained)

### Key Design Decisions

- **Expression helpers**: `report-class`, `actor-ident`, `resolve-alias-path`, `read-alias`,
  `current-control-parameters` provide data access without UISM env
- **Pure state-map functions**: `preprocess-raw-result`, `filter-rows-state`, `sort-rows-state`, `populate-page-state`
  take `(state-map data)` and return updated state-map, used via `fops/apply-action`
- **Observable intermediate states**: `:state/sorting` and `:state/filtering` are separate states with eventless
  transitions to `:state/ready`, enabling UI feedback
- **Session ID**: Uses `sc.session/ident->session-id` via `report-session-id` helper
- **`::sc/ok-event` not `::scf/ok-event`**: Load options use `com.fulcrologic.statecharts` namespace, not the Fulcro
  integration namespace
- **`rc/nc` not `comp/nc`**: Use `fulcro.raw.components/nc` for dynamic component creation
- **No `fops/send-self`**: Statecharts doesn't have a self-send operation; use `ops/assign` with a flag and conditional
  eventless transitions instead (see incrementally-loaded report)
- **`::machine` option key**: `ro/machine` is deprecated; `ro/statechart` now exists and the macro generates sfro
  options from it
- **UISM fully removed**: Old `report-machine` and all UISM handlers deleted from `report.cljc`
- **Server-paginated aliases**: Adds `:page-cache`, `:loaded-page`, `:total-results`, `:point-in-time` aliases beyond
  the standard set
- **Incrementally-loaded data model**: Uses `ops/assign` for `:last-load-time` and `:raw-items-in-table` (session-local
  cache tracking)

### Critical Bug Fix: `fops/apply-action` with `(constantly snapshot)` (Feb 2026)

- **NEVER** use `(fops/apply-action (constantly snapshot))` where `snapshot` is a captured state-map
- `fops/apply-action` runs via `transact!!` containing `do-apply-action` mutation, which does
  `(swap! state (fn [s] (apply f s args)))`
- If `f` is `(constantly snapshot)`, it replaces the ENTIRE Fulcro state including statechart working memory
- In headless/immediate mode, the ok-action callback may have already updated working memory (e.g. #{:state/ready})
  between when the snapshot was captured and when the deferred `transact!!` executes
- The snapshot overwrites working memory back to its old value (e.g. #{:state/loading}), causing the statechart to
  appear stuck
- **Fix**: Pass a transform function that applies changes to the current state at swap-time, not a snapshot replacement
- Example: `(fops/apply-action (fn [current] (-> current (transform1 data) (transform2 data))))` instead of
  `(fops/apply-action (constantly pre-computed))`

## Macro Rewrites (Statecharts Routing Integration)

### defsc-form

- **Query**: `[::uism/asm-id '_]` already removed (from form-statechart conversion)
- **Component options**: Generates `sfro/statechart` (or `sfro/statechart-id` for keyword), `sfro/busy?` → `form-busy?`,
  `sfro/initialize` → `:always`
- **Removed**: `:route-segment`, `:will-enter`, `:will-leave`, `:allow-route-change?`, `:route-denied`
- **`form-busy?`**: New function that checks `fs/dirty?` on the form actor
- **`fo/statechart`**: New option (keyword `:com.fulcrologic.rad.form/statechart`) for user-specified statecharts
- **Compile-time warning**: Emitted when `:will-enter` is specified

### defsc-report

- **Query**: `[::uism/asm-id [::id fqkw]]` removed
- **Component options**: Generates `sfro/statechart` (or `sfro/statechart-id`), `sfro/initialize` → `:once`
- **Removed**: `:will-enter`, `:route-segment`
- **`ro/statechart`**: New option (keyword `:com.fulcrologic.rad.report/statechart`)

### defsc-container

- **Component options**: Generates `sfro/statechart` (or `sfro/statechart-id`), `sfro/initialize` → `:once`
- **Removed**: `:will-enter`, `:route-segment`
- **Compile-time warning**: Emitted when `:will-enter` is specified

### sfro Namespace

- Require: `[com.fulcrologic.statecharts.integration.fulcro.routing-options :as sfro]`
- Keys: `sfro/initialize`, `sfro/busy?`, `sfro/statechart`, `sfro/statechart-id`, `sfro/actors`, `sfro/initial-props`
