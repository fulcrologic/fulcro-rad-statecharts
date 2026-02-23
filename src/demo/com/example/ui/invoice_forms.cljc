(ns com.example.ui.invoice-forms
  "Invoice form with line items and customer picker. Ported from fulcro-rad-demo
   with statecharts lifecycle (no UISM, no dynamic routing)."
  (:require
    [clojure.string :as str]
    [com.example.model :as model]
    [com.example.model.invoice :as invoice]
    [com.example.ui.account-forms :refer [BriefAccountForm]]
    [com.example.ui.line-item-forms :refer [LineItemForm]]
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [com.fulcrologic.fulcro.components :refer [defsc]]
    [com.fulcrologic.rad.form-options :as fo]
    [com.fulcrologic.rad.picker-options :as po]
    [com.fulcrologic.rad.report-options :as ro]
    [com.fulcrologic.rad.statechart.form :as form]
    [com.fulcrologic.rad.statechart.form-options :as sfo]
    [com.fulcrologic.rad.statechart.report :as report]
    [com.fulcrologic.rad.type-support.date-time :as datetime]
    [com.fulcrologic.rad.type-support.decimal :as math]))

(def invoice-validator (fs/make-validator (fn [form field]
                                            (let [value (get form field)]
                                              (case field
                                                :invoice/line-items (> (count value) 0)
                                                (= :valid (model/all-attribute-validator form field)))))))

(defsc AccountQuery [_ _]
  {:query [:account/id :account/name :account/email]
   :ident :account/id})

(defn sum-subtotals*
  "Compute the invoice total from line item subtotals."
  [{:invoice/keys [line-items] :as invoice}]
  (assoc invoice :invoice/total
                 (reduce
                   (fn [t {:line-item/keys [subtotal]}]
                     (math/+ t subtotal))
                   (math/zero)
                   line-items)))

(form/defsc-form InvoiceForm [this props]
  {fo/id             invoice/id
   fo/attributes     [invoice/customer invoice/date invoice/line-items invoice/total]
   fo/default-values {:invoice/date (datetime/now)}
   fo/validator      invoice-validator
   fo/layout         [[:invoice/customer :invoice/date]
                      [:invoice/line-items]
                      [:invoice/total]]
   fo/field-styles   {:invoice/customer :pick-one}
   fo/field-options  {:invoice/customer {po/form            BriefAccountForm
                                         fo/title           (fn [_i {:account/keys [id]}]
                                                              (if (tempid/tempid? id)
                                                                "New Account"
                                                                "Edit Account"))
                                         po/quick-create    (fn [v] {:account/id        (tempid/tempid)
                                                                     :account/email     (str/lower-case (str v "@example.com"))
                                                                     :time-zone/zone-id :time-zone.zone-id/America-Los_Angeles
                                                                     :account/active?   true
                                                                     :account/name      v})
                                         po/allow-create?   true
                                         po/allow-edit?     true
                                         po/query-key       :account/all-accounts
                                         po/query-component AccountQuery
                                         po/options-xform   (fn [_ options] (mapv
                                                                              (fn [{:account/keys [id name email]}]
                                                                                {:text (str name ", " email) :value [:account/id id]})
                                                                              (sort-by :account/name options)))
                                         po/cache-time-ms   30000}}
   fo/subforms       {:invoice/line-items {fo/ui          LineItemForm
                                           fo/can-delete? (fn [_ _] true)
                                           fo/can-add?    (fn [_ _] true)}}
   sfo/triggers      {:derive-fields (fn [new-form-tree] (sum-subtotals* new-form-tree))}
   fo/route-prefix   "invoice"
   fo/title          (fn [_ {:invoice/keys [id]}]
                       (if (tempid/tempid? id)
                         (str "New Invoice")
                         (str "Invoice " id)))})

(def AccountInvoices
  (report/report ::AccountInvoices
    {ro/title            "Customer Invoices"
     ro/source-attribute :account/invoices
     ro/row-pk           invoice/id
     ro/columns          [invoice/id invoice/date invoice/total]
     ro/column-headings  {:invoice/id "Invoice Number"}
     ro/form-links       {:invoice/id InvoiceForm}
     ro/controls         {:account/id {:type   :uuid
                                       :local? true
                                       :label  "Account"}}
     ro/run-on-mount?    true
     ro/route            "account-invoices"}))
