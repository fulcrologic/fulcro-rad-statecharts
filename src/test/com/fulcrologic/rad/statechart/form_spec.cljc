(ns com.fulcrologic.rad.statechart.form-spec
  (:require
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.raw.components :as rc]
    [com.fulcrologic.rad.attributes :refer [defattr]]
    [com.fulcrologic.rad.attributes-options :as ao]
    [com.fulcrologic.rad.form-options :as fo]
    [com.fulcrologic.rad.statechart.form :as form]
    [com.fulcrologic.rad.statechart.form-options :as sfo]
    [fulcro-spec.core :refer [assertions component specification]]))

(def default-street "111 Main")
(def default-email "nobody@example.net")

(defattr person-id :person/id :uuid {ao/identity? true})
(defattr person-name :person/name :string {})
(defattr account-id :account/id :uuid {ao/identity? true})
(defattr spouse :account/spouse :ref {ao/cardinality :one
                                      ao/target      :person/id})
(defattr email :account/email :string {ao/required?     true
                                       fo/default-value default-email})
(defattr addresses :account/addresses :ref {ao/target      :address/id
                                            ao/cardinality :many})
(defattr address-id :address/id :uuid {ao/identity? true})
(defattr street :address/street :string {ao/required? true})

(defsc AddressForm [_ _]
  {fo/attributes     [street]
   fo/default-values {:address/street default-street}
   ao/target         :address/id
   fo/id             address-id
   :query            [:mocked/query]})

(defsc PersonForm [_ _]
  {fo/attributes [person-name]
   fo/id         person-id})

(defsc AccountForm [_ _]
  {fo/attributes     [email addresses spouse]
   fo/id             account-id
   fo/default-values {:account/spouse {}}
   fo/subforms       {:account/addresses {fo/ui AddressForm}
                      :account/spouse    {fo/ui PersonForm}}})

