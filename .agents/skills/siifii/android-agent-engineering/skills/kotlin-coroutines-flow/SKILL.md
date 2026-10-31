---
name: kotlin-coroutines-flow
description: Use when writing or reviewing Kotlin coroutines and Flow code on Android - structured concurrency, CoroutineScope ownership, dispatchers, cancellation, exception handling, StateFlow/SharedFlow/Flow choice, lifecycle interaction, race conditions, testing coroutines, and avoiding GlobalScope or other unmanaged jobs.
metadata:
  version: "0.1.0"
---

# Kotlin Coroutines & Flow

## Structured concurrency: every coroutine has an owner

A coroutine is always launched into a scope with a defined lifecycle, and
that scope's owner is responsible for its cancellation. On Android, the
scope is almost always one of: `viewModelScope` (ViewModel, cancelled on
`onCleared`), `lifecycleScope`/`repeatOnLifecycle` (a Fragment/Activity,
tied to the lifecycle state), or a scope your class creates and explicitly
cancels in a matching teardown (e.g. `close()`/`onCleared()` for a
non-ViewModel class that owns background work).

**Forbidden by default:** `GlobalScope.launch { ... }`, a raw
`CoroutineScope(Dispatchers.IO)` created and never cancelled, or a
`launch` whose job is dropped on the floor without ever being cancelled
when its owner goes away. Each of these is a leak: the coroutine outlives
the thing that should own it, keeps references alive, and keeps doing
work (network calls, DB writes) for a screen or object nobody is looking
at anymore. If a coroutine genuinely must outlive its triggering UI (e.g.
"finish this upload even if the user navigates away"), that's an
application-scoped, deliberately-injected `CoroutineScope` with a
documented lifetime and cancellation policy — not `GlobalScope`.

```kotlin
// Leak: nothing ever cancels this
class SyncManager {
    private val scope = CoroutineScope(Dispatchers.IO)
    fun startSync() = scope.launch { /* ... */ }
}

// Owned: caller controls the lifetime
class SyncManager(private val scope: CoroutineScope) {
    fun startSync() = scope.launch { /* ... */ }
}
```

## Dispatchers

Inject the dispatcher (or a `DispatcherProvider`/similar abstraction)
rather than hardcoding `Dispatchers.IO`/`Dispatchers.Default` inside a
class — this is what makes the class testable with `TestDispatcher`
without touching real threads. Use `Dispatchers.IO` for blocking I/O
(network via a blocking client, disk, database — though Room's suspend
DAO functions already dispatch correctly and don't need a manual
`withContext(Dispatchers.IO)` wrapper), `Dispatchers.Default` for CPU-bound
work (sorting/parsing a large payload, image processing), and
`Dispatchers.Main`/`Dispatchers.Main.immediate` only where the code
genuinely needs the main thread (Compose/View updates) — most ViewModel
code doesn't need an explicit dispatcher at all, since `viewModelScope`
already runs on `Dispatchers.Main.immediate` and suspend functions
further down switch context themselves via `withContext`.

