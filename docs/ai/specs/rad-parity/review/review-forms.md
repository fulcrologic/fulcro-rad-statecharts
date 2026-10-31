# Forms parity review: statechart form vs UISM RAD 1.6.24

Path keys: **SF** = `src/main/com/fulcrologic/rad/statechart/form.cljc`, **SFO** = `statechart/form_options.cljc`, **UF** = `../fulcro-rad/src/main/com/fulcrologic/rad/form.cljc`, **HF/HFm** = `src/main/com/fulcrologic/rad/rendering/headless/field.cljc` / `form.cljc`, **SCR** = `../statecharts/src/main/com/fulcrologic/statecharts/integration/fulcro/routing.cljc`.
V = Verified (traced in code), S = Suspected.

## Prioritized gaps

### P0: core feature broken
1. **Attribute validation is not enforced on save (V).** Upstream `convert-options` installs a default `::validator` (`attr/make-attribute-validator (form-and-subform-attributes Form) true`, UF:559). SF `convert-options` (SF:448-473) has no default. So `form-valid?`/`valid?` (SF:1018, 1588-1616) only check that required keys are non-nil, plus a user validator if one is given. `ao/valid?`, blank required strings, and every subform field are skipped. Fields can render as invalid (`invalid-attribute-value?` falls back to `attr/valid-value?`, SF:1659) while the save still goes through.
2. **Server save errors are never shown (V).** `on-save-failed-expr` runs `(get (scf/mutation-result data) save-mutation)` (SF:1099-1100). `scf/mutation-result` already returns `:body vals first`, i.e. the mutation's value with the symbol key stripped (`statecharts/.../integration/fulcro.cljc:393-397`). Result: `errors` is always nil, `::form/errors` is never set, and `server-errors` comes back empty.
3. **`form/delete!` sends a mutation the standard server doesn't have (V).** The CLJS `delete-entity` is defined in `statechart.form` (SF:1494-1509), so its symbol is `com.fulcrologic.rad.statechart.form/delete-entity`. Servers register upstream `form/resolvers` (`com.fulcrologic.rad.form/delete-entity`; UF:1890; demo `src/demo/com/example/components/parser.cljc:46`). SF exports no `resolvers`. `delete!` is also a no-op in CLJ (SF:1515).

