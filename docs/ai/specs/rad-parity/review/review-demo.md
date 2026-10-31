# Demo port review: `src/demo` vs `../fulcro-rad-demo`

The port covers the Datomic CRUD core: account, address, item, invoice and line-item forms, plus three reports. It is **not** a complete demo. Everything outside that core was left out on purpose by `docs/ai/specs/demo-port.md`. On top of that, a renderer bug breaks some of what was ported. I ran nothing. "Verified" means I confirmed it by reading the code; "Suspected" means I inferred it.

Shorthand: **f-w-s** = `../fulcro-with-statecharts`; **sc-demo** = `../fulcro-rad-statecharts-demo`.

## Prioritized gaps for "complete demo working"

**P0: blocks ported features or the browser UI**

1. **Headless renderer never calls `form/input-changed!`** (Verified). Every field `onChange` writes state directly with `m/set-string!`, `m/set-value!!` and similar (`rendering/headless/field.cljc:79,99,125,146,177,203,238`). Because of this, `:event/attribute-changed` never fires, so these never run from UI input in CLJS or CLJ:
   - `sfo/triggers :on-change` and `:derive-fields`;
   - `fs/mark-complete*` (`statechart/form.cljc:960-998`).

   Effects: the invoice total, line-item subtotal, item→price autofill and category→item reload are all dead, and validation messages never appear on edit. The f-w-s SUI renderer calls `sc-form/input-changed!` correctly.
2. **The only renderer is the headless plain-HTML plugin** (Verified, `system.cljc:7`). The page has no CSS (`ring_middleware.cljc:18-28`). For a real UI, port the f-w-s SUI renderer: `src/main/com/fulcrologic/rad/rendering/semantic_ui/**`, which is CLJC and wired through `install-ui-controls!`. The library's `src/main` has changed only cosmetically since 0.1.3, so the API should match (Suspected). Its `entity_picker.cljc` still requires `uism`, which looks unused (Suspected).
3. **Containers can't render** (Verified). `statechart/container.cljc:186-195` `render-layout` just logs an error and returns nil, and there is no container defmethod. Any Dashboard port renders nothing until this is fixed.
4. **The headless plugin is missing controls and field styles** (Verified):
   - Controls: only `:button`, `:string` and `:boolean` (`controls.cljc:11,22,42`). Missing `:picker`, `:instant` (`:starting-date`/`:ending-date`) and `:uuid`.
   - Fields: no `:pick-many`, `:autocomplete`, or blob image/file upload.
   - Pickers: no quick-create or edit modal (`field.cljc:205-253`).
   - Reports: `ro/links` is not rendered (Suspected).

**P1: missing original features**

5. **Auth/login**: LoginForm, login/logout/check-session, `authorization.clj`, logged-in menu, timezone set at login. Reuse f-w-s `client.cljs` `session-management-nodes`, `ui/login_dialog.cljc` and `model/account.cljc` mutations.
6. **AccountList report**: filter, show-inactive toggle, Enable/Disable row actions, New Account button, AccountListItem list style. `set-account-active` already exists but nothing uses it (`model/account.cljc:97-109`). Reuse f-w-s `ui/account_forms.cljc`.
7. **Master-detail**: needs a custom `sro/statechart` to replace the UISM `master-detail-report-machine`. No usable port exists: f-w-s is still UISM, and sc-demo has it commented out as a TODO.
8. **Sales reports**:
   - SalesReport: mock 1000-row data, denormalized, paginated, picker filter.
   - RealSalesReport: invoice-statistics resolvers, `raw-result-xform` with `rotate-result`, pivot, instant and group-by controls, victory charts that need CLJ stubs.

   f-w-s has the statistics attributes and resolver (`model_rad/invoice.cljc:74-95`, `model/invoice.cljc:33`). sc-demo's `ui/sales_report.cljc` is the original with only the namespaces swapped.
9. **Dashboard container**: blocked by #3.
10. **Account-form extras**:
    - avatar blob, `FileForm` / `file.cljc`, and the files subform;
    - tags (`:pick-many`, quick-create, `TagForm`, `all-tags`);
    - timezone `zone-id` with autocomplete (`timezone.cljc`, `all-time-zones`);
    - server-side blob store, `wrap-persist-images`, blob resolvers, and the `/images` `/files` `/preview` services with upload middleware.

    Upstream `rad.blob` has no UISM dependency, but `upload-file!` is CLJS-only. f-w-s wires the blob server side and has timezone with autocomplete. Its AccountForm does not show the avatar field.
