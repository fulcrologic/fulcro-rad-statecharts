(ns com.fulcrologic.rad.statechart.container
  "A RAD container is a component for grouping together reports.
   They allow you pull up controls to the container level to coordinate reports so that one set of controls is shared among them.

   Reports may keep controls local to themselves by adding `:local?` to a control; otherwise, all of the controls
   from all nested reports will be pulled up to the container level and will be unified when their names match. The
   container itself will then be responsible for asking the children to refresh (though technically you can add a local
   control to any child to make such a control available for a particular child)."
  #?(:cljs
     (:require-macros com.fulcrologic.rad.statechart.container))
  (:require
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.mutations :refer [defmutation]]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.rad.statechart.report :as report]
    [com.fulcrologic.rad.statechart.control :as control :refer [Control]]
    [com.fulcrologic.rad.options-util :as opts :refer [?! debounce]]
    [com.fulcrologic.rad.statechart.session :as sc.session]
    [com.fulcrologic.statecharts.integration.fulcro :as scf]
    [com.fulcrologic.statecharts.integration.fulcro.operations :as fops]
    [com.fulcrologic.statecharts.chart :refer [statechart]]
    [com.fulcrologic.statecharts.convenience :refer [handle]]
    [com.fulcrologic.statecharts.elements :refer [on-entry on-exit script state transition]]
    #?@(:clj
        [[cljs.analyzer :as ana]])
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.statecharts.integration.fulcro.routing-options :as sfro]
    [taoensso.timbre :as log]
    [taoensso.encore :as enc]
    [com.fulcrologic.rad.statechart.container-options :as co]
    [clojure.spec.alpha :as s]))

;; ===== Helpers =====

(defn id-child-pairs
  "Returns a sequence of [id cls] pairs for each child (i.e. the seq of the children setting)"
  [container]
  (seq (comp/component-options container ::children)))

(defn child-classes
  "Returns a de-duped set of classes of the children of the given instance/class (using it's query)"
  [container]
  (set (vals (comp/component-options container ::children))))

(defn child-report-session-id
  "Returns the statechart session ID for a child report given its class and container-assigned `id`."
  [child-class id]
  (sc.session/ident->session-id (comp/get-ident child-class {::report/id id})))

(defn broadcast-to-children!
  "Sends `event` to all child report statecharts of the given `container-class`."
  [app container-class event & [event-data]]
  (doseq [[id child-class] (id-child-pairs container-class)]
    (scf/send! app (child-report-session-id child-class id) event (or event-data {}))))

;; ===== Expression Helpers =====

(defn container-class
  "Returns the container component class from the statechart `data`."
  [data]
  (scf/resolve-actor-class data :actor/container))

(defn- broadcast-children!
  "Internal expression helper: sends `event` to all child report statecharts using env/data context."
  [env data event & [event-data]]
  (broadcast-to-children! (:fulcro/app env) (container-class data) event event-data))

