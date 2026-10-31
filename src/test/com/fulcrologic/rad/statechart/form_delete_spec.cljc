(ns com.fulcrologic.rad.statechart.form-delete-spec
  "`form/delete!` (binding decision: it sends the legacy symbol `com.fulcrologic.rad.form/delete-entity`, which
   upstream `form/resolvers` registers on servers).

   Uses a RAD app with synchronous transactions whose remote records each send node, so the test sees the exact AST
   on the wire and delivers the server's answer itself. Runs on both platforms. The legacy symbol's client-side
   `m/mutate` method is swapped in and out to show `delete!` does not depend on it: upstream's CLJS `defmutation`
   registers one, a build without upstream `com.fulcrologic.rad.form` has none."
  (:require
   [com.fulcrologic.fulcro.algorithms.tx-processing :as txn]
   [com.fulcrologic.fulcro.algorithms.tx-processing.synchronous-tx-processing :as stx]
   [com.fulcrologic.fulcro.application :as app]
   [com.fulcrologic.fulcro.mutations :as m]
   ;; Upstream's form ns loads beside this lib on every full-stack JVM (save/delete middleware, `form/resolvers`).
   #?(:clj [com.fulcrologic.rad.form])
   [com.fulcrologic.rad.statechart.application :as rad-app]
   [com.fulcrologic.rad.statechart.form :as form]
   [fulcro-spec.core :refer [=> assertions component specification]]))

(def legacy-delete
  "The mutation symbol servers register for deletes (upstream `form/resolvers`)."
  'com.fulcrologic.rad.form/delete-entity)

(def ident [:dperson/id 1])

(defn delete-app
  "A RAD app with synchronous transactions. Its remote records every send node in `sends` and never answers."
  [sends]
  (let [a (stx/with-synchronous-transactions
            (rad-app/fulcro-rad-app {:remotes {:remote {:transmit! (fn [_remote send-node] (swap! sends conj send-node))}}}))]
    (swap! (::app/state-atom a) merge {:dperson/id  {1 {:dperson/id 1 :dperson/name "Doomed"}
                                                     2 {:dperson/id 2 :dperson/name "Kept"}}
                                       :root/people [[:dperson/id 1] [:dperson/id 2]]})
    a))

(defn sent-mutations
  "The `[dispatch-key params]` of every mutation in the recorded `sends`."
  [sends]
  (into []
    (comp
      (mapcat (fn [{::txn/keys [ast]}] (:children ast)))
      (map (juxt :dispatch-key :params)))
    sends))

(defn answer!
  "Delivers `result` to the first recorded send, if there is one."
  [sends result]
  (when-let [{::txn/keys [result-handler]} (first sends)]
    (result-handler result)))

(defn with-legacy-client-method
  "Calls `f` with `handler` installed as the client-side `m/mutate` method for `legacy-delete` (or with none when
   `handler` is nil), then restores whatever method was there before."
  [handler f]
  (let [original (get (methods m/mutate) legacy-delete)
        install! (fn [h] #?(:clj (.addMethod ^clojure.lang.MultiFn m/mutate legacy-delete h)
                            :cljs (-add-method m/mutate legacy-delete h)))]
    (try
      (if handler (install! handler) (remove-method m/mutate legacy-delete))
      (f)
      (finally
        (if original (install! original) (remove-method m/mutate legacy-delete))))))

(defn delete-scenario
  "Runs `delete!` on person 1 in a fresh app and answers the send with `result`. Returns what the test checks."
  [result]
  (let [sends (atom [])
        app   (delete-app sends)]
    (form/delete! app :dperson/id 1)
    (let [sent         (sent-mutations @sends)
          before-reply (app/current-state app)]
      (answer! @sends result)
      {:sent         sent
       :before-reply before-reply
       :after-reply  (app/current-state app)})))

(def legacy-client-calls
  "How many times the stand-in for upstream's legacy client mutation was dispatched."
  (atom 0))

(def ok-result {:status-code 200 :body {legacy-delete {}}})
(def failed-result {:status-code 500 :body {}})

(specification "form/delete!"
  (doseq [[situation handler] [["with a client mutation registered for the legacy symbol (upstream CLJS)"
                                (fn [_env] (swap! legacy-client-calls inc) {})]
                               ["with no client mutation for the legacy symbol (no upstream rad.form)" nil]]]
    (component situation
      (with-legacy-client-method handler
        (fn []
          (reset! legacy-client-calls 0)
          (let [{:keys [sent before-reply after-reply]} (delete-scenario ok-result)]
            (assertions
              "runs the lib's own client mutation (any legacy client method is left alone)"
              @legacy-client-calls => 0
              "sends exactly the legacy delete-entity mutation, with the entity's id as params"
              sent => [[legacy-delete {:dperson/id 1}]]
              "keeps the entity until the server confirms the delete"
              (get-in before-reply ident) => {:dperson/id 1 :dperson/name "Doomed"}
              "removes the entity once the server confirms"
              (contains? (:dperson/id after-reply) 1) => false
              "removes the idents that pointed at it"
              (:root/people after-reply) => [[:dperson/id 2]]
              "leaves other entities alone"
              (get-in after-reply [:dperson/id 2 :dperson/name]) => "Kept"))))))

  (component "when the server fails the delete"
    (let [{:keys [after-reply]} (delete-scenario failed-result)]
      (assertions
        "keeps the entity"
        (get-in after-reply ident) => {:dperson/id 1 :dperson/name "Doomed"}))))

(specification "pathom2-server-delete-entity-mutation"
  (let [{sym    :com.wsscode.pathom.connect/sym
         mutate :com.wsscode.pathom.connect/mutate} form/pathom2-server-delete-entity-mutation]
    (assertions
      "is registered under the legacy symbol"
      sym => legacy-delete
      "runs the delete middleware with the mutation params"
      (mutate {:com.fulcrologic.rad.form/delete-middleware (fn [env] (:com.fulcrologic.rad.form/params env))}
        {:dperson/id 1})
      => {:dperson/id 1}
      "throws when the form pathom plugin is not installed"
      (mutate {} {:dperson/id 1}) =throws=> #"pathom-plugin")))
