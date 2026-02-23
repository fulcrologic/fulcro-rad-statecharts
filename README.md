# fulcro-rad-statecharts

A statecharts-based engine for [Fulcro RAD](https://github.com/fulcrologic/fulcro-rad) that replaces UI State Machines (UISM) and Dynamic Router with [fulcrologic/statecharts](https://github.com/fulcrologic/statecharts) for all form, report, and routing lifecycle management. Everything is CLJC — the full application lifecycle is testable headlessly from a plain Clojure REPL.

[![Clojars Project](https://img.shields.io/clojars/v/com.fulcrologic/fulcro-rad-statecharts.svg)](https://clojars.org/com.fulcrologic/fulcro-rad-statecharts)

---

## CRITICAL: What NOT to Use from Upstream

You will have both `fulcro-rad` and `fulcro-rad-statecharts` on your classpath. **Do not use these upstream namespaces** — they use the UISM or Dynamic Router engine and will conflict with or bypass the statecharts engine.

| Do NOT use | Why | Use instead |
|---|---|---|
| `com.fulcrologic.rad.form` | UISM engine — conflicts with statecharts | `com.fulcrologic.rad.statechart.form` |
| `com.fulcrologic.rad.report` | UISM engine | `com.fulcrologic.rad.statechart.report` |
| `com.fulcrologic.rad.routing` | Dynamic Router based | `scr/route-to!` from statecharts; `form/create!`, `form/edit!` |
| `com.fulcrologic.rad.authorization` | UISM-based auth system | Not provided — handle auth separately |
| `com.fulcrologic.fulcro.ui-state-machines` | UISM library | `com.fulcrologic.statecharts.*` |
| `com.fulcrologic.fulcro.routing.dynamic-routing` | DR-based routing | Statecharts routing |
| `fo/triggers` (UISM callback shape) | Wrong callback signature | `sfo/triggers` (returns ops vector) |
| `fo/machine` | UISM machine reference | `sfo/statechart` |
| `rad-hooks` (`use-form`, `use-report`) | React hooks for data | Statecharts manage lifecycle |
| `install-form-container-renderer!` etc. | Map-based dispatch (removed) | `defmethod render-element` |
| `com.fulcrologic.rad.form.impl` | Internal to upstream. Transitively depends on UISM via rad.routing. | `com.fulcrologic.rad.statechart.form` |
| `com.fulcrologic.rad.report.impl` | Internal to upstream. Transitively depends on UISM. | `com.fulcrologic.rad.statechart.report` |

---

## What IS Safe to Use from Upstream

The following upstream `fulcro-rad` namespaces are shared and safe to use:

- `com.fulcrologic.rad.attributes` and `attributes-options` — attribute definitions (`defattr`)
- `com.fulcrologic.rad.form-options` (`fo/`) — shared option keys (`fo/id`, `fo/attributes`, `fo/subforms`, `fo/layout`, etc.)
- `com.fulcrologic.rad.report-options` (`ro/`) — shared report option keys
- `com.fulcrologic.rad.form-render` / `report-render` — rendering multimethods (`fr/render-field`, `rr/render-report`)
- `com.fulcrologic.rad.application` — `fulcro-rad-app`, `install-ui-controls!` (also re-exported from `statechart.application`)
- `com.fulcrologic.rad.picker-options` — picker infrastructure (`po/query-key`, `po/load-options!`, etc.)
- All type support: `date-time`, `decimal`, `integer`
- All middleware: `save-middleware`, `autojoin`
- All Pathom/resolver infrastructure
- `com.fulcrologic.rad.ids`, `locale`, `errors`, `options-util`

---

## Dependencies

```clojure
;; deps.edn
{:deps {com.fulcrologic/fulcro                  {:mvn/version "3.9.3"}
        com.fulcrologic/fulcro-rad              {:mvn/version "LATEST"}
        com.fulcrologic/fulcro-rad-statecharts  {:mvn/version "LATEST"}
        com.fulcrologic/statecharts             {:mvn/version "1.4.0-RC1"
                                                 :exclusions  [com.fulcrologic/fulcro-rad]}}}
```

Requires Fulcro 3.9.3+ and Clojure 1.11.4+.

---

## Quick Start

### 1. Define Attributes

Attributes are unchanged from standard RAD:

```clojure
(ns com.example.model.account
  (:require
   [com.fulcrologic.rad.attributes :as attr :refer [defattr]]
   [com.fulcrologic.rad.attributes-options :as ao]))

(defattr id :account/id :uuid
  {ao/identity? true
   ao/schema    :production})

(defattr name :account/name :string
  {ao/identities #{:account/id}
   ao/schema     :production})
```

### 2. Define a Form

Use the three-require pattern: `statechart.form` for the engine, `form-options` (`fo/`) for shared keys, `statechart.form-options` (`sfo/`) for engine-specific keys.

```clojure
(ns com.example.ui.account-forms
  (:require
   [com.example.model.account :as account]
   [com.fulcrologic.rad.statechart.form :as form]          ; engine
   [com.fulcrologic.rad.form-options :as fo]               ; shared keys
   [com.fulcrologic.rad.statechart.form-options :as sfo])) ; engine-specific keys

(form/defsc-form AccountForm [this props]
  {fo/id           account/id
   fo/attributes   [account/name account/email account/active?]
   fo/route-prefix "account"
   fo/title        "Edit Account"})
```

### 3. Add Lifecycle Triggers

Use `sfo/triggers` (not `fo/triggers`) for statechart lifecycle callbacks. Callbacks return a **vector of ops**, not a modified env:

```clojure
(ns com.example.ui.line-item-forms
  (:require
   [com.fulcrologic.rad.statechart.form :as form]
   [com.fulcrologic.rad.form-options :as fo]
   [com.fulcrologic.rad.statechart.form-options :as sfo]
   [com.fulcrologic.statecharts.integration.fulcro.operations :as fops]))

(form/defsc-form LineItemForm [this props]
  {fo/id         line-item/id
   fo/attributes [line-item/quantity line-item/price line-item/subtotal]

   sfo/triggers
   {:derive-fields (fn [new-form-tree]
                     (assoc new-form-tree
                            :line-item/subtotal
                            (* (:line-item/quantity new-form-tree)
                               (:line-item/price new-form-tree))))

    :on-change    (fn [env data form-ident qualified-key old-value new-value]
                    (case qualified-key
                      :line-item/quantity
                      [(fops/apply-action assoc-in
                                         (conj form-ident :line-item/subtotal)
                                         (* new-value (get-in (:fulcro/state-map data)
                                                               (conj form-ident :line-item/price))))]
                      nil))}})
```

> **Note:** The `sfo/triggers` `:on-change` signature `(fn [env data form-ident key old new] -> [ops])` is **different** from the UISM `fo/triggers` callback. It must return a vector of statechart ops.

### 4. Define a Report

Use the same three-require pattern with `statechart.report`, `report-options` (`ro/`), and `statechart.report-options` (`sro/`):

```clojure
(ns com.example.ui.inventory-report
  (:require
   [com.example.model.item :as item]
   [com.fulcrologic.rad.statechart.report :as report]    ; engine
   [com.fulcrologic.rad.report-options :as ro]           ; shared keys
   [com.fulcrologic.rad.statechart.report-options :as sro])) ; engine-specific keys

(report/defsc-report InventoryReport [this props]
  {ro/title            "Inventory"
   ro/source-attribute :item/all-items
   ro/row-pk           item/id
   ro/columns          [item/name item/price item/in-stock]
   ro/run-on-mount?    true
   ro/route            "inventory"})
```

### 5. Define the Routing Chart

Replace Dynamic Router with a statechart that declares all routes. Use `form/form-route-state` and `report/report-route-state` to wire up form/report lifecycle on route entry:

```clojure
(ns com.example.ui.ui
  (:require
    [com.example.ui.account-forms :refer [AccountForm]]
    [com.example.ui.inventory-report :refer [InventoryReport]]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.rad.statechart.form :as form]
    [com.fulcrologic.rad.statechart.report :as report]
    [com.fulcrologic.statecharts.chart :refer [statechart]]
    [com.fulcrologic.statecharts.integration.fulcro.routing :as scr]))

;; Container component that renders the current route
(defsc Routes [this props]
  {:query                   [:ui/placeholder]
   :preserve-dynamic-query? true
   :initial-state           {}
   :ident                   (fn [] [:component/id ::Routes])}
  (scr/ui-current-subroute this comp/factory))

;; Routing statechart — replaces defrouter
(def routing-chart
  (statechart {:initial :state/route-root}
    (scr/routing-regions
      (scr/routes {:id :state/root :routing/root `Routes}

        ;; Landing page (plain route state)
        (scr/rstate {:route/target `LandingPage})

        ;; Reports
        (report/report-route-state {:route/target InventoryReport})

        ;; Forms (with route params for the entity id)
        (form/form-route-state {:route/target AccountForm
                                :route/params #{:account/id}})))))
```

### 6. Bootstrap the Application

```clojure
(ns com.example.system
  (:require
   [com.example.ui.ui :refer [routing-chart]]
   [com.fulcrologic.rad.statechart.application :as rad-app]
   [com.fulcrologic.rad.rendering.headless.plugin]  ; or your UI plugin
   [taoensso.timbre :as log]))

(defn start!
  ([app] (start! app {}))
  ([app {:keys [event-loop?] :or {event-loop? true}}]
   ;; 1. Install statechart infrastructure
   (rad-app/install-statecharts! app {:event-loop? event-loop?})
   ;; 2. Start routing with your chart
   (rad-app/start-routing! app routing-chart)
   ;; 3. CLJS only — enable URL synchronization
   #?(:cljs (rad-app/install-url-sync! app))))
```

> **Note:** Requiring a rendering plugin namespace (e.g. `com.fulcrologic.rad.rendering.headless.plugin`) is sufficient — its `defmethod` forms register renderers automatically. No `install-ui-controls!` call needed for multimethod-based plugins.

### 7. Navigate

```clojure
(require '[com.fulcrologic.rad.statechart.form :as form])
(require '[com.fulcrologic.statecharts.integration.fulcro.routing :as scr])

;; Navigate to a report
(scr/route-to! this InventoryReport)

;; Navigate to a report with parameters
(scr/route-to! this AccountInvoices {:account/id account-id})

;; Edit an existing entity
(form/edit! this AccountForm account-id)

;; Create a new entity
(form/create! this AccountForm)

;; Form operations (from within a form)
(form/save! this)
(form/cancel! this)
(form/undo-all! this)

;; Back / forward
(scr/route-back! this)
(scr/route-forward! this)

;; Handle route-denied (unsaved changes)
(scr/force-continue-routing! this)   ; proceed and lose changes
(scr/abandon-route-change! this)     ; cancel the navigation
```

---

## Namespace Reference

### Your Code Requires These

| Namespace | Alias | Purpose |
|---|---|---|
| `com.fulcrologic.rad.statechart.form` | `form` | Form engine: `defsc-form`, `create!`, `edit!`, `save!`, `cancel!`, `undo-all!`, `form-route-state` |
| `com.fulcrologic.rad.statechart.report` | `report` | Report engine: `defsc-report`, `run-report!`, `filter-rows!`, `report-route-state` |
| `com.fulcrologic.rad.statechart.container` | `container` | Container engine: `defsc-container` |
| `com.fulcrologic.rad.statechart.application` | `rad-app` | App init: `install-statecharts!`, `start-routing!`, `install-url-sync!` |
| `com.fulcrologic.rad.form-options` | `fo` | Shared form option keys (from upstream) |
| `com.fulcrologic.rad.statechart.form-options` | `sfo` | Engine-specific form keys: `sfo/triggers`, `sfo/statechart` |
| `com.fulcrologic.rad.report-options` | `ro` | Shared report option keys (from upstream) |
| `com.fulcrologic.rad.statechart.report-options` | `sro` | Engine-specific report keys: `sro/triggers`, `sro/statechart` |
| `com.fulcrologic.statecharts.integration.fulcro.routing` | `scr` | Routing: `scr/route-to!`, `scr/ui-current-subroute`, `scr/routes`, `scr/rstate`, `scr/routing-regions` |
| `com.fulcrologic.statecharts.chart` | -- | `statechart` macro for defining routing charts |

### Engine-Specific Option Keys

**`sfo/` (form)**

| Key | Type | Description |
|---|---|---|
| `sfo/triggers` | map | Lifecycle callbacks: `:derive-fields`, `:on-change`, `:started`, `:saved`, `:save-failed` |
| `sfo/statechart` | statechart def or keyword | Override the default form statechart |
| `sfo/statechart-id` | keyword | Pre-registered statechart ID (alternative to inline `sfo/statechart`) |
| `sfo/on-started` | fn | `(fn [env data event-name event-data] ops-vec)` — called on session init |
| `sfo/on-saved` | fn | `(fn [env data event-name event-data] ops-vec)` — called after successful save |
| `sfo/on-save-failed` | fn | `(fn [env data event-name event-data] ops-vec)` — called after failed save |

**`sro/` (report)**

| Key | Type | Description |
|---|---|---|
| `sro/triggers` | map | Lifecycle callbacks: `:on-change` |
| `sro/statechart` | statechart def or keyword | Override the default report statechart |
| `sro/statechart-id` | keyword | Pre-registered statechart ID |
| `sro/on-loaded` | fn | `(fn [env data event-name event-data] ops-vec)` — called after data load |

---

## Rendering Plugins

The old `install-ui-controls!` map-based dispatch has been replaced by multimethods. Rendering plugins register via `defmethod` on:

- `fr/render-field` — Field rendering (dispatches on `[type style]`)
- `fr/render-form` — Form layout rendering
- `rr/render-report` — Report layout rendering
- `form/render-element` — Generic form element rendering (dispatches on `[element style]`)
- `control/render-control` — Control rendering (dispatches on `[control-type style]`)

Simply requiring the plugin namespace registers all renderers — no explicit install call needed.

A headless rendering plugin is included at `com.fulcrologic.rad.rendering.headless.plugin` for testing.

---

## Headless Testing

Because everything is CLJC, the full form and report lifecycle is testable from a plain Clojure REPL with `:immediate` event loop processing:

```clojure
(ns com.example.form-tests
  (:require
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.rad.statechart.application :as rad-app]
    [com.fulcrologic.rad.statechart.form :as form]
    [com.fulcrologic.rad.rendering.headless.plugin] ; registers headless renderers
    [com.example.system :as system]
    [com.example.ui.account-forms :refer [AccountForm]]))

;; Create app with :immediate event loop for synchronous test processing
(def test-app (rad-app/fulcro-rad-app {}))
(system/start! test-app {:event-loop? :immediate})

;; Create a new account and inspect state
(form/create! test-app AccountForm)
```

See `src/demo/` for complete working examples.

---

## Migration Guide (from fulcro-rad UISM)

### Require Changes

| Old | New |
|---|---|
| `[com.fulcrologic.rad.form :as form]` | `[com.fulcrologic.rad.statechart.form :as form]` |
| `[com.fulcrologic.rad.report :as report]` | `[com.fulcrologic.rad.statechart.report :as report]` |
| `[com.fulcrologic.rad.routing :as rroute]` | Removed -- see note below |
| `[com.fulcrologic.rad.application :as rad-app]` | `[com.fulcrologic.rad.statechart.application :as rad-app]` |
| Add new: | `[com.fulcrologic.rad.statechart.form-options :as sfo]` |
| Add new: | `[com.fulcrologic.rad.statechart.report-options :as sro]` |

**Routing note:** `rad.routing` has no single replacement. Its functions are now split across:
- `form/create!`, `form/edit!`, `form/form-route-state` -- from `statechart.form`
- `report/report-route-state` -- from `statechart.report`
- `scr/route-to!`, `scr/route-back!` -- from `statecharts.integration.fulcro.routing`

### Key Changes

1. **App initialization** -- Replace `uism/install!` pattern with:
   ```clojure
   (rad-app/install-statecharts! app)
   (rad-app/start-routing! app routing-chart)
   #?(:cljs (rad-app/install-url-sync! app))
   ```

2. **Routing** — Replace `defrouter` and `dr/change-route!` with a statechart routing chart (see Quick Start step 5). Use `scr/route-to!` and `form/create!` / `form/edit!` for navigation.

3. **Form triggers** — Move engine-specific trigger keys from `fo/triggers` to `sfo/triggers`. Update `:on-change` callback signature:
   ```clojure
   ;; Old (UISM)
   fo/triggers {:on-change (fn [uism-env form-ident k old new] uism-env)}

   ;; New (statecharts) — returns ops vector, not uism-env
   sfo/triggers {:on-change (fn [env data form-ident k old new] [ops...])}
   ```

4. **`fo/machine`** — Replace with `sfo/statechart` if you were providing a custom state machine.

5. **Rendering plugins** — Replace `install-*-renderer!` calls with `defmethod` on the appropriate multimethod. Existing plugins need to be updated to the multimethod dispatch API.

6. **`dr/` and `uism/` references** -- Remove all Dynamic Router and UISM imports. There are no compatibility shims.

7. **Navigation** -- `form/edit!` and `form/create!` are now on `statechart.form`. Report and plain navigation uses `scr/route-to!` directly from the statecharts library.

---

## Demo Application

A complete working demo is in `src/demo/`. It shows:

- Attribute definitions (`com.example.model.*`)
- Form and report definitions (`com.example.ui.*`) -- all CLJC
- Routing chart with forms, reports, and a landing page (`com.example.ui.ui`)
- Bootstrap sequence (`com.example.system`)
- Subform with `sfo/triggers` `:derive-fields` and `:on-change` (`com.example.ui.line_item_forms`)
- Picker options with quick-create (`com.example.ui.invoice_forms`)

---

## Support

Questions and issues: `#fulcro` on [Clojurians Slack](http://clojurians.net/).

Paid support: [Fulcrologic, LLC](http://www.fulcrologic.com).

---

## License

The MIT License (MIT)
Copyright (c) Fulcrologic, LLC

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
