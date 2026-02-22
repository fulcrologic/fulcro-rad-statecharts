# Spec: deps.edn Update and Forked File Cleanup

**Status**: backlog
**Priority**: P0
**Created**: 2026-02-22
**Owner**: AI
**Depends-on**: phase8-upstream-impl-extraction
**Phase**: 8 — Library Restructuring

## Context

Currently fulcro-rad-statecharts is a fork that contains its own copies of all fulcro-rad source files. To become an add-on library, it must:

1. Add fulcro-rad as a dependency in `deps.edn`
2. Delete all forked files that should come from upstream — whether identical or diverged
3. Update `pom.xml` to declare the fulcro-rad dependency

After this spec, only statechart-specific and statechart-modified files remain in this project's source tree.

### Key Decision: All forked shared files are deleted

Diff analysis (2026-02-22) found that 16 of 29 files had diverged from upstream. The divergences fall into categories that all lead to deletion:

- **Whitespace/formatting only** (report_render, resolvers, autojoin, integer): trivial, use upstream
- **UISM removal stubs** (authorization, resolvers_common, debugging): this library doesn't provide auth, pathom3, or UISM — upstream's versions are correct
- **Upstream evolution we missed** (options_util `->arity-tolerant`, picker_options cache fix, attributes_options `pathom3-batch?`): upstream is better, use it
- **Statechart additions** (application.cljc adds `install-statecharts!`; form_render.cljc adds requires): these additions must move to this library's own namespaces (e.g. `statechart.application`), not shadow upstream
- **Dead requires** (save_middleware, js_date_formatter): unused `log`/`timbre` requires, use upstream

No auth, no pathom3 — those are not concerns for this library.

## Requirements

1. Add `com.fulcrologic/fulcro-rad {:mvn/version "LATEST"}` to `deps.edn` `:deps`
2. Update `pom.xml` `<dependencies>` to include fulcro-rad
3. Delete ALL files in the "Files to Delete" list below
4. Before deleting `application.cljc`: extract the 3 statechart-specific functions into `com.fulcrologic.rad.statechart.application` (new file in this library):
   - `install-statecharts!` — wraps `scf/install-fulcro-statecharts!` with URL sync
   - `start-routing!` — wraps `scr/start!`
   - `install-url-sync!` — wraps `scr/install-url-sync!`
   All other functions in this file (`fulcro-rad-app`, `install-ui-controls!`, `secured-request-middleware`, `elision-predicate`, `elide-params`, `elide-ast-nodes`, `global-eql-transform`, `default-remote-error?`, `default-network-blacklist`) are shared and come from upstream.
5. Before deleting `form_render.cljc`: verify the extra requires (`app`, `rad`) are not actually needed — if they are, add them where they're used in statechart code, not by shadowing upstream
6. Delete `rad.cljc` (empty namespace stub at `src/main/com/fulcrologic/rad.cljc`) — it shadows the upstream namespace for no reason
6. Verify the project still compiles after deletion
7. Remove test files that test only shared code (those tests belong in fulcro-rad)
8. Do NOT delete files that are being restructured by later specs (form.cljc, report.cljc, etc.)

## Files to Delete

All files below are deleted. Upstream versions (from fulcro-rad dependency) take over.

### Truly Identical to Upstream (13 files)

| File | Namespace |
|------|-----------|
| `attributes.cljc` | `com.fulcrologic.rad.attributes` |
| `ids.cljc` | `com.fulcrologic.rad.ids` |
| `locale.cljc` | `com.fulcrologic.rad.locale` |
| `errors.cljc` | `com.fulcrologic.rad.errors` |
| `form_render_options.cljc` | `com.fulcrologic.rad.form-render-options` |
| `report_render_options.cljc` | `com.fulcrologic.rad.report-render-options` |
| `registered_maps.clj` | `com.fulcrologic.rad.registered-maps` |
| `pathom_async.clj` | `com.fulcrologic.rad.pathom-async` |
| `middleware/autojoin_options.cljc` | `com.fulcrologic.rad.middleware.autojoin-options` |
| `type_support/date_time.cljc` | `com.fulcrologic.rad.type-support.date-time` |
| `type_support/decimal.cljc` | `com.fulcrologic.rad.type-support.decimal` |
| `type_support/js_joda_base.cljs` | `com.fulcrologic.rad.type-support.js-joda-base` |
| `type_support/ten_year_timezone.cljs` | `com.fulcrologic.rad.type-support.ten-year-timezone` |

### Diverged but Upstream is Better (5 files — upstream evolved past our fork)

| File | Namespace | Why upstream is better |
|------|-----------|----------------------|
| `attributes_options.cljc` | `com.fulcrologic.rad.attributes-options` | Missing `pathom3-batch?` def, doc improvements |
| `options_util.cljc` | `com.fulcrologic.rad.options-util` | Missing `->arity-tolerant` bug fix for CLJ arity handling |
| `picker_options.cljc` | `com.fulcrologic.rad.picker-options` | Missing `assoc-in time-path` — breaks picker option caching |
| `pathom.clj` | `com.fulcrologic.rad.pathom` | Missing `p/env-plugin`, sensitive-key improvements |
| `pathom_common.clj` | `com.fulcrologic.rad.pathom-common` | Has sensitive-key path fix |

