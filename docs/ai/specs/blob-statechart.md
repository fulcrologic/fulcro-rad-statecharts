# Spec: Blob/File-Upload Statechart Conversion

**Status**: deferred
**Priority**: P3 (deferred to v2)
**Created**: 2026-02-20
**Owner**: conductor
**Depends-on**: project-setup

## Deferral Note

**Deferred to v2.** The blob system has zero UISM dependency -- it uses Fulcro mutations with `action`, `progress-action`, and `result-action`, not UI State Machines. Converting it to a statechart adds complexity and risk without functional benefit for the v1 migration. The current mutation-based approach is fully functional. A statechart conversion may be revisited in v2 for consistency and enhanced testability, but it is not on the critical path for replacing UISMs.

## Context

The blob namespace (`com.fulcrologic.rad.blob`) handles binary large object upload, storage, and retrieval. Unlike forms, reports, and containers, the blob system does **not** use Fulcro UI State Machines (UISMs). It uses:

- Fulcro mutations (`upload-file`) with custom `defmethod m/mutate` for action/progress-action/result-action
- Direct `swap!` state manipulation for upload progress tracking
- `core.async` channels for SHA256 computation
- Server-side pathom resolvers for blob storage

The upload lifecycle has implicit states managed through status keywords stored in Fulcro app state:
- `:uploading` - set in `action` of the mutation
- `:available` - set in `result-action` on success (HTTP 200)
- `:failed` - set in `result-action` on error
- `:not-found` - returned by server-side status resolver when blob is not in storage

This is a natural candidate for a statechart, as it already has well-defined states and transitions.

## Requirements

1. Define a statechart that models the file upload lifecycle
2. Preserve all existing public API functions (`upload-file!`, `blob-downloadable?`, `uploading?`, `failed-upload?`, `upload-percentage`, `evt->js-files`, `defblobattr`)
3. Server-side code (resolvers, middleware, storage) requires NO changes -- it has no UISM dependency
4. The statechart should manage the upload status transitions currently handled by mutation action/result-action/progress-action
5. SHA256 computation (async) must still work correctly
6. Progress tracking must still be supported
7. The `Blob` component and `ui-blob` factory should be preserved or adapted

## Affected Modules

- `src/main/com/fulcrologic/rad/blob.cljc` - Client-side upload logic (CLJS reader conditionals)
- No server-side changes needed

## Approach

### Current Upload Flow

1. User selects a file via `evt->js-files`
2. `upload-file!` is called with form instance, attribute config, js-file, and file-ident
3. SHA256 is computed asynchronously via `file-sha256`
4. `comp/transact!` dispatches `upload-file` mutation with file upload attached
5. Mutation's `action` sets status to `:uploading`, filename, and progress to 0
6. Mutation's `progress-action` updates upload percentage
7. Mutation's `result-action` sets status to `:available` or `:failed`

### Statechart Design

```
[idle] --(:event/upload)--> [computing-sha] --(:event/sha-ready)--> [uploading] --(:event/complete)--> [available]
                                                                        |
                                                                        +--(:event/progress)--> [uploading] (self-transition for progress update)
                                                                        |
                                                                        +--(:event/error)--> [failed]

[failed] --(:event/retry)--> [computing-sha]
```

```clojure
(def blob-upload-chart
  (statechart {:initial :idle}
    (data-model {:expr (fn [_ _] {:progress 0 :status :idle})})

    (state {:id :idle}
      (transition {:event :event/upload :target :computing-sha}
        (script {:expr (fn [env data]
                         (let [{:keys [js-file qualified-key file-ident remote]} (-> data :_event :data)]
                           [(ops/assign :js-file js-file)
                            (ops/assign :qualified-key qualified-key)
                            (ops/assign :file-ident file-ident)
                            (ops/assign :remote (or remote :remote))]))})))

    (state {:id :computing-sha}
      (on-entry {}
        (script {:expr (fn [env data]
                         ;; Kick off SHA computation, send :event/sha-ready when done
                         ;; This needs platform-specific async handling
                         nil)}))
      (on :event/sha-ready :uploading))

    (state {:id :uploading}
      (on-entry {}
        (script {:expr (fn [env data]
                         [(ops/assign :status :uploading)
                          (ops/assign :progress 0)])}))
      (handle :event/progress
        (fn [env data]
          (let [pct (-> data :_event :data :percent)]
            [(ops/assign :progress pct)])))
      (on :event/complete :available)
      (on :event/error :failed))

    (state {:id :available}
      (on-entry {}
        (script {:expr (fn [env data]
                         [(ops/assign :status :available)
                          (ops/assign :progress 100)])})))

    (state {:id :failed}
      (on-entry {}
        (script {:expr (fn [env data]
                         [(ops/assign :status :failed)
                          (ops/assign :progress 0)])}))
      (on :event/retry :computing-sha))))
```

