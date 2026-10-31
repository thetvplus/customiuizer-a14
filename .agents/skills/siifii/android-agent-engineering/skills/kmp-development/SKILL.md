---
name: kmp-development
description: Use when designing or reviewing shared Kotlin Multiplatform code from the Android side - KMP module boundaries, shared domain/data logic, expect/actual, cross-platform dependency management, coroutine usage in shared code, API design for multiplatform consumers, Android/iOS separation, and testing shared code. Scoped to the Android-side boundary, not deep iOS-specific guidance.
metadata:
  version: "0.1.0"
---

# Kotlin Multiplatform Development

Written from the Android side of a KMP setup: what belongs in shared
code, what stays platform-specific, and how to keep the boundary honest.
Deep iOS-specific implementation guidance is out of scope for this skill.

## What belongs in `commonMain`

The same layering as `android-architecture`'s `domain` (and much of
`data`) is exactly what's shareable: use cases, domain models, repository
interfaces, and any data-layer logic that doesn't touch a
platform-specific API. If a piece of business logic has zero Android or
iOS framework dependency today, it's a candidate for `commonMain` even in
an Android-only app that's expected to add another target later —
writing it against `expect`/`actual` abstractions from the start is
cheaper than migrating it afterward, but don't introduce KMP module
structure speculatively for a project with no concrete multiplatform plan
(see `android-architecture`'s YAGNI note on modularization — the same
applies here).

## `expect`/`actual`

Use `expect`/`actual` for the minimum surface that's genuinely
platform-different — a platform's date/time source, secure storage
primitive, HTTP client engine, logging sink. Keep the `expect`
declaration's shape as narrow and behavior-neutral as possible (an
interface or a small function), so `commonMain` code depends on a stable
contract rather than reaching for a lower-level platform-specific type by
accident. Prefer a shared library (e.g. `kotlinx-datetime`,
`kotlinx-io`) over a hand-rolled `expect`/`actual` when one already
exists and fits — every `expect`/`actual` pair is a boundary the team
has to maintain on both platforms.

```kotlin
// commonMain
expect class SecureStorage {
    suspend fun get(key: String): String?
    suspend fun set(key: String, value: String)
}

// androidMain — backed by EncryptedSharedPreferences (see android-security)
// iosMain — backed by Keychain
```

## Dependency management across targets

A `commonMain` dependency must itself be multiplatform (published with
the right target artifacts) — adding an Android-only library to a
`commonMain` source set doesn't compile, but adding one that happens to
have a multiplatform artifact without checking its iOS target is
production-ready is a common trap (e.g. an alpha/experimental iOS target).
Keep the version catalog (`gradle-build`) as the single source of truth
for shared-library versions across all source sets, same as an
Android-only project.

## Coroutines in shared code

Structured concurrency rules from `kotlin-coroutines-flow` apply
unchanged in `commonMain` — own the scope, no unmanaged
`GlobalScope`-equivalent, cooperative cancellation. The platform-specific
part is dispatcher defaults: `Dispatchers.Main`/`Default`/`IO` availability
and threading model differ enough between Android and iOS that shared
code should take an injected dispatcher (or use `Dispatchers.Default`/
explicitly-`expect`ed IO dispatcher) rather than assume Android's
dispatcher semantics translate directly.

## API design for multiplatform consumers

Design a shared module's public API against what a consumer on *either*
platform can call idiomatically, not just what's convenient from Kotlin.
Concretely: avoid exposing `Flow` directly to a Swift/iOS consumer without
a wrapping layer (Swift interop with `Flow` needs either a
KMP-Swift bridging library or a hand-written callback/`async` wrapper);
avoid Kotlin-only idioms (default parameter values, which don't surface
to Swift the same way; extension functions on platform types) at the
outermost shared API surface a native iOS caller will touch directly.

## Android/iOS separation

`androidMain`/`iosMain` hold everything that's genuinely
platform-specific: UI (Compose stays Android-only unless the project has
separately adopted Compose Multiplatform, which is a distinct, larger
decision outside this skill's default scope), platform SDK calls, and the
`actual` implementations. Don't let an Android-specific concern leak back
into `commonMain` through a convenience shortcut (e.g. an `expect` function
that's really just "give me a `Context`" — that's a sign the abstraction
boundary is in the wrong place; redesign the `commonMain` API around what
the operation needs, not around the Android type that currently satisfies
it).

## Testing shared code

`commonTest` runs against every target's test runner, so a `commonMain`
use case or mapper gets tested once and verified on every platform — this
is one of the concrete payoffs of putting business logic there. Platform
-specific `actual` implementations still need their own
`androidTest`/`iosTest` coverage (see `android-testing` for the Android
half); a shared interface with no test on either `actual` implementation
means the platform-specific correctness is untested even though the
`commonMain` contract "looks" tested.
