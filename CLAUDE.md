# fulcro-rad-statecharts

Convert fulcro-rad from Fulcro UI State Machines (UISMs) to the fulcrologic statecharts library. All routing uses statecharts routing (not Fulcro dynamic routing). Everything must be CLJC for headless testing. Public API stays as similar as possible.

## Commits and Pushing

Commit and push (on the current branch) at major checkpoints as you work. Don't wait until the end — push progress incrementally.

## Spec-Based Team Workflow

This project uses a spec-driven, team-based workflow. See `docs/ai/specs/WORKFLOW.md` for full details, `docs/ai/specs/TRACKER.md` for status.

**The top-level agent is a pure conductor** — it creates teams, spawns opus-model implementer agents, assigns tasks from specs, and runs critique. It does NO implementation, testing, or file editing itself. Implementers should load appropriate skills, use sub-agents for research, and keep context windows small. Each agent owns its own REPL — start one when needed, kill it when done. Don't leave orphan processes.

## Sibling Projects

All related projects live in the parent directory (`../`). Use relative paths only — never absolute paths.

What each repo is for (verified from git history 2026-10-31):

- `../statecharts`: the engine, the Fulcro integration, and the routing system `integration.fulcro.routing` (`scr`). Its `ui-routes`, `route-url`, `route-history` and `rad_integration` namespaces are deprecated. Never use them.
- `../fulcro-rad`: upstream RAD (UISM). This lib depends on it and reuses `form.impl`/`report.impl`. Its `statecharts` branch is an abandoned "RAD 2.0" attempt. Ignore it.
- `../fulcro-rad-semantic-ui`: the published semantic-ui plugin. Legacy `com.fulcrologic.rad.rendering.semantic-ui.*` code must never change. The statechart variant lives on `main` (no branch) under `com.fulcrologic.rad.statechart.rendering.semantic-ui.*` and `com.fulcrologic.rad.statechart.semantic-ui-options`. That code is duplicated from legacy on purpose. Its `statecharts` branch is abandoned.
- `../fulcro-with-statecharts`: a sandbox **app** that consumes this lib. It is not a dependency. Use it read-only as a donor (its semantic-ui clone, login chart, demo screens).
- `../fulcro-rad-demo`: the original UISM demo, which `src/demo` ports. Read-only donor.
- `../fulcro-rad-statecharts-demo`: abandoned. Only useful as source text for the sales/dashboard UIs.
- `../fulcro-rad-tui`: a downstream consumer of this lib. It still renders reports, controls and containers via the map-based registries, so keep those working as a fallback.
- `../fulcro-rad-datomic`: must keep working with this lib. Do not edit it.

## Current Effort: rad-parity (branch `rad-parity`)

Goal: full parity with UISM RAD. Specs are in `docs/ai/specs/rad-parity/`, the review that seeded them is in `docs/ai/specs/rad-parity/review/`, and status is in `TRACKER.md`.

Binding human decisions:

- View mode comes back (`view!`, `view-mode?`, `read-only?` honoring view mode).
- `fo/confirm` and `fo/silent-abandon?` are dropped. Route denial and confirmation belong in a routing parallel region (scr `:routing-info`). The old keys should warn at compile time.
- `form/delete!` sends the legacy symbol `com.fulcrologic.rad.form/delete-entity`.
- Library-level auth is undecided. Demo login is app-level.

## Maintaining Context with CLAUDE.md Files

As you work, **create and update `CLAUDE.md` files in subdirectories** to capture decisions, patterns, and conventions discovered during implementation. These act as compact memory for future sessions and agents working in that area.

- When you make a non-obvious decision, record it in the nearest `CLAUDE.md`
- When you discover a pattern or convention in existing code, document it
- When you encounter a gotcha or pitfall, write it down so the next agent doesn't repeat the mistake
- Keep them concise — bullet points, not essays
- Nest them: `src/main/com/fulcrologic/rad/CLAUDE.md` for RAD-wide notes, `src/main/com/fulcrologic/rad/form/CLAUDE.md` for form-specific notes, etc.
- Update them when things change — stale notes are worse than no notes
