# Headless Rendering Plugin

## Purpose
Sparse headless rendering plugin for REPL-based testing. No CSS, no React component libraries.

## Architecture
- All files are CLJC (headless testing in CLJ)
- `plugin.cljc` is the entry point — require it to install all renderers
- Renderers register via `defmethod` on shared multimethods from `form-render.cljc`, `report-render.cljc`, and `control.cljc`

## Dispatch Patterns
- **Form fields** (`fr/render-field`): Dispatch on `[ao/type style]` — e.g. `[:string :default]`, `[:enum :default]`
- **Form layout** (`form/render-element`): Dispatch on `[element style]` — e.g. `[:form-container :default]`
- **Form header/footer** (`fr/render-header`, `fr/render-footer`): Dispatch on `[qualified-key style]`
- **Report layout** (`rr/render-report`, `rr/render-body`, etc.): Dispatch on `[row-pk-key style]`
- **Controls** (`control/render-control`): Dispatch on `[control-type style]` — e.g. `[:button :default]`

## Key Decisions
- **No `fr/render-form :default` override**: form.cljc already defines this to delegate to `render-element`, so we only provide `render-element` implementations
- **Busy detection**: Uses `::app/active-remotes` from props (no `saving?` function exists)
- **Field rendering**: Uses `form/field-context` for unified field state (value, validation, read-only, visibility)
- **Data attributes**: All meaningful elements have `data-rad-type` and contextual `data-rad-*` attributes for headless test selection

## Added Field Renderers (Feb 2026)
- `:decimal` — renders as `<input type="number" step="any">`
- `:ref :default` — renders `<select>` picker from `po/current-form-options` when options available, falls back to `<span>` text display. Subforms render nil.
- `:ref :pick-one` — same as `:ref :default`
- `:instant :date-at-noon` — delegates to the standard instant renderer (date input)

## CLJ onChange Pattern
All field onChange handlers use reader conditionals for CLJ/CLJS compatibility:
- **CLJS**: Uses `m/set-string!`, `m/set-integer!`, `m/set-value!` with `:event evt` (DOM event)
- **CLJ**: Uses `m/set-string!!`, `m/set-integer!!`, `m/set-value!!` with `:value evt` (raw value, synchronous)
- In CLJ headless testing, the "event" passed to onChange IS the value directly (string, number, etc.)
- The `!!` suffix makes the mutation synchronous (required for headless/test mode)

## Subform Add/Delete Buttons (Feb 2026)
- To-many subforms render "Add" button when `fo/can-add?` is truthy in subform opts
- Each subform row wrapped in `div[data-rad-type="subform-row"]` with optional "Delete" button
- `fo/can-add?` resolved via `?!` (supports boolean or `(fn [form-instance ref-key])`)
- `fo/can-delete?` resolved via `?!` per row (supports boolean or `(fn [parent-instance child-props])`)
- `:prepend` value for `can-add?` passes `{:order :prepend}` to `form/add-child!`

## CLJ-Compatible Controls (Feb 2026)
- String control onChange: CLJ passes `evt` directly as value (no DOM event object)
- Boolean control onChange: No reader conditionals needed (doesn't use event object)

## Report Form-Links Bug Fix (Feb 2026)
- **form-links stored on Row class, not Report class**: `defsc-report` macro generates a Row component and stores `::report/form-links` in its options, not the report component's options
- `report/form-link` reads from `(comp/component-options report-instance)` — always nil for form-links
- Fix: `row-form-link` helper in report.cljc looks up `ro/BodyItem` from report options, gets Row class options, and finds form-links there
- The Row class is accessible via `(ro/BodyItem options)` where `options` is the report's component options

## Report Row Index via Metadata (Feb 2026)
- `rr/render-row` multimethod has fixed 3-arg signature: `[report-instance options row-props]`
- Row index is passed via metadata: `(with-meta row-props {:row-index idx})` in render-body
- Read back in render-row: `(or (:row-index (meta row-props)) 0)`

## Data Attribute Conventions
- `data-rad-type` — element role: "form-field", "field-error", "field-label", "busy", "form", "report", "report-row", "report-cell", "control", etc.
- `data-rad-field` — stringified qualified keyword of the attribute
- `data-rad-action` — action name: "save", "undo", "cancel"
- `data-rad-control` — stringified control key
- `data-rad-column` — stringified column qualified key
- `data-rad-form` — stringified form entity id key
- `data-rad-report` — stringified report row-pk key
