# RAD Source Notes

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
- `form-container-renderer`, `form-layout-renderer` removed (use `render-element` with `:form-container` / `:form-body-container`)
- `attr->renderer` simplified to delegate to `fr/render-field` multimethod
- `default-render-field` simplified to log error (plugins register via `defmethod fr/render-field`)
- `install-field-renderer!`, `install-form-container-renderer!`, `install-form-body-renderer!`, `install-form-ref-renderer!` removed
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
