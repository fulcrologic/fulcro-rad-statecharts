# Spec: Final Verification — Full Suite Health Check

**Status**: backlog
**Priority**: P0
**Created**: 2026-02-22
**Owner**: AI
**Depends-on**: phase9-update-tests, phase9-update-demo
**Phase**: 9 — Namespace Consolidation

## Context

After all consolidation work is complete, a comprehensive verification pass ensures nothing was missed and the library is in a shippable state.

## Requirements

1. **No stale references**: grep entire codebase for deleted namespace names (`form_chart`, `form_expressions`, `form_machines`, `report_chart`, `report_expressions`, `container_chart`, `container_expressions`, `statechart.routing`) — 0 hits in requires
2. **Full CLJ test suite**: all tests pass, 0 failures, 0 errors
3. **CLJS compilation**: 0 warnings
4. **No dead files**: satellite files are all deleted
5. **No circular dependencies**: form.cljc uses `declare` forward refs, no `requiring-resolve` or registry patterns
6. **Public API intact**: all functions that were public before consolidation are still public and accessible
7. **Demo compiles and runs**: both CLJ and CLJS
8. **pom.xml and deps.edn consistent**

## Verification

1. [ ] `grep -r` for deleted namespaces: 0 hits in requires
2. [ ] Test suite: 77+ tests, 366+ assertions, 0 failures
3. [ ] CLJS: 0 warnings
4. [ ] No satellite files exist
5. [ ] Demo compiles
6. [ ] pom.xml matches deps.edn
