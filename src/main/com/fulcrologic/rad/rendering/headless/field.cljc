(ns com.fulcrologic.rad.rendering.headless.field
  "Headless field renderers for all standard RAD attribute types. Dispatches via the
   `fr/render-field` multimethod on `[ao/type style]`. Renders plain HTML inputs with
   data attributes for headless test selection. No CSS, no React component libraries."
  (:require
   [com.fulcrologic.fulcro.components :as comp]
   #?(:cljs [com.fulcrologic.fulcro.dom :as dom]
      :clj  [com.fulcrologic.fulcro.dom-server :as dom])
   [com.fulcrologic.fulcro.mutations :as m]
   [com.fulcrologic.rad.attributes-options :as ao]
   [com.fulcrologic.rad.statechart.form :as form]
   [com.fulcrologic.rad.form-options :as fo]
   [com.fulcrologic.rad.form-render :as fr]
   [com.fulcrologic.rad.options-util :refer [?!]]
   [com.fulcrologic.rad.picker-options :as po]
   #?(:clj [clojure.edn :as edn]
      :cljs [cljs.reader :as reader])))

(defn- field-id
  "Generate a stable DOM id for a field based on its qualified key."
  [qualified-key]
  (str (namespace qualified-key) "--" (name qualified-key)))

(defn- render-label
  "Render a label element for a field, unless labels are omitted."
  [field-label qualified-key omit-label? required?]
  (when-not omit-label?
    (dom/label {:htmlFor (field-id qualified-key)
                :data-rad-type "field-label"
                :data-rad-field (str qualified-key)}
               field-label
               (when required? (dom/span {:data-rad-type "required-indicator"} " *")))))

(defn- render-validation
  "Render validation error message if the field is invalid."
  [invalid? validation-message qualified-key]
  (when invalid?
    (dom/span {:data-rad-type  "field-error"
               :data-rad-field (str qualified-key)}
              (or validation-message "Invalid value"))))

(defn- field-wrapper
  "Wrap a field input with label, the input element, and validation message."
  [{:keys [qualified-key field-label omit-label? required? visible? invalid? validation-message]} input-element]
  (when visible?
    (dom/div {:key            (str qualified-key)
              :data-rad-type  "form-field"
              :data-rad-field (str qualified-key)}
             (render-label field-label qualified-key omit-label? required?)
             input-element
             (render-validation invalid? validation-message qualified-key))))

(defn- common-input-attrs
  "Build the common HTML attributes shared across input types."
  [qualified-key value read-only? required? on-change-fn]
  (cond-> {:id       (field-id qualified-key)
           :name     (str qualified-key)
           :value    (or (str value) "")
           :onChange on-change-fn}
    read-only? (assoc :readOnly true :disabled true)
    required?  (assoc :required true)))

(defn render-text-field
  "Render a string field as a text input."
  [{::form/keys [form-instance] :as env} attribute]
  (let [qualified-key (ao/qualified-key attribute)
        {:keys [value field-label visible? invalid? validation-message
                read-only? omit-label?]} (form/field-context env attribute)
        required? (ao/required? attribute)]
    (field-wrapper
     {:qualified-key qualified-key :field-label field-label :omit-label? omit-label?
      :required? required? :visible? visible? :invalid? invalid?
      :validation-message validation-message}
     (dom/input (common-input-attrs qualified-key value read-only? required?
                                    (fn [evt]
                                      #?(:cljs (m/set-string! form-instance qualified-key :event evt)
                                         :clj  (m/set-string!! form-instance qualified-key :value evt))))))))

(defn render-number-field
  "Render a numeric field (int, long, double) as a number input."
  [{::form/keys [form-instance] :as env} attribute]
  (let [qualified-key (ao/qualified-key attribute)
        type          (ao/type attribute)
        {:keys [value field-label visible? invalid? validation-message
                read-only? omit-label?]} (form/field-context env attribute)
        required? (ao/required? attribute)]
    (field-wrapper
     {:qualified-key qualified-key :field-label field-label :omit-label? omit-label?
      :required? required? :visible? visible? :invalid? invalid?
      :validation-message validation-message}
     (dom/input (-> (common-input-attrs qualified-key value read-only? required?
                                        (fn [evt]
                                          #?(:cljs (if (= :double type)
                                                     (m/set-string! form-instance qualified-key :event evt)
                                                     (m/set-integer! form-instance qualified-key :event evt))
                                             :clj  (if (= :double type)
                                                     (m/set-string!! form-instance qualified-key :value evt)
                                                     (m/set-integer!! form-instance qualified-key :value evt)))))
                    (assoc :type "number")
                    (cond->
                     (= :double type) (assoc :step "any")))))))