11. **Resolvers dropped** (Verified):
    - `item-category-resolver` (original `item.cljc`).
    - The `:line-item/category` `pc-resolve` (original `line_item.cljc:10-20` vs port `line_item.cljc:10-12`). Existing line items load with no category.
    - InventoryReport works around the first one with a join plus a formatter (`inventory_report.cljc:26,47`). Sorting on `:category/label` is probably broken because rows have no top-level `:category/label` (Suspected).
12. **Report simplifications**:
    - InvoiceList → `InvoiceReport` lost the invoice-id column, the Account Invoices and Delete row actions, and the New Invoice/New Account buttons.
    - InventoryReport lost the category picker filter and the click-category-to-filter link; a text search replaces them.
    - f-w-s has full versions of both (`ui/invoice_forms.cljc:112-126`, `ui/item_forms.cljc:51-72`).

**P2**

13. **Demo tests are not part of any automated run** (Verified):
    - `tests.edn` only looks in `src/test`, and its `-test$` pattern does not match the `*-tests` namespaces.
    - CI runs `-A:dev:tests:clj-tests` without `:demo`.
14. **Small gaps:**
    - Quick-create writes `:time-zone/zone-id`, which isn't in the model (`invoice_forms.cljc:58`). Saving a quick-created account might fail (Suspected).
    - Dropped from the original client and config: the `install-formatter! :affirmation` formatter, batched reads, applying the logging config, and `prod.edn` with `-main`.
    - SQL, Asami and XTDB backend variants are not ported (by design).
    - Housekeeping: there are duplicate `config/` and `resources/config/` directories, `Procfile.dev` points to make targets that don't exist, and the claim in `ui/CLAUDE.md` that the inventory and invoice reports are "new files" is wrong (the originals are in `item_forms.cljc` and `invoice_forms.cljc`).

## Runnability

- **Browser build:** shadow `:main` → `com.example.client/init` is set up. In CLJS it creates `rad-app/fulcro-rad-app`, mounts on `"app"`, and starts the system with URL sync.
- **Serving the page:** there is no `resources/public/index.html`. `ring_middleware.cljc` builds the page with hiccup and injects the CSRF token, then serves `/js/main/main.js` from the classpath folder `public` via `wrap-resource`. `resources/` is on the classpath through the `:dev` alias.
- **Compiles?** The browser build was last fixed in a3d98a5 (2026-02-21). The Phase 8/9 namespace moves came after that, and the phase9 checkbox "Demo CLJS compilation succeeds" is still unchecked. So whether it compiles today is unverified (Suspected).
- **Rendering:** only the headless renderer is installed, so the result is unstyled HTML, and triggers don't work (#1).
- **`(go)`:** `development.cljc` `(go)` starts mount with `config/dev.edn` from the classpath and seeds the same data as the original. It needs `-A:dev:demo`. Looks workable (Suspected).
- **"Everything CLJC":**
  - The only CLJ-only non-test file is `components/database_queries.clj`.
  - `components/*.cljc` are CLJC in name only: every body is inside `#?(:clj)`.
  - `config.cljc:4` requires the CLJ-only `fulcro.server.config` without a reader conditional, so it can't compile under CLJS. That doesn't matter today because nothing on the CLJS side requires it.
  - The test-support files are `.clj`: `headless_client.clj`, `test_server.clj` and the three `headless_*_tests.clj` (acceptable).

## Headless test coverage

- **What's tested:**
  - Routing: startup, clicking Inventory and Invoices, `route-to!` to each form, sequential navigation, and the dirty-form route guard with continue and cancel.
  - Forms: editing account, item and invoice; saving the account name and the item name; reset; primary address loads.
  - Reports: Inventory loads 7 rows and survives a filter event that changes nothing; Invoice rows include Tony and Sally; AccountInvoices reaches the ready state.
- **How:** form tests change data by `swap!`-ing app state directly, never through rendered inputs.
- **Contradiction:** the test docstrings say hiccup returns nil for routed views, yet the routing tests assert that "Inventory Report" is rendered (Suspected).
- **AccountInvoices likely fails:** the test routes with `{}` but asserts more than 0 rows (`headless_report_tests.clj:166-181`). Without an id, `get-customer-invoices` returns `[]` (Suspected).
- **Never tested:**
  - forms: `form/create!` and tempid save, delete, subform add/delete, pickers and option loading, triggers, validation, save errors;
  - reports: sorting, pagination, `control/run!` with parameters, row actions;
  - URL sync (statecharts ships `routing/simulated_history.cljc` for this);
  - every P1 feature.

## Inventory

