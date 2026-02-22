(ns com.fulcrologic.rad.statechart.options-validation-spec
  "Tests for compile-time options validation in defsc-form and defsc-report macros.
   These tests exercise the validation functions directly (they are pure CLJ functions
   that throw ex-info on bad inputs)."
  (:require
   [com.fulcrologic.rad.statechart.form :as f]
   [com.fulcrologic.rad.statechart.report :as r]
   [fulcro-spec.core :refer [assertions specification behavior =>]]))

#?(:clj
   (specification "validate-form-options!"
                  (behavior "rejects UISM-specific fo/triggers key"
                            (assertions
                             (f/validate-form-options! {:com.fulcrologic.rad.form/triggers {:on-change identity}})
                             =throws=> clojure.lang.ExceptionInfo))

                  (behavior "rejects UISM-specific fo/machine key"
                            (assertions
                             (f/validate-form-options! {:com.fulcrologic.rad.form/machine :some-machine})
                             =throws=> clojure.lang.ExceptionInfo))

                  (behavior "rejects :will-enter key"
                            (assertions
                             (f/validate-form-options! {:will-enter (fn [& _] nil)})
                             =throws=> clojure.lang.ExceptionInfo))

                  (behavior "rejects :will-leave key"
                            (assertions
                             (f/validate-form-options! {:will-leave (fn [& _] nil)})
                             =throws=> clojure.lang.ExceptionInfo))

                  (behavior "rejects :route-denied key"
                            (assertions
                             (f/validate-form-options! {:route-denied (fn [& _] nil)})
                             =throws=> clojure.lang.ExceptionInfo))

                  (behavior "accepts valid statechart form options"
                            (assertions
                             (f/validate-form-options! {:com.fulcrologic.rad.form/id         :account/id
                                                        :com.fulcrologic.rad.form/attributes  []
                                                        :com.fulcrologic.rad.statechart.form-options/triggers {:on-change (fn [& _])}})
                             => nil))

                  (behavior "accepts empty options map"
                            (assertions
                             (f/validate-form-options! {})
                             => nil))

                  (behavior "error message includes actionable guidance for fo/triggers"
                            (assertions
                             (try
                               (f/validate-form-options! {:com.fulcrologic.rad.form/triggers {}})
                               nil
                               (catch clojure.lang.ExceptionInfo e
                                 (ex-message e)))
                             => "defsc-form compile error: Use sfo/triggers instead of fo/triggers. The statecharts engine uses a different callback signature: (fn [env data form-ident k old new] ops-vec)"))))

#?(:clj
   (specification "validate-report-options!"
                  (behavior "rejects UISM-specific ro/triggers key"
                            (assertions
                             (r/validate-report-options! {:com.fulcrologic.rad.report/triggers {:on-change identity}})
                             =throws=> clojure.lang.ExceptionInfo))

                  (behavior "rejects UISM-specific ro/machine key"
                            (assertions
                             (r/validate-report-options! {:com.fulcrologic.rad.report/machine :some-machine})
                             =throws=> clojure.lang.ExceptionInfo))

                  (behavior "rejects :will-enter key"
                            (assertions
                             (r/validate-report-options! {:will-enter (fn [& _] nil)})
                             =throws=> clojure.lang.ExceptionInfo))

                  (behavior "accepts valid statechart report options"
                            (assertions
                             (r/validate-report-options! {:com.fulcrologic.rad.report/columns         []
                                                          :com.fulcrologic.rad.statechart.report-options/triggers {:on-change (fn [& _])}})
                             => nil))

                  (behavior "accepts empty options map"
                            (assertions
                             (r/validate-report-options! {})
                             => nil))

                  (behavior "error message includes actionable guidance for ro/machine"
                            (assertions
                             (try
                               (r/validate-report-options! {:com.fulcrologic.rad.report/machine :my-machine})
                               nil
                               (catch clojure.lang.ExceptionInfo e
                                 (ex-message e)))
                             => "defsc-report compile error: Use sro/statechart instead of ro/machine. The ro/machine option is for the UISM engine."))))
