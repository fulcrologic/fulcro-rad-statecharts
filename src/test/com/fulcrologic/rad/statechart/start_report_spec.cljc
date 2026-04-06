(ns com.fulcrologic.rad.statechart.start-report-spec
  "Tests for machine-key resolution and chart registration in start-report!,
   start-server-paginated-report!, and start-incrementally-loaded-report!.
   Verifies all 4 cases: default, sfro/statechart-id priority, sfro/statechart keyword,
   and sfro/statechart inline map."
  (:require
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.rad.statechart.incrementally-loaded-report :as ilr]
    [com.fulcrologic.rad.statechart.report :as report]
    [com.fulcrologic.rad.statechart.server-paginated-report :as spr]
    [com.fulcrologic.rad.statechart.session :as sc.session]
    [com.fulcrologic.statecharts.integration.fulcro :as scf]
    [com.fulcrologic.statecharts.integration.fulcro.routing-options :as sfro]
    [fulcro-spec.core :refer [=> assertions component specification]]))

;; ===== Helpers =====

(def fake-ident [:report/id ::test-report])

(defn capturing-start-fn!
  "Returns [capture-atom, start-fn]. The start-fn records all calls to scf/start!
   so we can inspect the :machine key passed."
  []
  (let [calls (atom [])]
    [calls (fn [_app opts] (swap! calls conj opts) nil)]))

(defn capturing-register-fn!
  "Returns [capture-atom, register-fn]. Records all calls to scf/register-statechart!."
  []
  (let [calls (atom [])]
    [calls (fn [_app key chart] (swap! calls conj {:key key :chart chart}) nil)]))

