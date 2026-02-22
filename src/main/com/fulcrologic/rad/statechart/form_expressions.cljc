(ns com.fulcrologic.rad.statechart.form-expressions
  "Statechart expression functions for the RAD form chart. These are the
   executable content that runs inside form statechart states and transitions.

   Each expression function follows the 4-arg Fulcro convention:
   `(fn [env data event-name event-data] ops-or-nil)`

   The Fulcro integration ALWAYS calls expressions with 4 args. When event-name
   and event-data are not needed, they are bound as `_` or elided with `& _`."
  (:require
   [com.fulcrologic.fulcro.algorithms.form-state :as fs]
   [com.fulcrologic.fulcro.algorithms.merge :as merge]
   [com.fulcrologic.fulcro.algorithms.normalized-state :as fns]
   [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
   [com.fulcrologic.fulcro.raw.components :as rc]
   [com.fulcrologic.statecharts :as sc]
   [com.fulcrologic.statecharts.data-model.operations :as ops]
   [com.fulcrologic.statecharts.integration.fulcro :as scf]
   [com.fulcrologic.statecharts.integration.fulcro.operations :as fops]
   [com.fulcrologic.rad.attributes :as attr]
   [com.fulcrologic.rad.attributes-options :as ao]
   [com.fulcrologic.rad.form-options :as fo]
   [com.fulcrologic.rad.options-util :refer [?!]]
   [com.fulcrologic.rad.picker-options :as po]
   [com.fulcrologic.statecharts.integration.fulcro.routing :as scr]
   [clojure.set :as set]
   [edn-query-language.core :as eql]
   [taoensso.timbre :as log]))

;; ===== Internal Helpers =====

(defn- actor-ident
  "Extract the ident for :actor/form from statechart data."
  [data]
  (get-in data [:fulcro/actors :actor/form :ident]))

(defn- actor-class
  "Resolve the component class for :actor/form from statechart data."
  [data]
  (scf/resolve-actor-class data :actor/form))

(defn- form-component-options
  "Get component options for the form actor's class."
  [data]
  (some-> (actor-class data) rc/component-options))

(defn- subform-options
  "Get subform options. Delegates to fo/subform-options."
  [form-options ref-key-or-attribute]
  (fo/subform-options form-options ref-key-or-attribute))

(defn- subform-ui
  "Get the UI class for a subform."
  [form-options ref-key-or-attribute]
  (some-> (subform-options form-options ref-key-or-attribute) fo/ui))

;; ===== Store Options =====

(defn store-options
  "Stores startup options from the initial event data into the statechart session data.
   Also invokes the `:started` trigger if defined on the form."
  [env data _event-name event-data]
  (let [FormClass  (actor-class data)
        form-ident (actor-ident data)
        {{:keys [started]} :com.fulcrologic.rad.form/triggers} (some-> FormClass rc/component-options)
        base-ops   [(ops/assign :options (or (:options data) event-data {}))]
        ;; Note: options may already be in data if passed at start!, or in event-data
        trigger-ops (when (fn? started)
                      (started env data form-ident))]
    (into base-ops (when (seq trigger-ops) trigger-ops))))

;; ===== Create? Predicate =====

(defn create?
  "Condition predicate: Returns true if this form session is for creating a new entity."
  [_env data & _]
  (boolean (:com.fulcrologic.rad.form/create? data)))

;; ===== Default State Generation =====
;; These are delegated to form.cljc functions which remain unchanged

(defn- all-keys [m]
  (reduce-kv
   (fn [result k v]
     (cond-> (conj result k)
       (map? v) (into (all-keys v))))
   #{}
   m))

;; Forward declarations - these will be resolved at runtime from form ns
;; We use registry-based lookup to avoid circular dependencies
;; In CLJS, we use a registry atom since there's no requiring-resolve
#?(:cljs (defonce ^:private form-fn-registry (atom {})))

(defn- resolve-form-fn
  "Resolve a function from the form namespace at runtime to avoid circular deps.
   In CLJ, uses requiring-resolve. In CLJS, looks up from a registry that form.cljc
   populates at load time via `register-form-fn!`."
  [sym]
  #?(:clj  (requiring-resolve (symbol "com.fulcrologic.rad.statechart.form" (name sym)))
     :cljs (or (get @form-fn-registry (name sym))
               (throw (ex-info (str "Form function not registered: " sym
                                    ". Ensure com.fulcrologic.rad.statechart.form is required.")
                               {:sym sym})))))