| Original feature (file) | Status | Evidence | Reusable port elsewhere? | Note |
|---|---|---|---|---|
| Root menu, busy loader (`ui.cljc`) | Simplified | `ui/ui.cljc:88-138` | f-w-s `ui.cljc` (SUI menus, route-denied modal) | No Account/Reports menus, no login state; adds a route-denied modal |
| `defrouter` routing | Ported (scr) | `ui/ui.cljc:52-74` | f-w-s `client.cljs` chart | — |
| HTML5 history / restore-route (`client.cljs`) | Ported in CLJS only | `system.cljc:32` | f-w-s | Not tested headless |
| Auth (`login_dialog`, `authorization.clj`, account mutations) | Missing | — | f-w-s session statechart | Upstream `rad.authorization` is UISM |
| LandingPage | Ported | `ui/ui.cljc:26` | — | — |
| AccountForm with addresses (primary autocreate, sort, can-add/delete) | Ported | `account_forms.cljc:11-34` | f-w-s (no addresses) | — |
| Avatar blob, FileForm/files (`file.cljc`, `file_forms.cljc`) | Missing | — | f-w-s server blob plus SUI `blob_field` | — |
| Tags pick-many with quick-create, TagForm | Missing | — | sc-demo (ns swap only) | No headless `:pick-many` |
| Timezone `zone-id` autocomplete (`timezone.cljc`) | Missing | — | f-w-s `model/timezone.cljc`, SUI `autocomplete` | — |
| BriefAccountForm | Simplified | `account_forms.cljc:36` | f-w-s | No `zone-id` |
| AccountList, AccountListItem | Missing | — | f-w-s `account_forms.cljc` | — |
| AddressForm, state enum | Ported | `address_forms.cljc` | — | No `cancel-route` |
| ItemForm with category picker | Ported | `item_forms.cljc:15` | f-w-s | — |
| InventoryReport | Simplified | `inventory_report.cljc` | f-w-s `item_forms.cljc` | Picker control and links dropped |
| InvoiceForm (customer picker, quick-create, validator, derive) | Ported, but behavior broken | `invoice_forms.cljc:42-78` | f-w-s | #1; no quick-create UI headless |
| LineItemForm triggers converted | Ported, but behavior broken | `line_item_forms.cljc:29-45` | f-w-s | #1, #11 |
| AccountInvoices runtime report | Ported | `invoice_forms.cljc:80` | f-w-s | — |
| InvoiceList | Simplified | `invoice_report.cljc` | f-w-s `invoice_forms.cljc` | Row actions and buttons dropped |
| Master-detail (UISM report machine) | Missing | — | None usable | Needs `sro/statechart` |
| SalesReport (mock data, `sales.cljc`) | Missing | — | sc-demo (ns swap) | Needs `:picker` control |
| RealSalesReport, invoice statistics, pivot, charts | Missing | — | f-w-s model/resolver; sc-demo UI | Needs `:instant`/`:picker` controls |
| Dashboard container | Missing | — | sc-demo (ns swap) | #3 |
| Models (enum, decimal, instant, refs, validators) | Ported (subset) | `model/*.cljc` | — | No file, sales, timezone, tag, password-perm, or statistics attributes |
| Resolvers: `customer-id`, `all-*` | Ported | `model/invoice.cljc:49` | — | — |
| Resolvers: item-category, line-item category, all-tags, time-zones | Missing | — | f-w-s `model/item.cljc:8` | #11 |
| `set-account-active` | Ported, unused | `model/account.cljc:97` | f-w-s | — |
| Save middleware | Simplified | `save_middleware.cljc` | f-w-s | No blob persist |
| Delete middleware | Ported | `delete_middleware.cljc` | — | — |
| Ring: blob services, file upload | Missing | `ring_middleware.cljc:58-68` | f-w-s `ring_middleware.clj` | — |
| Parser | Simplified | `parser.cljc:32-49` | f-w-s | No blob, item, sales or timezone resolvers |
| Seed and `development` `(go)` | Ported | `development.cljc:17-84` | — | No timezone on Tony |
| Config, server | Simplified | `config.cljc`, `server.cljc` | — | No logging config, prod config or `-main` |
| Client `setup-RAD` (SUI controls, formatter) | Simplified | `system.cljc:12-18` | f-w-s `client.cljs` | Headless only |
| SQL, Asami, XTDB variants | Missing | — | — | Out of scope by design |
| i18n | Not applicable | — | — | Original demo uses none |

**Status of the other ports:** f-w-s is the best donor: a real statechart consumer with a SUI renderer, login, AccountList/InvoiceList/InventoryReport with full controls, a timezone field and blob wiring; its master-detail is still UISM. sc-demo is an abandoned three-commit namespace swap. It still uses `defrouter`/`dr/` and the deleted `rad.statechart.routing` namespace plus the UISM-era `fulcro-rad-semantic-ui` 1.4.7, so it won't compile against the current library (Suspected). Its only value is as source text for the sales and dashboard UIs.
