(ns com.example.ui.ui
  "Demo Root component with statecharts routing.
   Replaces the original fulcro-rad-demo ui.cljc which used dynamic routing."
  (:require
    #?(:clj  [com.fulcrologic.fulcro.dom-server :as dom]
       :cljs [com.fulcrologic.fulcro.dom :as dom])
    [com.example.ui.account-forms :refer [AccountForm]]
    [com.example.ui.invoice-forms :refer [InvoiceForm AccountInvoices]]
    [com.example.ui.item-forms :refer [ItemForm]]
    [com.example.ui.inventory-report :refer [InventoryReport]]
    [com.example.ui.invoice-report :refer [InvoiceReport]]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.rad.ids :refer [new-uuid]]
    [com.fulcrologic.rad.statechart.form :as form]
    [com.fulcrologic.rad.statechart.report :as report]
    [com.fulcrologic.statecharts :as sc]
    [com.fulcrologic.statecharts.chart :refer [statechart]]
    [com.fulcrologic.statecharts.integration.fulcro :as scf]
    [com.fulcrologic.statecharts.integration.fulcro.routing :as scr]))

;; ---------------------------------------------------------------------------
;; Landing Page
;; ---------------------------------------------------------------------------

(defsc LandingPage [this props]
  {:query         [:ui/placeholder]
   :ident         (fn [] [:component/id ::LandingPage])
   :initial-state {:ui/placeholder true}}
  (dom/div {:data-rad-type "landing-page"}
    (dom/h2 nil "Welcome to the RAD Statecharts Demo")
    (dom/p nil "Use the navigation above to browse accounts, inventory, and invoices.")))

;; ---------------------------------------------------------------------------
;; Routes — displays the current subroute via statecharts routing
;; ---------------------------------------------------------------------------

(defsc Routes [this props]
  {:query                   [:ui/placeholder]
   :preserve-dynamic-query? true
   :initial-state           {}
   :ident                   (fn [] [:component/id ::Routes])}
  (dom/div {:data-rad-type "route-container"}
    (scr/ui-current-subroute this comp/factory)))

(def ui-routes (comp/factory Routes))

;; ---------------------------------------------------------------------------
;; Routing statechart
;; ---------------------------------------------------------------------------

(def routing-chart
  "Main routing statechart for the demo application.
   Uses `report-state` and `form-state` helpers for forms/reports,
   and `rstate` for the landing page."
  (statechart {:initial :state/route-root}
    (scr/routing-regions
      (scr/routes {:id :state/root :routing/root `Routes}

        (scr/rstate {:route/target `LandingPage})

        ;; Reports
        (report/report-route-state {:route/target InventoryReport})
        (report/report-route-state {:route/target InvoiceReport})
        (report/report-route-state {:route/target AccountInvoices
                                    :route/params #{:account/id}})

        ;; Forms
        (form/form-route-state {:route/target AccountForm
                                :route/params #{:account/id}})
        (form/form-route-state {:route/target ItemForm
                                :route/params #{:item/id}})
        (form/form-route-state {:route/target InvoiceForm
                                :route/params #{:invoice/id}})))))

;; ---------------------------------------------------------------------------
;; Root component
;; ---------------------------------------------------------------------------

(defn nav-button
  "Renders a navigation button with data attributes for headless testing."
  [label on-click]
  (dom/button {:data-rad-type "nav-button"
               :data-label    label
               :onClick       on-click}
    label))

(defsc Root [this {::app/keys [active-remotes]
                   :ui/keys   [ready? routes]}]
  {:query         [:ui/ready?
                   {:ui/routes (comp/get-query Routes)}
                   [::sc/session-id '_]
                   ::app/active-remotes]
   :initial-state {:ui/ready? false
                   :ui/routes {}}}
  (let [busy?          (seq active-remotes)
        routing-states (scf/current-configuration this scr/session-id)
        route-denied?  (boolean (contains? routing-states :routing-info/open))]
    (if ready?
      (dom/div {:data-rad-type "app-root"}
        (dom/nav {:data-rad-type "nav-bar"}
          (dom/span {:data-rad-type "app-title"} "RAD Demo")

          (nav-button "New Account"
            (fn [] (form/create! this AccountForm)))

          (nav-button "Inventory"
            (fn [] (scr/route-to! this InventoryReport)))
          (nav-button "New Item"
            (fn [] (form/create! this ItemForm)))

          (nav-button "Invoices"
            (fn [] (scr/route-to! this InvoiceReport)))
          (nav-button "New Invoice"
            (fn [] (form/create! this InvoiceForm)))

          (nav-button "Acct 101 Invoices"
            (fn [] (scr/route-to! this AccountInvoices {:account/id (new-uuid 101)})))

          (when busy?
            (dom/span {:data-rad-type "busy-indicator"} "Loading...")))

        (when route-denied?
          (dom/div {:data-rad-type "route-denied-modal"}
            (dom/p nil "You have unsaved changes.")
            (dom/button {:data-rad-type "route-denied-action"
                         :data-label    "Cancel"
                         :onClick       (fn [] (scr/abandon-route-change! this))}
              "Cancel")
            (dom/button {:data-rad-type "route-denied-action"
                         :data-label    "Continue"
                         :onClick       (fn [] (scr/force-continue-routing! this))}
              "Continue and lose changes")))

        (dom/div {:data-rad-type "main-content"}
          (ui-routes routes)))

      (dom/div {:data-rad-type "loading"} "Loading..."))))
