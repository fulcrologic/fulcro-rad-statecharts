# fulcro-rad-statecharts

Convert fulcro-rad from Fulcro UI State Machines (UISMs) to the fulcrologic statecharts library. All routing uses statecharts routing (not Fulcro dynamic routing). Everything must be CLJC for headless testing. Public API stays as similar as possible.

## Spec-Based Workflow

This project uses a spec-driven workflow. See `docs/ai/specs/WORKFLOW.md` for full details, `docs/ai/specs/TRACKER.md` for status.

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
