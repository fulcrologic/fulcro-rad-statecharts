(ns com.example.ui.item-forms
  "Item form and inventory report. Ported from fulcro-rad-demo with
   statecharts lifecycle (no UISM, no dynamic routing)."
  (:require
    [com.example.model.item :as item]
    [com.fulcrologic.fulcro.components :refer [defsc]]
    [com.fulcrologic.rad.form-options :as fo]
    [com.fulcrologic.rad.picker-options :as po]
    [com.fulcrologic.rad.statechart.form :as form]))

(defsc CategoryQuery [_ _]
  {:query [:category/id :category/label]
   :ident :category/id})

(form/defsc-form ItemForm [this props]
  {fo/id            item/id
   fo/attributes    [item/item-name
                     item/category
                     item/description
                     item/in-stock
                     item/price]
   fo/field-styles  {:item/category :pick-one}
   fo/field-options {:item/category {po/query-key       :category/all-categories
                                     po/query-component CategoryQuery
                                     po/options-xform   (fn [_ options]
                                                          (mapv
                                                            (fn [{:category/keys [id label]}]
                                                              {:text (str label) :value [:category/id id]})
                                                            (sort-by :category/label options)))
                                     po/cache-time-ms   30000}}
   fo/route-prefix  "item"
   fo/title         "Edit Item"})
