(ns com.fulcrologic.rad.report
  "Support for generated reports. Report rendering is pluggable, so reports can be quite varied. The general
  definition of a report is a component that loads data and displays it, possibly paginates, sorts and
  filters it, but for which interactions are done via custom mutations (disable, delete, sort) or reloads.

  Reports can customize their layout via plugins, and the layout can then allow futher nested customization of element
  render. For example, it is trivial to create a layout renderer that is some kind of graph, and then use loaded data
  as the input for that display.

  Customizing the report's statechart and possibly wrapping it with more complex layout controls makes it possible
  to create UI dashboards and much more complex application features.
  "
  #?(:cljs (:require-macros com.fulcrologic.rad.report))
  (:require
   #?@(:clj
       [[clojure.pprint :refer [pprint]]
        [cljs.analyzer :as ana]])
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [com.fulcrologic.fulcro-i18n.i18n :refer [tr]]
   [com.fulcrologic.fulcro.application :as app]
   [com.fulcrologic.fulcro.components :as comp]
   [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
   [com.fulcrologic.fulcro.data-fetch :as df]
   [com.fulcrologic.fulcro.raw.components :as rc]
   [com.fulcrologic.fulcro.algorithms.lambda :as lambda]
   [com.fulcrologic.fulcro.algorithms.merge :as merge]
   [com.fulcrologic.rad.attributes :as attr]
   [com.fulcrologic.rad.attributes-options :as ao]
   [com.fulcrologic.rad.control :as control :refer [Control]]
   [com.fulcrologic.rad.form :as form]
   [com.fulcrologic.rad.options-util :as opts :refer [?! debounce]]
   [com.fulcrologic.rad.report-options :as ro]
   [com.fulcrologic.rad.report-render :as rr]
   [com.fulcrologic.rad.type-support.date-time :as dt]
   [com.fulcrologic.rad.type-support.decimal :as math]
   [com.fulcrologic.rad.picker-options :as picker-options]
   [com.fulcrologic.rad.report-chart :as report-chart]
   [com.fulcrologic.rad.sc.session :as sc.session]
   [com.fulcrologic.statecharts.integration.fulcro :as scf]
   [edn-query-language.core :as eql]
   [taoensso.encore :as enc]
   [taoensso.timbre :as log]
   [com.fulcrologic.statecharts.integration.fulcro.routing-options :as sfro]))

(defn report-ident
  "Returns the ident of a RAD report. The parameter can be a react instance, a class, or the registry key(word)
   of the report."
  [report-class-or-registry-key]
  (if (keyword? report-class-or-registry-key)
    [::id report-class-or-registry-key]
    (comp/get-ident report-class-or-registry-key {})))

