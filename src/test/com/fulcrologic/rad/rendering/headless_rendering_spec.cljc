(ns com.fulcrologic.rad.rendering.headless-rendering-spec
  "Comprehensive tests for the headless rendering plugin. Tests all field types,
   form layout, report layout, controls, subforms, and ref/picker fields.
   All tests run in CLJ using dom-server Element records."
  (:require
   [clojure.test :refer [use-fixtures]]
   [com.fulcrologic.fulcro.algorithms.form-state :as fs]
   [com.fulcrologic.fulcro.application :as app]
   [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
   [com.fulcrologic.fulcro.dom-server :as dom]
   [com.fulcrologic.fulcro.mutations :as m]
   [com.fulcrologic.guardrails.malli.fulcro-spec-helpers :as gsh]
   [com.fulcrologic.rad.attributes :as attr :refer [defattr]]
   [com.fulcrologic.rad.attributes-options :as ao]
   [com.fulcrologic.rad.statechart.control :as control]
   [com.fulcrologic.rad.statechart.form :as form]
   [com.fulcrologic.rad.form-options :as fo]
   [com.fulcrologic.rad.form-render :as fr]
   [com.fulcrologic.rad.options-util :refer [?!]]
   [com.fulcrologic.rad.picker-options :as po]
   [com.fulcrologic.rad.rendering.headless.controls]
   [com.fulcrologic.rad.rendering.headless.field :as hfield]
   [com.fulcrologic.rad.rendering.headless.form]
   [com.fulcrologic.rad.rendering.headless.report]
   [com.fulcrologic.rad.statechart.report :as report]
   [com.fulcrologic.rad.report-options :as ro]
   [com.fulcrologic.rad.report-render :as rr]
   [com.fulcrologic.rad.statechart.routing :as routing]
   [fulcro-spec.core :refer [specification component assertions =>
                             when-mocking!]]))

;; =============================================================================
;; Test Helpers — Element tree traversal
;; =============================================================================

(defn element?
  "Returns true if `x` is a dom-server Element."
  [x]
  (instance? com.fulcrologic.fulcro.dom_server.Element x))

(defn text-node?
  "Returns true if `x` is a dom-server Text or ReactText node."
  [x]
  (or (instance? com.fulcrologic.fulcro.dom_server.Text x)
      (instance? com.fulcrologic.fulcro.dom_server.ReactText x)))

(defn text-content
  "Extract the string from a Text or ReactText node."
  [node]
  (or (:s node) (:text node) ""))

(defn element-text
  "Extract the concatenated text content from an element's children (recursive)."
  [el]
  (cond
    (text-node? el) (text-content el)
    (element? el)   (apply str (mapv element-text (:children el)))
    (string? el)    el
    (vector? el)    (apply str (mapv element-text el))
    :else           ""))

(defn find-all
  "Find all elements in the tree matching `pred`. Returns a flat vector."
  [el pred]
  (cond
    (nil? el) []
    (text-node? el) []
    (element? el)
    (into (if (pred el) [el] [])
          (mapcat #(find-all % pred))
          (:children el))
    (vector? el)
    (into [] (mapcat #(find-all % pred)) el)
    (sequential? el)
    (into [] (mapcat #(find-all % pred)) el)
    :else []))

(defn find-by-attr
  "Find all elements with `data-rad-type` equal to `type-val`."
  [el type-val]
  (find-all el #(= type-val (get-in % [:attrs :data-rad-type]))))

(defn find-by-tag
  "Find all elements with the given tag string."
  [el tag-str]
  (find-all el #(= tag-str (:tag %))))

(defn find-first
  "Find the first element matching `pred`, or nil."
  [el pred]
  (first (find-all el pred)))

(defn attr-val
  "Get an attribute value from an element."
  [el k]
  (get-in el [:attrs k]))

(defn on-change!
  "Invoke the onChange handler of an element with the given value."
  [el value]
  (when-let [handler (get-in el [:attrs :onChange])]
    (handler value)))

(defn on-click!
  "Invoke the onClick handler of an element."
  [el]
  (when-let [handler (get-in el [:attrs :onClick])]
    (handler nil)))

;; =============================================================================
;; Test Attribute / Component Definitions
;; =============================================================================

(defattr account-id :account/id :uuid {ao/identity? true})
(defattr account-name :account/name :string {ao/required? true})
(defattr account-email :account/email :string {})
(defattr account-age :account/age :int {})
(defattr account-score :account/score :long {})
(defattr account-rating :account/rating :double {})
(defattr account-active :account/active? :boolean {})
(defattr account-created :account/created :instant {})
(defattr account-status :account/status :enum
  {ao/enumerated-values [:active :inactive :pending]
   ao/enumerated-labels {:active "Active" :inactive "Inactive" :pending "Pending"}})
(defattr account-balance :account/balance :decimal {})
(defattr account-category :account/category :ref {ao/target :category/id ao/cardinality :one})

(defattr category-id :category/id :uuid {ao/identity? true})
(defattr category-name :category/name :string {})

(defattr address-id :address/id :uuid {ao/identity? true})
(defattr address-street :address/street :string {ao/required? true})

(defsc AddressForm [_ _]
  {fo/attributes [address-street]
   fo/id         address-id
   :query        [:address/id :address/street fs/form-config-join]})

(defsc AccountForm [_ _]
  {fo/attributes [account-name account-email account-age account-active
                  account-status account-balance account-category]
   fo/id         account-id
   fo/subforms   {:account/addresses {fo/ui       AddressForm
                                      fo/can-add? true
                                      fo/can-delete? true}}
   :query        [:account/id :account/name :account/email :account/age
                  :account/active? :account/status :account/balance
                  :account/category {:account/addresses [:address/id :address/street]}
                  fs/form-config-join]})

;; =============================================================================
;; Standard field-context mock for visible, non-invalid fields
;; =============================================================================

(defn mock-field-context
  "Build a standard field-context map for testing."
  [value & {:keys [label visible? invalid? validation-message read-only? omit-label?]
            :or   {label "Test Field" visible? true invalid? false read-only? false omit-label? false}}]
  {:value              value
   :field-label        label
   :visible?           visible?
   :invalid?           invalid?
   :validation-message validation-message
   :read-only?         read-only?
   :omit-label?        omit-label?})

(defn mock-form-instance
  "Create a minimal mock form-instance as a plain map. Since all Fulcro/RAD function
   calls are mocked, this just needs to be passable as an argument."
  [& {:keys [props options] :or {props {} options {}}}]
  {:fulcro$options (merge {fo/id        account-id
                           fo/attributes [account-name account-email]
                           fo/subforms  {}}
                          options)
   :props          {:fulcro$value (merge {:account/id #uuid "00000000-0000-0000-0000-000000000001"} props)}})

(defn mock-env
  "Create a standard rendering env map."
  [form-instance]
  {::form/form-instance form-instance
   ::form/master-form   form-instance})

;; =============================================================================
;; Field Rendering Tests
;; =============================================================================

(specification "render-text-field"
               (component "Visible text field with value"
                          (let [inst (mock-form-instance)]
                            (gsh/when-mocking!
                             (form/field-context env attr) => (mock-field-context "hello" :label "Name")
                             (ao/required? attr) => false

                             (let [el (hfield/render-text-field (mock-env inst) account-name)]
                               (assertions
                                "Renders a form-field wrapper div"
                                (attr-val el :data-rad-type) => "form-field"

                                "Contains an input element"
                                (count (find-by-tag el "input")) => 1

                                "Input has the field value"
                                (attr-val (first (find-by-tag el "input")) :value) => "hello"

                                "Renders a field label"
                                (count (find-by-attr el "field-label")) => 1

                                "Label text is correct"
                                (element-text (first (find-by-attr el "field-label"))) => "Name")))))

               (component "Hidden text field"
                          (let [inst (mock-form-instance)]
                            (gsh/when-mocking!
                             (form/field-context env attr) => (mock-field-context "x" :visible? false)
                             (ao/required? attr) => false

                             (let [el (hfield/render-text-field (mock-env inst) account-name)]
                               (assertions
                                "Returns nil when not visible"
                                el => nil)))))

               (component "Invalid text field"
                          (let [inst (mock-form-instance)]
                            (gsh/when-mocking!
                             (form/field-context env attr) => (mock-field-context "bad" :invalid? true :validation-message "Required")
                             (ao/required? attr) => true

                             (let [el (hfield/render-text-field (mock-env inst) account-name)]
                               (assertions
                                "Renders a validation error"
                                (count (find-by-attr el "field-error")) => 1

                                "Error contains the message"
                                (element-text (first (find-by-attr el "field-error"))) => "Required"

                                "Input has required attribute"
                                (attr-val (first (find-by-tag el "input")) :required) => true)))))

               (component "onChange in CLJ"
                          (let [inst       (mock-form-instance)
                                called-with (atom nil)]
                            (gsh/when-mocking!
                             (form/field-context env attr) => (mock-field-context "old")
                             (ao/required? attr) => false
                             (m/set-string!! form-inst qk & kv-args) => (reset! called-with {:form-inst form-inst :key qk :args (vec kv-args)})

                             (let [el    (hfield/render-text-field (mock-env inst) account-name)
                                   input (first (find-by-tag el "input"))]
                               (on-change! input "new-value")
                               (assertions
                                "Calls set-string!! with :value and the raw value"
                                (:key @called-with) => :account/name
                                (:args @called-with) => [:value "new-value"]))))))

(specification "render-number-field"
               (component "Integer field"
                          (let [inst (mock-form-instance)]
                            (gsh/when-mocking!
                             (form/field-context env attr) => (mock-field-context 42 :label "Age")
                             (ao/required? attr) => false
                             (ao/type attr) => :int

                             (let [el    (hfield/render-number-field (mock-env inst) account-age)
                                   input (first (find-by-tag el "input"))]
                               (assertions
                                "Renders with type=number"
                                (attr-val input :type) => "number"

                                "Value is stringified"
                                (attr-val input :value) => "42")))))

               (component "Double field"
                          (let [inst (mock-form-instance)]
                            (gsh/when-mocking!
                             (form/field-context env attr) => (mock-field-context 3.14 :label "Rating")
                             (ao/required? attr) => false
                             (ao/type attr) => :double

                             (let [el    (hfield/render-number-field (mock-env inst) account-rating)
                                   input (first (find-by-tag el "input"))]
                               (assertions
                                "Has step=any for doubles"
                                (attr-val input :step) => "any")))))

               (component "onChange for int in CLJ"
                          (let [inst       (mock-form-instance)
                                called-with (atom nil)]
                            (gsh/when-mocking!
                             (form/field-context env attr) => (mock-field-context 0)
                             (ao/required? attr) => false
                             (ao/type attr) => :int
                             (m/set-integer!! form-inst qk & kv-args) => (reset! called-with {:key qk :args (vec kv-args)})

                             (let [el    (hfield/render-number-field (mock-env inst) account-age)
                                   input (first (find-by-tag el "input"))]
                               (on-change! input "99")
                               (assertions
                                "Calls set-integer!! for int type"
                                (:key @called-with) => :account/age
                                (:args @called-with) => [:value "99"])))))

               (component "onChange for double in CLJ"
                          (let [inst       (mock-form-instance)
                                called-with (atom nil)]
                            (gsh/when-mocking!
                             (form/field-context env attr) => (mock-field-context 0.0)
                             (ao/required? attr) => false
                             (ao/type attr) => :double
                             (m/set-string!! form-inst qk & kv-args) => (reset! called-with {:key qk :args (vec kv-args)})

                             (let [el    (hfield/render-number-field (mock-env inst) account-rating)
                                   input (first (find-by-tag el "input"))]
                               (on-change! input "3.14")
                               (assertions
                                "Calls set-string!! for double type"
                                (:key @called-with) => :account/rating
                                (:args @called-with) => [:value "3.14"]))))))

(specification "render-boolean-field"
               (component "Checked checkbox"
                          (let [inst (mock-form-instance)]
                            (gsh/when-mocking!
                             (form/field-context env attr) => (mock-field-context true :label "Active?")
                             (ao/required? attr) => false

                             (let [el    (hfield/render-boolean-field (mock-env inst) account-active)
                                   input (first (find-by-tag el "input"))]
                               (assertions
                                "Renders a checkbox"
                                (attr-val input :type) => "checkbox"

                                "Checkbox is checked"
                                (attr-val input :checked) => true)))))

               (component "onChange toggles value in CLJ"
                          (let [inst       (mock-form-instance)
                                called-with (atom nil)]
                            (gsh/when-mocking!
                             (form/field-context env attr) => (mock-field-context true :label "Active?")
                             (ao/required? attr) => false
                             (m/set-value!! form-inst qk val) => (reset! called-with {:key qk :val val})

                             (let [el    (hfield/render-boolean-field (mock-env inst) account-active)
                                   input (first (find-by-tag el "input"))]
                               (on-change! input nil)
                               (assertions
                                "Toggles to false (not true)"
                                (:val @called-with) => false))))))

(specification "render-instant-field"
               (component "Date field rendering"
                          (let [inst (mock-form-instance)]
                            (gsh/when-mocking!
                             (form/field-context env attr) => (mock-field-context "2024-01-15" :label "Created")
                             (ao/required? attr) => false

                             (let [el    (hfield/render-instant-field (mock-env inst) account-created)
                                   input (first (find-by-tag el "input"))]
                               (assertions
                                "Renders with type=date"
                                (attr-val input :type) => "date"

                                "Value is the date string"
                                (attr-val input :value) => "2024-01-15")))))

               (component "onChange in CLJ"
                          (let [inst       (mock-form-instance)
                                called-with (atom nil)]
                            (gsh/when-mocking!
                             (form/field-context env attr) => (mock-field-context "")
                             (ao/required? attr) => false
                             (m/set-string!! form-inst qk & kv-args) => (reset! called-with {:key qk :args (vec kv-args)})

                             (let [el    (hfield/render-instant-field (mock-env inst) account-created)
                                   input (first (find-by-tag el "input"))]
                               (on-change! input "2025-06-01")
                               (assertions
                                "Calls set-string!! with the date value"
                                (:args @called-with) => [:value "2025-06-01"]))))))

(specification "render-enum-field"
               (component "Enum dropdown rendering"
                          (let [inst (mock-form-instance)]
                            (gsh/when-mocking!
                             (form/field-context env attr) => (mock-field-context :active :label "Status")
                             (ao/required? attr) => false
                             (ao/enumerated-values attr) => [:active :inactive :pending]
                             (ao/enumerated-labels attr) => {:active "Active" :inactive "Inactive" :pending "Pending"}
                             (comp/component-options inst) => {fo/enumerated-labels nil}
                             (fo/enumerated-labels opts) => nil

                             (let [el      (hfield/render-enum-field (mock-env inst) account-status)
                                   select  (first (find-by-tag el "select"))
                                   options (find-by-tag el "option")]
                               (assertions
                                "Renders a select element"
                                (some? select) => true

                                "Has empty option + 3 enum options"
                                (count options) => 4

                                "Current value is set"
                                (attr-val select :value) => ":active")))))

               (component "onChange converts to keyword in CLJ"
                          (let [inst       (mock-form-instance)
                                called-with (atom nil)]
                            (gsh/when-mocking!
                             (form/field-context env attr) => (mock-field-context :active)
                             (ao/required? attr) => false
                             (ao/enumerated-values attr) => [:active :inactive]
                             (ao/enumerated-labels attr) => {}
                             (comp/component-options inst) => {fo/enumerated-labels nil}
                             (fo/enumerated-labels opts) => nil
                             (m/set-value!! form-inst qk val) => (reset! called-with {:key qk :val val})

                             (let [el     (hfield/render-enum-field (mock-env inst) account-status)
                                   select (first (find-by-tag el "select"))]
                               (on-change! select ":inactive")
                               (assertions
                                "Converts string to keyword"
                                (:val @called-with) => :inactive))))))

(specification "render-decimal-field"
               (component "Decimal field rendering"
                          (let [inst (mock-form-instance)]
                            (gsh/when-mocking!
                             (form/field-context env attr) => (mock-field-context "123.45" :label "Balance")
                             (ao/required? attr) => false

                             (let [el    (hfield/render-decimal-field (mock-env inst) account-balance)
                                   input (first (find-by-tag el "input"))]
                               (assertions
                                "Renders as number input"
                                (attr-val input :type) => "number"

                                "Has step=any"
                                (attr-val input :step) => "any"

                                "Has the decimal value"
                                (attr-val input :value) => "123.45")))))

               (component "onChange in CLJ"
                          (let [inst       (mock-form-instance)
                                called-with (atom nil)]
                            (gsh/when-mocking!
                             (form/field-context env attr) => (mock-field-context "0")
                             (ao/required? attr) => false
                             (m/set-string!! form-inst qk & kv-args) => (reset! called-with {:key qk :args (vec kv-args)})

                             (let [el    (hfield/render-decimal-field (mock-env inst) account-balance)
                                   input (first (find-by-tag el "input"))]
                               (on-change! input "99.99")
                               (assertions
                                "Calls set-string!! with the decimal value"
                                (:key @called-with) => :account/balance
                                (:args @called-with) => [:value "99.99"]))))))

(specification "render-ref-field"
               (component "Subform ref renders nil"
                          (let [inst (mock-form-instance :options {fo/subforms {:account/category {fo/ui AddressForm}}})]
                            (gsh/when-mocking!
                             (comp/component-options inst) => {fo/subforms {:account/category {fo/ui AddressForm}}}
                             (fo/subforms opts) => {:account/category {fo/ui AddressForm}}
                             (ao/qualified-key attr) => :account/category

                             (let [el (hfield/render-ref-field (mock-env inst) account-category)]
                               (assertions
                                "Returns nil for subform refs"
                                el => nil)))))

               (component "Picker ref with options renders select"
                          (let [inst (mock-form-instance)]
                            (gsh/when-mocking!
                             (comp/component-options inst) => {fo/subforms {}}
                             (fo/subforms opts) => {}
                             (ao/qualified-key attr) => :account/category
                             (form/field-context env attr) => (mock-field-context [:category/id #uuid "00000000-0000-0000-0000-000000000002"] :label "Category")
                             (ao/required? attr) => false
                             (po/current-form-options inst attr) => [{:text "Electronics" :value [:category/id #uuid "00000000-0000-0000-0000-000000000002"]}
                                                                     {:text "Clothing"    :value [:category/id #uuid "00000000-0000-0000-0000-000000000003"]}]

                             (let [el     (hfield/render-ref-field (mock-env inst) account-category)
                                   select (first (find-all el #(= "ref-picker" (get-in % [:attrs :data-rad-type]))))]
                               (assertions
                                "Renders a form-field wrapper"
                                (attr-val el :data-rad-type) => "form-field"

                                "Contains a select with ref-picker type"
                                (some? select) => true

                                "Select has correct data-rad-type"
                                (attr-val select :data-rad-type) => "ref-picker"

                                "Has empty option + 2 picker options"
                                (count (find-by-tag select "option")) => 3)))))

               (component "Ref without options falls back to span"
                          (let [inst (mock-form-instance)]
                            (gsh/when-mocking!
                             (comp/component-options inst) => {fo/subforms {}}
                             (fo/subforms opts) => {}
                             (ao/qualified-key attr) => :account/category
                             (form/field-context env attr) => (mock-field-context [:category/id #uuid "00000000-0000-0000-0000-000000000002"] :label "Category")
                             (ao/required? attr) => false
                             (po/current-form-options inst attr) => nil

                             (let [el   (hfield/render-ref-field (mock-env inst) account-category)
                                   span (first (find-by-attr el "ref-display"))]
                               (assertions
                                "Falls back to a span display"
                                (some? span) => true

                                "Span shows the value"
                                (element-text span) => "[:category/id #uuid \"00000000-0000-0000-0000-000000000002\"]")))))

               (component "Picker onChange in CLJ"
                          (let [inst       (mock-form-instance)
                                called-with (atom nil)]
                            (gsh/when-mocking!
                             (comp/component-options inst) => {fo/subforms {}}
                             (fo/subforms opts) => {}
                             (ao/qualified-key attr) => :account/category
                             (form/field-context env attr) => (mock-field-context nil :label "Category")
                             (ao/required? attr) => false
                             (po/current-form-options inst attr) => [{:text "Electronics" :value [:category/id #uuid "00000000-0000-0000-0000-000000000002"]}]
                             (m/set-value!! form-inst qk val) => (reset! called-with {:key qk :val val})

                             (let [el     (hfield/render-ref-field (mock-env inst) account-category)
                                   select (first (find-all el #(= "ref-picker" (get-in % [:attrs :data-rad-type]))))]
                               (on-change! select "[:category/id #uuid \"00000000-0000-0000-0000-000000000002\"]")
                               (assertions
                                "Parses the ident string and calls set-value!!"
                                (:key @called-with) => :account/category
                                (:val @called-with) => [:category/id #uuid "00000000-0000-0000-0000-000000000002"]))))))

;; =============================================================================
;; Field Wrapper Tests — shared behavior
;; =============================================================================

(specification "Field wrapper behavior"
               (component "Read-only field"
                          (let [inst (mock-form-instance)]
                            (gsh/when-mocking!
                             (form/field-context env attr) => (mock-field-context "locked" :read-only? true)
                             (ao/required? attr) => false

                             (let [el    (hfield/render-text-field (mock-env inst) account-name)
                                   input (first (find-by-tag el "input"))]
                               (assertions
                                "Input is disabled"
                                (attr-val input :disabled) => true

                                "Input is read-only"
                                (attr-val input :readOnly) => true)))))

               (component "Label omission"
                          (let [inst (mock-form-instance)]
                            (gsh/when-mocking!
                             (form/field-context env attr) => (mock-field-context "v" :omit-label? true)
                             (ao/required? attr) => false

                             (let [el (hfield/render-text-field (mock-env inst) account-name)]
                               (assertions
                                "No label is rendered"
                                (count (find-by-attr el "field-label")) => 0))))))

;; =============================================================================
;; Control Rendering Tests
;; =============================================================================

(specification "render-control — button"
               (component "Visible enabled button"
                          (let [action-called (atom false)
                                inst          (reify)]
                            (gsh/when-mocking!
                             (control/component-controls inst) => {:report/run {:label   "Run Report"
                                                                                :action  (fn [_] (reset! action-called true))
                                                                                :visible? true}}

                             (let [el (control/render-control :button :default inst :report/run)]
                               (assertions
                                "Renders a button"
                                (:tag el) => "button"

                                "Has control data attribute"
                                (attr-val el :data-rad-control) => ":report/run"

                                "Is not disabled"
                                (attr-val el :disabled) => false

                                "Has label text"
                                (element-text el) => "Run Report")

                               (on-click! el)
                               (assertions
                                "Clicking invokes the action"
                                @action-called => true)))))

               (component "Hidden button"
                          (let [inst (reify)]
                            (gsh/when-mocking!
                             (control/component-controls inst) => {:report/run {:label   "Run"
                                                                                :visible? false}}

                             (let [el (control/render-control :button :default inst :report/run)]
                               (assertions
                                "Returns nil when not visible"
                                el => nil))))))

(specification "render-control — string input"
               (component "Visible string control"
                          (let [inst       (reify)
                                param-set  (atom nil)]
                            (gsh/when-mocking!
                             (control/component-controls inst) => {:search {:label "Search" :visible? true}}
                             (control/current-value inst k) => "current"
                             (control/set-parameter! inst k v) => (reset! param-set {:key k :val v})

                             (let [el    (control/render-control :string :default inst :search)
                                   input (first (find-by-tag el "input"))]
                               (assertions
                                "Renders an input"
                                (some? input) => true

                                "Input has current value"
                                (attr-val input :value) => "current"

                                "Has control data attribute"
                                (attr-val el :data-rad-control) => ":search")

                               (on-change! input "new-search")
                               (assertions
                                "onChange sets the parameter"
                                (:val @param-set) => "new-search"))))))

(specification "render-control — boolean checkbox"
               (component "Checked boolean control"
                          (let [inst       (reify)
                                param-set  (atom nil)]
                            (gsh/when-mocking!
                             (control/component-controls inst) => {:show-all {:label "Show All" :visible? true}}
                             (control/current-value inst k) => true
                             (control/set-parameter! inst k v) => (reset! param-set {:key k :val v})

                             (let [el    (control/render-control :boolean :default inst :show-all)
                                   input (first (find-by-tag el "input"))]
                               (assertions
                                "Renders a checkbox"
                                (attr-val input :type) => "checkbox"

                                "Checkbox is checked"
                                (attr-val input :checked) => true)

                               (on-change! input nil)
                               (assertions
                                "onChange toggles to false"
                                (:val @param-set) => false))))))

;; =============================================================================
;; Multimethod Registration Tests
;; =============================================================================

(specification "Field multimethod registration"
               (assertions
                "String :default is registered"
                (some? (get-method fr/render-field [:string :default])) => true

                "Int :default is registered"
                (some? (get-method fr/render-field [:int :default])) => true

                "Long :default is registered"
                (some? (get-method fr/render-field [:long :default])) => true

                "Double :default is registered"
                (some? (get-method fr/render-field [:double :default])) => true

                "Boolean :default is registered"
                (some? (get-method fr/render-field [:boolean :default])) => true

                "Instant :default is registered"
                (some? (get-method fr/render-field [:instant :default])) => true

                "Instant :date-at-noon is registered"
                (some? (get-method fr/render-field [:instant :date-at-noon])) => true

                "Enum :default is registered"
                (some? (get-method fr/render-field [:enum :default])) => true

                "Decimal :default is registered"
                (some? (get-method fr/render-field [:decimal :default])) => true

                "Ref :default is registered"
                (some? (get-method fr/render-field [:ref :default])) => true

                "Ref :pick-one is registered"
                (some? (get-method fr/render-field [:ref :pick-one])) => true))

(specification "Control multimethod registration"
               (assertions
                "Button :default is registered"
                (some? (get-method control/render-control [:button :default])) => true

                "String :default is registered"
                (some? (get-method control/render-control [:string :default])) => true

                "Boolean :default is registered"
                (some? (get-method control/render-control [:boolean :default])) => true))

(specification "Form element multimethod registration"
               (assertions
                "Form container :default is registered"
                (some? (get-method form/render-element [:form-container :default])) => true

                "Form body container :default is registered"
                (some? (get-method form/render-element [:form-body-container :default])) => true

                "Ref container :default is registered"
                (some? (get-method form/render-element [:ref-container :default])) => true))

(specification "Report multimethod registration"
               (assertions
                "Report :default is registered"
                (some? (get-method rr/render-report :default)) => true

                "Report body :default is registered"
                (some? (get-method rr/render-body :default)) => true

                "Report row :default is registered"
                (some? (get-method rr/render-row :default)) => true

                "Report controls :default is registered"
                (some? (get-method rr/render-controls :default)) => true

                "Report header :default is registered"
                (some? (get-method rr/render-header :default)) => true

                "Report footer :default is registered"
                (some? (get-method rr/render-footer :default)) => true))
