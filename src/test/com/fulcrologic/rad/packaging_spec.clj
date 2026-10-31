(ns com.fulcrologic.rad.packaging-spec
  "Guards the published artifact. `pom.xml` is what `make deploy` publishes, so its dependencies must be exactly the
   runtime `:deps` of `deps.edn`. When this fails, regenerate the pom's dependencies with `clj -Spom` from the repo
   root. Never hand-edit them."
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.xml :as xml]
   [fulcro-spec.core :refer [=> assertions specification]]))

(defn- children
  "Returns the child elements of the parsed XML `element` whose tag is `tag`."
  [element tag]
  (filterv #(= tag (:tag %)) (:content element)))

(defn- child-text
  "Returns the text of the first child of the parsed XML `element` whose tag is `tag`, or nil."
  [element tag]
  (some-> (first (children element tag)) :content first))

(defn pom-dependencies
  "Returns a map from Maven coordinate string `group/artifact` to `{:version v :scope s}` (`:scope` only when
   declared) for each dependency in the pom file at `path`."
  [path]
  (let [project (xml/parse (io/file path))]
    (into {}
      (comp
        (mapcat #(children % :dependency))
        (map (fn [dep]
               [(str (child-text dep :groupId) "/" (child-text dep :artifactId))
                (cond-> {:version (child-text dep :version)}
                  (child-text dep :scope) (assoc :scope (child-text dep :scope)))])))
      (children project :dependencies))))

(defn deps-edn-dependencies
  "Returns a map from Maven coordinate string `group/artifact` to the coordinate map for each top-level `:deps` entry
   of the deps.edn file at `path`."
  [path]
  (into {}
    (map (fn [[lib coord]]
           [(str (or (namespace lib) (name lib)) "/" (name lib)) coord]))
    (:deps (edn/read-string (slurp path)))))

(specification "pom.xml dependencies"
  (let [pom  (pom-dependencies "pom.xml")
        deps (deps-edn-dependencies "deps.edn")]
    (assertions
      "declare exactly the artifacts in deps.edn :deps"
      (set (keys pom)) => (set (keys deps))
      "pin the same version as deps.edn for every artifact"
      (update-vals pom :version) => (update-vals deps :mvn/version)
      "carry no Maven scope, because clj -Spom drops :scope and the pom must stay regenerable"
      (into {} (filter (comp :scope val)) pom) => {}
      "come only from deps.edn coordinates that carry no :scope"
      (into {} (filter (comp :scope val)) deps) => {})))
