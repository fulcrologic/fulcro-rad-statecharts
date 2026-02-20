# demo-port

**Status:** Backlog | **Priority:** P1 | **Created:** 2026-02-20 | **Depends-on:** cleanup-verification, headless-plugin

## Summary

Port the Datomic portion of fulcro-rad-demo into `src/demo` of this project, converting everything to CLJC with headless support. This provides a full-stack exerciser for REPL-based testing without a browser.

## Source Reference

- **fulcro-rad-demo:** `/Users/tonykay/fulcrologic/fulcro-rad-demo/` — Datomic backend with forms, reports, containers, picker options, subforms, validation
- **dataico-expansion:** `/Users/tonykay/fulcrologic/dataico-expansion/` — Reference for headless full-stack testing pattern

## What to Port (Datomic only)

### Model layer (`src/demo/com/example/model/`)
- `account.cljc` — User accounts with addresses (subform), roles (enum), email
- `address.cljc` — Address subform with state enum
- `invoice.cljc` — Invoice with line items (nested), customer picker
- `line_item.cljc` — Line item with item ref, quantity, price
- `item.cljc` — Inventory items with categories
- `category.cljc` — Item categories
- `model.cljc` — Aggregated attribute list
- `seed.cljc` — Database seeding (convert from .clj to .cljc where possible)

**Remove from port:** file.cljc, sales.cljc (mock data), timezone.cljc (35K entries), authorization.clj (deferred)

### UI layer (`src/demo/com/example/ui/`)
All CLJC files:
- `account_forms.cljc` — Account form (remove blob/avatar, file upload)
- `address_forms.cljc` — Address subform
- `invoice_forms.cljc` — Invoice form with line items, customer picker quick-create
- `item_forms.cljc` — Item form
- `line_item_forms.cljc` — Line item subform
- `inventory_report.cljc` — Item inventory report
- `invoice_report.cljc` — Account invoices report
- `ui.cljc` — Root component (remove DR router, use statecharts routing)

**Remove from port:** login_dialog.cljc (auth deferred), master_detail.cljc (UISM demo), sales_report.cljc (mock data), file_forms.cljc (blob), dashboard.cljc (optional)

### Server layer (`src/demo/com/example/components/`)
- `datomic.cljc` — Datomic connections (Mount state)
- `auto_resolvers.cljc` — Auto-generated resolvers
- `save_middleware.cljc` — Form save (remove blob middleware)
- `delete_middleware.cljc` — Delete operations
- `parser.cljc` — Pathom parser
- `server.cljc` — HTTP server (use conditional compilation for http-kit)
- `config.cljc` — Configuration

**Remove from port:** blob_store.clj, database_queries.clj (custom report queries — add back if needed), ring_middleware.clj (convert to CLJC)

### Client entry (`src/demo/com/example/client.cljc`)
Follow dataico-expansion pattern:
- CLJC with reader conditionals
- CLJ: `h/build-test-app` with http-kit-driver, full URL
- CLJS: `rad-app/fulcro-rad-app` with relative `/api`
- Install headless plugin renderers

### Dev/Test support
- `src/demo/com/example/development.cljc` — `(go)`, `(restart)` for REPL
- `src/demo/com/example/headless.clj` — Headless client helper (following dataico-expansion pattern)

## Key Conversions

1. **All `.clj` → `.cljc`** with reader conditionals where needed
2. **Remove DR routing** → Use statecharts routing (`rstate`, `istate`, `route-to!`)
3. **Remove auth** → No login, no session checking
4. **Remove blob** → No avatar, no file upload
5. **Remove `install-ui-controls!`** → Headless plugin multimethods auto-register
6. **UISM → statecharts** — Forms/reports use statechart lifecycle
7. **Headless app creation** — CLJ uses `h/build-test-app`, `:event-loop? :immediate`

## Headless Testing Pattern (from dataico-expansion)

```clojure
;; Start server
(development/go)

;; Create headless client
(def app (client/init {:port 3000}))

;; Render and inspect
(h/render-frame! app)
(h/hiccup-frame app)

;; Navigate, interact
(h/click-on-text! app "Inventory")
(h/text-exists? (h/hiccup-frame app) "Items")
```

## Critical: Classpath Isolation

fulcro-rad-datomic (and other RAD database adapters) depend on old `com.fulcrologic/fulcro-rad`. The demo's `deps.edn` MUST use `:override-deps` to ensure old RAD is NOT on the classpath:

```clojure
:override-deps {com.fulcrologic/fulcro-rad {:local/root "."}}
```

This ensures all RAD references resolve to this project (fulcro-rad-statecharts), not the published fulcro-rad artifact. Without this, transitive deps from fulcro-rad-datomic will pull in the old RAD and cause conflicts.

**Reference:** fulcro-rad-datomic source is at `/Users/tonykay/fulcrologic/fulcro-rad-datomic/` — maintain compatible namespace names so it can reference this library without modification.

## Acceptance Criteria

- Demo starts from REPL with `(development/go)` → seeds Datomic, starts server
- Headless client connects and can render all forms/reports
- Forms: create, edit, save, cancel work via headless API
- Reports: load, filter, sort work via headless API
- Picker options work (customer picker on invoice)
- Subforms work (addresses on account, line items on invoice)
- All demo files are CLJC
- No browser required for any testing
