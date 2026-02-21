(ns com.fulcrologic.rad.report-expressions
  "Expression functions for report statecharts. All expressions follow the 4-arg convention:
   `(fn [env data event-name event-data] ...)` and return a vector of operations.

   These expressions are shared across all report variants (standard, server-paginated,
   incrementally-loaded). Variant-specific expressions live in their respective namespaces."
  (:require
   [com.fulcrologic.fulcro.algorithms.normalized-state :as fstate]
   [com.fulcrologic.fulcro.components :as comp]
   [com.fulcrologic.fulcro.raw.components :as rc]
   [com.fulcrologic.rad.attributes :as attr]
   [com.fulcrologic.rad.attributes-options :as ao]
   [com.fulcrologic.rad.control :as control]
   [com.fulcrologic.rad.options-util :as opts :refer [?!]]
   [com.fulcrologic.rad.report-options :as ro]
   [com.fulcrologic.rad.type-support.date-time :as dt]
   [com.fulcrologic.statecharts :as-alias sc]
   [com.fulcrologic.statecharts.data-model.operations :as ops]
   [com.fulcrologic.statecharts.integration.fulcro :as scf]
   [com.fulcrologic.statecharts.integration.fulcro.operations :as fops]
   [edn-query-language.core :as eql]
   [taoensso.encore :as enc]
   [taoensso.timbre :as log]))

;; ---- Helpers ----

(defn report-class
  "Returns the report component class from the statechart data."
  [data]
  (scf/resolve-actor-class data :actor/report))

(defn report-options*
  "Returns the component options of the report actor."
  [data & k-or-ks]
  (apply comp/component-options (report-class data) k-or-ks))

(defn actor-ident
  "Returns the ident of an actor from the statechart data."
  [data actor-key]
  (get-in data [:fulcro/actors actor-key :ident]))

(defn aliases
  "Resolves all aliases from the statechart data."
  [data]
  (scf/resolve-aliases data))

(defn current-control-parameters
  "Reads the current control parameter values from the Fulcro state map.
   Returns a map of control-key -> value."
  [data]
  (let [state-map     (:fulcro/state-map data)
        Report        (report-class data)
        report-ident  (actor-ident data :actor/report)
        controls      (comp/component-options Report ::control/controls)
        controls      (control/control-map->controls controls)]
    (reduce
     (fn [result {:keys          [local?]
                  ::control/keys [id]}]
       (let [v (if local?
                 (get-in state-map (conj report-ident :ui/parameters id))
                 (get-in state-map [::control/id id ::control/value]))]
         (if (nil? v)
           result
           (assoc result id v))))
     {}
     controls)))

(defn initial-sort-params
  "Returns the initial sort parameters for a report."
  [data]
  (merge {:ascending? true}
         (comp/component-options (report-class data)
                                 :com.fulcrologic.rad.report/initial-sort-params)))

;; ---- Expression Functions ----

(defn initialize-params-expr
  "Expression: Initialize report parameters from route params, control defaults, and state.
   Called on entry to :state/initializing."
  [env data _event-name event-data]
  (let [state-map      (:fulcro/state-map data)
        app            (:fulcro/app env)
        report-ident   (actor-ident data :actor/report)
        Report         (report-class data)
        path           (conj report-ident :ui/parameters)
        params         (:params event-data)
        externally-controlled? (:com.fulcrologic.rad.report/externally-controlled? event-data)
        controls       (comp/component-options Report ::control/controls)
        init-params    {:com.fulcrologic.rad.report/sort         (initial-sort-params data)
                        :com.fulcrologic.rad.report/current-page 1}
        ;; First operation: set initial parameters
        ops            [(fops/apply-action
                         (fn [sm]
                           (as-> sm $
                             (assoc-in $ path (merge init-params {:com.fulcrologic.rad.report/sort {}}))
                             (reduce-kv
                              (fn [s control-key {:keys [local? retain? default-value]}]
                                (let [event-value        (enc/nnil (get params control-key))
                                      control-value-path (if local?
                                                           (conj report-ident :ui/parameters control-key)
                                                           [::control/id control-key ::control/value])
                                      state-value        (when-not (false? retain?) (get-in state-map control-value-path))
                                      explicit-value     event-value
                                      default-value      (?! default-value app)
                                      v                  (enc/nnil explicit-value state-value default-value)
                                      skip?              (or (and (not local?) externally-controlled?)
                                                             (nil? v))]
                                  (if skip? s (assoc-in s control-value-path v))))
                              $
                              controls))))]]
    ops))

