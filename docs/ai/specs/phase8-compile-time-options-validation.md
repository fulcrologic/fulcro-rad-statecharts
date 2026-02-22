# Spec: Compile-Time Options Validation

**Status**: backlog
**Priority**: P1
**Created**: 2026-02-22
**Owner**: AI
**Depends-on**: phase8-options-namespace-split, phase8-form-namespace-restructure, phase8-report-namespace-restructure
**Phase**: 8 — Library Restructuring

## Context

With two engines (UISM and statecharts) sharing some option keys but having different engine-specific keys, it's easy for users to accidentally use the wrong engine's options. For example, using `fo/triggers` (UISM callback signature) in a statecharts form, or `fo/machine` (UISM state machine) instead of `sfo/statechart`.

The `defsc-form` and `defsc-report` macros in this library run at compile time and can inspect the options map to catch these mismatches early with clear error messages.

## Requirements

1. The `defsc-form` macro in `statechart.form` must validate the options map at compile time
2. The `defsc-report` macro in `statechart.report` must validate similarly
3. Wrong-engine option keys produce a **compile-time error** (not a warning) with a clear message
4. The validation function (`validate-options!`) is a separate, testable function

## Validation Rules for defsc-form

| If macro sees... | Error message |
|-----------------|---------------|
| `fo/triggers` | "Use sfo/triggers instead of fo/triggers. The statecharts engine uses a different callback signature: (fn [env data form-ident k old new] ops-vec)" |
| `fo/machine` | "Use sfo/statechart instead of fo/machine. The fo/machine option is for the UISM engine." |
| `:will-enter` | "Remove :will-enter. Statecharts routing handles form lifecycle automatically via sfro/statechart." |
| `:will-leave` | "Remove :will-leave. Use sfro/busy? for route-change guarding." |
| `:route-denied` | "Remove :route-denied. The routing statechart handles route denial automatically." |

**Shared keys that pass through without error**: `fo/id`, `fo/attributes`, `fo/subforms`, `fo/route-prefix`, `fo/title`, `fo/layout`, `fo/field-styles`, `fo/default-values`, `fo/validation`, `fo/can-add?`, `fo/can-delete?`, and all other shared keys.

## Validation Rules for defsc-report

| If macro sees... | Error message |
|-----------------|---------------|
| `ro/triggers` | "Use sro/triggers instead of ro/triggers. The statecharts engine uses a different callback signature." |
| `ro/machine` | "Use sro/statechart instead of ro/machine. The ro/machine option is for the UISM engine." |
| `:will-enter` | "Remove :will-enter. Statecharts routing handles report lifecycle automatically." |

## Implementation

```clojure
;; In statechart/form.cljc
#?(:clj
   (defn validate-form-options!
     "Compile-time validation of defsc-form options. Throws if wrong-engine
      option keys are detected."
     [options-map]
     (let [wrong-keys {::fo/triggers  "Use sfo/triggers instead of fo/triggers. Statecharts uses (fn [env data form-ident k old new] ops-vec)"
                       ::fo/machine   "Use sfo/statechart instead of fo/machine. fo/machine is for the UISM engine."
                       :will-enter    "Remove :will-enter. Statecharts routing handles form lifecycle via sfro/statechart."
                       :will-leave    "Remove :will-leave. Use sfro/busy? for route-change guarding."
                       :route-denied  "Remove :route-denied. The routing statechart handles route denial."}]
       (doseq [[k msg] wrong-keys]
         (when (contains? options-map k)
           (throw (ex-info (str "defsc-form compile error: " msg)
                    {:key k :form-options (keys options-map)})))))))

;; In the macro:
#?(:clj
   (defmacro defsc-form [sym arglist options & body]
     (validate-form-options! options)
     (impl/defsc-form* &env [sym arglist options body] convert-options)))
```

## Approach

1. Define `validate-form-options!` in `statechart.form` (CLJ-only, runs at compile time)
2. Define `validate-report-options!` in `statechart.report` (CLJ-only)
3. Call validation as the first step in each macro
4. Write tests that verify the compile-time errors fire correctly
5. Write tests that verify valid options pass through without error

## Testing

```clojure
(specification "Compile-time options validation"
  (behavior "rejects UISM-specific form options"
    (assertions
      ;; This should throw at macro expansion time
      (validate-form-options! {::fo/triggers {:on-change identity}})
      =throws=> ExceptionInfo

      (validate-form-options! {::fo/machine :some-machine})
      =throws=> ExceptionInfo))

  (behavior "accepts valid statechart form options"
    (assertions
      ;; These should NOT throw
      (validate-form-options! {::fo/id :account/id
                               ::fo/attributes [:account/name]
                               ::sfo/triggers {:on-change (fn [& _])}})
      => nil)))
```

## Affected Modules

- `src/main/com/fulcrologic/rad/statechart/form.cljc` — Add `validate-form-options!`, integrate into `defsc-form`
- `src/main/com/fulcrologic/rad/statechart/report.cljc` — Add `validate-report-options!`, integrate into `defsc-report`

## Verification

1. [ ] `fo/triggers` in defsc-form produces compile error with helpful message
2. [ ] `fo/machine` in defsc-form produces compile error
3. [ ] `:will-enter` in defsc-form produces compile error
4. [ ] `ro/triggers` in defsc-report produces compile error
5. [ ] `ro/machine` in defsc-report produces compile error
6. [ ] Valid shared keys (`fo/id`, `fo/attributes`, etc.) pass through without error
7. [ ] Valid statechart keys (`sfo/triggers`, `sfo/statechart`) pass through without error
8. [ ] Error messages are actionable (tell the user exactly what to do instead)
9. [ ] Validation is tested independently of the macro
