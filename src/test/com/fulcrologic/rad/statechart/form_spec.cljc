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
    [com.fulcrologic.statecharts.data-model.operations :as ops]
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

;; --- Issue 3: :after-load trigger ---

(defattr al-id :al/id :uuid {ao/identity? true})
(defattr al-name :al/name :string {})

(defn test-after-load
  "Test trigger that returns an assign op and captures the env it received."
  [env data form-ident]
  [(ops/assign :after-load-ran true)
   (ops/assign :trigger-env env)])

(form/defsc-form AfterLoadForm [this props]
  {fo/attributes [al-name]
   fo/id         al-id
   sfo/triggers  {:after-load test-after-load}})

(defn nil-after-load
  "Test trigger that returns nil."
  [_env _data _form-ident]
  nil)

(form/defsc-form NilTriggerForm [this props]
  {fo/attributes [al-name]
   fo/id         al-id
   sfo/triggers  {:after-load nil-after-load}})

(defn empty-after-load
  "Test trigger that returns an empty vector."
  [_env _data _form-ident]
  [])

(form/defsc-form EmptyTriggerForm [this props]
  {fo/attributes [al-name]
   fo/id         al-id
   sfo/triggers  {:after-load empty-after-load}})

(defattr al2-id :al2/id :uuid {ao/identity? true})
(defattr al2-name :al2/name :string {})

(form/defsc-form NoAfterLoadForm [this props]
  {fo/attributes [al2-name]
   fo/id         al2-id})

(defn make-form-data
  "Builds the `data` map that `on-loaded-expr` expects for a given form class and ident."
  [form-class form-ident props]
  {:fulcro/state-map {form-ident props}
   :fulcro/actors    {:actor/form {:component (rc/class->registry-key form-class)
                                   :ident     form-ident}}})

(specification "on-loaded-expr :after-load trigger"
  (component "with trigger"
    (let [test-env   {:my-env-key "test-value"}
          form-ident [:al/id #uuid "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"]
          data       (make-form-data AfterLoadForm form-ident
                       {:al/id (second form-ident) :al/name "test"})
          ops        (form/on-loaded-expr test-env data nil nil)
          assign-ops (filterv #(= (:op %) :assign) ops)
          after-load-assign (first (filter #(contains? (:data %) :after-load-ran) assign-ops))
          env-assign (first (filter #(contains? (:data %) :trigger-env) assign-ops))]
      (assertions
        "includes the after-load assign op with correct value"
        (:data after-load-assign) => {:after-load-ran true}
        "passes the env argument through to the trigger"
        (:data env-assign) => {:trigger-env test-env}
        "includes base ops from the standard on-loaded logic"
        (pos? (count (filterv #(= (:op %) :fulcro/apply-action) ops))) => true)))

  (component "without trigger"
    (let [form-ident [:al2/id #uuid "cccccccc-cccc-cccc-cccc-cccccccccccc"]
          data       (make-form-data NoAfterLoadForm form-ident
                       {:al2/id (second form-ident) :al2/name "test"})
          ops        (form/on-loaded-expr {} data nil nil)
          assign-ops (filterv #(= (:op %) :assign) ops)]
      (assertions
        "returns base ops from the standard on-loaded logic"
        (pos? (count (filterv #(= (:op %) :fulcro/apply-action) ops))) => true
        "does not include after-load-specific assign ops"
        (first (filter #(contains? (:data %) :after-load-ran) assign-ops)) => nil)))

  (component "trigger returns nil"
    (let [form-ident [:al/id #uuid "dddddddd-dddd-dddd-dddd-dddddddddddd"]
          data       (make-form-data NilTriggerForm form-ident
                       {:al/id (second form-ident) :al/name "nil-test"})
          ops        (form/on-loaded-expr {} data nil nil)]
      (assertions
        "still includes the base on-loaded ops"
        (pos? (count (filterv #(= (:op %) :fulcro/apply-action) ops))) => true)))

  (component "trigger returns empty vector"
    (let [form-ident [:al/id #uuid "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"]
          data       (make-form-data EmptyTriggerForm form-ident
                       {:al/id (second form-ident) :al/name "empty-test"})
          ops        (form/on-loaded-expr {} data nil nil)]
      (assertions
        "still includes the base on-loaded ops"
        (pos? (count (filterv #(= (:op %) :fulcro/apply-action) ops))) => true))))

