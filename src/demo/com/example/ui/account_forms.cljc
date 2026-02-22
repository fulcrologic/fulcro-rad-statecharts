(ns com.example.ui.account-forms
  "Account form with address subforms. Ported from fulcro-rad-demo with
   blob/avatar/file/tag fields removed and converted to statecharts."
  (:require
   [com.example.model.account :as account]
   [com.example.ui.address-forms :refer [AddressForm]]
   [com.fulcrologic.fulcro.components :as comp]
   [com.fulcrologic.rad.statechart.form :as form]
   [com.fulcrologic.rad.form-options :as fo]))

(form/defsc-form AccountForm [this props]
  {fo/id             account/id
   fo/attributes     [account/name
                      account/role
                      account/email
                      account/active?
                      account/primary-address
                      account/addresses]
   fo/default-values {:account/active?         true
                      :account/primary-address {}
                      :account/addresses       [{}]}
   fo/route-prefix   "account"
   fo/title          "Edit Account"
   fo/subforms       {:account/primary-address {fo/ui                                         AddressForm
                                                fo/title                                      "Primary Address"
                                                :com.fulcrologic.rad.form/autocreate-on-load? true}
                      :account/addresses       {fo/ui            AddressForm
                                                fo/title         "Additional Addresses"
                                                fo/sort-children (fn [addresses] (sort-by :address/zip addresses))
                                                fo/can-delete?   (fn [parent _] (< 1 (count (:account/addresses (comp/props parent)))))
                                                fo/can-add?      (fn [parent _]
                                                                   (and
                                                                    (< (count (:account/addresses (comp/props parent))) 4)
                                                                    :prepend))}}})

(form/defsc-form BriefAccountForm [this props]
  {fo/id             account/id
   fo/attributes     [account/name
                      account/role
                      account/email
                      account/active?]
   fo/default-values {:account/active? true}})
