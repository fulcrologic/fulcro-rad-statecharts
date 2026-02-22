(ns com.fulcrologic.rad.statechart.server-paginated-report
  "A Report statechart for large data sets where the server handles pagination, sorting, and filtering.
   Each page is loaded separately with `:indexed-access/options` params. Pages are cached client-side.

   Replaces `com.fulcrologic.rad.state-machines.server-paginated-report`."
  (:require
   [com.fulcrologic.fulcro.algorithms.merge :as merge]
   [com.fulcrologic.fulcro.components :as comp]
   [com.fulcrologic.fulcro.raw.components :as rc]
   [com.fulcrologic.rad.attributes :as attr]
   [com.fulcrologic.rad.attributes-options :as ao]
   [com.fulcrologic.rad.statechart.control :as control]
   [com.fulcrologic.rad.options-util :as opts :refer [?!]]
   [com.fulcrologic.rad.statechart.report :as report]
   [com.fulcrologic.rad.statechart.report-expressions :as rexpr]
   [com.fulcrologic.rad.report-options :as ro]
   [com.fulcrologic.rad.statechart.session :as sc.session]
   [com.fulcrologic.rad.type-support.date-time :as dt]
   [com.fulcrologic.statecharts :as-alias sc]
   [com.fulcrologic.statecharts.chart :refer [statechart]]
   [com.fulcrologic.statecharts.convenience :refer [on handle]]
   [com.fulcrologic.statecharts.data-model.operations :as ops]
   [com.fulcrologic.statecharts.elements :refer [state transition on-entry script data-model]]
   [com.fulcrologic.statecharts.integration.fulcro :as scf]
   [com.fulcrologic.statecharts.integration.fulcro.operations :as fops]
   [taoensso.timbre :as log]))

;; ---- Options ----

(def point-in-time-view?
  "Report option. Boolean (default true). When true the report will send a point-in-time timestamp to the resolver."
  ::point-in-time-view?)

(def direct-page-access?
  "Report option. Boolean (default true). When true the report will ask the resolver for the total result count."
  ::direct-page-access?)

;; ---- Expression Functions ----

(defn server-paginated-init-expr
  "Expression: Initialize the server-paginated report."
  [env data _event-name event-data]
  (let [Report     (rexpr/report-class data)
        pit-view?  (comp/component-options Report ::point-in-time-view?)
        start-time (when pit-view? (dt/now))
        init-ops   (rexpr/initialize-params-expr env data _event-name event-data)]
    (cond-> init-ops
      start-time (conj (ops/assign :point-in-time start-time)))))

(defn load-server-page-expr
  "Expression: Load a page of data from the server with indexed-access options."
  [env data _event-name _event-data]
  (let [Report         (rexpr/report-class data)
        report-ident   (rexpr/actor-ident data :actor/report)
        state-map      (:fulcro/state-map data)
        point-in-time  (:point-in-time data)
        aliases        (scf/resolve-aliases data)
        sort-by-val    (:sort-by aliases)
        ascending?     (:ascending? aliases)
        current-page   (or (:current-page aliases) 1)
        total-results  (:total-results aliases)
        {::report/keys [source-attribute load-options page-size BodyItem]
         ::keys        [direct-page-access?]
         :or           {direct-page-access? true}} (comp/component-options Report)
        page-size      (or (?! page-size env) 20)
        load-options   (?! load-options env)
        PageQuery      (rc/nc [:total
                               {:results (comp/get-query BodyItem)}]
                              {:componentName (keyword (str (comp/component-name Report) "-pagequery"))})
        current-params (assoc (rexpr/current-control-parameters data)
                              :indexed-access/options (cond-> {:limit  page-size
                                                               :offset (* (max 0 (dec current-page)) page-size)}
                                                        (and (not total-results) direct-page-access?) (assoc :include-total? true)
                                                        (keyword? sort-by-val) (assoc :sort-column sort-by-val)
                                                        (false? ascending?) (assoc :reverse? true)
                                                        (inst? point-in-time) (assoc :point-in-time point-in-time)))
        page-path      (rexpr/resolve-alias-path data :loaded-page)]
    [(fops/assoc-alias :raw-rows [] :busy? true)
     (fops/load source-attribute PageQuery
                (merge
                 {:params          current-params
                  ::sc/ok-event    :event/page-loaded
                  ::sc/error-event :event/failed
                  :marker          report-ident
                  :target          page-path}
                 load-options))]))

