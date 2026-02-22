(ns com.fulcrologic.rad.rendering.headless.form
  "Headless form layout renderers. Registers defmethod implementations for
   `fr/render-form`, `fr/render-header`, `fr/render-fields`, `fr/render-footer`,
   and `form/render-element`. Plain HTML only, no CSS."
  (:require
   [com.fulcrologic.fulcro.application :as app]
   [com.fulcrologic.fulcro.components :as comp]
   #?(:cljs [com.fulcrologic.fulcro.dom :as dom]
      :clj  [com.fulcrologic.fulcro.dom-server :as dom])
   [com.fulcrologic.rad.attributes-options :as ao]
   [com.fulcrologic.rad.statechart.form :as form]
   [com.fulcrologic.rad.form-options :as fo]
   [com.fulcrologic.rad.form-render :as fr]
   [com.fulcrologic.rad.options-util :refer [?!]]
   [taoensso.timbre :as log]))

(defn- render-action-buttons
  "Render form action buttons (save, cancel, undo, delete)."
  [{::form/keys [form-instance master-form] :as env}]
  (let [props        (comp/props form-instance)
        master-props (comp/props master-form)
        remote-busy? (seq (::app/active-remotes master-props))
        form-opts    (comp/component-options form-instance)
        can-save?    (not (?! (::form/read-only? form-opts) form-instance))
        can-undo?    can-save?
        can-cancel?  true]
    (dom/div {:data-rad-type "form-actions"}
             (when can-save?
               (dom/button {:data-rad-type "action"
                            :data-rad-action "save"
                            :disabled (boolean remote-busy?)
                            :onClick  (fn [_] (form/save! env))}
                           "Save"))
             (when can-undo?
               (dom/button {:data-rad-type "action"
                            :data-rad-action "undo"
                            :onClick (fn [_] (form/undo-all! env))}
                           "Undo"))
             (when can-cancel?
               (dom/button {:data-rad-type "action"
                            :data-rad-action "cancel"
                            :onClick (fn [_] (form/cancel! env))}
                           "Cancel")))))

(defn- render-form-fields-by-layout
  "Render form fields according to the declared layout or all attributes in order.
   Returns a `comp/fragment` to avoid React key warnings from vector-as-children."
  [{::form/keys [form-instance] :as env}]
  (let [options    (comp/component-options form-instance)
        id-attr    (fo/id options)
        attributes (fo/attributes options)
        layout     (fo/layout options)]
    (apply comp/fragment
           (if layout
             ;; Render by layout rows
             (into []
                   (map-indexed (fn [ridx row]
                                  (dom/div {:key (str "row-" ridx) :data-rad-type "form-row"}
                                           (apply comp/fragment
                                                  (mapv (fn [field-key]
                                                          (if-let [attr (some #(when (= (ao/qualified-key %) field-key) %) attributes)]
                                                            (form/render-field env attr)
                                                            (do (log/warn "Layout references unknown attribute" field-key)
                                                                nil)))
                                                        row)))))
                   layout)
             ;; Render all non-identity attributes in order
             (mapv (fn [attr]
                     (when-not (ao/identity? attr)
                       (form/render-field env attr)))
                   attributes)))))

(defn- render-subforms
  "Render any subforms declared on this form.
   Returns a `comp/fragment` to avoid React key warnings from vector-as-children."
  [{::form/keys [form-instance] :as env}]
  (let [options  (comp/component-options form-instance)
        subforms (fo/subforms options)]
    (when (seq subforms)
      (apply comp/fragment
             (mapv (fn [[ref-key subform-opts]]
                     (let [subform-class (fo/ui subform-opts)
                           props         (comp/props form-instance)
                           subform-data  (get props ref-key)]
                       (when subform-data
                         (dom/div {:key              (str ref-key)
                                   :data-rad-type    "subform"
                                   :data-rad-field   (str ref-key)}
                                  (if (vector? subform-data)
                                    (let [factory     (comp/computed-factory subform-class {:keyfn #(comp/get-ident subform-class %)})
                                          can-add?    (?! (fo/can-add? subform-opts) form-instance ref-key)
                                          can-delete? (fo/can-delete? subform-opts)]
                                      (dom/div nil
                                               (when can-add?
                                                 (dom/button {:data-rad-type  "add-child"
                                                              :data-rad-field (str ref-key)
                                                              :onClick        (fn [_]
                                                                                (form/add-child! form-instance ref-key subform-class
                                                                                                 (when (= can-add? :prepend) {:order :prepend})))}
                                                             "Add"))
                                               (apply comp/fragment
                                                      (into []
                                                            (map-indexed
                                                             (fn [cidx child-props]
                                                               (dom/div {:key (str ref-key "-" cidx) :data-rad-type "subform-row"}
                                                                        (factory child-props
                                                                                 {::form/master-form     (::form/master-form env)
                                                                                  ::form/parent          form-instance
                                                                                  ::form/parent-relation ref-key})
                                                                        (when (?! can-delete? form-instance child-props)
                                                                          (dom/button {:data-rad-type  "delete-child"
                                                                                       :data-rad-field (str ref-key)
                                                                                       :onClick        (fn [_]
                                                                                                         (form/delete-child! form-instance ref-key
                                                                                                                             (comp/get-ident subform-class child-props)))}
                                                                                      "Delete")))))
                                                            subform-data))))
                                    (let [factory (comp/computed-factory subform-class)]
                                      (factory subform-data
                                               {::form/master-form    (::form/master-form env)
                                                ::form/parent         form-instance
                                                ::form/parent-relation ref-key})))))))
                   subforms)))))

;; -- render-element: structural elements for forms ----------------------------

(defmethod form/render-element [:form-container :default] [{::form/keys [form-instance master-form] :as env} _element]
  (let [props        (comp/props form-instance)
        master-props (when master-form (comp/props master-form))
        busy?        (seq (::app/active-remotes (or master-props props)))
        title        (some-> form-instance comp/component-options fo/title)]
    (dom/div {:data-rad-type "form"
              :data-rad-form (some-> form-instance comp/component-options fo/id ao/qualified-key str)}
             (when busy?
               (dom/div {:data-rad-type "busy"} "Saving..."))
             (when title
               (dom/h1 {:data-rad-type "form-title"} (?! title form-instance props)))
             (fr/render-header env (comp/component-options form-instance fo/id))
             (form/render-element env :form-body-container)
             (fr/render-footer env (comp/component-options form-instance fo/id)))))

(defmethod form/render-element [:form-body-container :default] [env _element]
  (dom/div {:data-rad-type "form-body"}
           (render-form-fields-by-layout env)
           (render-subforms env)))

(defmethod form/render-element [:ref-container :default] [env _element]
  (dom/div {:data-rad-type "ref-container"}
           (render-subforms env)))

;; -- render-header/footer: form-level ----------------------------------------

(defmethod fr/render-header :default [{::form/keys [form-instance] :as env} attr]
  (when (ao/identity? attr)
    (render-action-buttons env)))

(defmethod fr/render-footer :default [_env _attr]
  nil)

;; -- render-fields: field layout ----------------------------------------------

(defmethod fr/render-fields :default [env _id-attr]
  (dom/div {:data-rad-type "form-fields"}
           (render-form-fields-by-layout env)))
