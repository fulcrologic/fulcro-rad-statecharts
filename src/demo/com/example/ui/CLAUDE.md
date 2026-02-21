# Demo UI Layer

## Porting Decisions

### account_forms.cljc
- **Removed** from original: blob/avatar fields, FileForm subform, TagForm, timezone/zone-id field, `fo/query-inclusion` for blob status keys, semantic-ui rendering options (`suo/*`)
- **Kept**: AccountForm with address subforms (primary + additional), BriefAccountForm (simplified view)
- `::form/autocreate-on-load?` used on primary-address subform to auto-create when parent loads without one
- No custom rendering body — uses default `render-layout` via headless plugin multimethods

### address_forms.cljc
- Minimal subform: id, street, city, state (enum), zip
- `fo/route-prefix` kept for standalone editing capability
- `fo/cancel-route` removed (original had `["landing-page"]`) — statechart routing handles navigation

### inventory_report.cljc
- **New file** — no equivalent existed in fulcro-rad-demo (only sales_report.cljc existed)
- Created based on the item model attributes: name, category, price, in-stock
- Source attribute: `:item/all-items` (resolver already in item model)
- Includes client-side text search filter and column sorting
- `category/label` included as a cross-entity column via join resolution

### invoice_report.cljc
- **New file** — no equivalent existed in fulcro-rad-demo
- Created based on invoice model: customer (account/name), date, total
- Source attribute: `:invoice/all-invoices` (resolver already in invoice model)
- `account/name` resolves through the `:invoice/customer` ref → `:account/id` → `:account/name` chain
- Date formatting uses `dt/inst->human-readable-date`
- Default sort: by date descending (most recent first)

### item_forms.cljc
- ItemForm with category picker (`:pick-one` style)
- CategoryQuery helper component for picker options
- No custom rendering, no reports (inventory report is in separate file)

### line_item_forms.cljc
- **Key conversion**: `on-change` trigger converted from UISM env-returning to statechart ops-returning convention
  - Old: `(fn [uism-env form-ident k old new] uism-env)` — used `uism/apply-action`
  - New: `(fn [env data form-ident key old new] [ops])` — returns vector of `fops/apply-action` ops
- Category change clears item selection and reloads item picker options via `po/load-options!` side-effect
- Item change auto-fills quoted-price from item's price
- `po/load-options!` called as a side-effect (not an op) since it's an imperative load trigger
- `derive-fields` computes subtotal = quantity × quoted-price

### invoice_forms.cljc
- InvoiceForm with customer picker (quick-create enabled), line-item subform
- Customer picker uses `BriefAccountForm` (from account_forms) for editing
- `po/quick-create` generates tempid-based account on the fly
- `derive-fields` sums line-item subtotals into invoice total
- Custom `invoice-validator` requires at least one line item
- AccountInvoices runtime report for per-customer invoice listing
- **Removed from original**: InvoiceList report (replaced by standalone invoice_report.cljc)

### General Patterns
- All files are CLJC — no reader conditionals needed for pure form declarations
- No `install-ui-controls!` — headless plugin auto-registers via multimethods
- No UISM/`uism/` references — forms use statechart lifecycle via `fo/statechart` (defaulted by macro)
- Model attributes imported from `com.example.model.*` (already ported)
- `comp/props` used in subform can-add?/can-delete? lambdas to inspect parent state
