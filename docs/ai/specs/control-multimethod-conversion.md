# control-multimethod-conversion

**Status:** Backlog | **Priority:** P0 | **Created:** 2026-02-20 | **Depends-on:** dead-reference-cleanup

## Summary

Convert `control.cljc` from map-based rendering dispatch (`::rad/controls ::type->style->control`) to multimethod-based dispatch, consistent with `form_render.cljc` and `report_render.cljc`.

## Current State

`control.cljc` line 41-64: `render-control` does map-based lookup via `::rad/controls ::type->style->control`. This is the only remaining map-based dispatch in the rendering system.

## Target

Replace with a multimethod:

```clojure
(defmulti render-control
  "Render a control element. Dispatches on [control-type style]."
  (fn [control-type style instance control-key]
    [control-type style])
  :hierarchy #'fr/render-hierarchy)
```

## Changes

- `control.cljc`: Replace `render-control` function with defmulti
- Remove `::rad/controls` runtime atom concept (no more `install!` of control maps)
- Keep pure functions: `current-value`, `component-controls`, `standard-control-layout`

## Acceptance Criteria

- `render-control` is a multimethod dispatching on `[control-type style]`
- No remaining references to `::type->style->control`, `::element->style->layout`, or `::style->layout` map-based dispatch
- Shares `fr/render-hierarchy` with form and report renderers
