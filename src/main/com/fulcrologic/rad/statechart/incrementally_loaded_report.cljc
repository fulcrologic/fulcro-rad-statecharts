(ns com.fulcrologic.rad.statechart.incrementally-loaded-report
  "A Report statechart that loads data in chunks to prevent network timeouts for large result sets.
   Requires a resolver that accepts `:report/offset` and `:report/limit` parameters and returns:

   ```
   {:report/next-offset n
    :report/results data}
   ```

   where `n` is the next offset for the next page of data, and `data` is a vector of results.

   Replaces `com.fulcrologic.rad.state-machines.incrementally-loaded-report`."
  (:require
   [com.fulcrologic.fulcro.algorithms.merge :as merge]
   [com.fulcrologic.fulcro.components :as comp]
   [com.fulcrologic.fulcro.raw.components :as rc]
   [com.fulcrologic.rad.attributes :as attr]
   [com.fulcrologic.rad.attributes-options :as ao]
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

(def chunk-size
  "Report option. Integer (default 100). Number of rows to load per chunk."
  ::chunk-size)

;; ---- Expression Functions ----

(defn incremental-init-expr
  "Expression: Initialize the incrementally-loaded report."
  [env data _event-name event-data]
  (rexpr/initialize-params-expr env data _event-name event-data))

(defn start-chunk-load-expr
  "Expression: Start loading data from the server, beginning at offset 0."
  [env data _event-name _event-data]
  (let [Report         (rexpr/report-class data)
        report-ident   (rexpr/actor-ident data :actor/report)
        {::report/keys [source-attribute load-options]
         ::keys        [chunk-size]} (comp/component-options Report)
        load-options   (?! load-options env)
        current-params (assoc (rexpr/current-control-parameters data)
                              :report/offset 0
                              :report/limit (or chunk-size 100))
        page-path      (rexpr/resolve-alias-path data :loaded-page)]
    [(fops/assoc-alias :raw-rows [] :busy? true)
     (fops/load source-attribute nil
                (merge
                 {:params          current-params
                  ::sc/ok-event    :event/page-loaded
                  ::sc/error-event :event/failed
                  :marker          report-ident
                  :target          page-path}
                 load-options))]))

(defn process-chunk-expr
  "Expression: Process a loaded chunk — append results, load next chunk or signal completion."
  [env data _event-name _event-data]
  (let [Report         (rexpr/report-class data)
        report-ident   (rexpr/actor-ident data :actor/report)
        {::report/keys [BodyItem source-attribute load-options]
         ::keys        [chunk-size]} (comp/component-options Report)
        load-options   (?! load-options env)
        aliases        (scf/resolve-aliases data)
        loaded-page    (:loaded-page aliases)
        {:report/keys [next-offset results]} loaded-page
        raw-path       (rexpr/resolve-alias-path data :raw-rows)
        current-params (assoc (rexpr/current-control-parameters data)
                              :report/offset next-offset
                              :report/limit (or chunk-size 100))
        page-path      (rexpr/resolve-alias-path data :loaded-page)
        more?          (and (number? next-offset) (pos? next-offset))]
    (into
     [(fops/apply-action
       (fn [sm]
         (reduce
          (fn [s item] (merge/merge-component s BodyItem item :append raw-path))
          sm
          results)))
      (ops/assign :loading-complete? (not more?))]
     (when more?
       [(fops/load source-attribute nil
                   (merge
                    {:params          current-params
                     ::sc/ok-event    :event/page-loaded
                     ::sc/error-event :event/failed
                     :marker          report-ident
                     :target          page-path}
                    load-options))]))))

(defn loading-complete?
  "Condition: Have all chunks been loaded?"
  [_env data _event-name _event-data]
  (:loading-complete? data))

(defn finalize-report-expr
  "Expression: Finalize the report after all chunks are loaded — preprocess, filter, sort, paginate."
  [_env data _event-name _event-data]
  (let [Report (rexpr/report-class data)
        {::report/keys [row-pk report-loaded]} (comp/component-options Report)
        table-name (::attr/qualified-key row-pk)
        state-map  (:fulcro/state-map data)]
    [(fops/apply-action
      (fn [sm]
        (-> sm
            (rexpr/preprocess-raw-result data)
            (rexpr/filter-rows-state data)
            (rexpr/sort-rows-state data)
            (rexpr/populate-page-state data))))
     (fops/assoc-alias :busy? false)
     (ops/assign :last-load-time (inst-ms (dt/now)))
     (ops/assign :raw-items-in-table (count (keys (get state-map table-name))))]))

(defn cache-expired?
  "Condition: Is the cached data expired for the incrementally-loaded report?"
  [_env data _event-name _event-data]
  (let [Report    (rexpr/report-class data)
        {::report/keys [load-cache-seconds load-cache-expired? row-pk]} (comp/component-options Report)
        state-map (:fulcro/state-map data)
        now-ms              (inst-ms (dt/now))
        last-load-time      (:last-load-time data)
        last-table-count    (:raw-items-in-table data)
        cache-expiration-ms (* 1000 (or load-cache-seconds 0))
        table-name          (::attr/qualified-key row-pk)
        current-table-count (count (keys (get state-map table-name)))
        cache-looks-stale?  (or
                             (nil? last-load-time)
                             (not= current-table-count last-table-count)
                             (< last-load-time (- now-ms cache-expiration-ms)))
        user-cache-expired? (?! load-cache-expired? nil cache-looks-stale?)]
    (if (boolean? user-cache-expired?)
      user-cache-expired?
      cache-looks-stale?)))

(defn resume-from-cache-expr
  "Expression: Resume from cached data — re-filter and paginate."
  [env data _event-name event-data]
  (let [init-ops (rexpr/initialize-params-expr env data _event-name event-data)]
    (into init-ops
          [(fops/apply-action
            (fn [sm]
              (-> sm
                  (rexpr/filter-rows-state data)
                  (rexpr/sort-rows-state data)
                  (rexpr/populate-page-state data))))])))

;; ---- Statechart Definition ----

(def incrementally-loaded-report-statechart
  "Incrementally-loaded report statechart. Data is loaded in chunks from the server,
   then filtered/sorted/paginated on the client."
  (statechart {:id ::incrementally-loaded-report-chart :initial :state/initializing}

              (data-model {:expr (fn [_ _]
                                   {:last-load-time nil
                                    :raw-items-in-table 0})})

              (state {:id :state/initializing}
                     (on-entry {}
                               (script {:expr incremental-init-expr}))
                     (transition {:cond rexpr/should-run-on-mount? :target :state/loading})
                     (transition {:target :state/ready}))

              (state {:id :state/loading}
                     (on-entry {}
                               (script {:expr start-chunk-load-expr}))
                     ;; Each chunk loaded triggers processing
                     (on :event/page-loaded :state/processing-chunk)
                     (on :event/failed :state/ready))

              (state {:id :state/processing-chunk}
                     (on-entry {}
                               (script {:expr process-chunk-expr}))
                     ;; If all chunks loaded, finalize immediately via eventless transition
                     (transition {:cond loading-complete? :target :state/finalizing})
                     ;; Otherwise wait for next chunk
                     (on :event/page-loaded :state/processing-chunk)
                     (on :event/failed :state/ready))

              (state {:id :state/finalizing}
                     (on-entry {}
                               (script {:expr finalize-report-expr}))
                     (transition {:target :state/ready}))

              (state {:id :state/ready}
                     ;; Sort (client-side)
                     (transition {:event :event/sort :target :state/sorting}
                                 (script {:expr rexpr/do-sort-and-clear-busy-expr}))

                     ;; Filter (client-side)
                     (transition {:event :event/filter :target :state/filtering}
                                 (script {:expr rexpr/do-filter-and-clear-busy-expr}))

                     ;; Pagination (client-side)
                     (handle :event/goto-page rexpr/goto-page-expr)
                     (handle :event/next-page rexpr/next-page-expr)
                     (handle :event/prior-page rexpr/prior-page-expr)

                     ;; Row selection
                     (handle :event/select-row rexpr/select-row-expr)

                     ;; Parameter management
                     (handle :event/set-ui-parameters rexpr/set-params-expr)

                     ;; Reload — full chunk load
                     (on :event/run :state/loading)

                     ;; Resume — check cache first
                     (transition {:event :event/resume :cond cache-expired? :target :state/loading}
                                 (script {:expr incremental-init-expr}))
                     (transition {:event :event/resume :target :state/ready}
                                 (script {:expr resume-from-cache-expr})))

              ;; Observable intermediate states for sort/filter
              (state {:id :state/sorting}
                     (transition {:target :state/ready}))

              (state {:id :state/filtering}
                     (transition {:target :state/ready}))))

;; ---- Public API ----

(defn start-incrementally-loaded-report!
  "Start an incrementally-loaded report."
  ([app report-class]
   (start-incrementally-loaded-report! app report-class {}))
  ([app report-class options]
   (let [report-ident (comp/ident report-class options)
         session-id   (sc.session/ident->session-id report-ident)
         machine-key  (or (comp/component-options report-class ::report/machine) ::incrementally-loaded-report-chart)
         params       (:route-params options)
         running?     (seq (scf/current-configuration app session-id))]
     (when (= machine-key ::incrementally-loaded-report-chart)
       (scf/register-statechart! app ::incrementally-loaded-report-chart incrementally-loaded-report-statechart))
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
                                                  :loaded-page   [:actor/report :ui/incremental-page]
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