(defn process-server-page-expr
  "Expression: Process a loaded page — merge results, update page cache."
  [_env data _event-name _event-data]
  (let [Report       (rexpr/report-class data)
        state-map    (:fulcro/state-map data)
        report-ident (rexpr/actor-ident data :actor/report)
        {::report/keys [BodyItem page-size report-loaded]} (comp/component-options Report)
        page-size    (or (?! page-size nil) 20)
        aliases      (scf/resolve-aliases data)
        loaded-page  (:loaded-page aliases)
        {:keys [results total]} loaded-page
        current-page (or (:current-page aliases) 1)
        page-count   (when (number? total)
                       (if (zero? total) 0
                           (cond-> (int (/ total page-size))
                             (pos? (mod total page-size)) inc)))
        raw-path     (rexpr/resolve-alias-path data :raw-rows)
        page-cache-path (conj (rexpr/resolve-alias-path data :page-cache) current-page)]
    (into
     [(fops/apply-action
       (fn [sm]
         (let [;; Append results to raw-rows (normalized merge)
               sm (reduce
                   (fn [s item] (merge/merge-component s BodyItem item :append raw-path))
                   sm
                   results)
               ;; Preprocess
               sm (rexpr/preprocess-raw-result sm data)
               ;; Move raw-rows to page cache
               rows (get-in sm raw-path)
               sm   (assoc-in sm page-cache-path rows)
               ;; Set current-rows to this page's data
               sm   (assoc-in sm (rexpr/resolve-alias-path data :current-rows) rows)]
           sm)))]
     (cond-> [(fops/assoc-alias :busy? false)]
       (number? page-count) (conj (fops/assoc-alias :page-count page-count))
       (number? total) (conj (fops/assoc-alias :total-results total))))))

(defn page-cached?
  "Condition: Is the requested page already in the cache?"
  [_env data _event-name event-data]
  (let [state-map  (:fulcro/state-map data)
        page       (:page event-data)
        cache-path (conj (rexpr/resolve-alias-path data :page-cache) page)
        rows       (get-in state-map cache-path)]
    (seq rows)))

(defn serve-cached-page-expr
  "Expression: Serve a page from the client cache."
  [_env data _event-name event-data]
  (let [page      (:page event-data)
        state-map (:fulcro/state-map data)
        cache-path (conj (rexpr/resolve-alias-path data :page-cache) page)
        rows      (get-in state-map cache-path)]
    [(fops/assoc-alias :current-page page :selected-row -1)
     (fops/apply-action
      (fn [sm]
        (assoc-in sm (rexpr/resolve-alias-path data :current-rows) rows)))]))

(defn set-target-page-expr
  "Expression: Set the target page for loading."
  [_env _data _event-name event-data]
  (let [page (:page event-data)]
    [(fops/assoc-alias :current-page page :selected-row -1)]))

(defn update-sort-and-refresh-expr
  "Expression: Update sort params and clear cache for fresh server query."
  [_env data _event-name event-data]
  (let [attr       (get event-data ::attr/attribute)
        qk         (when attr (::attr/qualified-key attr))
        state-map  (:fulcro/state-map data)
        sort-by    (rexpr/read-alias state-map data :sort-by)
        ascending? (rexpr/read-alias state-map data :ascending?)
        ascending? (if (and qk (= qk sort-by)) (not ascending?) true)]
    (if qk
      [(fops/assoc-alias :sort-by qk :ascending? ascending? :current-page 1)
       (fops/apply-action
        (fn [sm]
          (-> sm
              (assoc-in (rexpr/resolve-alias-path data :page-cache) {})
              (assoc-in (rexpr/resolve-alias-path data :total-results) nil))))
       (ops/assign :point-in-time (dt/now))]
      [])))

(defn refresh-expr
  "Expression: Clear cache and reload."
  [_env data _event-name _event-data]
  [(fops/assoc-alias :current-page 1)
   (fops/apply-action
    (fn [sm]
      (-> sm
          (assoc-in (rexpr/resolve-alias-path data :page-cache) {})
          (assoc-in (rexpr/resolve-alias-path data :total-results) nil))))
   (ops/assign :point-in-time (dt/now))])

(defn resume-server-paginated-expr
  "Expression: Resume a server-paginated report."
  [env data _event-name event-data]
  (let [Report     (rexpr/report-class data)
        pit-view?  (comp/component-options Report ::point-in-time-view?)
        start-time (when pit-view? (dt/now))
        init-ops   (rexpr/initialize-params-expr env data _event-name event-data)]
    (cond-> init-ops
      start-time (conj (ops/assign :point-in-time start-time)))))

;; ---- Statechart Definition ----

