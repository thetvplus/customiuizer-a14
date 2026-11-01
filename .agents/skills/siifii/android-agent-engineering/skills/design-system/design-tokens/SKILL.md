---
name: design-tokens
description: Use when defining or consuming Design System tokens - color, typography, spacing, shape, elevation, opacity, sizing, iconography, motion. Establishes the primitive -> semantic -> component token layering and the rule that application code should rarely touch primitive/raw tokens directly, and never generate arbitrary raw values when a token exists.
metadata:
  version: "0.1.0"
---

# Design Tokens

The taxonomy and layering rules for Design System tokens. `compose-theming`
covers how these tokens actually flow through Compose's `MaterialTheme`/
`CompositionLocal` mechanics — this skill covers what a token *is* and
which layer it belongs to.

## Categories

Color, typography, spacing, shape, elevation, opacity, sizing,
iconography, and motion (where the Design System defines motion tokens —
durations/easing curves) are all token categories. Every one of them
follows the same layering below.

## The layering

```
Raw Design Values
        ↓
Primitive Tokens        (e.g. blue500 = #1A73E8, spacing4 = 4.dp)
        ↓
Semantic Tokens          (e.g. colorActionPrimary = blue500, spacingContentGap = spacing4)
        ↓
Component Tokens         (e.g. buttonPrimaryBackground = colorActionPrimary)
        ↓
Components
        ↓
Application UI
```

- **Primitive tokens** are the raw palette/scale — a color ramp, a
  spacing scale, a type scale — with no meaning attached yet. They exist
  so the *next* layer has a consistent, finite set of values to choose
  from, not so application code references them directly.
- **Semantic tokens** attach meaning to a primitive: `colorBackgroundPrimary`,
  `colorSurface`, `colorContentPrimary`, `colorContentSecondary`,
  `colorActionPrimary`, `colorError` — named for *what the value means*
  in context, not for the raw value itself. This is the layer that
  changes between light/dark theme (see `compose-theming`) while the
  component code referencing it doesn't need to change at all.
- **Component tokens** are semantic tokens scoped to one component
  (`buttonPrimaryBackground`, `cardElevationDefault`) when a component
  needs its own themeable seam distinct from the general semantic set —
  not every component needs these; introduce one only when a component's
  styling genuinely needs to vary independently of the semantic token it
  would otherwise inherit.

## The rule

**Application code should rarely access primitive/raw tokens directly.**
It consumes semantic (or component) tokens almost exclusively — reaching
past semantic tokens down to a primitive (`blue500` instead of
`colorActionPrimary`) reintroduces exactly the coupling-to-a-raw-value
problem tokens exist to prevent: the primitive's *meaning* in that
context is now implicit again, and a future rebrand/theme change can't
safely reassign what `blue500` maps to without checking every direct
primitive reference.

**Never generate an arbitrary color, dp value, text size, corner radius,
or elevation when an appropriate token already exists.** Before writing a
raw value anywhere — in the Design System itself or in a consuming
application (see `design-system-consumer`) — check whether a semantic
token already expresses that value's *meaning*. If genuinely nothing
fits, that's a signal to either add the token to the Design System (if
the value represents a real, reusable concept) or reconsider whether the
value should really be arbitrary at all, before falling back to a literal.

## What to check in review

- Any raw hex color, unscoped `.dp`/`.sp` literal, or magic elevation
  value in a component or (per `design-system-consumer`) in an
  application — Major at minimum; Critical if it's inside the Design
  System itself, since that's the one place meant to define the tokens
  everyone else should be using instead.
- A component reaching past a semantic token to reference a primitive
  directly, with no documented reason.
- A new component token introduced where an existing semantic token would
  have worked — unnecessary layer, harder to reason about the theme as a
  whole.
