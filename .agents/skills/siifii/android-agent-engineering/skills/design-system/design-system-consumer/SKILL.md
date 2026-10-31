---
name: design-system-consumer
description: Use when writing UI inside an Android application repository (not the Design System repository itself) - before implementing custom UI, search the shared Design System first. Covers preferring existing components/variants over new ones, never copying Design System source into an app, using tokens instead of hardcoded values, and keeping business logic out of Design System components.
metadata:
  version: "0.1.0"
---

# Design System Consumer

For an agent working inside a normal Android **application** repository
(not the Design System repository itself — see
`design-system-boundary`/`design-system-component-authoring` for that
side). If this project's own `AGENTS.md`/`CLAUDE.md` doesn't say which
side of that boundary it is, treat "application" as the default and ask
if genuinely unsure.

## The rule

**Before implementing custom UI, search the shared Design System first.**

```
UI Requirement
      ↓
Search Design System
      ↓
Existing component?
   ↙           ↘
 YES            NO
 ↓               ↓
Use it        Evaluate boundary (design-system-boundary):
              app-specific, or does this belong in the
              Design System too?
```

"Search" means actually looking — the component catalog/index the
Design System repository ships (its README or component catalog, per
that repo's own docs), not guessing from memory that "there's probably a
Button." If the Design System isn't available to search (not installed,
catalog not accessible), say so explicitly rather than assuming nothing
exists.

## Principles

- Prefer an existing Design System component over writing a new one.
- Prefer an existing component's documented **variant** (see
  `design-system-component-authoring`'s API-design section) over wrapping
  the base component with app-local styling to fake a variant that
  doesn't exist yet — a wrapper that reimplements what a variant should
  be is exactly the kind of drift a Design System exists to prevent. If
  the needed variant genuinely doesn't exist, that's a `design-system-boundary`
  conversation, not a local workaround.
- **Never copy Design System source code into the application.** Depend
  on it as a library/module, not as pasted source — a copy immediately
  starts drifting and defeats the entire point of a shared system.
- **Never recreate a component locally because changing the shared one
  feels inconvenient.** "It's faster to just build my own" is exactly the
  pressure that produces duplicate, inconsistent components across an
  org — if the shared component is missing something, that's a request
  against the Design System (see `design-system-boundary`/the
  `design-system-component` workflow), not a reason to fork it.
- **Never hardcode a design value (color, dp, text size, corner radius,
  elevation) when a token exists for it.** See `design-tokens` — if
  you're about to write a raw value, check for a semantic token first.
- Use the component's documented, semantic public API — not
  implementation details, internal modifiers, or reflection/theme-poking
  to force a look the API doesn't expose. If the API can't do what's
  needed, that's feedback for the Design System, not a reason to reach
  around it.
- Keep application business logic **out of** Design System components —
  a Design System component takes data and callbacks; it doesn't know
  what a "PermitApplication" or an "EmployeeDashboard" is (see
  `design-system-boundary`'s domain-knowledge test). If a design-system
  component would need to import an app-specific domain type to do its
  job, that's a sign it's not actually a Design System component.
- Don't modify Design System behavior from application code through
  hacks (reflection, copy-pasted internals, monkey-patching a theme
  value at runtime to work around a missing token/variant). Every such
  hack is untracked technical debt the Design System team doesn't know
  exists.

## Before generating UI code, work out

1. Which Design System components are available for this requirement?
2. Which tokens are needed (see `design-tokens`)?
3. Which variants/states does this requirement actually need, and do
   they already exist?
4. Does this requirement already exist as a documented pattern somewhere
   in the app (search before assuming novelty)?
5. Is custom, app-specific UI actually necessary here, or does it just
   feel that way because nobody checked the Design System first?

If the honest answer to (5) is "yes, custom UI is necessary" — a
domain-specific screen, a one-off layout with no reuse potential — build
it in the application, following `android-architecture` and
`compose-development` as normal. Not everything belongs in the Design
System; see `design-system-boundary` for how to tell the difference.

## Accessibility and RTL still apply to app-specific UI

Using a Design System component gets accessible behavior and RTL support
"for free" (see `design-system-accessibility`/`rtl-localization` — the
Design System is expected to make these the default, not something the
consumer repairs). Custom, app-specific UI that isn't built from Design
System components still has to meet the same bar itself — falling back to
custom UI doesn't lower the accessibility/RTL requirement, it just moves
who's responsible for it.
