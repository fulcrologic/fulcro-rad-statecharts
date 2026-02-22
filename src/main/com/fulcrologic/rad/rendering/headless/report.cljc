(ns com.fulcrologic.rad.rendering.headless.report
  "Headless report layout renderers. Registers defmethod implementations for
   `rr/render-report`, `rr/render-body`, `rr/render-row`, `rr/render-controls`,
   `rr/render-header`, and `rr/render-footer`. Uses HTML table elements.
   Plain HTML only, no CSS."
  (:require
   #?(:cljs [com.fulcrologic.fulcro.dom :as dom]
      :clj  [com.fulcrologic.fulcro.dom-server :as dom])
   [com.fulcrologic.fulcro.components :as comp]
   [com.fulcrologic.rad.attributes-options :as ao]
   [com.fulcrologic.rad.control :as control]
   [com.fulcrologic.rad.options-util :refer [?!]]
   [com.fulcrologic.rad.form :as-alias form]
   [com.fulcrologic.rad.report :as report]
   [com.fulcrologic.rad.report-options :as ro]
   [com.fulcrologic.rad.report-render :as rr]
   [com.fulcrologic.rad.routing :as routing]))

(defn- render-report-controls
  "Render the control bar for a report using the control multimethod."
  [report-instance options]
  (let [{:keys [action-layout input-layout]} (control/standard-control-layout report-instance)
        controls (control/component-controls report-instance)]
    (dom/div {:data-rad-type "report-controls"}
      ;; Input controls
             (when (seq input-layout)
               (dom/div {:data-rad-type "report-inputs"}
                        (mapv (fn [row]
                                (dom/div {:data-rad-type "control-row"}
                                         (mapv (fn [control-key]
                                                 (when (keyword? control-key)
                                                   (let [{:keys [type] :or {type :string}} (get controls control-key)
                                                         style (or (:style (get controls control-key)) :default)]
                                                     (control/render-control type style report-instance control-key))))
                                               row)))
                              input-layout)))
      ;; Action buttons
             (when (seq action-layout)
               (dom/div {:data-rad-type "report-actions"}
                        (mapv (fn [control-key]
                                (let [{:keys [type] :or {type :button}} (get controls control-key)
                                      style (or (:style (get controls control-key)) :default)]
                                  (control/render-control type style report-instance control-key)))
                              action-layout))))))

(defn- render-column-headings
  "Render the table header row with column headings."
  [report-instance options]
  (let [heading-descriptors (report/column-heading-descriptors report-instance options)]
    (dom/thead {:data-rad-type "report-header"}
               (dom/tr nil
                       (mapv (fn [{:keys [label column help]}]
                               (let [qk (str (ao/qualified-key column))]
                                 (dom/th {:key             qk
                                          :data-rad-type   "column-header"
                                          :data-rad-column qk
                                          :title           (or help "")
                                          :onClick         (fn [_]
                                                             (report/sort-rows! report-instance column))}
                                         label)))
                             heading-descriptors)))))

(defn- row-form-link
  "Look up form-link for a column. Checks the report's options directly (the `options`
   parameter is already `(comp/component-options report-instance)` from render-layout),
   then falls back to the Row class options."
  [_report-instance options row-props qualified-key]
  (let [form-links  (or (get options ::report/form-links)
                        (let [row-class (ro/BodyItem options)]
                          (when row-class
                            (get (comp/component-options row-class) ::report/form-links))))
        cls         (get form-links qualified-key)
        id-key      (some-> cls (comp/component-options ::form/id) ao/qualified-key)]
    (when cls
      {:edit-form cls
       :entity-id (get row-props id-key)})))

(defn- render-report-row
  "Render a single table row for a report."
  [report-instance options row-props idx]
  (let [columns     (ro/columns options)
        row-actions (ro/row-actions options)]
    (dom/tr {:key           (str "row-" idx)
             :data-rad-type "report-row"
             :onClick       (fn [_] (report/select-row! report-instance idx))}
            (mapv (fn [col-attr]
                    (let [qualified-key (ao/qualified-key col-attr)
                          cell-text     (str (report/formatted-column-value report-instance row-props col-attr))]
                      (dom/td {:data-rad-type   "report-cell"
                               :data-rad-column (str qualified-key)}
                              (if-let [{:keys [edit-form entity-id]} (row-form-link report-instance options row-props qualified-key)]
                                (dom/a {:data-rad-type  "form-link"
                                        :data-rad-column (str qualified-key)
                                        :onClick        (fn [_] (routing/edit! (comp/any->app report-instance) edit-form entity-id))}
                                       cell-text)
                                (dom/span nil cell-text)))))
                  columns)
            (when (seq row-actions)
              (dom/td {:data-rad-type "row-actions"}
                      (mapv (fn [{:keys [label action disabled?]}]
                              (dom/button {:data-rad-type "row-action"
                                           :disabled      (boolean (?! disabled? report-instance row-props))
                                           :onClick       (fn [_] (when action (action report-instance row-props)))}
                                          (?! label report-instance row-props)))
                            row-actions))))))

;; -- Multimethod registrations -----------------------------------------------

(defmethod rr/render-report :default [report-instance options]
  (let [title   (ro/title options)
        loading? (report/loading? report-instance)]
    (dom/div {:data-rad-type   "report"
              :data-rad-report (str (some-> (ro/row-pk options) ao/qualified-key))}
             (when loading?
               (dom/div {:data-rad-type "busy"} "Loading..."))
             (when title
               (dom/h1 {:data-rad-type "report-title"} (?! title report-instance)))
             (rr/render-controls report-instance options)
             (rr/render-body report-instance options)
             (rr/render-footer report-instance options))))

(defmethod rr/render-body :default [report-instance options]
  (let [rows (report/current-rows report-instance)]
    (dom/table {:data-rad-type "report-table"}
               (render-column-headings report-instance options)
               (dom/tbody {:data-rad-type "report-body"}
                          (if (seq rows)
                            (into []
                                  (map-indexed (fn [idx row-props]
                                                 (rr/render-row report-instance options
                                                                (with-meta row-props {:row-index idx}))))
                                  rows)
                            (dom/tr {:data-rad-type "empty-message"}
                                    (dom/td {:colSpan (count (ro/columns options))}
                                            "No rows")))))))

(defmethod rr/render-row :default [report-instance options row-props]
  (render-report-row report-instance options row-props
                     (or (:row-index (meta row-props)) 0)))

(defmethod rr/render-controls :default [report-instance options]
  (render-report-controls report-instance options))

(defmethod rr/render-header :default [report-instance options]
  (render-column-headings report-instance options))

(defn- render-pagination
  "Render pagination controls when the report is paginated."
  [report-instance _options]
  (let [current (report/current-page report-instance)
        total   (report/page-count report-instance)]
    (when (> total 1)
      (dom/div {:data-rad-type "pagination"}
               (dom/button {:data-rad-type "pagination-prev"
                            :disabled      (= current 1)
                            :onClick       (fn [_] (report/prior-page! report-instance))}
                           "Prev")
               (dom/span {:data-rad-type "pagination-info"}
                         (str "Page " current " of " total))
               (dom/button {:data-rad-type "pagination-next"
                            :disabled      (>= current total)
                            :onClick       (fn [_] (report/next-page! report-instance))}
                           "Next")))))

(defmethod rr/render-footer :default [report-instance options]
  (render-pagination report-instance options))