### Integration Challenge: Mutations vs Statecharts

The current implementation uses Fulcro's mutation system with `progress-action` and `result-action` hooks. These are tightly coupled to Fulcro's transaction processing. Two approaches:

**Option A: Statechart wraps mutations (recommended)**

Keep the `upload-file` mutation for the actual HTTP transport (it handles `file-upload/attach-uploads` and the remote call), but have the statechart manage the state transitions. The mutation's `action`, `progress-action`, and `result-action` would send events to the statechart instead of directly manipulating state:

```clojure
;; In mutation result-action:
(let [ok? (= 200 (:status-code result))]
  (scf/send! app session-id (if ok? :event/complete :event/error) {:result result}))
```

This preserves Fulcro's file upload middleware integration while giving the statechart control over state transitions.

**Option B: Full statechart with invoke**

Use `invoke` to handle the upload as an external service. This is cleaner from a statechart perspective but requires reimplementing Fulcro's file upload plumbing inside a statechart invocation processor.

### Recommended Approach: Option A

Option A is pragmatic. The mutation already works and integrates with Fulcro's file upload middleware (which handles multipart form encoding, progress events, etc.). The statechart adds:
- Explicit state management (no more implicit status keywords scattered in mutations)
- Retry capability (transition from `:failed` back to `:computing-sha`)
- Testability (statechart can be tested headlessly)
- Observable state (via `scf/current-configuration`)

### API Preservation

The public helper functions (`blob-downloadable?`, `uploading?`, `failed-upload?`, `upload-percentage`) currently read status/progress from Fulcro state using narrowed keys. These can either:

1. Continue reading from Fulcro state (if the statechart writes status there via aliases)
2. Read from the statechart session data

Option 1 is simpler and preserves backward compatibility. The statechart would use `fops/assoc-alias` to write status and progress to the same state paths the mutation currently uses.

### Session ID Convention

Each blob upload needs its own session. A natural session ID is the combination of file-ident and qualified-key:

```clojure
(defn blob-session-id [file-ident qualified-key]
  [::blob-upload file-ident qualified-key])
```

### What Does NOT Change

- `sha256` and `file-sha256` -- pure/async utility functions
- `Blob` defsc component and `ui-blob` factory
- `defblobattr` macro
- `evt->js-files` -- pure DOM utility
- All server-side code (`#?(:clj ...)` blocks):
  - `upload-file` pathom mutation
  - `wrap-persist-images` middleware
  - `wrap-blob-service` Ring middleware
  - `blob-resolvers` resolver generation
  - `pathom-plugin` and `wrap-env`

## Open Questions

- Is the statechart conversion of blob worth the effort given it has no UISM dependency today? The current mutation-based approach is functional. The main benefit would be testability and consistency with the rest of the RAD statecharts conversion.
- Should the SHA computation be synchronous in CLJ and use `invoke` in CLJS, or should it remain as `core.async` go blocks with events sent back to the statechart on completion?
- The current `progress-action` in the mutation is tightly coupled to Fulcro's networking layer (`net/overall-progress`). How should progress events flow into the statechart? The mutation could fire `scf/send!` with progress data.
- Should blob uploads support cancellation via the statechart? The current system supports abort via `abort-id` on `comp/transact!`. A statechart could add explicit cancel support.
- Is one statechart per upload the right granularity, or should there be a single blob-manager statechart with parallel regions?

## Verification

1. [ ] Blob upload statechart defined with states: idle, computing-sha, uploading, available, failed
2. [ ] `upload-file!` starts a statechart session and initiates the upload
3. [ ] Progress updates flow through the statechart
4. [ ] Success/failure transitions work correctly
5. [ ] `blob-downloadable?`, `uploading?`, `failed-upload?`, `upload-percentage` still work
6. [ ] Server-side blob code completely unchanged
7. [ ] `defblobattr` macro still works
8. [ ] File upload middleware integration preserved
9. [ ] Statechart can be tested headlessly (at least the state transitions)
