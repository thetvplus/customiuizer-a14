---
name: design-system-component-authoring
description: Use when creating or modifying a component inside the actual Design System repository (not an application). Covers defining a component's API before implementing internals, API design (small, predictable, strongly typed, hard to misuse), avoiding excessive Boolean parameters, slots, modifiers, controlled vs stateless/stateful components, and evolution/versioning.
metadata:
  version: "0.1.0"
---

# Design System Component Authoring

For work happening inside the Design System repository itself. Every
reusable component's design touches: API, variants, sizes, states,
tokens, theming, interaction, accessibility, RTL, localization, previews,
tests, documentation, and migration/versioning. This skill covers the
first item — API design — plus the Compose-specific authoring concerns;
the rest are owned by `design-tokens`, `compose-theming`,
`design-system-accessibility`, `rtl-localization`, and `android-testing`
respectively, referenced rather than restated here. See
`templates/component-specification.md` for the artifact that pulls all of
these together.

## Define the API before implementing internals

Sketch the public function signature(s) — parameters, types, defaults —
and get that reviewed/settled before writing the component's internal
layout logic. A component whose internals are built first tends to grow
an API that mirrors whatever the implementation happened to need, rather
than what's actually easy and hard-to-misuse for a caller.

## What makes a good component API

Prefer APIs that are small, predictable, strongly typed, discoverable,
Compose-native, and difficult to misuse. Concretely:

- **Avoid excessive Boolean parameters.** `isLarge: Boolean`,
  `isRed: Boolean`, `hasIcon: Boolean`, `isSpecial: Boolean` stacked on
  one component don't compose (what does `isLarge = true, isSpecial =
  true` render?), aren't self-documenting at the call site, and grow
  combinatorially as new variants are needed. Prefer a semantic type:

  ```kotlin
  // Avoid
  @Composable
  fun AcmeButton(text: String, isLarge: Boolean = false, isDestructive: Boolean = false, ...)

  // Prefer
  enum class AcmeButtonSize { Small, Medium, Large }
  enum class AcmeButtonVariant { Primary, Secondary, Destructive }

  @Composable
  fun AcmeButton(
      text: String,
      onClick: () -> Unit,
      variant: AcmeButtonVariant = AcmeButtonVariant.Primary,
      size: AcmeButtonSize = AcmeButtonSize.Medium,
      modifier: Modifier = Modifier,
  )
  ```

  A single Boolean is fine when the parameter genuinely is a two-state
  toggle with no foreseeable third state (`enabled: Boolean`) — the
  problem is several Booleans encoding what's really one multi-way
  choice.

- **Slots over rigid parameters** for content that varies in shape, not
  just value — a `leadingIcon: (@Composable () -> Unit)? = null` slot
  lets a caller pass any composable rather than the component needing a
  new parameter for every icon-adjacent variant a caller might want.
- **`Modifier` parameter, always** — every public composable takes a
  `modifier: Modifier = Modifier` and applies it to its outermost element
  first, per standard Compose convention; skipping this silently breaks
  every layout customization a caller would reasonably expect to work.
- **Stateless by default, offer a stateful convenience overload when
  useful** — the same state-hoisting principle as `compose-development`,
  applied at the library-API level: the core composable takes
  value+`onValueChange` (a controlled component), with an optional
  `remember`-backed convenience wrapper for callers who don't need to
  observe/control the state themselves. Don't ship only the stateful
  version — it can't be driven by a ViewModel, which most real app
  integrations need.
- **Sensible, named defaults** for every optional parameter, so the
  common case is a short call and the full parameter list is discoverable
  via autocomplete/docs rather than needing to be memorized.
- **Prefer new overloads over changing an existing signature** once a
  component has shipped — see "Evolution and versioning" below.

## Slots, controlled components, and state ownership

A component that owns meaningful state internally (not just
purely-visual, momentary state like a ripple) should be a **controlled
component**: state and its change callback are parameters, owned by the
caller, exactly like `compose-development`'s state-hoisting rule applied
to a library boundary rather than an app screen. This is what lets a
consumer wire a component to a ViewModel's `StateFlow` (see
`design-system-consumer`) instead of the component silently diverging
from application state.

## API evolution and compatibility

- Prefer adding a new overload or a new optional parameter with a
  backward-compatible default over changing an existing public
  signature — a signature change breaks every consumer at their next
  build, which is expensive at Design-System scale (many consuming
  repos, not one).
- A genuinely breaking change (removing a parameter, changing a type,
  removing a variant) needs a documented migration path and a version
  bump per this component library's own semver — out of scope for what
  this repo tracks (see `docs/design-system.md`), but the *discipline* of
  treating a public component API as a real contract is exactly what
  this section is about.
- `@Deprecated` with a `ReplaceWith` on the old signature, kept for at
  least one minor version, is the default deprecation path — don't
  delete a public API in the same change that introduces its
  replacement.

## What to check before calling a component "done"

- Does the API avoid excessive Booleans (see above)?
- Does every public composable take and apply a `Modifier` parameter?
- Is state controlled (hoisted) rather than owned internally, for
  anything beyond momentary visual state?
- Are the required variants/sizes/states from
  `templates/component-specification.md` actually covered — no more than
  needed, no fewer than the spec calls for?
- Tokens used, not hardcoded values (`design-tokens`).
- Theming applied correctly (`compose-theming`).
- Accessible by default (`design-system-accessibility`).
- RTL/localization correct (`rtl-localization`).
- Previews and tests present (`android-testing`).