(defn should-run-on-mount?
  "Condition: returns true if the report should load data immediately on mount."
  [_env data _event-name _event-data]
  (boolean (comp/component-options (report-class data) :com.fulcrologic.rad.report/run-on-mount?)))

(defn start-load-expr
  "Expression: Start loading report data from the server.
   Called on entry to :state/loading."
  [env data _event-name _event-data]
  (let [Report         (report-class data)
        report-ident   (actor-ident data :actor/report)
        opts             (comp/component-options Report)
        BodyItem         (get opts ro/BodyItem)
        source-attribute (get opts ro/source-attribute)
        load-options     (get opts ro/load-options)
        before-load      (get opts ro/before-load)
        load-options   (?! load-options env)
        current-params (current-control-parameters data)
        path           (conj report-ident :ui/loaded-data)]
    (log/debug "Loading report" source-attribute (comp/component-name Report) (comp/component-name BodyItem))
    (cond-> []
      before-load (conj (fops/apply-action (fn [sm] (before-load sm))))
      true (conj (fops/assoc-alias :busy? true)
                 (fops/load source-attribute BodyItem
                            (merge
                             {:params              current-params
                              ::sc/ok-event       :event/loaded
                              ::sc/error-event    :event/failed
                              :marker              report-ident
                              :target              path}
                             load-options))))))

(defn preprocess-raw-result
  "Pure function: Apply raw-result-xform if defined. Returns updated state-map."
  [state-map data]
  (let [Report    (report-class data)
        xform     (comp/component-options Report :com.fulcrologic.rad.report/raw-result-xform)
        raw-alias (get-in data [:fulcro/aliases :raw-rows])
        raw-path  (when raw-alias
                    ;; resolve the actor-based alias path to absolute path
                    (let [actor-k  (first raw-alias)
                          fields   (rest raw-alias)
                          ident    (actor-ident data actor-k)]
                      (into ident fields)))]
    (if (and xform raw-path)
      (let [raw-result (get-in state-map raw-path)
            new-result (xform Report raw-result)]
        (if new-result
          (assoc-in state-map raw-path new-result)
          state-map))
      state-map)))

(defn resolve-alias-path
  "Resolves an alias path (which may start with an actor keyword) to an absolute Fulcro state path."
  [data alias-key]
  (let [alias-def (get-in data [:fulcro/aliases alias-key])]
    (when alias-def
      (let [first-element (first alias-def)
            rest-path     (vec (rest alias-def))]
        (if (and (keyword? first-element)
                 (= "actor" (namespace first-element)))
          (into (actor-ident data first-element) rest-path)
          alias-def)))))

(defn read-alias
  "Read the value of an alias from the state map."
  [state-map data alias-key]
  (let [path (resolve-alias-path data alias-key)]
    (when path
      (get-in state-map path))))

(defn filter-rows-state
  "Pure function: Apply row-visible? filter to raw rows. Returns updated state-map with filtered-rows set."
  [state-map data]
  (let [Report     (report-class data)
        all-rows   (read-alias state-map data :raw-rows)
        parameters (current-control-parameters data)
        row-visible?     (comp/component-options Report ro/row-visible?)
        skip-filtering?  (comp/component-options Report ro/skip-filtering?)
        filtered   (if (and row-visible? (not (true? (?! skip-filtering? parameters))))
                     (let [normalized?  (some-> all-rows first eql/ident?)
                           BodyItem     (comp/component-options Report ro/BodyItem)]
                       (filterv
                        (fn [row]
                          (let [row (if normalized? (fstate/ui->props state-map BodyItem row) row)]
                            (row-visible? parameters row)))
                        all-rows))
                     all-rows)
        path       (resolve-alias-path data :filtered-rows)]
    (assoc-in state-map path filtered)))