(def server-paginated-report-statechart
  "Server-paginated report statechart. Each page is loaded from the server on demand,
   with client-side page caching for back-navigation."
  (statechart {:id ::server-paginated-report-chart :initial :state/initializing}

              (data-model {:expr (fn [_ _]
                                   {:point-in-time nil})})

              (state {:id :state/initializing}
                     (on-entry {}
                               (script {:expr server-paginated-init-expr}))
                     (transition {:cond rexpr/should-run-on-mount? :target :state/loading-page})
                     (transition {:target :state/ready}))

              (state {:id :state/loading-page}
                     (on-entry {}
                               (script {:expr load-server-page-expr}))
                     (on :event/page-loaded :state/processing-page)
                     (on :event/failed :state/ready))

              (state {:id :state/processing-page}
                     (on-entry {}
                               (script {:expr process-server-page-expr}))
                     (transition {:target :state/ready}))

              (state {:id :state/ready}
      ;; Page navigation: check cache first, else load
                     (transition {:event :event/goto-page :cond page-cached? :target :state/ready}
                                 (script {:expr serve-cached-page-expr}))
                     (transition {:event :event/goto-page :target :state/loading-page}
                                 (script {:expr set-target-page-expr}))

      ;; Next/prior page helpers
                     (transition {:event :event/next-page :target :state/ready}
                                 (script {:expr (fn [_env data _event-name _event-data]
                                                  (let [aliases  (scf/resolve-aliases data)
                                                        page     (or (:current-page aliases) 1)]
                                                    [(fops/assoc-alias :current-page (inc (max 1 page)) :selected-row -1)]))}))
                     (transition {:event :event/prior-page :target :state/ready}
                                 (script {:expr (fn [_env data _event-name _event-data]
                                                  (let [aliases  (scf/resolve-aliases data)
                                                        page     (or (:current-page aliases) 1)]
                                                    [(fops/assoc-alias :current-page (dec (max 2 page)) :selected-row -1)]))}))

      ;; Sort triggers full reload (server sorts)
                     (transition {:event :event/sort :target :state/loading-page}
                                 (script {:expr update-sort-and-refresh-expr}))

      ;; Filter triggers full reload (server filters)
                     (transition {:event :event/filter :target :state/loading-page}
                                 (script {:expr refresh-expr}))

      ;; Row selection
                     (handle :event/select-row rexpr/select-row-expr)

      ;; Parameter management
                     (handle :event/set-ui-parameters rexpr/set-params-expr)

      ;; Reload
                     (on :event/run :state/loading-page)

      ;; Resume
                     (transition {:event :event/resume :target :state/loading-page}
                                 (script {:expr resume-server-paginated-expr})))))

;; ---- Public API ----

(defn start-server-paginated-report!
  "Start a server-paginated report."
  ([app report-class]
   (start-server-paginated-report! app report-class {}))
  ([app report-class options]
   (let [report-ident (comp/ident report-class options)
         session-id   (sc.session/ident->session-id report-ident)
         machine-key  (or (comp/component-options report-class ::report/machine) ::server-paginated-report-chart)
         params       (:route-params options)
         running?     (seq (scf/current-configuration app session-id))]
     (when (= machine-key ::server-paginated-report-chart)
       (scf/register-statechart! app ::server-paginated-report-chart server-paginated-report-statechart))
     (if (not running?)
       (scf/start! app
                   {:machine    machine-key
                    :session-id session-id
                    :data       {:fulcro/actors  {:actor/report (scf/actor report-class report-ident)}
                                 :fulcro/aliases {:parameters    [:actor/report :ui/parameters]
                                                  :sort-params   [:actor/report :ui/parameters ::report/sort]
                                                  :sort-by       [:actor/report :ui/parameters ::report/sort :sort-by]
                                                  :ascending?    [:actor/report :ui/parameters ::report/sort :ascending?]
                                                  :raw-rows      [:actor/report :ui/loaded-data]
                                                  :current-rows  [:actor/report :ui/current-rows]
                                                  :current-page  [:actor/report :ui/parameters ::report/current-page]
                                                  :selected-row  [:actor/report :ui/parameters ::report/selected-row]
                                                  :page-count    [:actor/report :ui/page-count]
                                                  :point-in-time [:actor/report :ui/point-in-time]
                                                  :total-results [:actor/report :ui/total-results]
                                                  :loaded-page   [:actor/report :ui/cache :loaded-page]
                                                  :page-cache    [:actor/report :ui/cache :page-cache]
                                                  :busy?         [:actor/report :ui/busy?]}
                                 :params         params
                                 :route-params   params}})
       (scf/send! app session-id :event/resume (assoc options :params params))))))

(defn raw-loaded-item-count
  "Returns the count of raw loaded items when given the props of the report."
  [report-instance]
  (let [state-map (#?(:clj deref :cljs deref) (:com.fulcrologic.fulcro.application/state-atom (comp/any->app report-instance)))
        path      (conj (comp/get-ident report-instance) :ui/loaded-data)]
    (count (get-in state-map path))))
