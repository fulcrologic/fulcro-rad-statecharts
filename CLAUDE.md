# fulcro-rad-statecharts

Convert fulcro-rad from Fulcro UI State Machines (UISMs) to the fulcrologic statecharts library. All routing uses statecharts routing (not Fulcro dynamic routing). Everything must be CLJC for headless testing. Public API stays as similar as possible.

## Commits and Pushing

Commit and push (on the current branch) at major checkpoints as you work. Don't wait until the end — push progress incrementally.

## Spec-Based Team Workflow

This project uses a spec-driven, team-based workflow. See `docs/ai/specs/WORKFLOW.md` for full details, `docs/ai/specs/TRACKER.md` for status.

**The top-level agent is a pure conductor** — it creates teams, spawns opus-model implementer agents, assigns tasks from specs, and runs critique. It does NO implementation, testing, or file editing itself. Implementers should load appropriate skills, use sub-agents for research, and keep context windows small. Each agent owns its own REPL — start one when needed, kill it when done. Don't leave orphan processes.

## Sibling Projects

All related projects live in the parent directory (`../`). Use relative paths only — never absolute paths.

## Maintaining Context with CLAUDE.md Files

As you work, **create and update `CLAUDE.md` files in subdirectories** to capture decisions, patterns, and conventions discovered during implementation. These act as compact memory for future sessions and agents working in that area.

- When you make a non-obvious decision, record it in the nearest `CLAUDE.md`
- When you discover a pattern or convention in existing code, document it
- When you encounter a gotcha or pitfall, write it down so the next agent doesn't repeat the mistake
- Keep them concise — bullet points, not essays
- Nest them: `src/main/com/fulcrologic/rad/CLAUDE.md` for RAD-wide notes, `src/main/com/fulcrologic/rad/form/CLAUDE.md` for form-specific notes, etc.
- Update them when things change — stale notes are worse than no notes