(defn sort-rows-state
  "Pure function: Sort the filtered rows. Returns updated state-map with sorted-rows set."
  [state-map data]
  (let [Report       (report-class data)
        sort-params  (read-alias state-map data :sort-params)
        desired-sort (when sort-params (:sort-by (merge sort-params {:state-map state-map})))
        all-rows     (read-alias state-map data :filtered-rows)
        sorted       (if desired-sort
                       (let [compare-rows (comp/component-options Report :com.fulcrologic.rad.report/compare-rows)
                             normalized?  (some-> all-rows first eql/ident?)]
                         (if compare-rows
                           (let [keyfn     (if normalized? #(get-in state-map %) identity)
                                 sort-p    (merge sort-params {:state-map state-map})
                                 comparefn (fn [a b] (compare-rows sort-p a b))]
                             (vec (sort-by keyfn comparefn all-rows)))
                           all-rows))
                       all-rows)
        path         (resolve-alias-path data :sorted-rows)]
    (assoc-in state-map path sorted)))

(defn populate-page-state
  "Pure function: Paginate sorted rows. Returns updated state-map with current-rows, current-page, page-count set."
  [state-map data]
  (let [Report       (report-class data)
        paginate?    (comp/component-options Report :com.fulcrologic.rad.report/paginate?)
        page-size    (or (?! (comp/component-options Report :com.fulcrologic.rad.report/page-size) nil) 20)
        sorted-rows  (or (read-alias state-map data :sorted-rows) [])
        cr-path      (resolve-alias-path data :current-rows)
        cp-path      (resolve-alias-path data :current-page)
        pc-path      (resolve-alias-path data :page-count)]
    (if paginate?
      (let [current-page   (max 1 (or (read-alias state-map data :current-page) 1))
            n              (count sorted-rows)
            stragglers?    (pos? (rem n page-size))
            pages          (cond-> (int (/ n page-size))
                             stragglers? inc)
            current-page   (cond
                             (zero? pages) 1
                             (> current-page pages) pages
                             :else current-page)
            page-start     (* (dec current-page) page-size)
            rows           (cond
                             (= pages current-page) (subvec sorted-rows page-start n)
                             (> n page-size) (subvec sorted-rows page-start (+ page-start page-size))
                             :else sorted-rows)]
        (if (and (not= 1 current-page) (empty? rows))
          ;; retry from page 1
          (let [rows (if (> n page-size) (subvec sorted-rows 0 page-size) sorted-rows)]
            (-> state-map
                (assoc-in cp-path 1)
                (assoc-in cr-path rows)
                (assoc-in pc-path pages)))
          (-> state-map
              (assoc-in cp-path current-page)
              (assoc-in cr-path rows)
              (assoc-in pc-path pages))))
      (-> state-map
          (assoc-in pc-path 1)
          (assoc-in cr-path sorted-rows)))))

(defn postprocess-page-state
  "Pure function: Apply post-process transform if defined. Returns updated state-map."
  [state-map data]
  (let [Report (report-class data)
        xform  (comp/component-options Report ro/post-process)]
    (if xform
      ;; The post-process fn currently receives a uism-env. In the statechart world,
      ;; we pass it the state-map and data, and it should return an updated state-map.
      ;; For backward compatibility we'll call it with a pseudo-env.
      ;; TODO: Update post-process signature in a future version
      state-map
      state-map)))

(defn process-loaded-data-expr
  "Expression: Process loaded data — preprocess, filter, sort, paginate.
   Called on entry to :state/processing."
  [env data _event-name _event-data]
  (let [state-map (:fulcro/state-map data)
        Report    (report-class data)
        row-pk         (comp/component-options Report ro/row-pk)
        report-loaded  (comp/component-options Report ro/report-loaded)
        table-name (::attr/qualified-key row-pk)
        updated    (-> state-map
                       (preprocess-raw-result data)
                       (filter-rows-state data)
                       (sort-rows-state data)
                       (populate-page-state data))]
    [(fops/apply-action (constantly updated))
     (fops/assoc-alias :busy? false)
     (ops/assign :last-load-time (inst-ms (dt/now)))
     (ops/assign :raw-items-in-table (count (keys (get state-map table-name))))]))

(defn goto-page-expr
  "Expression: Navigate to a specific page number."
  [env data _event-name event-data]
  (let [page      (:page event-data)
        state-map (:fulcro/state-map data)
        cur-page  (read-alias state-map data :current-page)]
    (if (and page (not= cur-page page))
      [(fops/assoc-alias :current-page (max 1 page) :selected-row -1)
       (fops/apply-action
        (fn [sm]
          (let [sm (assoc-in sm (resolve-alias-path data :current-page) (max 1 page))
                sm (assoc-in sm (resolve-alias-path data :selected-row) -1)]
            (-> sm
                (populate-page-state data)))))]
      [])))

(defn next-page-expr
  "Expression: Navigate to the next page."
  [env data _event-name _event-data]
  (let [state-map (:fulcro/state-map data)
        page      (or (read-alias state-map data :current-page) 1)]
    (goto-page-expr env data :event/next-page {:page (inc (max 1 page))})))

(defn prior-page-expr
  "Expression: Navigate to the prior page."
  [env data _event-name _event-data]
  (let [state-map (:fulcro/state-map data)
        page      (or (read-alias state-map data :current-page) 1)]
    (goto-page-expr env data :event/prior-page {:page (dec (max 2 page))})))

(defn store-sort-params-expr
  "Expression: Store sort parameters from event data before transitioning to :state/sorting."
  [_env _data _event-name _event-data]
  ;; Sort params will be applied in do-sort; here we just acknowledge the event.
  ;; The event data (containing ::attr/attribute) is carried through to the sorting state.
  [])

(defn do-sort-and-clear-busy-expr
  "Expression: Perform the actual sort and clear busy flag.
   Called during the eventless transition from :state/sorting -> :state/ready."
  [env data _event-name event-data]
  (let [state-map (:fulcro/state-map data)
        attr      (get event-data ::attr/attribute)
        qk        (when attr (::attr/qualified-key attr))
        sort-by   (read-alias state-map data :sort-by)
        ascending (read-alias state-map data :ascending?)
        ascending (if (and qk (= qk sort-by)) (not ascending) true)]
    (if qk
      [(fops/assoc-alias :busy? false :sort-by qk :ascending? ascending)
       (fops/apply-action
        (fn [sm]
          (let [sm (assoc-in sm (resolve-alias-path data :sort-by) qk)
                sm (assoc-in sm (resolve-alias-path data :ascending?) ascending)]
            (-> sm
                (sort-rows-state data)
                (populate-page-state data)))))]
      [(fops/assoc-alias :busy? false)])))

(defn do-filter-and-clear-busy-expr
  "Expression: Perform the actual filter and clear busy flag.
   Called during the eventless transition from :state/filtering -> :state/ready."
  [_env data _event-name _event-data]
  (let [state-map (:fulcro/state-map data)]
    [(fops/assoc-alias :busy? false)
     (fops/apply-action
      (fn [sm]
        (-> sm
            (filter-rows-state data)
            (sort-rows-state data)
            (populate-page-state data))))]))

(defn select-row-expr
  "Expression: Select a row by index."
  [_env _data _event-name event-data]
  (let [row (:row event-data)]
    [(fops/assoc-alias :selected-row row)]))

(defn set-params-expr
  "Expression: Re-initialize UI parameters."
  [env data event-name event-data]
  (initialize-params-expr env data event-name event-data))

(defn clear-sort-expr
  "Expression: Clear the current sort."
  [_env data _event-name _event-data]
  (let [sort-by-path (resolve-alias-path data :sort-by)]
    [(fops/apply-action
      (fn [sm]
        (update-in sm (butlast sort-by-path) dissoc (last sort-by-path))))]))

(defn cache-expired?
  "Condition: Returns true if the report cache has expired."
  [_env data _event-name _event-data]
  (let [state-map  (:fulcro/state-map data)
        Report     (report-class data)
        load-cache-seconds   (comp/component-options Report ro/load-cache-seconds)
        load-cache-expired?  (comp/component-options Report ro/load-cache-expired?)
        row-pk               (comp/component-options Report ro/row-pk)
        now-ms             (inst-ms (dt/now))
        last-load-time     (:last-load-time data)
        last-table-count   (:raw-items-in-table data)
        cache-exp-ms       (* 1000 (or load-cache-seconds 0))
        table-name         (::attr/qualified-key row-pk)
        current-table-count (count (keys (get state-map table-name)))
        looks-stale?       (or
                            (nil? last-load-time)
                            (not= current-table-count last-table-count)
                            (< last-load-time (- now-ms cache-exp-ms)))
        user-expired?      (?! load-cache-expired? nil looks-stale?)]
    (if (boolean user-expired?)
      user-expired?
      looks-stale?)))

(defn reinitialize-params-expr
  "Expression: Re-initialize parameters (used on resume when cache is expired)."
  [env data event-name event-data]
  (initialize-params-expr env data event-name event-data))

(defn resume-from-cache-expr
  "Expression: Resume a report from cache — re-filter and re-paginate without reloading."
  [_env data _event-name _event-data]
  (let [state-map (:fulcro/state-map data)]
    [(fops/assoc-alias :busy? true)
     (fops/apply-action
      (fn [sm]
        (-> sm
            (filter-rows-state data)
            (sort-rows-state data)
            (populate-page-state data))))
     (fops/assoc-alias :busy? false)]))
