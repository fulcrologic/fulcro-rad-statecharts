(ns com.fulcrologic.rad.statechart.render-dispatch-spec
  "Form rendering does not depend on namespace load order (rp-02 req 5).

   Upstream `com.fulcrologic.rad.form` (UISM) registers `:default` methods on `fr/render-form` and `fr/render-field`
   for its map-based control registry, and it is unavoidably loaded on full-stack JVMs. This lib registers no
   `:default` on those multimethods, and its own form body falls back to `render-element`/`default-render-field` when no
   specific `fr/*` method exists. Each test re-loads the two namespaces in a given order (the last one loaded wins any
   `:default` both define), then renders:

   * a statechart form through the headless plugin: its fields and Save button must render;
   * a legacy (upstream UISM) form through upstream's map-based registry: its fields must render.

   The `fr/render-form`/`fr/render-field` method tables are restored afterwards. CLJ only (headless rendering)."
  (:require
   #?@(:clj [[com.fulcrologic.fulcro.application :as app]
             [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
             [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
             [com.fulcrologic.fulcro.dom-server :as dom]
             [com.fulcrologic.fulcro.headless :as h]
             [com.fulcrologic.fulcro.headless.hiccup :as hic]
             [com.fulcrologic.rad.application :as upstream-app]
             [com.fulcrologic.rad.attributes :refer [defattr]]
             [com.fulcrologic.rad.attributes-options :as ao]
             [com.fulcrologic.rad.form :as legacy-form]
             [com.fulcrologic.rad.form-options :as fo]
             [com.fulcrologic.rad.form-render :as fr]
             [com.fulcrologic.rad.rendering.headless.plugin]
             [com.fulcrologic.rad.statechart.application :as rad-app]
             [com.fulcrologic.rad.statechart.form :as form]])
   [fulcro-spec.core :refer [=> assertions component specification]]))

