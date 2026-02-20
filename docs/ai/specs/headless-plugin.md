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

- Use `dom/div`, `dom/input`, `dom/select`, `dom/button` — no CSS framework
- Data attributes for test selection: `{:data-rad-type "form-field" :data-rad-field (str field-key)}`
- All CLJC for headless testing
- Reference `fulcro-rad-semantic-ui` for patterns, but keep sparse

## Acceptance Criteria

- All form/report rendering multimethods have `:default` implementations
- All standard field types render something meaningful
- Control multimethods have `:default` implementations
- All files are `.cljc`
- Plugin can be loaded and used in REPL without browser
