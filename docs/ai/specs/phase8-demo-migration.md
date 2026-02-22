# Spec: Demo App Migration

**Status**: backlog
**Priority**: P2
**Created**: 2026-02-22
**Owner**: AI
**Depends-on**: phase8-test-migration
**Phase**: 8 — Library Restructuring

## Context

The demo app under `src/demo/` uses the old namespace structure (`rad.form`, `rad.report`, `rad.routing`, `fo/` for engine-specific options). After the namespace restructure, all demo files must be updated to use `rad.statechart.*` namespaces and `sfo/`/`sro/` for engine-specific options.

This is the user-facing validation that the restructured library works end-to-end: the demo should compile, start a server, and serve all 6 pages verified in Phase 7 Chrome testing.

## Requirements

1. Update all demo file requires from old namespaces to new `statechart.*` namespaces
2. Update engine-specific option keys from `fo/` to `sfo/` and `ro/` to `sro/` where appropriate
3. Keep shared option keys as `fo/` and `ro/` (they come from fulcro-rad dependency)
4. Demo must compile (CLJ and CLJS)
5. Demo server must start and serve all pages
6. Headless demo tests must pass

## Files to Update

### UI Files (require form/report/routing)

| File | Key Changes |
|------|-------------|
| `ui/account_forms.cljc` | `rad.form` → `rad.statechart.form`, `fo/triggers` → `sfo/triggers` if used |
| `ui/invoice_forms.cljc` | Same pattern |
| `ui/address_forms.cljc` | Same pattern |
| `ui/item_forms.cljc` | Same pattern |
| `ui/line_item_forms.cljc` | Same pattern |
| `ui/inventory_report.cljc` | `rad.report` → `rad.statechart.report`, `ro/` → `sro/` for engine-specific |
| `ui/invoice_report.cljc` | Same pattern |
| `ui/ui.cljc` | `rad.routing` → `rad.statechart.routing` |

### Client/Server Files

| File | Key Changes |
|------|-------------|
| `client.cljc` | `rad.application/install-statecharts!` → `rad.statechart.application/install-statecharts!`, same for `start-routing!`, `install-url-sync!`. Shared functions (`fulcro-rad-app`, `install-ui-controls!`) stay as `rad.application/`. |
| `development.cljc` | Update REPL startup requires (note: file moved to `src/demo/development.cljc`) |
| `headless_client.clj` | Update requires — `statechart.application` for statechart init functions |
| `system.cljc` | Update if it references form/report namespaces |

### Test Files

| File | Key Changes |
|------|-------------|
| `headless_form_tests.clj` | Update requires |
| `headless_report_tests.clj` | Update requires |
| `headless_routing_tests.clj` | Update requires |
| `test_server.clj` | Update requires |

## Require Update Pattern

```clojure
;; OLD (in demo UI forms)
(:require
  [com.fulcrologic.rad.form :as form]
  [com.fulcrologic.rad.form-options :as fo]
  [com.fulcrologic.rad.report :as report]
  [com.fulcrologic.rad.report-options :as ro]
  [com.fulcrologic.rad.routing :as rroute])

;; NEW
(:require
  [com.fulcrologic.rad.statechart.form :as form]
  [com.fulcrologic.rad.form-options :as fo]                    ;; shared keys unchanged
  [com.fulcrologic.rad.statechart.form-options :as sfo]        ;; add if using engine-specific keys
  [com.fulcrologic.rad.statechart.report :as report]
  [com.fulcrologic.rad.report-options :as ro]                  ;; shared keys unchanged
  [com.fulcrologic.rad.statechart.report-options :as sro]      ;; add if using engine-specific keys
  [com.fulcrologic.rad.statechart.routing :as rroute])
```

## Option Key Updates

For each demo file, audit option maps and change engine-specific keys:

```clojure
;; OLD
{fo/id           account/id         ;; KEEP as fo/ (shared)
 fo/attributes   [account/name]     ;; KEEP as fo/ (shared)
 fo/triggers     {:on-change ...}   ;; CHANGE to sfo/triggers
 fo/route-prefix "account"}         ;; KEEP as fo/ (shared)

;; NEW
{fo/id           account/id
 fo/attributes   [account/name]
 sfo/triggers    {:on-change ...}   ;; statechart-specific
 fo/route-prefix "account"}
```

## Approach

1. List all demo files and their current requires
2. Update requires in each file
3. Audit each file's option maps for engine-specific keys
4. Update option keys as needed
5. Start demo server and verify it compiles
6. Run headless demo tests
7. If possible, verify browser rendering (same 6 pages as Phase 7)

## Verification

1. [ ] All demo files updated with new requires
2. [ ] Engine-specific options use `sfo/` and `sro/` prefixes
3. [ ] Shared options still use `fo/` and `ro/` prefixes
4. [ ] Demo CLJ compilation succeeds
5. [ ] Demo CLJS compilation succeeds
6. [ ] Demo server starts and serves pages
7. [ ] Headless form tests pass
8. [ ] Headless report tests pass
9. [ ] Headless routing tests pass
10. [ ] The three-require pattern is demonstrated in at least one demo file (form + fo + sfo)