(defn register-form-fn!
  "Register a form.cljc function for cross-namespace resolution in CLJS.
   Called by form.cljc at load time. No-op in CLJ."
  [sym-name f]
  #?(:clj nil
     :cljs (swap! form-fn-registry assoc sym-name f)))

;; ===== Start Create Expression =====

(defn start-create-expr
  "Expression for creating a new form entity. Generates default state,
   merges it into the Fulcro state map, and sets up form config."
  [_env data _event-name _event-data]
  (let [FormClass     (actor-class data)
        form-ident    (actor-ident data)
        options       (:options data)
        form-overrides (:initial-state options)
        default-state-fn (resolve-form-fn 'default-state)
        optional-fields-fn (resolve-form-fn 'optional-fields)
        mark-fields-complete-fn (resolve-form-fn 'mark-fields-complete*)
        id            (second form-ident)
        initial-state (merge (default-state-fn FormClass id) form-overrides)
        entity-to-merge (fs/add-form-config FormClass initial-state)
        initialized-keys (all-keys initial-state)
        optional-keys (optional-fields-fn FormClass)]
    [(fops/apply-action merge/merge-component FormClass entity-to-merge)
     (fops/apply-action mark-fields-complete-fn {:entity-ident form-ident
                                                 :target-keys  (set/union initialized-keys optional-keys)})]))

;; ===== Start Load Expression =====

(defn start-load-expr
  "Expression for loading an existing form entity from the server."
  [_env data _event-name _event-data]
  (let [FormClass  (actor-class data)
        form-ident (actor-ident data)]
    (log/debug "Issuing load of pre-existing form entity" form-ident)
    [(fops/load form-ident FormClass
                {::sc/ok-event    :event/loaded
                 ::sc/error-event :event/failed})]))

;; ===== On Loaded Expression =====

(defn- build-autocreate-ops
  "Builds ops to auto-create to-one subform entities that are nil and marked with autocreate-on-load?.
   Returns a sequence of `fops/apply-action` ops, or nil."
  [FormClass form-ident state-map]
  (let [form-options  (rc/component-options FormClass)
        attributes    (get form-options fo/attributes)
        subforms      (subform-options form-options nil)
        possible-keys (when subforms (set (keys subforms)))
        form-value    (get-in state-map form-ident)
        attrs-to-create (when (and attributes possible-keys)
                          (into []
                                (filter (fn [{::attr/keys [qualified-key type cardinality]}]
                                          (and
                                           (true? (get-in subforms [qualified-key :com.fulcrologic.rad.form/autocreate-on-load?]))
                                           (nil? (get form-value qualified-key))
                                           (contains? possible-keys qualified-key)
                                           (= :ref type)
                                           (or (= :one cardinality) (nil? cardinality)))))
                                attributes))]
    (when (seq attrs-to-create)
      (let [default-state-fn (resolve-form-fn 'default-state)]
        (mapcat (fn [{::attr/keys [qualified-key target]}]
                  (let [ui-class   (fo/ui (get subforms qualified-key))
                        id         (tempid/tempid)
                        new-entity (default-state-fn ui-class id)
                        new-ident  [target id]]
                    [(fops/apply-action assoc-in (conj form-ident qualified-key) new-ident)
                     (fops/apply-action assoc-in new-ident new-entity)]))
                attrs-to-create)))))

(defn- build-ui-props-ops
  "Builds ops to initialize user-defined UI props on a loaded form entity.
   Denormalizes current props, calls the `fo/initialize-ui-props` function, then merges
   only the new keys (not already present) back into state, normalizing any component values.
   Returns a vector of ops, or nil."
  [FormClass form-ident]
  (let [initialize-ui-props (some-> FormClass rc/component-options (get fo/initialize-ui-props))]
    (when initialize-ui-props
      [(fops/apply-action
        (fn [state-map]
          (let [denorm-props    (fns/ui->props state-map FormClass form-ident)
                predefined-keys (set (keys denorm-props))
                ui-props        (?! initialize-ui-props FormClass denorm-props)
                query           (rc/get-query FormClass state-map)
                k->component    (into {}
                                      (keep (fn [{:keys [key component]}]
                                              (when component {key component})))
                                      (:children (eql/query->ast query)))
                all-ks          (set (keys ui-props))
                allowed-keys    (set/difference all-ks predefined-keys)]
            (reduce
             (fn [s k]
               (let [raw-value       (get ui-props k)
                     c               (k->component k)
                     component-ident (when c (rc/get-ident c raw-value))
                     value-to-place  (if (and c (vector? component-ident) (some? (second component-ident)))
                                       component-ident
                                       raw-value)]
                 (cond-> (assoc-in s (conj form-ident k) value-to-place)
                   c (merge/merge-component c raw-value))))
             state-map
             allowed-keys))))])))

(defn on-loaded-expr
  "Expression that runs when form data has been loaded successfully.
   Clears errors, handles autocreate, sets up form config, and marks complete."
  [_env data _event-name _event-data]
  (let [FormClass  (actor-class data)
        form-ident (actor-ident data)
        state-map  (:fulcro/state-map data)]
    (log/debug "Loaded. Marking the form complete.")
    (into
     [(fops/assoc-alias :server-errors [])
      (fops/apply-action fs/add-form-config* FormClass form-ident {:destructive? true})
      (fops/apply-action fs/mark-complete* form-ident)]
     (concat
      (build-autocreate-ops FormClass form-ident state-map)
      (build-ui-props-ops FormClass form-ident)))))

;; ===== Load Picker Options Expression =====

(defn load-picker-options-expr
  "Side-effect expression that loads picker options for all ref fields that have
   field-options configured. Runs on entry to :state/editing so dropdowns are populated
   for both create and edit flows. Returns nil (pure side-effect)."
  [env data _event-name _event-data]
  (let [app        (:fulcro/app env)
        FormClass  (actor-class data)
        form-ident (actor-ident data)
        state-map  (:fulcro/state-map data)
        props      (get-in state-map form-ident)
        options    (rc/component-options FormClass)
        attributes (fo/attributes options)]
    (when (and app attributes)
      (doseq [attr attributes]
        (let [qk            (ao/qualified-key attr)
              field-options (fo/get-field-options options attr)]
          (when (and field-options (po/query-key (merge attr field-options)))
            (po/load-options! app FormClass props attr)))))
    nil))

;; ===== On Load Failed Expression =====

(defn on-load-failed-expr
  "Expression that sets server errors when form load fails."
  [_env _data _event-name _event-data]
  [(fops/assoc-alias :server-errors [{:message "Load failed."}])])

;; ===== Attribute Changed Expression =====

(defn- update-tree*
  "Run the given `(xform ui-props)` against the current ui props of `component-class`'s instance at `component-ident`
  in `state-map`. Returns an updated state map with the transformed ui-props re-normalized and merged back into app state."
  [state-map xform component-class component-ident]
  (if (and xform component-class component-ident)
    (let [ui-props      (fns/ui->props state-map component-class component-ident)
          new-ui-props  (xform ui-props)]
      (merge/merge-component state-map component-class new-ui-props))
    state-map))

(defn- derive-fields-ops
  "Build operations for applying derive-fields triggers."
  [data event-data]
  (let [{:keys [form-key form-ident]} event-data
        form-class        (some-> form-key rc/registry-key->class)
        master-form-class (actor-class data)
        master-form-ident (actor-ident data)
        {{master-derive :derive-fields} :com.fulcrologic.rad.form/triggers} (some-> master-form-class rc/component-options)
        {{:keys [derive-fields]} :com.fulcrologic.rad.form/triggers} (some-> form-class rc/component-options)]
    (cond-> []
      derive-fields
      (conj (fops/apply-action update-tree* derive-fields form-class form-ident))

      (and (not= master-form-class form-class) master-derive)
      (conj (fops/apply-action update-tree* master-derive master-form-class master-form-ident)))))

(defn attribute-changed-expr
  "Expression for handling field value changes.
   Clears errors, updates value, marks field complete, fires on-change trigger, runs derive-fields."
  [env data _event-name event-data]
  (let [{:keys       [old-value form-key value form-ident]
         ::attr/keys [cardinality type qualified-key]} event-data
        form-class     (some-> form-key rc/registry-key->class)
        form-options   (some-> form-class rc/component-options)
        {{:keys [on-change]} :com.fulcrologic.rad.form/triggers} form-options
        many?          (= :many cardinality)
        ref?           (= :ref type)
        value          (cond
                         (and ref? many? (nil? value)) []
                         (and many? (nil? value)) #{}
                         (and ref? many?) (filterv #(not (nil? (second %))) value)
                         (and ref? (nil? (second value))) nil
                         :else value)
        path           (when (and form-ident qualified-key)
                         (conj form-ident qualified-key))
        base-ops       [(fops/assoc-alias :server-errors [])
                        (fops/apply-action fs/mark-complete* form-ident qualified-key)]
        value-ops      (cond
                         (and path (nil? value))
                         [(fops/apply-action update-in form-ident dissoc qualified-key)]

                         (and path (some? value))
                         [(fops/apply-action assoc-in path value)]

                         :else [])
        on-change-ops  (when on-change
                         (on-change env data form-ident qualified-key old-value value))
        derive-ops     (derive-fields-ops data event-data)]
    (when #?(:clj true :cljs goog.DEBUG)
      (when-not path
        (log/error "Unable to record attribute change. Path cannot be calculated."))
      (when (and ref? many? (not (every? eql/ident? value)))
        (log/error "Setting a ref-many attribute to incorrect type. Value should be a vector of idents:" qualified-key value))
      (when (and ref? (not many?) (some? value) (not (eql/ident? value)))
        (log/error "Setting a ref-one attribute to incorrect type. Value should an ident:" qualified-key value)))
    (into base-ops (concat value-ops (or on-change-ops []) derive-ops))))

;; ===== Blur Expression =====

(defn blur-expr
  "Expression for handling blur events. Currently a no-op placeholder."
  [_env _data _event-name _event-data]
  nil)

;; ===== Mark Complete Expressions =====

(defn mark-all-complete-expr
  "Expression for marking all form fields as complete for validation."
  [_env data _event-name _event-data]
  (let [form-ident (actor-ident data)]
    [(fops/apply-action fs/mark-complete* form-ident)]))

(defn mark-complete-on-invalid-expr
  "Expression that marks all fields complete when save is attempted on an invalid form.
   This is the fallback handler for :event/save when form-valid? returns false."
  [_env data _event-name _event-data]
  (let [form-ident (actor-ident data)]
    [(fops/apply-action fs/mark-complete* form-ident)]))

;; ===== Save Validation =====

(defn form-valid?
  "Condition predicate: Returns true if the form passes validation."
  [_env data & _]
  (let [FormClass  (actor-class data)
        form-ident (actor-ident data)
        state-map  (:fulcro/state-map data)
        valid?-fn  (resolve-form-fn 'valid?)
        proposed   (fs/completed-form-props state-map FormClass form-ident)]
    (valid?-fn FormClass proposed)))

;; ===== Prepare Save Expression =====

(defn prepare-save-expr
  "Expression for initiating the save flow. Calculates the diff and triggers the remote save mutation."
  [_env data _event-name event-data]
  (let [FormClass      (actor-class data)
        form-ident     (actor-ident data)
        state-map      (:fulcro/state-map data)
        form-options   (rc/component-options FormClass)
        id             (get form-options fo/id)
        save-mutation-sym (get form-options fo/save-mutation)
        master-pk      (::attr/qualified-key id)
        props          (fns/ui->props state-map FormClass form-ident)
        delta          (fs/dirty-fields props true)
        save-mutation  (or save-mutation-sym
                           (symbol "com.fulcrologic.rad.statechart.form" "save-form"))
        params         (merge event-data
                              {(keyword "com.fulcrologic.rad.form" "delta")     delta
                               (keyword "com.fulcrologic.rad.form" "master-pk") master-pk
                               (keyword "com.fulcrologic.rad.form" "id")        (second form-ident)})]
    [(fops/assoc-alias :server-errors [])
     (fops/invoke-remote [(list save-mutation params)]
                         {:returning   :actor/form
                          :ok-event    :event/saved
                          :error-event :event/save-failed})]))

;; ===== On Saved Expression =====

(defn on-saved-expr
  "Expression that runs after a successful save. Marks form as pristine
   and invokes any saved triggers or on-saved transactions."
  [env data _event-name _event-data]
  (let [form-ident  (actor-ident data)
        FormClass   (actor-class data)
        options     (:options data)
        {{:keys [saved]} :com.fulcrologic.rad.form/triggers} (some-> FormClass rc/component-options)
        {:keys [on-saved]} options
        base-ops    [(fops/apply-action fs/entity->pristine* form-ident)]
        saved-ops   (when (fn? saved)
                      (saved env data form-ident))
        ;; Handle on-saved transaction by injecting form id into mutation params
        on-saved-ops (when (seq on-saved)
                       (let [[id-key id] form-ident
                             {:keys [children] :as ast} (eql/query->ast on-saved)
                             new-ast (assoc ast :children
                                            (mapv (fn [{:keys [type] :as node}]
                                                    (if (= type :call)
                                                      (assoc-in node [:params id-key] id)
                                                      node))
                                                  children))
                             txn (eql/ast->query new-ast)
                             app (:fulcro/app env)]
                         (when app
                           (log/debug "Running on-saved tx:" txn)
                           (rc/transact! app txn))
                         nil))]
    (into base-ops (concat (or saved-ops []) (or on-saved-ops [])))))

;; ===== On Save Failed Expression =====

(defn on-save-failed-expr
  "Expression that handles save failure. Extracts errors from mutation result and sets them."
  [env data _event-name _event-data]
  (let [FormClass     (actor-class data)
        form-ident    (actor-ident data)
        options       (:options data)
        comp-options  (rc/component-options FormClass)
        save-mutation (get comp-options fo/save-mutation)
        {:keys [save-failed]} (get comp-options fo/triggers)
        save-mutation (or save-mutation
                          (symbol "com.fulcrologic.rad.statechart.form" "save-form"))
        result        (scf/mutation-result data)
        errors        (some-> result (get save-mutation) :com.fulcrologic.rad.form/errors)
        {:keys [on-save-failed]} options
        base-ops      (when (seq errors)
                        [(fops/assoc-alias :server-errors errors)])
        trigger-ops   (when (fn? save-failed)
                        (save-failed env data form-ident))
        _             (when (seq on-save-failed)
                        (let [app (:fulcro/app env)]
                          (when app
                            (rc/transact! app on-save-failed))))]
    (into (or base-ops []) (or trigger-ops []))))

;; ===== Undo All Expression =====

(defn undo-all-expr
  "Expression for undoing all form changes. Clears errors and restores pristine state."
  [_env data _event-name _event-data]
  (let [form-ident (actor-ident data)]
    [(fops/assoc-alias :server-errors [])
     (fops/apply-action fs/pristine->entity* form-ident)]))

;; ===== Leave Form Expression =====

(defn prepare-leave-expr
  "Expression that prepares data for leaving a form. Stores the abandoned? flag."
  [_env _data _event-name _event-data]
  [(ops/assign :abandoned? true)])

(defn leave-form-expr
  "Expression that executes form cleanup when leaving.
   Reverts form to pristine, handles cancel routing, runs on-cancel transaction."
  [env data _event-name _event-data]
  (let [FormClass    (actor-class data)
        form-ident   (actor-ident data)
        state-map    (:fulcro/state-map data)
        cancel-route (?! (some-> FormClass rc/component-options :com.fulcrologic.rad.form/cancel-route)
                         (:fulcro/app env)
                         (fns/ui->props state-map FormClass form-ident))
        {:keys [on-cancel embedded?]} (:options data)
        app          (:fulcro/app env)
        base-ops     [(ops/assign :abandoned? true)
                      (fops/apply-action fs/pristine->entity* form-ident)]
        ;; Execute on-cancel transaction outside of swap!
        _            (when (and app (seq on-cancel))
                       (rc/transact! app on-cancel))
        ;; Route away from the form outside of swap!
        _            (when (and app (not embedded?) cancel-route)
                       (cond
                         (map? cancel-route)
                         (let [{:keys [route]} cancel-route]
                           (when (and (seq route) (every? string? route))
                             (scr/route-to! app nil {:route route})))

                         (= :none cancel-route) nil

                         (and (seq cancel-route) (every? string? cancel-route))
                         (scr/route-to! app nil {:route cancel-route})))]
    base-ops))

;; ===== Route Denied Expression =====

(defn route-denied-expr
  "Expression for handling route-denied events.
   For async confirmation: stores desired route and sets route-denied? flag.
   For sync confirmation: prompts user and either leaves or stays."
  [env data _event-name event-data]
  (let [{:keys [form]} event-data
        Form         (some-> form rc/registry-key->class)
        user-confirm (some-> Form rc/component-options (get :com.fulcrologic.rad.form/confirm))]
    (if (= :async user-confirm)
      [(ops/assign :desired-route event-data)
       (fops/assoc-alias :route-denied? true)]
      ;; Sync confirmation path
      (let [confirm-fn (or user-confirm #?(:cljs js/confirm :clj (constantly true)))]
        (if (confirm-fn "You will lose unsaved changes. Are you sure?")
          ;; User confirmed: leave the form
          (leave-form-expr env data nil nil)
          ;; User cancelled: stay in editing
          nil)))))

;; ===== Continue Abandoned Route Expression =====

(defn continue-abandoned-route-expr
  "Expression for continuing a previously denied route change.
   Retrieves stored route, retries routing, and resets form to pristine."
  [env data _event-name _event-data]
  (let [form-ident   (actor-ident data)
        {:keys [form relative-root route timeouts-and-params]} (:desired-route data)
        app          (:fulcro/app env)]
    ;; Retry route via routing system outside of swap!
    (when app
      (scr/force-continue-routing! app))
    [(fops/assoc-alias :route-denied? false)
     (fops/apply-action fs/pristine->entity* form-ident)]))

;; ===== Clear Route Denied Expression =====

(defn clear-route-denied-expr
  "Expression for clearing the route-denied flag."
  [_env _data _event-name _event-data]
  [(fops/assoc-alias :route-denied? false)])

;; ===== Add Row Expression =====

(defn add-row-expr
  "Expression for adding a child row to a subform relation."
  [env data _event-name event-data]
  (let [{:com.fulcrologic.rad.form/keys [order parent-relation parent child-class
                                         initial-state default-overrides]} event-data
        form-options   (some-> parent rc/component-options)
        {{:keys [on-change]} :com.fulcrologic.rad.form/triggers} form-options
        form-key->attr-fn (resolve-form-fn 'form-key->attribute)
        default-state-fn  (resolve-form-fn 'default-state)
        optional-fields-fn (resolve-form-fn 'optional-fields)
        parent-ident     (rc/get-ident parent)
        relation-attr    (form-key->attr-fn parent parent-relation)
        many?            (attr/to-many? relation-attr)
        target-path      (conj parent-ident parent-relation)
        state-map        (:fulcro/state-map data)
        old-value        (get-in state-map target-path)
        new-child        (if (map? initial-state)
                           initial-state
                           (merge
                            (default-state-fn child-class (tempid/tempid))
                            default-overrides))
        child-ident      (rc/get-ident child-class new-child)
        optional-keys    (optional-fields-fn child-class)
        merge-and-config (fn [s]
                           (-> s
                               (merge/merge-component child-class new-child
                                                      (if many? (or order :append) :replace) target-path)
                               (fs/add-form-config* child-class child-ident)
                               ((fn [sm]
                                  (reduce
                                   (fn [s k] (fs/mark-complete* s child-ident k))
                                   sm
                                   (concat optional-keys (keys new-child)))))))
        base-ops         [(fops/apply-action merge-and-config)]
        derive-event-data {:form-key  (when parent (rc/class->registry-key (rc/component-type parent)))
                           :form-ident parent-ident}
        derive-ops       (derive-fields-ops data derive-event-data)]
    (when-not relation-attr
      (log/error "Cannot add child because you forgot to put the attribute for" parent-relation
                 "in the fo/attributes of" (when parent (rc/component-name parent))))
    (into base-ops derive-ops)))

;; ===== Delete Row Expression =====

(defn delete-row-expr
  "Expression for deleting a child row from a subform relation."
  [env data _event-name event-data]
  (let [{:com.fulcrologic.rad.form/keys [form-instance child-ident parent parent-relation]} event-data
        form-options   (some-> parent rc/component-options)
        {{:keys [on-change]} :com.fulcrologic.rad.form/triggers} form-options
        form-key->attr-fn (resolve-form-fn 'form-key->attribute)
        relation-attr   (form-key->attr-fn parent parent-relation)
        many?           (attr/to-many? relation-attr)
        child-ident     (or child-ident (when form-instance (rc/get-ident form-instance)))
        parent-ident    (rc/get-ident parent)
        target-path     (conj parent-ident parent-relation)
        delete-ops      (if many?
                          [(fops/apply-action fns/remove-ident child-ident target-path)]
                          [(fops/apply-action update-in parent-ident dissoc parent-relation)])
        derive-event-data {:form-key  (when parent (rc/class->registry-key (rc/component-type parent)))
                           :form-ident parent-ident}
        derive-ops       (derive-fields-ops data derive-event-data)]
    (into delete-ops derive-ops)))