;; ===== Expression Functions =====

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
        merge-op        (fops/apply-action
                          (fn [state-map]
                            (reduce
                              (fn [s [id cls]]
                                (let [k    (comp/class->registry-key cls)
                                      path (conj container-ident k)]
                                  (merge/merge-component
                                    s cls
                                    (or (comp/get-initial-state cls {::report/id id}) {})
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
    (doseq [[id c] children]
      (report/start-report! app c {::report/id                     id
                                   ::report/externally-controlled? true
                                   :route-params                   route-params}))
    (into [merge-op] param-ops)))

(defn run-children-expr
  "Broadcasts :event/run to all child report statecharts."
  [env data _event-name _event-data]
  (broadcast-children! env data :event/run)
  nil)

(defn resume-children-expr
  "Broadcasts :event/resume to all children and re-initializes container parameters."
  [env data _event-name event-data]
  (let [app            (:fulcro/app env)
        ContainerClass (container-class data)
        route-params   (or (:route-params event-data) (:route-params data))
        controls       (control/component-controls ContainerClass)]
    (broadcast-children! env data :event/resume {:route-params route-params})
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
  (broadcast-children! env data :event/unmount)
  nil)

;; ===== Container Statechart =====

(def container-statechart
  "Container statechart definition. Initializes and coordinates child report statecharts."
  (statechart {:id ::container-chart :initial :state/initializing}

    (state {:id :state/initializing}
      (on-entry {}
        (script {:expr initialize-params-expr}))
      (transition {:target :state/ready}))

    (state {:id :state/ready}
      (handle :event/run run-children-expr)
      (handle :event/resume resume-children-expr)
      (on-exit {}
        (script {:expr unmount-children-expr})))))

;; ===== Public API =====

(defn render-layout
  "Auto-render the content of a container. This is the automatic body of a container. If you supply no render body
   to a container, this is what it will hold. Configurable through component options via `::container/layout-style`.  You can also do custom rendering
   in the container, and call this to embed the generated UI.

   NOTE: Container rendering plugins should provide a `defmethod` for the appropriate multimethod
   instead of using the old `install-layout!` approach."
  [container-instance]
  (log/error "No container layout renderer installed for style"
    (or (some-> container-instance comp/component-options ::layout-style) :default))
  nil)

(defn start-container!
  "Starts the container statechart. Initializes parameters, merges children, and starts
   child report statecharts."
  [app container-class options]
  (log/info "Starting container!")
  (let [container-ident (comp/get-ident container-class {})
        session-id      (sc.session/ident->session-id container-ident)
        running?        (seq (scf/current-configuration app session-id))]
    (scf/register-statechart! app ::container-chart container-statechart)
    (if (not running?)
      (scf/start! app
        {:machine    ::container-chart
         :session-id session-id
         :data       {:fulcro/actors  {:actor/container (scf/actor container-class container-ident)}
                      :fulcro/aliases {:parameters [:actor/container :ui/parameters]}
                      :route-params   (:route-params options)}})
      (scf/send! app session-id :event/resume (merge options {:route-params (:route-params options)})))))

(defn container-will-enter
  "DEPRECATED: Routing lifecycle is now managed by statecharts routing via sfro options.
   This function should not be called. Use `sfro/initialize` and `sfro/statechart` on your container instead."
  [_app _route-params _container-class]
  (log/error "container-will-enter is removed. Routing lifecycle is managed by statecharts routing. See sfro/initialize.")
  (throw (ex-info "container-will-enter is removed. Use statecharts routing (sfro options) instead." {})))

;; ===== Macro =====

#?(:clj
   (defmacro defsc-container
     "Define a container, which is a specialized component that holds and coordinates more than one report under
      a common set of controls.

      To make this a route target, include it in your routing statechart definition.

      You should at least specify a ::children option.

      If you elide the body, one will be generated for you."
     [sym arglist & args]
     (let [this-sym (first arglist)
           options  (first args)
           options  (opts/macro-optimize-options &env options #{::field-formatters ::column-headings ::form-links} {})
           {::control/keys [controls] :as options} options
           children (get options co/children)
           route    (get options co/route)]
       (when-not (map? children)
         (throw (ana/error &env (str "defsc-container " sym " has no declared children."))))
       (when (and route (not (string? route)))
         (throw (ana/error &env (str "defsc-container " sym " ::route, when defined, must be a string."))))
       (when (contains? options :will-enter)
         (log/warn "defsc-container" sym ":will-enter is ignored. Routing lifecycle is managed by statecharts routing."))
       (let [query-expr      (into [:ui/parameters
                                    {:ui/controls `(comp/get-query Control)}
                                    [df/marker-table '(quote _)]]
                               (map (fn [[id child-sym]] `{~id (comp/get-query ~child-sym)}) children))
             query           (list 'fn '[] query-expr)
             nspc            (if (enc/compiling-cljs?) (-> &env :ns :name str) (name (ns-name *ns*)))
             fqkw            (keyword (str nspc) (name sym))
             user-statechart (::statechart options)
             options         (cond-> (assoc options
                                       :query query
                                       :initial-state (list 'fn '[_]
                                                        `(into {:ui/parameters {}
                                                                :ui/controls   (mapv #(select-keys % #{::control/id}) (control/control-map->controls ~controls))}
                                                           (map (fn [[id# c#]] [id# (comp/get-initial-state c# {::report/id id#})]) ~children)))
                                       :ident (list 'fn [] [::id fqkw])
                                       sfro/initialize :once)
                               (keyword? user-statechart) (assoc sfro/statechart-id user-statechart)
                               (not (keyword? user-statechart)) (assoc sfro/statechart (or user-statechart `container-statechart)))
             body            (if (seq (rest args))
                               (rest args)
                               [`(render-layout ~this-sym)])]
         `(comp/defsc ~sym ~arglist ~options ~@body)))))
