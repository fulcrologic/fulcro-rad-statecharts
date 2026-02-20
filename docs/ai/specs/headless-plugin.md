# headless-plugin

**Status:** Backlog | **Priority:** P0 | **Created:** 2026-02-20 | **Depends-on:** control-multimethod-conversion

## Summary

Create a sparse headless rendering plugin using multimethods for all rendering: forms, reports, fields, controls, and layouts. Just enough for REPL-based testing — not a production UI plugin.

## Location

`src/main/com/fulcrologic/rad/rendering/headless/`

## Files

### `plugin.cljc`
Entry point that requires all headless renderers and installs defaults.

### `form.cljc`
Default form layout multimethods:
- `render-form` — div wrapper with fields
- `render-header` / `render-footer` — minimal wrappers
- `render-fields` — iterates attributes, calls `render-field`

### `report.cljc`
Default report layout multimethods:
- `render-report` — report with rows
- `render-body` — table-like structure
- `render-row` — row of columns
- `render-controls` / `render-header` / `render-footer` — minimal wrappers

### `field.cljc`
Default field renderers by attribute type:
- `:string` → text input
- `:int` / `:long` / `:double` → number input
- `:boolean` → checkbox
- `:instant` → date input
- `:enum` → select/dropdown
Dispatches via existing `render-field` multimethod on `[ao/type style]`

### `controls.cljc`
Default control renderers:
- Action button, text input, boolean toggle
- Dispatches via new `render-control` multimethod on `[control-type style]`

## Rendering Approach

**Plain HTML only.** No CSS framework, no external React component libraries, no styling. Use only standard HTML elements via Fulcro's `dom` namespace:

- **Layout:** `dom/div`, `dom/h1`..`dom/h3`, `dom/label`, `dom/span`
- **Fields:** Each field wrapped in a `dom/div` containing a `dom/label` and the input. Use `dom/input` (text, number, checkbox, date), `dom/select` + `dom/option` (enums), `dom/textarea` (long text). The label's `htmlFor` should match the input's `id` per HTML standard. Use standard HTML attributes to reflect field state: `disabled`, `readonly`, `required`. When a field has validation errors, render them as a `dom/span` with `{:data-rad-type "field-error"}` after the input.
- **Actions:** `dom/button` (use `disabled` attribute when the action is not available)
- **Reports:** `dom/table`, `dom/thead`, `dom/tbody`, `dom/tr`, `dom/th`, `dom/td`
- **Loading/busy state:** Render a `dom/div` with `{:data-rad-type "busy"}` when the form or report is in a loading/busy state. This gives headless tests a simple element to assert on without digging into Fulcro state.
- **No CSS at all** — no inline styles, no class names, no stylesheets. Structure only.
- **No React component libraries** — no material-ui, no semantic-ui-react, no third-party components of any kind. Every rendered element must be a plain HTML DOM element.

Use data attributes for headless test selection: `{:data-rad-type "form-field" :data-rad-field (str field-key)}`

All files must be `.cljc` for headless testing. Reference `../fulcro-rad-semantic-ui/` for multimethod dispatch patterns only (not for its rendering approach — that uses Semantic UI React components which we explicitly avoid here).

**Implementer discretion:** The agent implementing this spec may add additional data attributes, wrapper elements, or structural choices beyond what is listed here if they improve headless testability. The goal is to make every meaningful state and interaction detectable from the rendered hiccup tree. Use good judgment — keep it plain HTML, but don't hold back on structure that makes testing easier.

## Acceptance Criteria

- All form/report rendering multimethods have `:default` implementations
- All standard field types render with plain HTML inputs (no wrapper components)
- Field validation errors render as a span next to the input
- Field states (disabled, required, readonly) use standard HTML attributes
- Busy/loading states produce a detectable element in the render tree
- Control multimethods have `:default` implementations
- Zero external CSS or React component dependencies
- All files are `.cljc`
- Plugin can be loaded and used in REPL without browser
