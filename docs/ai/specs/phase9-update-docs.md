# Spec: Update Documentation for Consolidation

**Status**: backlog
**Priority**: P2
**Created**: 2026-02-22
**Owner**: AI
**Depends-on**: phase9-delete-routing-update-deps
**Phase**: 9 — Namespace Consolidation

## Context

After namespace consolidation, documentation references to deleted namespaces need updating.

## Requirements

1. Update `src/main/com/fulcrologic/rad/CLAUDE.md`:
   - Remove architecture sections referencing `form_chart.cljc`, `form_expressions.cljc`, `form_machines.cljc`, `report_chart.cljc`, `report_expressions.cljc`, `container_chart.cljc`, `container_expressions.cljc`
   - Update architecture descriptions to reflect consolidated structure
   - Remove routing delegation section (routing.cljc is gone)
   - Keep design decisions and bug fix notes (they're still relevant)

2. Update `README.md` if it references deleted namespaces

3. Update any `CLAUDE.md` files in subdirectories that reference deleted namespaces

## Affected Files

- `src/main/com/fulcrologic/rad/CLAUDE.md`
- `README.md` (if applicable)
- Any subdirectory `CLAUDE.md` files

## Verification

1. [ ] No documentation references deleted namespace files
2. [ ] Architecture descriptions match actual file structure
