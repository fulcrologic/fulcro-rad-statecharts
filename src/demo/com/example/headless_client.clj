(ns com.example.headless-client
  "Thin wrapper around `client/init` for headless E2E tests."
  (:require
   [com.example.client :as client]))

(defn test-client
  "Create a headless test client connecting to http://localhost:`port`/api."
  [port]
  (client/init port))
