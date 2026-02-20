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
