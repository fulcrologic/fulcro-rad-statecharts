# Routing review: fulcro-rad-statecharts vs. statecharts routing

How this was checked: I only read code. Nothing was executed (no REPL, per the brief). **Verified** means I followed the code path all the way through. **Suspected** means it is inferred and needs a test to confirm.

Statecharts RC18 → HEAD (RC19+): the only change is the `promesa.cljc` shim. **There are no routing changes to adopt.** The consumer app is on RC13; the RC13→RC18 routing diff is about 10 lines (empty-segment validation, cross-chart params fix), so it should bump.

## P0

1. **Routed forms never get their start options.** `edit!` ignores `params` and sends only `{:id id}` (`src/main/.../statechart/form.cljc:1912`). The istate invoke passes only actors and aliases. The form chart's data-model seeds `{:options {}}` (form.cljc:1250), so `store-options` always stores `{}` (form.cljc:769). As a result, `create!` `:initial-state`, `:on-saved`, `:on-cancel`, `:on-save-failed` and `:embedded?` do nothing for any routed form. Even the deprecated `rad_integration/edit!` forwarded `:params`. — Verified
2. **Only standard reports and forms can be route targets.**
   - `report-route-state` always calls the standard `report/start-report!` (report.cljc:1107-1131). Its alias set is missing `:loaded-page` (needed by incremental reports) and `:page-cache`/`:loaded-page`/`:total-results`/`:point-in-time` (needed by server-paginated reports). Those report types break when routed. — Verified
   - There is no container route helper. `defsc-container` sets `sfro/statechart` but no `sfro/actors`, while the container chart resolves `:actor/container` (container.cljc:67). `scr/istate` provides only `:actor/component`, so an istate'd container (or report) starts with a nil class. Containers can only be routed with a hand-written `rstate` plus an `entry-fn` that calls `start-container!`. — Verified
3. **The README and migration guide teach a namespace this lib deleted.** They use `com.fulcrologic.rad.routing :as rroute` (`rroute/form-route-state`, `edit!`, `create!`, `route-denied?`) and `com.fulcrologic.rad.application/install-statecharts!` (README.adoc:95-168, migration-guide.adoc:170-265). That name now resolves to upstream fulcro-rad 1.6.24's Dynamic-Router namespace, which is still on the classpath. So `rroute/route-to!` silently drives Fulcro dynamic routing, and the other calls don't compile. The docs' `form-route-state {:route/params #{:account/id}}` also replaces the `#{:id}` default, which makes form URLs impossible to restore. — Verified

## P1

4. **Route-denied and abandon behavior regressed.**
   - Nothing ever sends `:event/route-denied` to a form. That makes `route-denied-expr`, `continue-abandoned-route-expr`, `fo/confirm` and `:ui/route-denied?` dead code. There is no public `continue-abandoned-route!` or `clear-route-denied!`, which upstream rendering plugins call. Only an app-level modal driven by `:routing-info/open` works. — Verified
   - Force-continuing away from a dirty form does not undo its edits. istate exit only cancels the child session, and the `form-route-state` exit-fn (form.cljc:1899-1905) never calls `pristine->entity`. The dirty values stay in normalized state, so reports keep showing them. Upstream restored the pristine values. — Verified
   - `form-busy?` (form.cljc:412) ignores `fo/silent-abandon?` and `fo/read-only?`, which upstream's `form-allow-route-change` respected. — Verified
   - `container-busy?` (container.cljc:264) blocks routing while any child report is loading. The user then sees the "unsaved changes" prompt. Upstream containers never blocked routing. — Verified
5. **After a create is saved, the new URL is pushed instead of replacing the old one.** `rewrite-created-form-route*` (form.cljc:737) updates the root routing params via `apply-action`. The child session's save then takes url-sync branch 4, which calls `-push-url!` (`../statecharts/.../routing.cljc:1327-1342`). Pressing Back lands on the tempid URL, which opens a blank create form. Upstream used `history/replace-route!`. The fix is to use `replace-route-params!`. — Verified in code; no test covers it
6. **`fo/route-prefix`, `ro/route` and `co/route` are ignored.** URLs come out as `/AccountForm?_p=<base64 transit>`. The project's own spec (docs/ai/specs/app-initialization.md:165) required mapping these to `:route/segment`, and the consumer app sets `:route/segment` by hand on every route (`../fulcro-with-statecharts/src/main/com/example/client.cljs:72-86`). — Verified
7. **Report URL sync is incomplete.**
   - `sync-url-params-expr` (report.cljc:520-540) mirrors only `:ui/parameters`: local controls, sort, page and selected-row.
   - `replace-route-params!` overwrites the whole params map (routing.cljc:1052). Global (non-`:local?`) control values passed through `route-to!` therefore drop out of the URL at the first `:state/ready`.
   - Later changes to global controls never reach the URL. Upstream's `control/set-parameter` did that.
   - Server-paginated and incremental reports have no URL sync at all.
   — Verified
