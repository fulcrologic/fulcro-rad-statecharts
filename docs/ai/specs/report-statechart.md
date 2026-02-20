# Spec: Report Statechart Conversion

**Status**: active
**Priority**: P0
**Created**: 2026-02-20
**Owner**: AI
**Depends-on**: project-setup, session-id-convention, macro-rewrites

## Context

RAD reports currently use Fulcro UI State Machines (UISMs) to manage data loading, filtering, sorting, pagination, and route integration. There are three report state machine variants:

1. **`report-machine`** (report.cljc) -- Standard client-side report: loads all data, filters/sorts/paginates on the client
2. **`server-paginated-report/machine`** -- Server-side pagination/sorting/filtering for large datasets
3. **`incrementally-loaded-report/incrementally-loaded-machine`** -- Loads data in chunks to avoid timeouts, then processes client-side

All three must be converted to statecharts. The public API (`run-report!`, `filter-rows!`, `sort-rows!`, `goto-page!`, etc.) must remain as similar as possible.

## Current UISM Analysis

### report-machine (Standard)

**Actor**: `:actor/report` (the report component)

**Aliases** (UISM paths into Fulcro state):

| Alias | Path | Purpose |
|-------|------|---------|
| `:parameters` | `[:actor/report :ui/parameters]` | All report parameters |
| `:sort-params` | `[:actor/report :ui/parameters ::sort]` | Sort config map |
| `:sort-by` | `[:actor/report :ui/parameters ::sort :sort-by]` | Current sort column |
| `:ascending?` | `[:actor/report :ui/parameters ::sort :ascending?]` | Sort direction |
| `:filtered-rows` | `[:actor/report :ui/cache :filtered-rows]` | Post-filter cache |
| `:sorted-rows` | `[:actor/report :ui/cache :sorted-rows]` | Post-sort cache |
| `:raw-rows` | `[:actor/report :ui/loaded-data]` | Raw loaded data |
| `:current-rows` | `[:actor/report :ui/current-rows]` | Displayed rows |
| `:current-page` | `[:actor/report :ui/parameters ::current-page]` | Page number |
| `:selected-row` | `[:actor/report :ui/parameters ::selected-row]` | Selected row index |
| `:page-count` | `[:actor/report :ui/page-count]` | Total pages |
| `:busy?` | `[:actor/report :ui/busy?]` | Busy indicator |

**States**:

1. `:initial` -- On start: stores route params, initializes parameters, either loads data (`run-on-mount?`) or goes to `:state/gathering-parameters`
2. `:state/loading` -- Waiting for remote data. Events:
   - `:event/loaded` -- Preprocess, filter, sort, paginate, store cache timestamps, transition to `:state/gathering-parameters`
   - `:event/failed` -- Log error, transition to `:state/gathering-parameters`
3. `:state/gathering-parameters` -- Main interactive state. Events:
   - `:event/goto-page`, `:event/next-page`, `:event/prior-page` -- Pagination
   - `:event/sort`, `:event/do-sort` -- Sort (two-phase: set busy, then sort)
   - `:event/filter`, `:event/do-filter` -- Filter (two-phase: set busy, then filter)
   - `:event/select-row` -- Select a row by index
   - `:event/set-ui-parameters` -- Re-initialize parameters
   - `:event/run` -- Reload from server
   - `:event/resume` -- Re-enter a previously loaded report (check cache)
   - `:event/clear-sort` -- Remove sort (global event available in all states)

**Key Logic Functions**:
- `initialize-parameters` -- Merges route params, URL history params, control defaults into state
- `load-report!` -- Issues `uism/load` with control params, targets `:ui/loaded-data`
- `filter-rows` -- Applies `ro/row-visible?` predicate, writes to `:filtered-rows` cache
- `sort-rows` -- Applies `ro/compare-rows`, writes to `:sorted-rows` cache
- `populate-current-page` -- Slices sorted rows by page, writes to `:current-rows`
- `report-cache-expired?` -- Checks timestamps and table counts for staleness
- `handle-resume-report` -- Re-initializes params, reloads if cache expired, else re-filters

### server-paginated-report/machine

**Additional Aliases** (beyond standard):

