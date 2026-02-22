# Spec: Options Namespace Split (sfo/ and sro/)

**Status**: backlog
**Priority**: P1
**Created**: 2026-02-22
**Owner**: AI
**Depends-on**: phase8-form-namespace-restructure, phase8-report-namespace-restructure
**Phase**: 8 — Library Restructuring

## Context

Currently `form_options.cljc` and `report_options.cljc` in this project define both shared option keys (identical to fulcro-rad) and statechart-specific option keys (new or with changed semantics).

**Important**: Some keys like `fo/triggers` and `fo/statechart` already exist in the current `form_options.cljc` with statechart-style docstrings and callback signatures (the file was already partially migrated during earlier phases). The work here is **keyword relocation** — moving these keys from the `fo/` keyword namespace (`:com.fulcrologic.rad.form/triggers`) to the `sfo/` keyword namespace (`:com.fulcrologic.rad.statechart.form-options/triggers`) — and updating all code that reads them. It is NOT reinventing the semantics from scratch.

After restructuring:

- **Shared keys** (`fo/id`, `fo/attributes`, `fo/subforms`, `fo/route-prefix`, etc.) come from fulcro-rad's `form-options` / `report-options` — users require these with `fo/` and `ro/` aliases as usual
- **Statechart-specific keys** (`sfo/triggers`, `sfo/statechart`, `sro/statechart`, etc.) are defined in NEW namespaces `statechart.form-options` / `statechart.report-options` — users require these with `sfo/` and `sro/` aliases

This means statecharts users have three requires for forms:
```clojure
(:require
  [com.fulcrologic.rad.statechart.form :as form]           ;; engine functions
  [com.fulcrologic.rad.form-options :as fo]                 ;; shared option keys
  [com.fulcrologic.rad.statechart.form-options :as sfo])    ;; statechart-specific keys
```

## Requirements

### 1. Create `com.fulcrologic.rad.statechart.form-options` (sfo/)

This namespace defines ONLY statechart-specific option keys — keys that either:
- Don't exist in UISM fulcro-rad (new functionality)
- Have different semantics than their fulcro-rad counterpart (different callback signatures)

**Statechart-specific form options:**

| Key | Type | Description |
|-----|------|-------------|
| `sfo/triggers` | map | Trigger callbacks with statechart expression signature `(fn [env data form-ident k old new] ops-vec)` — differs from UISM's `(fn [uism-env ident k old new] uism-env)` |
| `sfo/statechart` | statechart-def or keyword | Custom form statechart (replaces `fo/machine` from UISM) |
| `sfo/statechart-id` | keyword | Registered statechart ID (alternative to inline def) |
| `sfo/on-started` | fn | `(fn [env data event-name event-data] ops-vec)` — called when form chart enters initial state |
| `sfo/on-saved` | fn | `(fn [env data event-name event-data] ops-vec)` — called after successful save |
| `sfo/on-save-failed` | fn | `(fn [env data event-name event-data] ops-vec)` — called after save failure |

### 2. Create `com.fulcrologic.rad.statechart.report-options` (sro/)

**Statechart-specific report options:**

| Key | Type | Description |
|-----|------|-------------|
| `sro/triggers` | map | Trigger callbacks with statechart expression signature |
| `sro/statechart` | statechart-def or keyword | Custom report statechart (replaces `ro/machine`) |
| `sro/statechart-id` | keyword | Registered statechart ID |
| `sro/on-loaded` | fn | Called when report data loads |

### 3. Delete engine-specific keys from current options files

The current `form_options.cljc` and `report_options.cljc` in this project contain modifications (changed callback signatures). After restructuring:
- These files are deleted from this project (shared keys come from fulcro-rad dependency)
- Engine-specific keys move to `statechart.form-options` / `statechart.report-options`

### 4. Shared keys remain in `fo/` (from fulcro-rad)

These keys have identical semantics in both engines and stay in fulcro-rad's `form-options`:
- `fo/id`, `fo/attributes`, `fo/subforms`, `fo/route-prefix`
- `fo/title`, `fo/layout`, `fo/field-styles`, `fo/field-labels`
- `fo/default-values`, `fo/validation`, `fo/tabbed-layout`
- `fo/can-add?`, `fo/can-delete?`, `fo/added-via-upload?`
- All other keys not listed as statechart-specific above

## File Changes

| Action | File |
|--------|------|
| CREATE | `src/main/.../rad/statechart/form_options.cljc` |
| CREATE | `src/main/.../rad/statechart/report_options.cljc` |
| DELETE | `src/main/.../rad/form_options.cljc` (comes from fulcro-rad dep) |
| DELETE | `src/main/.../rad/report_options.cljc` (comes from fulcro-rad dep) |

## Update Consumers

All files using `fo/triggers`, `fo/machine`, `fo/statechart` must switch to `sfo/triggers`, `sfo/statechart`:
- `statechart/form.cljc` — macro reads options
- `statechart/form-expressions.cljc` — reads trigger functions
- `statechart/form-chart.cljc` — if it references options
- Demo files — user-facing option usage
- Test files — assertions on options

Similarly for report: `ro/machine` → `sro/statechart`, `ro/triggers` → `sro/triggers`.

## Approach

1. Audit current `form_options.cljc` and `report_options.cljc` to inventory every key. Cross-reference with cleanup-analysis Section 2B. Distinguish between keys that currently exist in the code vs. keys being newly introduced — mark any key not present in current code as NEW in the spec table.
2. Create new `statechart/form_options.cljc` with only engine-specific keys
3. Create new `statechart/report_options.cljc` with only engine-specific keys
4. Delete old modified options files
5. Update all consumers: grep for `fo/triggers`, `fo/machine`, `fo/statechart`, `ro/machine`, `ro/triggers`, etc.
6. Verify compilation

## Verification

1. [ ] `sfo/triggers` is a keyword in `statechart.form-options`
2. [ ] `sfo/statechart` is a keyword in `statechart.form-options`
3. [ ] `sro/triggers` is a keyword in `statechart.report-options`
4. [ ] `sro/statechart` is a keyword in `statechart.report-options`
5. [ ] No statechart-specific keys remain in `fo/` or `ro/` (those come from fulcro-rad)
6. [ ] All expression functions reference `sfo/` not `fo/` for engine-specific options
7. [ ] All demo/test code uses `sfo/` and `sro/` for engine-specific options
8. [ ] Project compiles and all tests pass