**Flag:** a suspend repository/use-case function that does its own
`withContext(Dispatchers.IO)` around a Retrofit suspend call (already
non-blocking, doesn't need it) — mild inefficiency, not correctness — vs.
a blocking call (`File.readText()`, a synchronous SDK call) made with no
dispatcher switch at all inside a suspend function on the main path —
this one is a real bug (blocks the calling dispatcher, e.g. freezes the
UI if reached from `Dispatchers.Main`).

## Cancellation

Cancellation in Kotlin coroutines is cooperative: a suspending function
checks for cancellation at suspension points, but a tight non-suspending
loop or a blocking call doesn't get cancelled on its own. In a long-running
computation, call `ensureActive()`/`yield()` periodically, or use
`isActive` in a loop condition. Catching `CancellationException` and
swallowing it (rather than rethrowing) breaks cancellation for the rest of
the coroutine hierarchy — always rethrow it:

```kotlin
try {
    doWork()
} catch (e: CancellationException) {
    throw e // never swallow this
} catch (e: IOException) {
    // handle real failures
}
```

A `finally` block runs on cancellation too, but it runs in a
non-cancellable context only if wrapped in `withContext(NonCancellable)`
— use that specifically for cleanup that itself needs to suspend (closing
a network resource with a suspend `close()`), not as a general habit.

## Exception handling

- A failure inside `launch` propagates to the parent job and, by default,
  crashes the app unless caught locally or unless the scope has a
  `CoroutineExceptionHandler` (a last-resort logging/reporting boundary,
  not a substitute for handling the error where it's meaningful to the
  user). Prefer catching at the call site where you can act on the error
  (show a UI error state, retry), and reserve a top-level
  `CoroutineExceptionHandler` for truly unexpected failures.
- A failure inside `async` is held until `.await()` is called — an
  `async` whose result is never awaited silently drops the exception.
  Don't fire-and-forget an `async`; use `launch` if the result is unused.
- Prefer `runCatching`/`Result<T>` at a repository or use-case boundary
  over letting a raw exception cross into `presentation`, so the ViewModel
  handles a typed outcome rather than a try/catch around business logic.

## `StateFlow`, `SharedFlow`, `Flow` — picking the right one

- **`StateFlow`** — a single current value, always has one (a
  non-nullable initial value required), conflates rapid updates, replays
  the latest value to new collectors. Use for UI state.
- **`SharedFlow`** — configurable replay/buffer, no required current
  value. Use for one-shot events (navigation, snackbars, "show this
  toast once") with `replay = 0` so a late collector doesn't re-receive an
  already-handled event — see `compose-development`'s navigation section
  for why this matters.
- **`Flow`** (cold) — a fresh sequence per collector, does no work until
  collected, cancels when the collector's coroutine cancels. Use for a
  data stream a repository exposes (e.g. `observeUser(): Flow<User>`)
  that should only run its upstream work while something is actually
  listening.

**Flag:** `StateFlow<T?>` used to model "no event yet" as `null` and never
reset back to `null` after consuming — that's the `SharedFlow`/`Channel`
use case in disguise, and it'll re-fire the "event" on every new
collector (e.g. after a configuration change) if not paired with lifecycle
-aware, event-consuming collection.

## Lifecycle interaction

A `Flow` collected in an Activity/Fragment should use
`repeatOnLifecycle(Lifecycle.State.STARTED)` (or, in Compose,
`collectAsStateWithLifecycle()` — see `compose-development`), not a bare
`lifecycleScope.launch { flow.collect { ... } }`, which keeps collecting
even while the UI is stopped. `viewModelScope` itself needs no lifecycle
wrapping — its lifetime already matches the ViewModel.

## Race conditions to watch for

- Two coroutines both reading-then-writing shared mutable state (e.g. a
  `MutableStateFlow.value = current + 1` under concurrent access) — prefer
  `update { it + 1 }` (atomic, retries on contention) over separate
  read/write.
- A `launch` started from a Compose event handler without
  cancelling a prior in-flight one for the same logical operation (e.g.
  rapid button taps each starting a new network call) — track the job and
  cancel-and-replace, or debounce at the source.
- Awaiting multiple independent results sequentially
  (`val a = fetchA(); val b = fetchB()`) when they don't depend on each
  other — use `coroutineScope { val a = async { fetchA() }; val b = async
  { fetchB() }; a.await() to b.await() }` for both correctness of
  structured concurrency (a failure in either cancels the other) and
  latency.

## Testing coroutines

- Use `kotlinx-coroutines-test`'s `runTest` (not `runBlocking`) so virtual
  time skips real delays.
- Inject a `TestDispatcher` (via the `DispatcherProvider` abstraction
  mentioned above) everywhere a class would otherwise use
  `Dispatchers.IO`/`Default`/`Main`; for `Dispatchers.Main` specifically,
  set it once via `Dispatchers.setMain(testDispatcher)` in a test rule's
  setup and reset in teardown.
- Test `Flow`/`StateFlow` emissions with Turbine (`flow.test { ... }`)
  rather than manually collecting into a list — it makes missed/extra
  emissions and unconsumed items fail loudly instead of silently passing.
- A coroutine test that passes only because the test never advances
  virtual time past a `delay()`, or that doesn't assert the *sequence* of
  emissions (just the final value) when the sequence is the actual bug
  surface (loading → content vs. loading skipped), is a weak test — see
  `android-testing`.
