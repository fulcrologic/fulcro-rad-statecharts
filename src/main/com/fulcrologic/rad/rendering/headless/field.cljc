(ns com.fulcrologic.rad.rendering.headless.field
  "Headless field renderers for all standard RAD attribute types. Dispatches via the
   `fr/render-field` multimethod on `[ao/type style]`. Renders plain HTML inputs with
   data attributes for headless test selection. No CSS, no React component libraries."
  (:require
   [com.fulcrologic.fulcro.components :as comp]
   #?(:cljs [com.fulcrologic.fulcro.dom :as dom]
      :clj  [com.fulcrologic.fulcro.dom-server :as dom])
   [com.fulcrologic.rad.attributes-options :as ao]
   [com.fulcrologic.rad.form :as form]
   [com.fulcrologic.rad.form-options :as fo]
   [com.fulcrologic.rad.form-render :as fr]
   [com.fulcrologic.rad.options-util :refer [?!]]))

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
  [{:keys [qualified-key field-label omit-label? required? visible? invalid? validation-message]} & children]
  (when visible?
    (dom/div {:data-rad-type  "form-field"
              :data-rad-field (str qualified-key)}
             (render-label field-label qualified-key omit-label? required?)
             (vec children)
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
  [{::form/keys [form-instance] :as env} {::ao/keys [qualified-key] :as attribute}]
  (let [{:keys [value field-label visible? invalid? validation-message
                read-only? omit-label?]} (form/field-context env attribute)
        required? (ao/required? attribute)]
    (field-wrapper
     {:qualified-key qualified-key :field-label field-label :omit-label? omit-label?
      :required? required? :visible? visible? :invalid? invalid?
      :validation-message validation-message}
     (dom/input (common-input-attrs qualified-key value read-only? required?
                                    (fn [evt]
                                      #?(:cljs (com.fulcrologic.fulcro.mutations/set-string! form-instance qualified-key :event evt)
                                         :clj  nil)))))))

(defn render-number-field
  "Render a numeric field (int, long, double) as a number input."
  [{::form/keys [form-instance] :as env} {::ao/keys [qualified-key type] :as attribute}]
  (let [{:keys [value field-label visible? invalid? validation-message
                read-only? omit-label?]} (form/field-context env attribute)
        required? (ao/required? attribute)]
    (field-wrapper
     {:qualified-key qualified-key :field-label field-label :omit-label? omit-label?
      :required? required? :visible? visible? :invalid? invalid?
      :validation-message validation-message}
     (dom/input (-> (common-input-attrs qualified-key value read-only? required?
                                        (fn [evt]
                                          #?(:cljs (if (= :double type)
                                                     (com.fulcrologic.fulcro.mutations/set-string! form-instance qualified-key :event evt)
                                                     (com.fulcrologic.fulcro.mutations/set-integer! form-instance qualified-key :event evt))
                                             :clj  nil)))
                    (assoc :type "number")
                    (cond->
                     (= :double type) (assoc :step "any")))))))

(defn render-boolean-field
  "Render a boolean field as a checkbox."
  [{::form/keys [form-instance] :as env} {::ao/keys [qualified-key] :as attribute}]
  (let [{:keys [value field-label visible? invalid? validation-message
                read-only? omit-label?]} (form/field-context env attribute)
        required? (ao/required? attribute)]
    (when visible?
      (dom/div {:data-rad-type  "form-field"
                :data-rad-field (str qualified-key)}
               (dom/label {:htmlFor      (field-id qualified-key)
                           :data-rad-type "field-label"
                           :data-rad-field (str qualified-key)}
                          (dom/input (cond-> {:id       (field-id qualified-key)
                                              :name     (str qualified-key)
                                              :type     "checkbox"
                                              :checked  (boolean value)
                                              :onChange (fn [_evt]
                                                          #?(:cljs (com.fulcrologic.fulcro.mutations/set-value! form-instance qualified-key (not value))
                                                             :clj  nil))}
                                       read-only? (assoc :disabled true)
                                       required?  (assoc :required true)))
                          (when-not omit-label?
                            (dom/span nil field-label)))
               (render-validation invalid? validation-message qualified-key)))))

(defn render-instant-field
  "Render an instant/date field as a date input."
  [{::form/keys [form-instance] :as env} {::ao/keys [qualified-key] :as attribute}]
  (let [{:keys [value field-label visible? invalid? validation-message
                read-only? omit-label?]} (form/field-context env attribute)
        required? (ao/required? attribute)]
    (field-wrapper
     {:qualified-key qualified-key :field-label field-label :omit-label? omit-label?
      :required? required? :visible? visible? :invalid? invalid?
      :validation-message validation-message}
     (dom/input (-> (common-input-attrs qualified-key (str (or value "")) read-only? required?
                                        (fn [evt]
                                          #?(:cljs (com.fulcrologic.fulcro.mutations/set-string! form-instance qualified-key :event evt)
                                             :clj  nil)))
                    (assoc :type "date"))))))

(defn render-enum-field
  "Render an enum field as a select/dropdown."
  [{::form/keys [form-instance] :as env} {::ao/keys [qualified-key] :as attribute}]
  (let [{:keys [value field-label visible? invalid? validation-message
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
                                      #?(:cljs
                                         (let [raw-val (.. evt -target -value)
                                               kw-val  (when (seq raw-val) (keyword raw-val))]
                                           (com.fulcrologic.fulcro.mutations/set-value! form-instance qualified-key kw-val))
                                         :clj nil))}
                   read-only? (assoc :disabled true)
                   required?  (assoc :required true))
                 (dom/option {:value ""} "")
                 (mapv (fn [ev]
                         (dom/option {:key   (str ev)
                                      :value (str ev)}
                                     (or (get enumerated-labels ev) (name ev))))
                       enumerated-values)))))

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

(defmethod fr/render-field [:enum :default] [env attr]
  (render-enum-field env attr))
