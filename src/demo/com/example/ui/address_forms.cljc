(ns com.example.ui.address-forms
  "Address subform for account editing."
  (:require
   [com.example.model.address :as address]
   [com.fulcrologic.rad.form :as form]
   [com.fulcrologic.rad.form-options :as fo]))

(form/defsc-form AddressForm [this props]
  {fo/id         address/id
   fo/attributes [address/street address/city address/state address/zip]
   fo/route-prefix "address"
   fo/title      "Edit Address"
   fo/layout     [[:address/street]
                  [:address/city :address/state :address/zip]]})
