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

## Delete (rp-02, done)

- `form/delete!` transacts the CLJC `m/defmutation` `com.fulcrologic.rad.statechart.form/delete-entity`. Its remote sends the legacy symbol `com.fulcrologic.rad.form/delete-entity` (`m/with-server-side-mutation`), which upstream `form/resolvers` registers on servers. Works in CLJ.
- It dispatches locally under the lib's own symbol, so upstream's CLJS `defmutation` for the legacy symbol is never involved, loaded or not.
- Removal is pessimistic (upstream parity): `ok-action` removes the entity with `fns/remove-entity` once the server confirms. A failed delete leaves it.
- `pathom2-server-delete-entity-mutation` keeps existing, with `::pc/sym` = the legacy symbol. Servers should register only one of it and upstream `form/resolvers`.

## Form Rendering Without `:default`s (rp-02)

- Upstream `com.fulcrologic.rad.form` registers `fr/render-form :default` and `fr/render-field :default` (map-registry renderers). It can't be kept off a full-stack JVM, and the last ns loaded wins any `:default`.
- So the lib registers **no** `:default` on any upstream `fr/*` multimethod (same policy as `report.cljc`).
- Mechanism: `specific-render-method` returns the method a multimethod would dispatch to, or nil when that is its `:default`. It reads the dispatch fn from the `MultiFn` (`.-dispatchFn` / `-dispatch-fn`), so dispatch stays identical to upstream's.
  - `render-layout` (default form body): specific `fr/render-form` method if any, else `(render-element env :form-container)`.
  - `render-field`/`render-input`/`attr->renderer`: specific `fr/render-field` method if any, else `default-render-field` (logs, renders nil).
- Rejected: re-asserting `:default` at plugin install time. It still depends on load and reload order.
- Plugin-author rule (also in the `form.cljc` ns docstring):
  - Register: `form/render-element` (`[:form-container style]`, `[:form-body-container style]`, `[:ref-container style]`), and specific `fr/*` dispatch values (`fr/render-field [type style]`, `fr/render-form [id-key style]`).
  - Never register `:default` on upstream `fr/*` multimethods. Call `form/render-field`, not `fr/render-field`.
- Mixed builds: legacy UISM forms keep upstream's `:default`s, so their fields render whichever ns loads last.
- Test: `render_dispatch_spec` re-loads the two namespaces in both orders and renders a statechart form (headless) and a legacy form (upstream map registry).

## Validation (rp-02, GitHub #30)

- `convert-options` installs `default-validator` before user options are merged, so a user `fo/validator` wins. It is upstream's `(attr/make-attribute-validator (form-and-subform-attributes Form) true)`, built lazily: `convert-options` only has a registry key (or, from `form`, a get-class fn). It resolves the class on first call and caches the result per class.
- `valid?`/`invalid?`/`invalid-attribute-value?` call validators through the private `validity`: `->arity-tolerant` drops extra args, and (CLJ) missing args are nil, CLJS-style. So `(fn [props])` and `(fn [form field])` both work.
- Because every `defsc-form` has a validator now, the save guard and field-level `invalid-attribute-value?` agree (subform fields use the master form's validator, which covers subform attributes).
- Gotcha: undo on a *created* form clears its default values, so a required field becomes invalid. Fulcro's `fs/add-form-config` gives a tempid entity an empty pristine state (same upstream).

## Save Errors (rp-02, GitHub #29)

- `scf/mutation-result` is already the mutation's return value (`:body vals first`). Read `::form/errors` from it directly, never `(get result save-mutation)`.
- Errors are cleared by `prepare-save-expr` (start of every save) and by undo. A refused save sets them again.
- Server-side detection: RAD's `default-remote-error?` (from `fulcro-rad-app`) treats a body with `com.fulcrologic.rad.form/save-form` `::form/errors` as an error. A custom `fo/save-mutation` needs its own `:remote-error?`.