| Alias | Path | Purpose |
|-------|------|---------|
| `:point-in-time` | `[:actor/report :ui/point-in-time]` | Snapshot timestamp |
| `:total-results` | `[:actor/report :ui/total-results]` | Server total count |
| `:loaded-page` | `[:actor/report :ui/cache :loaded-page]` | Current page data from server |
| `:page-cache` | `[:actor/report :ui/cache :page-cache]` | Cached pages by number |

**Key Differences**:
- Each page is loaded separately from server with `:indexed-access/options` params (limit, offset, sort-column, reverse?, point-in-time, include-total?)
- Pages are cached client-side; navigating to a cached page avoids re-load
- Sort/filter changes trigger server re-query (not client-side processing)
- Supports `point-in-time` for consistent pagination views
- No client-side `filter-rows`/`sort-rows` -- server does it

### incrementally-loaded-report/incrementally-loaded-machine

**Additional Alias**: `:loaded-page` at `[:actor/report :ui/incremental-page]`

**Key Differences**:
- Built by modifying `report-machine` via `assoc-in` on the machine map
- Loads data in chunks (`:report/offset` + `:report/limit` params)
- Server returns `{:report/next-offset n :report/results data}`
- Keeps loading pages until `next-offset` is zero/nil
- Then triggers `:event/loaded` to finalize (same as standard report: filter/sort/paginate client-side)
- Supports same cache expiration as standard report

## Proposed Statechart Structure

### Standard Report Statechart

```clojure
(statechart {:id ::report-chart :initial :state/initializing}
  (data-model {:expr (fn [_ _]
                       {:current-page 1
                        :page-count   1
                        :selected-row -1
                        :busy?        false})})

  ;; Actor: :actor/report mapped to the report component

  (state {:id :state/initializing}
    (on-entry {}
      (script {:expr initialize-params-expr}))
    ;; Decision: run on mount or wait?
    (transition {:cond should-run-on-mount? :target :state/loading})
    (transition {:target :state/ready}))

  (state {:id :state/loading}
    (on-entry {}
      (script {:expr start-load-expr}))  ;; fops/load instead of uism/load
    (on :event/loaded :state/processing)
    (on :event/failed :state/ready))

  ;; Transient processing state -- runs synchronously and transitions out
  (state {:id :state/processing}
    (on-entry {}
      (script {:expr process-loaded-data-expr}))  ;; preprocess, filter, sort, paginate
    (transition {:target :state/ready}))

  (state {:id :state/ready}
    ;; Pagination
    (handle :event/goto-page goto-page-expr)
    (handle :event/next-page next-page-expr)
    (handle :event/prior-page prior-page-expr)

    ;; Sort -> intermediate observable state
    (on :event/sort :state/sorting
      (script {:expr store-sort-params-expr}))

    ;; Filter -> intermediate observable state
    (on :event/filter :state/filtering)

    ;; Row selection
    (handle :event/select-row select-row-expr)

    ;; Parameter management
    (handle :event/set-ui-parameters set-params-expr)

    ;; Reload
    (on :event/run :state/loading)

    ;; Resume (re-mount)
    (transition {:event :event/resume :cond cache-expired? :target :state/loading}
      (script {:expr reinitialize-params-expr}))
    (transition {:event :event/resume}
      (script {:expr resume-from-cache-expr}))  ;; re-filter and re-paginate

    ;; Clear sort (available everywhere)
    (handle :event/clear-sort clear-sort-expr))

  ;; Observable intermediate state for sorting -- UI can show loading indicator
  (state {:id :state/sorting}
    (on-entry {}
      (script {:expr (fn [env data & _] [(fops/assoc-alias :busy? true)])}))
    (transition {:target :state/ready}
      (script {:expr do-sort-and-clear-busy-expr})))

  ;; Observable intermediate state for filtering -- UI can show loading indicator
  (state {:id :state/filtering}
    (on-entry {}
      (script {:expr (fn [env data & _] [(fops/assoc-alias :busy? true)])}))
    (transition {:target :state/ready}
      (script {:expr do-filter-and-clear-busy-expr}))))
```

### Two-Phase Sort/Filter Pattern

