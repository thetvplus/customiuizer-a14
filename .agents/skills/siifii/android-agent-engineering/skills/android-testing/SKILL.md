---
name: siifii-android-testing
description: Write or review independent Android behavior tests, including JVM, Robolectric, coroutine/Flow, Compose semantics, lifecycle and regression tests. Reuse A14 test infrastructure; not a framework installation workflow.
metadata:
  version: "0.1.0"
---

# Android behavior tests

Use [A14 TESTING](../../../../../../docs/TESTING.md) for scope, existing fixtures,
verification commands and evidence limits. Do not import upstream workflow
names or introduce another DI/test framework.

Exercise the production subject and assert independently expected output,
ordered calls, native return/exception identity and resource ownership. A mock
may replace external IO; it must not reimplement the algorithm under test.
Source/ABI assertions protect structural contracts, not Android runtime behavior.

Choose the existing smallest useful boundary:

- Pure JVM for deterministic algorithms and policy. Use explicit cases or fixed
  seed sequences with invariants; retain the failing sequence for reproduction.
- Android 14 Robolectric for Handler queues, View/Canvas, window geometry,
  registration and lifecycle. Verify cancellation, replacement, stale callbacks,
  partial initialization and native fallback. Default-returning Android stubs
  cannot prove these behaviors.
- Coroutine/Flow tests use the existing test scheduler and owned scope; assert
  intermediate events, cancellation, absent collectors and disposal. Prefer
  [structured concurrency](../../../../chrisbanes/skills/skills/kotlin-concurrency-and-flow/references/structured-concurrency.md)
  to sleep-based synchronization.
- [Compose semantics tests](../../../../chrisbanes/skills/skills/compose-ui-testing-patterns/SKILL.md)
  exercise the actual user action, route, saved value and observable UI state.
  Keep screenshots for contracts semantics cannot express. Host rendering does
  not prove ROM Hook installation or visual equivalence.

For a bug, first run the smallest regression against the incorrect behavior,
then fix and repeat that same check. Distinguish assertion failure from build,
tool, missing-source, zero-test and stale-report failures. Clean up registered
listeners, queues, static state, temporary files and fake IO within each test;
avoid order dependence and shared mutable fixtures. Reuse valid unchanged
results rather than rerunning every layer for a new commit hash.
