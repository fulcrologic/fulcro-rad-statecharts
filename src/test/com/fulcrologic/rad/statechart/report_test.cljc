(ns com.fulcrologic.rad.statechart.report-test
  (:require
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.rad.attributes :refer [defattr]]
    [com.fulcrologic.rad.attributes-options :as ao]
    [com.fulcrologic.rad.report-options :as ro]
    [com.fulcrologic.rad.statechart.report :as report]
    [com.fulcrologic.statecharts.integration.fulcro.operations :as fops]
    [fulcro-spec.core :refer [=> assertions component specification]]))

(def seen (atom nil))

(defattr attr :object/x :string
  {ao/identity? true})

(report/defsc-report A [this props]
  {ro/columns             [attr]
   ro/source-attribute    :foo/bar
   ro/row-pk              attr
   ro/initialize-ui-props (fn [cls params] (reset! seen [cls params])
                            {:user-add-on 42})})
(report/defsc-report B [this props]
  {ro/columns             [attr]
   ro/source-attribute    :foo/bar
   ro/row-pk              attr
   ro/initialize-ui-props {:user-add-on 43}})

(specification "Initial state (user-provided fn)"
  (component "user-provided fn"
    (let [is (comp/get-initial-state A {:params 1})]
      (assertions
        "Adds ui controls"
        (:ui/controls is) => []
        "Adds ui current-rows"
        (:ui/current-rows is) => []
        "Adds false busy flag"
        (false? (:ui/busy? is)) => true
        "Adds current page"
        (:ui/current-page is) => 1
        "Adds page count"
        (:ui/page-count is) => 1
        "Adds parameters map"
        (:ui/parameters is) => {}
        "User initialize ui props sees the class and the params"
        (= A (first @seen)) => true
        (= {:params 1} (second @seen)) => true
        "Includes user add-ins"
        (:user-add-on is) => 42)))

  (component "user-provided fn"
    (let [is (comp/get-initial-state B {})]
      (assertions
        "Adds ui controls"
        (:ui/controls is) => []
        "Adds ui current-rows"
        (:ui/current-rows is) => []
        "Adds false busy flag"
        (false? (:ui/busy? is)) => true
        "Adds current page"
        (:ui/current-page is) => 1
        "Adds page count"
        (:ui/page-count is) => 1
        "Adds parameters map"
        (:ui/parameters is) => {}
        "Includes user add-ins"
        (:user-add-on is) => 43))))

(specification "report helper"
  (component "literal map in initialize ui props"
    (let [C  (report/report ::C
               {ro/columns             [attr]
                ro/source-attribute    :foo/bar
                ro/row-pk              attr
                ro/initialize-ui-props {:user-add-on 44}})
          is (comp/get-initial-state C {})]
      (assertions
        "Adds ui controls"
        (:ui/controls is) => []
        "Adds ui current-rows"
        (:ui/current-rows is) => []
        "Adds false busy flag"
        (false? (:ui/busy? is)) => true
        "Adds current page"
        (:ui/current-page is) => 1
        "Adds page count"
        (:ui/page-count is) => 1
        "Adds parameters map"
        (:ui/parameters is) => {}
        "Includes user add-ins"
        (:user-add-on is) => 44)))
  (component "fn in initialize ui props"
    (let [D  (report/report ::D
               {ro/columns             [attr]
                ro/source-attribute    :foo/bar
                ro/row-pk              attr
                ro/initialize-ui-props (fn [cls params] {:user-add-on 44
                                                         :seen        [cls params]})})
          is (comp/get-initial-state D {:x 1})]
      (assertions
        "Adds ui controls"
        (:ui/controls is) => []
        "Adds ui current-rows"
        (:ui/current-rows is) => []
        "Adds false busy flag"
        (false? (:ui/busy? is)) => true
        "Adds current page"
        (:ui/current-page is) => 1
        "Adds page count"
        (:ui/page-count is) => 1
        "Adds parameters map"
        (:ui/parameters is) => {}
        "Includes user add-ins"
        (:user-add-on is) => 44
        (:seen is) => [D {:x 1}]))))

;; ===== Expression function tests =====

(def busy-true-op (fops/assoc-alias :busy? true))
(def busy-false-op (fops/assoc-alias :busy? false))

(defn count-apply-action-ops
  "Returns the count of :fulcro/apply-action ops in the given operations vector."
  [ops]
  (count (filterv #(= :fulcro/apply-action (:op %)) ops)))

(specification "process-loaded-data-expr"
  (component "when ro/report-loaded is provided"
    (let [Report (report/report ::LoadedReport
                   {ro/columns          [attr]
                    ro/source-attribute :foo/bar
                    ro/row-pk           attr
                    ro/report-loaded    (fn [sm] (assoc sm ::callback-called true))})
          data   {:fulcro/state-map {}
                  :fulcro/actors   {:actor/report {:component Report
                                                   :ident (comp/ident Report {})}}}
          ops    (report/process-loaded-data-expr nil data nil nil)]
      (assertions
        "includes an apply-action for the report-loaded callback in addition to transforms"
        (count-apply-action-ops ops) => 2)))

  (component "when ro/report-loaded is not provided"
    (let [Report (report/report ::NoLoadedReport
                   {ro/columns          [attr]
                    ro/source-attribute :foo/bar
                    ro/row-pk           attr})
          data   {:fulcro/state-map {}
                  :fulcro/actors   {:actor/report {:component Report
                                                   :ident (comp/ident Report {})}}}
          ops    (report/process-loaded-data-expr nil data nil nil)]
      (assertions
        "includes only the transform apply-action"
        (count-apply-action-ops ops) => 1))))

(specification "resume-from-cache-expr"
  (let [data {:fulcro/state-map {}}
        ops  (report/resume-from-cache-expr nil data nil nil)]
    (assertions
      "does not produce a redundant busy-true operation"
      (some #{busy-true-op} ops) => nil
      "produces a busy-false operation to clear the busy flag"
      (some #{busy-false-op} ops) => busy-false-op
      "returns a non-empty operations vector"
      (pos? (count ops)) => true)))
