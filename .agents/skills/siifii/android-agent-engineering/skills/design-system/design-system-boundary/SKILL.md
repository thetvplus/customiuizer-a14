---
name: design-system-boundary
description: Use when deciding whether a piece of UI belongs in the shared Design System or inside an application - evaluates reusability, domain knowledge, and visual-primitive-vs-product-feature to decide. Not a "used twice equals Design System" rule. When uncertain, prefer keeping domain-specific UI in the application until a stable reusable pattern emerges.
metadata:
  version: "0.1.0"
---

# Design System Boundary

The question this skill answers: **does this UI belong in the shared
Design System, or inside the application?** Use it before creating a new
reusable component (paired with `workflows/design-system-component`'s
"Boundary Evaluation" step) and during `design-system-review` when a PR's
component doesn't obviously belong on one side.

## What to evaluate

Don't apply a single mechanical rule ("used in two places = Design
System"). Weigh all of these together:

- **Reusability** — could multiple *products*, not just multiple screens
  in one product, reasonably use this? A component reused three times
  within one app's checkout flow is not automatically Design System
  material; a component every product independently needs is a stronger
  signal.
- **Domain knowledge** — does the component know business/domain
  concepts (a permit type, an employee role, a specific product's
  terminology)? If so, it's application-layer by definition — a Design
  System component takes generic data (`title: String`, `items: List<T>`)
  and callbacks, never a domain model.
- **Visual primitive vs. product feature** — is this a building block
  (button, field, card, dialog, navigation primitive) or is it a
  recognizable *feature* of one product wearing a UI (a specific
  dashboard widget, a specific application's summary card)?
- **Abstraction quality** — can this be designed with a clean, general
  API today, or does making it "reusable" require bolting on
  configuration flags for every current consumer's specific need (a sign
  it isn't actually one reusable thing yet)?
- **API stability** — is the shape of this UI settled, or still actively
  changing as the one product that needs it iterates? A component whose
  API would need to change weekly isn't ready to be shared.
- **Customization pressure** — how much would different consumers need to
  customize it? Heavy, divergent customization needs are a sign the
  "shared" version would end up as an unmaintainable pile of flags (see
  `design-system-component-authoring`'s Boolean-parameter warning).
- **Accessibility behavior** — does this component's accessible behavior
  generalize, or is it tied to this one product's specific interaction
  model?
- **Ownership** — who would maintain this going forward, and does that
  team have the bandwidth/mandate to support it as a shared dependency?

## Examples (illustrative, not exhaustive)

Likely Design System: Button, TextField, Card, Dialog, BottomSheet,
Snackbar, Avatar, Badge, Tabs, Chips, date/time inputs, loading states,
empty states, navigation primitives.

Likely application-specific: a component named after a domain concept
(a permit-application card, an employee-dashboard widget, a
license-renewal summary, a product-specific journey header) — these know
what the product's data means, which is the domain-knowledge test above
failing.

## When uncertain

**Prefer keeping domain-specific UI inside the application until a
stable, genuinely reusable pattern emerges.** It's far cheaper to promote
a proven application component into the Design System later (once its
API has settled from real, repeated use) than to walk back a
prematurely-shared component that's now a public API three teams depend
on. A "maybe reusable" component defaults to staying in the app.

## Output of a boundary evaluation

State explicitly: which side of the boundary, and *why* — naming which
of the factors above tipped the decision. "This seems reusable" without
naming the factors isn't a usable evaluation; see
`android-code-review`'s "usable finding" bar for the same standard
applied here.
