(ns com.fulcrologic.rad.statechart.form-chart
  "Statechart definition for RAD forms. Replaces the UISM `form-machine` from form.cljc.

   States:
   - :initial       — Decision state: dispatches to creating or loading
   - :state/creating — Synchronous setup for new entities
   - :state/loading  — Waiting for server load of existing entity
   - :state/load-failed — Load error state, can retry or exit
   - :state/editing  — Main interactive editing state
   - :state/saving   — Waiting for save response
   - :state/leaving  — Transient cleanup state before exit
   - :state/exited   — Final state (statechart terminated)"
  (:require
    [com.fulcrologic.rad.statechart.form-expressions :as fex]
    [com.fulcrologic.statecharts.chart :refer [statechart]]
    [com.fulcrologic.statecharts.convenience :refer [handle on]]
    [com.fulcrologic.statecharts.elements :refer
     [data-model final on-entry script state transition]]))

(def form-chart
  "The default statechart for RAD forms. Manages the full lifecycle of entity editing:
   loading, creating, editing, saving, undo, route guarding, subform management, and dirty tracking.

   This chart can be overridden per-form via the `sfo/statechart` component option."
  (statechart {:initial :initial}
    (data-model {:expr (fn [_ _ _ _] {:options {}})})

    ;; ===== INITIAL (decision state) =====
    ;; Stores startup options, then dispatches based on create? flag
    (state {:id :initial}
      (on-entry {}
        (script {:expr fex/store-options}))
      ;; Eventless transitions: create? check first, then fall through to loading
      (transition {:cond fex/create? :target :state/creating})
      (transition {:target :state/loading}))

    ;; ===== CREATING =====
    ;; Synchronous: generates default state, merges into Fulcro db
    (state {:id :state/creating}
      (on-entry {}
        (script {:expr fex/start-create-expr}))
      ;; Immediately transition to editing after create setup
      (transition {:target :state/editing}))

    ;; ===== LOADING =====
    ;; Issues a server load and waits for response
    (state {:id :state/loading}
      (on-entry {}
        (script {:expr fex/start-load-expr}))

      (on :event/loaded :state/editing
        (script {:expr fex/on-loaded-expr}))

      (on :event/failed :state/load-failed
        (script {:expr fex/on-load-failed-expr}))

      ;; Global events available during loading
      (on :event/exit :state/exited)
      (on :event/reload :state/loading
        (script {:expr fex/start-load-expr})))

    ;; ===== LOAD FAILED =====
    ;; Terminal-ish: user can retry or exit
    (state {:id :state/load-failed}
      (on :event/reload :state/loading)
      (on :event/exit :state/exited))

    ;; ===== EDITING (main interactive state) =====
    (state {:id :state/editing}
      (on-entry {}
        (script {:expr fex/load-picker-options-expr}))
      ;; --- Global events ---
      (on :event/exit :state/exited)
      (on :event/reload :state/loading
        (script {:expr fex/start-load-expr}))
      (handle :event/mark-complete fex/mark-all-complete-expr)

      ;; --- Field editing ---
      (handle :event/attribute-changed fex/attribute-changed-expr)
      (handle :event/blur fex/blur-expr)

      ;; --- Subform management ---
      (handle :event/add-row fex/add-row-expr)
      (handle :event/delete-row fex/delete-row-expr)

      ;; --- Save flow ---
      ;; First transition: if valid, proceed to saving (document-order evaluation)
      (transition {:event :event/save :cond fex/form-valid? :target :state/saving}
        (script {:expr fex/prepare-save-expr}))
      ;; Second transition: fallback for invalid form (unconditional, only reached if first doesn't match)
      (handle :event/save fex/mark-complete-on-invalid-expr)

      ;; --- Undo ---
      (handle :event/reset fex/undo-all-expr)

      ;; --- Cancel / Route guarding ---
      (on :event/cancel :state/leaving
        (script {:expr fex/prepare-leave-expr}))
      (handle :event/route-denied fex/route-denied-expr)
      (handle :event/continue-abandoned-route fex/continue-abandoned-route-expr)
      (handle :event/clear-route-denied fex/clear-route-denied-expr))

    ;; ===== SAVING =====
    (state {:id :state/saving}
      (on :event/saved :state/editing
        (script {:expr fex/on-saved-expr}))
      (on :event/save-failed :state/editing
        (script {:expr fex/on-save-failed-expr}))

      ;; Global events
      (on :event/exit :state/exited))

    ;; ===== LEAVING =====
    ;; Transient state: runs leave-form cleanup and immediately transitions to exited
    (state {:id :state/leaving}
      (on-entry {}
        (script {:expr fex/leave-form-expr}))
      (transition {:target :state/exited}))

    ;; ===== EXITED (final) =====
    (final {:id :state/exited})))
