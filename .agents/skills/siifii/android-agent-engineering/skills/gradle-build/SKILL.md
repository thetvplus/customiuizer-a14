---
name: gradle-build
description: Use when writing or reviewing Gradle build configuration for an Android project - Kotlin DSL, dependency management, version catalogs, convention plugins, Android Gradle Plugin/Kotlin/dependency upgrades, build performance, modularization's effect on the build graph, and CI compatibility.
metadata:
  version: "0.1.0"
---

# Gradle Build

## Kotlin DSL and version catalogs

New and touched build files use `build.gradle.kts`, not Groovy, for
type-checked build scripts. Dependency versions and coordinates live in
`gradle/libs.versions.toml` (a version catalog), referenced as
`libs.someLibrary` in module build files — never a hardcoded version
string repeated across modules. Adding a dependency means adding/reusing
a catalog entry, not pasting a coordinate+version directly into a
`build.gradle.kts`.

```toml
# gradle/libs.versions.toml
[versions]
coroutines = "1.9.0"

[libraries]
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
```

## Convention plugins

Repeated build-script configuration (the same Compose setup, the same
Kotlin compiler options, the same test configuration across every feature
module) belongs in a convention plugin under `build-logic/` (an
included build), applied by ID from each module
(`plugins { id("myapp.android.feature") }`), not copy-pasted across every
module's `build.gradle.kts`. If the same 15 lines appear in three or more
modules, that's the signal to extract a convention plugin rather than
keep pasting.

## AGP, Kotlin, and dependency upgrades

- Upgrade AGP and the Kotlin Gradle plugin together, checking the AGP
  release notes' minimum-required and maximum-tested Kotlin version
  range — an AGP upgrade with an incompatible Kotlin version fails in
  ways that look unrelated to the actual cause.
- Bump one major moving part at a time (AGP, or Kotlin, or a major
  dependency like Compose BOM) rather than several simultaneously, so a
  build break is attributable. Read the actual release notes/migration
  guide for a major-version bump, not just the version number — behavior
  changes (new lint defaults, new required flags, deprecated APIs) are
  often the real cost, not the compile itself.
- Prefer a Compose BOM (`implementation(platform(libs.androidx.compose.bom))`)
  over pinning individual Compose artifact versions, so Compose library
  versions stay mutually compatible.
- After any upgrade: full clean build, full test suite, and a check of
  the dependency resolution result for unexpected version bumps pulled in
  transitively (`./gradlew :app:dependencies` or the version catalog's
  update-check tooling) before merging.

## Build performance

- Keep Gradle's configuration cache and build cache enabled
  (`org.gradle.configuration-cache=true`, `org.gradle.caching=true`) and
  treat a change that breaks configuration-cache compatibility (a
  build-script-time side effect, a non-serializable task input) as a
  regression to fix, not a feature to disable the cache for.
- Modularization (see `android-architecture`) is itself the primary
  build-performance lever on a large app: more, smaller modules with a
  correct dependency graph let Gradle parallelize and incrementally
  rebuild only what changed. A single giant `:app` module with everything
  in it recompiles far more than necessary on every change.
- Avoid `api(...)` where `implementation(...)` suffices — `api` leaks a
  dependency to every downstream consumer's compile classpath, widening
  what has to recompile when that dependency changes and often masking an
  architecture boundary violation (a module depending on something it
  should only get transitively is itself worth questioning, see
  `android-architecture`).
- Watch for accidental eager task configuration (using `tasks.getByName`
  instead of `tasks.named` / the lazy task API) in custom build logic —
  it defeats configuration avoidance and slows every build, not just the
  task in question.

## CI compatibility

- The build must be reproducible from a clean checkout with no
  machine-local state assumed (no absolute local paths, no relying on a
  previously-populated Gradle cache that CI won't have on a cold run).
- Pin the Gradle wrapper version (commit `gradle/wrapper/gradle-wrapper.properties`)
  so local and CI builds use an identical Gradle version — never rely on
  a globally installed `gradle`.
- Keep lint/test/assemble as separate, independently-runnable Gradle
  tasks/CI steps so a failure is attributable to the right stage, and so
  CI can parallelize them.
- A build-logic or version-catalog change that only reproduces locally
  (works on the author's machine, fails in CI) usually means a
  machine-local Gradle property, JDK version mismatch, or an
  environment-variable dependency snuck into the build script — treat
  that as a build-script bug, not a CI configuration workaround.
