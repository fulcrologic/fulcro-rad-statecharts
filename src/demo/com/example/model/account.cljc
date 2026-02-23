(ns com.example.model.account
  (:refer-clojure :exclude [name])
  (:require
    #?(:clj [com.example.components.database-queries :as queries])
    [clojure.string :as str]
    [com.fulcrologic.rad.attributes :as attr :refer [defattr]]
    [com.fulcrologic.rad.attributes-options :as ao]
    [com.fulcrologic.rad.form :as form]
    [com.fulcrologic.rad.form-options :as fo]
    [com.fulcrologic.fulcro.mutations :as m]
    [com.wsscode.pathom.connect :as pc]))

(defattr id :account/id :uuid
  {ao/identity? true
   ao/schema    :production})

(defattr email :account/email :string
  {ao/identities         #{:account/id}
   ao/required?          true
   ao/schema             :production
   :com.fulcrologic.rad.database-adapters.datomic/attribute-schema
   {:db/unique :db.unique/value}
   fo/validation-message "Must use your lower-case first name as the email address name."
   ao/valid?             (fn [v props _]
                           (let [prefix (or
                                          (some-> props
                                            :account/name
                                            (str/split #"\s")
                                            (first)
                                            (str/lower-case))
                                          "")]
                             (str/starts-with? (or v "") prefix)))})

(defattr active? :account/active? :boolean
  {ao/identities    #{:account/id}
   ao/schema        :production
   fo/default-value true})

(defattr password :password/hashed-value :string
  {ao/required?  true
   ao/identities #{:account/id}
   ao/schema     :production})

(defattr password-salt :password/salt :string
  {ao/schema     :production
   ao/identities #{:account/id}
   ao/required?  true})

(defattr password-iterations :password/iterations :int
  {ao/identities #{:account/id}
   ao/schema     :production
   ao/required?  true})

(def account-roles {:account.role/superuser "Superuser"
                    :account.role/user      "Normal User"})

(defattr role :account/role :enum
  {ao/identities        #{:account/id}
   ao/enumerated-values (set (keys account-roles))
   ao/enumerated-labels account-roles
   ao/schema            :production})

(defattr name :account/name :string
  {fo/field-label "Name"
   ao/identities  #{:account/id}
   ao/schema      :production
   ao/required?   true})

(defattr primary-address :account/primary-address :ref
  {ao/target                                                       :address/id
   ao/cardinality                                                  :one
   ao/identities                                                   #{:account/id}
   :com.fulcrologic.rad.database-adapters.datomic/attribute-schema {:db/isComponent true}
   ao/schema                                                       :production})

(defattr addresses :account/addresses :ref
  {ao/target                                                       :address/id
   ao/cardinality                                                  :many
   ao/identities                                                   #{:account/id}
   ao/schema                                                       :production
   :com.fulcrologic.rad.database-adapters.datomic/attribute-schema {:db/isComponent true}})

(defattr all-accounts :account/all-accounts :ref
  {ao/target     :account/id
   ao/pc-output  [{:account/all-accounts [:account/id]}]
   ao/pc-resolve (fn [{:keys [query-params] :as env} _]
                   #?(:clj
                      {:account/all-accounts (queries/get-all-accounts env query-params)}))})

(defattr account-invoices :account/invoices :ref
  {ao/target     :account/id
   ao/pc-output  [{:account/invoices [:invoice/id]}]
   ao/pc-resolve (fn [{:keys [query-params] :as env} _]
                   #?(:clj
                      {:account/invoices (queries/get-customer-invoices env query-params)}))})

(m/defmutation set-account-active [{:account/keys [id active?]}]
  (action [{:keys [state]}]
    (swap! state assoc-in [:account/id id :account/active?] active?))
  (remote [_] true))

#?(:clj
   (pc/defmutation set-account-active-ss [env {:account/keys [id active?]}]
     {::pc/params #{:account/id}
      ::pc/sym    `set-account-active
      ::pc/output [:account/id]}
     (form/save-form* env {::form/id        id
                           ::form/master-pk :account/id
                           ::form/delta     {[:account/id id] {:account/active? {:before (not active?) :after (boolean active?)}}}})))

(def attributes [id name primary-address role email password password-iterations password-salt active?
                 addresses all-accounts account-invoices])

#?(:clj
   (def resolvers [set-account-active-ss]))
