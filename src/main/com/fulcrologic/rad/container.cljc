(ns com.fulcrologic.rad.container
  "A RAD container is a component for grouping together reports.
   They allow you pull up controls to the container level to coordinate reports so that one set of controls is shared among them.

   Reports may keep controls local to themselves by adding `:local?` to a control; otherwise, all of the controls
   from all nested reports will be pulled up to the container level and will be unified when their names match. The
   container itself will then be responsible for asking the children to refresh (though technically you can add a local
   control to any child to make such a control available for a particular child)."
  #?(:cljs
     (:require-macros com.fulcrologic.rad.container))
  (:require
   [com.fulcrologic.fulcro.components :as comp]
   [com.fulcrologic.fulcro.application :as app]
   [com.fulcrologic.fulcro.mutations :refer [defmutation]]
   [com.fulcrologic.fulcro.algorithms.merge :as merge]
   [com.fulcrologic.rad.container-chart :as container-chart]
   [com.fulcrologic.rad.container-expressions :as cexpr]
   [com.fulcrologic.rad.report :as report]
   [com.fulcrologic.rad.control :as control :refer [Control]]
   [com.fulcrologic.rad.options-util :as opts :refer [?! debounce]]
   [com.fulcrologic.rad.sc.session :as sc.session]
   [com.fulcrologic.statecharts.integration.fulcro :as scf]
   #?@(:clj
       [[cljs.analyzer :as ana]])
   [com.fulcrologic.fulcro.data-fetch :as df]
   [taoensso.timbre :as log]
   [taoensso.encore :as enc]
   [clojure.spec.alpha :as s]))

(defn id-child-pairs
  "Returns a sequence of [id cls] pairs for each child (i.e. the seq of the children setting)"
  [container]
  (seq (comp/component-options container ::children)))

(defn child-classes
  "Returns a de-duped set of classes of the children of the given instance/class (using it's query)"
  [container]
  (set (vals (comp/component-options container ::children))))

(defn broadcast-to-children!
  "Sends `event` to all child report statecharts of the given `container-class`."
  [app container-class event & [event-data]]
  (doseq [[id child-class] (id-child-pairs container-class)]
    (scf/send! app (cexpr/child-report-session-id child-class id) event (or event-data {}))))

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
    (scf/register-statechart! app ::container-chart container-chart/container-statechart)
    (if (not running?)
      (scf/start! app
                  {:machine    ::container-chart
                   :session-id session-id
                   :data       {:fulcro/actors  {:actor/container (scf/actor container-class container-ident)}
                                :fulcro/aliases {:parameters [:actor/container :ui/parameters]}
                                :route-params   (:route-params options)}})
      (scf/send! app session-id :event/resume (merge options {:route-params (:route-params options)})))))

(defn container-will-enter
  "Route lifecycle handler for containers. Starts or resumes the container statechart."
  [app route-params container-class]
  (start-container! app container-class {:route-params route-params}))

#?(:clj
   (defmacro defsc-container
     "Define a container, which is a specialized component that holds and coordinates more than one report under
      a common set of controls.

      If you want this to be a route target, then you must add `:route-segment`.

      You should at least specify a ::children option.

      If you elide the body, one will be generated for you."
     [sym arglist & args]
     (let [this-sym (first arglist)
           options  (first args)
           options  (opts/macro-optimize-options &env options #{::field-formatters ::column-headings ::form-links} {})
           {::control/keys [controls]
            ::keys         [children route] :as options} options]
       (when-not (map? children)
         (throw (ana/error &env (str "defsc-container " sym " has no declared children."))))
       (when (and route (not (string? route)))
         (throw (ana/error &env (str "defsc-container " sym " ::route, when defined, must be a string."))))
       (let [query-expr (into [:ui/parameters
                               {:ui/controls `(comp/get-query Control)}
                               [df/marker-table '(quote _)]]
                              (map (fn [[id child-sym]] `{~id (comp/get-query ~child-sym)}) children))
             query      (list 'fn '[] query-expr)
             nspc       (if (enc/compiling-cljs?) (-> &env :ns :name str) (name (ns-name *ns*)))
             fqkw       (keyword (str nspc) (name sym))
             options    (cond-> (assoc options
                                       :query query
                                       :initial-state (list 'fn '[_]
                                                            `(into {:ui/parameters {}
                                                                    :ui/controls   (mapv #(select-keys % #{::control/id}) (control/control-map->controls ~controls))}
                                                                   (map (fn [[id# c#]] [id# (comp/get-initial-state c# {::report/id id#})]) ~children)))
                                       :ident (list 'fn [] [::id fqkw]))
                          (string? route) (assoc
                                           :route-segment [route]
                                           :will-enter `(fn [app# route-params#] (container-will-enter app# route-params# ~sym))))
             body       (if (seq (rest args))
                          (rest args)
                          [`(render-layout ~this-sym)])]
         `(comp/defsc ~sym ~arglist ~options ~@body)))))
