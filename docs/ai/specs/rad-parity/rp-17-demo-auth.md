# Spec: Demo Authentication (App-Level)

**Status**: backlog
**Priority**: P1
**Created**: 2026-10-31
**Owner**: AI
**Depends-on**: rp-09, rp-11, rp-16
**Skills**: `clojure`, `clojure-repl`, `fulcro-spec-tdd`, `statechart`, `fulcro-headless`, `fulcro-rad`, `pathom`, `date-time`

## Context

The original demo has login, logout, check-session, a logged-in menu, and the timezone set at login. The port has none of it. Sources: gap report §4 "Auth", §5; `review/review-demo.md` P1-5; `review/review-ecosystem.md` §1 (authorization row); `review/review-routing.md` P2-15.

Upstream `com.fulcrologic.rad.authorization` has a UISM client (`auth-machine`) that can't talk to statechart sessions. Server-side `redact` still works, through upstream `resolvers-common/secure-resolver`.

**Human decision 6:** library-level auth is **not** decided. Demo login is app-level.

Donor (read-only):
- `../fulcro-with-statecharts`: `client.cljs` (`session-management-nodes`), `ui/login_dialog.cljc`, `model/account.cljc` login/logout/check-session mutations.
- That consumer hard-codes the post-login route to `AccountList`. This spec must do bookmark replay instead.
- Original server side: `../fulcro-rad-demo` `authorization.clj` and account mutations.

## Requirements

1. **Session chart.** An app-level session statechart in the demo: checking session → logged out | logged in. It handles login (success and failure), logout and check-session at startup. All session state lives in Fulcro state and the chart. No React hooks for data.
2. **Server.**
   - login, logout and check-session mutations or resolvers, with a hashed password attribute (seeded for the demo users) and session storage in the ring session;
   - the original demo's authorization behavior for unauthenticated queries.
3. **Auth-gated routes with bookmark replay.**
   - Routing to a gated route while logged out shows login and remembers the requested target **and params**.
   - After login, routing continues to that target. There is no hard-coded post-login route.
   - Restoring a gated bookmark URL behaves the same, through rp-11 URL sync.
   - Implement with the routing chart. scr's busy/deny mechanics and rp-09's pattern are fine to reuse if they fit; record the design.
4. **UI.**
   - A login dialog: SUI in the browser, headless in CLJ.
   - A logged-in menu with the user name and Logout.
   - On login, the timezone is set from the account's `zone-id` (rp-15) via `dt/set-timezone!`.
5. **Headless tests** (new demo test ns, real server fixture):
   - a gated route while logged out → login is shown;
   - correct login → lands on the requested route with its params;
   - wrong password → error shown, still logged out;
   - logout → gated again;
   - bookmark replay via simulated history;
   - an unauthenticated server query is denied or redacted, per req 2.

## File Ownership

- `src/demo/com/example/ui/ui.cljc` (routes, menu), `ui/login_dialog.cljc` (new), and a new session-chart ns, e.g. `src/demo/com/example/ui/session.cljc`
- `src/demo/com/example/client.cljc` and `system.cljc`: starting the session chart only
- `src/demo/com/example/model/account.cljc`, model additions, seed, `components/**` (auth middleware, parser registration)
- `src/demo/com/example/headless_auth_tests.clj` (new)
- `src/demo/com/example/ui/CLAUDE.md` (your own section only)

Read-only: `src/main`. If a library change is needed, report it to the conductor.

## Affected Modules

- Demo session chart, login UI, routes, server auth

## Approach

- Port the f-w-s session nodes. Replace its hard-coded post-login route with a remembered pending route stored in the session chart's data.

## Open Questions

- **Library-level auth (human decision 6: undecided).** Should this lib ship a statechart-based replacement for upstream `com.fulcrologic.rad.authorization`? That would mean a provider/session chart API plus a gated-route helper for the routing chart.
  - Recommendation: not yet. Ship app-level auth in the demo first. Revisit once a second consumer (fulcro-with-statecharts or a real app) needs the same pattern, then extract the session chart and gated-route helper.
  - Tradeoff: until then, every app writes its own session chart, and there's no library answer for upstream's multi-provider flow.
  - Human to decide.

## Verification

1. [ ] New auth tests (req 5) fail before (no auth) and pass after.
2. [ ] Tests run with kaocha in the implementer's own REPL. No CLI test runs.
3. [ ] Full CLJ suite green, `:unit` + `:demo`. Existing demo tests are updated to log in where routes became gated.
4. [ ] CLJS `main` compiles with 0 warnings. Chrome check of login, logout and bookmark replay.
5. [ ] `cljfmt check <changed files>` passes from the repo root (root `CLAUDE.md` "Build Hygiene").
6. [ ] Session-chart design recorded in `ui/CLAUDE.md`. REPL and server stopped. Committed and pushed.
