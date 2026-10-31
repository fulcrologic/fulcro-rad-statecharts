(ns com.example.headless-client
  "Headless E2E test client and helpers: `test-client` builds a headless client for a test server, and `await!`
   polls the client until an async condition (HTTP round-trip, statechart transition) holds."
  (:require
   [com.example.client :as client]
   [com.example.test-server :refer [*test-port*]]
   [com.fulcrologic.fulcro.headless :as h]
   [com.fulcrologic.statecharts.integration.fulcro :as scf]))

(def ^:dynamic *await-timeout-ms*
  "Default time `await!` polls before failing. Generous because a cold JVM's first load can take seconds."
  10000)

(defn test-client
  "Create a headless test client connecting to http://localhost:`port`/api. With no `port`, uses
   `com.example.test-server/*test-port*`, the port the `with-test-system` fixture's server bound."
  ([] (test-client *test-port*))
  ([port] (client/init port)))

(defn await!
  "Calls `h/render-frame!` on `app` until `(pred app)` is truthy, then returns `app`. Throws (failing the test) once
   `:timeout-ms` passes without `pred` holding. The options are:

   * `:description` - (required) What is being waited for, shown on timeout.
   * `:session-id` - (optional) The statechart session whose configuration is reported on timeout. Either a session
     id or a `(fn [app] session-id)`, which is called at timeout so ids that are only known after startup (such as a
     routed form's) are reported correctly.
   * `:timeout-ms` - (optional) Defaults to `*await-timeout-ms*` (10 s).
   * `:poll-ms` - (optional) Sleep between polls, letting HTTP threads deliver. Defaults to 20 ms."
  [app {:keys [description session-id timeout-ms poll-ms]
        :or   {poll-ms 20}} pred]
  (let [timeout-ms (or timeout-ms *await-timeout-ms*)
        deadline   (+ (System/currentTimeMillis) timeout-ms)]
    (loop []
      (h/render-frame! app)
      (cond
        (pred app) app

        (< (System/currentTimeMillis) deadline)
        (do (Thread/sleep (long poll-ms)) (recur))

        :else
        (let [sid    (if (fn? session-id) (session-id app) session-id)
              config (when sid (scf/current-configuration app sid))]
          (throw (ex-info (str "Timed out after " timeout-ms " ms waiting for: " description
                            "; session " (pr-str sid) " is in " (pr-str config))
                   {:description   description
                    :session-id    sid
                    :configuration config
                    :timeout-ms    timeout-ms})))))))
