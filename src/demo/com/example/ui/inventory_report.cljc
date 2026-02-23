(ns com.example.ui.inventory-report
  "Item inventory report — lists all items with category, price, and stock."
  (:require
    [clojure.string :as str]
    [com.example.model.category :as category]
    [com.example.model.item :as item]
    [com.example.ui.item-forms :refer [ItemForm]]
    [com.fulcrologic.rad.report-options :as ro]
    [com.fulcrologic.rad.statechart.control :as control]
    [com.fulcrologic.rad.statechart.report :as report]
    [com.fulcrologic.rad.type-support.decimal :as math]))

(report/defsc-report InventoryReport [this props]
  {ro/title               "Inventory Report"
   ro/source-attribute    :item/all-items
   ro/row-pk              item/id
   ro/columns             [item/item-name category/label item/price item/in-stock]

   ro/column-headings     {:item/name      "Item"
                           :category/label "Category"
                           :item/price     "Price"
                           :item/in-stock  "In Stock"}

   ro/column-formatters   {:item/price     (fn [_report v _row _attr]
                                             (math/numeric->currency-str v))
                           :category/label (fn [_report _v row _attr]
                                             (get-in row [:item/category :category/label]))}

   ro/row-visible?        (fn [filter-params {:item/keys [name] :as row}]
                            (let [{::keys [search]} filter-params]
                              (or (str/blank? search)
                                (str/includes? (str/lower-case (or name ""))
                                  (str/lower-case search)))))

   ro/controls            {::search  {:type     :string
                                      :label    "Search"
                                      :local?   true
                                      :onChange (fn [this _] (report/filter-rows! this))}
                           ::refresh {:type   :button
                                      :label  "Refresh"
                                      :local? true
                                      :action (fn [this] (control/run! this))}}

   ro/control-layout      {:action-buttons [::refresh]
                           :inputs         [[::search]]}

   ro/row-query-inclusion [{:item/category [:category/id :category/label]}]

   ro/initial-sort-params {:sort-by          :item/name
                           :sortable-columns #{:item/name :category/label :item/price :item/in-stock}
                           :ascending?       true}

   ro/paginate?           true
   ro/page-size           20
   ro/form-links          {:item/name ItemForm}
   ro/run-on-mount?       true
   ro/route               "inventory"})