(defn report-session-id
  "Returns the statechart session ID for a report. Accepts a report instance, class, or registry keyword."
  [report-class-or-instance]
  (sc.session/ident->session-id
   (if (comp/component-instance? report-class-or-instance)
     (comp/get-ident report-class-or-instance)
     (report-ident report-class-or-instance))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; RENDERING
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn default-render-layout
  "Default render layout for reports. Rendering plugins should provide a defmethod for
   `rr/render-report` instead of using `install-layout!`."
  [report-instance]
  (log/error "No report layout renderer installed for style"
             (or (some-> report-instance comp/component-options ::layout-style) :default))
  nil)

(defn render-layout [report-instance]
  (rr/render-report report-instance (rc/component-options report-instance)))

(defn default-render-row [report-instance row-class row-props]
  (let [{::app/keys [runtime-atom]} (comp/any->app report-instance)
        layout-style (or (some-> report-instance comp/component-options ::row-style) :default)
        render       (some-> runtime-atom deref :com.fulcrologic.rad/controls ::row-style->row-layout layout-style)]
    (if render
      (render report-instance row-class row-props)
      (do
        (log/error "No layout function found for form layout style" layout-style)
        nil))))

(defn render-row
  "Render a row of the report. Leverages report-render/render-row, whose default uses whatever UI plugin you have."
  [report-instance row-class row-props]
  (rr/render-row report-instance (rc/component-options report-instance) row-props))

(defn control-renderer
  "Get the report controls renderer for the given report instance. Returns a `(fn [this])`."
  [report-instance]
  (let [{::app/keys [runtime-atom]} (comp/any->app report-instance)
        control-style (or (some-> report-instance comp/component-options ::control-style) :default)
        control       (some-> runtime-atom deref :com.fulcrologic.rad/controls ::control-style->control control-style)]
    (if control
      control
      (do
        (log/error "No layout function found for report control style" control-style)
        nil))))

(defn render-controls
  "Renders just the control section of the report. See also `control-renderer` if you desire rendering the controls in
   more than one place in the UI at once (e.g. top/bottom)."
  [report-instance]
  (rr/render-controls report-instance (rc/component-options report-instance)))

(defn column-heading-descriptors
  "Returns a vector of maps describing what should be shown for column headings. Each
   map may contain:

   :label - The text label
   :help - A string that could be shown as a longer description (e.g. on hover)
   :column - The actual column attribute from the RAD model.
   "
  [report-instance report-options]
  (let [{report-column-headings ::column-headings
         report-column-infos    ::column-infos} report-options
        columns (ro/columns report-options)]
    (mapv (fn [{::keys      [column-heading column-info]
                ::attr/keys [qualified-key] :as attr}]
            {:column attr
             :help   (or
                      (?! (get report-column-infos qualified-key) report-instance)
                      (?! column-info report-instance))
             :label  (or
                      (?! (get report-column-headings qualified-key) report-instance)
                      (?! column-heading report-instance)
                      (?! (ao/label attr) report-instance)
                      (some-> qualified-key name str/capitalize)
                      "")})
          columns)))

;; NOTE: Do NOT register :default defmethods here. Rendering plugins register
;; their own :default implementations via defmethod. Any :default registered
;; here would silently overwrite plugin renderers if this namespace is loaded
;; after the plugin (which happens during namespace reloading or when the
;; require order varies between REPL and production).

(def render-control
  "[control-type style instance control-key]

   Render a single control. Dispatches on [control-type style]. This is an alias for control/render-control."
  control/render-control)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; LOGIC
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn rotate-result
  "Given a report class that has columns, and a raw result grouped by those columns: returns a vector of rows that
   rotate the grouped result into a normal report shape."
  [report-class grouped-result]
  (when-not (map? grouped-result)
    (log/warn "The incoming result looks like it was normalized. Did you forget `ro/denormalize? true` on your report?"))
  (let [columns  (comp/component-options report-class ::columns)
        ks       (map ::attr/qualified-key columns)
        row-data (map (fn [{::attr/keys [qualified-key]}]
                        (get grouped-result qualified-key [])) columns)]
    (apply mapv (fn [& args] (zipmap ks args)) row-data)))

(defn run-report!
  "Run a report with the current parameters"
  ([this]
   (scf/send! this (report-session-id this) :event/run))
  ([app-ish class-or-registry-key]
   (scf/send! app-ish (report-session-id class-or-registry-key) :event/run)))

#?(:clj
   (defn req!
     ([env sym options k pred?]
      (when-not (and (contains? options k) (pred? (get options k)))
        (throw (ana/error env (str "defsc-report " sym " is missing or invalid option " k)))))
     ([env sym options k]
      (when-not (contains? options k)
        (throw (ana/error env (str "defsc-report " sym " is missing option " k)))))))

