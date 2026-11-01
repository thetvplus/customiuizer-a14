---
name: design-system-review
description: Use when reviewing a pull request against the Design System repository itself. Reviews API quality, genuine reusability, token usage, Compose correctness, accessibility, localization/RTL, state coverage, and documentation/test quality. Delegates general Kotlin/Compose/concurrency/performance review to android-code-review's taxonomy rather than duplicating it - only adds Design-System-specific axes.
metadata:
  version: "0.1.0"
---

# Design System Review

A specialized review lens for Design System pull requests, used by
`workflows/design-system-component`'s "Engineering Review" step. Classify
findings using this repository's existing severity taxonomy —
`skills/android-code-review`'s Critical/Major/Minor/Suggestion/Positive —
and run that skill's general checklist (correctness, Kotlin, Compose
state/recomposition/stability/side effects/Modifier usage/performance,
concurrency, lifecycle, testing, maintainability, API compatibility)
first. This skill adds only what's specific to reviewing a *Design
System* component, on top of that.

## API

- Is the public API understandable from its signature alone, without
  reading the implementation?
- Is it difficult to misuse — could a caller construct an invalid or
  nonsensical combination of parameters the type system should have
  prevented?
- Are unnecessary Boolean parameters present (see
  `design-system-component-authoring`)?
- Is customization exposed through the documented API (slots, modifiers,
  variants), or does a caller need to reach around it?

## Reuse

- Is this component genuinely reusable, or does it encode
  product/domain knowledge that should have kept it in an application
  (see `design-system-boundary` — apply the same evaluation here,
  reviewing whether the boundary call was actually correct)?
- Is business logic leaking into the component (data fetching, domain
  validation, anything beyond taking data and calling back)?

## Tokens

- Any hardcoded design value (color, dp, text size, corner radius,
  elevation) where a token exists (`design-tokens`)? Critical-severity
  inside the Design System itself, since this is the one place meant to
  define the tokens everyone else uses.
- Are semantic (or component) tokens used, rather than reaching past them
  to a primitive?

## Compose

Apply `android-code-review`'s Compose axis in full (state, recomposition,
stability, side effects, Modifier usage, performance) — nothing
additional here beyond noting that a Design System component's
recomposition behavior matters more than an app screen's, since it's
instantiated far more often across the app(s) that consume it.

## Accessibility

Apply `design-system-accessibility`'s full checklist: TalkBack/semantics,
roles, state descriptions, headings, focus/traversal, touch targets,
contrast, font scaling, and disabled/error-state announcements.

## Localization

Apply `rtl-localization`'s checklist: RTL correctness (start/end, icon
mirroring), Arabic typography, text expansion tolerance, and no hardcoded
strings.

## States

Verify the states in `templates/component-specification.md` that
actually apply to this component: default, pressed, focused, selected,
disabled, loading, success, warning, error. **Don't require every state
for every component** — a static display component may legitimately have
only default/disabled; flag a missing state only when it's plausibly
reachable for that specific component, not mechanically against the full
list.

## Quality

- Previews present, covering the states/variants that differ visually,
  in both light/dark theme and LTR/RTL where applicable.
- Tests present per `android-testing` (unit tests for any non-trivial
  logic, Compose UI tests for interaction/accessibility semantics).
- `templates/component-specification.md` filled in and accurate to what
  actually shipped — a spec that describes an earlier version of the API
  than what's in the diff is itself a finding.
- Examples in the spec/docs are runnable/representative, not
  pseudocode that wouldn't actually compile against the real API.
- Migration implications: if this changes an existing public API, is
  there a `@Deprecated`/`ReplaceWith` path (see
  `design-system-component-authoring`'s evolution section) rather than a
  silent breaking change?
