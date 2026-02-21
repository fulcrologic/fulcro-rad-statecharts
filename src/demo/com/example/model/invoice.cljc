(ns com.example.model.invoice
  (:require
   #?(:clj [com.example.components.database-queries :as queries])
   [com.fulcrologic.rad.attributes :refer [defattr]]
   [com.fulcrologic.rad.attributes-options :as ao]
   [com.fulcrologic.rad.form-options :as fo]
   [com.fulcrologic.rad.report-options :as ro]
   [com.fulcrologic.rad.type-support.date-time :as dt]
   [com.fulcrologic.rad.type-support.decimal :as math]
   [com.wsscode.pathom.connect :as pc]))

(defattr id :invoice/id :uuid
  {ao/identity? true
   ao/schema    :production})

(defattr date :invoice/date :instant
  {fo/field-style             :date-at-noon
   ::dt/default-time-zone     "America/Los_Angeles"
   ao/required?               true
   ao/identities              #{:invoice/id}
   ao/schema                  :production})

(defattr line-items :invoice/line-items :ref
  {ao/target                                                       :line-item/id
   :com.fulcrologic.rad.database-adapters.datomic/attribute-schema {:db/isComponent true}
   ao/required?                                                    true
   ao/valid?                                                       (fn [v props k]
                                                                     (and
                                                                      (vector? v)
                                                                      (pos? (count v))))
   fo/validation-message                                           "You must have a least one line item."
   ao/cardinality                                                  :many
   ao/identities                                                   #{:invoice/id}
   ao/schema                                                       :production})

(defattr total :invoice/total :decimal
  {ao/identities      #{:invoice/id}
   ao/schema          :production
   ro/column-formatter (fn [_report v _row _attr] (math/numeric->currency-str v))
   ao/read-only?      true})

(defattr customer :invoice/customer :ref
  {ao/cardinality :one
   ao/target      :account/id
   ao/required?   true
   ao/identities  #{:invoice/id}
   ao/schema      :production})

#?(:clj
   (pc/defresolver customer-id [env {:invoice/keys [id]}]
     {::pc/input  #{:invoice/id}
      ::pc/output [:account/id]}
     {:account/id (queries/get-invoice-customer-id env id)}))

(defattr all-invoices :invoice/all-invoices :ref
  {ao/target     :invoice/id
   ao/pc-output  [{:invoice/all-invoices [:invoice/id]}]
   ao/pc-resolve (fn [{:keys [query-params] :as env} _]
                   #?(:clj
                      {:invoice/all-invoices (queries/get-all-invoices env query-params)}))})

(def attributes [id date line-items customer all-invoices total])

#?(:clj
   (def resolvers [customer-id]))
