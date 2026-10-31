---
name: android-code-review
description: Use when reviewing Android/Kotlin code changes - defines the review finding taxonomy (Critical, Major, Minor, Suggestion, Positive) and what to inspect (correctness, architecture, Kotlin idiom, Compose, concurrency, lifecycle, security, performance, accessibility, testing, maintainability, API compatibility). This skill is the taxonomy and checklist; workflows/code-review is the process that applies it.
metadata:
  version: "0.1.0"
---

# Android Code Review

This skill defines *what a finding is and how severe it is*.
`workflows/code-review/SKILL.md` defines the *process* of running a review
(gather the diff, don't assume the author was right, produce a report) —
read that for process; read this for the taxonomy and what to look at.

## Severity taxonomy

- **Critical** — will cause data loss, a security exposure, a crash on a
  common path, or silently wrong behavior with real user impact (wrong
  balance shown, wrong data sent to the wrong user). Blocks merge.
- **Major** — a real bug, architecture violation, or missing test on a
  non-trivial path that should be fixed before merge but doesn't rise to
  Critical (e.g. a race condition on a rare path, a repository returning
  a DTO, a missing error state for a call that can fail).
- **Minor** — a real but low-impact issue: a missed edge case with low
  likelihood/impact, an inconsistency with the codebase's established
  pattern, a small performance concern with no measured impact. Worth
  fixing, doesn't need to block merge if tracked.
- **Suggestion** — a genuine improvement that isn't a defect: a clearer
  name, an available simplification, an idiom the codebase prefers
  elsewhere. Non-blocking, offered as an opinion.
- **Positive** — something done well worth calling out explicitly (a
  well-structured test, a good abstraction, a thoughtful edge case
  handled). Include at least one Positive finding when one genuinely
  applies — a review that's 100% negative reads as adversarial rather
  than calibrated, and positives reinforce the patterns worth repeating.

Every finding states: what's wrong (or right), where (file/line), why it
matters (the concrete failure scenario for a defect, or the concrete
benefit for a suggestion/positive), and — for Critical/Major — what a fix
looks like. "This could be cleaner" with no concrete alternative isn't a
usable finding.

## What to inspect

- **Correctness** — does the code do what the spec/PR description claims;
  are edge cases from `workflows/requirements-grill` actually handled;
  any off-by-one, null-handling, or state-transition bug.
- **Architecture** — apply `android-architecture`'s dependency-direction
  and layering rules; flag a DTO/entity leak, a repository bypass, a
  cross-feature-module dependency.
- **Kotlin** — idiomatic null-handling (no `!!` without a genuinely
  justified invariant comment), appropriate use of sealed types/`when`
  exhaustiveness for state modeling, scope functions used for clarity not
  cleverness, data classes for value types.
- **Compose** — apply `compose-development`: state hoisting correctness,
  stability, effect-API choice, lifecycle-aware collection, oversized
  composables (see the size heuristic below), accessibility.
- **Concurrency** — apply `kotlin-coroutines-flow`: scope ownership,
  cancellation correctness, dispatcher choice, `StateFlow`/`SharedFlow`
  misuse, race conditions.
- **Lifecycle** — work started that isn't tied to (or explicitly and
  deliberately decoupled from, with a stated reason) the lifecycle of the
  screen/component that triggered it; listeners/receivers registered
  without a matching unregister.
- **Security** — apply `android-security`'s checklist; any hardcoded
  secret or weakened certificate validation is automatically Critical.
- **Performance** — apply `compose-development`'s and `gradle-build`'s
  performance notes; flag only issues with a plausible concrete impact,
  not micro-optimizations with no measurable effect.
- **Accessibility** — missing content descriptions on actionable elements,
  touch target size, font-scale robustness (see `compose-development`).
- **Testing** — apply `android-testing`: is there a test for the new/
  changed behavior; does it assert sequence/behavior rather than only a
  final value where that matters; is a bug fix accompanied by a
  regression test (`workflows/bug-fix` requires this — its absence is at
  least Major).
- **Maintainability** — naming, function/file size and cohesion, whether a
  change follows the codebase's existing conventions rather than
  introducing a one-off pattern.
- **API compatibility** — a public API (a library module's public surface,
  a serialized model shared with a backend or another module) changed in
  a way that breaks an existing consumer without a migration path; a
  `minSdk`-gated API used without the corresponding `Build.VERSION.SDK_INT`
  check or `@RequiresApi` annotation.

## Oversized composable / function heuristic

Not a hard line count. A composable or function is a review target for
extraction when it does more than one of: (a) render more than one
logically distinct section of UI, (b) mix layout structure with business
logic (formatting, validation, event orchestration) that could be hoisted
out, or (c) has grown past the point where its parameters and branches fit
in a reviewer's head at once. A long composable that's a single, cohesive,
linear layout (a form with many fields, none of which have independent
reuse value) is not automatically a finding — judge cohesion, not line
count alone.

## Independence from the implementing agent

Never treat a change as correct because "an agent following
`workflows/implement` wrote it." Review from the diff and the spec, the
same way you would review a human's PR — see
`workflows/code-review/SKILL.md` for how this is enforced procedurally.

See `references/finding-examples.md` for worked before/after examples at
each severity level.