#?(:clj
   (do
     ;; ===== A statechart form, rendered by the headless plugin =====

     (defattr widget-id :rd-widget/id :uuid
       {ao/identity? true})

     (defattr widget-name :rd-widget/name :string
       {ao/identities #{:rd-widget/id}})

     (form/defsc-form WidgetForm [_this _props]
       {fo/id             widget-id
        fo/attributes     [widget-name]
        fo/default-values {:rd-widget/name "Sprocket"}
        fo/title          "Widget"})

     (def ui-widget-form (comp/computed-factory WidgetForm {:keyfn :rd-widget/id}))

     (defsc WidgetRoot [_this {:root/keys [form]}]
       {:query         [{:root/form (comp/get-query WidgetForm)}
                        [::app/active-remotes '_]]
        :initial-state {:root/form {}}}
       (when form (ui-widget-form form)))

     (defn rendered-widget-form
       "Starts a new `WidgetForm` in a headless app and returns the rendered hiccup."
       []
       (let [app (h/build-test-app {:root-class WidgetRoot})
             tid (tempid/tempid)]
         (rad-app/install-statecharts! app {:event-loop? :immediate})
         (form/start-form! app tid WidgetForm)
         (swap! (::app/state-atom app) assoc :root/form [:rd-widget/id tid])
         (h/render-frame! app)
         (h/hiccup-frame app)))

     ;; ===== A legacy upstream (UISM) form, rendered by upstream's map-based control registry =====

     (defattr gadget-id :rd-gadget/id :uuid
       {ao/identity? true})

     ;; The :legacy style has no `fr/render-field` method (the headless plugin only has [:string :default]), so the
     ;; field dispatches to `fr/render-field :default`, which must stay upstream's.
     (defattr gadget-name :rd-gadget/name :string
       {ao/identities #{:rd-gadget/id}
        ao/style      :legacy})

     (legacy-form/defsc-form GadgetForm [_this _props]
       {fo/id         gadget-id
        fo/attributes [gadget-name]})

     (def ui-gadget-form (comp/computed-factory GadgetForm {:keyfn :rd-gadget/id}))

     (defsc GadgetRoot [_this {:root/keys [form]}]
       {:query         [{:root/form (comp/get-query GadgetForm)}]
        :initial-state {:root/form {}}}
       (when form (ui-gadget-form form)))

     (def legacy-controls
       "A minimal upstream map-based control registry: a form container that renders every field through upstream
        `form/render-field`, and a :string/:legacy control."
       {:com.fulcrologic.rad.form/element->style->layout
        {:form-container {:default (fn [{:com.fulcrologic.rad.form/keys [form-instance] :as env}]
                                     (dom/div {:data-legacy "form"}
                                       (mapv #(legacy-form/render-field env %)
                                         (comp/component-options form-instance fo/attributes))))}}
        :com.fulcrologic.rad.form/type->style->control
        {:string {:legacy (fn [{:com.fulcrologic.rad.form/keys [form-instance]} attr]
                            (let [k (ao/qualified-key attr)]
                              (dom/span {:key (str k) :data-legacy-field (str k)}
                                (str (get (comp/props form-instance) k)))))}}})

     (defn rendered-gadget-form
       "Renders a `GadgetForm` (no UISM started: rendering only) and returns the hiccup."
       []
       (let [app   (h/build-test-app {:root-class GadgetRoot})
             ident [:rd-gadget/id #uuid "00000000-0000-0000-0000-00000000000a"]]
         (upstream-app/install-ui-controls! app legacy-controls)
         (swap! (::app/state-atom app)
           (fn [s]
             (-> s
               (assoc-in ident {:rd-gadget/id (second ident) :rd-gadget/name "Gizmo"})
               (assoc :root/form ident))))
         (h/render-frame! app)
         (h/hiccup-frame app)))

     ;; ===== Hiccup queries =====

     (defn attr-value
       "The first element of `hiccup` whose `attr-key` attribute is `v`."
       [hiccup attr-key v]
       (hic/find-first hiccup #(= v (get (hic/element-attrs %) attr-key))))

     (defn rendered-field-text
       "The text of the legacy field `k` in `hiccup`, or nil when it was not rendered."
       [hiccup k]
       (some-> (attr-value hiccup :data-legacy-field (str k)) hic/element-text))

     ;; ===== Load order =====

     (defn load-in-order!
       "Re-loads `namespaces` in order, so the last one's `defmethod`s win."
       [namespaces]
       (doseq [ns-sym namespaces]
         (require ns-sym :reload)))

     (defn with-restored-render-methods
       "Calls `f`, then restores the method tables of `fr/render-form` and `fr/render-field` to what they were."
       [f]
       (let [saved (into {} (map (fn [mf] [mf (methods mf)])) [fr/render-form fr/render-field])]
         (try
           (f)
           (finally
             (doseq [[^clojure.lang.MultiFn mf method-table] saved]
               (doseq [k (keys (methods mf))]
                 (remove-method mf k))
               (doseq [[k method] method-table]
                 (.addMethod mf k method)))))))

     (def load-orders
       {"lib loaded before upstream com.fulcrologic.rad.form" ['com.fulcrologic.rad.statechart.form
                                                               'com.fulcrologic.rad.form]
        "lib loaded after upstream com.fulcrologic.rad.form"  ['com.fulcrologic.rad.form
                                                               'com.fulcrologic.rad.statechart.form]})

     (specification "Form rendering in both load orders (rp-02 req 5)"
       (doseq [[order namespaces] load-orders]
         (component order
           (with-restored-render-methods
             (fn []
               (load-in-order! namespaces)
               (let [widget (rendered-widget-form)
                     gadget (rendered-gadget-form)]
                 (assertions
                   "a statechart form renders its fields through the plugin"
                   (some? (attr-value widget :data-rad-field (str :rd-widget/name))) => true
                   "a statechart form renders its Save button"
                   (some? (attr-value widget :data-rad-action "save")) => true
                   "a legacy upstream form renders its fields through upstream's registry"
                   (rendered-field-text gadget :rd-gadget/name) => "Gizmo")))))))

     (specification "The lib's form namespace"
       (with-restored-render-methods
         (fn []
           (let [before {:form  (get-method fr/render-form :default)
                         :field (get-method fr/render-field :default)}]
             (load-in-order! ['com.fulcrologic.rad.statechart.form])
             (assertions
               "leaves fr/render-form :default as it was when it loads"
               (identical? (:form before) (get-method fr/render-form :default)) => true
               "leaves fr/render-field :default as it was when it loads"
               (identical? (:field before) (get-method fr/render-field :default)) => true)))))))