The current UISM uses `uism/trigger!` within a handler to self-send `:event/do-sort` after setting `:busy? true`. This allows the UI to render the busy state before the potentially expensive sort runs.

**DECIDED: Use observable intermediate state (NOT self-send).** The statechart uses an intermediate state like `:state/processing` (or `:state/sorting` / `:state/filtering`) that is observable by the UI. This allows the UI to show a loading animation while sorting/filtering happens, matching how the original UISM worked. The intermediate state sets busy on entry, performs the sort/filter, and transitions back to `:state/ready`:

```clojure
(state {:id :state/sorting}
  (on-entry {}
    (script {:expr (fn [env data & _] [(fops/assoc-alias :busy? true)])}))
  ;; Eventless transition fires after entry actions complete and UI has rendered busy state
  (transition {:target :state/ready}
    (script {:expr do-sort-and-clear-busy-expr})))

(state {:id :state/filtering}
  (on-entry {}
    (script {:expr (fn [env data & _] [(fops/assoc-alias :busy? true)])}))
  (transition {:target :state/ready}
    (script {:expr do-filter-and-clear-busy-expr})))
```

This approach is preferred because:
1. The intermediate state is observable -- the UI can check if the chart is in `:state/sorting` or `:state/filtering` and show appropriate loading indicators
2. It matches the original UISM behavior where the busy state was visible to the UI between the two phases
3. It is more "statechart-native" -- state transitions are explicit rather than relying on self-send timing

### Server-Paginated Report Statechart

```clojure
(statechart {:id ::server-paginated-report-chart :initial :state/initializing}
  (data-model {:expr (fn [_ _]
                       {:current-page   1
                        :page-count     1
                        :selected-row   -1
                        :busy?          false
                        :point-in-time  nil
                        :total-results  nil
                        :page-cache     {}})})

  (state {:id :state/initializing}
    (on-entry {}
      (script {:expr server-paginated-init-expr}))
    (transition {:cond should-run-on-mount? :target :state/loading-page})
    (transition {:target :state/ready}))

  (state {:id :state/loading-page}
    (on-entry {}
      (script {:expr load-server-page-expr}))  ;; fops/load with indexed-access/options
    (on :event/page-loaded :state/processing-page)
    (on :event/failed :state/ready))

  (state {:id :state/processing-page}
    (on-entry {}
      (script {:expr process-server-page-expr}))  ;; merge results, update page-cache
    (transition {:target :state/ready}))

  (state {:id :state/ready}
    ;; Page navigation: check cache first, else load
    (transition {:event :event/goto-page :cond page-cached? :target :state/ready}
      (script {:expr serve-cached-page-expr}))
    (transition {:event :event/goto-page :target :state/loading-page}
      (script {:expr set-target-page-expr}))

    ;; Sort triggers full reload (server sorts)
    (transition {:event :event/sort :target :state/loading-page}
      (script {:expr update-sort-and-refresh-expr}))

    ;; Filter triggers full reload (server filters)
    (transition {:event :event/filter :target :state/loading-page}
      (script {:expr refresh-expr}))

    (handle :event/select-row select-row-expr)
    (on :event/run :state/loading-page)

    (transition {:event :event/resume :target :state/loading-page}
      (script {:expr resume-server-paginated-expr}))))
```

### Incrementally-Loaded Report Statechart