8. **`report-route-state` passes the whole routing data model as route params.** It sends `(merge data event-data)` (report.cljc:1129), which includes the `:fulcro/state-map` snapshot, `:_event` and `:route/idents`. That gets stored in the report session's local data inside the Fulcro DB. It should pass only `[:routing/parameters <target>]` plus the event data. This flaw was copied from `rad_integration`. — Verified
9. **`form-route-state` throws when the chart is built, for any form that uses `fo/statechart <keyword>`.** For a keyword, the form macro sets only `sfro/statechart-id`. `istate` then tries to work out `:route/reachable` automatically and throws when `sfro/statechart` is missing (routing.cljc:505-509). Fix: default `:route/reachable` to `#{}`. — Verified
10. **No tests cover URL behavior.**
    - `test-helpers/create-test-app-with-url-sync` is defined but never used.
    - No test in `src/test` references `form-route-state`, `report-route-state` or `sync-url-params-expr`.
    - The demo installs URL sync only in CLJS (system.cljc:32).
    - The demo E2E form tests reach routed forms through `ident->session-id` (headless_form_tests.clj:55,131). Since e364841, routed forms are invoked by istate and get generated session ids, and those tests were last edited before that commit. — Suspected stale/failing
    - The helpers themselves can be tested headlessly: SimulatedURLHistory works in CLJ.

## P2

11. The RAD helpers write only to the **root** routing session (`rewrite-created-form-route*`, and `replace-route-params!` via `local-data-path scr/session-id`). That is wrong when RAD routes live inside an istate child module. — Verified
12. `replace-route-params!` doesn't update url-sync's `prev-url`. The next root save with an unchanged route (for example when the route-denied prompt opens) pushes a duplicate history entry. This would be a statecharts-side fix. — Suspected
13. `form-route-state` exit-fn cleanup:
    - `class->registry-key` returns nil for symbol or keyword targets, and istate's own on-exit deletes `:route/idents` before the exit-fn runs. So `::form-session-ids` is probably never cleaned. — Suspected
    - It deletes `[:route/idents form-ident]`, which is the wrong key. — Verified
14. `rad-app/install-url-sync!` documents a `:prefix` option (application.cljc:65) that scr ignores. A prefix actually needs `:url-codec (ruct/transit-base64-codec "/app")`. — Verified
15. Helpers the consumer has to write itself are missing (Verified absent):
    - an auth-gated route with bookmark replay (the consumer hard-codes the post-login route to `AccountList`);
    - an equivalent of upstream `can-change-route?`;
    - a generic `update-route-params!`.
16. Docs drift. `src/main/com/fulcrologic/rad/CLAUDE.md` and `src/demo/.../ui/CLAUDE.md` still say `form-route-state` is `rstate` plus `start-form!`. The statecharts docs never mention this lib, and `rad_integration.cljc` still says "Better RAD integration coming soon". — Verified

## istate vs. side-effect start

The new routing system is designed around istate for anything with a co-located chart. Its Guide says to use `sfro/statechart` → `istate`, and deep-busy, deep URL, `send-to-self!` and `current-invocation-configuration` all walk the invocation tree. The project's spec (macro-rewrites.md:30) also says each macro must produce a component that works as an istate target. But this lib only delivers that for forms.

- **Forms (istate):**
  - Pros: the session lives and dies with the route, deep busy works, and `done.invoke` could carry the cancel-route instead of the in-child `route-to!` side effect.
  - Costs: session ids aren't deterministic (hence the `::form-session-ids` map), and options have to be threaded through invoke params, which is the missing piece in P0-1.
- **Reports (rstate + side effect):**
  - Pros: keeps upstream caching and `:event/resume` across visits, and keeps the ident-based ids that containers broadcast to.
  - Costs: reports are outside the invocation tree, the session is never stopped, and the macro's `sfro/statechart` is misleading.