### P1: features users will hit
4. **The `on-change` trigger is never called for add-row or delete-row (V).** `on-change` and `old-value` are bound but never used (SF:1188-1194, 1226). Upstream calls `apply-on-change` with `(parent-ident relation old new)` (UF:1383-1423).
5. **`autocreate-on-load?` runs in the wrong order (V).** SF:926-934 runs `add-form-config*` and `mark-complete` before `build-autocreate-ops`; upstream autocreates first (UF:1225-1231). The new child gets no form config, and the parent is dirty straight after load, which also blocks routing via `form-busy?`.
6. **`edit!`/`create!` drop their params, and routed forms never get options (V).** `edit!` ignores `params` (SF:1911-1912). `istate` passes only actors and aliases (SCR:551-564). `store-options` uses `(or (:options data) …)`, but the data-model seeds `{:options {}}` (SF:1250, 769), so `:on-saved`, `:on-cancel`, `:on-save-failed`, `:embedded?` and `:initial-state` only work through `start-form!`.
7. **Cancel ends the form session (V).** `:event/cancel` → `:state/leaving` → final `:state/exited` (SF:1293, 1305-1310). Upstream `leave-form` stays in `:editing` (UF:994-1036). With `cancel-route :none`, `embedded?`, `start-form!`, or `:back` without URL sync (`scr/route-back!` returns nil, SCR:1392-1398), the form stays on screen with no session behind it.
8. **Abandon confirmation changed and `fo/confirm`/`silent-abandon?` do nothing (V).** Nothing sends `:event/route-denied`: the routing chart raises `:event.routing-info/show` instead (SCR:776-779), so `route-denied-expr` (SF:1153) never runs. The default is no longer `js/confirm`: navigation is silently blocked unless the app renders `scr/route-denied?` UI. `form-busy?` ignores `silent-abandon?` and `read-only?` (SF:412-419 vs UF:500-509). `clear-route-denied!` and `continue-abandoned-route!` are missing. Forced routing leaves the entity dirty (S).
9. **View mode is gone (V).** `view!` and `view-mode?` are removed, and `read-only?` no longer checks view mode (SF:1462-1489 vs UF:1556-1582).
10. **A keyword `sfo/statechart` gets overwritten in `start-form!` (V).** The keyword becomes `sfro/statechart-id` (SF:472). `start-form!` then registers the default `form-chart` under that id (SF:385-394). The routed/`istate` path is unaffected.
11. **Picker preload is incomplete (V).** `load-picker-options-expr` (SF:936-953) covers only master-form attributes (subform pickers are skipped). It also needs non-nil field-options, so `po/*` set only on the attribute is skipped, and it passes normalized props, not UI props, to `po/query-parameters`.
12. **The headless renderer bypasses the form chart (V).** Field `onChange` calls `m/set-string!`/`set-value!` directly (HF:78, 96-100, 124, 145, 176, 203, 238-241), and nothing calls `input-blur!`. Headless UI tests therefore never exercise `on-change`, `derive-fields`, mark-complete (validation display) or error clearing.
13. **CLJS edits are async (S).** `input-changed!` uses async `scf/send!` (SF:1545); upstream used `uism/trigger!!` (UF:1744). Expect the controlled-input cursor jump.
14. **Two `:default` render methods compete (S).** SF:227/273 and HFm:162 both define `fr/render-form :default` and `fr/render-field :default`, and so does upstream (UF:299/364). Any client that loads `com.fulcrologic.rad.form` (the demo's `model/account.cljc:8` does, unconditionally) makes rendering depend on load order. If upstream loads last, forms render blank.

### P2: minor / docs
15. Dead `sfo` keys: `sfo/statechart-id`, `on-started`, `on-saved`, `on-save-failed` are documented (SFO:56-81) but never read (V). `:on-blur` works but is undocumented.
16. `:started` runs in `:initial` before the create merge or load is issued (SF:772, 1252); upstream runs it after (V). `on-change` gets a `:fulcro/state-map` from before the change (SF:988); upstream's env already had the new value (V).
17. Reload double-loads: the transition script and `:state/loading` on-entry both call `start-load-expr` (SF:1266-1274, 1282, 1321) (V). Reload is not blocked on a tempid (upstream UF:1045-1049) (V).
18. `created-form-route-update` effectively never fires: Fulcro's tempid `prewalk-replace` remaps `:original-ident` in `::sc/local-data` before `:event/saved` (`fulcro/.../tempid.cljc:85-96`). The URL probably still updates through the remapped routing params plus URL-sync on-save. It should use `scr/replace-route-params!` (S).
19. `cancel-route` as a vector of strings or `{:route [...]}` is ignored (SF:1138-1150) (V).
20. `valid?`/`invalid?` dropped `->arity-tolerant` (SF:1586 vs UF:1796), so a plain 2-arity validator throws (V).
21. The `form-route-state` exit-fn calls `class->registry-key` on a keyword/symbol target and deletes the wrong path, leaking `::form-session-ids` (SF:1902-1905) (S).
22. Missing re-exports: `parent-relation`, `save-as-form` (client), `save-form`/`pathom-plugin`/`resolvers`, the union helpers, and `use-form` (rad-hooks). The users of these must require upstream `com.fulcrologic.rad.form`, which leads to #14.
23. Custom charts: the exported pieces are fragments (SF:1317-1358) plus public `*-expr` fns. There are no state-level fragments (loading/saving/creating). `derive-fields-ops`, `build-autocreate-ops` and `actor-ident` are private. The fragments hard-code `:state/exited` and `:state/loading`.
24. Docs are stale. `docs/migration-guide.adoc:64-75, 274, 286-289` cites non-existent namespaces (`form-chart`, `form-machines`, `rroute/*`, `fo/statechart`). The `start-form!` docstring says `::form/statechart` (SF:378). `DevelopersGuide.adoc` documents `fo/triggers`/`fo/machine`, both of which now fail to compile.
25. `fo/route-prefix` is silently ignored (no warning).

## Option table (`fo/*`)

| Option | Status | Evidence | Note |
|---|---|---|---|
| id, attributes | Works | SF:437-459 | V |
| layout | Works (renderer) | HFm:46-66 | V |
| tabbed-layout, debug?, show-header?, add-label, action-buttons, controls | Renderer-only; headless ignores | HFm:17-43 | Upstream core doesn't read these either; the SUI plugin needs upstream form (V) |
| title | Works (renderer) | HFm:132 | V |
| field-visible?, fields-visible?, omit-label?, field-label(s), validation-message(s), field-style-config(s), input-props, enumerated-labels | Works | SF:1491-1492, 1561, 1663, 1669-1702 (form-impl delegates) | V |
| field-style(s) | Works | SF:190-197; form-impl default-state | V |
| field-options | Partial | SF:950-951 | Picker preload gaps (#11) |
| validator | Partial | SF:1588-1616 | No default validator (#1); arity (#20) |
| route-prefix | Dead-key / API-changed | SF:439 | Routes now come from `form-route-state`/`istate` |
| cancel-route | Partial | SF:1127-1150 | #7, #19 |
| query-inclusion (form + attr) | Works | SF:474-479 | Covers blob attrs (V) |
| default-value(s) | Works | SF:792 → form-impl | Create only (V) |
| subforms, subform, ui | Works (core) / Partial (headless) | SF:349; HFm:76-79 | Headless reads the raw `fo/subforms`, so it misses `fo/subform` and fn/registry-key forms (V) |
| layout-styles, fro/style | Works | SF:136-145 | V |
| triggers | API-changed → `sfo/triggers` | SF:299-300 | Compile error on `fo/triggers` (literal maps only) |
| sort-children | Works | form-impl/form-pre-merge, SF:447 | On load only, same as upstream |
| read-only-fields, read-only? | Works | SF:1462-1489 | No view mode (#9) |
| can-add?, can-delete? | Renderer-only, Partial | HFm:84-114 | Headless covers to-many only; the chart doesn't enforce them (neither does upstream) |
| machine | API-changed → `sfo/statechart` | SF:301-302, 441 | Keyword form bug (#10) |
| initialize-ui-props | Works | SF:839-867; form-impl default-state | V |
| save-mutation | Works | SF:1035-1048 | V |
| save-params | Works | SF:1364-1367 | V |
| silent-abandon? | Dead-key | (no reader) | #8 |
| confirm | Dead-key in practice | SF:1158 | #8 |
| autocreate-on-load? (subform) | Broken | SF:810-837, 926-934 | #5 |
| sfo/statechart | Works (map) / Broken (keyword + start-form!) | SF:385-394 | #10 |
| sfo/statechart-id, on-started, on-saved, on-save-failed | Dead-key | SFO:56-81 | #15 |

## UISM states/events → chart

| UISM | Statechart | Status |
|---|---|---|
| `:initial` (store options, create or edit, `started`) | `:initial` → `:state/creating` or `:state/loading` (SF:1252-1261) | Works; `started` timing (#16) |
| `:event/loaded` | `on-loaded-expr` (SF:913) | Partial (#5). Adds an `:after-load` trigger |
| `:event/failed` | → `:state/load-failed` + "Load failed." | Works. Adds a retry state |
| global `:event/exit` | `:state/active` (SF:1264) | Works |
| global `:event/reload` | loading/load-failed/editing | Partial (#17); not in `:saving` |
| global `:event/mark-complete` | editing only | Partial |
| `:event/attribute-changed` | SF:960 | Works (`on-change` state timing #16) |
| `:event/blur` (no-op upstream) | SF:1000 + `:on-blur` trigger | Works (extended) |
| `:event/add-row`, `:event/delete-row` | SF:1182, 1221 | Partial (#4) |
| `:event/save` (valid / invalid) | SF:1289-1291 | Partial (#1) |
| `:event/saved` (history replace, `saved`, on-saved txn, pristine) | SF:1050-1087 | Works except URL (#18, S) |
| `:event/save-failed` | SF:1089-1109 | Broken (#2) |
| `:event/reset` | SF:1292 | Works |
| `:event/cancel` | → `:leaving` → final | Behavior change (#7) |
| `:event/route-denied` / `:continue-abandoned-route` / `:clear-route-denied` | Handlers exist (SF:1295-1297) | Unreachable by default (#8) |
| `:state/asking-to-discard-changes` | Absent | Upstream never enters it either |

## Public API

| Upstream fn | Status | Evidence |
|---|---|---|
| save!, undo-all!, cancel!, add-child!, delete-child!, input-blur!, input-changed!, trigger!, server-errors, mark-all-complete!, undo-via-load!, abandon-form!, start-form!, read-only?, field-context, with-field-context, render-subform, defunion, form | Works (session lookup via `::form-session-ids`, SF:54-61) | SF:1360-1875 |
| edit!, create! | Partial (params dropped) | SF:1907-1919 |
| view!, view-mode? | Missing | #9 |
| delete! | Broken | #3 |
| clear-route-denied!, continue-abandoned-route! | Missing | UF:2194-2204 |
| form-will-enter / will-leave / allow-route-change | Replaced by `sfro/*` + `form-busy?` | SF:412-419, 460-470 |
| install-*-renderer!, render-fn | Removed → multimethods | SF:133-149 |
| UISM helpers (start-edit, leave-form, calc-diff, undo-all, ...) | Replaced by public `*-expr`/`*-ops` | SF:760-1238, 873-895 |
| save-form, save-as-form, pathom-plugin, resolvers, save-form*, parent-relation, union helpers | Not re-exported | #22 |

## Not covered by the core engine either way
- **Blob uploads.** Upstream core doesn't block save while an upload is in progress; that is renderer-level (SUI `blob_field`). `upload-file!` is CLJS-only upstream. Attribute `query-inclusion` from `defblobattr` is merged (SF:474), but there is no headless file-upload renderer. Status: parity with core; renderer gap.
- **Quick-create and picker options** (`po/quick-create`, `allow-create?`, `allow-edit?`, `po/form`): renderer-only. Headless has a plain `<select>` (HF:205-245).

## CLJS-only or divergent behavior
- `delete!` is a CLJS-only body, so it does nothing in CLJ/headless (SF:1515).
- `route-denied-expr` confirms with `js/confirm` in CLJS and `(constantly true)` in CLJ (SF:1162).
- Headless fields use `m/set-*!!` in CLJ and `m/set-*!` in CLJS, so the two take different code paths (HF:78-79, etc.).
- Everything else in SF is CLJC and runs headless.
