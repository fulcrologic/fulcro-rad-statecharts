(ns com.example.ui.line-item-forms
  "Line item subform for invoices. Ported from fulcro-rad-demo with
   UISM on-change converted to statechart ops-returning convention."
  (:require
   [com.example.model :as model]
   [com.example.model.line-item :as line-item]
   [com.example.ui.item-forms :as item-forms]
   [com.fulcrologic.fulcro.components :as comp]
   [com.fulcrologic.fulcro.raw.components :as rc]
   [com.fulcrologic.rad.form :as form]
   [com.fulcrologic.rad.form-options :as fo]
   [com.fulcrologic.rad.picker-options :as po]
   [com.fulcrologic.rad.type-support.decimal :as math]
   [com.fulcrologic.statecharts.integration.fulcro.operations :as fops]))

(defn add-subtotal*
  "Compute line-item subtotal from quantity and quoted-price."
  [{:line-item/keys [quantity quoted-price] :as item}]
  (assoc item :line-item/subtotal (math/* quantity quoted-price)))

(form/defsc-form LineItemForm [this props]
  {fo/id            line-item/id
   fo/attributes    [line-item/category line-item/item line-item/quantity line-item/quoted-price line-item/subtotal]
   fo/validator     model/all-attribute-validator
   fo/route-prefix  "line-item"
   fo/title         "Line Items"
   fo/layout        [[:line-item/category :line-item/item :line-item/quantity :line-item/quoted-price :line-item/subtotal]]
   fo/triggers      {:derive-fields (fn [new-form-tree] (add-subtotal* new-form-tree))
                     :on-change     (fn [env data form-ident qualified-key old-value new-value]
                                      (let [state-map (:fulcro/state-map data)]
                                        (case qualified-key
                                          :line-item/category
                                          (let [app        (:fulcro/app env)
                                                form-class (some-> (comp/ident->any app form-ident) rc/component-type)
                                                props      (get-in state-map form-ident)]
                                            (when (and app form-class)
                                              (po/load-options! app form-class props line-item/item))
                                            [(fops/apply-action assoc-in (conj form-ident :line-item/item) nil)])

                                          :line-item/item
                                          (let [item-price (get-in state-map (conj new-value :item/price))]
                                            [(fops/apply-action assoc-in (conj form-ident :line-item/quoted-price) item-price)])

                                          nil)))}
   fo/field-styles  {:line-item/item     :pick-one
                     :line-item/category :pick-one}
   fo/field-options {:line-item/category {po/query-key       :category/all-categories
                                          po/query-component (rc/nc [:category/id :category/label])
                                          po/options-xform   (fn [_ options]
                                                               (mapv
                                                                (fn [{:category/keys [id label]}]
                                                                  {:text (str label) :value [:category/id id]})
                                                                (sort-by :category/label options)))
                                          po/cache-time-ms   10000}
                     :line-item/item     {po/query-key        :item/all-items
                                          po/cache-key        (fn [_ {:line-item/keys [id] :as props}]
                                                                (keyword "item-list" (or
                                                                                      (some-> props :line-item/category :category/id str)
                                                                                      "all")))
                                          po/query-component  item-forms/ItemForm
                                          po/options-xform    (fn [_ options]
                                                                (mapv
                                                                 (fn [{:item/keys [id name price]}]
                                                                   {:text (str name " - " (math/numeric->currency-str price)) :value [:item/id id]})
                                                                 (sort-by :item/name options)))
                                          po/query-parameters (fn [_app _form-class props]
                                                                (let [category (get props :line-item/category)]
                                                                  category))
                                          po/cache-time-ms    60000}}})