```clojure
(statechart {:id ::incrementally-loaded-report-chart :initial :state/initializing}
  ;; Same data-model as standard, plus :loaded-page

  (state {:id :state/initializing}
    (on-entry {}
      (script {:expr incremental-init-expr}))
    (transition {:cond should-run-on-mount? :target :state/loading-chunk})
    (transition {:target :state/ready}))

  (state {:id :state/loading-chunk}
    (on-entry {}
      (script {:expr load-chunk-expr}))  ;; load with current offset
    (on :event/page-loaded :state/processing-chunk)
    (on :event/failed :state/ready))

  (state {:id :state/processing-chunk}
    (on-entry {}
      (script {:expr process-chunk-expr}))  ;; append results
    ;; If more data, go back to loading
    (transition {:cond more-chunks? :target :state/loading-chunk})
    ;; If done, finalize
    (transition {:target :state/finalizing}))

  (state {:id :state/finalizing}
    (on-entry {}
      (script {:expr finalize-incremental-report-expr}))  ;; filter/sort/paginate like standard
    (transition {:target :state/ready}))

  (state {:id :state/ready}
    ;; Full event list (same as standard report's :state/ready)
    (handle :event/goto-page goto-page-expr)
    (handle :event/next-page next-page-expr)
    (handle :event/prior-page prior-page-expr)
    (on :event/sort :state/sorting
      (script {:expr store-sort-params-expr}))
    (on :event/filter :state/filtering)
    (handle :event/select-row select-row-expr)
    (handle :event/set-ui-parameters set-params-expr)
    (on :event/run :state/loading-chunk)
    (transition {:event :event/resume :cond cache-expired? :target :state/loading-chunk}
      (script {:expr reinitialize-params-expr}))
    (transition {:event :event/resume}
      (script {:expr resume-from-cache-expr}))
    (handle :event/clear-sort clear-sort-expr))

  ;; Observable intermediate states (same as standard report)
  (state {:id :state/sorting}
    (on-entry {}
      (script {:expr (fn [env data & _] [(fops/assoc-alias :busy? true)])}))
    (transition {:target :state/ready}
      (script {:expr do-sort-and-clear-busy-expr})))

  (state {:id :state/filtering}
    (on-entry {}
      (script {:expr (fn [env data & _] [(fops/assoc-alias :busy? true)])}))
    (transition {:target :state/ready}
      (script {:expr do-filter-and-clear-busy-expr}))))
```

## Actor/Alias Mapping

### UISM Actors to Statechart Actors

| UISM Actor | Statechart Actor | Notes |
|-----------|-----------------|-------|
| `:actor/report` | `:actor/report` | Same. Defined via `(scf/actor ReportClass)` at `scf/start!` |

### UISM Aliases to Statechart Aliases

Statechart aliases are defined at `scf/start!` time in the `:data` map:

```clojure
(scf/start! app
  {:machine    ::report-chart
   :session-id (report-session-id report-class)
   :data       {:fulcro/actors  {:actor/report (scf/actor ReportClass report-ident)}
                :fulcro/aliases {:parameters    [:actor/report :ui/parameters]
                                 :sort-params   [:actor/report :ui/parameters ::sort]
                                 :sort-by       [:actor/report :ui/parameters ::sort :sort-by]
                                 :ascending?    [:actor/report :ui/parameters ::sort :ascending?]
                                 :filtered-rows [:actor/report :ui/cache :filtered-rows]
                                 :sorted-rows   [:actor/report :ui/cache :sorted-rows]
                                 :raw-rows      [:actor/report :ui/loaded-data]
                                 :current-rows  [:actor/report :ui/current-rows]
                                 :current-page  [:actor/report :ui/parameters ::current-page]
                                 :selected-row  [:actor/report :ui/parameters ::selected-row]
                                 :page-count    [:actor/report :ui/page-count]
                                 :busy?         [:actor/report :ui/busy?]}}})
```

All alias reads are available directly on `data` (the Fulcro data model auto-resolves aliases -- see CC-5). They can also be read explicitly via `(scf/resolve-aliases data)`.
All alias writes use `(fops/assoc-alias :alias value)` with keyword-argument pairs (can set multiple: `(fops/assoc-alias :busy? true :current-page 1)`).

### `report-session-id` Definition

See `session-id-convention.md` for the full convention. Report session IDs are derived from the report ident:

```clojure
(defn report-session-id
  "Returns the statechart session ID for a report instance or class."
  ([report-instance]
   (ident->session-id (comp/get-ident report-instance)))
  ([report-class]
   (ident->session-id (comp/get-ident report-class {}))))
```

This produces a keyword like `:com.fulcrologic.rad.sc/report_id--myapp.ui_AccountList` which satisfies `::sc/id`.

### Data Storage Strategy

**All report data is stored in Fulcro state via aliases.** This ensures a single source of truth that components can read at render time. Specifically:
- Raw rows, filtered rows, sorted rows, current rows: all via aliases pointing into the report component's Fulcro state
- Pagination metadata (current-page, page-count): via aliases
- Sort params: via aliases