- **Recommendation:**
  - Keep the side-effect start for reports, since caching is a parity feature. Add `container-route-state` and variant-aware report starting.
  - Either drop `sfro/statechart` from the report and container macros, or make them istate-safe (add `sfro/actors` and set aliases in the data-model).

## Feature mapping

| Upstream feature | New-system equivalent | Wired? | Evidence | Note |
|---|---|---|---|---|
| `install-route-history!` | `scr/install-url-sync!` + `url-sync-on-save` | Yes | application.cljc:28-68; demo system.cljc:32 | CLJS only in demo; `:prefix` doc wrong |
| `rad-routing/route-to!` | `scr/route-to!` | Yes | ui.cljc:113-125 | no RAD facade ns |
| `back!` / forward | `scr/route-back!`/`route-forward!` | Yes | form.cljc:1144 | nil without URL sync |
| Form route `[prefix :action :id]` | `:route/segment` + `_p` | No | form.cljc:1877-1905 | prefix ignored (P1-6) |
| `form/edit!`/`create!` params | istate invoke params | No | form.cljc:1907-1919, 1250, 769 | P0-1 |
| `view!` | — | No | | view-mode removed on purpose |
| Bookmark/reload: form (edit, tempid) | transit `_p` with `:id` | Partial | form.cljc:1895-1898 | works with default `#{:id}`; docs break it |
| Bookmark/reload: report sort/page/sel | `report-route-state` + `initialize-params-expr` | Partial | report.cljc:196-203, 1095-1105 | standard report, local controls only |
| `update-route-params!` (report state) | `scr/replace-route-params!` | Partial | report.cljc:538 | overwrites map; root only |
| History replace after save | `replace-route-params!` | No | form.cljc:737, routing.cljc:1340 | pushes (P1-5) |
| `allow-route-change?` | `sfro/busy?` → `form-busy?` | Partial | form.cljc:412,460 | no silent-abandon/read-only |
| Deep busy (subforms / nested charts) | `scr/busy?` deep-busy | Yes | routing.cljc:588-643 | `fs/dirty?` covers subforms; containers over-block |
| `:route-denied` / `fo/confirm` / continue | `:routing-info/open`, `force-continue-routing!` | Partial | ui.cljc:105-136; form.cljc:1153-1180 dead | app-level modal only |
| `form-will-leave` abandon + pristine | istate cancel | Partial | form.cljc:1899-1905 | no pristine restore |
| `fo/cancel-route` | `leave-form-expr` | Yes | form.cljc:1121-1151 | `:back` needs URL sync |
| Report → form links | `form/edit!` | Yes (headless) | rendering/headless/report.cljc:97 | upstream plugins call upstream `form/edit!` |
| Report route + params | `report-route-state` | Partial | report.cljc:1107-1131 | std reports only; leaks data (P1-8) |
| Container as route target | (none) | No | container.cljc:67,264-267 | P0-2 |
| Route config validation | `start!`/`install-url-sync!` `:routing/checks` | Yes | application.cljc:48-55 | defaults to `:warn` |
| Parallel regions / `:route/reachable` | `:parallel?`, istate reachable | Partial | routing.cljc:466-545 | not used by RAD; keyword `fo/statechart` throws (P1-9) |
| Deprecated ns use (`ui-routes`, `route-url`, `route-history`, `rad-integration`) | — | None used | grep `src/` | docs point to upstream `rad.routing` (P0-3) |
| Headless URL tests | SimulatedURLHistory | No | test_helpers.cljc:39-49 unused | P1-10 |

## rad_integration.cljc parity

- `report-state` → `report-route-state`: Yes, same pattern, including the same `(merge data event-data)` flaw.
- `form-state` → `form-route-state`: Partial. It is now istate, but it lost two things: `:params` pass-through, and undoing edits on exit.
- `leave-form` with deferred `route-to!` → `leave-form-expr`: Yes, and it adds `:back` and `:none`.
- `edit!`/`create!` with `:params` → No.
- `:state/asking-to-discard-changes` → No equivalent.

In practice this lib is the successor to `rad_integration` (it uses only the new routing namespace). But nothing in statecharts points to it, so `rad_integration` should be deprecated with a pointer here once P0-1 and P0-2 are fixed.
