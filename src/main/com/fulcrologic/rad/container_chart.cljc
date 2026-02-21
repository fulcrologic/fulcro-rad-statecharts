(ns com.fulcrologic.rad.container-chart
  "Container statechart. Replaces the UISM `container-machine` from container.cljc.
   Coordinates child report statecharts under a shared set of controls.

   States: initializing -> ready
   Events: :event/run (broadcast to children), :event/resume, :event/unmount"
  (:require
   [com.fulcrologic.rad.container-expressions :as cexpr]
   [com.fulcrologic.statecharts.chart :refer [statechart]]
   [com.fulcrologic.statecharts.convenience :refer [on handle]]
   [com.fulcrologic.statecharts.elements :refer [state transition on-entry on-exit script]]))

(def container-statechart
  "Container statechart definition. Initializes and coordinates child report statecharts."
  (statechart {:id ::container-chart :initial :state/initializing}

              (state {:id :state/initializing}
                     (on-entry {}
                               (script {:expr cexpr/initialize-params-expr}))
                     (transition {:target :state/ready}))

              (state {:id :state/ready}
                     ;; Run all children
                     (handle :event/run cexpr/run-children-expr)

                     ;; Resume: re-initialize params and resume children
                     (handle :event/resume cexpr/resume-children-expr)

                     ;; Cleanup on exit: send unmount to all children
                     (on-exit {}
                              (script {:expr cexpr/unmount-children-expr})))))
