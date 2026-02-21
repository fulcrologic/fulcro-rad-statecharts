(ns com.fulcrologic.rad.rendering.headless.report
  "Headless report layout renderers. Registers defmethod implementations for
   `rr/render-report`, `rr/render-body`, `rr/render-row`, `rr/render-controls`,
   `rr/render-header`, and `rr/render-footer`. Uses HTML table elements.
   Plain HTML only, no CSS."
  (:require
   #?(:cljs [com.fulcrologic.fulcro.dom :as dom]
      :clj  [com.fulcrologic.fulcro.dom-server :as dom])
   [com.fulcrologic.rad.attributes-options :as ao]
   [com.fulcrologic.rad.control :as control]
   [com.fulcrologic.rad.options-util :refer [?!]]
   [com.fulcrologic.rad.report :as report]
   [com.fulcrologic.rad.report-options :as ro]
   [com.fulcrologic.rad.report-render :as rr]))

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
                               (dom/th {:data-rad-type   "column-header"
                                        :data-rad-column (str (ao/qualified-key column))
                                        :title           (or help "")
                                        :onClick         (fn [_]
                                                           #?(:cljs (report/sort-rows! report-instance column)
                                                              :clj  nil))}
                                       label))
                             heading-descriptors)))))

(defn- render-report-row
  "Render a single table row for a report."
  [report-instance options row-props]
  (let [columns    (ro/columns options)
        row-actions (ro/row-actions options)]
    (dom/tr {:data-rad-type "report-row"}
            (mapv (fn [col-attr]
                    (let [qualified-key (ao/qualified-key col-attr)]
                      (dom/td {:data-rad-type   "report-cell"
                               :data-rad-column (str qualified-key)}
                              (str (report/formatted-column-value report-instance row-props col-attr)))))
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
             (rr/render-body report-instance options))))

(defmethod rr/render-body :default [report-instance options]
  (let [rows (report/current-rows report-instance)]
    (dom/table {:data-rad-type "report-table"}
               (render-column-headings report-instance options)
               (dom/tbody {:data-rad-type "report-body"}
                          (if (seq rows)
                            (mapv (fn [row-props]
                                    (rr/render-row report-instance options row-props))
                                  rows)
                            (dom/tr {:data-rad-type "empty-message"}
                                    (dom/td {:colSpan (count (ro/columns options))}
                                            "No rows")))))))

(defmethod rr/render-row :default [report-instance options row-props]
  (render-report-row report-instance options row-props))

(defmethod rr/render-controls :default [report-instance options]
  (render-report-controls report-instance options))

(defmethod rr/render-header :default [report-instance options]
  (render-column-headings report-instance options))

(defmethod rr/render-footer :default [_report-instance _options]
  nil)
