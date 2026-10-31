---
name: design-system-accessibility
description: Use when authoring or reviewing accessibility behavior for a Design System component (TalkBack, semantics, roles, state descriptions, headings, focus/traversal, touch targets, contrast, font scaling, disabled/error states). Builds on compose-development's general Compose accessibility guidance; adds the requirement that accessible behavior is the component's default, not something an application repairs per use.
metadata:
  version: "0.1.0"
---

# Design System Accessibility

`compose-development` already covers general Compose accessibility
basics (content descriptions, touch target size, font-scale robustness) —
read that first if you haven't. This skill is the additional bar a
**Design System component** specifically has to clear: accessibility here
isn't a per-screen concern to remember, it's a component contract that,
once correct, benefits every consumer automatically.

## The rule

**Accessible behavior is the default.** An application developer
consuming a Design System component should not need to add a content
description, fix a touch target, or patch a semantics property for that
component to work with TalkBack — if they do, that's a defect in the
component, not a gap the consumer is expected to fill (see
`design-system-consumer`'s framing: consumers use the documented API,
they don't repair it). This is the single biggest difference between
"accessibility as an afterthought" and "accessibility as a first-class
Design System concern": it has to be solved once, centrally, correctly.

## What a component's accessibility contract covers

- **TalkBack / semantics** — every interactive element exposes a role
  (`Role.Button`, `Role.Checkbox`, etc. via `Modifier.semantics` or the
  underlying Material component's own role) and an accessible
  name — either real visible text or a required `contentDescription`
  parameter the component's API forces the caller to supply when there's
  no visible label (a component that *can* be used with no accessible
  name at all, because the parameter is optional with a silently-empty
  default, is a defect).
- **State descriptions** — a stateful component (a toggle, a tab, a
  selectable chip) exposes its current state via
  `Modifier.semantics { stateDescription = ... }` or the appropriate
  built-in state semantics, so a screen reader announces "selected" /
  "on" / "expanded" rather than only the label.
- **Headings and traversal order** — a component representing a section
  heading uses `Modifier.semantics { heading() }`; composite components
  (a card with several inner elements) group with
  `Modifier.semantics(mergeDescendants = true)` where the whole thing
  should read as one unit, and expose a sensible traversal order rather
  than whatever the layout order happens to produce when that diverges
  from visual/logical reading order.
- **Focus** — keyboard/switch-access focus order follows a logical
  sequence, and any custom focus-handling (a modal that traps focus, a
  multi-step component) manages focus explicitly rather than leaving it
  to accident.
- **Touch targets** — minimum 48dp regardless of the component's visual
  size; a small visual icon still gets a full-size touch target via
  padding, not a shrunk hit area.
- **Contrast** — text/icon-on-background combinations meet WCAG AA
  contrast in every theme variant the component supports (light, dark,
  and any high-contrast mode the Design System offers) — verified per
  token pairing (see `design-tokens`/`compose-theming`), not just eyeballed
  once.
- **Font scaling** — the component's layout doesn't clip or truncate text
  at large system font scales; test previews at an increased font scale,
  not only the default (see `compose-development`'s preview guidance).
- **Disabled and error states** — a disabled component is announced as
  disabled (not just visually dimmed with no semantic signal), and an
  error state announces the error text, not only a color change a
  screen-reader user can't perceive.

## What to check in review

Everything above, applied as a checklist per component
(`design-system-review` runs this alongside its other axes) — plus:
does the component's own documentation
(`templates/component-specification.md`'s Accessibility section) actually
describe this behavior, so a consumer knows what they're getting without
having to test it themselves?
