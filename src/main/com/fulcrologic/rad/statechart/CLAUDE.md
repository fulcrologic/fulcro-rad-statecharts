# Statechart Engine Notes

## View Mode (decision 2026-10-31: RESTORE)

- The old `view-mode?` was removed because it was dead code: nothing ever set `:action` to `"view"`.
- The human has now decided view mode comes back for parity: `view!`, `view-mode?`, and `read-only?` honoring view mode, routable to a view-only form. This is spec `docs/ai/specs/rad-parity/rp-08-form-view-mode.md`.
- Implement it as real engine behavior that something actually sets. Don't resurrect the old dead constants as-is.

## Route Denial (decision 2026-10-31)

- Drop `fo/confirm` and `fo/silent-abandon?`. Route denial and confirmation are handled by a routing parallel region (scr `:routing-info`, `scr/route-denied?`, `force-continue-routing!`, `abandon-route-change!`).
- The old keys should produce a compile-time warning.
- Plugins get thin replacement fns for `continue-abandoned-route!`/`clear-route-denied!`.
- Spec: `rp-09-route-denial-region`.

## Delete

- `form/delete!` must send the legacy mutation symbol `com.fulcrologic.rad.form/delete-entity`, which is what upstream `form/resolvers` registers on servers. It must also work in CLJ.
- Spec: `rp-02`.
