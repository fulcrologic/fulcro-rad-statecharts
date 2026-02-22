(ns com.fulcrologic.rad.statechart.form-machines
  "Helper functions and chart fragments for writing custom form statecharts.

   This namespace provides reusable pieces for building custom form statecharts:
   - Reusable transition vectors that can be included in custom states
   - Expression helper functions that return operation vectors
   - Standard chart fragments for common form patterns

   Usage in a custom statechart:

   ```clojure
   (statechart {:initial :initial}
     (state {:id :state/editing}
       ;; Include standard global transitions
       (apply concat global-transitions)
       ;; Add your custom transitions
       (handle :event/custom my-custom-handler)))
   ```"
  (:require
   [com.fulcrologic.statecharts.elements :refer [transition script]]
   [com.fulcrologic.statecharts.convenience :refer [on handle]]
   [com.fulcrologic.statecharts.data-model.operations :as ops]
   [com.fulcrologic.statecharts.integration.fulcro.operations :as fops]
   [com.fulcrologic.fulcro.algorithms.form-state :as fs]
   [com.fulcrologic.rad.statechart.form-expressions :as fex]))

;; ===== Reusable Chart Fragments =====

(def global-transitions
  "Reusable transitions for exit, reload, and mark-complete. Include these
   in any state that should support the standard global form events."
  [(on :event/exit :state/exited)
   (on :event/reload :state/loading
       (script {:expr fex/start-load-expr}))
   (handle :event/mark-complete fex/mark-all-complete-expr)])

(def editing-field-transitions
  "Reusable transitions for field editing events (attribute-changed, blur)."
  [(handle :event/attribute-changed fex/attribute-changed-expr)
   (handle :event/blur fex/blur-expr)])

(def editing-subform-transitions
  "Reusable transitions for subform management events (add-row, delete-row)."
  [(handle :event/add-row fex/add-row-expr)
   (handle :event/delete-row fex/delete-row-expr)])

(def editing-save-transitions
  "Reusable transitions for the save flow. Includes conditional save (validation)
   with fallback to mark-complete-on-invalid."
  [(transition {:event :event/save :cond fex/form-valid? :target :state/saving}
               (script {:expr fex/prepare-save-expr}))
   (handle :event/save fex/mark-complete-on-invalid-expr)])

(def editing-cancel-transitions
  "Reusable transitions for cancel, undo, and route guarding."
  [(handle :event/reset fex/undo-all-expr)
   (on :event/cancel :state/leaving
       (script {:expr fex/prepare-leave-expr}))
   (handle :event/route-denied fex/route-denied-expr)
   (handle :event/continue-abandoned-route fex/continue-abandoned-route-expr)
   (handle :event/clear-route-denied fex/clear-route-denied-expr)])

(def all-editing-transitions
  "All standard editing state transitions combined. Includes global transitions,
   field editing, subform management, save flow, and cancel/route guarding."
  (into []
        (concat global-transitions
                editing-field-transitions
                editing-subform-transitions
                editing-save-transitions
                editing-cancel-transitions)))

;; ===== Expression Helper Functions =====
;; These return operation vectors for use in custom expression functions

(defn clear-server-errors-ops
  "Returns ops to clear server errors on the form."
  []
  [(fops/assoc-alias :server-errors [])])

(defn undo-all-ops
  "Returns ops to revert the form to pristine state and clear errors.
   `form-ident` is the Fulcro ident of the form entity."
  [form-ident]
  [(fops/assoc-alias :server-errors [])
   (fops/apply-action fs/pristine->entity* form-ident)])

(defn mark-complete-ops
  "Returns ops to mark all form fields as complete for validation.
   `form-ident` is the Fulcro ident of the form entity."
  [form-ident]
  [(fops/apply-action fs/mark-complete* form-ident)])

(defn mark-pristine-ops
  "Returns ops to mark the current form state as pristine (after save).
   `form-ident` is the Fulcro ident of the form entity."
  [form-ident]
  [(fops/apply-action fs/entity->pristine* form-ident)])
