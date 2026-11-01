---
name: siifii-android-testing
description: Use when writing or reviewing tests for Android code - TDD, unit tests for ViewModels/use cases/repositories, coroutine and Flow testing, Compose UI tests, integration tests, regression and characterization tests, test doubles, determinism, and the testing pyramid.
metadata:
  version: "0.1.0"
---

# Android Testing

See `workflows/implement` and `workflows/bug-fix` for *when* in the
process each kind of test gets written (RED before GREEN, a
characterization test before a fix). This skill covers *how* to write
each kind well.

## The testing pyramid, applied to a typical layered Android app

```
        ┌───────────────┐
        │  Compose UI /  │   few — screen-level wiring, real interaction
        │  e2e tests     │
        ├───────────────┤
        │  Integration   │   some — repository ⇄ real Room/fake server
        ├───────────────┤
        │  Unit          │   most — use cases, ViewModels, mappers,
        │                │   repositories (with fakes), pure functions
        └───────────────┘
```

Most business logic (use cases, mappers, ViewModel state transitions)
should be covered by fast, JVM-only unit tests with no Android framework
dependency (Robolectric or an emulator) — that's what the layering in
`android-architecture` is for. Reserve instrumented/Compose tests for
things that genuinely need a real (or Robolectric-simulated) Android
environment: actual composition, real navigation, real `Context`-dependent
code.

## TDD: RED → GREEN → REFACTOR

Write the test first, watch it fail for the *expected* reason (not a
compile error or a wrong-test-target mistake), write the minimum code to
pass it, then refactor with the test as a safety net. See `workflows/implement`
for how this fits into a full feature; this skill is about test quality
once you're in that loop. A test written after the implementation to match
whatever the code already does isn't a regression test — it's
documentation of current behavior, useful for `refactor` work
specifically (see "Characterization tests" below), but it's not the same
tool as TDD's RED step.

## ViewModel tests

Test through the public `StateFlow`/event surface, not internal fields.
Given a sequence of ViewModel method calls (simulating user actions),
assert the resulting sequence of `UiState` values — using Turbine (see
`kotlin-coroutines-flow`) rather than only asserting the final state,
since a bug that shows the wrong *intermediate* state (e.g. skips
loading, or shows stale content then flickers to new) won't be caught by
a final-value-only assertion.

```kotlin
@Test
fun `search updates state through loading to results`() = runTest {
    val viewModel = SearchViewModel(fakeSearchUseCase, dispatcherProvider = testDispatchers)
    viewModel.uiState.test {
        assertEquals(SearchUiState(), awaitItem())
        viewModel.onQueryChange("kotlin")
        assertEquals(SearchUiState(query = "kotlin", isLoading = true), awaitItem())
        assertEquals(SearchUiState(query = "kotlin", results = fakeResults), awaitItem())
    }
}
```

## Use case tests

Pure unit tests against fake/mock repositories — no coroutine dispatcher
concerns beyond `runTest`. Cover the actual business logic branches (a
use case that only proxies a repository call needs, at most, one test
confirming the proxy; a use case with a validation rule or a
multi-repository combination needs a test per branch, including the
failure paths).

## Repository tests

Two levels, both useful, neither a substitute for the other:

- **Unit**: repository implementation tested against fake/mocked
  data-source interfaces (fake `UserApi`, fake `UserDao`), verifying the
  DTO/entity → domain mapping and the repository's own logic (caching,
  fallback between sources).
  - **Integration**: repository tested against a real local Room database
    (in-memory) and/or a fake server (MockWebServer) to catch things a
    mocked data source can't — actual SQL behavior, actual JSON parsing
    of a real (or realistic fixture) payload, actual HTTP error mapping.

## Coroutine and Flow testing

See `kotlin-coroutines-flow`'s testing section for the mechanics
(`runTest`, injected `TestDispatcher`, Turbine). The test-quality bar here:
assert emission *sequences*, cancel a collector mid-test and assert
cleanup happened where relevant (e.g. a `Flow` backed by a listener that
should unregister), and use `advanceTimeBy`/`runCurrent` deliberately when
a test depends on `delay()`-based timing (a debounce, a retry backoff)
rather than only relying on virtual-time auto-advance.

## Compose UI tests

Test stateless composables directly with fake state and lambdas captured
in the test (fast, no ViewModel/DI wiring needed); reserve full
screen-with-ViewModel tests for the handful of cases where the wiring
itself, or genuinely UI-only behavior (scroll, animation completion,
accessibility semantics), is what's under test. Query by semantics
(`onNodeWithText`, `onNodeWithContentDescription`), which also doubles as
an accessibility check — a node with no findable semantic label is
usually also an accessibility gap (see `compose-development`).

## Integration tests

Exercise a real seam the unit tests fake out — a full request/response
cycle through a fake server, a full Room migration, a full DI graph
assembly (`@HiltAndroidTest` smoke test that the graph builds). Keep the
integration suite deliberately smaller than the unit suite; it's there to
catch what unit tests structurally can't, not to duplicate unit coverage
with more setup cost.

## Regression and characterization tests

- **Regression test**: written to fail on a specific bug that occurred in
  production or was caught in review, then fixed — see `workflows/bug-fix`,
  which requires one before every fix. It stays in the suite permanently
  as a tripwire for that exact bug recurring.
- **Characterization test**: written to pin down what code *currently*
  does, before changing it — used at the start of `workflows/refactor` on
  code with little or no existing test coverage. It is not a statement
  that the current behavior is correct, only that it's the known baseline
  the refactor must preserve (or the spec must explicitly say it's
  changing).

## Test doubles

Prefer a hand-written fake (a real, minimal implementation of the
interface, e.g. an in-memory `FakeUserRepository`) over a mocking
framework for anything with real behavior a test needs to rely on across
several assertions — fakes are more readable at the call site and don't
silently pass when an unexpected method is called. Reach for
mocks/stubs (MockK, Mockito) for a true single-call collaborator where
writing a fake would be pure overhead, or for verifying an interaction
happened (`verify { analytics.track(...) }`) rather than a state outcome.

## Determinism

A flaky test (passes/fails inconsistently with no code change) is a
defect in the test, not something to retry past. Common causes on
Android: real `delay()`/`Thread.sleep()` instead of virtual time, real
system clock/`Date()` instead of an injected clock, unordered collection
assertions where order isn't guaranteed, animation/transition timing in a
Compose test not using `mainClock.autoAdvance = false` with manual
advancement, and shared mutable state leaking between tests (missing
`@Before` reset, a singleton not reset between test classes). Fix the
root cause; don't add a retry annotation to paper over it.
