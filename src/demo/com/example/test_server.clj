(ns com.example.test-server
  "Test server fixture. Starts the full Mount system on an ephemeral port (or a pinned one) with a freshly seeded
   in-memory Datomic Local database, and binds `*test-port*` to the port the server actually bound."
  (:require
   [com.example.components.server :refer [http-server]]
   [com.fulcrologic.fulcro.server.config :as fserver]
   [com.fulcrologic.rad.database-adapters.datomic-cloud :as datomic]
   [com.fulcrologic.rad.database-adapters.datomic-options :as do]
   [datomic.client.api :as d]
   [development :as dev]
   [mount.core :as mount]))

(def ^:dynamic *test-port*
  "The port the test server is listening on, bound during test execution. Pass it to
   `com.example.headless-client/test-client` (its 0-arity does)."
  nil)

(def config-path
  "The config file the test system starts from."
  "config/dev.edn")

(defn delete-databases!
  "Deletes every Datomic database named in the config at `config-path`. Datomic Local `:mem` storage lives as long as
   the JVM, and `datomic/start-databases` reuses an existing database, so without this each fixture start would seed
   on top of the previous run's data (duplicating the randomly-keyed invoices, keeping earlier saves)."
  []
  (doseq [[_ db-config] (get (fserver/load-config! {:config-path config-path}) do/databases)]
    (d/delete-database (datomic/config->client db-config) {:db-name (:datomic/database db-config)})))

(defn with-test-system
  "Returns a clojure.test fixture function that starts the full mount system with freshly seeded data and binds
   `*test-port*` to the port the server actually listens on. Anti-forgery is disabled for headless testing since the
   client uses a hardcoded CSRF token. The options are:

   * `:port` - (optional) The port to listen on. Defaults to 0, an ephemeral port chosen by the OS, so several JVMs
     can run the `:demo` suite at once. Pin a port only for manual debugging."
  ([] (with-test-system {}))
  ([{:keys [port] :or {port 0}}]
   (fn [tests]
     (delete-databases!)
     (mount/start-with-args
       {:config    config-path
        :overrides {:org.httpkit.server/config {:port port}
                    :ring.middleware/defaults-config
                    {:params    {:urlencoded true :multipart  true
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
       (binding [*test-port* (:port http-server)]
         (tests))
       (finally
         (mount/stop)
         (delete-databases!))))))
