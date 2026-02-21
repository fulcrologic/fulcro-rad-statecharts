(ns com.fulcrologic.rad.rendering.headless.plugin
  "Entry point for the headless rendering plugin. Requiring this namespace installs
   all default headless renderers for forms, reports, fields, and controls.

   Usage: Simply require this namespace in your application or test setup:

   ```clojure
   (require 'com.fulcrologic.rad.rendering.headless.plugin)
   ```

   All rendering multimethods will be populated with `:default` implementations
   that produce plain HTML elements with data attributes for headless test selection.

   No CSS, no React component libraries — just structural HTML."
  (:require
    ;; Requiring these namespaces registers all defmethod implementations
   com.fulcrologic.rad.rendering.headless.field
   com.fulcrologic.rad.rendering.headless.form
   com.fulcrologic.rad.rendering.headless.report
   com.fulcrologic.rad.rendering.headless.controls))
