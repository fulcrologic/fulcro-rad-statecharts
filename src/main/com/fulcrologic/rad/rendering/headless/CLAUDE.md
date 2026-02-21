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
- `:ref :default` — renders nothing for subforms (handled by `render-subforms`), shows value for non-subform refs
- `:ref :pick-one` — same as `:ref :default` (headless display only; no interactive picker)
- `:instant :date-at-noon` — delegates to the standard instant renderer (date input)

## Data Attribute Conventions
- `data-rad-type` — element role: "form-field", "field-error", "field-label", "busy", "form", "report", "report-row", "report-cell", "control", etc.
- `data-rad-field` — stringified qualified keyword of the attribute
- `data-rad-action` — action name: "save", "undo", "cancel"
- `data-rad-control` — stringified control key
- `data-rad-column` — stringified column qualified key
- `data-rad-form` — stringified form entity id key
- `data-rad-report` — stringified report row-pk key
