(ns com.fulcrologic.rad.statechart.report-options
  "Statechart-engine-specific report options. These keys are only relevant when using the
  statechart report engine (not the UISM engine). Users require this namespace with the alias `sro/`:

  ```clojure
  (:require [com.fulcrologic.rad.statechart.report-options :as sro])
  ```

  Shared report options (row-pk, columns, source-attribute, controls, etc.) still come from
  `com.fulcrologic.rad.report-options` (alias `ro/`) as usual.

  NOTE: These files must be CLJC to ensure the symbols are resolvable at *compile* time.
  No dynamic tricks please.")

(def triggers
  "Statechart-specific trigger callbacks for the report lifecycle. A map of lifecycle hooks
  called at specific points in the statechart session.

  Each callback follows the statechart expression signature, returning a vector of ops:

  * `:on-change` - Called when a control or parameter changes. A
    `(fn [env data report-ident qualified-key old-value new-value])` that returns a vector of
    statechart operations.

    NOTE: This signature differs from the UISM `ro/triggers` callback signature.
  "
  :com.fulcrologic.rad.statechart.report-options/triggers)

(def statechart
  "Override the statechart definition that is used to control this report. Defaults to
  `report/report-statechart`. Can be either a statechart definition (the output of `statechart`)
  or a keyword referencing a pre-registered chart ID.

  When a keyword is given, the macro sets `sfro/statechart-id` on the component options
  instead of `sfro/statechart`."
  :com.fulcrologic.rad.statechart.report-options/statechart)

(def statechart-id
  "A keyword referencing a pre-registered statechart ID to use as this report's controller.
  Alternative to providing an inline `sro/statechart` definition.

  When the macro processes this option, it stores the keyword under `sfro/statechart-id`
  in the component options."
  :com.fulcrologic.rad.statechart.report-options/statechart-id)

(def on-loaded
  "A `(fn [env data event-name event-data] ops-vec)` called when the report finishes loading
  its data. Returns a vector of statechart operations."
  :com.fulcrologic.rad.statechart.report-options/on-loaded)
