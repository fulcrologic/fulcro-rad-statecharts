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
    [com.fulcrologic.statecharts.integration.fulcro.operations :as fops]
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

(def after-load-result (atom nil))

(defn test-after-load [env data form-ident]
  (reset! after-load-result {:called? true :form-ident form-ident})
  [(ops/assign :after-load-ran true)])

(defattr al-id :al/id :uuid {ao/identity? true})
(defattr al-name :al/name :string {})

(form/defsc-form AfterLoadForm [this props]
  {fo/attributes [al-name]
   fo/id         al-id
   sfo/triggers  {:after-load test-after-load}})

(specification "on-loaded-expr invokes :after-load trigger"
  (let [form-ident [:al/id #uuid "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"]
        form-key   (rc/class->registry-key AfterLoadForm)
        data       {:fulcro/state-map {form-ident {:al/id   (second form-ident)
                                                   :al/name "test"}}
                    :fulcro/actors    {:actor/form {:component form-key
                                                   :ident     form-ident}}}
        _          (reset! after-load-result nil)
        ops        (form/on-loaded-expr {} data nil nil)
        assign-ops (filterv #(= (:op %) :assign) ops)]
    (assertions
      "calls the :after-load trigger"
      (:called? @after-load-result) => true
      "passes the form ident to the trigger"
      (:form-ident @after-load-result) => form-ident
      "merges ops returned by :after-load into the result"
      (boolean (some #(and (= (:op %) :assign) (contains? (:data %) :after-load-ran)) ops)) => true)))

(defattr al2-id :al2/id :uuid {ao/identity? true})
(defattr al2-name :al2/name :string {})

(form/defsc-form NoAfterLoadForm [this props]
  {fo/attributes [al2-name]
   fo/id         al2-id})

(specification "on-loaded-expr works without :after-load trigger"
  (let [form-ident [:al2/id #uuid "cccccccc-cccc-cccc-cccc-cccccccccccc"]
        form-key   (rc/class->registry-key NoAfterLoadForm)
        data       {:fulcro/state-map {form-ident {:al2/id   (second form-ident)
                                                   :al2/name "test"}}
                    :fulcro/actors    {:actor/form {:component form-key
                                                   :ident     form-ident}}}
        ops        (form/on-loaded-expr {} data nil nil)]
    (assertions
      "returns ops without errors when no :after-load trigger"
      (vector? ops) => true
      "does not include assign ops from after-load"
      (boolean (some #(and (= (:op %) :assign) (contains? (:data %) :after-load-ran)) ops)) => false)))

;; --- Issues 12 & 13: Side effects as ops + URL update after create save ---

(defn- apply-action-ops
  "Extract all :fulcro/apply-action ops from an ops vector."
  [ops]
  (filterv #(= (:op %) :fulcro/apply-action) ops))

(defn- mark-pristine-op?
  "Returns true if the given op is a mark-pristine (entity->pristine*) apply-action."
  [op]
  (and (= (:op op) :fulcro/apply-action)
       (= (:f op) fs/entity->pristine*)))

(defn- has-mark-pristine-op?
  "Returns true if ops contain a mark-pristine apply-action."
  [ops]
  (boolean (some mark-pristine-op? ops)))

(defn- non-pristine-apply-action-ops
  "Returns apply-action ops that are NOT mark-pristine (i.e. routing, transactions, etc.)."
  [ops]
  (filterv #(and (= (:op %) :fulcro/apply-action)
                 (not (mark-pristine-op? %)))
    ops))

(defattr saved-id :saved/id :uuid {ao/identity? true})
(defattr saved-name :saved/name :string {})

(def saved-trigger-result (atom nil))

(defn test-saved-trigger [env data form-ident]
  (reset! saved-trigger-result {:called? true :form-ident form-ident})
  [(ops/assign :saved-trigger-ran true)])

(form/defsc-form SavedForm [this props]
  {fo/attributes [saved-name]
   fo/id         saved-id
   sfo/triggers  {:saved test-saved-trigger}})

(form/defsc-form SavedFormNoTrigger [this props]
  {fo/attributes [saved-name]
   fo/id         saved-id})

(specification "on-saved-expr — side effects as ops (issue 13)"
  (component "with on-saved transaction"
    (let [form-ident [:saved/id #uuid "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"]
          form-key   (rc/class->registry-key SavedFormNoTrigger)
          data       {:fulcro/state-map {}
                      :fulcro/actors    {:actor/form {:component form-key
                                                      :ident     form-ident}}
                      :options          {:on-saved [(list 'some-mutation {})]}}
          mock-app   {:mock true}
          env        {:fulcro/app mock-app}
          ops        (form/on-saved-expr env data nil nil)
          aa-ops     (apply-action-ops ops)]
      (assertions
        "includes apply-action ops for mark-pristine AND on-saved transaction"
        (count aa-ops) => 2
        "returns a vector of ops"
        (vector? ops) => true)))

  (component "without on-saved transaction"
    (let [form-ident [:saved/id #uuid "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"]
          form-key   (rc/class->registry-key SavedFormNoTrigger)
          data       {:fulcro/state-map {}
                      :fulcro/actors    {:actor/form {:component form-key
                                                      :ident     form-ident}}
                      :options          {}}
          ops        (form/on-saved-expr {} data nil nil)
          aa-ops     (apply-action-ops ops)]
      (assertions
        "includes only mark-pristine apply-action op"
        (count aa-ops) => 1)))

  (component "with :saved trigger"
    (let [form-ident [:saved/id #uuid "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"]
          form-key   (rc/class->registry-key SavedForm)
          data       {:fulcro/state-map {}
                      :fulcro/actors    {:actor/form {:component form-key
                                                      :ident     form-ident}}}
          _          (reset! saved-trigger-result nil)
          ops        (form/on-saved-expr {} data nil nil)]
      (assertions
        "calls the :saved trigger"
        (:called? @saved-trigger-result) => true
        "passes form ident to trigger"
        (:form-ident @saved-trigger-result) => form-ident
        "includes trigger ops in result"
        (boolean (some #(and (= (:op %) :assign) (contains? (:data %) :saved-trigger-ran)) ops)) => true))))

(specification "on-saved-expr — URL update after create (issue 12)"
  (component "when form was created and is not embedded"
    (let [real-id    #uuid "dddddddd-dddd-dddd-dddd-dddddddddddd"
          form-ident [:saved/id real-id]
          form-key   (rc/class->registry-key SavedFormNoTrigger)
          data       {:fulcro/state-map                   {}
                      :fulcro/actors                      {:actor/form {:component form-key
                                                                        :ident     form-ident}}
                      :com.fulcrologic.rad.form/create?    true
                      :options                             {}}
          mock-app   {:mock true}
          env        {:fulcro/app mock-app}
          ops        (form/on-saved-expr env data nil nil)]
      (assertions
        "includes a mark-pristine op"
        (has-mark-pristine-op? ops) => true
        "includes a routing/URL-update op beyond mark-pristine"
        (boolean (seq (non-pristine-apply-action-ops ops))) => true)))

  (component "when form was edited (not a create)"
    (let [form-ident [:saved/id #uuid "dddddddd-dddd-dddd-dddd-dddddddddddd"]
          form-key   (rc/class->registry-key SavedFormNoTrigger)
          data       {:fulcro/state-map {}
                      :fulcro/actors    {:actor/form {:component form-key
                                                      :ident     form-ident}}
                      :options          {}}
          ops        (form/on-saved-expr {} data nil nil)]
      (assertions
        "includes a mark-pristine op"
        (has-mark-pristine-op? ops) => true
        "does not include any routing op (no URL update needed)"
        (empty? (non-pristine-apply-action-ops ops)) => true)))

  (component "when form is embedded"
    (let [form-ident [:saved/id #uuid "dddddddd-dddd-dddd-dddd-dddddddddddd"]
          form-key   (rc/class->registry-key SavedFormNoTrigger)
          data       {:fulcro/state-map                   {}
                      :fulcro/actors                      {:actor/form {:component form-key
                                                                        :ident     form-ident}}
                      :com.fulcrologic.rad.form/create?    true
                      :options                             {:embedded? true}}
          ops        (form/on-saved-expr {} data nil nil)]
      (assertions
        "includes a mark-pristine op"
        (has-mark-pristine-op? ops) => true
        "does not include any routing op (embedded forms don't route)"
        (empty? (non-pristine-apply-action-ops ops)) => true))))

(specification "on-save-failed-expr — side effects as ops (issue 13)"
  (component "with on-save-failed transaction"
    (let [form-ident [:saved/id #uuid "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"]
          form-key   (rc/class->registry-key SavedFormNoTrigger)
          data       {:fulcro/state-map {}
                      :fulcro/actors    {:actor/form {:component form-key
                                                      :ident     form-ident}}
                      :options          {:on-save-failed [(list 'handle-failure {})]}
                      :_event           {:data {}}}
          mock-app   {:mock true}
          env        {:fulcro/app mock-app}
          ops        (form/on-save-failed-expr env data nil nil)
          aa-ops     (apply-action-ops ops)]
      (assertions
        "returns the on-save-failed transaction as an apply-action op"
        (count aa-ops) => 1
        "returns a vector of ops"
        (vector? ops) => true))))

(specification "leave-form-expr — side effects as ops (issue 13)"
  (component "with on-cancel transaction and default cancel-route"
    (let [form-ident [:saved/id #uuid "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"]
          form-key   (rc/class->registry-key SavedFormNoTrigger)
          data       {:fulcro/state-map {}
                      :fulcro/actors    {:actor/form {:component form-key
                                                      :ident     form-ident}}
                      :options          {:on-cancel [(list 'on-cancel-mutation {})]}}
          mock-app   {:mock true}
          env        {:fulcro/app mock-app}
          ops        (form/leave-form-expr env data nil nil)
          aa-ops     (apply-action-ops ops)]
      (assertions
        "includes pristine->entity, on-cancel transaction, and route-back ops"
        (count aa-ops) => 3
        "returns a vector of ops"
        (vector? ops) => true)))

  (component "with no on-cancel and no app (no side effects)"
    (let [form-ident [:saved/id #uuid "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"]
          form-key   (rc/class->registry-key SavedFormNoTrigger)
          data       {:fulcro/state-map {}
                      :fulcro/actors    {:actor/form {:component form-key
                                                      :ident     form-ident}}
                      :options          {}}
          ops        (form/leave-form-expr {} data nil nil)
          aa-ops     (apply-action-ops ops)]
      (assertions
        "includes only the pristine->entity state restoration op"
        (count aa-ops) => 1))))

(specification "continue-abandoned-route-expr — side effects as ops (issue 13)"
  (let [form-ident [:saved/id #uuid "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"]
        form-key   (rc/class->registry-key SavedFormNoTrigger)
        data       {:fulcro/state-map {}
                    :fulcro/actors    {:actor/form {:component form-key
                                                    :ident     form-ident}}
                    :desired-route    {:form "some-form"}}
        mock-app   {:mock true}
        env        {:fulcro/app mock-app}
        ops        (form/continue-abandoned-route-expr env data nil nil)
        aa-ops     (apply-action-ops ops)]
    (assertions
      "returns ops including pristine->entity and force-continue-routing"
      (count aa-ops) => 2
      "includes the route-denied? alias reset"
      (boolean (some #(= (:op %) :fulcro/assoc-alias) ops)) => true)))

