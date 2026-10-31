# Spec: Demo Screens (Accounts, Invoices, Inventory, Account Extras)

**Status**: backlog
**Priority**: P1
**Created**: 2026-10-31
**Owner**: AI
**Depends-on**: rp-06, rp-10, rp-14
**Skills**: `clojure`, `clojure-repl`, `fulcro-spec-tdd`, `fulcro-headless`, `fulcro-rad`, `fulcro-rad-reports`, `datomic`, `pathom`, `date-time`

## Context

The demo covers about a third of `../fulcro-rad-demo`. Sources: gap report §5; `review/review-demo.md` P1-6, 10, 11, 12 and P2-14.

**Missing or simplified screens:**
- **AccountList is missing**: filter, show-inactive toggle, Enable/Disable row actions, New Account button, `AccountListItem` list style. `set-account-active` exists in `src/demo/com/example/model/account.cljc` but nothing uses it.
- **InvoiceList** (now `invoice_report.cljc` `InvoiceReport`) lost the invoice-id column, the Account Invoices and Delete row actions, and the New Invoice/New Account buttons.
- **InventoryReport** lost the category picker filter and the click-category-to-filter link.

**Dropped resolvers:**
- `item-category-resolver` (original `item.cljc`);
- the `:line-item/category` resolver (original `line_item.cljc:10-20`).

Existing line items load with no category. InventoryReport works around the first with a join plus a formatter, and sorting on `:category/label` is probably broken (Suspected).

**Missing account extras:**
- avatar blob, `FileForm`/files subform, and the server-side blob store (`wrap-persist-images`, blob resolvers, `/images` `/files` `/preview` services, upload middleware);
- tags `:pick-many` with quick-create (`TagForm`, `all-tags`);
- timezone `zone-id` with autocomplete (`timezone.cljc`, `all-time-zones`).

**Bug:** quick-create in `src/demo/com/example/ui/invoice_forms.cljc` writes `:time-zone/zone-id`, which isn't in the model (Suspected save failure).

**Stale notes:** `src/demo/com/example/ui/CLAUDE.md` calls the inventory and invoice reports "new files" and documents `fo/statechart`.

**Donors (read-only):**
- `../fulcro-with-statecharts`: `ui/account_forms.cljc`, `invoice_forms.cljc`, `item_forms.cljc`, `model/timezone.cljc`, `model/item.cljc`, `components/blob_store.clj`, `ring_middleware.clj`;
- `../fulcro-rad-demo/src/shared/com/example/**`.

## Requirements

1. **AccountList**:
   - search filter control;
   - show-inactive boolean control, passed as a resolver param that filters inactive accounts;
   - Enable/Disable row actions calling `set-account-active`, then re-running the report;
   - a New Account button;
   - `:affirmation` formatter for the active column;
   - a route and nav entry.
2. **InvoiceList (full)**:
   - invoice-id column;
   - Account Invoices row action (routes to `AccountInvoices` with `:account/id`);
   - Delete row action through `form/delete!` (legacy symbol, rp-02; the entity is gone after reload);
   - New Invoice and New Account buttons.
3. **Inventory (full)**: a category `:picker` control (rp-06), a click-category-to-filter link (`ro/links`), and a New Item button.
4. **Resolvers.**
   - Restore `item-category-resolver` and the `:line-item/category` resolver.
   - Existing line items load with their category, and the InventoryReport category column sorts.
5. **Account extras:**
   - **Avatar and files.** Blob avatar plus a files subform (`FileForm`), with the server-side blob store, persistence middleware, resolvers and ring routes, and the client upload remote middleware.
   - **Tags.** `:pick-many` with quick-create (`TagForm`, `all-tags` resolver).
   - **Timezone.** `zone-id` with autocomplete (`all-time-zones` resolver), seeded for Tony.
6. **Fix quick-create** in `invoice_forms.cljc` so it writes only modeled attributes.
7. **Behavior that now works through the UI** (rp-06), covered by demo headless tests: invoice total, line-item subtotal, item→price autofill, category→item reload.
8. **Headless tests.** New demo test namespaces, one per screen or feature:
   - AccountList filter, toggle and row actions;
   - InvoiceList delete and row actions;
   - Inventory category filter and link;
   - account form tags quick-create, timezone selection, and blob (complete an upload in CLJ by setting the blob fields; rp-06);
   - invoice derived fields (req 7).
   - Update existing report and form tests whose screens changed.

## File Ownership

- `src/demo/com/example/model.cljc`, `model/**`, `model/seed.cljc`, `development.cljc`
- `src/demo/com/example/ui/**`, except `ui/route_denied.cljc` (rp-09)
- `src/demo/com/example/components/**`, except `ring_middleware.cljc` page markup (rp-14's; adding blob routes is allowed)
- `src/demo/com/example/client.cljc`: upload remote middleware only
- `deps.edn`: `:demo` alias only, if blob or ring deps are needed. If `:deps` changes, run `clj -Spom`; it shouldn't.
- Existing and new `src/demo/com/example/headless_*_tests.clj` for the screens above
- `src/demo/com/example/ui/CLAUDE.md`, `components/CLAUDE.md`: rewrite the stale claims

Must not edit `src/demo/com/example/system.cljc` (rp-11) or any `src/main` file. If a library change is needed, report it to the conductor.

## Affected Modules

- Demo model, resolvers, server components, forms and reports, routes and nav, seed

## Approach

- Port from f-w-s first, since it already targets this engine. Use `fulcro-rad-demo` for anything f-w-s lacks.
- Keep Datomic queries per the `datomic` skill. Add seed data minimally for each test (`seed-data` skill pattern).

## Open Questions

- None.

## Verification

1. [ ] New demo tests (req 8) fail before and pass after. The req 7 derived-field tests prove the rp-06 change path end to end.
2. [ ] Tests run with kaocha in the implementer's own REPL. No CLI test runs.
3. [ ] Full CLJ suite green: `:unit` + `:demo`.
4. [ ] CLJS `main` compiles with 0 warnings.
5. [ ] Quick Chrome smoke of each new screen (rp-14 setup). Plugin-debt glitches are listed, not fixed.
6. [ ] `cljfmt check <changed files>` passes from the repo root (root `CLAUDE.md` "Build Hygiene").
7. [ ] Demo `CLAUDE.md` files corrected. REPL and server stopped. Committed and pushed.
