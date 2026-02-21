(ns com.fulcrologic.rad.report-chart
  "Standard report statechart. Replaces the UISM `report-machine` from report.cljc.
   Handles data loading, filtering, sorting, pagination, and row selection.

   All report data is stored in Fulcro state via aliases. Session-internal data
   (cache timestamps) uses `ops/assign`."
  (:require
   [com.fulcrologic.rad.report-expressions :as rexpr]
   [com.fulcrologic.statecharts.chart :refer [statechart]]
   [com.fulcrologic.statecharts.convenience :refer [on handle]]
   [com.fulcrologic.statecharts.data-model.operations :as ops]
   [com.fulcrologic.statecharts.elements :refer [state transition on-entry script data-model]]
   [com.fulcrologic.statecharts.integration.fulcro.operations :as fops]))

(def report-statechart
  "Standard report statechart definition. Supports data loading, client-side filtering,
   sorting, pagination, row selection, and cache-aware resume."
  (statechart {:id :com.fulcrologic.rad.report-chart/report-chart :initial :state/initializing}

              (data-model {:expr (fn [_ _]
                                   {:last-load-time      nil
                                    :raw-items-in-table  nil})})

              (state {:id :state/initializing}
                     (on-entry {}
                               (script {:expr rexpr/initialize-params-expr}))
                     (transition {:cond rexpr/should-run-on-mount? :target :state/loading})
                     (transition {:target :state/ready}))

              (state {:id :state/loading}
                     (on-entry {}
                               (script {:expr rexpr/start-load-expr}))
                     (on :event/loaded :state/processing)
                     (on :event/failed :state/ready))

              (state {:id :state/processing}
                     (on-entry {}
                               (script {:expr rexpr/process-loaded-data-expr}))
                     (transition {:target :state/ready}))

              (state {:id :state/ready}
      ;; Pagination
                     (handle :event/goto-page rexpr/goto-page-expr)
                     (handle :event/next-page rexpr/next-page-expr)
                     (handle :event/prior-page rexpr/prior-page-expr)

      ;; Sort -> intermediate observable state
                     (on :event/sort :state/sorting)

      ;; Filter -> intermediate observable state
                     (on :event/filter :state/filtering)

      ;; Row selection
                     (handle :event/select-row rexpr/select-row-expr)

      ;; Parameter management
                     (handle :event/set-ui-parameters rexpr/set-params-expr)

      ;; Reload
                     (on :event/run :state/loading)

      ;; Resume: cache expired -> reload, otherwise re-filter from cache
                     (transition {:event :event/resume :cond rexpr/cache-expired? :target :state/loading}
                                 (script {:expr rexpr/reinitialize-params-expr}))
                     (transition {:event :event/resume}
                                 (script {:expr rexpr/resume-from-cache-expr}))

      ;; Clear sort
                     (handle :event/clear-sort rexpr/clear-sort-expr))

    ;; Observable intermediate state for sorting
              (state {:id :state/sorting}
                     (on-entry {}
                               (script {:expr (fn [_env _data _event-name _event-data]
                                                [(fops/assoc-alias :busy? true)])}))
                     (transition {:target :state/ready}
                                 (script {:expr rexpr/do-sort-and-clear-busy-expr})))

    ;; Observable intermediate state for filtering
              (state {:id :state/filtering}
                     (on-entry {}
                               (script {:expr (fn [_env _data _event-name _event-data]
                                                [(fops/assoc-alias :busy? true)])}))
                     (transition {:target :state/ready}
                                 (script {:expr rexpr/do-filter-and-clear-busy-expr})))))