### Diverged — Whitespace/Formatting Only (4 files)

| File | Namespace |
|------|-----------|
| `report_render.cljc` | `com.fulcrologic.rad.report-render` |
| `resolvers.cljc` | `com.fulcrologic.rad.resolvers` |
| `middleware/autojoin.cljc` | `com.fulcrologic.rad.middleware.autojoin` |
| `type_support/integer.cljc` | `com.fulcrologic.rad.type-support.integer` |

### Diverged — UISM Removal Stubs (3 files — not our concern)

| File | Namespace | What our fork did |
|------|-----------|-------------------|
| `authorization.cljc` | `com.fulcrologic.rad.authorization` | Replaced ~300 lines with pass-through `redact` stub. Auth is not in scope for this library. |
| `resolvers_common.cljc` | `com.fulcrologic.rad.resolvers-common` | Auth wrapper stubbed out |
| `debugging.cljc` | `com.fulcrologic.rad.debugging` | Removed UISM require |

### Diverged — Statechart Additions to Extract First (2 files)

| File | Namespace | Action before deletion |
|------|-----------|----------------------|
| `application.cljc` | `com.fulcrologic.rad.application` | Extract `install-statecharts!`, `start-routing!`, `install-url-sync!` into `com.fulcrologic.rad.statechart.application` |
| `form_render.cljc` | `com.fulcrologic.rad.form-render` | Verify extra requires (`app`, `rad`) are used in statechart code; move if needed |

### Diverged — Dead Requires (2 files)

| File | Namespace | Issue |
|------|-----------|-------|
| `middleware/save_middleware.cljc` | `com.fulcrologic.rad.middleware.save-middleware` | Unused `log`/`tempid` requires |
| `type_support/js_date_formatter.cljs` | `com.fulcrologic.rad.type-support.js-date-formatter` | Unused `timbre` require |

### Test Files to Delete (test shared code that belongs in fulcro-rad)

| File | Reason |
|------|--------|
| `attributes_spec.cljc` | Tests shared attributes code |
| `ids_spec.cljc` | Tests shared ids code |
| `type_support/date_time_spec.cljc` | Tests shared date-time code |
| `type_support/decimal_spec.cljc` | Tests shared decimal code |
| `type_support/js_date_formatter_spec.cljs` | Tests shared formatter code |

### Files to KEEP (modified for statecharts — handled by later specs)

- `form.cljc`, `report.cljc`, `container.cljc` — engine-specific code
- `form_options.cljc`, `report_options.cljc`, `container_options.cljc`, `control_options.cljc` — modified option keys
- `control.cljc` — multimethod conversion
- `routing.cljc` — statecharts routing delegation
- `form_chart.cljc`, `form_expressions.cljc`, `form_machines.cljc` — NEW statecharts files
- `report_chart.cljc`, `report_expressions.cljc` — NEW statecharts files
- `container_chart.cljc`, `container_expressions.cljc` — NEW statecharts files
- `sc/session.cljc` — NEW session conversion
- `rendering/headless/*` — NEW headless rendering
- `server_paginated_report.cljc`, `incrementally_loaded_report.cljc` — statechart variants

## Approach

1. **Extract statechart additions** from `application.cljc` into new `com.fulcrologic.rad.statechart.application` namespace (creates `install-statecharts!`, `start-routing!`, `install-url-sync!`)
2. **Check `form_render.cljc`** extra requires — trace usage, move to statechart code if needed
3. Add fulcro-rad dependency to `deps.edn` (use `:local/root "../fulcro-rad"` during development)
4. Add fulcro-rad dependency to `pom.xml`
5. Delete all 29 source files listed above
6. Delete 5 test files listed above
7. Run `clj -e "(require ...)"` for key namespaces to verify resolution from dependency
8. Commit with clear message listing deletions and rationale

**Note**: Verification covers source compilation only. Test compilation will be broken until the test-migration spec is complete.

## Open Questions

1. **fulcro-rad version**: Use `:local/root "../fulcro-rad"` during development (needs impl extraction first), switch to `:mvn/version` for release.
2. **Options files**: `form_options.cljc` and `report_options.cljc` have been modified (callback signature changes). These MUST NOT be deleted. After restructuring (later specs), shared keys will come from fulcro-rad's `form-options`, and only statechart-specific keys will be in `statechart.form-options`.

## Verification

1. [ ] Statechart-specific code extracted from `application.cljc` before deletion
2. [ ] `form_render.cljc` extra requires traced and moved if needed
3. [ ] `deps.edn` has fulcro-rad as a dependency
4. [ ] `pom.xml` declares fulcro-rad dependency
5. [ ] All 29 source files deleted
6. [ ] All 5 test files deleted
7. [ ] Project compiles (`clj -e "(require 'com.fulcrologic.rad.attributes)"` succeeds — resolves from dep)
8. [ ] No namespace resolution errors for deleted files
9. [ ] CLJS compilation still succeeds (if applicable)
10. [ ] `form_options.cljc` and `report_options.cljc` are NOT deleted
