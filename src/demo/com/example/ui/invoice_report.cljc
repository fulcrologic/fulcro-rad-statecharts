(ns com.example.ui.invoice-report
  "Invoice report — lists all invoices with customer, date, and total."
  (:require
    [com.example.model.account :as account]
    [com.example.model.invoice :as invoice]
    [com.fulcrologic.rad.report-options :as ro]
    [com.fulcrologic.rad.statechart.control :as control]
    [com.fulcrologic.rad.statechart.report :as report]
    [com.fulcrologic.rad.type-support.date-time :as dt]
    [com.fulcrologic.rad.type-support.decimal :as math]))

(report/defsc-report InvoiceReport [this props]
  {ro/title               "Invoice Report"
   ro/source-attribute    :invoice/all-invoices
   ro/row-pk              invoice/id
   ro/columns             [account/name invoice/date invoice/total]

   ro/column-headings     {:account/name  "Customer"
                           :invoice/date  "Date"
                           :invoice/total "Total"}

   ro/column-formatters   {:invoice/total (fn [_report v _row _attr]
                                            (math/numeric->currency-str v))
                           :invoice/date  (fn [_report v _row _attr]
                                            (when v
                                              (dt/inst->human-readable-date v)))}

   ro/controls            {::refresh {:type   :button
                                      :label  "Refresh"
                                      :local? true
                                      :action (fn [this] (control/run! this))}}

   ro/control-layout      {:action-buttons [::refresh]}

   ro/initial-sort-params {:sort-by          :invoice/date
                           :sortable-columns #{:account/name :invoice/date :invoice/total}
                           :ascending?       false}

   ro/paginate?           true
   ro/page-size           20
   ro/run-on-mount?       true
   ro/route               "invoices"})
