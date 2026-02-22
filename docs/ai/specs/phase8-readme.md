# Spec: README Documentation

**Status**: backlog
**Priority**: P2
**Created**: 2026-02-22
**Owner**: AI
**Depends-on**: phase8-demo-migration
**Phase**: 8 — Library Restructuring

## Context

After the restructuring is complete, the project needs a clear README that explains what this library is, how to use it, and — critically — what NOT to use from the upstream fulcro-rad dependency.

Users will have both fulcro-rad and fulcro-rad-statecharts on the classpath. Without clear guidance, they may accidentally use UISM-based functions from upstream that conflict with the statecharts engine.

## Requirements

### 1. Basic Usage Section

1. What this library is (statecharts engine for Fulcro RAD — replaces UISM, not the whole library)
2. Dependencies — what to add to `deps.edn`
3. The three-require pattern for forms:
   ```clojure
   [com.fulcrologic.rad.statechart.form :as form]
   [com.fulcrologic.rad.form-options :as fo]
   [com.fulcrologic.rad.statechart.form-options :as sfo]
   ```
4. Same pattern for reports (`statechart.report`, `report-options`, `statechart.report-options`)
5. App initialization — `statechart.application/install-statecharts!`, `start-routing!`, `install-url-sync!`

### 2. Examples Section

1. Minimal form example (`defsc-form` with a few attributes, `sfo/triggers`)
2. Minimal report example
3. App setup example showing the initialization sequence
4. Routing example (`statechart.routing/route-to!`, `edit!`, `create!`)

### 3. What NOT to Use from Upstream (Critical Section)

This section must be prominent and clear. Users have fulcro-rad on the classpath and must avoid:

| Do NOT use | Why | Use instead |
|-----------|-----|-------------|
| `com.fulcrologic.rad.form` | UISM engine — conflicts with statecharts | `com.fulcrologic.rad.statechart.form` |
| `com.fulcrologic.rad.report` | UISM engine | `com.fulcrologic.rad.statechart.report` |
| `com.fulcrologic.rad.routing` (upstream) | Fulcro Dynamic Router based | `com.fulcrologic.rad.statechart.routing` |
| `com.fulcrologic.rad.authorization` | UISM-based auth system | Not provided — handle auth separately |
| `com.fulcrologic.fulcro.ui-state-machines` | UISM library | `com.fulcrologic.statecharts.*` |
| `com.fulcrologic.fulcro.routing.dynamic-routing` | DR-based routing | Statecharts routing |
| `fo/triggers` (with UISM callback signature) | Wrong callback shape | `sfo/triggers` (returns ops vector) |
| `fo/machine` | UISM machine reference | `sfo/statechart` |
| `rad-hooks` (`use-form`, `use-report`) | React hooks for data | Statecharts manage lifecycle |
| `install-form-container-renderer!`, etc. | Map-based dispatch | `defmethod render-element` |

### 4. What IS Safe to Use from Upstream

Equally important — users should know what's shared and fine:

- `com.fulcrologic.rad.attributes` and `attributes-options` — attribute definitions
- `com.fulcrologic.rad.form-options` (`fo/`) — shared option keys (`fo/id`, `fo/attributes`, `fo/subforms`, etc.)
- `com.fulcrologic.rad.report-options` (`ro/`) — shared option keys
- `com.fulcrologic.rad.form-render` / `report-render` — rendering multimethods
- `com.fulcrologic.rad.application` — `fulcro-rad-app`, `install-ui-controls!` (shared app setup)
- `com.fulcrologic.rad.picker-options` — picker infrastructure
- All type support (`date-time`, `decimal`, `integer`)
- All middleware (`save-middleware`, `autojoin`)
- All pathom/resolver infrastructure
- `com.fulcrologic.rad.ids`, `locale`, `errors`, `options-util`

### 5. Migration Guide (brief)

For users converting from UISM RAD to statecharts RAD:
- Replace `rad.form` requires with `rad.statechart.form`
- Replace `rad.report` requires with `rad.statechart.report`
- Add `rad.statechart.form-options` require, move engine-specific keys from `fo/` to `sfo/`
- Replace `rad.routing` with `rad.statechart.routing`
- Replace `rapp/install-statecharts!` pattern (was not in upstream — new)
- Trigger callbacks now return ops vectors instead of threading UISM env

## Approach

1. Read the final namespace layout after all restructuring specs are done
2. Read the demo app for real usage examples to base the README on
3. Write README.md at project root
4. Include the "What NOT to Use" section prominently (not buried at the bottom)
5. Keep it practical — code examples over prose

## Affected Modules

- `README.md` — NEW or rewritten

## Verification

1. [ ] README exists at project root
2. [ ] Three-require pattern shown correctly for forms and reports
3. [ ] "What NOT to Use" section lists all UISM/DR/auth namespaces to avoid
4. [ ] "What IS Safe" section lists shared upstream namespaces
5. [ ] Code examples compile (spot-check against actual namespace layout)
6. [ ] Migration guide covers the key require changes
