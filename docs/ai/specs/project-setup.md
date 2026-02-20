# Spec: Project Setup - Deps, Artifact, and Configuration

**Status**: backlog
**Priority**: P1
**Created**: 2026-02-20
**Owner**: conductor
**Depends-on**: none

## Context

fulcro-rad is being forked into fulcro-rad-statecharts. The new project replaces Fulcro UI State Machines (UISMs) with the fulcrologic statecharts library. This spec covers all build/project configuration changes needed before any code conversion begins.

The project currently lives at `/Users/tonykay/fulcrologic/fulcro-rad-statecharts/` and was cloned from fulcro-rad. The statecharts library is developed locally at `../statecharts`.

## Requirements

1. Change the Maven artifact from `com.fulcrologic/fulcro-rad` to `com.fulcrologic/fulcro-rad-statecharts` in all build files
2. Add `com.fulcrologic/statecharts` as a dependency (use local dep `../statecharts` during development, maven coordinates for release)
3. No UISM dependency needs to be explicitly removed -- it comes transitively through fulcro itself and will remain available (just unused by RAD code)
4. Update version to `0.1.0-SNAPSHOT` to indicate this is a new project, not a continuation of fulcro-rad versioning
5. Update project metadata (name, description, URLs) to reflect the new project identity
6. Shadow-cljs.edn needs the statecharts source path added for dev builds
7. Namespace structure (`com.fulcrologic.rad.*`) should NOT be renamed -- the package identity stays the same, only the artifact changes. This allows downstream projects to switch artifacts without rewriting all requires.

## Affected Modules

- `deps.edn` - Add statecharts dep, update artifact identity
- `pom.xml` - Change artifactId, name, description, URLs, version, add statecharts dependency
- `shadow-cljs.edn` - May need source path for local statecharts dev

## Approach

### deps.edn Changes

Current deps.edn has no statecharts dependency. Add:

```clojure
;; In :deps
com.fulcrologic/statecharts {:local/root "../statecharts"}
```

For release, this becomes:
```clojure
com.fulcrologic/statecharts {:mvn/version "RELEASE_VERSION"}
```

The `:dev` alias should also include the statecharts dependency if it overrides versions. Current dev alias does not override fulcro or add statecharts -- the main dep will flow through.

### pom.xml Changes

1. Change `<artifactId>` from `fulcro-rad` to `fulcro-rad-statecharts`
2. Change `<version>` from `1.6.11-SNAPSHOT` to `0.1.0-SNAPSHOT`
3. Change `<name>` from `Fulcro RAD` to `Fulcro RAD Statecharts`
4. Change `<description>` to reference statecharts
5. Update `<url>` and `<scm>` URLs from `fulcro-rad` to `fulcro-rad-statecharts` (once the repo exists)
6. Add `<dependency>` for `com.fulcrologic/statecharts` (version TBD, use latest release)

### shadow-cljs.edn Changes

The current shadow-cljs.edn pulls deps from `:dev` and `:tests` aliases. Since the statecharts dependency is in the main `:deps` section of deps.edn, shadow-cljs will pick it up automatically. No changes needed to shadow-cljs.edn itself.

However, if local development requires the statecharts source to be on the classpath directly (for hot-reload of statecharts source), the `:local/root` dep in deps.edn handles that -- shadow-cljs respects local deps.

### Namespace Considerations

- **No namespace renaming.** The `com.fulcrologic.rad.*` namespace structure remains intact.
- Downstream consumers switch their dependency artifact but keep all `(:require [com.fulcrologic.rad.*])` unchanged.
- New statechart-specific namespaces (if any) will also live under `com.fulcrologic.rad.*`.
- The `com.fulcrologic.fulcro.ui-state-machines` require will be removed from individual files as they are converted, but this is handled by the per-module conversion specs.

### Statecharts Library Info

From the statecharts `deps.edn`, the library's own dependencies are:
- `metosin/malli` 0.20.0
- `com.fulcrologic/guardrails` 1.2.16
- `org.clojure/core.async` 1.8.741
- `com.taoensso/timbre` 6.8.0
- `funcool/promesa` 11.0.678

Potential version conflicts with fulcro-rad's current deps (DECIDED: bump all to latest):
- **guardrails**: RAD uses 1.2.9, statecharts uses 1.2.16 -- bump to latest (>= 1.2.16)
- **core.async**: RAD uses 1.6.673, statecharts uses 1.8.741 -- bump to latest (>= 1.8.741)
- **timbre**: RAD uses 4.10.0, statecharts uses 6.8.0 -- **DECIDED: bump to 6.x**. Timbre 6.x has API changes from 4.x. RAD's usage of timbre must be audited during implementation for any breaking changes (mainly logging macro changes).

## Open Questions

- What version of `com.fulcrologic/statecharts` should be used for the maven dependency at release time? The statecharts library appears to be pre-1.0 and under active development.
- **DECIDED: Bump all deps to latest.** Timbre goes to 6.x to match statecharts. All other dependencies (guardrails, core.async, etc.) bumped to latest available versions. RAD's timbre usage should be audited for any 4.x -> 6.x API changes during implementation.
- Will `com.taoensso/encore` (used by RAD at 3.45.0) be compatible with timbre 6.x? Timbre 6.x may pull in a newer encore. (Audit during implementation.)
- Should the GitHub repo `fulcrologic/fulcro-rad-statecharts` be created before or after initial code conversion?
- Is `0.1.0-SNAPSHOT` the right starting version, or should it track closer to the fulcro-rad version it forked from (e.g. `1.6.11-sc-SNAPSHOT`)?
- **DECIDED: Statecharts as local/root during dev.** Use `{:local/root "../statecharts"}` in deps.edn during development. Switch to maven coordinates for release.

## Verification

1. [ ] `deps.edn` has `com.fulcrologic/statecharts` as a dependency
2. [ ] `pom.xml` artifact is `fulcro-rad-statecharts`
3. [ ] `pom.xml` version is updated to new project version
4. [ ] `pom.xml` metadata (name, description, URLs) reflect new project
5. [ ] `clj -A:dev` resolves all dependencies without errors
6. [ ] `shadow-cljs` can compile the test build successfully
7. [ ] No version conflicts cause runtime errors (especially timbre)
8. [ ] Namespace structure unchanged -- all existing requires still resolve
