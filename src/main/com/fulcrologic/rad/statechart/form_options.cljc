(ns com.fulcrologic.rad.statechart.form-options
  "Statechart-engine-specific form options. These keys are only relevant when using the statechart
  form engine (not the UISM engine). Users require this namespace with the alias `sfo/`:

  ```clojure
  (:require [com.fulcrologic.rad.statechart.form-options :as sfo])
  ```

  Shared form options (id, attributes, subforms, layout, etc.) still come from
  `com.fulcrologic.rad.form-options` (alias `fo/`) as usual.

  NOTE: These files must be CLJC to ensure the symbols are resolvable at *compile* time.
  No dynamic tricks please.")

(def triggers
  "Statechart-specific trigger callbacks for the form lifecycle. A map of lifecycle hooks,
  each called at specific points in the statechart session:

  * `:derive-fields` - A `(fn [props] new-props)` that can rewrite any of the props on the form
    (as a tree). This function is allowed to look into subforms, and even generate new members
    (though it must be careful to add form config if it does so). The `new-props` must be a tree
    of props that matches the correct shape of the form and is non-destructive to the form config
    and other non-field attributes on that tree.

  * `:on-change` - Called when an individual field changes. A
    `(fn [env data form-ident qualified-key old-value new-value])` that returns a vector of
    statechart operations (e.g. `fops/apply-action`, `fops/assoc-alias`). The `env` is the
    statechart expression env and `data` contains `:fulcro/state-map` and session data.
    Use `fops/apply-action` to modify state.

    NOTE: This signature differs from the UISM `fo/triggers` `:on-change` callback.

  * `:started` - A `(fn [env data form-ident])`. Called after the form statechart session has
    been initialized. Returns a vector of ops. New instances (create) will have a tempid.

  * `:saved` - A `(fn [env data form-ident])`. Called after a successful save. Returns a vector
    of ops.

  * `:save-failed` - A `(fn [env data form-ident])`. Called after a failed save. Returns a
    vector of ops.
  "
  :com.fulcrologic.rad.statechart.form-options/triggers)

(def statechart
  "Override the statechart definition that is used to control this form. Defaults to
  `form/form-statechart`. Can be either a statechart definition (the output of `statechart`)
  or a keyword referencing a pre-registered chart ID.

  When a keyword is given, the macro sets `sfro/statechart-id` on the component options
  instead of `sfro/statechart`."
  :com.fulcrologic.rad.statechart.form-options/statechart)

(def statechart-id
  "A keyword referencing a pre-registered statechart ID to use as this form's controller.
  Alternative to providing an inline `sfo/statechart` definition.

  When the macro processes this option, it stores the keyword under `sfro/statechart-id`
  in the component options."
  :com.fulcrologic.rad.statechart.form-options/statechart-id)

(def on-started
  "A `(fn [env data event-name event-data] ops-vec)` called when the form statechart session
  enters its initial state. Returns a vector of statechart operations.

  New instances (create) will have a tempid for the form's id attribute."
  :com.fulcrologic.rad.statechart.form-options/on-started)

(def on-saved
  "A `(fn [env data event-name event-data] ops-vec)` called after a successful save.
  Returns a vector of statechart operations."
  :com.fulcrologic.rad.statechart.form-options/on-saved)

(def on-save-failed
  "A `(fn [env data event-name event-data] ops-vec)` called after a save failure.
  Returns a vector of statechart operations."
  :com.fulcrologic.rad.statechart.form-options/on-save-failed)
