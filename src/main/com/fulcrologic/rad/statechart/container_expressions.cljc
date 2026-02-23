(ns com.fulcrologic.rad.statechart.container-expressions
  "Expression functions for the container statechart. All expressions follow the 4-arg
   convention: `(fn [env data event-name event-data] ...)` and return a vector of operations
   or nil."
  (:require
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.rad.options-util :refer [?!]]
    [com.fulcrologic.rad.statechart.control :as control]
    [com.fulcrologic.rad.statechart.report :as report]
    [com.fulcrologic.rad.statechart.session :as sc.session]
    [com.fulcrologic.statecharts.integration.fulcro :as scf]
    [com.fulcrologic.statecharts.integration.fulcro.operations :as fops]))

;; ---- Helpers ----

(defn container-class
  "Returns the container component class from the statechart data."
  [data]
  (scf/resolve-actor-class data :actor/container))

(defn id-child-pairs
  "Returns a sequence of [id cls] pairs for each child of the container."
  [container-class-or-instance]
  (seq (comp/component-options container-class-or-instance :com.fulcrologic.rad.statechart.container/children)))

(defn child-report-session-id
  "Returns the statechart session ID for a child report given its class and container-assigned id."
  [child-class id]
  (sc.session/ident->session-id (comp/get-ident child-class {:com.fulcrologic.rad.statechart.report/id id})))

;; ---- Expression Functions ----

(defn initialize-params-expr
  "Initializes container parameters from route params or defaults. Also merges children's
   initial state into Fulcro state and starts child report statecharts."
  [env data _event-name _event-data]
  (let [app             (:fulcro/app env)
        ContainerClass  (container-class data)
        container-ident (get-in data [:fulcro/actors :actor/container :ident])
        route-params    (:route-params data)
        controls        (control/component-controls ContainerClass)
        children        (id-child-pairs ContainerClass)
        ;; Build ops for merging children and initializing parameters
        merge-op        (fops/apply-action
                          (fn [state-map]
                            (reduce
                              (fn [s [id cls]]
                                (let [k    (comp/class->registry-key cls)
                                      path (conj container-ident k)]
                                  (merge/merge-component
                                    s cls
                                    (or (comp/get-initial-state cls {:com.fulcrologic.rad.statechart.report/id id}) {})
                                    :replace path)))
                              state-map
                              children)))
        param-ops       (reduce-kv
                          (fn [ops control-key {:keys [default-value]}]
                            (let [v (cond
                                      (contains? route-params control-key) (get route-params control-key)
                                      (not (nil? default-value)) (?! default-value app))]
                              (if-not (nil? v)
                                (conj ops (fops/apply-action assoc-in
                                            [::control/id control-key ::control/value] v))
                                ops)))
                          []
                          controls)]
    ;; Side effect: start child report statecharts
    (doseq [[id c] children]
      (report/start-report! app c {:com.fulcrologic.rad.statechart.report/id                     id
                                   :com.fulcrologic.rad.statechart.report/externally-controlled? true
                                   :route-params                                                 route-params}))
    (into [merge-op] param-ops)))

(defn broadcast-to-children!
  "Sends an event to all child report statecharts of the container."
  [env data event & [event-data]]
  (let [app            (:fulcro/app env)
        ContainerClass (container-class data)]
    (doseq [[id child-class] (id-child-pairs ContainerClass)]
      (scf/send! app (child-report-session-id child-class id) event (or event-data {})))))

(defn run-children-expr
  "Broadcasts :event/run to all child report statecharts."
  [env data _event-name _event-data]
  (broadcast-to-children! env data :event/run)
  nil)

(defn resume-children-expr
  "Broadcasts :event/resume to all children and re-initializes container parameters."
  [env data _event-name event-data]
  (let [app            (:fulcro/app env)
        ContainerClass (container-class data)
        route-params   (or (:route-params event-data) (:route-params data))
        controls       (control/component-controls ContainerClass)]
    ;; Side effect: resume children
    (broadcast-to-children! env data :event/resume {:route-params route-params})
    ;; Return ops: re-initialize parameters
    (reduce-kv
      (fn [ops control-key {:keys [default-value]}]
        (let [v (cond
                  (contains? route-params control-key) (get route-params control-key)
                  (not (nil? default-value)) (?! default-value app))]
          (if-not (nil? v)
            (conj ops (fops/apply-action assoc-in
                        [::control/id control-key ::control/value] v))
            ops)))
      []
      controls)))

(defn unmount-children-expr
  "Sends :event/unmount to all child report statecharts for cleanup."
  [env data _event-name _event-data]
  (broadcast-to-children! env data :event/unmount)
  nil)