(defn render-boolean-field
  "Render a boolean field as a checkbox."
  [{::form/keys [form-instance] :as env} attribute]
  (let [qualified-key (ao/qualified-key attribute)
        {:keys [value field-label visible? invalid? validation-message
                read-only? omit-label?]} (form/field-context env attribute)
        required? (ao/required? attribute)]
    (when visible?
      (dom/div {:key            (str qualified-key)
                :data-rad-type  "form-field"
                :data-rad-field (str qualified-key)}
               (dom/label {:htmlFor      (field-id qualified-key)
                           :data-rad-type "field-label"
                           :data-rad-field (str qualified-key)}
                          (dom/input (cond-> {:id       (field-id qualified-key)
                                              :name     (str qualified-key)
                                              :type     "checkbox"
                                              :checked  (boolean value)
                                              :onChange (fn [_evt]
                                                          #?(:cljs (m/set-value! form-instance qualified-key (not value))
                                                             :clj  (m/set-value!! form-instance qualified-key (not value))))}
                                       read-only? (assoc :disabled true)
                                       required?  (assoc :required true)))
                          (when-not omit-label?
                            (dom/span nil field-label)))
               (render-validation invalid? validation-message qualified-key)))))

(defn render-instant-field
  "Render an instant/date field as a date input."
  [{::form/keys [form-instance] :as env} attribute]
  (let [qualified-key (ao/qualified-key attribute)
        {:keys [value field-label visible? invalid? validation-message
                read-only? omit-label?]} (form/field-context env attribute)
        required? (ao/required? attribute)]
    (field-wrapper
     {:qualified-key qualified-key :field-label field-label :omit-label? omit-label?
      :required? required? :visible? visible? :invalid? invalid?
      :validation-message validation-message}
     (dom/input (-> (common-input-attrs qualified-key (str (or value "")) read-only? required?
                                        (fn [evt]
                                          #?(:cljs (m/set-string! form-instance qualified-key :event evt)
                                             :clj  (m/set-string!! form-instance qualified-key :value evt))))
                    (assoc :type "date"))))))

(defn render-enum-field
  "Render an enum field as a select/dropdown."
  [{::form/keys [form-instance] :as env} attribute]
  (let [qualified-key      (ao/qualified-key attribute)
        {:keys [value field-label visible? invalid? validation-message
                read-only? omit-label?]} (form/field-context env attribute)
        required?          (ao/required? attribute)
        enumerated-values  (ao/enumerated-values attribute)
        enumerated-labels  (or (?! (fo/enumerated-labels (comp/component-options form-instance)) attribute)
                               (ao/enumerated-labels attribute)
                               {})]
    (field-wrapper
     {:qualified-key qualified-key :field-label field-label :omit-label? omit-label?
      :required? required? :visible? visible? :invalid? invalid?
      :validation-message validation-message}
     (dom/select (cond-> {:id       (field-id qualified-key)
                          :name     (str qualified-key)
                          :value    (str value)
                          :onChange (fn [evt]
                                      (let [raw-val #?(:cljs (.. evt -target -value)
                                                       :clj  (str evt))
                                            ;; Option values are (str kw) which includes the leading colon
                                            s       (cond-> raw-val
                                                      (and (string? raw-val)
                                                           (.startsWith ^String raw-val ":"))
                                                      (subs 1))
                                            kw-val  (when (seq s) (keyword s))]
                                        #?(:cljs (m/set-value! form-instance qualified-key kw-val)
                                           :clj  (m/set-value!! form-instance qualified-key kw-val))))}
                   read-only? (assoc :disabled true)
                   required?  (assoc :required true))
                 (dom/option {:value ""} "")
                 (mapv (fn [ev]
                         (dom/option {:key   (str ev)
                                      :value (str ev)}
                                     (or (get enumerated-labels ev) (name ev))))
                       enumerated-values)))))

