# fulcro-rad-statecharts Workflow

## Sibling Project Locations

All related projects (fulcro-rad-demo, fulcro-rad-datomic, statecharts, dataico-expansion, fulcro-rad-semantic-ui, etc.) live in the **parent directory** (`../`). Specs reference them using relative paths like `../fulcro-rad-demo/`. Do not use absolute paths — developers may have the projects checked out in different locations, but the parent directory structure is consistent.

## The Conductor Pattern (Team-Based)

**The top-level agent is a pure coordinator.** It does ZERO implementation or testing work. Its job:

1. Read specs and the tracker to understand the backlog
2. Create and manage a team (`TeamCreate`)
3. Create tasks from specs (`TaskCreate`)
4. Spawn subagents as teammates to do all real work
5. Assign tasks and manage dependencies
6. Monitor progress, unblock agents, and keep the backlog moving
7. Spawn a **critique agent** to review completed work
8. Keep working until the entire backlog is done

**The conductor never:**
- Edits source files
- Runs tests
- Starts REPLs
- Loads skills (skills are for implementers)
- Reads implementation code (except to understand blockers)

## Team Structure

### Implementer Agents
- Spawned via `Task` tool with `model: "opus"` and `subagent_type: "general-purpose"`
- Each implementer works on one spec or a small slice of work
- **Must load appropriate skills** before starting work (see Skills section below)
- **Should spawn their own sub-agents** (Explore for research, haiku for simple searches) to keep their context windows clean
- Should commit and push progress at checkpoints

### Critique Agent
- At least one dedicated critique agent must be running or spawned regularly
- Uses `subagent_type: "critique"` with `model: "opus"`
- Reviews completed specs for:
  - Correctness against the spec requirements
  - Code quality and adherence to project conventions
  - Test coverage completeness
  - Missed edge cases or regressions
  - Cross-spec consistency (naming, patterns, API shape)
- Gives **detailed, context-specific feedback** considering every angle
- Critique findings become new tasks or block completion of existing ones

### Explore Agents
- Use `model: "haiku"` for search/grep/read tasks
- Spawn liberally for codebase research

## Context Management

### Keep Context Small
- **Start new team members frequently.** Don't reuse an implementer across many specs — spawn a fresh one per spec or major task slice. This keeps context windows lean.
- Each agent should use < 50% of its context window
- When an agent finishes a task, let it complete (don't reassign more work to it)
- Prefer many short-lived agents over few long-lived ones

### REPL Management (CRITICAL)
- **Each agent owns its own REPL.** When an agent starts work that needs a REPL, it starts one. When the agent is done (or shutting down), it **must kill its own REPL** before exiting.
- Never kill another agent's REPL — that will stomp on their work
- Multiple REPLs on the same source tree are fine; Clojure handles this
- The conductor should remind implementers: "Kill your REPL when you're done"
- If the machine runs low on memory, the conductor should wind down agents rather than killing REPLs externally

### Parallelization Rules
- **Parallelize independent specs** that don't share files (e.g., two specs touching different namespaces)
- **Serialize specs that share source files** — concurrent edits to the same file will cause conflicts
- **Each agent runs its own REPL** — no conflicts, but each agent must kill its REPL on shutdown to avoid memory bloat
- Research/exploration agents can always run in parallel
- Critique can run in parallel with implementation (it reads, doesn't write)
- Check spec `Depends-on` fields — never start a spec before its dependencies are done

## Maintaining Context with CLAUDE.md Files

As agents work, they should **create and update `CLAUDE.md` files in subdirectories** to preserve decisions, patterns, and gotchas for future sessions and agents. These are compact memory files, not documentation — bullet points, not essays.

- Create them close to the code: e.g. `src/main/com/fulcrologic/rad/form/CLAUDE.md`
- Record non-obvious decisions, discovered conventions, and pitfalls
- Update them when things change — stale notes are worse than no notes
- Check for an existing `CLAUDE.md` in a directory before starting work there

## Spec Lifecycle (Team Version)

1. **Select**: Conductor picks next spec(s) from backlog based on dependencies and priority
2. **Create Task**: Conductor creates a task from the spec
3. **Assign**: Conductor spawns a fresh implementer and assigns the task
4. **Implement**: Implementer loads skills, reads the spec, works in slices, commits progress
5. **Signal Done**: Implementer marks task completed and reports back
6. **Critique**: Conductor assigns the critique agent to review the completed work
7. **Iterate**: If critique finds issues, conductor creates fix tasks and assigns them
8. **Complete**: Once critique passes, conductor updates spec status to `done` in TRACKER.md

## Spec File Format

```markdown
# Spec: Feature Name

**Status**: backlog|active|blocked|done
**Priority**: P0|P1|P2|P3
**Created**: YYYY-MM-DD
**Completed**: YYYY-MM-DD (if done)
**Owner**: conductor|AI
**Depends-on**: list of spec names this depends on

## Context
Problem statement

## Requirements
1. Numbered requirements

## Affected Modules
- `path/to/file.cljc` - Description

## Approach
High-level summary

## Open Questions
- Questions for human review

## Verification
1. [ ] Verification checklist
```

## Skills to Load (for Implementer Agents)

The conductor should tell each implementer which skills to load based on the task. Common mappings:

| Task Type | Skills to Load |
|-----------|---------------|
| Any .clj/.cljs/.cljc work | `clojure` (MANDATORY, load first) |
| Running code or tests | `clojure-repl` (MANDATORY) |
| Writing tests with fulcro-spec | `fulcro-spec-tdd` |
| Statechart work | `statechart` |
| Fulcro framework | `fulcro` |
| RAD patterns | `fulcro-rad` |
| UISM understanding (read-only reference) | `fulcro-uism` |
| Converting CLJS to CLJC | `clj-stubs` |
| Headless testing | `fulcro-headless` |
| Guardrails/schemas | `guardrails` |

## Conductor Prompt Template

When spawning an implementer, the conductor should include in the prompt:

```
You are an implementer agent on the fulcro-rad-statecharts team.

FIRST: Load these skills: [list skills]
THEN: Read the spec at docs/ai/specs/[spec-name].md
ALSO: Check for CLAUDE.md files in directories you'll work in

Your task: [description]

IMPORTANT:
- Commit and push progress at major checkpoints (on the current branch)
- When you're done, KILL YOUR REPL before exiting (don't leave orphan processes)
- Use sub-agents (Explore with haiku model) for codebase research
- Create/update CLAUDE.md files in directories where you make non-obvious decisions
- When done, report what you completed and any issues found
```

## Project Goal

Convert fulcro-rad from Fulcro UI State Machines (UISMs) to the fulcrologic statecharts library.
All routing should use statecharts routing (not Fulcro dynamic routing). Everything must be CLJC
for headless testing support. The public API should remain as similar as possible.
