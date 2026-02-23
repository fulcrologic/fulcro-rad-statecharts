(ns com.fulcrologic.rad.statechart.session
  "Deterministic conversion between Fulcro idents and statechart session IDs.

   Statechart session IDs must satisfy `::sc/id` (keyword, UUID, number, or string).
   Fulcro idents are vectors like `[:account/id #uuid \"abc\"]`, which are NOT valid session IDs.
   This namespace provides a bidirectional mapping between idents and keywords.

   Routed components use `send-to-self!` and do not need these helpers.
   Embedded (non-routed) components use `ident->session-id` to derive a deterministic session ID."
  (:require
    [clojure.string :as str]
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.guardrails.core :refer [=> >defn]]
    [com.fulcrologic.rad.ids :as ids]))

(def ^{:doc "The namespace used for all ident-derived session ID keywords.
             NOTE: This is kept as 'com.fulcrologic.rad.sc' for backward compatibility
             with existing persisted session IDs — do NOT change this value."}
  session-ns "com.fulcrologic.rad.sc")

(>defn ident->session-id
  "Converts a Fulcro ident to a valid statechart session ID keyword.
The ident `[:account/id #uuid \"abc\"]` becomes `:com.fulcrologic.rad.sc/account_id--abc`.

The result is a namespaced keyword that:
- Satisfies `::sc/id`
- Is deterministic (same ident always produces same session-id)
- Is collision-free (the `--` separator is unambiguous)"
  [[k v]]
  [vector? => keyword?]
  (let [v-str (cond
                (keyword? v) (if-let [ns (namespace v)]
                               (str "KW." ns ".." (name v))
                               (str "KW." (name v)))
                :else (str v))
        n     (str (namespace k) "_" (name k) "--" v-str)]
    (keyword session-ns n)))

(defn- parse-id-value
  "Parses a string representation of an ident value back to its original type.
   Tries UUID first, then integer, then keyword, falling back to string."
  [s]
  (cond
    (ids/valid-uuid-string? s) (ids/new-uuid s)
    (re-matches #"-?\d+" s) (ids/id-string->id :int s)
    (str/starts-with? s "KW.") (let [without-prefix (subs s 3)
                                     sep-idx        (str/index-of without-prefix "..")]
                                 (if sep-idx
                                   (keyword (subs without-prefix 0 sep-idx)
                                     (subs without-prefix (+ sep-idx 2)))
                                   (keyword without-prefix)))
    ;; Legacy format (CLJ only, colons in keyword names)
    (str/starts-with? s ":") (let [without-colon (subs s 1)
                                   slash-idx     (str/index-of without-colon "/")]
                               (if slash-idx
                                 (keyword (subs without-colon 0 slash-idx)
                                   (subs without-colon (inc slash-idx)))
                                 (keyword without-colon)))
    :else s))

(>defn session-id->ident
  "Converts a session ID keyword back to a Fulcro ident. Returns nil if the keyword
is not a converted ident (i.e., does not have the expected namespace)."
  [session-id]
  [keyword? => any?]
  (when (= session-ns (namespace session-id))
    (let [n (name session-id)
          [qk v] (str/split n #"--" 2)
          [ns nm] (when qk (str/split qk #"_" 2))]
      (when (and ns nm v)
        [(keyword ns nm) (parse-id-value v)]))))

(>defn form-session-id
  "Returns the statechart session ID for a form. Accepts either a Fulcro component
instance (1-arity) or a class and ident (2-arity). The 2-arity form ignores the
class and derives the session ID from the ident alone."
  ([form-instance]
   [any? => keyword?]
   (ident->session-id (comp/get-ident form-instance)))
  ([_form-class ident]
   [any? vector? => keyword?]
   (ident->session-id ident)))

(>defn report-session-id
  "Returns the statechart session ID for a report. The 1-arity form takes a
component instance. The 2-arity form takes a component class and `props` map
(use `{}` for singleton reports whose ident is class-derived)."
  ([report-instance]
   [any? => keyword?]
   (ident->session-id (comp/get-ident report-instance)))
  ([report-class props]
   [any? map? => keyword?]
   (ident->session-id (comp/get-ident report-class props))))

(>defn container-session-id
  "Returns the statechart session ID for a container. The 1-arity form takes a
component instance. The 2-arity form takes a component class and `props` map
(use `{}` for singleton containers whose ident is class-derived)."
  ([container-instance]
   [any? => keyword?]
   (ident->session-id (comp/get-ident container-instance)))
  ([container-class props]
   [any? map? => keyword?]
   (ident->session-id (comp/get-ident container-class props))))

(def auth-session-id
  "Well-known session ID for the singleton auth statechart."
  ::auth-session)
