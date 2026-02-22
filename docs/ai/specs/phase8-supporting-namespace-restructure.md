# Spec: Supporting Namespace Restructure (Routing, Session, Container, Control)

**Status**: backlog
**Priority**: P1
**Created**: 2026-02-22
**Owner**: AI
**Depends-on**: phase8-deps-and-identical-cleanup
**Phase**: 8 — Library Restructuring

## Context

Beyond form and report, several other namespaces must move under the `com.fulcrologic.rad.statechart.*` prefix to avoid conflicts with upstream fulcro-rad. This spec covers routing, session, container, and control — all the remaining statechart-specific namespaces.

## Requirements

### 1. Routing Namespace

Move `com.fulcrologic.rad.routing` → `com.fulcrologic.rad.statechart.routing`

This namespace is a thin delegation layer to `com.fulcrologic.statecharts.integration.fulcro.routing`. It provides:
- `route-to!`, `back!`, `route-forward!`
- `force-continue-routing!`, `abandon-route-change!`, `route-denied?`
- `edit!`, `create!`, `view!` (form routing helpers)
- `rstate`, `istate` (re-exported from statecharts routing for convenience)

The old `com.fulcrologic.rad.routing` in fulcro-rad wraps Dynamic Router — completely different. No conflict after the move.

### 2. Session Namespace

Move `com.fulcrologic.rad.sc.session` → `com.fulcrologic.rad.statechart.session`

This aligns with the `statechart.*` prefix convention. The `sc` abbreviation was used early in development; the cleanup analysis specifies full word `statechart`.

Functions: `ident->session-id`, `session-id->ident`, `form-session-id`, `report-session-id`, `container-session-id`.

### 3. Container Namespaces

Move container statechart code:
- `com.fulcrologic.rad.container` → `com.fulcrologic.rad.statechart.container`
- `com.fulcrologic.rad.container-chart` → `com.fulcrologic.rad.statechart.container-chart`
- `com.fulcrologic.rad.container-expressions` → `com.fulcrologic.rad.statechart.container-expressions`

Container is deferred for impl extraction (no `container.impl` in upstream fulcro-rad yet), but the namespace still needs to move for consistency.

### 4. Control Namespace

Move `com.fulcrologic.rad.control` → `com.fulcrologic.rad.statechart.control`

This namespace was converted from map-dispatch to multimethod and uses `scf/send!` with `ident->session-id`. It is engine-specific and must move.

### 5. Options Files

Move engine-specific options:
- `com.fulcrologic.rad.container-options` → `com.fulcrologic.rad.statechart.container-options`
- `com.fulcrologic.rad.control-options` → `com.fulcrologic.rad.statechart.control-options`

Note: `form-options` and `report-options` are handled by the options-namespace-split spec.

## File Moves

| Current Path | New Path |
|-------------|----------|
| `src/main/.../rad/routing.cljc` | `src/main/.../rad/statechart/routing.cljc` |
| `src/main/.../rad/sc/session.cljc` | `src/main/.../rad/statechart/session.cljc` |
| `src/main/.../rad/container.cljc` | `src/main/.../rad/statechart/container.cljc` |
| `src/main/.../rad/container_chart.cljc` | `src/main/.../rad/statechart/container_chart.cljc` |
| `src/main/.../rad/container_expressions.cljc` | `src/main/.../rad/statechart/container_expressions.cljc` |
| `src/main/.../rad/container_options.cljc` | `src/main/.../rad/statechart/container_options.cljc` |
| `src/main/.../rad/control.cljc` | `src/main/.../rad/statechart/control.cljc` |
| `src/main/.../rad/control_options.cljc` | `src/main/.../rad/statechart/control_options.cljc` |

Also delete the old `src/main/.../rad/sc/` directory after moving session.

## Cross-Reference Updates

Every file that requires any moved namespace must be updated. Key consumers:
- `statechart.form` requires `session`, `routing`
- `statechart.report` requires `session`, `routing`, `control`
- `statechart.container` requires `session`, `routing`
- `statechart.form-expressions` may require `session`
- `statechart.report-expressions` may require `session`
- Headless rendering files require `form`, `report`, `control`
- Demo files require most namespaces

## Approach

1. Create all new files under `src/main/.../rad/statechart/`
2. Update namespace declarations
3. Update all internal cross-references
4. Delete old files
5. Grep entire project for old namespace references to catch stragglers
6. Verify compilation

**Note**: Verification covers source compilation only (`clj -e "(require ...)"` for source namespaces). Test compilation will be broken until the test-migration spec is complete.

## Affected Modules

- All files listed in File Moves table above (move + update ns)
- All files requiring any of the moved namespaces (update requires)
- `src/main/com/fulcrologic/rad/sc/` directory — DELETE after moving session
- `src/main/com/fulcrologic/rad/rendering/headless/report.cljc` — requires `rad.control`, `rad.form`, `rad.report`, `rad.routing` (update to `statechart.*`)
- `src/main/com/fulcrologic/rad/rendering/headless/form.cljc` — requires `rad.form` (update to `statechart.form`)
- `src/main/com/fulcrologic/rad/rendering/headless/controls.cljc` — update requires for moved namespaces
- `src/main/com/fulcrologic/rad/rendering/headless/plugin.cljc` — update requires for moved namespaces
- `src/main/com/fulcrologic/rad/rendering/headless/field.cljc` — update requires for moved namespaces

## Verification

1. [ ] All moved namespaces compile at new locations
2. [ ] `com.fulcrologic.rad.statechart.routing` provides `route-to!`, `edit!`, `create!`, `view!`
3. [ ] `com.fulcrologic.rad.statechart.session` provides `ident->session-id`, `session-id->ident`
4. [ ] `com.fulcrologic.rad.statechart.container` provides `defsc-container` macro
5. [ ] `com.fulcrologic.rad.statechart.control` provides multimethod-based `render-control`, `run!`
6. [ ] No references to old `rad.sc.session` or `rad.routing` or `rad.container` or `rad.control` remain
7. [ ] The `rad/sc/` directory is deleted
8. [ ] All tests still pass (after test-migration spec)