`ops/assign` is reserved for statechart-internal data that does NOT need to be rendered (e.g., cache timestamps used in `cache-expired?` checks). For server-paginated reports, the page cache is stored via aliases at `[:actor/report :ui/cache :page-cache]` rather than in session data, to maintain the single-source-of-truth principle.

## Event Mapping

| UISM Event | Statechart Event | Notes |
|-----------|-----------------|-------|
| `::uism/started` (implicit) | Statechart `:initial` state | Handled by `on-entry` of initial state |
| `:event/loaded` | `:event/loaded` | Same name, load ok-event |
| `:event/failed` | `:event/failed` | Same name, load error-event |
| `:event/goto-page` | `:event/goto-page` | `{:page n}` in event data |
| `:event/next-page` | `:event/next-page` | No data |
| `:event/prior-page` | `:event/prior-page` | No data |
| `:event/sort` | `:event/sort` | `{::attr/attribute attr}` in event data. Transitions to `:state/sorting` |
| `:event/do-sort` | (removed) | No longer needed -- sort runs in `:state/sorting` intermediate state |
| `:event/filter` | `:event/filter` | No data. Transitions to `:state/filtering` |
| `:event/do-filter` | (removed) | No longer needed -- filter runs in `:state/filtering` intermediate state |
| `:event/select-row` | `:event/select-row` | `{:row idx}` in event data |
| `:event/set-ui-parameters` | `:event/set-ui-parameters` | Route param update |
| `:event/run` | `:event/run` | Reload from server |
| `:event/resume` | `:event/resume` | Re-mount existing report |
| `:event/clear-sort` | `:event/clear-sort` | Reset sort |
| (server-paginated) `:event/page-loaded` | `:event/page-loaded` | Server page result |

## Remote Data Loading

### Current (UISM)

```clojure
(uism/load source-attribute BodyItem
  {:params            current-params
   ::uism/ok-event    :event/loaded
   ::uism/error-event :event/failed
   :marker            report-ident
   :target            path})
```

### Proposed (Statechart)

```clojure
;; All expressions use 4-arg convention per install-fulcro-statecharts! docs
(fn [env data _event-name _event-data]
  (let [{:keys [source-attribute BodyItem]} (report-config env data)
        current-params (current-control-parameters env data)
        report-ident   (actor-ident data :actor/report)
        path           (conj report-ident :ui/loaded-data)]
    [(fops/load source-attribute BodyItem
       {::sc/ok-event    :event/loaded
        ::sc/error-event :event/failed
        :marker          report-ident
        :target          path
        :params          current-params})]))
```

Key change: `uism/load` becomes `fops/load` from `com.fulcrologic.statecharts.integration.fulcro.operations`.

## Pagination Logic

Client-side pagination remains the same algorithm:
1. Take `:sorted-rows`
2. Calculate page boundaries from `:current-page` and `page-size`
3. `subvec` the rows
4. Assign to `:current-rows`

This is pure data manipulation, unchanged between UISM and statecharts. The only difference is how state is read/written (aliases vs ops).

For server-paginated: page cache is stored in Fulcro state via the `:page-cache` alias (at `[:actor/report :ui/cache :page-cache]`). The `goto-page` event checks the cache first, loads from server only if the page isn't cached. This keeps all data in Fulcro state (single source of truth) rather than splitting between session data and Fulcro state.

## Sorting and Filtering

Sorting and filtering are pure functions that operate on vectors of row data. They remain unchanged algorithmically. The statechart versions will:

1. Read raw rows via `(scf/resolve-aliases data)` or by reading the Fulcro state map
2. Apply `ro/row-visible?` (filter) and `ro/compare-rows` (sort)
3. Write results via `fops/assoc-alias`

The two-phase sort/filter pattern (set busy, then process) uses **observable intermediate states** (`:state/sorting`, `:state/filtering`). The intermediate state sets `:busy? true` on entry, then an eventless transition performs the actual sort/filter and transitions back to `:state/ready`. This makes the processing phase observable by the UI for loading indicators, matching the original UISM behavior.

## Route Integration

### Current

Reports use Fulcro dynamic routing (`dr/route-deferred`, `will-enter`). The report's `start-report!` function calls `uism/begin!` or `uism/trigger!` for resume.

