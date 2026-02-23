(ns com.example.test-server
  "Test server fixture. Starts the full Mount system on a configurable port
   and binds `*test-port*` for use in test code."
  (:require
    [com.example.components.server]
    [development :as dev]
    [mount.core :as mount]))

(def ^:dynamic *test-port*
  "The port the test server is running on, bound during test execution."
  nil)

(defn with-test-system
  "Returns a clojure.test fixture function that starts the full mount system
   with seeded data and binds `*test-port*`. Anti-forgery is disabled for
   headless testing since the client uses a hardcoded CSRF token."
  ([] (with-test-system {}))
  ([{:keys [port] :or {port 3100}}]
   (fn [tests]
     (mount/start-with-args
       {:config    "config/dev.edn"
        :overrides {:org.httpkit.server/config {:port port}
                    :ring.middleware/defaults-config
                    {:params    {:urlencoded true :multipart true
                                 :nested     true :keywordize true}
                     :cookies   true
                     :session   {:flash        true
                                 :cookie-attrs {:http-only true :same-site :lax}}
                     :security  {:anti-forgery false}
                     :responses {:not-modified-responses true
                                 :absolute-redirects     true
                                 :content-types          true
                                 :default-charset        "utf-8"}}}})
     (try
       (dev/seed!)
       (binding [*test-port* port]
         (tests))
       (finally
         (mount/stop))))))
