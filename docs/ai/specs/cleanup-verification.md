# cleanup-verification

**Status:** Backlog | **Priority:** P0 | **Created:** 2026-02-20 | **Depends-on:** headless-plugin

## Summary

Verify the cleanup phase is complete: no dead references, everything compiles, existing tests pass.

## Steps

1. **Dead reference grep:** Search all remaining files for references to deleted namespaces
2. **Compile check:** Compile CLJ and CLJS to verify no missing namespace errors
3. **Test run:** Run all remaining tests (remove test files for deleted modules first)
4. **Multimethod audit:** Verify headless plugin registers defaults for all rendering multimethods defined in form_render, report_render, and control

## Acceptance Criteria

- Zero references to deleted namespaces
- Clean CLJ compilation
- Clean CLJS compilation
- All remaining tests pass
- Headless plugin covers all rendering multimethods