Route params (page, sort, selected-row) are tracked in URL via `rad-routing/update-route-params!`.

### Proposed

Reports will use statecharts routing instead of dynamic routing. The `will-enter` lifecycle is replaced by statechart route states (using `rstate` from the patterns).

Route param tracking stays the same -- `rad-routing/update-route-params!` is called from within statechart expressions (via side effects or `fops/apply-action`).

```clojure
;; Start: instead of uism/begin!, use scf/start!
(defn start-report! [app report-class options]
  (let [session-id (report-session-id report-class options)
        machine-key (or (comp/component-options report-class ro/statechart) ::report-chart)]
    (scf/start! app
      {:machine    machine-key
       :session-id session-id
       :data       (report-actor-data report-class options)})))

;; Resume: send event to existing session
(defn resume-report! [app report-class options]
  (let [session-id (report-session-id report-class options)]
    (scf/send! app session-id :event/resume options)))
```

## Control Integration

Controls (buttons, inputs) are defined via `ro/controls` / `::control/controls`. They appear in the report's query and render via the UI plugin.

Controls interact with the report by:
1. **Buttons with `:action`**: Call functions like `(report/run-report! this)` which sends events to the UISM/statechart
2. **Inputs with `:onChange`**: Call functions like `(report/filter-rows! this)` or `(control/set-parameter! this k v)`

In the statechart version:
- `run-report!` becomes `(scf/send! app session-id :event/run)`
- `filter-rows!` becomes `(scf/send! app session-id :event/filter)`
- `sort-rows!` becomes `(scf/send! app session-id :event/sort {...})`
- `goto-page!` becomes `(scf/send! app session-id :event/goto-page {:page n})`

The public API functions (`run-report!`, `filter-rows!`, `sort-rows!`, `goto-page!`, `next-page!`, `prior-page!`, `select-row!`) will be thin wrappers around `scf/send!`.

### Control Parameter Storage

Global controls are stored at `[::control/id control-key ::control/value]` in the Fulcro state map. Local controls are stored at `[report-ident :ui/parameters control-key]`. This storage pattern is independent of UISM/statecharts and can remain the same -- the statechart expressions read/write these paths via `fops/apply-action`.

## Custom State Machine Override

Currently, reports can override the machine via `ro/statechart`. The statechart equivalent:

```clojure
(defsc-report MyReport [this props]
  {ro/statechart ::my-custom-chart  ;; keyword of registered statechart
   ...})
```