(defn start-report!
  "Start a report. Not normally needed, since a report is started when it is routed to; however, if you put
  a report on-screen initially (or don't use dynamic router), then you must call this to start your report.

  `options` can contain `::id`, which will cause an instance of the report to be started. Used by containers so that
  multiple instances of the same report can co-exist with different views on the same screen."
  ([app report-class]
   (start-report! app report-class {}))
  ([app report-class options]
   (let [report-ident (comp/ident report-class options)
         session-id   (sc.session/ident->session-id report-ident)
         user-chart   (comp/component-options report-class sfro/statechart)
         machine-key  (or (comp/component-options report-class sfro/statechart-id)
                          (when (keyword? user-chart) user-chart)
                          ::report-chart)
         params       (:route-params options)
         running?     (seq (scf/current-configuration app session-id))]
     (let [chart (if (map? user-chart)
                   user-chart
                   report-chart/report-statechart)]
       (scf/register-statechart! app machine-key chart))
     (if (not running?)
       (scf/start! app
                   {:machine    machine-key
                    :session-id session-id
                    :data       {:fulcro/actors  {:actor/report (scf/actor report-class report-ident)}
                                 :fulcro/aliases {:parameters    [:actor/report :ui/parameters]
                                                  :sort-params   [:actor/report :ui/parameters ::sort]
                                                  :sort-by       [:actor/report :ui/parameters ::sort :sort-by]
                                                  :ascending?    [:actor/report :ui/parameters ::sort :ascending?]
                                                  :filtered-rows [:actor/report :ui/cache :filtered-rows]
                                                  :sorted-rows   [:actor/report :ui/cache :sorted-rows]
                                                  :raw-rows      [:actor/report :ui/loaded-data]
                                                  :current-rows  [:actor/report :ui/current-rows]
                                                  :current-page  [:actor/report :ui/parameters ::current-page]
                                                  :selected-row  [:actor/report :ui/parameters ::selected-row]
                                                  :page-count    [:actor/report :ui/page-count]
                                                  :busy?         [:actor/report :ui/busy?]}
                                 :params         params
                                 :route-params   params}})
       (scf/send! app session-id :event/resume (assoc options :params params))))))

(defn default-compare-rows
  [{:keys [sort-by ascending?]} a b]
  (try
    (let [av (get a sort-by)
          bv (get b sort-by)]
      (if ascending?
        (compare av bv)
        (compare bv av)))
    (catch #?(:clj Exception :cljs :default) _
      0)))

(defn report-will-enter
  "DEPRECATED: Routing lifecycle is now managed by statecharts routing via sfro options.
   This function should not be called. Use `sfro/initialize` and `sfro/statechart` on your report instead."
  [_app _route-params _report-class]
  (log/error "report-will-enter is removed. Routing lifecycle is managed by statecharts routing. See sfro/initialize.")
  (throw (ex-info "report-will-enter is removed. Use statecharts routing (sfro options) instead." {})))

#?(:clj
   (defmacro defsc-report
     "Define a report. Just like defsc, but you do not specify query/ident/etc.

     Instead, use report-options (aliased as ro below):

     ro/columns
     ro/row-pk
     ro/source-attribute

     If you elide the body, one will be generated for you with the classname `{sym}-Row` where `sym` is the sym you supply
     for the report itself.
     "
     [sym arglist & args]
     (let [this-sym  (first arglist)
           props-sym (second arglist)
           props-sym (if (map? props-sym) (:as props-sym) props-sym)
           options   (first args)
           options   (opts/macro-optimize-options &env options #{::column-formatters ::field-formatters ::column-headings ::form-links} {})]
       (when (or (= '_ props-sym) (= '_ this-sym) (= props-sym this-sym) (not (symbol? this-sym)) (not (symbol? props-sym)))
         (throw (ana/error &env (str "defsc-report argument list must use a real (unique) symbol (or a destructuring with `:as`) for the `this` and `props` (1st and 2nd) arguments."))))
       (req! &env sym options ::columns #(or (symbol? %) (every? symbol? %)))
       (req! &env sym options ::row-pk #(symbol? %))
       (req! &env sym options ::source-attribute keyword?)
       (let
        [generated-row-sym (symbol (str (name sym) "-Row"))
         {::control/keys [controls]
          ::keys [BodyItem edit-form columns row-pk form-links query-inclusions
                  row-query-inclusion denormalize? row-actions route initialize-ui-props] :as options} options
         _                 (when edit-form (throw (ana/error &env "::edit-form is no longer supported. Use ::form-links instead.")))
         normalize?        (not denormalize?)
         ItemClass         (or BodyItem generated-row-sym)
         subquery          `(comp/get-query ~ItemClass)
         nspc              (if (enc/compiling-cljs?) (-> &env :ns :name str) (name (ns-name *ns*)))
         fqkw              (keyword (str nspc) (name sym))
         user-statechart   (::statechart options)
         query             (into [::id
                                  :ui/parameters
                                  :ui/cache
                                  :ui/busy?
                                  :ui/page-count
                                  :ui/current-page
                                  [::picker-options/options-cache (quote '_)]
                                  {:ui/controls `(comp/get-query Control)}
                                  {:ui/current-rows subquery}
                                  [df/marker-table '(quote _)]]
                                 query-inclusions)
         _                 (when (contains? options :will-enter)
                             (log/warn "defsc-report" sym ":will-enter is ignored. Routing lifecycle is managed by statecharts routing."))
         options           (merge
                            {::compare-rows `default-compare-rows}
                            options
                            (cond-> {::BodyItem      ItemClass
                                     sfro/initialize :once
                                     :query          query
                                     :initial-state  (list 'fn ['params]
                                                           `(let [user-ui-props# (?! ~initialize-ui-props ~sym ~'params)]
                                                              (cond-> {:ui/parameters   {}
                                                                       :ui/cache        {}
                                                                       :ui/controls     (mapv #(select-keys % #{::control/id})
                                                                                              (remove :local? (control/control-map->controls ~controls)))
                                                                       :ui/busy?        false
                                                                       :ui/current-page 1
                                                                       :ui/page-count   1
                                                                       :ui/current-rows []}
                                                                (contains? ~'params ::id) (assoc ::id (::id ~'params))
                                                                (seq user-ui-props#) (merge user-ui-props#))))
                                     :ident          (list 'fn [] [::id `(or (::id ~props-sym) ~fqkw)])}
                              (keyword? user-statechart) (assoc sfro/statechart-id user-statechart)
                              (not (keyword? user-statechart)) (assoc sfro/statechart (or user-statechart `report-chart/report-statechart))))
         body              (if (seq (rest args))
                             (rest args)
                             [`(render-layout ~this-sym)])
         row-query         (list 'fn [] `(let [forms#    ~(::form-links options)
                                               id-attrs# (keep #(comp/component-options % ::form/id) (vals forms#))]
                                           (vec
                                            (into #{~@row-query-inclusion}
                                                  (map (fn [attr#] (or
                                                                    (::column-EQL attr#)
                                                                    (::attr/qualified-key attr#))) (conj (set (concat id-attrs# ~columns)) ~row-pk))))))
         props-sym         (gensym "props")
         row-ident         (list 'fn []
                                 `(let [k# (::attr/qualified-key ~row-pk)]
                                    [k# (get ~props-sym k#)]))
         row-actions       (or row-actions [])
         body-options      (cond-> {:query        row-query
                                    ::row-actions row-actions
                                    ::columns     columns}
                             normalize? (assoc :ident row-ident)
                             form-links (assoc ::form-links form-links))
         defs              (if-not BodyItem
                             [`(comp/defsc ~generated-row-sym [this# ~props-sym computed#]
                                 ~body-options
                                 (render-row (:report-instance computed#) ~generated-row-sym ~props-sym))
                              `(comp/defsc ~sym ~arglist ~options ~@body)]
                             [`(comp/defsc ~sym ~arglist ~options ~@body)])]
         `(do
            ~@defs)))))

#?(:clj (s/fdef defsc-report :args ::comp/args))

(defn form-link
  "Get the form link info for a given (column) key.

  Returns nil if there is no link info, otherwise returns:

  ```
  {:edit-form FormClass
   :entity-id id-of-entity-to-edit}
  ```
  "
  [report-instance row-props column-key]
  (let [{::keys [form-links]} (comp/component-options report-instance)
        cls    (get form-links column-key)
        id-key (some-> cls (comp/component-options ::form/id ::attr/qualified-key))]
    (when cls
      {:edit-form cls
       :entity-id (get row-props id-key)})))

(defn link
  "Get a regular lambda link for a given (column) key.

  Returns nil if there is no link info, otherwise returns:

  ```
  {:edit-form FormClass
   :entity-id id-of-entity-to-edit}
  ```
  "
  [report-instance row-props column-key]
  (let [{::keys [form-links]} (comp/component-options report-instance)
        cls    (get form-links column-key)
        id-key (some-> cls (comp/component-options ::form/id ::attr/qualified-key))]
    (when cls
      {:edit-form cls
       :entity-id (get row-props id-key)})))

(defn built-in-formatter [type style]
  (get-in
   {:string  {:default (fn [_ value] value)}
    :instant {:default         (fn [_ value] (dt/inst->human-readable-date value))
              :short-timestamp (fn [_ value] (dt/tformat "MMM d, h:mma" value))
              :timestamp       (fn [_ value] (dt/tformat "MMM d, yyyy h:mma" value))
              :date            (fn [_ value] (dt/tformat "MMM d, yyyy" value))
              :month-day       (fn [_ value] (dt/tformat "MMM d" value))
              :time            (fn [_ value] (dt/tformat "h:mma" value))}
    :keyword {:default (fn [_ value _ column-attribute]
                         (if-let [labels (::attr/enumerated-labels column-attribute)]
                           (labels value)
                           (some-> value (name) str/capitalize)))}
    :enum    {:default (fn [_ value _ column-attribute]
                         (if-let [labels (::attr/enumerated-labels column-attribute)]
                           (labels value) (str value)))}
    :int     {:default (fn [_ value] (str value))}
    :decimal {:default    (fn [_ value] (math/numeric->str value))
              :currency   (fn [_ value] (math/numeric->str (math/round value 2)))
              :percentage (fn [_ value] (math/numeric->percent-str value))
              :USD        (fn [_ value] (math/numeric->currency-str value))}
    :boolean {:default (fn [_ value] (if value (tr "true") (tr "false")))}}
   [type style]))

(defn formatted-column-value
  "Given a report instance, a row of props, and a column attribute for that report:
   returns the formatted value of that column using the field formatter(s) defined
   on the column attribute or report. If no formatter is provided a default formatter
   will be used."
  [report-instance row-props {::keys      [field-formatter column-formatter]
                              ::attr/keys [qualified-key type style] :as column-attribute}]
  (let [value                  (get row-props qualified-key)
        report-field-formatter (or
                                (comp/component-options report-instance ::column-formatters qualified-key)
                                (comp/component-options report-instance ::field-formatters qualified-key))
        {::app/keys [runtime-atom]} (comp/any->app report-instance)
        formatter              (cond
                                 report-field-formatter report-field-formatter
                                 column-formatter column-formatter
                                 field-formatter field-formatter
                                 :else (let [style                (or
                                                                   (comp/component-options report-instance ::column-styles qualified-key)
                                                                   style
                                                                   :default)
                                             installed-formatters (some-> runtime-atom deref :com.fulcrologic.rad/controls ::type->style->formatter)
                                             formatter            (get-in installed-formatters [type style])]
                                         (or
                                          formatter
                                          (built-in-formatter type style)
                                          (fn [_ v] (str v)))))
        formatted-value        ((lambda/->arity-tolerant formatter) report-instance value row-props column-attribute)]
    formatted-value))

(defn install-formatter!
  "Install a formatter for the given data type and style. The data type must match a supported data type
   of attributes, and the style can either be `:default` or a user-defined keyword the represents the
   style you want to support. Some common styles have predefined support, such as `:USD` for US Dollars.

   This should be called before mounting your app.

   Ex.:

   ```clojure
   (install-formatter! app :boolean :default (fn [report-instance value] (if value \"yes\" \"no\")))
   ```"
  [app type style formatter]
  (let [{::app/keys [runtime-atom]} app]
    (swap! runtime-atom assoc-in [:com.fulcrologic.rad/controls ::type->style->formatter type style] formatter)))

(defn install-row-layout!
  "Install a row layout renderer for the given style. `render-row` is a `(fn [report-instance row-class row-props])`.

  See other support functions in this ns for help rendering, such as `formatted-column-value`, `form-link`,
  `select-row!`.

   This should be called before mounting your app.
   "
  [app row-style render-row]
  (let [{::app/keys [runtime-atom]} app]
    (swap! runtime-atom assoc-in [:com.fulcrologic.rad/controls ::row-style->row-layout row-style] render-row)))

(defn current-rows
  "Get a vector of the current rows that should be shown by the renderer (sorted/paginated/filtered). `report-instance`
   is available in the rendering `env`."
  [report-instance]
  (let [props (comp/props report-instance)]
    (get props :ui/current-rows [])))

(defn loading?
  "Returns true if the given report instance has an active network load in progress."
  [report-instance]
  (when report-instance
    (df/loading? (get-in (comp/props report-instance) [df/marker-table (comp/get-ident report-instance)]))))

(defn sort-rows!
  "Sort the report by the given attribute. Changes direction if the report is already sorted by that attribute. The implementation
   of sorting is built-in and uses compare, but you can override how sorting works by defining `ro/compare-rows` on your report."
  ([this by-attribute]
   (scf/send! this (report-session-id this) :event/sort {::attr/attribute by-attribute}))
  ([app class-or-reg-key by-attribute]
   (scf/send! app (report-session-id class-or-reg-key) :event/sort {::attr/attribute by-attribute})))

(defn clear-sort!
  "Make it so the report is not sorted (skips the sort step on any action that would normally (re)sort
   the report). This can be used to speed up loading of large results, especially if they were
   already in an acceptable order from the server.

   NOTE: This does NOT refresh the report. The natural order will appear next time the report needs sorted."
  ([this]
   (scf/send! this (report-session-id this) :event/clear-sort))
  ([app class-or-reg-key]
   (scf/send! app (report-session-id class-or-reg-key) :event/clear-sort)))

(defn filter-rows!
  "Update the filtered rows based on current report parameters."
  ([this]
   (scf/send! this (report-session-id this) :event/filter))
  ([app class-or-reg-key]
   (scf/send! app (report-session-id class-or-reg-key) :event/filter)))

(defn goto-page!
  "Move to the given page (if it exists)"
  ([this page-number]
   (scf/send! this (report-session-id this) :event/goto-page {:page page-number}))
  ([app class-or-reg-key page-number]
   (scf/send! app (report-session-id class-or-reg-key) :event/goto-page {:page page-number})))

(defn next-page!
  "Move to the next page (if there is one)"
  ([this]
   (scf/send! this (report-session-id this) :event/next-page))
  ([app class-or-reg-key]
   (scf/send! app (report-session-id class-or-reg-key) :event/next-page)))

(defn prior-page!
  "Move to the prior page (if there is one)"
  ([this]
   (scf/send! this (report-session-id this) :event/prior-page))
  ([app class-or-reg-key]
   (scf/send! app (report-session-id class-or-reg-key) :event/prior-page)))

(defn current-page
  "Returns the current page number displayed on the report"
  ([report-instance]
   (get-in (comp/props report-instance) [:ui/parameters ::current-page] 1))
  ([state-map report-class-or-registry-key]
   (get-in state-map (conj (report-ident report-class-or-registry-key) :ui/parameters ::current-page) 1)))

(defn page-count
  "Returns how many pages the current report has."
  ([report-instance]
   (get-in (comp/props report-instance) [:ui/page-count] 1))
  ([state-map report-class-or-registry-key]
   (get-in state-map (conj (report-ident report-class-or-registry-key) :ui/page-count) 1)))

(defn currently-selected-row
  "Returns the currently-selected row index, if any (-1 if nothing is selected)."
  ([report-instance]
   (get-in (comp/props report-instance) [:ui/parameters ::selected-row] -1))
  ([state-map report-class-or-registry-key]
   (get-in state-map (conj (report-ident report-class-or-registry-key) :ui/parameters ::selected-row) -1)))

(defn select-row!
  ([report-instance idx]
   (scf/send! report-instance (report-session-id report-instance) :event/select-row {:row idx}))
  ([app class-or-reg-key idx]
   (scf/send! app (report-session-id class-or-reg-key) :event/select-row {:row idx})))

(defn column-classes
  "Returns a string of column classes that can be defined on the attribute at ::report/column-class or on the
   report in the ::report/column-classes map. The report map overrides the attribute"
  [report-instance-or-class {::keys      [column-class]
                             ::attr/keys [qualified-key] :as attr}]
  (let [rpt-column-class (comp/component-options report-instance-or-class ::column-classes qualified-key)]
    (or rpt-column-class column-class)))

(defn genrow
  "Generates a row class for reports. Mainly meant for internal use, but might be useful in custom report generation code.

  registry-key - The unique key to register the generated class under
  options - The top report options"
  [registry-key options]
  (let [{::keys [columns row-pk form-links initLocalState
                 row-query-inclusion denormalize? row-actions]} options
        normalize?   (not denormalize?)
        row-query    (let [id-attrs (keep #(comp/component-options % ::form/id) (vals form-links))]
                       (vec
                        (into (set row-query-inclusion)
                              (map (fn [attr] (or
                                               (::column-EQL attr)
                                               (::attr/qualified-key attr))) (conj (set (concat id-attrs columns)) row-pk)))))
        row-key      (::attr/qualified-key row-pk)
        row-ident    (fn [this props] [row-key (get props row-key)])
        row-actions  (or row-actions [])
        row-render   (fn [this]
                       (comp/wrapped-render this
                                            (fn []
                                              (let [props (comp/props this)]
                                                (render-row this (rc/registry-key->class registry-key) props)))))
        body-options (cond-> {:query        (fn [this] row-query)
                              ::row-actions row-actions
                              ::columns     columns}
                       normalize? (assoc :ident row-ident)
                       form-links (assoc ::form-links form-links))]
    (comp/sc registry-key body-options row-render)))

(defn report
  "Create a RAD report component. `options` is the map of report/Fulcro options. The `registry-key` is the globally
   unique name (as a keyword) that this component should be known by, and `render` is a `(fn [this props])` (optional)
   for rendering the body, which defaults to the built-in `render-layout`.

   WARNING: The macro version ensures that there is a constant react type to refer to. Using this function MAY cause
   hot code reload behaviors that rely on react-type to misbehave due to the mismatch (closure over old version)."
  ([registry-key options]
   (report registry-key options (fn [this _] (render-layout this))))
  ([registry-key options render]
   (assert (vector? (options ::columns)))
   (assert (attr/attribute? (options ::row-pk)))
   (assert (keyword? (options ::source-attribute)))
   (let [generated-row-key (keyword (namespace registry-key) (str (name registry-key) "-Row"))
         {::control/keys [controls]
          ::keys         [BodyItem query-inclusions route initialize-ui-props]} options
         constructor       (comp/react-constructor (:initLocalState options))
         generated-class   (volatile! nil)
         get-class         (fn [] @generated-class)
         ItemClass         (or BodyItem (genrow generated-row-key options))
         user-statechart   (::statechart options)
         query             (into [::id
                                  :ui/parameters
                                  :ui/cache
                                  :ui/busy?
                                  :ui/page-count
                                  :ui/current-page
                                  [::picker-options/options-cache '_]
                                  {:ui/controls (comp/get-query Control)}
                                  {:ui/current-rows (comp/get-query ItemClass)}
                                  [df/marker-table '_]]
                                 query-inclusions)
         render            (fn [this _props]
                             (comp/wrapped-render this
                                                  (fn []
                                                    (let [props (comp/props this)]
                                                      (render this props)))))
         options           (merge
                            {::compare-rows default-compare-rows}
                            options
                            (cond-> {:render        render
                                     ::BodyItem     ItemClass
                                     :query         (fn [_] query)
                                     :initial-state (fn [params]
                                                      (let [user-initial-state (?! initialize-ui-props (get-class) params)]
                                                        (cond-> {:ui/parameters   {}
                                                                 :ui/cache        {}
                                                                 :ui/controls     (mapv #(select-keys % #{::control/id})
                                                                                        (remove :local? (control/control-map->controls controls)))
                                                                 :ui/busy?        false
                                                                 :ui/current-page 1
                                                                 :ui/page-count   1
                                                                 :ui/current-rows []}
                                                          (contains? params ::id) (assoc ::id (::id params))
                                                          (seq user-initial-state) (merge user-initial-state))))
                                     :ident         (fn [this props] [::id (or (::id props) registry-key)])
                                     sfro/initialize :once}
                              (keyword? user-statechart) (assoc sfro/statechart-id user-statechart)
                              (not (keyword? user-statechart)) (assoc sfro/statechart (or user-statechart report-chart/report-statechart))))
         cls               (comp/sc registry-key options render)]
     (vreset! generated-class cls)
     cls)))

(defn clear-report*
  "Mutation helper. Clear a report out of app state. The report should not be visible when you do this."
  [state-map ReportClass]
  (let [report-ident (comp/get-ident ReportClass {})
        session-id   (sc.session/ident->session-id report-ident)
        [table report-class-registry-key] report-ident]
    (-> state-map
        (update :com.fulcrologic.statecharts/session-id dissoc session-id)
        (update table dissoc report-class-registry-key)
        (merge/merge-component ReportClass (comp/get-initial-state ReportClass {})))))

(defmutation clear-report
  "MUTATION: Clear a report (which should not be on screen) out of app state."
  [{:keys [report-ident]}]
  (action [{:keys [state]}]
          (let [[table report-class-registry-key] report-ident
                Report (comp/registry-key->class report-class-registry-key)]
            (swap! state clear-report* Report))))

(defn clear-report!
  "Run a transaction that completely clears a report (which should not be on-screen) out of app state."
  [app-ish ReportClass]
  (comp/transact! app-ish [(clear-report {:report-ident (comp/get-ident ReportClass {})})]))

(defn trigger!
  "Trigger an event on a report. You can use the `this` of the report with arity-2 and -3.

   For arity-4 the `report-class-ish` is something from which the report's ident can be derived: I.e. The
   report class, report's Fulcro registry key, or the ident itself."
  ([report-instance event]
   (trigger! report-instance event {}))
  ([report-instance event event-data]
   (trigger! report-instance report-instance event event-data))
  ([app-ish report-class-ish event event-data]
   (let [ident (cond
                 (or
                  (string? report-class-ish)
                  (symbol? report-class-ish)
                  (keyword? report-class-ish)) (some-> report-class-ish (comp/registry-key->class) (comp/get-ident {}))
                 (vector? report-class-ish) report-class-ish
                 (comp/component-class? report-class-ish) (comp/get-ident report-class-ish {})
                 (comp/component-instance? report-class-ish) (comp/get-ident report-class-ish))]
     (when-not (vector? ident)
       (log/error (ex-info "Cannot trigger an event on a report with invalid report identifier"
                           {:value report-class-ish
                            :type  (type report-class-ish)})))
     (when (vector? ident)
       (scf/send! app-ish (sc.session/ident->session-id ident) event event-data)))))