(defn make-component-options-fn
  "Returns a function that mimics comp/component-options for the given overrides map.
   When called with (f class key), returns (get overrides key)."
  [overrides]
  (fn
    ([_class key] (get overrides key))
    ([_class key & more-keys]
     ;; multi-arity: return first found
     (some #(get overrides %) (cons key more-keys)))))

;; ===== start-report! =====

(specification "start-report! machine-key resolution"
  (component "default (no sfro/statechart or sfro/statechart-id set)"
    (let [[start-calls start-fn] (capturing-start-fn!)
          [reg-calls reg-fn] (capturing-register-fn!)]
      (with-redefs [comp/ident                   (fn [_ _] fake-ident)
                    comp/component-options        (make-component-options-fn {})
                    sc.session/ident->session-id  (fn [_] "session-1")
                    scf/current-configuration     (fn [_ _] nil)
                    scf/register-statechart!      reg-fn
                    scf/start!                    start-fn]
        (report/start-report! :app :report-class {})
        (assertions
          "uses the default ::report-chart machine key"
          (:machine (first @start-calls)) => ::report/report-chart
          "registers the built-in report-statechart"
          (:key (first @reg-calls)) => ::report/report-chart
          (:chart (first @reg-calls)) => report/report-statechart))))

  (component "sfro/statechart-id takes priority over sfro/statechart"
    (let [[start-calls start-fn] (capturing-start-fn!)
          [reg-calls reg-fn] (capturing-register-fn!)]
      (with-redefs [comp/ident                   (fn [_ _] fake-ident)
                    comp/component-options        (make-component-options-fn
                                                    {sfro/statechart-id :my/custom-chart-id
                                                     sfro/statechart    :some/other-chart})
                    sc.session/ident->session-id  (fn [_] "session-1")
                    scf/current-configuration     (fn [_ _] nil)
                    scf/register-statechart!      reg-fn
                    scf/start!                    start-fn]
        (report/start-report! :app :report-class {})
        (assertions
          "uses the statechart-id as the machine key"
          (:machine (first @start-calls)) => :my/custom-chart-id))))

  (component "sfro/statechart as keyword"
    (let [[start-calls start-fn] (capturing-start-fn!)
          [reg-calls reg-fn] (capturing-register-fn!)]
      (with-redefs [comp/ident                   (fn [_ _] fake-ident)
                    comp/component-options        (make-component-options-fn
                                                    {sfro/statechart :my/keyword-chart})
                    sc.session/ident->session-id  (fn [_] "session-1")
                    scf/current-configuration     (fn [_ _] nil)
                    scf/register-statechart!      reg-fn
                    scf/start!                    start-fn]
        (report/start-report! :app :report-class {})
        (assertions
          "uses the keyword as the machine key"
          (:machine (first @start-calls)) => :my/keyword-chart))))

  (component "sfro/statechart as inline map"
    (let [[start-calls start-fn] (capturing-start-fn!)
          [reg-calls reg-fn] (capturing-register-fn!)
          inline-chart {:id :my-inline-chart :states {}}]
      (with-redefs [comp/ident                   (fn [_ _] fake-ident)
                    comp/component-options        (make-component-options-fn
                                                    {sfro/statechart inline-chart})
                    sc.session/ident->session-id  (fn [_] "session-1")
                    scf/current-configuration     (fn [_ _] nil)
                    scf/register-statechart!      reg-fn
                    scf/start!                    start-fn]
        (report/start-report! :app :report-class {})
        (assertions
          "falls through to the default machine key"
          (:machine (first @start-calls)) => ::report/report-chart
          "registers the user's inline map (not the built-in chart)"
          (:chart (first @reg-calls)) => inline-chart)))))

;; ===== start-server-paginated-report! =====

(specification "start-server-paginated-report! machine-key resolution"
  (component "default (no sfro/statechart or sfro/statechart-id set)"
    (let [[start-calls start-fn] (capturing-start-fn!)
          [reg-calls reg-fn] (capturing-register-fn!)]
      (with-redefs [comp/ident                   (fn [_ _] fake-ident)
                    comp/component-options        (make-component-options-fn {})
                    sc.session/ident->session-id  (fn [_] "session-1")
                    scf/current-configuration     (fn [_ _] nil)
                    scf/register-statechart!      reg-fn
                    scf/start!                    start-fn]
        (spr/start-server-paginated-report! :app :report-class {})
        (assertions
          "uses the default ::server-paginated-report-chart machine key"
          (:machine (first @start-calls)) => ::spr/server-paginated-report-chart
          "registers the built-in server-paginated-report-statechart"
          (:key (first @reg-calls)) => ::spr/server-paginated-report-chart
          (:chart (first @reg-calls)) => spr/server-paginated-report-statechart))))

  (component "sfro/statechart-id takes priority over sfro/statechart"
    (let [[start-calls start-fn] (capturing-start-fn!)
          [reg-calls reg-fn] (capturing-register-fn!)]
      (with-redefs [comp/ident                   (fn [_ _] fake-ident)
                    comp/component-options        (make-component-options-fn
                                                    {sfro/statechart-id :my/custom-sp-chart
                                                     sfro/statechart    :some/other-chart})
                    sc.session/ident->session-id  (fn [_] "session-1")
                    scf/current-configuration     (fn [_ _] nil)
                    scf/register-statechart!      reg-fn
                    scf/start!                    start-fn]
        (spr/start-server-paginated-report! :app :report-class {})
        (assertions
          "uses the statechart-id as the machine key"
          (:machine (first @start-calls)) => :my/custom-sp-chart))))

  (component "sfro/statechart as keyword"
    (let [[start-calls start-fn] (capturing-start-fn!)
          [reg-calls reg-fn] (capturing-register-fn!)]
      (with-redefs [comp/ident                   (fn [_ _] fake-ident)
                    comp/component-options        (make-component-options-fn
                                                    {sfro/statechart :my/sp-keyword-chart})
                    sc.session/ident->session-id  (fn [_] "session-1")
                    scf/current-configuration     (fn [_ _] nil)
                    scf/register-statechart!      reg-fn
                    scf/start!                    start-fn]
        (spr/start-server-paginated-report! :app :report-class {})
        (assertions
          "uses the keyword as the machine key"
          (:machine (first @start-calls)) => :my/sp-keyword-chart))))

  (component "sfro/statechart as inline map"
    (let [[start-calls start-fn] (capturing-start-fn!)
          [reg-calls reg-fn] (capturing-register-fn!)
          inline-chart {:id :my-sp-inline :states {}}]
      (with-redefs [comp/ident                   (fn [_ _] fake-ident)
                    comp/component-options        (make-component-options-fn
                                                    {sfro/statechart inline-chart})
                    sc.session/ident->session-id  (fn [_] "session-1")
                    scf/current-configuration     (fn [_ _] nil)
                    scf/register-statechart!      reg-fn
                    scf/start!                    start-fn]
        (spr/start-server-paginated-report! :app :report-class {})
        (assertions
          "falls through to the default machine key"
          (:machine (first @start-calls)) => ::spr/server-paginated-report-chart
          "registers the user's inline map (not the built-in chart)"
          (:chart (first @reg-calls)) => inline-chart)))))

;; ===== start-incrementally-loaded-report! =====

(specification "start-incrementally-loaded-report! machine-key resolution"
  (component "default (no sfro/statechart or sfro/statechart-id set)"
    (let [[start-calls start-fn] (capturing-start-fn!)
          [reg-calls reg-fn] (capturing-register-fn!)]
      (with-redefs [comp/ident                   (fn [_ _] fake-ident)
                    comp/component-options        (make-component-options-fn {})
                    sc.session/ident->session-id  (fn [_] "session-1")
                    scf/current-configuration     (fn [_ _] nil)
                    scf/register-statechart!      reg-fn
                    scf/start!                    start-fn]
        (ilr/start-incrementally-loaded-report! :app :report-class {})
        (assertions
          "uses the default ::incrementally-loaded-report-chart machine key"
          (:machine (first @start-calls)) => ::ilr/incrementally-loaded-report-chart
          "registers the built-in incrementally-loaded-report-statechart"
          (:key (first @reg-calls)) => ::ilr/incrementally-loaded-report-chart
          (:chart (first @reg-calls)) => ilr/incrementally-loaded-report-statechart))))

  (component "sfro/statechart-id takes priority over sfro/statechart"
    (let [[start-calls start-fn] (capturing-start-fn!)
          [reg-calls reg-fn] (capturing-register-fn!)]
      (with-redefs [comp/ident                   (fn [_ _] fake-ident)
                    comp/component-options        (make-component-options-fn
                                                    {sfro/statechart-id :my/custom-il-chart
                                                     sfro/statechart    :some/other-chart})
                    sc.session/ident->session-id  (fn [_] "session-1")
                    scf/current-configuration     (fn [_ _] nil)
                    scf/register-statechart!      reg-fn
                    scf/start!                    start-fn]
        (ilr/start-incrementally-loaded-report! :app :report-class {})
        (assertions
          "uses the statechart-id as the machine key"
          (:machine (first @start-calls)) => :my/custom-il-chart))))

  (component "sfro/statechart as keyword"
    (let [[start-calls start-fn] (capturing-start-fn!)
          [reg-calls reg-fn] (capturing-register-fn!)]
      (with-redefs [comp/ident                   (fn [_ _] fake-ident)
                    comp/component-options        (make-component-options-fn
                                                    {sfro/statechart :my/il-keyword-chart})
                    sc.session/ident->session-id  (fn [_] "session-1")
                    scf/current-configuration     (fn [_ _] nil)
                    scf/register-statechart!      reg-fn
                    scf/start!                    start-fn]
        (ilr/start-incrementally-loaded-report! :app :report-class {})
        (assertions
          "uses the keyword as the machine key"
          (:machine (first @start-calls)) => :my/il-keyword-chart))))

  (component "sfro/statechart as inline map"
    (let [[start-calls start-fn] (capturing-start-fn!)
          [reg-calls reg-fn] (capturing-register-fn!)
          inline-chart {:id :my-il-inline :states {}}]
      (with-redefs [comp/ident                   (fn [_ _] fake-ident)
                    comp/component-options        (make-component-options-fn
                                                    {sfro/statechart inline-chart})
                    sc.session/ident->session-id  (fn [_] "session-1")
                    scf/current-configuration     (fn [_ _] nil)
                    scf/register-statechart!      reg-fn
                    scf/start!                    start-fn]
        (ilr/start-incrementally-loaded-report! :app :report-class {})
        (assertions
          "falls through to the default machine key"
          (:machine (first @start-calls)) => ::ilr/incrementally-loaded-report-chart
          "registers the user's inline map (not the built-in chart)"
          (:chart (first @reg-calls)) => inline-chart)))))
