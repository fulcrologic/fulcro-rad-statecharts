# fulcro-rad-statecharts Workflow

## The Conductor Pattern

The main conversation orchestrates work; agents do the heavy lifting.

**Context Budget Rules:**
- 5+ files to read -> spawn Explore agent
- 15+ turns deep -> spawn fresh agent
- Each agent should use < 50% context window
- Load primary skill first when spawning agents

## Spec Lifecycle

1. **Create**: Write spec in `docs/ai/specs/` with status `backlog`
2. **Activate**: Set status to `active`, assign owner
3. **Plan** (optional): For complex specs, create `plans/spec-name-plan.md`
4. **Implement**: Work in slices, update progress log
5. **Verify**: Check all verification points
6. **Complete**: Set status to `done`, add completion date

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

## Task Decomposition

- One concern per agent
- Use Teams for 4+ parallel tasks
- Use solo Task agents for 1-3 tasks

## Key Skills to Load

- `clojure` - Any .clj/.cljs/.cljc work
- `clojure-repl` - Running code or tests (MANDATORY)
- `fulcro-spec-tdd` - Writing tests with fulcro-spec
- `statechart` - Working with statecharts library
- `fulcro` - Fulcro framework
- `fulcro-rad` - RAD patterns

## Project Goal

Convert fulcro-rad from Fulcro UI State Machines (UISMs) to the fulcrologic statecharts library.
All routing should use statecharts routing (not Fulcro dynamic routing). Everything must be CLJC
for headless testing support. The public API should remain as similar as possible.