(defn render-decimal-field
  "Render a decimal field as a number input with step='any'."
  [{::form/keys [form-instance] :as env} attribute]
  (let [qualified-key (ao/qualified-key attribute)
        {:keys [value field-label visible? invalid? validation-message
                read-only? omit-label?]} (form/field-context env attribute)
        required? (ao/required? attribute)]
    (field-wrapper
     {:qualified-key qualified-key :field-label field-label :omit-label? omit-label?
      :required? required? :visible? visible? :invalid? invalid?
      :validation-message validation-message}
     (dom/input (-> (common-input-attrs qualified-key (str (or value "")) read-only? required?
                                        (fn [evt]
                                          #?(:cljs (m/set-string! form-instance qualified-key :event evt)
                                             :clj  (m/set-string!! form-instance qualified-key :value evt))))
                    (assoc :type "number" :step "any"))))))

(defn render-ref-field
  "Render a reference field. For picker-style refs, renders a `<select>` dropdown populated
   from `picker-options/current-form-options`. For subform refs, renders nothing (subforms
   are handled by `render-subforms` in form.cljc). Falls back to a text display when no
   picker options are available."
  [{::form/keys [form-instance] :as env} attribute]
  (let [qualified-key (ao/qualified-key attribute)
        options    (comp/component-options form-instance)
        subforms   (fo/subforms options)
        is-subform? (contains? subforms qualified-key)]
    (if is-subform?
      nil ;; Subform rendering is handled by render-subforms in form.cljc
      (let [{:keys [value field-label visible? invalid? validation-message
                    read-only? omit-label?]} (form/field-context env attribute)
            required?      (ao/required? attribute)
            picker-options (po/current-form-options form-instance attribute)]
        (field-wrapper
         {:qualified-key qualified-key :field-label field-label :omit-label? omit-label?
          :required? required? :visible? visible? :invalid? invalid?
          :validation-message validation-message}
         (if (seq picker-options)
           (dom/select (cond-> {:id            (field-id qualified-key)
                                :name          (str qualified-key)
                                :data-rad-type "ref-picker"
                                :data-rad-field (str qualified-key)
                                :value         (str value)
                                :onChange      (fn [evt]
                                                 #?(:cljs
                                                    (let [raw-val (.. evt -target -value)
                                                          parsed  (when (seq raw-val) (reader/read-string raw-val))]
                                                      (m/set-value! form-instance qualified-key parsed))
                                                    :clj
                                                    (let [parsed (when (seq (str evt)) (edn/read-string (str evt)))]
                                                      (m/set-value!! form-instance qualified-key parsed))))}
                         read-only? (assoc :disabled true)
                         required?  (assoc :required true))
                       (dom/option {:value ""} "")
                       (mapv (fn [{:keys [text value]}]
                               (dom/option {:key   (str value)
                                            :value (str value)}
                                           (or text (str value))))
                             picker-options))
           (dom/span {:data-rad-type  "ref-display"
                      :data-rad-field (str qualified-key)}
                     (str (or value "")))))))))

;; -- Multimethod registrations -----------------------------------------------

(defmethod fr/render-field [:string :default] [env attr]
  (render-text-field env attr))

(defmethod fr/render-field [:int :default] [env attr]
  (render-number-field env attr))

(defmethod fr/render-field [:long :default] [env attr]
  (render-number-field env attr))

(defmethod fr/render-field [:double :default] [env attr]
  (render-number-field env attr))

(defmethod fr/render-field [:boolean :default] [env attr]
  (render-boolean-field env attr))

(defmethod fr/render-field [:instant :default] [env attr]
  (render-instant-field env attr))

(defmethod fr/render-field [:instant :date-at-noon] [env attr]
  (render-instant-field env attr))

(defmethod fr/render-field [:enum :default] [env attr]
  (render-enum-field env attr))

(defmethod fr/render-field [:decimal :default] [env attr]
  (render-decimal-field env attr))

(defmethod fr/render-field [:ref :default] [env attr]
  (render-ref-field env attr))

(defmethod fr/render-field [:ref :pick-one] [env attr]
  (render-ref-field env attr))
