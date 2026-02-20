# cleanup-deletions

**Status:** Backlog | **Priority:** P0 | **Created:** 2026-02-20 | **Depends-on:** none

## Summary

Delete modules that are no longer needed: authorization, blob, react hooks, pathom3, dynamic routing subsystem, deprecated files, UISM state machines, and dynamic generator.

## Files to Delete

**Authorization (handled externally):**
- `src/main/com/fulcrologic/rad/authorization.cljc`
- `src/main/com/fulcrologic/rad/authorization/simple_authorization.cljc`

**Blob/file-upload (handled externally):**
- `src/main/com/fulcrologic/rad/blob.cljc`
- `src/main/com/fulcrologic/rad/blob_storage.clj`

**Pathom3 (keep only pathom v2):**
- `src/main/com/fulcrologic/rad/pathom3.clj`
- `src/main/com/fulcrologic/rad/resolvers_pathom3.cljc`

**Dynamic routing subsystem (replaced by statecharts routing):**
- `src/main/com/fulcrologic/rad/routing.cljc`
- `src/main/com/fulcrologic/rad/routing/base.cljc`
- `src/main/com/fulcrologic/rad/routing/history.cljc`
- `src/main/com/fulcrologic/rad/routing/html5_history.cljc`

**React hooks (banned — use statecharts for state management):**
- `src/main/com/fulcrologic/rad/rad_hooks.cljc`

**Deprecated files:**
- `src/main/com/fulcrologic/rad/ui_validation.cljc` — deprecated, merged into form ns

**UISM state machines (being replaced by statecharts):**
- `src/main/com/fulcrologic/rad/form_machines.cljc`
- `src/main/com/fulcrologic/rad/state_machines/incrementally_loaded_report.cljc`
- `src/main/com/fulcrologic/rad/state_machines/incrementally_loaded_report_options.cljc`
- `src/main/com/fulcrologic/rad/state_machines/server_paginated_report.cljc`
- `src/main/com/fulcrologic/rad/state_machines/server_paginated_report_options.cljc`

**Dynamic generator (DR-coupled):**
- `src/main/com/fulcrologic/rad/dynamic/generator.cljc`
- `src/main/com/fulcrologic/rad/dynamic/generator_options.cljc`

**Test files for deleted modules:**
- `src/test/com/fulcrologic/rad/blob_spec.cljc`
- `src/test/com/fulcrologic/rad/blob_storage_spec.clj`
- `src/test/com/fulcrologic/rad/resolvers_pathom3_spec.cljc`

**Deprecated vars to remove from surviving files:**
- `report.cljc`: `reload!`, `set-parameter!`, `generated-row-class`, `sc-report`
- `report_options.cljc`: `field-formatters`, `field-formatter`, `link` (deprecated aliases)
- `form.cljc`: `parse-long`, `mark-filled-fields-complete*`, `install-ui-controls!`
- `picker_options.cljc`: `current-options` (deprecated alias)
- `decimal.cljc`: deprecated `toCurrencyString` and `toLocaleString` functions

## Total: ~22 files deleted, ~5 files have deprecated vars removed

## Acceptance Criteria

- All listed files deleted
- All deprecated vars removed from surviving files
- No compilation errors from missing files (downstream cleanup is a separate task)
- Git commit with clear message listing what was removed and why
