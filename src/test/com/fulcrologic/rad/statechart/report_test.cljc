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

(defn apply-action-ops
  "Returns the :fulcro/apply-action ops from the given operations vector."
  [ops]
  (filterv #(= :fulcro/apply-action (:op %)) ops))

(specification "process-loaded-data-expr"
  (component "when ro/report-loaded is provided"
    (let [Report (report/report ::LoadedReport
                   {ro/columns          [attr]
                    ro/source-attribute :foo/bar
                    ro/row-pk           attr
                    ro/report-loaded    (fn [sm] (assoc sm ::callback-called true))})
          data   {:fulcro/state-map {}
                  :fulcro/actors {:actor/report {:component Report
                                                 :ident (comp/ident Report {})}}}
          ops         (report/process-loaded-data-expr nil data nil nil)
          action-ops  (apply-action-ops ops)
          callback-op (second action-ops)
          result-sm   ((:f callback-op) {:some :state})]
      (assertions
        "includes two apply-action ops (transforms + report-loaded callback)"
        (count action-ops) => 2
        "invokes the report-loaded callback as the second apply-action"
        (::callback-called result-sm) => true)))

  (component "when ro/report-loaded is not provided"
    (let [Report (report/report ::NoLoadedReport
                   {ro/columns          [attr]
                    ro/source-attribute :foo/bar
                    ro/row-pk           attr})
          data   {:fulcro/state-map {}
                  :fulcro/actors {:actor/report {:component Report
                                                 :ident (comp/ident Report {})}}}
          ops         (report/process-loaded-data-expr nil data nil nil)
          action-ops  (apply-action-ops ops)]
      (assertions
        "includes only the data-transform apply-action (no report-loaded callback)"
        (count action-ops) => 1
        "has no op whose :f triggers a callback marker"
        (some #(::callback-called ((:f %) {})) action-ops) => nil))))

(specification "resume-from-cache-expr"
  (let [data {:fulcro/state-map {}}
        ops  (report/resume-from-cache-expr nil data nil nil)]
    (assertions
      "produces ops for filter/sort/paginate then busy=false"
      (mapv :op ops) => [:fulcro/apply-action :fulcro/assoc-alias]
      "includes an assoc-alias op that sets busy? to false"
      (some #(and (= :fulcro/assoc-alias (:op %))
                  (= false (get-in % [:data :busy?])))
        ops) =fn=> some?)))
