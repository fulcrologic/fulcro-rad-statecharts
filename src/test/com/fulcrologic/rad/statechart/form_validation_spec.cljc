(ns com.fulcrologic.rad.statechart.form-validation-spec
  "GitHub #30: attribute validators are enforced on save.

   Forms without `fo/validator` get a default validator built from the attributes of the form and all of its
   subforms (upstream parity). The save guard (`form-valid?` on `:event/save`) is exercised through the real form
   chart: a valid save reaches `:state/saving` (the remote never answers), a blocked one stays in `:state/editing`.
   A CLJ-only section renders the form with the headless plugin to show the field-level `invalid-attribute-value?`
   agrees with the guard."
  (:require
   [clojure.string :as str]
   [com.fulcrologic.fulcro.algorithms.form-state :as fs]
   [com.fulcrologic.fulcro.application :as app]
   [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
   #?(:clj [com.fulcrologic.fulcro.headless :as h])
   #?(:clj [com.fulcrologic.fulcro.headless.hiccup :as hic])
   [com.fulcrologic.rad.attributes :refer [defattr]]
   [com.fulcrologic.rad.attributes-options :as ao]
   [com.fulcrologic.rad.form-options :as fo]
   [com.fulcrologic.rad.rendering.headless.plugin]
   [com.fulcrologic.rad.statechart.application :as rad-app]
   [com.fulcrologic.rad.statechart.form :as form]
   [com.fulcrologic.rad.statechart.session :as sc.session]
   [com.fulcrologic.statecharts.integration.fulcro :as scf]
   [fulcro-spec.core :refer [=> assertions component specification]]))

;; ===== Model =====

(defattr account-id :vaccount/id :uuid
  {ao/identity? true})

(defattr account-name :vaccount/name :string
  {ao/identities #{:vaccount/id}
   ao/required?  true})

(defattr account-email :vaccount/email :string
  {ao/identities #{:vaccount/id}
   ao/valid?     (fn [v _props _k] (and (string? v) (str/includes? v "@")))})

(defattr account-addresses :vaccount/addresses :ref
  {ao/identities  #{:vaccount/id}
   ao/target      :vaddress/id
   ao/cardinality :many})

(defattr address-id :vaddress/id :uuid
  {ao/identity? true})

(defattr address-street :vaddress/street :string
  {ao/identities #{:vaddress/id}
   ao/required?  true})

(form/defsc-form AddressForm [_this _props]
  {fo/id         address-id
   fo/attributes [address-street]})

(form/defsc-form AccountForm [_this _props]
  {fo/id         account-id
   fo/attributes [account-name account-email account-addresses]
   fo/subforms   {:vaccount/addresses {fo/ui AddressForm}}})

(def accept-all-validator
  "A user validator that accepts every field."
  (fs/make-validator (fn [_form _field] true)))

(form/defsc-form AcceptAllForm [_this _props]
  {fo/id         account-id
   fo/attributes [account-name account-email]
   fo/validator  accept-all-validator})

(form/defsc-form OneArityValidatorForm [_this _props]
  {fo/id         account-id
   fo/attributes [account-name account-email]
   fo/validator  (fn [_props] :valid)})

(def two-arity-answer
  "What `TwoArityValidatorForm`'s validator returns. A test sets it."
  (atom :valid))

(form/defsc-form TwoArityValidatorForm [_this _props]
  {fo/id         account-id
   fo/attributes [account-name account-email]
   fo/validator  (fn [_form _field] @two-arity-answer)})

;; ===== Helpers =====

(def account-uuid #uuid "00000000-0000-0000-0000-0000000000a1")
(def address-uuid #uuid "00000000-0000-0000-0000-0000000000b1")

(def valid-account
  "A normalized account (with one address) that passes every attribute validator."
  {:vaccount/id        account-uuid
   :vaccount/name      "Ann"
   :vaccount/email     "ann@example.com"
   :vaccount/addresses [[:vaddress/id address-uuid]]})

(def valid-address
  {:vaddress/id address-uuid :vaddress/street "1 Main St"})

(defn test-app
  "A RAD app with an immediate statechart event loop. The remote never answers, so a save that passes the guard
   stays in `:state/saving`."
  []
  (let [a (rad-app/fulcro-rad-app {:remotes {:remote {:transmit! (fn [_ _] nil)}}})]
    (rad-app/install-statecharts! a {:event-loop? :immediate})
    a))

(defn save-outcome
  "Loads `account` (merged over `valid-account`) and `address` (merged over `valid-address`) into a fresh app, starts
   `Form` on it, delivers the load, sends `:event/save`, and returns the resulting form state: `:state/saving` when
   the save guard passed, `:state/editing` when it blocked the save."
  ([Form account] (save-outcome Form account {}))
  ([Form account address]
   (let [app   (test-app)
         ident [:vaccount/id account-uuid]
         sid   (sc.session/ident->session-id ident)]
     (swap! (::app/state-atom app)
       (fn [s]
         (-> s
           (assoc-in ident (merge valid-account account))
           (assoc-in [:vaddress/id address-uuid] (merge valid-address address)))))
     (form/start-form! app account-uuid Form)
     (scf/send! app sid :event/loaded {})
     (scf/send! app sid :event/save {})
     (let [config (scf/current-configuration app sid)]
       (cond
         (contains? config :state/saving) :state/saving
         (contains? config :state/editing) :state/editing
         :else config)))))

;; ===== Specs =====

(specification "Default form validator (GitHub #30)"
  (assertions
    "is installed when the form has no fo/validator"
    (fn? (comp/component-options AccountForm fo/validator)) => true
    "is replaced by a user fo/validator"
    (identical? accept-all-validator (comp/component-options AcceptAllForm fo/validator)) => true)

  (component "the save guard"
    (assertions
      "lets a valid form save"
      (save-outcome AccountForm {}) => :state/saving
      "blocks a value its attribute's ao/valid? rejects"
      (save-outcome AccountForm {:vaccount/email "not-an-email"}) => :state/editing
      "blocks a required string that is blank"
      (save-outcome AccountForm {:vaccount/name "   "}) => :state/editing
      "blocks an invalid field in a subform"
      (save-outcome AccountForm {} {:vaddress/street ""}) => :state/editing
      "uses a user fo/validator instead of the default"
      (save-outcome AcceptAllForm {:vaccount/email "not-an-email"}) => :state/saving))

  (component "the default validator"
    (let [state (-> {}
                  (assoc-in [:vaccount/id account-uuid] (assoc valid-account :vaccount/email "bad"))
                  (assoc-in [:vaddress/id address-uuid] valid-address)
                  (fs/add-form-config* AccountForm [:vaccount/id account-uuid]))
          props (fs/completed-form-props state AccountForm [:vaccount/id account-uuid])
          v     (comp/component-options AccountForm fo/validator)]
      (assertions
        "judges the whole form (subforms included)"
        (v props) => :invalid
        "judges a single field"
        (v props :vaccount/email) => :invalid
        (v props :vaccount/name) => :valid))))

(specification "Calling fo/validator (GitHub #30, arity)"
  (assertions
    "a 1-arity (fn [props]) validator works in the save guard (for single fields see the CLJ render spec below)"
    (save-outcome OneArityValidatorForm {}) => :state/saving)

  (reset! two-arity-answer :valid)
  (assertions
    "a 2-arity (fn [form field]) validator does not throw in the save guard, and its :valid lets the save through"
    (save-outcome TwoArityValidatorForm {}) => :state/saving)
  (reset! two-arity-answer :invalid)
  (assertions
    "its :invalid blocks the save"
    (save-outcome TwoArityValidatorForm {}) => :state/editing
    "invalid? reports it"
    (form/invalid? TwoArityValidatorForm {}) => true)
  (reset! two-arity-answer :valid))

;; ===== Field level agrees with the save guard (CLJ: headless render) =====

#?(:clj
   (do
     (def ui-account-form (comp/computed-factory AccountForm {:keyfn :vaccount/id}))

     (defsc ValidationRoot [_this {:root/keys [form]}]
       {:query         [{:root/form (comp/get-query AccountForm)}
                        [::app/active-remotes '_]]
        :initial-state {:root/form {}}}
       (when form (ui-account-form form)))

     (def ui-one-arity-form (comp/computed-factory OneArityValidatorForm {:keyfn :vaccount/id}))

     (defsc OneArityRoot [_this {:root/keys [form]}]
       {:query         [{:root/form (comp/get-query OneArityValidatorForm)}
                        [::app/active-remotes '_]]
        :initial-state {:root/form {}}}
       (when form (ui-one-arity-form form)))

     (defn rendered-after-save
       "Renders `Form` (under `Root`) on `account`/`address` with the headless plugin after a save attempt. The remote
        never answers, so a save that passed the guard stays in `:state/saving`. Returns
        `{:state <form configuration> :hiccup <frame>}`."
       [Root Form account address]
       (let [app   (h/build-test-app {:root-class Root
                                      :remotes    {:remote {:transmit! (fn [_ _] nil)}}})
             ident [:vaccount/id account-uuid]
             sid   (sc.session/ident->session-id ident)]
         (rad-app/install-statecharts! app {:event-loop? :immediate})
         (swap! (::app/state-atom app)
           (fn [s]
             (-> s
               (assoc-in ident (merge valid-account account))
               (assoc-in [:vaddress/id address-uuid] (merge valid-address address))
               (assoc :root/form ident))))
         (form/start-form! app account-uuid Form)
         (scf/send! app sid :event/loaded {})
         (scf/send! app sid :event/save {})
         (h/render-frame! app)
         {:state  (scf/current-configuration app sid)
          :hiccup (h/hiccup-frame app)}))

     (defn field-error-keys
       "The qualified keys (as strings) of the fields rendered with a validation error."
       [hiccup]
       (into #{}
         (map #(:data-rad-field (hic/element-attrs %)))
         (hic/find-all hiccup #(= "field-error" (:data-rad-type (hic/element-attrs %))))))

     (specification "After a blocked save, field-level validation agrees with the save guard (GitHub #30)"
       (let [{:keys [state hiccup]} (rendered-after-save ValidationRoot AccountForm {:vaccount/email "nope"} {})]
         (assertions
           "the save is blocked by the ao/valid? failure"
           (contains? state :state/editing) => true
           "the rejected field (and only it) renders as invalid"
           (field-error-keys hiccup) => #{(str :vaccount/email)}))
       (let [{:keys [state hiccup]} (rendered-after-save ValidationRoot AccountForm {:vaccount/name "  "} {})]
         (assertions
           "the save is blocked by the blank required string"
           (contains? state :state/editing) => true
           "the blank field renders as invalid"
           (field-error-keys hiccup) => #{(str :vaccount/name)}))
       (let [{:keys [state hiccup]} (rendered-after-save ValidationRoot AccountForm {} {:vaddress/street ""})]
         (assertions
           "the save is blocked by the subform field"
           (contains? state :state/editing) => true
           "the subform field renders as invalid"
           (field-error-keys hiccup) => #{(str :vaddress/street)})))

     (specification "A 1-arity fo/validator renders fields (GitHub #30, arity)"
       (let [{:keys [hiccup]} (rendered-after-save OneArityRoot OneArityValidatorForm {} {})]
         (assertions
           "every field renders, with no validation error"
           (field-error-keys hiccup) => #{}
           (some? (hic/find-first hiccup #(= (str :vaccount/name) (:data-rad-field (hic/element-attrs %))))) => true)))))