The `defsc-report` macro reads `ro/statechart` and sets it as `sfro/statechart` (or `sfro/statechart-id` if it's a keyword). See `macro-rewrites.md` for the full macro rewrite specification.

**How custom charts are specified:**

```clojure
;; Pattern 1: Inline chart definition
(defsc-report MyReport [this props]
  {ro/statechart my-custom-report-chart  ;; a statechart definition
   ...})
;; Macro sets: sfro/statechart my-custom-report-chart

;; Pattern 2: Pre-registered chart ID
(defsc-report MyReport [this props]
  {ro/statechart ::my-custom-chart  ;; keyword of pre-registered chart
   ...})
;; Macro sets: sfro/statechart-id ::my-custom-chart

;; Pattern 3: Default (no ro/statechart)
(defsc-report MyReport [this props]
  {...})
;; Macro sets: sfro/statechart report/report-statechart
```

Users who extend the default `report-machine` by `assoc-in` on the machine map (as `incrementally-loaded-machine` does) can instead compose statecharts using standard statechart composition patterns (shared expression functions with different chart structures).

## Affected Modules

- `com.fulcrologic.rad.report` - Main conversion: replace `defstatemachine report-machine` with `(statechart ...)`, update `start-report!`, all public API functions
- `com.fulcrologic.rad.report-options` - `ro/statechart` semantics change (now a statechart registry key)
- `com.fulcrologic.rad.state-machines.server-paginated-report` - Full rewrite as statechart
- `com.fulcrologic.rad.state-machines.incrementally-loaded-report` - Full rewrite as statechart
- `com.fulcrologic.rad.control` - May need minor updates for `scf/send!` instead of `uism/trigger!`
- `com.fulcrologic.rad.container` - Container coordinates reports; needs statechart-aware child management

## Approach

### Phase 1: Standard Report

1. Define the report statechart in `report.cljc`
2. Port all expression functions (initialize-parameters, load-report!, filter-rows, sort-rows, populate-current-page, etc.)
3. Update `start-report!` to use `scf/start!`
4. Update all public API functions to use `scf/send!`
5. Update `defsc-report` macro to remove UISM query inclusion, add statechart session query
6. Write tests

### Phase 2: Server-Paginated

1. Define server-paginated statechart (separate chart, not parameterized)
2. Port page cache logic, point-in-time, indexed-access params
3. Write tests

### Phase 3: Incrementally-Loaded

1. Define incrementally-loaded statechart (separate chart)
2. Port chunk loading loop
3. Write tests

## Open Questions

1. **Session ID strategy**: **Resolved.** Report session IDs are deterministic via `report-session-id` (see session-id-convention.md). Uses `(ident->session-id (comp/get-ident report-class {}))` to produce a keyword from the report ident.

2. **DECIDED: Two-phase sort/filter uses observable intermediate state (NOT self-send).** The statechart uses intermediate states (`:state/sorting`, `:state/filtering`) that are observable by the UI. The intermediate state sets `:busy? true` on entry, performs the sort/filter, and transitions back to `:state/ready`. This matches how the original UISM worked and allows the UI to show loading indicators.

3. **DECIDED: Report variants use separate charts with shared expressions.** Each variant (standard, server-paginated, incrementally-loaded) is a completely separate statechart definition, but they share expression functions from a common namespace. This avoids the `assoc-in` modification pattern that statecharts don't support, while keeping expression logic DRY.

4. **Routing integration**: How does the statecharts routing system handle the `will-enter` / `route-deferred` pattern? This depends on the routing spec. Reports need to know when they are the target of navigation to begin loading.

5. **Container coordination**: Containers start child reports with `::report/externally-controlled? true`. How should this flag be communicated to the report statechart? Via invocation data at `scf/start!` time seems natural.

6. **Query inclusion**: **Resolved.** The `defsc-report` macro removes `[::uism/asm-id ...]` from the query. No statechart session-id query is needed because statechart working memory is stored at `[::sc/session-id session-id]` (a separate table), not in the component's props. See `macro-rewrites.md`.

7. **Load marker compatibility**: Reports use `df/marker-table` with the report ident as the marker key. This is independent of UISM/statecharts but needs verification that `fops/load` supports the `:marker` option.

## Verification

1. [ ] Standard report loads data on mount when `ro/run-on-mount?` is true
2. [ ] Standard report waits for user action when `ro/run-on-mount?` is false
3. [ ] Filtering works with `ro/row-visible?`
4. [ ] Sorting works with `ro/compare-rows` and toggles ascending/descending
5. [ ] Pagination correctly slices rows and navigates pages
6. [ ] Selected row tracking works
7. [ ] Route params (page, sort) are tracked in URL when `ro/track-in-url?` is true
8. [ ] Report resume uses cache when valid, reloads when expired
9. [ ] Server-paginated report loads pages on demand with correct indexed-access params
10. [ ] Server-paginated report caches pages and serves from cache on back-navigation
11. [ ] Incrementally-loaded report loads all chunks before processing
12. [ ] Custom machine override via `ro/statechart` works with statechart registry keys
13. [ ] Public API functions (`run-report!`, `filter-rows!`, etc.) work unchanged for callers
14. [ ] Controls (buttons, inputs) correctly send events to report statechart
15. [ ] All three variants are headless-testable (CLJC)
16. [ ] Load markers work correctly for loading indicators

## Revision History

- **R1**: Initial spec
- **R2**: Applied critique-round-1 fixes:
  - Fixed expression arity to 4-arg convention with `& _` pattern
  - Defined `report-session-id` (referencing session-id-convention.md)
  - Clarified data storage strategy (prefer Fulcro state via aliases consistently; `ops/assign` only for non-rendered internal data)
  - Referenced macro-rewrites.md for defsc-report changes
  - Fleshed out incrementally-loaded report events (full event list in `:state/ready`)
  - Specified `ro/statechart` override mechanics (inline chart, pre-registered keyword, or default)
