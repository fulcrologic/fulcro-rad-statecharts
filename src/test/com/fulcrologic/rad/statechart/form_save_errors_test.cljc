(ns com.fulcrologic.rad.statechart.form-save-errors-test
  "GitHub #29: errors the server returns from a save reach the form.

   Drives a real RAD app (synchronous transactions, immediate statechart event loop) whose remote answers the save
   itself, so `:event/save-failed` carries the real `:fulcro/mutation-result` event shape. Nothing in the form chart
   is mocked."
  (:require
   [com.fulcrologic.fulcro.algorithms.normalized-state :as fns]
   [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
   [com.fulcrologic.fulcro.algorithms.tx-processing :as txn]
   [com.fulcrologic.fulcro.algorithms.tx-processing.synchronous-tx-processing :as stx]
   [com.fulcrologic.fulcro.application :as app]
   [com.fulcrologic.fulcro.components :as comp]
   [com.fulcrologic.fulcro.mutations :as m]
   [com.fulcrologic.rad.attributes :refer [defattr]]
   [com.fulcrologic.rad.attributes-options :as ao]
   [com.fulcrologic.rad.form-options :as fo]
   [com.fulcrologic.rad.statechart.application :as rad-app]
   [com.fulcrologic.rad.statechart.form :as form]
   [com.fulcrologic.rad.statechart.form-options :as sfo]
   [com.fulcrologic.rad.statechart.session :as sc.session]
   [com.fulcrologic.statecharts.integration.fulcro :as scf]
   [fulcro-spec.core :refer [=> assertions component specification]]))

(defattr member-id :member/id :uuid
  {ao/identity? true})

(defattr member-name :member/name :string
  {ao/identities #{:member/id}
   ao/required?  true})

(def save-failed-calls
  "Form idents the `:save-failed` trigger was called with."
  (atom []))

(form/defsc-form MemberForm [_this _props]
  {fo/id             member-id
   fo/attributes     [member-name]
   fo/default-values {:member/name "Ann"}
   sfo/triggers      {:save-failed (fn [_env _data form-ident]
                                     (swap! save-failed-calls conj form-ident)
                                     [])}})

(def server-errors
  "The errors the fake server returns for a refused save."
  [{:message "Name is already taken"}])

(defn refused-save
  "A remote result in which the server refused the save with `server-errors`."
  []
  {:status-code 200
   :body        {`com.fulcrologic.rad.form/save-form {:com.fulcrologic.rad.form/errors server-errors}}})

(defn accepted-save
  "A remote result in which the server accepted the save."
  []
  {:status-code 200
   :body        {`com.fulcrologic.rad.form/save-form {}}})

(m/defmutation note-save-failed
  "Records that the form's `:on-save-failed` transaction ran."
  [_]
  (action [{:keys [state]}]
    (swap! state assoc ::on-save-failed-ran? true)))

(defn save-app
  "Returns a RAD app with synchronous transactions and an immediate statechart event loop. Its remote answers every
   send with `(@response)`, so a test can change the server's answer between saves, and counts sends in `sends`."
  [response sends]
  (let [a (stx/with-synchronous-transactions
            (rad-app/fulcro-rad-app
              {:remotes {:remote {:transmit! (fn [_remote {::txn/keys [result-handler]}]
                                               (swap! sends inc)
                                               (result-handler (@response)))}}}))]
    (rad-app/install-statecharts! a {:event-loop? :immediate})
    a))

(defn form-errors
  "The `::form/errors` stored on the form entity at `ident` (where the `:server-errors` alias points)."
  [app ident]
  (get-in (app/current-state app) (conj ident :com.fulcrologic.rad.form/errors)))

(specification "Server save errors (GitHub #29)"
  (reset! save-failed-calls [])
  (let [response   (atom refused-save)
        sends      (atom 0)
        app        (save-app response sends)
        tid        (tempid/tempid)
        ident      [:member/id tid]
        session-id (sc.session/ident->session-id ident)]
    (form/start-form! app tid MemberForm {:on-save-failed [(note-save-failed {})]})
    (scf/send! app session-id :event/save {})

    (assertions
      "a refused save returns the form to editing"
      (contains? (scf/current-configuration app session-id) :state/editing) => true
      "the server's errors are stored on the form (the :server-errors alias)"
      (form-errors app ident) => server-errors
      "the save-failed trigger still runs, with the form ident"
      @save-failed-calls => [ident]
      "the :on-save-failed transaction still runs"
      (::on-save-failed-ran? (app/current-state app)) => true)

    #?(:clj
       (let [instance ((comp/factory MemberForm) (fns/ui->props (app/current-state app) MemberForm ident))]
         (assertions
           "form/server-errors returns them from the form's props"
           (form/server-errors instance) => server-errors)))

    (component "a later successful save"
      (reset! response accepted-save)
      (scf/send! app session-id :event/save {})
      (assertions
        "is sent to the server"
        @sends => 2
        "clears the errors"
        (form-errors app ident) => []
        "returns the form to editing"
        (contains? (scf/current-configuration app session-id) :state/editing) => true))

    (component "undo"
      (reset! response refused-save)
      (scf/send! app session-id :event/save {})
      (assertions
        "(after another refused save restores the errors)"
        (form-errors app ident) => server-errors)
      (scf/send! app session-id :event/reset {})
      (assertions
        "clears the errors"
        (form-errors app ident) => []))))