(specification "attributes->form-query"
  (component "Single-level query conversion"
    (let [eql       (form/form-options->form-query (comp/component-options AddressForm))
          eql-items (set eql)]
      (assertions
        "Returns an EQL vector"
        (vector? eql) => true
        "Includes the ID of the form in the query"
        (contains? eql-items :address/id) => true
        "No longer includes the ASM table (removed during statecharts conversion)"
        (contains? eql-items [:com.fulcrologic.fulcro.ui-state-machines/asm-id '_]) => false
        "Includes the form config join"
        (contains? eql-items fs/form-config-join) => true
        "Includes the scalar attribute keys"
        (contains? eql-items :address/street) => true)))
  (component "Nested query conversion"
    (let [eql       (form/form-options->form-query (comp/component-options AccountForm))
          eql-items (set eql)]
      (assertions
        "Includes a join to the proper sub-query"
        (contains? eql-items {:account/addresses [:mocked/query]}) => true))))

(specification "New entity initial state"
  (component "simple entity"
    (let [id (tempid/tempid)
          v  (form/default-state AddressForm id)]
      (assertions
        "Includes the new ID as the ID of the entity"
        (get v :address/id) => id
        "Adds any default fields to the entity"
        (get v :address/street) => default-street)))
  (component "nested entity"
    (let [id (tempid/tempid)
          v  (form/default-state AccountForm id)]
      (assertions
        "Includes the new ID as the ID of the entity"
        (get v :account/id) => id
        "Adds default values from attribute declaration to the entity"
        (get v :account/email) => default-email
        "Includes a map with a new tempid for to-one entities that have a default value"
        (some-> (get-in v [:account/spouse :person/id]) (tempid/tempid?)) => true
        "Includes an empty vector for any to-many relation that has no default"
        (get v :account/addresses) => []))))

(defattr test-id :test/id :uuid {ao/identity? true})
(defattr test-name :test/name :string {ao/identities #{:test/id} ao/required? true})
(defattr test-note :test/note :string {ao/identities #{:test/id}})
(defattr test-marketing :test/marketing? :boolean {ao/identities #{:test/id}})
(defattr test-agree :test/agree? :boolean {ao/identities #{:test/id} ao/required? true})
(defattr test-children :test/children :ref {ao/identities #{:child/id} ao/target :child/id ao/cardinality :many})
(defattr child-id :child/id :uuid {ao/identity? true})
(defattr child-a :child/a :string {ao/identities #{:child/id} ao/required? true})
(defattr child-b :child/b :string {ao/identities #{:child/id}})
(defattr child-node :child/node :ref {ao/identities #{:child/id} ao/target :subchild/id})
(defattr subchild-id :subchild/id :uuid {ao/identity? true})
(defattr subchild-x :subchild/x :string {ao/identities #{:subchild/id}})
(defattr subchild-y :subchild/y :string {ao/identities #{:subchild/id} ao/required? true})

(form/defsc-form SubChildForm [this props]
  {fo/attributes [subchild-x subchild-y]
   fo/id         subchild-id})
(form/defsc-form ChildForm [this props]
  {fo/attributes [child-a child-b child-node]
   fo/id         child-id
   fo/subforms   {:child/node {fo/ui SubChildForm}}})
(form/defsc-form TestForm [this props]
  {fo/attributes [test-name test-note test-marketing test-agree test-children]
   fo/id         test-id
   fo/subforms   {:test/children {fo/ui ChildForm}}})

(specification "find-fields"
  (let [fields (form/find-fields TestForm #(#{:ref :boolean} (get % ao/type)))]
    (assertions
      "Finds all of the fields (recursively) that match the predicate."
      fields => #{:test/children :test/marketing? :test/agree? :child/node})))

(specification "optional-fields"
  (let [fields (form/optional-fields TestForm)]
    (assertions
      "Finds all of the fields (recursively) that are used in forms but are not required by the data model."
      ;; Booleans??? What if we just want to leave false == nil?
      fields => #{:test/note :test/children :test/marketing? :child/b :child/node :subchild/x})))

;; --- Issue 1: derive-fields on form load ---

(defn test-derive-fields [props]
  (assoc props :derived/field "computed"))

(defattr df-id :df/id :uuid {ao/identity? true})
(defattr df-name :df/name :string {})

(form/defsc-form DeriveFieldsForm [this props]
  {fo/attributes [df-name]
   fo/id         df-id
   sfo/triggers  {:derive-fields test-derive-fields}})

(defn- has-derive-fields-op?
  "Returns true if `ops` contains an apply-action op that passes `f` to update-tree*."
  [ops f]
  (boolean
    (some (fn [{:keys [op] :as m}]
            (and (= op :fulcro/apply-action)
                 (= (:f m) form/update-tree*)
                 (some #{f} (:args m))))
      ops)))

(specification "on-loaded-expr with derive-fields trigger"
  (let [form-ident [:df/id #uuid "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"]
        form-key   (rc/class->registry-key DeriveFieldsForm)
        data       {:fulcro/state-map {form-ident {:df/id   (second form-ident)
                                                   :df/name "test"}}
                    :fulcro/actors    {:actor/form {:component form-key
                                                   :ident     form-ident}}}
        ops        (form/on-loaded-expr {} data nil nil)]
    (assertions
      "produces a non-empty ops vector"
      (pos? (count ops)) => true
      "includes an apply-action op that passes the configured derive-fields fn to update-tree*"
      (has-derive-fields-op? ops test-derive-fields) => true)))

(specification "on-loaded-expr without derive-fields trigger"
  (let [form-ident [:address/id #uuid "cccccccc-cccc-cccc-cccc-cccccccccccc"]
        form-key   (rc/class->registry-key AddressForm)
        data       {:fulcro/state-map {form-ident {:address/id     (second form-ident)
                                                   :address/street "test"}}
                    :fulcro/actors    {:actor/form {:component form-key
                                                   :ident     form-ident}}}
        ops        (form/on-loaded-expr {} data nil nil)]
    (assertions
      "produces ops successfully"
      (pos? (count ops)) => true
      "omits derive-fields ops when no trigger is configured"
      (has-derive-fields-op? ops test-derive-fields) => false)))

(defn test-master-derive-fields [props]
  (assoc props :derived/master "master-computed"))

(defattr mdf-id :mdf/id :uuid {ao/identity? true})
(defattr mdf-name :mdf/name :string {})
(defattr mdf-children :mdf/children :ref {ao/target :df/id ao/cardinality :many})

(form/defsc-form MasterDeriveFieldsForm [this props]
  {fo/attributes [mdf-name mdf-children]
   fo/id         mdf-id
   fo/subforms   {:mdf/children {fo/ui DeriveFieldsForm}}
   sfo/triggers  {:derive-fields test-master-derive-fields}})

(specification "derive-fields-ops propagates master-form derive-fields when editing a subform"
  (let [derive-fields-ops @(resolve 'com.fulcrologic.rad.statechart.form/derive-fields-ops)
        master-key   (rc/class->registry-key MasterDeriveFieldsForm)
        master-ident [:mdf/id #uuid "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"]
        sub-key      (rc/class->registry-key DeriveFieldsForm)
        sub-ident    [:df/id #uuid "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"]
        data         {:fulcro/actors {:actor/form {:component master-key
                                                   :ident     master-ident}}}
        event-data   {:form-key sub-key :form-ident sub-ident}
        ops          (derive-fields-ops data event-data)]
    (assertions
      "fires the subform's own derive-fields"
      (has-derive-fields-op? ops test-derive-fields) => true
      "fires the master form's derive-fields"
      (has-derive-fields-op? ops test-master-derive-fields) => true)))

